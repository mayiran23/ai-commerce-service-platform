# 前端（原生 HTML / CSS / JS）

四个页面，零依赖、零构建、零 CDN —— 拷到服务器就能跑，断网也能演示。

技术选型理由见项目总文档 1.2 节「v1 前端用原生 HTML，不用 Vue3」：
你前端零基础，3 周内跑通比好看重要。**一个能跑的丑页面 >> 一个跑不起来的漂亮设计稿。**

---

## 怎么打开

### 方式一：直接双击（最快，看效果用）

双击 `login.html` 就行。此时是 **Mock 模式**，页面用内置假数据渲染，
不依赖后端，也不需要起服务。

> 缺点：浏览器的 `file://` 协议下无法发真实网络请求。
> 所以要接后端时必须用下面的方式二或三。

### 方式二：本地起个静态服务（开发推荐）

```bash
cd frontend
python -m http.server 5500
# 浏览器打开 http://localhost:5500/login.html
```

### 方式三：nginx（部署形态，也是苍穹外卖那套）

本机已经装好，目录：`D:\mayiran-work\tools\nginx-1.30.4`

| 想干什么 | 怎么做 |
|---|---|
| 启动 | 双击 nginx 目录里的 `nginx-start.bat` |
| 停止 | 双击 `nginx-stop.bat` |
| 改完配置生效 | 双击 `nginx-reload.bat`（会先检查语法，语法错就不重载） |
| 打开页面 | 浏览器访问 **http://localhost:8081** |

手工敲命令也行（要在 nginx 目录下执行）：

```bash
nginx.exe            # 启动
nginx.exe -t         # 只检查配置语法，不重启
nginx.exe -s reload  # 重载配置（不中断连接）
nginx.exe -s quit    # 优雅停止
```

> **为什么是 8081 不是 80？** 本机 80 端口被别的程序占了（同时监听 80/443）。
> 换 8081 避开，效果一样。以后部署到服务器时再改回 80。

nginx 配置文件分两份：

- **实际生效的**：`D:\mayiran-work\tools\nginx-1.30.4\conf\nginx.conf`
- **仓库里这份示例**：`nginx.conf.example`（给 Ubuntu 服务器部署用的模板，root 指向 `/var/www/...`）

nginx 同时托管静态文件并把 `/api` 反代到 8080，前后端同源，不用配 CORS。
等 Spring Boot 起来了，这里的 `/api/xxx` 会自动打到后端，前端代码一个字都不用改。

---

## 测试账号

密码统一 `123456`（数据库里存的是 BCrypt 密文）。登录页有快捷填充按钮。

| 用户名 | 姓名 | 角色 | 登录后进入 |
|---|---|---|---|
| `user0001` | 谢雨欣 | 普通用户 USER | 订单管理（只能看到自己的订单） |
| `user0108` | 郭思远 | 客服 AGENT | 售后工单 |
| `user0092` | 何皓宇 | 管理员 ADMIN | 售后工单 |

三个角色的导航菜单不一样 —— 这是权限设计在界面上的体现，不是写死的。

---

## 目录结构

```
frontend/
├── login.html            登录页
├── orders.html           订单管理（列表 / 筛选 / 详情抽屉）
├── tickets.html          售后工单（列表 / 状态机流转 / 流转时间线）
├── chat.html             智能客服（会话 / 对话 / 工具调用可视化）
├── nginx.conf.example    部署用的 nginx 配置示例
├── README.md             本文件
└── assets/
    ├── app.css           全站样式（改颜色只改顶部 :root 变量）
    ├── ui.js             状态字典 + 状态机 + 格式化 + 通用组件
    ├── api.js            ★ 请求层，含 USE_MOCK 开关
    ├── mock.js           Mock 路由：模拟后端返回什么
    └── mock_data.js      真实订单数据（由 scripts/gen_mock.py 生成，勿手改）
```

---

## 怎么切成真实后端

**只需要改一个地方**：`assets/api.js` 里的

```javascript
var USE_MOCK = true;      // true = 假数据；false = 请求真后端
```

改成 `false` 后，页面的所有数据就来自 Spring Boot 了。

**建议一个接口一个接口地切**，不要等后端全写完。比如订单接口写好了、
工单还没写，那就整体切 `false`，订单页能正常用，工单页会弹错误提示——
这不影响你验证订单页。出问题时也更容易定位。

**怎么确认当前用的是哪边？** 页面右上角有一行小字：
mock 模式显示「Mock 数据（后端未接入）」，真实模式显示后端地址。

后端地址也在 `api.js` 里：

```javascript
var API_BASE = '';                        // 走 nginx 同源（推荐）
var API_BASE = 'http://localhost:8080';   // 直连后端（后端要开 CORS）
```

### 联调时登录怎么办：`dev-login.html`

`login.html` 走 `POST /api/auth/login`，这个接口**还没写**（排在 9/17）。
但 `UI.requireLogin()` 只检查 localStorage 里有没有 `cs_token`，
后端也没有拦截器去校验 —— 所以直接打开

```
http://localhost:8081/dev-login.html
```

点一个身份（客服 / 管理员 / 普通用户）即可进入，不需要密码。

> ⚠️ 这是**开发脚手架**。等 9/17 登录接口 + JWT 拦截器写完就删掉，别带上生产。
> 用 `location.href='login.html'` 旁边那个「清除登录态」按钮可以退出。

完整的联调步骤、能/不能看到什么、排查顺序，见
`docs/ORDER_API_HANDOUT.md` 第 9 节。

---

## Mock 数据从哪来的

`assets/mock_data.js` **不是手写的**，是脚本从数据库里真实订单导出的：

```bash
cd ..                                     # 回到项目根目录
.venv\Scripts\python scripts\gen_mock.py  # 重新生成
```

导出时会做一次**时间平移**：把全库时间整体移动，使退货截止时间正好
卡在"现在"附近。这样**不管哪天演示**，页面上都能同时看到
「有资格 / 即将到期 / 已超期」三档，而且不会出现未来时间。

> 页面上的订单号、商品名、金额、收货地址全都是库里真实的。
> 等后端接上以后，这个文件就完成使命了，删掉也不影响（`mock.js` 里对它有兜底）。

---

## 前端和后端的"契约"

**所有接口的定义在 `docs/API.md`** —— 那是唯一的约定来源。

前端已经把这 13 个外部接口全部用上了，每个页面底部都有一行提示条写着
"本页对应接口"。后端按那份文档实现，这里改一个布尔值就能接上。

### 状态机是两边共享的规则

`assets/ui.js` 里的 `SM.TRANSITIONS` 定义了工单允许的流转关系：

```javascript
var TRANSITIONS = {
  PENDING:       ['APPROVED', 'REJECTED', 'MANUAL_REVIEW', 'CANCELLED'],
  MANUAL_REVIEW: ['APPROVED', 'REJECTED', 'CANCELLED'],
  APPROVED:      ['REFUNDING'],
  REFUNDING:     ['COMPLETED'],
  COMPLETED:     [], REJECTED: [], CANCELLED: []
};
```

前端用它决定**显示哪几个按钮**，后端用同一套规则做**真正的校验**。

**两边内容必须一致，但后端才是权威。** 前端就算被改坏（比如手动加一个按钮），
后端也必须拦住非法流转。这一点在答辩/面试时可以说，是"有安全意识"的体现。
