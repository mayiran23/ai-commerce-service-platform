"""
电商订单智能客服 —— Python AI 服务
v0.2：接上第一个工具 query_order，让 AI 能查到真实订单
v0.3：接上第二个工具 search_user_orders（契约 §8.2），
      AI 可以不带订单号、按状态或商品名搜订单列表
v0.4 接上售后三件套-check_refund_eligible
    create_after_sale,search_after_sale
    AI从此能自己判断退货资格并建工单
"""

import json
import os
import time
from pathlib import Path

import httpx
from dotenv import load_dotenv
from fastapi import FastAPI
from openai import OpenAI
from pydantic import BaseModel
import rag
from prompts import SYSTEM_PROMPT
from tool_specs import TOOLS

# ============================================================
# 1. 读取配置
# ============================================================
BASE_DIR = Path(__file__).resolve().parent
load_dotenv(BASE_DIR / ".env")

os.environ.setdefault("NO_PROXY", "127.0.0.1,localhost")
os.environ.setdefault("no_proxy", "127.0.0.1,localhost")

API_KEY = os.getenv("DEEPSEEK_API_KEY", "")
MODEL = os.getenv("DEEPSEEK_MODEL", "deepseek-chat")
BASE_URL = os.getenv("DEEPSEEK_BASE_URL", "https://api.deepseek.com")

# 调 Java 内部接口要用到的两个东西
JAVA_BASE_URL = os.getenv("JAVA_BASE_URL", "http://127.0.0.1:8080")
INTERNAL_TOKEN = os.getenv("INTERNAL_TOKEN", "")

# 客户端建一次全局复用（内部有连接池），不要每次请求都新建
client = OpenAI(api_key=API_KEY, base_url=BASE_URL)



app = FastAPI(title="AI Commerce Service", version="0.4.0")

def java_error_message(resp) -> str:
    """
    内部接口返回非 200 时，尽量把 Java 给的具体原因取出来交给模型。

    为什么不能只说一句"HTTP 400"：模型要靠这句话判断下一步怎么改。
    实测：返回"该订单中不存在此商品"时，它会自己去查订单、拿到正确的 productId 再重试。
    """
    try:
        data = resp.json()
        msg = data.get("msg") if isinstance(data, dict) else None
    except Exception:
        msg = None
    return msg or  f"Java 服务返回 HTTP {resp.status_code}"


# ============================================================
# 2. 工具的实际执行逻辑
# ============================================================
def build_query_order(user_id: int):
    """
    这是一个"工厂函数"：调用它，它会返回一个可用的 query_order 工具。

    为什么要绕这一层？因为工具的参数只能由模型提供（order_no），
    但 userId 必须由 Java 从 JWT 里解析后注入，绝不能让模型传 ——
    否则模型（或用户）可以伪造别人的 userId 去查别人的订单。

    这里用闭包把可信的 userId 锁在函数内部，模型能决定的只有 order_no。
    这个设计面试时值得讲：权限边界不交给模型。
    """

    def query_order(order_no: str) -> dict:
        """调 Java 的内部接口查订单详情。返回统一结构，方便交给模型理解。"""
        url = f"{JAVA_BASE_URL}/internal/orders/{order_no}"
        headers = {"X-Internal-Token": INTERNAL_TOKEN}

        try:
            resp = httpx.get(
                url,
                params={"userId": user_id},   # userId 走查询参数，Java 侧会校验归属
                headers=headers,
                timeout=10,
            )
        except Exception as e:
            # 网络不通、Java 没起来等等，都归到这里，不要让它抛出去
            return {"ok": False, "error": f"调用 Java 服务失败：{e}"}

        if resp.status_code != 200:
            return {"ok": False, "error": java_error_message(resp)}

        data = resp.json()

        # Java 侧"查不到"和"不是你的订单"返回的是统一错误体（有 msg、没有 orderNo）。
        # 两种情况故意给同一答复，防止别人靠错误信息试探订单是否存在。
        if "orderNo" not in data:
            return {"ok": False, "error": data.get("msg", "未找到该订单")}

        return {"ok": True, "order": data}

    return query_order


