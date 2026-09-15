/* ============================================================
   Mock 层：后端没写完时，让前端页面能完整跑起来
   ------------------------------------------------------------
   它模拟的是"后端返回什么"。每个函数返回的对象结构，
   和 docs/API.md 里定义的 Result<T> 一模一样：
       { code: 1, msg: "", data: ... }        code=1 成功，0 失败

   后端写好后：把 assets/api.js 里的 USE_MOCK 改成 false，
   本文件就完全不再被调用。可以放心删，也可以留着做演示兜底。

   数据来源：
     - 订单类  → mock_data.js（脚本从真实数据库导出）
     - 工单/对话 → 本文件手写（因为 t_after_sale 是空表，
                  这些数据要等你 AI 跑起来后由对话真实产生）
   ============================================================ */
(function () {
  'use strict';

  var D = window.MOCK_DATA || { orders: [], accounts: [], soonHours: 24 };

  /* ---------- 小工具 ---------- */
  function pad(n) { return n < 10 ? '0' + n : '' + n; }

  function fmt(d) {
    return d.getFullYear() + '-' + pad(d.getMonth() + 1) + '-' + pad(d.getDate()) + ' '
      + pad(d.getHours()) + ':' + pad(d.getMinutes()) + ':' + pad(d.getSeconds());
  }
  /** n 小时前（工单时间永远新鲜，不会因为放久了变成"半年前"） */
  function ago(h) { return fmt(new Date(Date.now() - h * 3600 * 1000)); }

  function ok(data) { return { code: 1, msg: '', data: data }; }
  function fail(msg, code) { return { code: code || 0, msg: msg, data: null }; }

  /** 给订单明细补上后端该算的字段：refundEligible */
  function decorate(order) {
    var now = Date.now();
    var items = (order.items || []).map(function (it) {
      var dl = it.refundDeadline ? new Date(it.refundDeadline.replace(' ', 'T')).getTime() : null;
      return Object.assign({}, it, {
        refundEligible: dl === null ? null : dl > now,
        // 距截止还有多少小时，前端用来显示「即将到期」
        hoursLeft: dl === null ? null : (dl - now) / 3600000
      });
    });
    return Object.assign({}, order, { items: items });
  }

  /** 一张订单里"最紧急"的那件商品（退货截止最近的）。
   *  页面显示、资格筛选都以它为准，规则要和 Java 侧一致。 */
  function worstItem(order) {
    var best = null;
    (order.items || []).forEach(function (it) {
      if (it.hoursLeft === null || it.hoursLeft === undefined) return;
      if (!best || it.hoursLeft < best.hoursLeft) best = it;
    });
    return best;
  }

  /* ============================================================
     一、工单示例数据（8 张，覆盖全部 7 个状态）
     ============================================================ */
  var T = window.SM.TICKET_STATUS_TEXT;
  var TYPE = { REFUND: '退货退款', EXCHANGE: '换货', REPAIR: '维修' };

  function flow(from, to, opType, opName, remark, h) {
    return {
      fromStatus: from, toStatus: to,
      operatorType: opType, operatorName: opName,
      remark: remark, createTime: ago(h)
    };
  }

  var tickets = [
    {
      ticketNo: 'AS20260914001', orderNo: 'SO2026090900353',
      productId: 43, productName: '丝绒口红套装', userId: 1, userName: '谢雨欣',
      type: 'REFUND', reason: '口红颜色和商品图差距太大，只拆了一支，想退货',
      status: 'MANUAL_REVIEW', aiGenerated: 1, aiConfidence: 0.58,
      source: 'AI', handlerId: 108, handlerName: '郭思远', handleRemark: null,
      createTime: ago(2), updateTime: ago(2),
      flows: [
        flow(null, 'MANUAL_REVIEW', 'AI', '智能客服',
          '用户原话：口红颜色和商品图差距太大。签收 7 天内，但"色差"是否属质量问题需人工判断，置信度 0.58 低于阈值 0.7，转人工', 2)
      ]
    },
    {
      ticketNo: 'AS20260914002', orderNo: 'SO2026091000416',
      productId: 29, productName: '《算法导论》', userId: 19, userName: '黄静怡',
      type: 'REFUND', reason: '书本封面有明显折痕，全新未拆，申请退货',
      status: 'PENDING', aiGenerated: 1, aiConfidence: 0.93,
      source: 'AI', handlerId: null, handlerName: null, handleRemark: null,
      createTime: ago(4), updateTime: ago(4),
      flows: [
        flow(null, 'PENDING', 'AI', '智能客服',
          '校验通过：签收 1 天，在 7 天无理由期内；工单已创建，等待客服审核', 4)
      ]
    },
    {
      ticketNo: 'AS20260914003', orderNo: 'SO2026090400190',
      productId: 36, productName: '跑步运动腰包', userId: 91, userName: '刘宇轩',
      type: 'REFUND', reason: '腰包拉链拉不动，收到就是坏的',
      status: 'REFUNDING', aiGenerated: 1, aiConfidence: 0.91,
      source: 'AI', handlerId: 122, handlerName: '韩丽娟', handleRemark: '已确认为质量问题，运费我方承担',
      createTime: ago(26), updateTime: ago(3),
      flows: [
        flow(null, 'PENDING', 'AI', '智能客服', '校验通过：签收 6 天，在 7 天无理由期内', 26),
        flow('PENDING', 'APPROVED', 'AGENT', '韩丽娟', '照片已核实，确属质量问题，同意退货退款', 20),
        flow('APPROVED', 'REFUNDING', 'AGENT', '韩丽娟', '已生成退货面单，等待用户寄回后原路退款', 3)
      ]
    },
    {
      ticketNo: 'AS20260914004', orderNo: 'SO2026090700275',
      productId: 39, productName: '轻量登山双肩包', userId: 197, userName: '高浩然',
      type: 'REFUND', reason: '尺寸偏小，装不下笔记本',
      status: 'PENDING', aiGenerated: 0, aiConfidence: null,
      source: 'WEB', handlerId: null, handlerName: null, handleRemark: null,
      createTime: ago(8), updateTime: ago(8),
      flows: [flow(null, 'PENDING', 'USER', '高浩然', '用户在前端自助提交售后申请', 8)]
    },
    {
      ticketNo: 'AS20260913005', orderNo: 'SO2026090300422',
      productId: 17, productName: '每日坚果礼盒', userId: 143, userName: '李佳琪',
      type: 'EXCHANGE', reason: '想换成 30 包装的规格',
      status: 'COMPLETED', aiGenerated: 1, aiConfidence: 0.88,
      source: 'AI', handlerId: 122, handlerName: '韩丽娟', handleRemark: '换货已发出，新单号 SF1092837465',
      createTime: ago(40), updateTime: ago(6),
      flows: [
        flow(null, 'PENDING', 'AI', '智能客服', '校验通过：签收 6 天，在 7 天无理由期内', 40),
        flow('PENDING', 'APPROVED', 'AGENT', '韩丽娟', '同意换货', 30),
        flow('APPROVED', 'REFUNDING', 'SYSTEM', '系统', '换货单已生成并推送仓库', 24),
        flow('REFUNDING', 'COMPLETED', 'AGENT', '韩丽娟', '换货商品已发出，工单关闭', 6)
      ]
    },
    {
      ticketNo: 'AS20260912006', orderNo: 'SO2026090300011',
      productId: 7, productName: '降噪头戴耳机', userId: 187, userName: '彭文博',
      type: 'REPAIR', reason: '左耳没有声音，怀疑是质量问题',
      status: 'REJECTED', aiGenerated: 1, aiConfidence: 0.86,
      source: 'AI', handlerId: 170, handlerName: '邓志强',
      handleRemark: '检测报告显示为进液损坏，非质量问题，不在保修范围',
      createTime: ago(70), updateTime: ago(48),
      flows: [
        flow(null, 'PENDING', 'AI', '智能客服', '校验通过：签收 5 天', 70),
        flow('PENDING', 'REJECTED', 'AGENT', '邓志强',
          '检测报告显示为进液损坏，非质量问题，不在保修范围', 48)
      ]
    },
    {
      ticketNo: 'AS20260911007', orderNo: 'SO2026090400446',
      productId: 36, productName: '跑步运动腰包', userId: 9, userName: '谢晨光',
      type: 'REFUND', reason: '买错了，不想要了',
      status: 'CANCELLED', aiGenerated: 0, aiConfidence: null,
      source: 'WEB', handlerId: null, handlerName: null, handleRemark: null,
      createTime: ago(90), updateTime: ago(80),
      flows: [
        flow(null, 'PENDING', 'USER', '谢晨光', '用户在前端自助提交售后申请', 90),
        flow('PENDING', 'CANCELLED', 'USER', '谢晨光', '用户自行撤销申请', 80)
      ]
    },
    {
      ticketNo: 'AS20260910008', orderNo: 'SO2026090400077',
      productId: 2, productName: '机械键盘', userId: 100, userName: '郭雨欣',
      type: 'REFUND', reason: '键盘有几个键手感不一致，想退货',
      status: 'APPROVED', aiGenerated: 1, aiConfidence: 0.90,
      source: 'AI', handlerId: 108, handlerName: '郭思远', handleRemark: '同意退货，请保持包装完整',
      createTime: ago(100), updateTime: ago(74),
      flows: [
        flow(null, 'PENDING', 'AI', '智能客服', '校验通过：签收 6 天，在 7 天无理由期内', 100),
        flow('PENDING', 'APPROVED', 'AGENT', '郭思远', '同意退货，请保持包装完整', 74)
      ]
    }
  ];

  /* ============================================================
     二、对话示例数据（3 个会话，覆盖 4 类意图 + 转人工）
     ============================================================ */
  var sessions = [
    {
      sessionId: 'S20260914093012', userId: 1, userName: '谢雨欣',
      createTime: ago(8), updateTime: ago(1),
      title: '退货 · 口红颜色不符',
      messages: [
        {
          role: 'user', content: '我的订单到哪了', createTime: ago(8)
        },
        {
          role: 'assistant', intent: 'ORDER_QUERY', confidence: 0.96, latencyMs: 1720,
          createTime: ago(8),
          content: '您最近有 3 笔订单：\n1. 丝绒口红套装等 2 件，9 月 14 日已签收\n2. 记忆棉枕头等 3 件，9 月 1 日已签收\n3. 丝绒口红套装，9 月 8 日已签收\n请问您要查哪一笔？',
          tools: [{ name: 'search_user_orders', args: {}, status: 'ok', ms: 96, result: '返回 3 条' }]
        },
        {
          role: 'user', content: '第一个，口红那个', createTime: ago(7)
        },
        {
          role: 'assistant', intent: 'ORDER_QUERY', confidence: 0.94, latencyMs: 1980,
          createTime: ago(7),
          content: '订单 SO2026090900353 已于 9 月 14 日 08:54 签收，商品是折叠收纳箱 2 件、丝绒口红套装 2 件，实付 583.86 元。退货资格截止 9 月 21 日，还在有效期内。',
          tools: [{ name: 'query_order', args: { order_no: 'SO2026090900353' }, status: 'ok', ms: 143, result: '已签收 / 截止 09-21' }]
        },
        {
          role: 'user', content: '口红颜色和图片差太多了，我要退货', createTime: ago(2)
        },
        {
          role: 'assistant', intent: 'AFTER_SALE_APPLY', confidence: 0.58, latencyMs: 2410,
          createTime: ago(2),
          content: '您反馈的是「颜色与图片不符」，这类情况需要人工核实一下实物照片，我已为您提交人工审核，客服会在 24 小时内联系您。工单号 AS20260914001。',
          tools: [
            { name: 'check_refund_eligible', args: { order_no: 'SO2026090900353', product_id: 43 }, status: 'ok', ms: 88, result: 'eligible=true，但识别为"非质量问题"场景' },
            { name: 'create_after_sale_ticket', args: { order_no: 'SO2026090900353', product_id: 43, type: 'REFUND' }, status: 'ok', ms: 132, result: 'AS20260914001 / MANUAL_REVIEW' }
          ],
          note: '置信度 0.58 < 阈值 0.7，工单直接进 MANUAL_REVIEW，不自动承诺退款'
        }
      ]
    },
    {
      sessionId: 'S20260914110233', userId: 70, userName: '周天宇',
      createTime: ago(5), updateTime: ago(5),
      title: '政策咨询 · 七天无理由怎么算',
      messages: [
        { role: 'user', content: '七天无理由是从下单那天算还是签收那天算？', createTime: ago(5) },
        {
          role: 'assistant', intent: 'POLICY_QA', confidence: 0.95, latencyMs: 1520,
          createTime: ago(5),
          content: '从**签收当天**起算，签收后 7 天内可申请无理由退货。另外提醒一下：生鲜类、已拆封的个护类商品，以及定制类商品不支持七天无理由。',
          tools: [{
            name: 'search_refund_policy',
            args: { question: '七天无理由从哪天开始计算' },
            status: 'ok', ms: 210,
            result: '命中《七天无理由退货规则》第 2 条、第 4 条'
          }]
        },
        { role: 'user', content: '那退货运费谁出？', createTime: ago(5) },
        {
          role: 'assistant', intent: 'POLICY_QA', confidence: 0.92, latencyMs: 1680,
          createTime: ago(5),
          content: '分两种情况：质量问题（破损、发错货等）运费由我们承担；七天无理由退货，运费由您承担。寄回时请保留运费凭证。',
          tools: [{
            name: 'search_refund_policy',
            args: { question: '退货运费由谁承担' },
            status: 'ok', ms: 195,
            result: '命中《运费承担说明》第 1、2 条'
          }]
        }
      ]
    },
    {
      sessionId: 'S20260914140055', userId: 55, userName: '陈静雯',
      createTime: ago(1), updateTime: ago(1),
      title: '转人工 · 置信度过低',
      messages: [
        { role: 'user', content: '你们能不能帮我看看我这个情况算不算质量问题啊，我朋友说他之前也遇到过但是没给退', createTime: ago(1) },
        {
          role: 'assistant', intent: 'OTHER', confidence: 0.41, latencyMs: 1310,
          createTime: ago(1),
          content: '您描述的情况我还需要再确认一下，已为您转接人工客服，请稍等。',
          tools: [],
          note: '置信度 0.41 < 阈值 0.6，按人机协同兜底策略直接转人工，不调用任何工具'
        }
      ]
    }
  ];

  /* ============================================================
     三、Mock 的"规则式假 AI"
     它不是真模型，只按关键词判断意图并给出预置回答。
     目的是让对话页在后端没起来时也能点得动。
     ============================================================ */
  var INTENT_RULES = [
    { intent: 'AFTER_SALE_APPLY', kw: ['退货', '退款', '换货', '维修', '坏了', '破了', '坏了', '不合适', '想退'] },
    { intent: 'ORDER_QUERY', kw: ['到哪', '物流', '快递', '发货', '什么时候到', '签收', '订单'] },
    { intent: 'POLICY_QA', kw: ['七天', '政策', '规则', '运费', '发票', '怎么算', '无理由', '退换'] },
  ];

  function detectIntent(text) {
    for (var i = 0; i < INTENT_RULES.length; i++) {
      var r = INTENT_RULES[i];
      for (var j = 0; j < r.kw.length; j++) {
        if (text.indexOf(r.kw[j]) >= 0) return r.intent;
      }
    }
    return 'OTHER';
  }

  var CANDID = D.orders.filter(function (o) { return o.status === 'RECEIVED'; });

  function buildReply(text) {
    var intent = detectIntent(text);
    var t0 = Date.now();

    if (intent === 'ORDER_QUERY') {
      var o = CANDID[0];
      if (!o) return { content: '暂时没有查询到您的订单。', intent: intent, confidence: 0.9, tools: [] };
      return {
        content: '您最近一笔订单 ' + o.orderNo + ' 当前状态是「' + o.statusText + '」'
          + (o.logistics ? '，最新物流节点：' + o.logistics.currentNode + '。' : '。'),
        intent: intent, confidence: 0.95,
        tools: [{ name: 'query_order', args: { order_no: o.orderNo }, status: 'ok', ms: 120, result: o.statusText }]
      };
    }

    if (intent === 'AFTER_SALE_APPLY') {
      var o2 = CANDID[1] || CANDID[0];
      var it = o2 && o2.items[0];
      return {
        content: '已为您核对：订单 ' + (o2 ? o2.orderNo : '') + ' 还在退货有效期内。'
          + '我已创建售后工单，客服会尽快审核，退款不会自动执行，需要人工确认。',
        intent: intent, confidence: 0.91,
        tools: [
          { name: 'check_refund_eligible', args: { order_no: o2 ? o2.orderNo : '', product_id: it ? it.productId : 0 }, status: 'ok', ms: 86, result: 'eligible=true' },
          { name: 'create_after_sale_ticket', args: { order_no: o2 ? o2.orderNo : '', product_id: it ? it.productId : 0, type: 'REFUND' }, status: 'ok', ms: 141, result: 'AS20260914099 / PENDING' }
        ]
      };
    }

    if (intent === 'POLICY_QA') {
      return {
        content: '七天无理由从签收当天起算，签收后 7 天内可申请。生鲜、已拆封个护、定制类商品不支持。',
        intent: intent, confidence: 0.94,
        tools: [{ name: 'search_refund_policy', args: { question: text }, status: 'ok', ms: 198, result: '命中《七天无理由退货规则》第 2 条' }]
      };
    }

    return {
      content: '抱歉，这个问题不在我的服务范围内。我可以帮您查订单物流、申请售后，或解答退换货政策。',
      intent: 'OTHER', confidence: 0.88, tools: []
    };
  }

  /* ============================================================
     四、Mock 路由：把请求分发给上面这些数据
     api.js 会按 USE_MOCK 决定调它还是真的发 HTTP
     ============================================================ */
  function handle(method, path, query, body) {
    query = query || {};
    method = (method || 'GET').toUpperCase();

    /* ---- 登录 ---- */
    if (method === 'POST' && path === '/api/auth/login') {
      var acc = (body && body.username) || '';
      var found = D.accounts.filter(function (a) { return a.username === acc; })[0];
      if (!found && body && body.username) {
        found = { id: 1, username: acc, nickname: acc, role: 'USER', roleText: '普通用户', phone: '' };
      }
      if (!found) return fail('用户名或密码错误');
      return ok({ token: 'mock-token-' + found.username, user: found });
    }
    if (path === '/api/auth/me') {
      var cur = window.Api && window.Api.getUser && window.Api.getUser();
      return cur ? ok(cur) : fail('未登录', 401);
    }

    /* ---- 订单列表：分页 + 关键词 + 状态 + 用户 ---- */
    if (method === 'GET' && path === '/api/orders') {
      var list = D.orders.map(decorate);
      if (query.status) list = list.filter(function (o) { return o.status === query.status; });
      if (query.userId) list = list.filter(function (o) { return String(o.userId) === String(query.userId); });
      /* 退货资格筛选：eligible 有资格 / soon 即将到期 / expired 已超期
         未签收的订单没有退货截止时间，任何资格条件下都不返回。 */
      if (query.eligibility) {
        var soon = D.soonHours || 24;
        list = list.filter(function (o) {
          var it = worstItem(o);
          if (!it) return false;
          var key = it.hoursLeft <= 0 ? 'expired'
                  : (it.hoursLeft <= soon ? 'soon' : 'eligible');
          return key === query.eligibility;
        });
      }
      if (query.keyword) {
        var kw = String(query.keyword).toLowerCase();
        list = list.filter(function (o) {
          return o.orderNo.toLowerCase().indexOf(kw) >= 0
            || (o.userNickname || '').toLowerCase().indexOf(kw) >= 0
            || (o.items || []).some(function (i) {
              return i.productName.toLowerCase().indexOf(kw) >= 0;
            });
        });
      }
      var page = parseInt(query.page || 1, 10);
      var limit = parseInt(query.limit || 10, 10);
      var total = list.length;
      return ok({ total: total, records: list.slice((page - 1) * limit, page * limit) });
    }

    /* ---- 订单详情 ---- */
    var mOrder = path.match(/^\/api\/orders\/([^\/]+)$/);
    if (method === 'GET' && mOrder) {
      var no = decodeURIComponent(mOrder[1]);
      var one = D.orders.filter(function (o) { return o.orderNo === no; })[0];
      return one ? ok(decorate(one)) : fail('订单不存在', 404);
    }

    /* ---- 用户自助建工单（POST /api/after-sales） ---- */
    if (method === 'POST' && path === '/api/after-sales') {
      var ord0 = D.orders.filter(function (o) { return o.orderNo === (body && body.orderNo); })[0];
      if (!ord0) return fail('订单不存在', 404);
      var itm = (ord0.items || []).filter(function (i) {
        return String(i.productId) === String(body.productId);
      })[0] || (ord0.items || [])[0];
      var newNo = 'AS' + fmt(new Date()).slice(0, 10).replace(/-/g, '')
        + ('0' + (tickets.length + 1)).slice(-2);
      var nt = {
        ticketNo: newNo, orderNo: ord0.orderNo,
        productId: itm ? itm.productId : null, productName: itm ? itm.productName : '',
        userId: ord0.userId, userName: ord0.userNickname,
        type: (body && body.type) || 'REFUND',
        reason: (body && body.reason) || '',
        status: 'PENDING', aiGenerated: 0, aiConfidence: null,
        source: 'WEB', handlerId: null, handlerName: null, handleRemark: null,
        createTime: fmt(new Date()), updateTime: fmt(new Date()),
        flows: [flow(null, 'PENDING', 'USER', ord0.userNickname, '用户在前端自助提交售后申请', 0)]
      };
      tickets.unshift(nt);
      return ok({ ticketNo: nt.ticketNo, status: nt.status, message: '工单已创建，等待客服审核' });
    }

    /* ---- 工单列表 ---- */
    if (method === 'GET' && path === '/api/after-sales') {
      var tl = tickets.slice();
      if (query.status) tl = tl.filter(function (t) { return t.status === query.status; });
      if (query.source) tl = tl.filter(function (t) { return t.source === query.source; });
      if (query.keyword) {
        var k2 = String(query.keyword).toLowerCase();
        tl = tl.filter(function (t) {
          return t.ticketNo.toLowerCase().indexOf(k2) >= 0
            || t.orderNo.toLowerCase().indexOf(k2) >= 0
            || (t.productName || '').toLowerCase().indexOf(k2) >= 0;
        });
      }
      var stats = {};
      Object.keys(T).forEach(function (k) {
        stats[k] = tickets.filter(function (t) { return t.status === k; }).length;
      });
      var p2 = parseInt(query.page || 1, 10);
      var l2 = parseInt(query.limit || 10, 10);
      return ok({
        total: tl.length,
        records: tl.slice((p2 - 1) * l2, p2 * l2),
        stats: stats
      });
    }

    /* ---- 工单详情 ---- */
    var mTk = path.match(/^\/api\/after-sales\/([^\/]+)$/);
    if (method === 'GET' && mTk) {
      var tn = decodeURIComponent(mTk[1]);
      var t1 = tickets.filter(function (x) { return x.ticketNo === tn; })[0];
      if (!t1) return fail('工单不存在', 404);
      var ord = D.orders.filter(function (o) { return o.orderNo === t1.orderNo; })[0];
      return ok(Object.assign({}, t1, {
        order: ord ? decorate(ord) : null,
        allowedTransitions: window.SM.allowed(t1.status)
      }));
    }

    /* ---- 工单状态流转 ---- */
    var mTr = path.match(/^\/api\/after-sales\/([^\/]+)\/transition$/);
    if (method === 'POST' && mTr) {
      var tno = decodeURIComponent(mTr[1]);
      var tk = tickets.filter(function (x) { return x.ticketNo === tno; })[0];
      if (!tk) return fail('工单不存在', 404);
      var to = body && body.toStatus;
      if (window.SM.allowed(tk.status).indexOf(to) < 0) {
        return fail('不允许从 ' + T[tk.status] + ' 流转到 ' + (T[to] || to));
      }
      tk.flows.push({
        fromStatus: tk.status, toStatus: to,
        operatorType: (body.operatorType || 'AGENT'),
        operatorName: (body.operatorName || '当前客服'),
        remark: body.remark || '', createTime: fmt(new Date())
      });
      tk.status = to;
      tk.handleRemark = body.remark || tk.handleRemark;
      tk.updateTime = fmt(new Date());
      return ok({ ticketNo: tk.ticketNo, status: tk.status, statusText: T[tk.status] });
    }

    /* ---- 会话 ---- */
    if (method === 'GET' && path === '/api/chat/sessions') {
      var sl = sessions.filter(function (s) {
        return !query.userId || String(s.userId) === String(query.userId);
      }).map(function (s) {
        var last = s.messages[s.messages.length - 1] || {};
        return {
          sessionId: s.sessionId, userId: s.userId, userName: s.userName,
          title: s.title, createTime: s.createTime, updateTime: s.updateTime,
          lastMessage: (last.content || '').slice(0, 40)
        };
      });
      return ok(sl);
    }

    if (method === 'POST' && path === '/api/chat/sessions') {
      var nid = 'S' + Date.now();
      sessions.unshift({
        sessionId: nid, userId: (body && body.userId) || 1,
        userName: (body && body.userName) || '当前用户',
        title: '新会话', createTime: fmt(new Date()), updateTime: fmt(new Date()),
        messages: []
      });
      return ok({ sessionId: nid });
    }

    if (method === 'GET' && path === '/api/chat/history') {
      var ss = sessions.filter(function (s) { return s.sessionId === query.sessionId; })[0];
      return ss ? ok(ss.messages) : fail('会话不存在', 404);
    }

    /* ---- 对话入口 ---- */
    if (method === 'POST' && path === '/api/chat') {
      var sid = body && body.sessionId;
      var sess = sessions.filter(function (s) { return s.sessionId === sid; })[0];
      if (!sess) {
        sid = 'S' + Date.now();
        sess = { sessionId: sid, userId: 1, userName: '当前用户', title: '新会话',
                 createTime: fmt(new Date()), updateTime: fmt(new Date()), messages: [] };
        sessions.unshift(sess);
      }
      var q = (body && body.message) || '';
      sess.messages.push({ role: 'user', content: q, createTime: fmt(new Date()) });
      var r = buildReply(q);
      var msg = {
        role: 'assistant', content: r.content, intent: r.intent,
        confidence: r.confidence, latencyMs: 1200 + Math.floor(Math.random() * 1600),
        tools: r.tools, createTime: fmt(new Date())
      };
      sess.messages.push(msg);
      sess.updateTime = fmt(new Date());
      if (sess.title === '新会话') sess.title = q.slice(0, 16);
      return ok({
        sessionId: sid, answer: r.content, intent: r.intent,
        confidence: r.confidence, tools: r.tools, latencyMs: msg.latencyMs
      });
    }

    /* ---- 订单状态统计（首页 KPI 用） ---- */
    if (method === 'GET' && path === '/api/stats/orders') {
      var byStatus = {};
      D.orders.forEach(function (o) {
        byStatus[o.status] = (byStatus[o.status] || 0) + 1;
      });
      return ok({ total: D.orders.length, byStatus: byStatus });
    }

    /* ---- 首页概览数字 ---- */
    if (method === 'GET' && path === '/api/stats/overview') {
      var st = {};
      Object.keys(T).forEach(function (k) {
        st[k] = tickets.filter(function (t) { return t.status === k; }).length;
      });
      return ok({
        totalOrders: D.orders.length,
        totalTickets: tickets.length,
        pendingTickets: st.PENDING || 0,
        manualReviewTickets: st.MANUAL_REVIEW || 0,
        aiCreatedRate: 0.75,
        avgLatencyMs: 1780
      });
    }

    return fail('Mock 未实现的接口：' + method + ' ' + path, 404);
  }

  /* 工单号自增，供页面演示"新建"用 */
  var seq = 100;
  window.MOCK = {
    accounts: D.accounts,
    orders: D.orders,
    tickets: tickets,
    sessions: sessions,
    handle: handle,
    nextTicketNo: function () { return 'AS2026091' + (seq++); }
  };
})();
