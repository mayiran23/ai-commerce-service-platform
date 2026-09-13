#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""
电商订单智能客服 —— 造数据脚本

设计目标：所见即所得
    preview 模式（默认）  只生成文件，绝不碰数据库
    commit  模式          读取 data_snapshot.json，原样写入 MySQL

用法（在项目根目录执行）：
    .venv\\Scripts\\python scripts\\gen_data.py                      # 预览：生成 HTML + SQL + 快照
    .venv\\Scripts\\python scripts\\gen_data.py --commit             # 真正写库（要求目标表为空）
    .venv\\Scripts\\python scripts\\gen_data.py --commit --truncate  # 先清空这 6 张表再写

产出文件：
    sql/seed_data.sql                  纯 INSERT 脚本，可直接丢进 DataGrip 跑
    scripts/data_snapshot.json         数据快照，--commit 就是照它写库
    scripts/preview/preview.html       可视化预览（浏览器打开，用来核对数据）
"""
from __future__ import annotations

import argparse
import datetime as dt
import html
import json
import os
import random
import sys

sys.stdout.reconfigure(encoding="utf-8")

BASE_DIR = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SNAPSHOT_FILE = os.path.join(BASE_DIR, "scripts", "data_snapshot.json")
SQL_FILE = os.path.join(BASE_DIR, "sql", "seed_data.sql")
HTML_FILE = os.path.join(BASE_DIR, "scripts", "preview", "preview.html")

SEED = 20260913

# BCrypt("123456")，rounds=10。登录功能做好后所有种子账号密码都是 123456。
PASSWORD_HASH = "$2a$10$wW6FiEDcYu.6H2txuF9EGuhp6jNmGR4gbg33/wB3oeqPCwYizNhC2"

# ---------------------------------------------------------------- 数据字典

CATEGORIES = ["数码", "服饰", "食品", "家居", "图书", "运动", "美妆", "母婴"]

# 商品名称、所属分类、价格区间（元）
PRODUCT_POOL = [
    # 数码
    ("无线蓝牙耳机", 1, (129, 1299)), ("机械键盘", 1, (199, 899)),
    ("4K 显示器", 1, (899, 3999)), ("便携充电宝", 1, (59, 299)),
    ("智能手环", 1, (149, 599)), ("USB-C 扩展坞", 1, (79, 499)),
    ("降噪头戴耳机", 1, (399, 2599)), ("无线鼠标", 1, (49, 399)),
    # 服饰
    ("纯棉圆领 T 恤", 2, (39, 199)), ("轻薄羽绒服", 2, (299, 1299)),
    ("直筒牛仔裤", 2, (129, 499)), ("针织开衫", 2, (159, 599)),
    ("运动速干短裤", 2, (59, 259)), ("真丝衬衫", 2, (399, 1599)),
    ("加绒卫衣", 2, (139, 459)),
    # 食品
    ("云南小粒咖啡豆", 3, (49, 189)), ("每日坚果礼盒", 3, (69, 259)),
    ("手工黑糖饼干", 3, (19, 79)), ("冷萃茶包组合", 3, (39, 149)),
    ("五常大米 5kg", 3, (59, 159)), ("原味酸奶块", 3, (25, 89)),
    # 家居
    ("记忆棉枕头", 4, (89, 399)), ("全棉四件套", 4, (199, 899)),
    ("折叠收纳箱", 4, (39, 189)), ("香薰蜡烛礼盒", 4, (79, 329)),
    ("懒人沙发", 4, (299, 1599)), ("陶瓷餐具套装", 4, (129, 699)),
    # 图书
    ("《深入理解计算机系统》", 5, (99, 149)), ("《算法导论》", 5, (89, 139)),
    ("《活着》", 5, (25, 49)), ("《纳瓦尔宝典》", 5, (39, 69)),
    ("《小王子》", 5, (19, 45)), ("考研英语真题解析", 5, (45, 99)),
    # 运动
    ("瑜伽垫加厚防滑", 6, (59, 269)), ("可调节哑铃", 6, (159, 899)),
    ("跑步运动腰包", 6, (29, 139)), ("羽毛球拍套装", 6, (129, 799)),
    ("跳绳计数款", 6, (19, 99)), ("轻量登山双肩包", 6, (189, 899)),
    # 美妆
    ("氨基酸洁面乳", 7, (49, 199)), ("烟酰胺精华", 7, (99, 499)),
    ("清爽防晒霜 SPF50", 7, (69, 259)), ("丝绒口红套装", 7, (129, 459)),
    ("补水面膜 10 片", 7, (39, 159)), ("温和卸妆油", 7, (59, 229)),
    # 母婴
    ("婴儿纸尿裤 L 码", 8, (89, 269)), ("恒温调奶器", 8, (159, 599)),
    ("儿童安全座椅", 8, (699, 2999)), ("有机高铁米粉", 8, (49, 159)),
    ("婴儿手口湿巾 80 抽", 8, (19, 69)),
]

SURNAMES = "王李张刘陈杨黄赵吴周徐孙马朱胡林郭何高罗郑梁谢宋唐许韩冯邓曹彭"
GIVEN_NAMES = ["子涵", "浩然", "欣怡", "宇轩", "雨欣", "嘉豪", "思远", "梦琪", "俊杰", "雅静",
               "泽宇", "诗涵", "文博", "雨桐", "皓宇", "婉婷", "梓豪", "静怡", "天宇", "可欣",
               "明轩", "若曦", "子豪", "佳琪", "博文", "语嫣", "晨光", "雪莹", "志强", "丽娟"]

ADDRESSES = [
    "江苏省南京市江宁区双龙大道 1698 号",
    "江苏省南京市鼓楼区中山北路 200 号",
    "江苏省南京市栖霞区仙林大道 163 号",
    "江苏省南京市浦口区浦滨路 211 号",
    "上海市浦东新区张江路 88 号",
    "浙江省杭州市西湖区文一西路 969 号",
    "广东省深圳市南山区科苑南路 2666 号",
    "北京市朝阳区望京东路 6 号",
    "四川省成都市武侯区天府大道 966 号",
    "宁夏石嘴山市大武口区朝阳东街 15 号",
]

LOGISTICS_NODES = [
    "商家已出库", "南京江宁集散中心", "杭州转运中心", "上海分拨中心",
    "广州白云中转场", "北京顺义分拣中心", "正在派送中，配送员：张师傅",
]

# 订单状态配比，合计 500
ORDER_PLAN = [
    ("PENDING_PAY", 50),
    ("PAID", 25),
    ("SHIPPED", 50),
    ("DELIVERING", 50),
    ("RECEIVED", 325),
]

STATUS_TEXT = {
    "PENDING_PAY": "待付款", "PAID": "已付款", "SHIPPED": "已发货",
    "DELIVERING": "运输中", "RECEIVED": "已签收", "CANCELLED": "已取消",
}

TABLES = ["t_user", "t_category", "t_product", "t_order", "t_order_item", "t_order_logistics"]


def ts(value: dt.datetime | None) -> str | None:
    """datetime -> 'YYYY-MM-DD HH:MM:SS'"""
    return value.strftime("%Y-%m-%d %H:%M:%S") if value else None


# ---------------------------------------------------------------- 生成逻辑

def build_dataset() -> dict:
    rng = random.Random(SEED)
    now = dt.datetime.now().replace(microsecond=0)
    data: dict = {
        "generated_at": ts(now),
        "seed": SEED,
        "t_user": [], "t_category": [], "t_product": [],
        "t_order": [], "t_order_item": [], "t_order_logistics": [],
    }

    # ---- 用户 200 条：195 USER / 3 AGENT / 2 ADMIN
    roles = ["USER"] * 195 + ["AGENT"] * 3 + ["ADMIN"] * 2
    rng.shuffle(roles)
    for i in range(1, 201):
        data["t_user"].append({
            "id": i,
            "username": f"user{i:04d}",
            "password": PASSWORD_HASH,
            "nickname": rng.choice(SURNAMES) + rng.choice(GIVEN_NAMES),
            "phone": "13" + "".join(str(rng.randint(0, 9)) for _ in range(9)),
            "role": roles[i - 1],
            "status": 1,
            "create_time": ts(now - dt.timedelta(days=rng.randint(30, 400),
                                                 hours=rng.randint(0, 23))),
        })

    # ---- 分类 8 条
    for i, name in enumerate(CATEGORIES, start=1):
        data["t_category"].append({"id": i, "name": name, "parent_id": 0})

    # ---- 商品 50 条
    for i, (name, cat_id, (lo, hi)) in enumerate(PRODUCT_POOL, start=1):
        data["t_product"].append({
            "id": i,
            "name": name,
            "category_id": cat_id,
            "price": round(rng.uniform(lo, hi) / 10) * 10 - 0.1 if hi > 500 else round(rng.uniform(lo, hi), 2),
            "description": f"{name}｜{CATEGORIES[cat_id - 1]}类目｜正品保障，支持 7 天无理由退换。",
            "image_url": f"https://cdn.example.com/product/{i:03d}.jpg",
            "status": 1,
            "create_time": ts(now - dt.timedelta(days=rng.randint(60, 500))),
        })

    products = data["t_product"]

    # ---- 订单 500 条
    statuses = []
    for status, count in ORDER_PLAN:
        statuses.extend([status] * count)
    rng.shuffle(statuses)

    order_id = 0
    item_id = 0
    logistics_id = 0

    for status in statuses:
        order_id += 1

        # 按状态倒推时间轴，保证 create < pay < ship < receive
        if status == "PENDING_PAY":
            create_time = now - dt.timedelta(hours=rng.uniform(1, 72))
            pay_time = ship_time = receive_time = None
        elif status == "PAID":
            pay_time = now - dt.timedelta(hours=rng.uniform(1, 48))
            create_time = pay_time - dt.timedelta(minutes=rng.uniform(10, 360))
            ship_time = receive_time = None
        elif status == "SHIPPED":
            ship_time = now - dt.timedelta(hours=rng.uniform(6, 72))
            pay_time = ship_time - dt.timedelta(hours=rng.uniform(6, 48))
            create_time = pay_time - dt.timedelta(minutes=rng.uniform(10, 360))
            receive_time = None
        elif status == "DELIVERING":
            ship_time = now - dt.timedelta(days=rng.uniform(2, 8))
            pay_time = ship_time - dt.timedelta(hours=rng.uniform(6, 48))
            create_time = pay_time - dt.timedelta(minutes=rng.uniform(10, 360))
            receive_time = None
        else:  # RECEIVED
            # 刻意分三档，保证"有退货资格 / 已超期 / 卡在边界"三类样本都存在
            roll = rng.random()
            if roll < 0.40:            # 有资格：签收 7 天内
                days_ago = rng.uniform(0.2, 6.5)
                bucket = "有资格"
            elif roll < 0.85:          # 已超期：签收超过 7 天
                days_ago = rng.uniform(8.0, 20.0)
                bucket = "已超期"
            else:                      # 边界：签收 7 天前后 2 小时
                days_ago = rng.uniform(6.92, 7.08)
                bucket = "边界"
            receive_time = now - dt.timedelta(days=days_ago)
            ship_time = receive_time - dt.timedelta(days=rng.uniform(1, 3))
            pay_time = ship_time - dt.timedelta(hours=rng.uniform(6, 48))
            create_time = pay_time - dt.timedelta(minutes=rng.uniform(10, 360))
            bucket = bucket  # noqa: 保留变量便于阅读

        # ---- 明细 1~3 条
        item_count = rng.randint(1, 3)
        picked = rng.sample(products, item_count)
        total = 0.0
        for p in picked:
            item_id += 1
            qty = rng.randint(1, 3)
            subtotal = round(p["price"] * qty, 2)
            total += subtotal
            deadline = receive_time + dt.timedelta(days=7) if receive_time else None
            data["t_order_item"].append({
                "id": item_id,
                "order_id": order_id,
                "order_no": None,  # 下面统一回填
                "product_id": p["id"],
                "product_name": p["name"],
                "product_price": p["price"],
                "quantity": qty,
                "subtotal": subtotal,
                "refund_deadline": ts(deadline),
            })

        total = round(total, 2)
        order_no = f"SO{create_time.strftime('%Y%m%d')}{order_id:05d}"

        data["t_order"].append({
            "id": order_id,
            "order_no": order_no,
            "user_id": rng.randint(1, 200),
            "status": status,
            "status_text": STATUS_TEXT[status],
            "total_amount": total,
            "pay_amount": total,
            "pay_time": ts(pay_time),
            "ship_time": ts(ship_time),
            "receive_time": ts(receive_time),
            "receiver_name": rng.choice(SURNAMES) + rng.choice(GIVEN_NAMES),
            "receiver_phone": "13" + "".join(str(rng.randint(0, 9)) for _ in range(9)),
            "receiver_addr": rng.choice(ADDRESSES),
            "create_time": ts(create_time),
        })

        # ---- 物流
        if status in ("SHIPPED", "DELIVERING", "RECEIVED"):
            logistics_id += 1
            if status == "SHIPPED":
                cur_status, node = "已发货", "商家已出库"
                eta = (ship_time + dt.timedelta(days=4)).date()
            elif status == "DELIVERING":
                cur_status = rng.choice(["运输中", "派送中"])
                node = rng.choice(LOGISTICS_NODES[1:6])
                eta = (now + dt.timedelta(days=rng.randint(1, 3))).date()
            else:
                cur_status, node = "已签收", "已签收，签收人：本人"
                eta = receive_time.date()
            data["t_order_logistics"].append({
                "id": logistics_id,
                "order_no": order_no,
                "track_no": "SF" + "".join(str(rng.randint(0, 9)) for _ in range(12)),
                "current_status": cur_status,
                "current_node": node,
                "estimated_arrival": eta.isoformat(),
                "update_time": ts(receive_time or ship_time or now),
            })

    # 回填明细里的 order_no
    no_by_order = {o["id"]: o["order_no"] for o in data["t_order"]}
    for it in data["t_order_item"]:
        it["order_no"] = no_by_order[it["order_id"]]

    return data


# ---------------------------------------------------------------- 统计

def compute_stats(data: dict) -> dict:
    orders = data["t_order"]
    items = data["t_order_item"]
    now = dt.datetime.now()

    by_status: dict[str, int] = {}
    for o in orders:
        by_status[o["status"]] = by_status.get(o["status"], 0) + 1

    received_ids = {o["id"] for o in orders if o["status"] == "RECEIVED"}
    eligible = expired = boundary = 0
    for it in items:
        if it["order_id"] not in received_ids or not it["refund_deadline"]:
            continue
        # 距截止时间 12 小时内 -> 边界样本
        dl = dt.datetime.strptime(it["refund_deadline"], "%Y-%m-%d %H:%M:%S")
        delta_hours = (dl - now).total_seconds() / 3600
        if abs(delta_hours) <= 12:
            boundary += 1
        elif delta_hours > 0:
            eligible += 1
        else:
            expired += 1

    # 金额校验：每单的 total_amount 必须等于明细小计之和
    sums: dict[int, float] = {}
    for it in items:
        sums[it["order_id"]] = round(sums.get(it["order_id"], 0.0) + it["subtotal"], 2)
    mismatch = [o["order_no"] for o in orders
                if abs(sums.get(o["id"], 0.0) - o["total_amount"]) > 0.001]

    return {
        "by_status": by_status,
        "eligible": eligible,
        "expired": expired,
        "boundary": boundary,
        "mismatch": mismatch,
        "received_orders": len(received_ids),
    }


# ---------------------------------------------------------------- 输出

def esc(value) -> str:
    if value is None:
        return "NULL"
    if isinstance(value, bool):
        return "1" if value else "0"
    if isinstance(value, (int, float)):
        return str(value)
    return "'" + str(value).replace("\\", "\\\\").replace("'", "''") + "'"


def write_sql(data: dict) -> None:
    cols = {
        "t_user": ["id", "username", "password", "nickname", "phone", "role", "status", "create_time"],
        "t_category": ["id", "name", "parent_id"],
        "t_product": ["id", "name", "category_id", "price", "description", "image_url", "status", "create_time"],
        "t_order": ["id", "order_no", "user_id", "status", "total_amount", "pay_amount", "pay_time",
                    "ship_time", "receive_time", "receiver_name", "receiver_phone", "receiver_addr", "create_time"],
        "t_order_item": ["id", "order_id", "order_no", "product_id", "product_name", "product_price",
                         "quantity", "refund_deadline"],
        "t_order_logistics": ["id", "order_no", "track_no", "current_status", "current_node",
                              "estimated_arrival", "update_time"],
    }
    lines = [
        "-- 自动生成，请勿手改；重跑 scripts/gen_data.py 即可覆盖",
        f"-- 生成时间：{data['generated_at']}   随机种子：{data['seed']}",
        "USE commerce_service;",
        "SET NAMES utf8mb4;",
        "START TRANSACTION;",
        "",
    ]
    for table in TABLES:
        c = cols[table]
        lines.append(f"-- ===== {table}（{len(data[table])} 条）=====")
        for row in data[table]:
            values = ", ".join(esc(row.get(k)) for k in c)
            lines.append(f"INSERT INTO {table} ({', '.join(c)}) VALUES ({values});")
        lines.append("")
    lines.append("COMMIT;")
    os.makedirs(os.path.dirname(SQL_FILE), exist_ok=True)
    with open(SQL_FILE, "w", encoding="utf-8") as f:
        f.write("\n".join(lines))


CSS = """
:root{--bg:#f7f8fa;--card:#fff;--line:#e3e6eb;--text:#1f2328;--muted:#6b7280;
--blue:#2563eb;--green:#15803d;--red:#dc2626;--amber:#b45309;--soft:#f1f5f9;}
*{box-sizing:border-box}
body{margin:0;background:var(--bg);color:var(--text);font:14px/1.6 -apple-system,"Segoe UI","Microsoft YaHei",sans-serif;}
.wrap{max-width:1180px;margin:0 auto;padding:28px 20px 60px;}
h1{font-size:24px;margin:0 0 6px;}
h2{font-size:17px;margin:32px 0 12px;padding-left:10px;border-left:4px solid var(--blue);}
.sub{color:var(--muted);margin:0 0 4px;}
.badge{display:inline-block;background:#fef3c7;color:var(--amber);border:1px solid #fcd34d;
padding:3px 10px;border-radius:999px;font-size:12px;font-weight:600;margin-left:8px;vertical-align:3px;}
.cards{display:grid;grid-template-columns:repeat(auto-fit,minmax(140px,1fr));gap:12px;margin-top:14px;}
.card{background:var(--card);border:1px solid var(--line);border-radius:10px;padding:14px 16px;}
.card .k{color:var(--muted);font-size:12px;}
.card .v{font-size:22px;font-weight:700;margin-top:2px;}
table{width:100%;border-collapse:collapse;background:var(--card);border:1px solid var(--line);
border-radius:10px;overflow:hidden;font-size:13px;}
th,td{padding:8px 12px;border-bottom:1px solid var(--line);text-align:left;}
th{background:var(--soft);font-weight:600;color:#374151;white-space:nowrap;}
tr:last-child td{border-bottom:none;}
.ok{color:var(--green);font-weight:700;}
.bad{color:var(--red);font-weight:700;}
.warn{color:var(--amber);font-weight:700;}
.tag{display:inline-block;padding:1px 8px;border-radius:999px;font-size:12px;border:1px solid var(--line);background:var(--soft);}
.t-a{color:var(--green);border-color:#bbf7d0;background:#f0fdf4;}
.t-b{color:var(--red);border-color:#fecaca;background:#fef2f2;}
.t-c{color:var(--amber);border-color:#fde68a;background:#fffbeb;}
details{background:var(--card);border:1px solid var(--line);border-radius:10px;margin-bottom:6px;}
summary{padding:9px 12px;cursor:pointer;font-size:13px;display:flex;gap:10px;align-items:center;}
summary:hover{background:var(--soft);}
summary::marker{color:var(--muted);}
.inner{padding:0 12px 12px;border-top:1px solid var(--line);background:#fcfcfd;}
.inner table{margin-top:10px;box-shadow:none;}
.mono{font-family:ui-monospace,Consolas,monospace;font-size:12.5px;}
.muted{color:var(--muted);font-size:12.5px;}
#filter{width:100%;padding:9px 12px;border:1px solid var(--line);border-radius:8px;
margin-bottom:10px;font-size:13px;background:var(--card);}
.note{background:#eff6ff;border:1px solid #bfdbfe;color:#1e3a8a;border-radius:10px;
padding:12px 16px;font-size:13px;}
.scroll{max-height:620px;overflow:auto;}
"""

JS = """
function filterRows(){
  var q=document.getElementById('filter').value.trim().toLowerCase();
  var rows=document.querySelectorAll('#orderList details');
  rows.forEach(function(r){
    var hit = !q || (r.dataset.k||'').toLowerCase().indexOf(q)>=0;
    r.style.display = hit ? '' : 'none';
  });
  document.getElementById('hitCount').textContent =
    '当前显示 ' + Array.prototype.filter.call(rows, function(r){return r.style.display!=='none';}).length + ' / ' + rows.length + ' 单';
}
"""


def write_html(data: dict, stats: dict) -> None:
    orders = data["t_order"]
    items = data["t_order_item"]
    logistics = {l["order_no"]: l for l in data["t_order_logistics"]}
    items_by_order: dict[int, list] = {}
    for it in items:
        items_by_order.setdefault(it["order_id"], []).append(it)

    checks = [
        ("用户 200 条", len(data["t_user"]) == 200, f"实际 {len(data['t_user'])}"),
        ("分类 8 条", len(data["t_category"]) == 8, f"实际 {len(data['t_category'])}"),
        ("商品 50 条", len(data["t_product"]) == 50, f"实际 {len(data['t_product'])}"),
        ("订单 500 条", len(orders) == 500, f"实际 {len(orders)}"),
        ("有退货资格的订单明细 > 0", stats["eligible"] > 0, f"{stats['eligible']} 条明细"),
        ("已超期的订单明细 > 0", stats["expired"] > 0, f"{stats['expired']} 条明细"),
        ("total_amount 与明细对得上", len(stats["mismatch"]) == 0,
         "全部一致" if not stats["mismatch"] else f"{len(stats['mismatch'])} 单不一致"),
    ]

    p = []
    p.append('<!DOCTYPE html><html lang="zh-CN"><head><meta charset="utf-8">')
    p.append('<meta name="viewport" content="width=device-width,initial-scale=1">')
    p.append('<title>造数据预览 - 电商订单智能客服</title>')
    p.append(f"<style>{CSS}</style></head><body><div class='wrap'>")
    p.append("<h1>电商订单智能客服 · 造数据预览<span class='badge'>预览模式 · 未写入数据库</span></h1>")
    p.append(f"<p class='sub'>生成时间 {data['generated_at']}　·　随机种子 {data['seed']}　"
             f"·　共 {sum(len(data[t]) for t in TABLES)} 行</p>")
    p.append("<div class='note'>这份页面就是即将写进数据库的完整数据。<b>确认无误后</b>执行 "
             "<span class='mono'>.venv\\Scripts\\python scripts\\gen_data.py --commit</span> 才会真正落库。"
             "写库用的是同一份快照文件，所以入库结果与本页完全一致。</div>")

    p.append("<h2>一、验收清单</h2><table><tr><th style='width:46%'>检查项</th><th style='width:14%'>结果</th><th>明细</th></tr>")
    for name, ok, detail in checks:
        mark = "<span class='ok'>通过</span>" if ok else "<span class='bad'>不通过</span>"
        p.append(f"<tr><td>{html.escape(name)}</td><td>{mark}</td><td class='muted'>{html.escape(detail)}</td></tr>")
    p.append("</table>")

    p.append("<h2>二、总量与分布</h2><div class='cards'>")
    for label, value in [("用户", len(data["t_user"])), ("分类", len(data["t_category"])),
                         ("商品", len(data["t_product"])), ("订单", len(orders)),
                         ("订单明细", len(items)), ("物流记录", len(data["t_order_logistics"]))]:
        p.append(f"<div class='card'><div class='k'>{label}</div><div class='v'>{value}</div></div>")
    p.append("</div>")

    p.append("<h2>三、订单状态分布</h2><table><tr><th>状态</th><th>含义</th><th>单数</th><th>占比</th></tr>")
    for status, _ in ORDER_PLAN:
        n = stats["by_status"].get(status, 0)
        p.append(f"<tr><td class='mono'>{status}</td><td>{STATUS_TEXT[status]}</td>"
                 f"<td>{n}</td><td>{n / len(orders) * 100:.1f}%</td></tr>")
    p.append("</table>")

    p.append("<h2>四、退货资格分布（演示「人机协同」的关键）</h2>")
    p.append(f"<p class='sub'>已签收订单 {stats['received_orders']} 单。退货资格 = 签收时间 + 7 天，"
             "由 Java 侧计算，AI 只负责读结果。</p>")
    p.append("<table><tr><th>分档</th><th>说明</th><th>明细条数</th><th>AI 应该怎么做</th></tr>")
    p.append(f"<tr><td><span class='tag t-a'>有资格</span></td><td>距截止时间还早</td><td>{stats['eligible']}</td>"
             "<td>直接建工单，走自动流程</td></tr>")
    p.append(f"<tr><td><span class='tag t-c'>边界</span></td><td>截止时间前后 12 小时内</td><td>{stats['boundary']}</td>"
             "<td>用来自测时间判断有没有写错</td></tr>")
    p.append(f"<tr><td><span class='tag t-b'>已超期</span></td><td>签收超过 7 天</td><td>{stats['expired']}</td>"
             "<td>转人工 MANUAL_REVIEW，不能自动承诺退款</td></tr>")
    p.append("</table>")

    # ---- 订单明细
    p.append("<h2>五、订单全量数据（500 单，点开看明细与物流）</h2>")
    p.append("<input id='filter' placeholder='输入订单号 / 用户ID / 状态 筛选，例如 SO2026 或 RECEIVED' oninput='filterRows()'>")
    p.append(f"<p class='muted' id='hitCount'>当前显示 {len(orders)} / {len(orders)} 单</p>")
    p.append("<div class='scroll' id='orderList'>")
    for o in orders:
        its = items_by_order.get(o["id"], [])
        lg = logistics.get(o["order_no"])
        cls = {"RECEIVED": "t-a", "DELIVERING": "t-c"}.get(o["status"], "")
        tag = f"<span class='tag {cls}'>{o['status_text']}</span>"
        key = f"{o['order_no']} u{o['user_id']} {o['status']}"
        p.append(f"<details data-k=\"{html.escape(key)}\"><summary><b class='mono'>{o['order_no']}</b>"
                 f"{tag}<span class='muted'>用户 {o['user_id']}　{o['receiver_name']}　"
                 f"¥{o['pay_amount']:.2f}　{len(its)} 件　下单 {o['create_time'][:16]}</span></summary>")
        p.append("<div class='inner'>")
        p.append(f"<p class='muted'>收货：{html.escape(o['receiver_name'])} {html.escape(o['receiver_phone'])}"
                 f"　{html.escape(o['receiver_addr'])}</p>")
        p.append(f"<p class='muted'>付款 {o['pay_time'] or '—'}　发货 {o['ship_time'] or '—'}　签收 {o['receive_time'] or '—'}</p>")
        p.append("<table><tr><th>商品</th><th>单价</th><th>数量</th><th>小计</th><th>退货截止</th><th>当前是否有资格</th></tr>")
        for it in its:
            if it["refund_deadline"]:
                dl = dt.datetime.strptime(it["refund_deadline"], "%Y-%m-%d %H:%M:%S")
                elig = dl > dt.datetime.now()
                flag = "<span class='ok'>有资格</span>" if elig else "<span class='bad'>已超期</span>"
                deadline_txt = it["refund_deadline"]
            else:
                flag, deadline_txt = "<span class='muted'>—</span>", "—"
            p.append(f"<tr><td>{html.escape(it['product_name'])}</td><td>¥{it['product_price']:.2f}</td>"
                     f"<td>{it['quantity']}</td><td>¥{it['subtotal']:.2f}</td>"
                     f"<td class='mono'>{deadline_txt}</td><td>{flag}</td></tr>")
        p.append("</table>")
        if lg:
            p.append(f"<p class='muted'>物流：<span class='mono'>{lg['track_no']}</span>　"
                     f"{lg['current_status']}　{html.escape(lg['current_node'])}　"
                     f"预计到达 {lg['estimated_arrival']}</p>")
        p.append("</div></details>")
    p.append("</div>")

    # ---- 商品
    p.append("<h2>六、商品（50 条）</h2><table><tr><th>ID</th><th>名称</th><th>分类</th><th>价格</th><th>状态</th></tr>")
    for pr in data["t_product"]:
        p.append(f"<tr><td>{pr['id']}</td><td>{html.escape(pr['name'])}</td>"
                 f"<td>{CATEGORIES[pr['category_id'] - 1]}</td><td>¥{pr['price']:.2f}</td>"
                 f"<td>{'上架' if pr['status'] else '下架'}</td></tr>")
    p.append("</table>")

    # ---- 用户
    role_names = {"USER": "普通用户", "AGENT": "客服", "ADMIN": "管理员"}
    p.append("<h2>七、用户（200 条，密码统一 123456）</h2>")
    p.append("<div class='scroll'><table><tr><th>ID</th><th>登录名</th><th>昵称</th><th>手机号</th><th>角色</th></tr>")
    for u in data["t_user"]:
        role = u["role"]
        mark = f"<b>{role_names[role]}</b>" if role != "USER" else "普通用户"
        p.append(f"<tr><td>{u['id']}</td><td class='mono'>{u['username']}</td><td>{html.escape(u['nickname'])}</td>"
                 f"<td class='mono'>{u['phone']}</td><td>{mark}</td></tr>")
    p.append("</table></div>")

    p.append("<h2>八、接下来会做什么</h2><div class='note'>"
             "① 你确认这份数据 → ② 执行 <span class='mono'>--commit</span> 落库 → "
             "③ 在 DataGrip 里跑验收 SQL 复核 → ④ 提交 <span class='mono'>seed_data.sql</span> 与脚本到 Git，"
             "然后把 <span class='mono'>t_after_sale</span> 等表留给 AI 真实产生工单。</div>")

    p.append(f"<script>{JS}</script>")
    p.append("</div></body></html>")

    os.makedirs(os.path.dirname(HTML_FILE), exist_ok=True)
    with open(HTML_FILE, "w", encoding="utf-8") as f:
        f.write("\n".join(p))


# ---------------------------------------------------------------- 写库

def load_db_conf() -> dict:
    props = os.path.join(BASE_DIR, "src", "main", "resources", "application.properties")
    conf: dict[str, str] = {}
    if os.path.exists(props):
        with open(props, encoding="utf-8") as f:
            for line in f:
                line = line.strip()
                if not line or line.startswith("#") or "=" not in line:
                    continue
                k, v = line.split("=", 1)
                conf[k.strip()] = v.strip()
    url = conf.get("spring.datasource.url", "")
    host, port, db = "localhost", 3306, "commerce_service"
    if url.startswith("jdbc:mysql://"):
        body = url[len("jdbc:mysql://"):].split("?")[0]
        hostport, _, dbname = body.partition("/")
        host, _, port_s = hostport.partition(":")
        port = int(port_s or 3306)
        db = dbname or db
    return {
        "host": os.getenv("DB_HOST", host),
        "port": int(os.getenv("DB_PORT", port)),
        "user": os.getenv("DB_USER", conf.get("spring.datasource.username", "root")),
        "password": os.getenv("DB_PASSWORD", conf.get("spring.datasource.password", "")),
        "database": os.getenv("DB_NAME", db),
    }


COLUMN_MAP = {
    "t_user": ["id", "username", "password", "nickname", "phone", "role", "status", "create_time"],
    "t_category": ["id", "name", "parent_id"],
    "t_product": ["id", "name", "category_id", "price", "description", "image_url", "status", "create_time"],
    "t_order": ["id", "order_no", "user_id", "status", "total_amount", "pay_amount", "pay_time",
                "ship_time", "receive_time", "receiver_name", "receiver_phone", "receiver_addr", "create_time"],
    "t_order_item": ["id", "order_id", "order_no", "product_id", "product_name", "product_price",
                     "quantity", "refund_deadline"],
    "t_order_logistics": ["id", "order_no", "track_no", "current_status", "current_node",
                          "estimated_arrival", "update_time"],
}


def commit(data: dict, truncate: bool) -> None:
    import pymysql

    conf = load_db_conf()
    print(f"→ 连接 {conf['user']}@{conf['host']}:{conf['port']}/{conf['database']}")
    conn = pymysql.connect(charset="utf8mb4", autocommit=False, **conf)
    try:
        with conn.cursor() as cur:
            counts = {}
            for t in TABLES:
                cur.execute(f"SELECT COUNT(*) FROM {t}")
                counts[t] = cur.fetchone()[0]
            dirty = {t: n for t, n in counts.items() if n > 0}
            if dirty and not truncate:
                print("✗ 以下表已有数据，为避免主键冲突已中止：")
                for t, n in dirty.items():
                    print(f"    {t}: {n} 行")
                print("  想覆盖请加 --truncate（会先清空这 6 张表）")
                raise SystemExit(1)
            if truncate:
                for t in TABLES:
                    cur.execute(f"TRUNCATE TABLE {t}")
                print("→ 已清空 6 张表")

            for t in TABLES:
                cols = COLUMN_MAP[t]
                rows = [[r.get(c) for c in cols] for r in data[t]]
                sql = f"INSERT INTO {t} ({', '.join(cols)}) VALUES ({', '.join(['%s'] * len(cols))})"
                cur.executemany(sql, rows)
                print(f"    {t}: 写入 {len(rows)} 行")
        conn.commit()
        print("✓ 全部写入成功，已提交事务")
    except Exception:
        conn.rollback()
        print("✗ 出错，已回滚，数据库保持原样")
        raise
    finally:
        conn.close()


def verify() -> None:
    import pymysql

    conf = load_db_config_safe()
    conn = pymysql.connect(charset="utf8mb4", **conf)
    try:
        with conn.cursor() as cur:
            print("\n—— 落库后复核 ——")
            for t in TABLES:
                cur.execute(f"SELECT COUNT(*) FROM {t}")
                print(f"  {t}: {cur.fetchone()[0]} 行")
            cur.execute("SELECT status, COUNT(*) FROM t_order GROUP BY status ORDER BY status")
            print("  订单状态分布:", ", ".join(f"{s}={n}" for s, n in cur.fetchall()))
            cur.execute("""SELECT COUNT(*) FROM t_order_item i JOIN t_order o ON i.order_id=o.id
                           WHERE o.status='RECEIVED' AND i.refund_deadline > NOW()""")
            print("  有退货资格明细:", cur.fetchone()[0])
            cur.execute("""SELECT COUNT(*) FROM t_order_item i JOIN t_order o ON i.order_id=o.id
                           WHERE o.status='RECEIVED' AND i.refund_deadline < NOW()""")
            print("  已超期明细:", cur.fetchone()[0])
            cur.execute("""SELECT COUNT(*) FROM t_order o JOIN
                           (SELECT order_id, ROUND(SUM(product_price*quantity),2) s FROM t_order_item GROUP BY order_id) x
                           ON o.id=x.order_id WHERE ABS(o.total_amount-x.s)>0.01""")
            print("  金额对不上的订单:", cur.fetchone()[0])
    finally:
        conn.close()


def load_db_config_safe() -> dict:
    return load_db_conf()


# ---------------------------------------------------------------- 入口

def main() -> None:
    ap = argparse.ArgumentParser(description="电商订单智能客服造数据")
    ap.add_argument("--commit", action="store_true", help="真正写入数据库")
    ap.add_argument("--truncate", action="store_true", help="写库前先清空这 6 张表")
    ap.add_argument("--regenerate", action="store_true", help="忽略已有快照，重新生成")
    args = ap.parse_args()

    if args.commit:
        if not os.path.exists(SNAPSHOT_FILE) or args.regenerate:
            print("→ 没有可用快照，先重新生成…")
            data = build_dataset()
            with open(SNAPSHOT_FILE, "w", encoding="utf-8") as f:
                json.dump(data, f, ensure_ascii=False, indent=1)
        else:
            with open(SNAPSHOT_FILE, encoding="utf-8") as f:
                data = json.load(f)
            print(f"→ 使用快照 {SNAPSHOT_FILE}（生成于 {data['generated_at']}）")
        commit(data, args.truncate)
        verify()
        return

    # 预览模式
    data = build_dataset()
    stats = compute_stats(data)
    os.makedirs(os.path.dirname(SNAPSHOT_FILE), exist_ok=True)
    with open(SNAPSHOT_FILE, "w", encoding="utf-8") as f:
        json.dump(data, f, ensure_ascii=False, indent=1)
    write_sql(data)
    write_html(data, stats)

    print("预览生成完毕（数据库未做任何改动）")
    print(f"  快照   {SNAPSHOT_FILE}")
    print(f"  SQL    {SQL_FILE}")
    print(f"  预览   {HTML_FILE}")
    for t in TABLES:
        print(f"  {t}: {len(data[t])} 行")
    print(f"  有退货资格明细 {stats['eligible']} 条 / 边界 {stats['boundary']} 条 / 已超期 {stats['expired']} 条")
    print(f"  金额对不上的订单：{len(stats['mismatch'])} 单")
    print("\n确认无误后执行：.venv\\Scripts\\python scripts\\gen_data.py --commit")


if __name__ == "__main__":
    main()
