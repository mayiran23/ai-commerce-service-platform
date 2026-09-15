/* ============================================================
   请求层：页面只调 Api.get / Api.post，不关心底层是真是假
   ------------------------------------------------------------
   ★ 后端写好后，你只需要改下面这一个开关 ★
   ============================================================ */
(function () {
  'use strict';

  /* ============================================================
     ★★★ 唯一的开关：true = 用假数据，false = 请求真后端 ★★★
     ------------------------------------------------------------
     开发阶段建议：
       1. 后端还没写接口 → 保持 true，先把页面和交互看明白
       2. 写完一个接口 → 把它对应的页面改成 false 试一次，
          通了再继续写下一个（不要等全部写完才联调）
       3. 全部通了 → 改成 false，mock.js 就可以不再管了
     ============================================================ */
  var USE_MOCK = false;

  /* 后端地址。
     留空字符串 = 同源，由 nginx 把 /api 转发到 8080（推荐，也是苍穹外卖的做法）。
     如果直接双击 HTML 打开（file://），或者不想配 nginx，
     就填 'http://localhost:8080'，但后端要开 CORS。 */
  var API_BASE = '';

  /* 请求头里带 JWT 的字段名。要和 Java 拦截器里读的保持一致。 */
  var TOKEN_HEADER = 'Authorization';
  var TOKEN_PREFIX = 'Bearer ';

  var MOCK_DELAY_MS = 220;   // 假数据加一点延迟，能看到"加载中"的效果

  /* ---------------- 本地存储（带兜底，隐私模式下 localStorage 会报错） ---------------- */
  var mem = {};
  function lsGet(k) {
    try { return localStorage.getItem(k); } catch (e) { return mem[k] || null; }
  }
  function lsSet(k, v) {
    try { localStorage.setItem(k, v); } catch (e) { mem[k] = v; }
  }
  function lsDel(k) {
    try { localStorage.removeItem(k); } catch (e) { delete mem[k]; }
  }

  /* ---------------- 统一响应处理 ---------------- */
  /**
   * 后端返回的固定格式（docs/API.md 定义）：
   *   { code: 1, msg: "", data: {...} }      code=1 成功
   *   { code: 0, msg: "订单不存在", data: null }
   * 这一层把 code/msg 剥掉，页面只拿到 data；
   * 失败就 throw，页面 catch 到 e.message 直接弹提示。
   */
  function unwrap(result) {
    if (!result || typeof result !== 'object') {
      throw new Error('返回格式不对，期望 {code,msg,data}');
    }
    if (result.code !== 1) {
      var err = new Error(result.msg || '请求失败');
      err.code = result.code;
      throw err;
    }
    return result.data;
  }

  /* ---------------- 真正的网络请求 ---------------- */
  function http(method, path, query, body) {
    var url = API_BASE + path;
    if (query) {
      var pairs = [];
      Object.keys(query).forEach(function (k) {
        if (query[k] !== undefined && query[k] !== null && query[k] !== '') {
          pairs.push(encodeURIComponent(k) + '=' + encodeURIComponent(query[k]));
        }
      });
      if (pairs.length) url += (url.indexOf('?') >= 0 ? '&' : '?') + pairs.join('&');
    }

    var headers = { 'Content-Type': 'application/json' };
    var token = lsGet('cs_token');
    if (token) headers[TOKEN_HEADER] = TOKEN_PREFIX + token;

    return fetch(url, {
      method: method,
      headers: headers,
      body: body ? JSON.stringify(body) : undefined
    }).then(function (res) {
      if (res.status === 401) {
        logout();
        var e = new Error('登录已过期，请重新登录');
        e.code = 401;
        throw e;
      }
      return res.json();
    }).then(unwrap);
  }

  /* ---------------- Mock 请求 ---------------- */
  function mocked(method, path, query, body) {
    return new Promise(function (resolve, reject) {
      setTimeout(function () {
        try {
          // 注意：这里返回的是 {code,msg,data}，和真后端一模一样，
          // 所以走的是同一个 unwrap，页面代码不需要区分。
          resolve(unwrap(window.MOCK.handle(method, path, query, body)));
        } catch (e) {
          reject(e);
        }
      }, MOCK_DELAY_MS);
    });
  }

  /* ---------------- 对外接口 ---------------- */
  function request(method, path, query, body) {
    return USE_MOCK ? mocked(method, path, query, body) : http(method, path, query, body);
  }

  function login(username, password) {
    return request('POST', '/api/auth/login', null, { username: username, password: password })
      .then(function (data) {
        lsSet('cs_token', data.token);
        lsSet('cs_user', JSON.stringify(data.user));
        return data.user;
      });
  }

  function logout() {
    lsDel('cs_token');
    lsDel('cs_user');
    location.replace('login.html');
  }

  function getUser() {
    try { return JSON.parse(lsGet('cs_user') || 'null'); } catch (e) { return null; }
  }

  /** 给页面顶部显示用：让用户一眼看到现在是真后端还是假数据 */
  function backendLabel() {
    return USE_MOCK ? 'Mock 数据（后端未接入）' : ('真实后端 ' + (API_BASE || location.origin));
  }

  window.Api = {
    get: function (p, q) { return request('GET', p, q); },
    post: function (p, b, q) { return request('POST', p, q, b); },
    put: function (p, b, q) { return request('PUT', p, q, b); },
    del: function (p, q) { return request('DELETE', p, q); },
    login: login, logout: logout, getUser: getUser,
    backendLabel: backendLabel,
    isMock: function () { return USE_MOCK; }
  };
})();
