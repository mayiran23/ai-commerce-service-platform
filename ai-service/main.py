"""
电商订单智能客服 —— Python AI 服务
v0.2：接上第一个工具 query_order，让 AI 能查到真实订单

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

# 系统提示词 = 给模型设定的角色和规矩，每次对话都会带上。
# 除了"防幻觉"，v0.2 新增了两条关于工具的规矩（实测有效，它因此不会瞎猜订单号）。
SYSTEM_PROMPT = (
    "你是一名电商平台的智能客服助手，负责解答订单、物流、退换货相关问题。"
    "回答要简洁、口语化，不要编造订单信息。"
    "你可以使用 query_order 工具查询订单，但必须先拿到订单号才能调用；"
    "如果用户没有提供订单号，就礼貌地请他提供，不要瞎猜订单号。"
    "工具查不到数据时，如实告知用户，不要编造。"
)

app = FastAPI(title="AI Commerce Service", version="0.2.0")


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
            return {"ok": False, "error": f"Java 服务返回 HTTP {resp.status_code}"}

        data = resp.json()

        # Java 侧"查不到"和"不是你的订单"返回的是统一错误体（有 msg、没有 orderNo）。
        # 两种情况故意给同一答复，防止别人靠错误信息试探订单是否存在。
        if "orderNo" not in data:
            return {"ok": False, "error": data.get("msg", "未找到该订单")}

        return {"ok": True, "order": data}

    return query_order


# ============================================================
# 3. 工具说明书（给模型看的，不是给程序看的）
# ============================================================
TOOLS = [
    {
        "type": "function",
        "function": {
            "name": "query_order",
            # description 决定了模型"什么时候想起来用这个工具"，要写清楚用途和前提
            "description": (
                "根据订单号查询订单的详细信息，包括订单状态、支付金额、"
                "物流轨迹和商品明细。当用户询问某个具体订单的进度、物流、"
                "金额或商品时调用。必须先获得订单号才能调用。"
            ),
            # parameters 用 JSON Schema 描述参数，模型据此生成调用参数
            "parameters": {
                "type": "object",
                "properties": {
                    "order_no": {
                        "type": "string",
                        "description": "订单号，形如 SO2026090900353",
                    }
                },
                "required": ["order_no"],
            },
        },
    }
]


# ============================================================
# 4. 请求体格式
# ============================================================
class ChatRequest(BaseModel):
    """对应 Java 传来的 {"userId":1,"sessionId":"S2026...","message":"..."}"""
    userId: int                     # 必填。由 Java 从 JWT 解析后注入，前端伪造不了
    sessionId: str | None = None    # 可选。传 None 表示新建会话
    message: str                    # 必填。用户问的那句话


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


@app.post("/chat")
def chat(req: ChatRequest):
    """核心接口：收一句话，让大模型答一句；需要查数据时它会自己调工具。"""
    started = time.time()

    # messages 是"对话历史"，本次请求的全部上下文都在这里
    messages = [
        {"role": "system", "content": SYSTEM_PROMPT},
        {"role": "user", "content": req.message},
    ]

    # 按本次请求的 userId 现场组装工具
    tool_impl = {"query_order": build_query_order(req.userId)}
    used_tools = []

    # ---------- 第一轮：把问题 + 工具清单发给模型，让它自己决定要不要调工具 ----------
    completion = client.chat.completions.create(
        model=MODEL,
        messages=messages,
        tools=TOOLS,          # 关键：带上工具清单，模型才知道自己有哪些本事
        temperature=0.3,
        timeout=30,
    )
    msg = completion.choices[0].message

    if msg.tool_calls:
        # 模型决定调工具了。必须把它的"调用意图"原样塞回对话历史，
        # 否则第二轮它会不知道自己在回应什么。
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
                "result": "查到订单" if result.get("ok") else result.get("error"),
            })

            # 把工具执行结果作为一条 tool 消息追加进去。
            # tool_call_id 是"回执编号"，模型靠它把结果和刚才的调用对上号。
            messages.append({
                "role": "tool",
                "tool_call_id": call.id,
                "content": json.dumps(result, ensure_ascii=False),
            })

        # ---------- 第二轮：把工具查到的数据交给模型，让它组织成人话 ----------
        # 这一轮不再传 tools，避免模型反复调用陷入死循环
        completion2 = client.chat.completions.create(
            model=MODEL,
            messages=messages,
            temperature=0.3,
            timeout=30,
        )
        answer = completion2.choices[0].message.content
    else:
        # 模型觉得不需要工具（比如用户在闲聊），直接用它的回答
        answer = msg.content

    return {
        "answer": answer,
        "intent": "ORDER_QUERY" if used_tools else "UNKNOWN",
        "confidence": 0.9 if used_tools else 0.0,
        "latencyMs": int((time.time() - started) * 1000),
        "tools": used_tools,
    }