def to_brief(order: dict) -> dict:
    """
    把 Java 返回的订单精简成模型真正需要的几个字段。

    为什么要有这一步：Java 那边一个 OrderVO 有十几个字段，包含
    receiverName / receiverPhone / receiverAddr（收货人姓名、电话、地址）
    这类隐私信息。全塞给模型有三个坏处：
      1. 白烧 token —— 搜 5 条订单就是十几倍的无用输入
      2. 模型容易被无关字段带偏，答出用户没问的东西
      3. 隐私字段一旦进了上下文，就可能被它在后面的回答里念出来
    """
    return {
        "orderNo": order.get("orderNo"),
        "status": order.get("status"),
        "statusText": order.get("statusText"),
        "payAmount": order.get("payAmount"),
        "createTime": order.get("createTime"),
        "items": [
            {
                "productId":it.get("productId"),
                "productName": it.get("productName"),
                "quantity": it.get("quantity"),
                "refundEligible": it.get("refundEligible"),
                "refundDeadline": it.get("refundDeadline"),
            }
            for it in (order.get("items") or [])
        ],
    }


def build_search_orders(user_id: int):
    """
    工厂函数：返回一个只属于当前用户的 search_user_orders 工具。

    和 build_query_order 同样的道理：userId 由闭包锁死，
    模型能决定的只有「按什么条件搜」，不能决定「搜谁的」。
    权限边界不交给模型 —— 这是整个 AI 服务最重要的一条纪律。
    """

    def search_user_orders(status: str | None = None,
                           keyword: str | None = None,
                           limit: int = 5) -> dict:
        """调 Java 的内部搜索接口。返回统一结构，方便交给模型理解。"""
        url = f"{JAVA_BASE_URL}/internal/orders/search"

        # userId 必传；status / keyword 为空时干脆不传，比传空字符串干净
        params: dict = {"userId": user_id, "limit": limit}
        if status:
            params["status"] = status
        if keyword:
            params["keyword"] = keyword

        try:
            resp = httpx.get(
                url,
                params=params,
                headers={"X-Internal-Token": INTERNAL_TOKEN},
                timeout=10,
            )
        except Exception as e:
            # 网络不通、Java 没起来等等，都归到这里，不要让它抛出去
            return {"ok": False, "error": f"调用 Java 服务失败：{e}"}

        if resp.status_code != 200:
            return {"ok": False, "error": java_error_message(resp)}

        orders = resp.json()

        # 注意：Java 那边「搜不到」返回的是空数组，不是错误体。
        # 这是刻意的设计 —— "没有符合条件的订单"是正常的业务结论，不是故障。
        # 所以这里 ok=True，让模型拿着"0 条"去回答，而不是让它以为服务坏了。
        if not orders:
            return {
                "ok": True,
                "count": 0,
                "orders": [],
                "summary": "没有符合条件的订单",
            }

        return {
            "ok": True,
            "count": len(orders),
            "orders": [to_brief(o) for o in orders],
            "summary": f"搜到 {len(orders)} 条订单",
        }

    return search_user_orders

def build_check_eligible(user_id: int):
    """
    工厂函数：退货资格校验（契约 §8.3）。
    这个接口 Java 侧不需要 userId（契约没给），保留参数是为了和其他工厂函数签名一致。
    """

    def check_refund_eligible(order_no: str, product_id: int) -> dict:
        """问 Java：这个订单里的这个商品现在还能不能退。"""
        url = f"{JAVA_BASE_URL}/internal/after-sale/check-eligible"

        try:
            resp = httpx.post(
                url,
                # ⚠️ 这是 POST + JSON body，不是 GET 的 params，别写混
                json={"orderNo": order_no, "productId": product_id},
                headers={"X-Internal-Token": INTERNAL_TOKEN},
                timeout=10,
            )
        except Exception as e:
            return {"ok": False, "error": f"调用 Java 服务失败：{e}"}

        if resp.status_code != 200:
            return {"ok": False, "error":java_error_message(resp)}

        data = resp.json()

        # ⚠️ Java 抛异常时会被全局兜底 handler 包成 {"code":0,"msg":"..."}，
        #    那种结构里没有 eligible 字段 → 用它判断是不是正常结果
        #    （注意：正常返回里也有 code，但值是 "OK"/"OVER_DEADLINE" 这种字符串，别拿它当判断依据）
        if data.get("eligible") is None:
            return {"ok": False, "error": data.get("msg", "查询退货资格失败")}

        return {
            "ok": True,
            "eligible": data.get("eligible"),
            "code": data.get("code"),
            "message": data.get("message"),
            # 这个字段决定了「AI 自己回答」还是「转人工」——是售后流程的分岔口
            "suggestManualReview": data.get("suggestManualReview"),
            "refundDeadline": data.get("refundDeadline"),
            "summary": data.get("message"),
        }

    return check_refund_eligible


