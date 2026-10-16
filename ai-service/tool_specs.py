"""
工具说明书 —— 给【模型】看的 JSON Schema，不是给程序看的。

【和其他文件的分工】
  tool_specs.py  → 工具的"说明书"：告诉模型有哪些工具、什么时候用、参数是什么
  main.py        → 工具的"实现"：build_* 系列函数，真正去调 Java 内部接口

⚠️ 这里的每个 name 必须和 main.py 里的实现逐字对应：
     query_order          ↔ build_query_order
     search_user_orders   ↔ build_search_orders
     search_after_sale    ↔ build_search_after_sale
     check_refund_eligible↔ build_check_eligible
     create_after_sale    ↔ build_create_after_sale
     search_refund_policy ↔ build_search_refund_policy
   改工具名时两处都要改 —— 漏一处，模型会去调一个不存在的工具。

⚠️ description 决定模型"什么时候想起来用这个工具"，比 parameters 更值钱。
   写 description 时要说清两件事：①什么场景用它 ②和邻近工具的分工边界。
"""

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
    },
    {
        "type": "function",
        "function": {
            "name": "search_user_orders",
            # description 决定模型「什么时候想起来用它」——这是整个工具定义里最值钱的一行。
            # 必须写清两件事：①什么场景用它 ②和 query_order 的分工边界
            "description": (
                "按条件搜索当前用户的订单列表。当用户没有给出具体订单号、"
                "但想了解自己的订单情况时调用，比如「我最近买了什么」"
                "「我前几天买的口红到哪了」「我有哪些还没收货的订单」。"
                "可以按订单状态筛选，也可以按商品名关键词搜索。"
                "如果用户已经给出了具体订单号，请改用 query_order。"
            ),
            "parameters": {
                "type": "object",
                "properties": {
                    "status": {
                        "type": "string",
                        # ⚠️ 枚举值必须和 Java 的 OrderStatus 逐字一致,写错会静默失败:
                        # 模型传了错值 -> Java 查不到 -> 返回空数组不报错 -> AI 回答"您没有相关订单"
                        "description": (
                            "订单状态筛选，可选。只能传这几个值之一："
                            "PENDING_PAY(待付款)、PAID(已付款)、SHIPPED(已发货)、"
                            "DELIVERING(运输中)、RECEIVED(已签收)、CANCELLED(已取消)。"
                            "不确定用户的意图时不要传，不要自己编状态值。"
                        ),
                    },
                    "keyword": {
                        "type": "string",
                        "description": (
                            "商品名关键词，可选。比如用户说「口红」就传「口红」。"
                            "只能匹配商品名，不要用它传订单号或用户名。"
                        ),
                    },
                    "limit": {
                        "type": "integer",
                        "description": "最多返回几条，默认 5，最大 20。",
                    },
                },
                # 三个参数全都可以不传(用户说"我最近买了什么"时没有任何筛选条件),所以是空数组。
                # 写成必填会让模型在该用的时候放弃调用、反过来找用户要订单号。
                "required": [],
            },
        },
    }    ,{
        "type": "function",
        "function": {
            "name": "search_after_sale",
            "description": (
                "查询当前用户的售后工单列表（退货/换货/维修的申请记录）。"
                "当用户问「我上次那个退货申请怎么样了」「我提交的换货单处理了吗」"
                "「我的售后有进展吗」这类问题时调用。"
                "不传 ticket_no 就返回该用户最近的工单；"
                "如果用户明确报出了一个工单号，才传 ticket_no 精确查那一张。"
            ),
            "parameters": {
                "type": "object",
                "properties": {
                    "ticket_no": {
                        "type": "string",
                        "description": "工单号，形如 AS20260927001。可选，用户明确报出工单号时才传。",
                    },
                },
                # 不传也能查到"我最近的售后"，所以是空数组
                "required": [],
            },
        },
    },
    {
        "type": "function",
        "function": {
            "name": "check_refund_eligible",
            # 这行的关键是写清「只读」和「建单要用另一个工具」，不然模型可能拿它当建单用
            "description": (
                "查询某个订单中的某个商品现在是否还在无理由退货期内。"
                "当用户表达退货/退款意向时，必须先调用它确认资格，"
                "不要自己计算 7 天期限、也不要凭订单状态猜。"
                "这是只读操作，不会产生任何工单。"
                "它只回答「能不能退」，真正建工单要用 create_after_sale。"
            ),
            "parameters": {
                "type": "object",
                "properties": {
                    "order_no": {
                        "type": "string",
                        "description": "订单号，形如 SO2026090900353。",
                    },
                    "product_id": {
                        "type": "integer",
                        "description": (
                            "商品ID，必须是这张订单里的商品。"
                            "从 query_order 或 search_user_orders 返回结果的 items 里取 productId。"
                        ),
                    },
                },
                "required": ["order_no", "product_id"],
            },
        },
    },
    {
        "type": "function",
        "function": {
            "name": "create_after_sale",
            "description": (
                "创建一张售后工单（退货退款/换货/维修）。"
                "【重要】这是写操作：调用它会在系统里真的生成一张工单。"
                "只有当用户已经明确表达「我要退货/换货/维修」「帮我申请」之后才能调用；"
                "如果用户只是在问「我这个还能退吗」，请先用 check_refund_eligible 回答他，不要建单。"
                "调用前应该已用 check_refund_eligible 确认过资格。"
                "同一个订单的同一个商品重复调用不会产生重复工单，系统会自动返回已有工单号。"
            ),
            "parameters": {
                "type": "object",
                "properties": {
                    "order_no": {
                        "type": "string",
                        "description": "订单号，形如 SO2026090900353。",
                    },
                    "product_id": {
                        "type": "integer",
                        "description": "要申请售后的商品ID，必须是这张订单里的商品。",
                    },
                    "ticket_type": {
                        "type": "string",
                        "description": (
                            "工单类型，只能传这三个值之一："
                            "REFUND(退货退款)、EXCHANGE(换货)、REPAIR(维修)。"
                            "用户说「退」用 REFUND，说「换」用 EXCHANGE，说「修」用 REPAIR。"
                        ),
                    },
                    "reason": {
                        "type": "string",
                        "description": "申请原因，用一句话转述用户的诉求，比如「用户说口红颜色和图片差太多」。",
                    },
                    "ai_confidence": {
                        "type": "number",
                        "description": (
                            "你对这次判断的把握程度，0 到 1 之间的小数。"
                            "用户诉求明确、表述清楚 → 给 0.85 以上；"
                            "用户表述含糊、你只能靠猜 → 给 0.5 以下。"
                            "低于 0.7 的工单会被系统转成人工审核，所以不要一律给高分。"
                        ),
                    },
                },
                "required": ["order_no", "product_id", "ticket_type", "reason", "ai_confidence"],
            },
        },
    },{
        "type": "function",
        "function": {
            "name": "search_refund_policy",
            "description": (
                "检索平台的政策规定原文，用来回答「规定是什么」这一类问题，"
                "只要用户的问题涉及平台规则,流程,时限,费用"
                "哪怕他没说[政策]两个字,表述很口语很模糊,都应该调用本工具确认,"
                "一定记住,不要按照常识来回答用户问题"
                "比如「七天无理由怎么算」「退货运费谁承担」「发票怎么开」"
                "「拆开包装算不算使用」「退款多久到账」,[我当时垫付的邮费怎么办]。"
                "[钱什么时候能回来],[东西坏了能修吗],[想换个尺码怎么办]"
                "它返回政策原文片段和出处，不涉及任何具体订单。"
                "⚠️ 如果用户问的是「我这一单/我这个商品」能不能怎么样，"
                "那是关于他自己的订单，请改用 check_refund_eligible。"
            ),
            "parameters": {
                "type": "object",
                "properties": {
                    "query": {
                        "type": "string",
                        "description": (
                            "要检索的政策问题.用用户的原话即可;"
                            "如果用户说得很省略,可以改写成一句完成得问句."
                        ),
                    },
                },
                "required": ["query"],
            },
        },
    }

]
