# -*- coding: utf-8 -*-
"""
从 MySQL 抽取真实订单数据，生成前端离线 Mock 数据。

为什么需要它：
  后端接口还没写完，前端页面要能先打开看。这个脚本把库里的真数据
  （订单号、金额、商品名、物流单号全是真的）导出成一份 JS 文件，
  前端在 mock 模式下直接读它。

用法：
  .venv\\Scripts\\python scripts\\gen_mock.py               # 推荐
  .venv\\Scripts\\python scripts\\gen_mock.py --no-rebase   # 保留库里原始时间

时间平移（--no-rebase 可关）解决两件事：
  1. 库里签收时间是造数据时写死的日期，放几天就全过期了，
     页面上「有资格 / 即将到期」两档会永远看不到。
  2. 平移量按"全库最晚时间"算，保证平移后**没有任何时间是未来时间**
     （否则订单列表会出现"下单时间是明天"这种一眼假的数据）。
"""
import sys, os, json, argparse, datetime
import pymysql

DB = dict(host='localhost', port=3306, user='root', password='15660872291',
          database='commerce_service', charset='utf8mb4')

STATUS_TEXT = {
    'PENDING_PAY': '待付款', 'PAID': '已付款', 'SHIPPED': '已发货',
    'DELIVERING': '运输中', 'RECEIVED': '已签收', 'CANCELLED': '已取消',
}
DT_FIELDS = ['payTime', 'shipTime', 'receiveTime', 'createTime', 'updateTime']

ORDERS_PER_BUCKET = 8      # 非「已签收」状态每个抽几单
RECV_SPREAD = 15           # 「已签收」跨时间均匀抽几单
RECV_SOON_CLUSTER = 6      # 「即将到期」那一簇抽几单
REFUND_DAYS = 7            # 退货资格 = 签收后 7 天（和造数据脚本保持一致）

# 「即将到期」的判定窗口（小时）。必须和前端 ui.js 里的语义一致：
#   截止 - 现在 > 24h      → 有资格
#   0 < 截止 - 现在 ≤ 24h  → 即将到期
#   截止 ≤ 现在             → 已超期
SOON_HOURS = 24