def build_create_after_sale(user_id: int):
    """
    工厂函数：AI 建工单（契约 §8.4）。
    这是本项目唯一一个"有副作用"的工具 —— 调一次就真的产生一张工单。
    """

    def create_after_sale(order_no: str, product_id: int, ticket_type: str,
                          reason: str, ai_confidence: float) -> dict:
        """让 Java 建一张售后工单。"""
        url = f"{JAVA_BASE_URL}/internal/after-sale/create"

        payload = {
            "orderNo": order_no,
            "productId": product_id,
            "type": ticket_type,
            "reason": reason,
            # ⚠️ 这两个字段必须传：
            #   aiGenerated=true 才会把工单来源记成 AI（不传会记成 SYSTEM，AI 建单的证据就没了）；
            #   aiConfidence 决定初始状态 —— 低于 0.7 的工单会被转成 MANUAL_REVIEW 待人工审核
            "aiGenerated": True,
            "aiConfidence": ai_confidence,
        }

        try:
            resp = httpx.post(
                url,
                json=payload,
                headers={"X-Internal-Token": INTERNAL_TOKEN},
                timeout=10,
            )
        except Exception as e:
            return {"ok": False, "error": f"调用 Java 服务失败：{e}"}

        if resp.status_code != 200:
            return {"ok": False, "error":java_error_message(resp)}

        data = resp.json()

        if data.get("ticketNo") is None:
            return {"ok": False, "error": data.get("msg", "创建工单失败")}

        # Java 侧做了幂等：重复建单会返回已有工单号，status 不会变。
        # 所以这里不区分"新建"和"已存在"，直接看 message 就行。
        return {
            "ok": True,
            "ticketNo": data.get("ticketNo"),
            "status": data.get("status"),
            "message": data.get("message"),
            "summary": f"{data.get('message')}（工单号 {data.get('ticketNo')}）",
        }

    return create_after_sale


def build_search_after_sale(user_id: int):
    """
    工厂函数：查售后工单（契约 §8.5）。
    userId 由闭包锁死 —— 模型只能决定"查哪一张"，不能决定"查谁的"。
    """

    def search_after_sale(ticket_no: str | None = None) -> dict:
        """查当前用户的售后工单列表。"""
        url = f"{JAVA_BASE_URL}/internal/after-sale/search"

        payload: dict = {"userId": user_id}
        if ticket_no:
            payload["ticketNo"] = ticket_no

        try:
            resp = httpx.post(
                url,
                json=payload,
                headers={"X-Internal-Token": INTERNAL_TOKEN},
                timeout=10,
            )
        except Exception as e:
            return {"ok": False, "error": f"调用 Java 服务失败：{e}"}

        if resp.status_code != 200:
            return {"ok": False, "error":java_error_message(resp)}

        tickets = resp.json()

        # ⚠️ 正常返回是数组，但 Java 抛异常时会被包成 {"code":0,...} 这个 dict。
        #    直接当数组遍历会炸，所以必须先判断类型。
        if isinstance(tickets, dict):
            return {"ok": False, "error": tickets.get("msg", "查询工单失败")}

        # 和订单搜索一样：查不到是正常结论，返回空数组而不是报错
        if not tickets:
            return {"ok": True, "count": 0, "tickets": [], "summary": "没有查到售后工单"}

        brief = [
            {
                "ticketNo": t.get("ticketNo"),
                "orderNo": t.get("orderNo"),
                "type": t.get("type"),
                "status": t.get("status"),
                "statusText": t.get("statusText"),
                "productName": t.get("productName"),
                "reason": t.get("reason"),
                # 客服的处理意见 —— 用户问"我的申请怎么样了"时这句话最有用
                "handleRemark": t.get("handleRemark"),
                "createTime": t.get("createTime"),
            }
            for t in tickets
        ]

        return {
            "ok": True,
            "count": len(brief),
            "tickets": brief,
            "summary": f"查到 {len(brief)} 张售后工单",
        }

    return search_after_sale
