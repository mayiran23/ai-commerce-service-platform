/* ============================================================
   UI 工具箱：状态字典 + 格式化 + 通用交互组件
   ------------------------------------------------------------
   这一层不掺业务，只解决"显示成什么样"。
   四类东西：
     1. SM  状态机字典（前端唯一来源，和 Java 侧必须保持一致）
     2. UI  格式化 & 标签（金额、时间、状态徽标、置信度条）
     3. UI  交互组件（侧边栏骨架、抽屉、分页、Toast）
     4. UI  通用小工具（取元素、转义、取 URL 参数）
   ============================================================ */
(function () {
  'use strict';

  /* ============================================================
     1. 状态字典 & 状态机
     ============================================================ */

  /* 订单状态（对应 t_order.status） */
  var ORDER_STATUS_TEXT = {
    PENDING_PAY: '待付款',
    PAID: '已付款',
    SHIPPED: '已发货',
    DELIVERING: '运输中',
    RECEIVED: '已签收',
    CANCELLED: '已取消'
  };
  /* 徽标配色：'muted' 是默认灰，其余对应 app.css 里的 .badge.xxx */
  var ORDER_STATUS_CLS = {
    PENDING_PAY: 'muted', PAID: 'info', SHIPPED: 'info',
    DELIVERING: 'info', RECEIVED: 'ok', CANCELLED: 'muted'
  };

  /* 工单状态（对应 t_after_sale.status） */
  var TICKET_STATUS_TEXT = {
    PENDING: '待审核', APPROVED: '已通过', REFUNDING: '退款中',
    COMPLETED: '已完成', REJECTED: '已驳回', MANUAL_REVIEW: '待人工', CANCELLED: '已撤销'
  };
  var TICKET_STATUS_CLS = {
    PENDING: 'warn', MANUAL_REVIEW: 'danger', APPROVED: 'info',
    REFUNDING: 'info', COMPLETED: 'ok', REJECTED: 'danger', CANCELLED: 'muted'
  };

  /* 工单状态机：合法流转表
     这张表和 Java 侧 AfterSaleStatus 枚举里的 Map 必须一模一样。
     前端用它决定"该显示哪几个按钮"，后端用它做真正校验。
     两边都算一遍，前端是为了体验，后端才是权威。 */
  var TRANSITIONS = {
    PENDING:       ['APPROVED', 'REJECTED', 'MANUAL_REVIEW', 'CANCELLED'],
    MANUAL_REVIEW: ['APPROVED', 'REJECTED', 'CANCELLED'],
    APPROVED:      ['REFUNDING'],
    REFUNDING:     ['COMPLETED'],
    COMPLETED:     [],
    REJECTED:      [],
    CANCELLED:     []
  };
  /* 流转按钮的文案与样式（按目标状态取） */
  var TRANSITION_UI = {
    APPROVED:      { text: '审核通过', cls: 'ok' },
    REJECTED:      { text: '驳回',     cls: 'danger' },
    MANUAL_REVIEW: { text: '转人工',   cls: '' },
    CANCELLED:     { text: '撤销工单', cls: '' },
    REFUNDING:     { text: '开始退款', cls: 'primary' },
    COMPLETED:     { text: '标记完成', cls: 'primary' }
  };

  /* ============================================================
     2. 格式化 & 标签
     ============================================================ */
  function esc(s) {
    return String(s === null || s === undefined ? '' : s)
      .replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;').replace(/'/g, '&#39;');
  }

  /** 金额：¥1,234.56 */
  function money(v) {
    if (v === null || v === undefined || v === '') return '-';
    var n = Number(v);
    if (isNaN(n)) return esc(v);
    return '¥' + n.toFixed(2).replace(/\B(?=(\d{3})+(?!\d))/g, ',');
  }

  /** 金额（不带符号，表格里用） */
  function money0(v) { return money(v).replace('¥', ''); }

  /** '2026-09-14 08:54:11' → '09-14 08:54' ；今天/昨天用相对词 */
  function dt(s, shortMode) {
    if (!s) return '-';
    var t = String(s).replace('T', ' ');
    if (shortMode) return t.slice(5, 16);
    return t.slice(0, 19);
  }

  /** 相对时间：3 小时前 / 2 天前 */
  function ago(s) {
    if (!s) return '-';
    var d = new Date(String(s).replace(' ', 'T')).getTime();
    if (isNaN(d)) return esc(s);
    var diff = Date.now() - d;
    var min = Math.floor(diff / 60000);
    if (min < 1) return '刚刚';
    if (min < 60) return min + ' 分钟前';
    var h = Math.floor(min / 60);
    if (h < 24) return h + ' 小时前';
    var day = Math.floor(h / 24);
    if (day < 30) return day + ' 天前';
    return dt(s, true);
  }

  function orderBadge(status) {
    var text = ORDER_STATUS_TEXT[status] || status;
    var cls = ORDER_STATUS_CLS[status] || 'muted';
    return '<span class="badge ' + cls + '">' + esc(text) + '</span>';
  }

  function ticketBadge(status) {
    var text = TICKET_STATUS_TEXT[status] || status;
    var cls = TICKET_STATUS_CLS[status] || 'muted';
    return '<span class="badge ' + cls + '">' + esc(text) + '</span>';
  }

  /** 退货资格三档展示。
   *  权威结论是后端算出来的 item.refundEligible —— 它说 false 就是 false。
   *  前端只额外加一档「即将到期」的可视化提示（截止时间不足 24 小时），
   *  目的是让客服一眼看出哪些单该优先处理。 */
  function eligibility(item) {
    if (!item || !item.refundDeadline) {
      return { key: 'none', text: '不适用', cls: 'muted', tip: '该订单尚未签收，暂不计算退货资格' };
    }
    var soon = (window.MOCK_DATA && window.MOCK_DATA.soonHours) || 24;
    var h = item.hoursLeft;
    if (h === undefined || h === null) {
      var ms = new Date(String(item.refundDeadline).replace(' ', 'T')).getTime() - Date.now();
      h = ms / 3600000;
    }
    /* 后端明确判定为「无资格」时，以它为准 */
    if (item.refundEligible === false) h = Math.min(h, 0);
    if (h <= 0) {
      return { key: 'expired', text: '已超期', cls: 'danger', tip: '已超过 7 天无理由期限，需转人工审核' };
    }
    if (h <= soon) {
      return { key: 'soon', text: '即将到期', cls: 'warn', tip: '距退货截止不足 ' + Math.round(h) + ' 小时，建议优先处理' };
    }
    return { key: 'ok', text: '有资格', cls: 'ok', tip: '在 7 天无理由期内，截止 ' + dt(item.refundDeadline, true) };
  }

  function eligBadge(item) {
    var e = eligibility(item);
    return '<span class="badge ' + e.cls + '" title="' + esc(e.tip) + '">' + e.text + '</span>';
  }

  /** 置信度进度条（工单详情里用） */
  function confBar(conf) {
    if (conf === null || conf === undefined) {
      return '<span class="muted">无（非 AI 创建）</span>';
    }
    var p = Math.round(Number(conf) * 100);
    var color = p >= 70 ? 'var(--ok)' : (p >= 60 ? 'var(--warn)' : 'var(--danger)');
    return '<div class="row" style="gap:10px">'
      + '<div style="flex:1;height:6px;border-radius:6px;background:var(--line);overflow:hidden">'
      + '<div style="width:' + p + '%;height:100%;background:' + color + '"></div></div>'
      + '<b style="font-variant-numeric:tabular-nums">' + p + '%</b>'
      + '<span class="muted" style="font-size:12px">阈值 70%</span></div>';
  }

  /* ============================================================
     3. 交互组件
     ============================================================ */

  /** Toast 轻提示：UI.toast('保存成功', 'ok') */
  function toast(msg, type) {
    var box = document.getElementById('toasts');
    if (!box) {
      box = document.createElement('div');
      box.id = 'toasts';
      document.body.appendChild(box);
    }
    var d = document.createElement('div');
    d.className = 'toast ' + (type || '');
    d.textContent = msg;
    box.appendChild(d);
    setTimeout(function () {
      d.style.transition = '.2s'; d.style.opacity = '0';
      setTimeout(function () { d.remove(); }, 220);
    }, 2400);
  }

  var NAV = [
    { key: 'orders', href: 'orders.html', label: '订单管理', roles: ['USER', 'AGENT', 'ADMIN'],
      icon: '<path d="M3 7l9-4 9 4v10l-9 4-9-4z"/><path d="M3 7l9 4 9-4M12 11v10"/>' },
    { key: 'tickets', href: 'tickets.html', label: '售后工单', roles: ['AGENT', 'ADMIN'],
      icon: '<path d="M9 3h6v3H9z"/><path d="M7 5H5v16h14V5h-2"/><path d="M9 11h6M9 15h4"/>' },
    { key: 'chat', href: 'chat.html', label: '智能客服', roles: ['USER', 'AGENT', 'ADMIN'],
      icon: '<path d="M21 12a8 8 0 1 1-3.2-6.4"/><path d="M4 20l1.6-4.4"/><path d="M8 11h8M8 15h5"/>' }
  ];

  /** 渲染侧边栏 + 顶栏。页面只需：
   *    <aside id="sidebar"></aside> ... <header id="topbar"></header>
   *  然后 UI.shell({active:'orders', title:'订单管理'}) */
  function shell(cfg) {
    cfg = cfg || {};
    var user = getUser() || { nickname: '未登录', role: 'USER', roleText: '普通用户' };
    var role = user.role || 'USER';

    var sb = document.getElementById('sidebar');
    if (sb) {
      var html = '<div class="brand"><span class="dot">智</span>智联商城 · 客服台</div><nav class="nav">'
        + '<div class="nav-title">工作台</div>';
      NAV.forEach(function (n) {
        if (n.roles.indexOf(role) < 0) return;
        html += '<a href="' + n.href + '" class="' + (cfg.active === n.key ? 'active' : '') + '">'
          + '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" '
          + 'stroke-linecap="round" stroke-linejoin="round">' + n.icon + '</svg>'
          + n.label + '</a>';
      });
      html += '</nav><div class="sidebar-foot">'
        + '<div class="muted" style="font-size:11.5px;line-height:1.5">'
        + '当前身份：<b>' + esc(role) + '</b><br>' + esc(user.nickname || '') + '</div>'
        + '</div>';
      sb.innerHTML = html;
    }

    var tb = document.getElementById('topbar');
    if (tb) {
      tb.innerHTML = '<div><h1>' + esc(cfg.title || '') + '</h1>'
        + (cfg.sub ? '<div class="sub">' + esc(cfg.sub) + '</div>' : '')
        + '</div>'
        + '<div class="right">' + (cfg.actions || '')
        + '<span class="userchip"><span class="avatar">' + esc((user.nickname || '?').slice(0, 1)) + '</span>'
        + '<span><span class="name">' + esc(user.nickname || '') + '</span>'
        + '<span class="role"> · ' + esc(user.roleText || role) + '</span></span></span>'
        + '<button class="btn sm" id="btnLogout">退出</button>'
        + '</div>';
      var lo = document.getElementById('btnLogout');
      if (lo) lo.onclick = function () { Api.logout(); };
    }
  }

  /** 抽屉：UI.openDrawer({title, body, footer, onClose}) */
  function openDrawer(cfg) {
    var mask = document.getElementById('mask');
    var drawer = document.getElementById('drawer');
    if (!mask || !drawer) {
      mask = document.createElement('div'); mask.id = 'mask'; mask.className = 'mask';
      drawer = document.createElement('div'); drawer.id = 'drawer'; drawer.className = 'drawer';
      drawer.innerHTML = '<div class="drawer-hd"><h3 id="dwTitle"></h3>'
        + '<div class="right"><button class="x-btn" id="dwClose">&times;</button></div></div>'
        + '<div class="drawer-bd" id="dwBody"></div>'
        + '<div class="drawer-ft" id="dwFoot"></div>';
      document.body.appendChild(mask); document.body.appendChild(drawer);
      mask.onclick = closeDrawer;
    }
    document.getElementById('dwTitle').innerHTML = cfg.title || '';
    document.getElementById('dwBody').innerHTML = cfg.body || '';
    var foot = document.getElementById('dwFoot');
    foot.innerHTML = cfg.footer || '';
    foot.style.display = cfg.footer ? '' : 'none';
    document.getElementById('dwClose').onclick = closeDrawer;
    drawer._onClose = cfg.onClose;
    requestAnimationFrame(function () {
      mask.classList.add('on'); drawer.classList.add('on');
    });
  }

  function closeDrawer() {
    var mask = document.getElementById('mask');
    var drawer = document.getElementById('drawer');
    if (drawer && drawer._onClose) drawer._onClose();
    if (mask) mask.classList.remove('on');
    if (drawer) drawer.classList.remove('on');
  }

  /** 分页条：UI.pager(el, {total, page, limit, onChange}) */
  function pager(el, cfg) {
    if (!el) return;
    var pages = Math.max(1, Math.ceil(cfg.total / cfg.limit));
    var cur = Math.min(cfg.page, pages);
    var h = '<span>共 <b>' + cfg.total + '</b> 条，第 ' + cur + '/' + pages + ' 页</span>'
      + '<span class="spacer"></span>'
      + '<button class="pn" data-go="' + (cur - 1) + '"' + (cur <= 1 ? ' disabled' : '') + '>上一页</button>';
    var from = Math.max(1, cur - 2), to = Math.min(pages, from + 4);
    from = Math.max(1, to - 4);
    for (var i = from; i <= to; i++) {
      h += '<button class="pn ' + (i === cur ? 'on' : '') + '" data-go="' + i + '">' + i + '</button>';
    }
    h += '<button class="pn" data-go="' + (cur + 1) + '"' + (cur >= pages ? ' disabled' : '') + '>下一页</button>';
    el.innerHTML = h;
    el.querySelectorAll('[data-go]').forEach(function (b) {
      b.onclick = function () {
        var p = parseInt(b.getAttribute('data-go'), 10);
        if (p >= 1 && p <= pages && p !== cur) cfg.onChange(p);
      };
    });
  }

  /** 表格空/加载状态 */
  function emptyRow(colspan, msg, tip) {
    return '<tr><td colspan="' + colspan + '"><div class="empty">'
      + '<div class="big">' + esc(msg || '没有数据') + '</div>'
      + (tip ? '<div>' + esc(tip) + '</div>' : '') + '</div></td></tr>';
  }
  function loadingRow(colspan) {
    return '<tr><td colspan="' + colspan + '"><div class="loading">'
      + '<div class="spinner"></div>加载中…</div></td></tr>';
  }

  /** 页面底部的"本页用到哪些接口"提示条 */
  function apiHint(items) {
    var h = '<div class="api-hint"><span class="lbl">本页对应接口</span>';
    h += items.map(function (i) {
      return '<code>' + esc(i) + '</code>';
    }).join('');
    h += '<span class="lbl" style="margin-left:8px">（详见 docs/API.md）</span></div>';
    return h;
  }

  /* ============================================================
     4. 小工具
     ============================================================ */
  function $(sel, root) { return (root || document).querySelector(sel); }
  function $$(sel, root) { return Array.prototype.slice.call((root || document).querySelectorAll(sel)); }
  function qs(name, def) {
    var m = new RegExp('[?&]' + name + '=([^&]*)').exec(location.search);
    return m ? decodeURIComponent(m[1]) : (def === undefined ? '' : def);
  }

  function getUser() {
    try { return JSON.parse(localStorage.getItem('cs_user') || 'null'); }
    catch (e) { return null; }
  }

  /** 没登录就踢回登录页。每个业务页面开头调一次 */
  function requireLogin() {
    var t = null;
    try { t = localStorage.getItem('cs_token'); } catch (e) {}
    if (!t) { location.replace('login.html'); return false; }
    return true;
  }

  window.SM = {
    ORDER_STATUS_TEXT: ORDER_STATUS_TEXT,
    ORDER_STATUS_CLS: ORDER_STATUS_CLS,
    TICKET_STATUS_TEXT: TICKET_STATUS_TEXT,
    TICKET_STATUS_CLS: TICKET_STATUS_CLS,
    TRANSITIONS: TRANSITIONS,
    TRANSITION_UI: TRANSITION_UI,
    allowed: function (from) { return (TRANSITIONS[from] || []).slice(); }
  };

  window.UI = {
    esc: esc, money: money, money0: money0, dt: dt, ago: ago,
    orderBadge: orderBadge, ticketBadge: ticketBadge,
    eligibility: eligibility, eligBadge: eligBadge, confBar: confBar,
    toast: toast, shell: shell, openDrawer: openDrawer, closeDrawer: closeDrawer,
    pager: pager, emptyRow: emptyRow, loadingRow: loadingRow, apiHint: apiHint,
    $: $, $$: $$, qs: qs, getUser: getUser, requireLogin: requireLogin
  };
})();