def to_iso(v):
    return v.strftime('%Y-%m-%d %H:%M:%S') if isinstance(v, datetime.datetime) else None


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--no-rebase', action='store_true', help='不平移时间，保留库中原值')
    ap.add_argument('--out', default=None, help='输出路径')
    args = ap.parse_args()

    root = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    out = args.out or os.path.join(root, 'frontend', 'assets', 'mock_data.js')

    c = pymysql.connect(**DB)
    cur = c.cursor(pymysql.cursors.DictCursor)

    # ============ 0. 先算时间平移量 ============
    # 用"全库最晚时间"当基准，保证平移后所有时间都不超过 (现在 - 1 小时)
    cur.execute("""
        SELECT MAX(mx) AS mx FROM (
          SELECT MAX(create_time)  AS mx FROM t_order
          UNION ALL SELECT MAX(pay_time)     FROM t_order
          UNION ALL SELECT MAX(ship_time)    FROM t_order
          UNION ALL SELECT MAX(receive_time) FROM t_order
          UNION ALL SELECT MAX(update_time)  FROM t_order
        ) t
    """)
    max_ts = cur.fetchone()['mx']
    if isinstance(max_ts, str):                      # 驱动返回字符串时兜一下
        max_ts = datetime.datetime.strptime(max_ts, '%Y-%m-%d %H:%M:%S')
    now = datetime.datetime.now()
    shift = 0
    if not args.no_rebase and max_ts:
        shift = int(((now - datetime.timedelta(hours=1)) - max_ts).total_seconds())
    delta = datetime.timedelta(seconds=shift)

    # 平移后能落进「即将到期」窗口的签收时间范围（在库里的原始时间）
    #   截止 + shift ∈ (现在, 现在+24h]
    #   ⇔ 签收 ∈ (max_ts + 1h - 7d, max_ts + 25h - 7d]
    lo = (max_ts - datetime.timedelta(days=REFUND_DAYS) + datetime.timedelta(hours=1)) if max_ts else None
    hi = (max_ts - datetime.timedelta(days=REFUND_DAYS) + datetime.timedelta(hours=25)) if max_ts else None

    # ============ 1. 账号（登录页快捷填充用） ============
    accounts = []
    for uname, role in [('user0001', 'USER'), ('user0108', 'AGENT'), ('user0092', 'ADMIN')]:
        cur.execute("SELECT id,username,nickname,phone,role FROM t_user WHERE username=%s", (uname,))
        r = cur.fetchone()
        if r:
            r['roleText'] = {'USER': '普通用户', 'AGENT': '客服', 'ADMIN': '管理员'}[r['role']]
            accounts.append(r)
    for role in ('USER', 'AGENT', 'ADMIN'):        # 万一账号名对不上，按角色兜底各取一个
        if not any(a['role'] == role for a in accounts):
            cur.execute("SELECT id,username,nickname,phone,role FROM t_user WHERE role=%s LIMIT 1", (role,))
            r = cur.fetchone()
            if r:
                r['roleText'] = {'USER': '普通用户', 'AGENT': '客服', 'ADMIN': '管理员'}[r['role']]
                accounts.append(r)

    # ============ 2. 选订单 ============
    picked = []
    cur.execute("SELECT status FROM t_order GROUP BY status")
    for st in [r['status'] for r in cur.fetchall()]:
        if st == 'RECEIVED':
            cur.execute("SELECT order_no, receive_time FROM t_order "
                        "WHERE status='RECEIVED' AND receive_time IS NOT NULL "
                        "ORDER BY receive_time")
            all_recv = cur.fetchall()
            ids = []
            # (a) 跨 21 天均匀抽：保证「有资格」和「已超期」都有
            step = max(1, len(all_recv) // RECV_SPREAD)
            ids += [r['order_no'] for r in all_recv[::step]]
            # (b) 卡在 7 天线上那一簇：保证「即将到期」有几条可看
            if lo:
                cur.execute("SELECT order_no FROM t_order WHERE status='RECEIVED' "
                            "AND receive_time BETWEEN %s AND %s ORDER BY receive_time LIMIT %s",
                            (lo, hi, RECV_SOON_CLUSTER))
                ids += [r['order_no'] for r in cur.fetchall()]
            ids = list(dict.fromkeys(ids))
            cur.execute("SELECT * FROM t_order WHERE order_no IN (%s)"
                        % ','.join(['%s'] * len(ids)), ids)
            picked.extend(cur.fetchall())
        else:
            cur.execute("SELECT * FROM t_order WHERE status=%s ORDER BY create_time DESC LIMIT %s",
                        (st, ORDERS_PER_BUCKET))
            picked.extend(cur.fetchall())
    # 演示用户(user_id=1)的订单全要，方便按用户筛选
    cur.execute("SELECT * FROM t_order WHERE user_id=1 ORDER BY id DESC LIMIT 12")
    picked.extend(cur.fetchall())

    # ============ 3. 组装明细与物流 ============
    seen, orders = set(), []
    for o in picked:
        if o['order_no'] in seen:
            continue
        seen.add(o['order_no'])

        cur.execute("SELECT product_id, product_name, product_price, quantity, refund_deadline "
                    "FROM t_order_item WHERE order_no=%s ORDER BY id", (o['order_no'],))
        items = [{'productId': it['product_id'], 'productName': it['product_name'],
                  'price': float(it['product_price']), 'quantity': it['quantity'],
                  'refundDeadline': to_iso(it['refund_deadline'])} for it in cur.fetchall()]

        cur.execute("SELECT * FROM t_order_logistics WHERE order_no=%s LIMIT 1", (o['order_no'],))
        lg = cur.fetchone()
        logistics = None
        if lg:
            logistics = {
                'trackNo': lg['track_no'],
                'currentStatus': lg['current_status'],
                'currentNode': lg['current_node'],
                'estimatedArrival': lg['estimated_arrival'].strftime('%Y-%m-%d')
                                    if lg['estimated_arrival'] else None,
            }

        cur.execute("SELECT id,nickname,phone FROM t_user WHERE id=%s", (o['user_id'],))
        u = cur.fetchone() or {}

        orders.append({
            'orderNo': o['order_no'], 'userId': o['user_id'],
            'userNickname': u.get('nickname') or '', 'userPhone': u.get('phone') or '',
            'status': o['status'], 'statusText': STATUS_TEXT.get(o['status'], o['status']),
            'totalAmount': float(o['total_amount']), 'payAmount': float(o['pay_amount']),
            'payTime': to_iso(o['pay_time']), 'shipTime': to_iso(o['ship_time']),
            'receiveTime': to_iso(o['receive_time']),
            'receiverName': o['receiver_name'], 'receiverPhone': o['receiver_phone'],
            'receiverAddr': o['receiver_addr'],
            'createTime': to_iso(o['create_time']), 'updateTime': to_iso(o['update_time']),
            'items': items, 'logistics': logistics,
        })

    # ============ 4. 应用时间平移 ============
    if shift:
        def sh(s):
            return (datetime.datetime.strptime(s, '%Y-%m-%d %H:%M:%S')
                    + delta).strftime('%Y-%m-%d %H:%M:%S')

        for o in orders:
            for f in DT_FIELDS:
                if o.get(f):
                    o[f] = sh(o[f])
            for it in o['items']:
                if it.get('refundDeadline'):
                    it['refundDeadline'] = sh(it['refundDeadline'])

    payload = {
        'generatedAt': now.strftime('%Y-%m-%d %H:%M:%S'),
        'rebaseOffsetHours': round(shift / 3600, 1),
        'soonHours': SOON_HOURS,
        'accounts': accounts,
        'orders': sorted(orders, key=lambda x: x['createTime'], reverse=True),
    }
    c.close()

    os.makedirs(os.path.dirname(out), exist_ok=True)
    with open(out, 'w', encoding='utf-8') as f:
        f.write('/* 本文件由 scripts/gen_mock.py 自动生成，请勿手改。\n')
        f.write('   数据来源：commerce_service 库真实订单。生成时间 %s，时间平移 %.1f 小时。\n'
                % (payload['generatedAt'], payload['rebaseOffsetHours']))
        f.write('   重新生成：.venv\\Scripts\\python scripts\\gen_mock.py  */\n')
        f.write('window.MOCK_DATA = ')
        json.dump(payload, f, ensure_ascii=False, indent=1)
        f.write(';\n')

    # ============ 5. 自检 ============
    print('OK -> %s' % out)
    print('账号 %d 个，订单 %d 单（明细 %d 条）' % (
        len(payload['accounts']), len(orders), sum(len(o['items']) for o in orders)))
    print('时间平移：%+.1f 小时（%s）' % (
        payload['rebaseOffsetHours'], '已关闭' if args.no_rebase else '已启用'))

    buckets = {'有资格': 0, '即将到期': 0, '已超期': 0}
    future = []
    for o in orders:
        for f in DT_FIELDS:
            if o.get(f) and o[f] > now.strftime('%Y-%m-%d %H:%M:%S'):
                future.append(o['orderNo'] + '.' + f)
        for it in o['items']:
            if not it.get('refundDeadline'):
                continue
            h = (datetime.datetime.strptime(it['refundDeadline'], '%Y-%m-%d %H:%M:%S')
                 - now).total_seconds() / 3600
            buckets['有资格' if h > SOON_HOURS else ('即将到期' if h > 0 else '已超期')] += 1
    print('退货资格自检：' + '，'.join('%s %d 条' % (k, v) for k, v in buckets.items()))
    missing = [k for k, v in buckets.items() if v == 0]
    if missing:
        print('  !! 警告：%s 没有样本，页面上会看不到这一档' % '、'.join(missing))
    if future:
        print('  !! 警告：有 %d 个未来时间，例如 %s' % (len(future), future[:3]))
    else:
        print('时间自检：无未来时间，最早下单 %s' % min(o['createTime'] for o in orders))


if __name__ == '__main__':
    sys.exit(main())