def build_search_refund_policy():
    """
    工厂函数: 检索平台政策知识库(RAG)
    """
    def search_refund_policy(query: str)-> dict:
        """检索政策原文,返回最相关得3段和它们得出处"""
        try:
            hits=rag.search_policy(query,top_k=3)
        except Exception as e:
            #向量库没建、key没配等都在这里兜住
            #避免整个对话因为政策检索挂掉
            return {"ok": False, "error": f"政策知识库暂时不可用：{e}"}

        if not hits:
            return {
                "ok": True,
                "found": False,
                "message": "知识库里没有检索到相关条款",
                # ⚠️ 这个 summary 是给前端看的简短描述。
                #    不提供的话，主流程里那句兜底文案会把政策检索显示成
                #    「查到订单」—— 明明查的是政策，界面却写查订单，会误导人。
                "summary": "没有检索到相关条款",
            }
        return {
            "ok": True,
            "found": True,
            "count": len(hits),
            # 同上：必须有 summary，否则前端会显示「查到订单」
            "summary": f"检索到 {len(hits)} 段相关政策",
            "results": [
                {
                    #原文——让模型照着这段说
                    "content": h["text"],
                    #出处——让模型说清”出自哪篇哪节“
                    "source": f"《{h['doc_title']}》· {h['section']}",
                }
                for h in hits
            ],
            #提示语会跟着工具结果一起回到模型跟前，是“就近提醒”
            "note": "回答时请依据以上原文，并注明出处。",
        }
    return search_refund_policy



# ============================================================
# 4. 请求体格式
# ============================================================
class HistoryMessage(BaseModel):
    """一轮历史对话,java端只回传role和content两样"""
    role: str
    content: str
class ChatRequest(BaseModel):
    """对应 Java 传来的 {"userId":1,"sessionId":"S2026...","message":"..."}"""
    userId: int                     # 必填。由 Java 从 JWT 解析后注入，前端伪造不了
    sessionId: str | None = None    # 可选。传 None 表示新建会话
    message: str                    # 必填。用户问的那句话
    history: list[HistoryMessage] | None = None

# ============================================================
# 5. 接口
# ============================================================
@app.get("/health")
def health():
    """体检接口：服务是否活着、配置有没有读到。"""
    return {
        "status": "ok",
        "model": MODEL,
        "hasKey": bool(API_KEY),    # 没读到 key 时这里是 false，一眼就能定位是配置问题
        "javaBaseUrl": JAVA_BASE_URL,
    }


def intent_of(used_tools: list) -> str:
    """
    按这次实际调用的工具推断意图。

    为什么不能写死成 "ORDER_QUERY"？
    因为 v0.4 之后 AI 能干的事早就不止查订单了 —— 它还会查售后工单、建售后工单。
    全标成"订单查询"，前端那个意图标签就是错的，而且丢掉了「AI 自己建了一张单」
    这个最值得展示的信号。

    ⚠️ 判断顺序有讲究：按"信息量从大到小"排。
      · 建单 是最终动作，只要出现就是最高优先级 —— 哪怕这一轮同时调了查资格，
        用户真正关心的是"我提交成功了没有"。
      · 查售后 次之：证明了 AI 走了售后流程（而不是把它当普通订单问答）。
      · 其它有工具调用 → 归到订单查询。
      · 一个工具都没调 → 闲聊。
    """
    names = [t["name"] for t in used_tools]
    if "create_after_sale" in names:
        return "AFTER_SALE_CREATE"
    if "check_refund_eligible" in names or "search_after_sale" in names:
        return "AFTER_SALE_QUERY"
    if "search_refund_policy" in names:
        return "POLICY_QA"
    if names:
        return "ORDER_QUERY"
    return "CHAT"


@app.post("/chat")
def chat(req: ChatRequest):
    """核心接口：收一句话，让大模型答一句；需要查数据时它会自己调工具。"""
    started = time.time()

    # messages 是"对话历史"，本次请求的全部上下文都在这里
    messages = [
        {"role": "system", "content": SYSTEM_PROMPT},
    ]
    #把java传过来的几轮历史按顺序拼进去
    for h in (req.history or []):
        if h.role in ("user","assistant") and h.content:
            messages.append({"role": h.role, "content": h.content})
    messages.append({"role": "user", "content": req.message})
    # 按本次请求的 userId 现场组装工具。
    # 每多一个工具就在这里加一行,key 必须和 TOOLS 里的 name 逐字一致
    tool_impl = {
        "query_order": build_query_order(req.userId),
        "search_user_orders": build_search_orders(req.userId),
        "search_after_sale": build_search_after_sale(req.userId),
        "check_refund_eligible": build_check_eligible(req.userId),
        "create_after_sale": build_create_after_sale(req.userId),
        "search_refund_policy": build_search_refund_policy(),
    }
    used_tools = []

    # ---------- 工具调用循环 ----------
    # 模型可以连续调多轮工具，直到它决定给出最终回答。
    # ⚠️ 这里必须是循环而不是"两轮"：
    # 像"先搜订单 → 再校验退货资格 → 最后建工单"这种多步链路，
    # 如果只在第一轮带 tools，模型第二轮想继续调工具时无处可调，
    # 就会把调用意图当纯文本吐出来，直接把 <｜｜DSML｜｜invoke> 之类的
    # 原始串漏进用户可见的 answer 里。
    MAX_ROUNDS = 5
    answer = None
    for _ in range(MAX_ROUNDS):
        completion = client.chat.completions.create(
            model=MODEL,
            messages=messages,
            tools=TOOLS,          # 关键：带上工具清单，模型才知道自己有哪些本事
            temperature=0.3,
            timeout=30,
        )
        msg = completion.choices[0].message

        # 模型不再要求调工具 → 这一轮就是最终回答，收工
        if not msg.tool_calls:
            answer = msg.content or "抱歉，我这边没能理解您的问题，可以换个说法再讲一次吗？"
            break

        # 模型决定调工具了。必须把它的"调用意图"原样塞回对话历史，
        # 否则下一轮它会不知道自己在回应什么。
        messages.append(msg)

        for call in msg.tool_calls:
            name = call.function.name
            # 模型给的参数是 JSON 字符串，要解析成 dict
            args = json.loads(call.function.arguments or "{}")

            fn = tool_impl.get(name)
            t0 = time.time()
            if fn is None:
                result = {"ok": False, "error": f"未知工具：{name}"}
            else:
                result = fn(**args)     # ← 真正去查订单的一行
            cost_ms = int((time.time() - t0) * 1000)

            # 记一笔，前端要展示"这次用了什么工具、花了多久"
            used_tools.append({
                "name": name,
                "args": args,
                "status": "ok" if result.get("ok") else "error",
                "ms": cost_ms,
                # 优先用工具自己给的摘要(比如"搜到 3 条订单")，
                # query_order 没有 summary 字段,所以它还是走原来那句"查到订单",行为不变
                "result": result.get("summary")
                          or ("查到订单" if result.get("ok") else result.get("error")),
            })

            # 把工具执行结果作为一条 tool 消息追加进去。
            # tool_call_id 是"回执编号"，模型靠它把结果和刚才的调用对上号。
            messages.append({
                "role": "tool",
                "tool_call_id": call.id,
                "content": json.dumps(result, ensure_ascii=False),
            })
    else:
        # 跑满 MAX_ROUNDS 还在调工具（模型钻牛角尖了）：
        # 去掉 tools 再问最后一次，逼它输出一段人话，
        # 避免把原始调用串返回给用户
        completion = client.chat.completions.create(
            model=MODEL,
            messages=messages,
            temperature=0.3,
            timeout=30,
        )
        answer = completion.choices[0].message.content

    return {
        "answer": answer,
        # 按实际调用的工具推断，不再是写死的 ORDER_QUERY
        "intent": intent_of(used_tools),
        "confidence": 0.9 if used_tools else 0.0,
        "latencyMs": int((time.time() - started) * 1000),
        "tools": used_tools,
    }
