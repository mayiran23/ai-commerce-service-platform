# 订单接口开发手记

> 用途：你要写订单接口时，对着这份看「入参是什么、出参是什么、怎么写」。代码由你自己写。
> 契约的权威版本是 `docs/API.md` 第 5 节和第 8.1 / 8.2 节，本文只是把它翻译成"实现步骤"。
> 制定于 2026-09-15。

---

## 0. 先纠正一个概念：这 5 个接口都没有"请求体"

"请求体"（request body）是 **POST / PUT** 才有的东西 —— 就是放在 JSON 里发给后端的那一坨数据。

订单这 5 个接口**全是 GET**（只读），所以：

| | 参数放哪 | 有没有 body |
|---|---|---|
| GET 接口（订单这 5 个） | URL 的 `?` 后面，叫 **query string** | **没有** |
| POST 接口（后面的登录、建工单） | 请求体 JSON 里 | 有 |

所以下面我只讲**入参**（query 参数 / 路径参数）和**出参**（响应体）。

另外三条铁律，写之前先记牢：

1. **`/api/**` 的出参一律包 `Result`**（`code` / `msg` / `data`），`/internal/**` **不包**，直接返回业务对象。
2. **字段一律驼峰**（`orderNo`），数据库是下划线（`order_no`）。靠 `application.properties` 里那行 `map-underscore-to-camel-case=true` 自动转 —— 你已经加了，不加就全是 `null`。
3. **全是查询，一个 `@Transactional` 都不需要**。用不到事务的地方别加。

---

## 1. 订单模块一共 5 个接口

| 序 | 接口 | 给谁用 | 干什么 | 计划 |
|---|---|---|---|---|
| ① | `GET /api/orders` | 前端 | 订单分页列表（带明细 + 物流） | **9/15–9/16 ← 今天的活** |
| ② | `GET /api/orders/{orderNo}` | 前端 | 单个订单详情 | 9/19 |
| ③ | `GET /api/stats/orders` | 前端 | 状态统计卡片 | 9/20 |
| ④ | `GET /internal/orders/{orderNo}` | Python AI | 同上，但**不包 Result** | 阶段三 |
| ⑤ | `GET /internal/orders/search` | Python AI | 按用户/状态/关键词搜单 | 阶段三 |

**关键认识：真正要写的东西只有一份。**

- ① 和 ② 共用同一套「组装订单」的逻辑（②只是把①的结果取一条）
- ④ = ② 去掉 `Result` 外壳
- ⑤ = ① 去掉 `Result` 外壳、去掉分页

所以你先老老实实把 ① 写透，后面四个是顺水推舟。

---

## 2. 入参（请求参数）

### ① `GET /api/orders`

| 参数 | 类型 | 必填 | 默认 | 说明 |
|---|---|---|---|---|
| `page` | int | 否 | `1` | 第几页，从 1 开始 |
| `limit` | int | 否 | `10` | 每页条数，**上限卡 100** |
| `keyword` | string | 否 | — | 模糊匹配 **订单号 / 用户昵称 / 商品名**，三者任一命中 |
| `status` | string | 否 | — | 订单状态枚举，见 `API.md` 2.1 |
| `userId` | long | 否 | — | 按用户筛。**USER 角色传了也无效，后端强制用自己的 id** |
| `eligibility` | string | 否 | — | `eligible` / `soon` / `expired` |

> 今天（9/15）只做 `limit` 一个参数就够 —— 先把链路跑通，明天再补其余。

### ② `GET /api/orders/{orderNo}`

| 参数 | 位置 | 说明 |
|---|---|---|
| `orderNo` | **路径参数**（写在 URL 里） | 订单号，如 `SO2026090900353`。**不是 id** |

### ③ `GET /api/stats/orders`

无参数。

### ④ `GET /internal/orders/{orderNo}`

| 参数 | 位置 | 必填 | 说明 |
|---|---|---|---|
| `orderNo` | 路径 | 是 | 订单号 |
| `userId` | query | **是** | 用来校验这单是不是这个用户的 |

### ⑤ `GET /internal/orders/search`

| 参数 | 必填 | 说明 |
|---|---|---|
| `userId` | 是 | 用户 ID |
| `status` | 否 | 订单状态 |
| `keyword` | 否 | 商品名模糊 |
| `limit` | 否 | 默认 5 |

---

## 3. 出参（响应体）

### ① `GET /api/orders`

```json
{
  "code": 1,
  "msg": "",
  "data": {
    "total": 57,
    "records": [
      {
        "orderNo": "SO2026090900353",
        "userId": 1,
        "userNickname": "谢雨欣",
        "userPhone": "13094475781",
        "status": "RECEIVED",
        "statusText": "已签收",
        "totalAmount": 583.86,
        "payAmount": 583.86,
        "payTime": "2026-09-14 09:12:00",
        "shipTime": "2026-09-14 15:30:00",
        "receiveTime": "2026-09-14 20:54:11",
        "receiverName": "徐雅静",
        "receiverPhone": "13005697472",
        "receiverAddr": "广东省深圳市南山区科苑南路 2666 号",
        "createTime": "2026-09-14 08:54:11",
        "items": [
          {
            "productId": 24,
            "productName": "折叠收纳箱",
            "price": 86.98,
            "quantity": 2,
            "refundDeadline": "2026-09-21 20:54:11",
            "refundEligible": true
          }
        ],
        "logistics": {
          "trackNo": "SF062649192963",
          "currentStatus": "已签收",
          "currentNode": "已签收，签收人：本人",
          "estimatedArrival": "2026-09-12"
        }
      }
    ]
  }
}
```

字段说明（重点看后三行）：

| 字段 | 说明 |
|---|---|
| `total` | **符合条件的总条数**，不是当前页条数。前端靠它算总页数 |
| `records` | 当前页数据 |
| `status` + `statusText` | **两个都要返回**。前端 `status` 用来判断，`statusText` 用来显示 |
| `refundEligible` | **必须由你（Java）算**：`refundDeadline > 现在`。逐件商品各算各的，**不能整单算一个** |
| `logistics` | 未发货的订单返回 **`null`**，前端会显示"暂无物流信息" |
| `items` | 列表接口也要返回，前端要显示商品摘要和退货徽标 |

### ② `GET /api/orders/{orderNo}`

`data` 就是上面 `records` 里的**单个元素**，结构完全一样。

### ③ `GET /api/stats/orders`

```json
{
  "code": 1,
  "msg": "",
  "data": {
    "total": 57,
    "byStatus": {
      "PENDING_PAY": 8,
      "PAID": 8,
      "SHIPPED": 8,
      "DELIVERING": 8,
      "RECEIVED": 25,
      "CANCELLED": 0
    }
  }
}
```

> `byStatus` 建议**补齐全部 6 个状态**，没有的填 0。这样前端不用写兜底逻辑。
> （`total` 是 `byStatus` 各项之和，前端 KPI 卡片直接用。）

### ④ ⑤ 两个内部接口

**注意：不包 `Result`**，直接返回业务对象。因为调用方是程序不是人，`code/msg` 这层壳没有意义，出错用 HTTP 状态码表达。

```json
{
  "orderNo": "SO2026090900353",
  "status": "RECEIVED",
  "statusText": "已签收",
  "payAmount": 583.86,
  "payTime": "2026-09-14 09:12:00",
  "receiveTime": "2026-09-14 20:54:11",
  "logistics": {
    "trackNo": "SF062649192963",
    "currentStatus": "已签收",
    "currentNode": "已签收，签收人：本人",
    "estimatedArrival": "2026-09-12"
  },
  "items": [
    {
      "productId": 43,
      "productName": "丝绒口红套装",
      "price": 204.95,
      "quantity": 2,
      "refundDeadline": "2026-09-21 20:54:11",
      "refundEligible": true
    }
  ]
}
```

⑤ 的响应是**这个对象的数组**，并且可以省略 `logistics`（省一次查询）。

---

## 4. 要新建哪些类

你现在有 `Order` 实体、`Result`、`PageResult`。订单模块还需要这些：

| 类 | 放哪 | 干什么 | 什么时候建 |
|---|---|---|---|
| `OrderItem` | `entity` | 对应 `t_order_item` 表 | 9/16 加 items 时 |
| `OrderLogistics` | `entity` | 对应 `t_order_logistics` 表 | 9/16 |
| `OrderVO` | `vo`（新建包） | 返回给前端的订单：实体字段 + 昵称/手机 + `statusText` + `items` + `logistics` | 9/16 |
| `OrderItemVO` | `vo` | 明细 + 多出来的 `refundEligible` | 9/16 |
| `LogisticsVO` | `vo` | 物流（字段和 `OrderLogistics` 一样，可以先直接用实体不建 VO） | 9/16 |
| `OrderStatus` | `enums`（新建包） | 状态枚举：值 + 中文，**替代一堆 if-else** | 9/16 |
| `StatsController` | `controller` | 因为 `/api/stats/orders` 不挂在 `/api/orders` 下，现有 `OrderController` 的类注解表达不了 | 9/20 |

**为什么不直接把 `items` 塞进 `Order` 实体？**
实体是"数据库一张表的镜子"，只该有表里的列。`userNickname`（在 `t_user`）、`statusText`（算出来的）、`refundEligible`（算出来的）、`items`（另一张表）都不是 `t_order` 的列。把它们塞进实体的后果：以后往表里插数据时，MyBatis 会拿着这些不存在的列去 INSERT，直接报错。

**VO 就是干这个的**：Entity 管"存"，VO 管"展示"。这个区分面试常问，值得在简历里提一句。

> **今天（9/15）的极简版不需要任何 VO**：只查 `t_order` 主表、只返回 `limit` 条，`List<Order>` 直接丢进 `PageResult` 就行。
> VO 是明天的事。**先跑通，再求全。**

**已经踩到的两个坑（写 VO 时注意）**

1. **`logistics` 是单个对象，不是 `List`。** 契约里它就是 `"logistics": { ... }`，
   前端 `orders.html` 写的是 `o.logistics.currentNode`，直接当对象取字段。
   写成 `List<OrderLogistics>` 的话前端拿不到 `currentNode`，物流那一段会空掉。
   （库里一张订单可能有多条轨迹，但接口只返回**最新那一条**。）
2. **`@Builder` 会让字段的初始化表达式失效。** `private List<OrderItem> items = new ArrayList<>();`
   配上 `@Builder` 之后，用 `OrderVO.builder().build()` 造出来的对象 `items` 是 **`null`**，不是空列表 ——
   前端遍历 `o.items` 直接报错。编译器会给出警告
   `@Builder will ignore the initializing expression entirely`。两条路：
   加 `@Builder.Default` 保留默认值，或者**干脆去掉 `@Builder`**（这个 VO 是查出来用 setter 赋值的，用不上 Builder）。

---

## 5. 实现思路（核心部分）

### 5.0 ① 接口到底要查出什么（查询清单）

**一句话**：前端表格和详情抽屉里会显示的每一个值，你都要有来源。数一遍就是 **4 张表 + 2 个 Java 计算**。

#### A. 要查的 4 张表

| # | 表 | 取哪些列 | 为什么要它 | 怎么查 |
|---|---|---|---|---|
| 1 | `t_order` | `order_no, user_id, status, total_amount, pay_amount, pay_time, ship_time, receive_time, receiver_name, receiver_phone, receiver_addr, create_time` | 订单主体，表格和抽屉的主干 | **主查询**，PageHelper 的 `LIMIT` 只作用在这一条 |
| 2 | `t_order_item` | `order_no, product_id, product_name, product_price, quantity, refund_deadline` | 抽屉里的商品明细 + 退货资格 | 收集本页 `orderNo` → 一次 `IN` 查完 |
| 3 | `t_order_logistics` | `order_no, track_no, current_status, current_node, estimated_arrival` | 抽屉里的物流快照（**每单只要一条：最新那条**） | 同上，一次 `IN` 查完，再在 Service 里取每组最新 |
| 4 | `t_user` | `id, nickname` | 列表的「用户昵称」列；`keyword` 要按昵称搜 | **建议直接 LEFT JOIN 进主查询**（原因见 D） |

#### B. 不用查、Java 自己算的 2 个

| 字段 | 算法 | 注意 |
|---|---|---|
| `refundEligible` | `refundDeadline != null && refundDeadline.isAfter(now)` | **逐件商品各算各的**，不能整单算一个 |
| `statusText` | `status` → 中文（`OrderStatus` 枚举） | 别写一串 if-else |

> 前端 `ui.js` 自己带了一份状态字典，所以 `statusText` 现在**不加也能跑**；但契约里写了要返回，加上更规范（以后换前端不用改）。

#### C. 要改名的一处

数据库 `t_order_item.product_price` → 前端要的是 `price`。用 SQL 别名 `product_price AS price`，或建一个 `OrderItemVO` 放 `price` 字段（**推荐后者**，因为 `refundEligible` 也没地方放）。

#### D. 为什么 `t_user` 建议 JOIN 进主查询

因为入参 `keyword` 要「匹配订单号 / 用户昵称 / 商品名，任一命中」，三样东西分别在两张表里：

```sql
SELECT o.order_no, o.user_id, u.nickname AS userNickname, o.status, o.total_amount, ...
FROM t_order o
LEFT JOIN t_user u ON u.id = o.user_id
WHERE 1 = 1
  <if test="status != null and status != ''"> AND o.status = #{status} </if>
  <if test="userId  != null and userId  != ''"> AND o.user_id = #{userId} </if>
  <if test="keyword != null and keyword != ''">
    AND ( o.order_no LIKE CONCAT('%', #{keyword}, '%')
       OR u.nickname LIKE CONCAT('%', #{keyword}, '%')
       OR EXISTS (SELECT 1 FROM t_order_item i
                  WHERE i.order_no = o.order_no
                    AND i.product_name LIKE CONCAT('%', #{keyword}, '%')) )
  </if>
ORDER BY o.create_time DESC
```

`LEFT JOIN t_user` 是 **1:1**，不会让行数膨胀，所以 PageHelper 放在这条 SQL 上是安全的。
对比：JOIN `t_order_item` 是**一对多**，会把分页切错（见 5.3）。

#### E. 所以 Mapper 不是一个方法，是 3 个

你现在写的是 `Page<OrderVO> pageOrders(OrderPageDTO)` —— 一个方法想同时带出平铺的订单字段和嵌套的 `items` / `logistics`。**一条 SQL 做不到**（除非用 MyBatis 嵌套 resultMap，那又会踩 5.3 的分页坑）。

拆成三个：

| 方法 | 返回 | 谁调 | 说明 |
|---|---|---|---|
| `selectPage(OrderPageDTO)` | `Page<OrderVO>` | Service 第 1 步 | 只填**订单主体 + userNickname**，`items`/`logistics` 暂时留空 |
| `selectItemsByOrderNos(List<String>)` | `List<OrderItem>` | Service 第 2 步 | `WHERE order_no IN (...)` |
| `selectLogisticsByOrderNos(List<String>)` | `List<OrderLogistics>` | Service 第 3 步 | `WHERE order_no IN (...)` |

> 主查询仍返回 `Page<OrderVO>` 的原因：`total` 可以直接从 `PageInfo` 拿，不用为"主表结果"再单独定义一个类。

#### F. 对照你现在写的 `OrderVO`，缺 5 样

| 缺什么 | 现状 | 怎么补 |
|---|---|---|
| `userNickname` | 没有 | 主查询 JOIN `t_user`，别名写 `userNickname` |
| `createTime` | 没有 | 主查询带上（表格「下单时间」列在用） |
| `statusText` | 没有 | 枚举翻译后 set 进去 |
| `logistics` 类型 | `List<OrderLogistics>` | 改成**单个 `OrderLogistics`**（前端是当对象用的：`o.logistics ? ... : ''`） |
| 明细的 `price` / `refundEligible` | `OrderItem` 里叫 `productPrice`，且没有 `refundEligible` | 建 `OrderItemVO`，别往 entity 上塞业务字段 |

另外 `OrderVO` 上那个 `@Builder` + `items = new ArrayList<>()` 的组合会让 `builder().build()` 造出来的 `items` 是 `null`（不是空列表）。要么删 `@Builder`，要么给字段加 `@Builder.Default`。

#### G. 极简版 vs 完整版

| | 今天（先跑通） | 明天（补全） |
|---|---|---|
| 查几张表 | **只查 `t_order`** | 4 张 |
| Mapper 方法数 | 1 个 | 3 个 |
| `items` / `logistics` | 空数组 / `null` | 真实数据 |
| `refundEligible` / `statusText` | 不做 | 做 |
| 通关证据 | `?limit=5` 出 5 条，`orderNo`、`totalAmount` 非 null | 抽屉里能看到商品和物流 |

#### H. 前端字段 ↔ SQL 输出 逐列对照（写 XML 时对着抄）

`resultType="OrderVO"` 靠**名字**匹配：列名（或别名）去掉下划线转驼峰后，必须等于 `OrderVO` 的属性名。

> ⚠️ 一个前提：`resultType` **只能做平铺映射**。`items` / `logistics` 这种嵌套集合它填不了
> —— 所以你这条空着的 `select id="pageOrders"` 只能填「订单主体」，明细和物流必须是**另外两条 select**。

**第 1 条：主查询（`t_order` LEFT JOIN `t_user`）→ 填订单主体**

| 前端字段 | SQL 列 / 别名 | 说明 |
|---|---|---|
| `orderNo` | `o.order_no` | 转驼峰即可，无需别名 |
| `userId` | `o.user_id` | |
| `userNickname` | `u.nickname AS userNickname` | ⚠️ **必须起别名**，否则会去找 `nickname` 属性（不存在） |
| `status` | `o.status` | 英文枚举，前端自己翻译 |
| `totalAmount` | `o.total_amount` | |
| `payAmount` | `o.pay_amount` | |
| `payTime` | `o.pay_time` | |
| `shipTime` | `o.ship_time` | |
| `receiveTime` | `o.receive_time` | |
| `receiverName` | `o.receiver_name` | |
| `receiverPhone` | `o.receiver_phone` | |
| `receiverAddr` | `o.receiver_addr` | |
| `createTime` | `o.create_time` | 表格「下单时间」列在用；`OrderVO` 现在没这个字段，**要加** |

```sql
SELECT o.order_no, o.user_id, u.nickname AS userNickname, o.status,
       o.total_amount, o.pay_amount, o.pay_time, o.ship_time, o.receive_time,
       o.receiver_name, o.receiver_phone, o.receiver_addr, o.create_time
FROM t_order o
LEFT JOIN t_user u ON u.id = o.user_id
WHERE 1 = 1
  <if test="status != null and status != ''">AND o.status = #{status}</if>
  <if test="userId  != null and userId  != ''">AND o.user_id = #{userId}</if>
  <if test="keyword != null and keyword != ''">
    AND ( o.order_no LIKE CONCAT('%', #{keyword}, '%')
       OR u.nickname LIKE CONCAT('%', #{keyword}, '%')
       OR EXISTS (SELECT 1 FROM t_order_item i
                  WHERE i.order_no = o.order_no
                    AND i.product_name LIKE CONCAT('%', #{keyword}, '%')) )
  </if>
ORDER BY o.create_time DESC
```

**第 2 条：明细（`t_order_item`）→ 填 `items`**

| 前端字段 | SQL 列 / 别名 | 说明 |
|---|---|---|
| `productId` | `product_id` | |
| `productName` | `product_name` | |
| `price` | `product_price AS price` | ⚠️ 库里叫 `product_price`，前端要 `price`，**必须起别名** |
| `quantity` | `quantity` | |
| `refundDeadline` | `refund_deadline` | |
| `refundEligible` | —— 不查 | Java 算：`refundDeadline` 是否晚于现在 |

**第 3 条：物流（`t_order_logistics`）→ 填 `logistics`**

| 前端字段 | SQL 列 | 说明 |
|---|---|---|
| `trackNo` | `track_no` | |
| `currentStatus` | `current_status` | |
| `currentNode` | `current_node` | |
| `estimatedArrival` | `estimated_arrival` | `DATE` → Java 侧用 `LocalDate` |

> **一句话自检**：明细那条 select 的字段名要对齐**上表**，不是照抄 `t_order_item` 的列名
> （`product_price` ≠ `price` 就是现成的例子）。`userNickname` 同理。

### 5.1 现在动手：照着勾的 8 步（2026-09-15 晚更新）

> 你**已经有的**：`OrderPageDTO`、`OrderVO`、`OrderMapper`（接口，返回 `Page<OrderVO>`）、
> `OrderMapper.xml`（**空的 `pageOrders`**）、`OrderService`、`OrderServiceImpl`（已写分页）、
> `OrderController`（已接好）、PageHelper 4.1.1。
> 也就是说：**管道全接好了，只差 XML 里那条 SQL 和几个字段。** 下面按顺序来。

---

#### 阶段一：今晚的目标 = 列表出 5 条真数据（5 步）

**第 1 步 · `application.properties` 加一行**

```properties
mybatis.mapper-locations=classpath*:/mapper/**/*.xml
```

> **不加上会怎样**：启动一声不响，一访问接口就报
> `Invalid bound statement (not found): ...OrderMapper.pageOrders`。
> 因为 `mapper-locations` **没有默认值**，XML 根本不会被加载。

**第 2 步 · `OrderPageDTO` 给两个字段默认值**

```java
private int page = 1;     // 不传也要是第 1 页
private int limit = 10;   // 不传也要有 10 条
```

> **不加上会怎样**：`int` 字段不传就是 `0`，`PageHelper.startPage(0, 0)` 会生成
> `LIMIT 0,0` → **返回空数组，一个错都不报**。这是最难查的一类问题。

**第 3 步 · `OrderVO` 补两个字段**

```java
private String userNickname;      // 来源 t_user.nickname，SQL 里必须 AS userNickname
private LocalDateTime createTime; // 来源 t_order.create_time，表格「下单时间」要显示
```

> 前端 `orders.html` 实际读的字段我列全了（见 5.0.H），就缺这两个。

**第 4 步 · `OrderMapper.xml` 把 `pageOrders` 填上**

```xml
<select id="pageOrders" resultType="com.mayiran.commerceservice.vo.OrderVO">
    SELECT o.order_no, o.user_id, u.nickname AS userNickname, o.status,
           o.total_amount, o.pay_amount, o.pay_time, o.ship_time, o.receive_time,
           o.receiver_name, o.receiver_phone, o.receiver_addr, o.create_time
    FROM t_order o
    LEFT JOIN t_user u ON u.id = o.user_id
    ORDER BY o.create_time DESC
</select>
```

**三个要点：**

| 要点 | 说明 |
|---|---|
| **不要写 LIMIT** | PageHelper 会自己往上加。你手写就成了 `LIMIT 10 LIMIT 0,10`，直接 SQL 语法错 |
| **`AS userNickname` 不能省** | 不起别名，MyBatis 去找 `nickname` 属性，找不到 → `userNickname` 恒为 `null` |
| **`ORDER BY` 要写** | 分页必须有稳定排序，否则翻页会出现重复/漏单 |

> 这条 SQL 是**严格子集**：后端要的 11 个字段 + `userNickname` + `createTime`。
> 只要前端读的字段都在 `SELECT` 里，就不算漏。

**第 5 步 · 启动并验证**

```bash
curl -s "http://localhost:8080/api/orders?limit=5"
```

**通关证据（唯一硬指标）**：5 条 JSON，且 `orderNo` / `totalAmount` / `userNickname` 都有值。

> 此时 `items` 和 `logistics` 是空的 —— **这是正常的**，阶段二补。

---

#### 阶段二：把 `items` / `logistics` 填上（补齐 ① 才算完整）

**第 6 步 · 新建 `OrderItemVO`（放 `vo` 包）**

前端读的明细字段是 `price` 和 `refundEligible`，但实体 `OrderItem` 里叫 `productPrice`、
也没有 `refundEligible`。**别往实体里加**（实体只该是表的镜子），新建一个：

| 字段 | 类型 | 来源 |
|---|---|---|
| `productId` / `productName` | `Long` / `String` | 表列直接映射 |
| `price` | `BigDecimal` | `product_price AS price` |
| `quantity` | `Integer` | 表列直接映射 |
| `refundDeadline` | `LocalDateTime` | 表列直接映射 |
| `refundEligible` | `Boolean` | **Java 算**：`refundDeadline.isAfter(now)` |

**第 7 步 · 改 `OrderVO` 两个字段的类型**

```java
private List<OrderItemVO> items = new ArrayList<>();  // 从 List<OrderItem> 改过来
private OrderLogistics logistics;                     // 从 List<OrderLogistics> 改成「单个对象」
```

> `logistics` 前端是**当对象用**的（`o.logistics.currentNode`），返回数组取不到值。
> 库里一单可能有多条轨迹，接口**只返回最新那条**。

**第 8 步 · 补两条子查询 + Service 组装**

`OrderMapper` 再加两个方法（参数都是 `List<String> orderNos`）：

```java
List<OrderItemVO> listItemsByOrderNos(@Param("orderNos") List<String> orderNos);
List<OrderLogistics> listLogisticsByOrderNos(@Param("orderNos") List<String> orderNos);
```

XML 里对应两条 `<select>`，字段照 **5.0.H** 那两张表；（注意 `product_price AS price`）。
`OrderServiceImpl` 里：查完主表 → 收集 `orderNo` → 两次 `IN` 查完 → 用
`Map<String, List<...>>` 按 `orderNo` 分组 → 遍历挂回去。具体见 **5.2 节**。

---

#### 顺手清理（不影响运行，但别留着）

| 位置 | 问题 |
|---|---|
| `OrderService` 接口 | `import org.springframework.beans.factory.annotation.Autowired;` 没用上，删 |
| `OrderController` | `import ...impl.OrderServiceImpl;` 和 `import ...entity.Order;` 没用上，删 |
| `OrderController` | 返回类型建议写成 `Result<PageResult<OrderVO>>`，别用裸 `PageResult` |

> 三层是"流水线"：Controller 收请求不干活，Service 干活，Mapper 只碰 SQL。
> 别把 SQL 写在 Service 里，也别在 Controller 里判断业务。

### 5.2 完整版列表：五步走，别想着一条 SQL 搞定

一张订单可能有多件商品，所以 `t_order` 和 `t_order_item` 是**一对多**。这时候**不能**简单 JOIN 一下再分页 —— 原因见 5.3。

正确做法是「**分步查 + 内存组装**」，五步：

| 步 | 做什么 | 产出 |
|---|---|---|
| 1 | **先数总数** `COUNT(*)`，WHERE 条件和第 2 步**完全一样**，但**不带 LIMIT** | `total`（前端算页数用） |
| 2 | 查主表：`SELECT * FROM t_order WHERE ... ORDER BY create_time DESC LIMIT 起始位置, 条数` | 当前页的订单行 |
| 3 | 把这页订单的 `orderNo` 收集起来，`SELECT * FROM t_order_item WHERE order_no IN (...)` **一次查完** | 这页所有明细 |
| 4 | 同上，一次性查 `t_order_logistics`（`IN`）和 `t_user` 的昵称手机（`IN`） | 物流 + 用户信息 |
| 5 | 在 Service 里，用 `Map<String, ...>` 把子表数据按 `orderNo` 分组，**挂到**对应订单上；顺手算 `statusText` 和 `refundEligible` | 完整的 VO 列表 |

**为什么强调"一次 IN 查完"**：如果每查完一条订单就再去查一次它的明细，10 条订单就是 10 次额外查询 —— 这叫 **N+1 查询**，是后端最经典的性能问题，面试高频。用「收集 ID → 一次 IN 查 → 内存分组」就能避免。

**分页的起始位置**算法：`起始位置 = (page - 1) * limit`。

> **你装了 PageHelper 4.1.1，所以上面第 1、2 步可以合并**：
> `PageHelper.startPage(page, limit)` 之后**紧跟**主表查询即可，PageHelper 会自己补 `LIMIT`、
> 并额外跑一次 count；`total` 从 `PageInfo.getTotal()` 取 —— **不要再手写 count**，否则查两次。
> 第 3–5 步（组装）完全不变。用法见第 8 节。

### 5.3 为什么不能一条 JOIN 完再分页

假设第 1 页要 10 单，其中一单有 3 件商品。JOIN 之后：

```
orderNo A | 商品 1
orderNo A | 商品 2      ← 同一单占了 3 行
orderNo A | 商品 3
orderNo B | 商品 1
...
LIMIT 10  ← 切在第 10 行，可能只装下 4 个订单
```

`LIMIT 10` 限的是 **JOIN 后的行数**，不是订单数。结果就是「第 1 页只有 4 单，每页数量还不一样」。

> 这就是"先分页主表、再批量补子表"的原因。面试如果被问"一对多怎么分页"，答这句。

### 5.4 `statusText` 怎么来

建一个 `OrderStatus` 枚举（`PENDING_PAY("待付款")` 这种），用 `valueOf(status)` 拿到枚举再取中文。

**别写一串 `if ("PAID".equals(s)) return "已付款";`** —— 6 个状态就是 6 个分支，以后加工单状态还要再来一遍。枚举的另一个好处：状态值写错时会在**启动/编译阶段**就报错，而不是线上才发现。

### 5.5 `refundEligible` 怎么算

对每件商品：`refundDeadline != null && refundDeadline.isAfter(LocalDateTime.now())`。

**你的库里有 88 条"卡在 7 天整"的边界数据，就是给这个方法做自测用的**（`API.md` 5.2 明确要求）。写完拿它们跑一遍，边界当天不能判错。

> 为什么要算这个字段而不是给前端：业务规则的**唯一权威**在后端。AI 层、前端都不复制"签收后 7 天"这条规则，否则两边算出不同结果时无人可仲裁。这句话面试能加分。

### 5.6 详情接口（②）的思路

跟列表**共用同一个组装方法**，区别只有三点：

1. 按 `orderNo` 查（不是按 id），结果是 0 或 1 条
2. **越权检查**：`order.userId != 当前登录用户id` 且角色是 `USER` → 返回 `Result.error("订单不存在")`
3. 查不到也返回 `Result.error("订单不存在")`

**注意第 2、3 点要返回一模一样的东西**。原因：如果"查别人的单"返回 403、"查不存在的单"返回 404，攻击者就能靠状态码差异**反推出这个订单号是否存在**。所以统一成"订单不存在"。这是安全细节，`API.md` 1.6 有写，面试可以讲。

### 5.7 统计接口（③）的思路

一条 SQL：`SELECT status, COUNT(*) AS n FROM t_order [WHERE user_id = ?] GROUP BY status`。

- 角色是 `USER` 时加 `WHERE user_id = ?`，客服/管理员不加
- 拿到结果后，在 Java 里**补齐 6 个状态的键**，没有的填 0
- `total` = 各项之和
- **不要写 6 个 `count(*)`** —— 6 次查库，面试官会皱眉

**Controller 放哪**：路径是 `/api/stats/orders`，不在 `/api/orders` 下面。所以新建 `StatsController`（类注解 `@RequestMapping("/api/stats")`，方法上 `@GetMapping("/orders")`），别硬塞进 `OrderController`。以后 `/api/stats/after-sales` 也归它。

### 5.8 `eligibility` 筛选的思路

要按"**最紧急的那件商品**"筛，即订单里 `refund_deadline` 最小的那件。用 `EXISTS` 子查询（`API.md` 5.1 给了三种写法）：

| 筛什么 | 条件 |
|---|---|
| `expired` | 存在一件 `refund_deadline <= NOW()` |
| `soon` | 存在一件 `NOW() < refund_deadline <= NOW() + 24h` |
| `eligible` | 最紧急那件 `> NOW() + 24h` |

**顺序有讲究**：一张单可能既有超期商品又有有资格商品。"已超期"应该优先被判定出来（客服最该先处理它），所以判断顺序是 `expired` → `soon` → `eligible`。

### 5.9 两个内部接口（④⑤）的思路

- **复用**：直接调 ① 和 ② 用的 Service 方法，只是把返回值去掉 `Result` 外壳。**不要为内部接口重写一套查询**。
- 鉴权走 `X-Internal-Token` 请求头 + `@InternalOnly` 拦截器，**不走 JWT**（AI 服务没有用户身份）。
- ④ 必须校验 `userId` 归属，查不到按"订单不存在"处理（用 HTTP 状态码表达，不是 `code=0`）。

---

## 6. 四个一定会踩的坑

| 坑 | 现象 | 原因 / 解法 |
|---|---|---|
| **XML 根本没被读到**（最坑） | 启动**不报错**，但一访问接口就 `Invalid bound statement (not found): com.mayiran.commerceservice.mapper.OrderMapper.pageOrders` | 你的 XML 放在 `src/main/resources/mapper/`，但 `mybatis.mapper-locations` **没有默认值** —— 已读本地 `mybatis-spring-boot-autoconfigure-4.0.1.jar` 核实：配置元数据里 `defaultValue = None`，jar 内也搜不到任何 `classpath*:/mapper/**` 常量。**必须在 `application.properties` 补一行**：`mybatis.mapper-locations=classpath*:/mapper/**/*.xml`（或用注解 `@Select` 就不用配） |
| 查出来字段全是 `null` | JSON 里 `orderNo` 是 null 但库里有值 | `map-underscore-to-camel-case=true` 没生效。你已加，若仍 null 就重启一次 |
| **时间格式不是想要的** | 返回 `2026-09-14T08:54:11`（带 T）而不是 `2026-09-14 08:54:11` | Spring Boot 对 `LocalDateTime` 默认走 ISO 格式。**`spring.jackson.date-format` 对 `LocalDateTime` 不生效**（它只管老的 `java.util.Date`）。正解：给时间字段加 `@JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")`，或写一个全局的 `LocalDateTime` 序列化配置类 |
| `limit` 被人传成 999999 | 库被拖慢 | 在 Service 里卡上限：`if (limit > 100) limit = 100;` |

另外 `estimatedArrival` 是 `DATE` 类型（没有时分秒），Java 侧用 `LocalDate`，返回 `"2026-09-12"`。别用 `LocalDateTime`，会报转换错误。

---

## 7. 自测（curl）

```bash
# ① 列表：看到 5 条，orderNo / totalAmount 有值
curl -s "http://localhost:8080/api/orders?limit=5"

# ① 分页：第二页和第一页的 orderNo 不重复
curl -s "http://localhost:8080/api/orders?page=2&limit=5"

# ① 筛选：RECEIVED 应该返回 325 条（造数据时的数量）
curl -s "http://localhost:8080/api/orders?status=RECEIVED&limit=1"

# ② 详情：拿一条真实订单号（从上面结果里复制）
curl -s "http://localhost:8080/api/orders/SO2026090900353"

# ③ 统计：数字之和应等于 500
curl -s "http://localhost:8080/api/stats/orders"
```

> 今天的验收只需要第一条命令。后面四条是 9/16 – 9/20 的靶子。

**卡住时先看两处**：控制台报错（只看最底的 `Description` / `Action` 段）+ nginx 无关（这时候直接打 8080，不经过 nginx）。

---

## 8. PageHelper 4.1.1 用法（Spring Boot 4）

**为什么必须是 4.1.1**：`pagehelper-spring-boot-starter` 有两条版本线 ——
`1.x / 2.x` 是 Spring Boot 2/3 时代的（网上教程、苍穹外卖里常见的 `1.4.6` 就在这条线上），
**`4.x` 才是为 Spring Boot 4 适配的**。它内部对齐的是 `spring-boot 4.1.0` +
`mybatis-spring-boot-starter 4.0.1`，跟你项目的 4.0.8 + 4.0.1 是同一条线。
**不要照抄网上教程的版本号，填错整条线会真冲突。**

依赖（已加，注意 `<version>` 不能省 —— 它不在 Spring Boot 的版本清单里）：

```xml
<dependency>
    <groupId>com.github.pagehelper</groupId>
    <artifactId>pagehelper-spring-boot-starter</artifactId>
    <version>4.1.1</version>
</dependency>
```

> 已开 jar 核对：它的自动配置用新式 `AutoConfiguration.imports` 注册（`PageHelperAutoConfiguration`），
> Spring Boot 4 能正常识别，不会静默失效。

**代码怎么写（三步）**

| 步 | 写什么 |
|---|---|
| 1 | 在 Service 里，**查询的前一行**调 `PageHelper.startPage(page, limit)` |
| 2 | **紧接着**执行主表查询（Mapper 方法），中间不要插任何别的查库动作 |
| 3 | `PageInfo<Order> info = new PageInfo<>(查出来的 list)`，`info.getTotal()` 就是 `total` |

**五条必须记住的规则**

1. **只对"紧接着的第一条查询"生效。** 如果 `startPage` 之后先查了用户表，分页就作用到那条上，
   你的订单查询反而没被分页 —— 这是最常见的坑。
2. **不要自己再写 count。** PageHelper 会自动生成并执行 count 语句，`PageInfo.getTotal()` 就是结果；
   自己再写一遍等于白查一次库。
3. **`page` 从 1 开始**（契约就是 1 开始）。传 0 默认按第一页处理。
4. **`limit` 上限仍要自己卡**：`if (limit > 100) limit = 100;`。
   或者开 `pagehelper.reasonable=true`（页码越界时给第一页/最后一页，而不是空列表）。
5. **分页只作用于主表查询。** 第 3–5 步的组装（`IN` 查子表 + 内存分组）发生在分页之后，不受影响 ——
   这正是"先分页主表、再补子表"能和 PageHelper 配合的原因。

**可选配置**（`application.properties`，不加也能跑）：

```properties
pagehelper.reasonable=true
pagehelper.support-methods-arguments=true
```

**如果以后被面试问到**：答"用了 PageHelper"只是及格线。要能接着说 ——
「它是在 MyBatis 执行 SQL 前拦截、自动改写语句并额外跑一次 count，本质是个拦截器插件；
但它解决不了深分页（`LIMIT 100000, 10` 仍要扫 10 万行），那种情况要靠延迟关联或游标分页。」
这句话能把"会用插件"和"懂原理"区分开。

---

## 9. 前后端联调（2026-09-15 晚新增）

### 9.1 整体链路

```
浏览器 → http://localhost:8081/orders.html
           │
           ├─ 静态文件（HTML/CSS/JS）← nginx 直接返回
           │
           └─ /api/orders?limit=5  ← nginx 反代到 → 127.0.0.1:8080（Spring Boot）
                                                        │
                                                        └→ MySQL
```

**nginx 里已经配好 `/api/` 的反代了**（`D:\mayiran-work\tools\nginx-1.30.4\conf\nginx.conf`），
所以**不需要配 CORS** —— 浏览器眼里前后端是同一个源（都是 8081）。

### 9.2 联调只改一个地方

`frontend/assets/api.js` 第 22 行：

```js
var USE_MOCK = false;   // ← 从 true 改成 false
```

改完刷新页面即可。**建议不要一把全切**：`?limit=5` 通了再切下一页，方便定位问题。

> 页面顶部/登录页底部会显示当前数据来源（`Api.backendLabel()`），
> 看到「真实后端 http://localhost:8081」就说明开关生效了。

**最硬的证据是 F12 → Network**：刷新页面后，列表里应该出现一条
`orders?page=1&limit=10` 的 fetch 记录，点开能看到请求头和响应体。
如果 Network 里干干净净、一条请求都没有，那不管页面显示什么都还是 Mock 模式 ——
**后端起没起、接口写没写，都还没被验证过。**

> ⚠️ 改完 `api.js` 一定要 **Ctrl+F5 强刷**。`api.js` 被浏览器缓存住的话，
> 你改的 `false` 不会生效，会继续看到假数据，然后误以为"联调通了"。

### 9.3 登录这一步现在怎么过

`login.html` 走的是 `POST /api/auth/login`，**这个接口还没写**（JWT 排在 9/17）。
但前端的 `UI.requireLogin()` 只检查 localStorage 里有没有 `cs_token`，
后端目前也没有拦截器去校验它 —— 所以可以直接把登录态写进去：

**方式一（推荐）**：打开 `http://localhost:8081/dev-login.html`，点一个身份进去。
- 客服 `郭思远`（id=108）/ 管理员 `何皓宇`（id=92）→ 能看全部订单
- 普通用户 `谢雨欣`（id=1）→ 只带自己的 `userId`

**方式二**：F12 → Console 粘一行

```js
localStorage.setItem('cs_token','dev');
localStorage.setItem('cs_user',JSON.stringify({id:108,username:'user0108',nickname:'郭思远',role:'AGENT',roleText:'客服'}));
location.href='orders.html';
```

> ⚠️ `dev-login.html` 是**开发脚手架**。等 9/17 登录接口和拦截器写完就删掉它，
> 别让它跟着项目上生产。

### 9.4 现在联调能看到什么 / 还看不到什么

| 页面位置 | 数据来源 | 现在有没有 |
|---|---|---|
| 订单列表表格 | `GET /api/orders` | ✅ **已实测通过**：`total:500`，经 nginx 8081 拿到真实库数据 |
| 顶部 4 张 KPI 卡片 | `GET /api/stats/orders` | ❌ 空（9/20 做；代码里已 catch，不阻塞列表） |
| 点一行打开详情抽屉 | `GET /api/orders/{orderNo}` | ❌ 报"加载失败"（9/19 做） |
| 筛选条件（状态/关键字/退货资格） | 同 `GET /api/orders` | ⚠️ **XML 里还没有 WHERE，点了不会有变化**（9/16 做） |
| 商品明细、物流节点条 | 同 `GET /api/orders` | ❌ 空（阶段二做，见下方⚠️） |
| 工单页 / 对话页 | `/api/after-sales`、`/api/chat` | ❌ 都没写 |

**别把「筛选没反应」「KPI 是空的」当成联调失败** —— 那是接口还没排到。

> ⚠️ **阶段二动手前先改一个类型**：`OrderVO.logistics` 现在是 `List<OrderLogistics>`，
> 但 `orders.html` 是按**单个对象**用的（`lg.trackNo`、`lg.currentNode`、`lg.currentStatus`）。
> 更隐蔽的是：JSON 里的空数组 `[]` 在 JS 里是 **truthy**，
> `var lg = o.logistics; lg ? '有物流' : '暂无物流'` 会走进「有物流」分支，
> 显示一个全是空的物流壳子，而不是「该订单暂无物流信息」。
> 所以补物流时把字段改成 `private OrderLogistics logistics;` ——
> 一个订单最多一条物流记录，**没有物流时给 `null`**，前端才会走对分支。

### 9.5 排查顺序（联调报错时按这个顺序看）

| 现象 | 先查哪里 |
|---|---|
| **后端根本没启动，页面却照样有完整数据** | **`USE_MOCK` 还是 `true`** —— 请求被 `mocked()` 截走了，一个字节都没发出去。这是最容易自我欺骗的一种"联调成功" |
| 页面一直转圈 / `Failed to fetch` | Spring Boot 起了吗？（`netstat -ano \| grep :8080`） |
| 404 | nginx 反代是否生效；后端路径是否真为 `/api/orders` |
| `code:0` + 后端报错信息 | **Spring Boot 控制台**，业务错误一定在这里 |
| 返回 JSON 但字段是 `null` | `map-underscore-to-camel-case`；或 SQL 没起别名（`AS userNickname`） |
| 列表 10 行全是同一单 | **JOIN 写成了逗号连接**（笛卡尔积）—— 见 9.6 |
| 返回空数组 `records: []` | `page`/`limit` 默认值丢了，或 PageHelper 拼出 `LIMIT 0,0` |

### 9.6 一个已经踩到的坑：逗号 join = 笛卡尔积

`from t_order o , t_user u` **不带 `on` / `where`**，MySQL 会把两表**两两组合**：
`500 单 × 200 用户 = 100000 行`。PageHelper 再拼上 `LIMIT 0,10`，
拿到的 10 行**全是同一张订单**，只是配了 10 个不同用户。

实测结果（用你的库跑的）：

```
SO2026091300069 | 用户ID 118 | 昵称 谢晨光
SO2026091300069 | 用户ID 118 | 昵称 周博文
SO2026091300069 | 用户ID 118 | 昵称 谢雨欣     ← 同一单，10 行
SO2026091300069 | 用户ID 118 | 昵称 彭博文
...  不同订单号数量 = 1（正常应为 10）
```

**正确写法**（两种都行，推荐第一种）：

```sql
from t_order o inner join t_user u on u.id = o.user_id
-- 或
from t_order o , t_user u where u.id = o.user_id
```

另外 `order by create_time` 要写成 **`order by o.create_time`**：
两张表都有 `create_time`，虽然它在 SELECT 列表里时 MySQL 能认出来，
**但一旦你改窄 SELECT 列表，就会报 `Column 'create_time' in order clause is ambiguous`**（实测报过）。

### 9.7 普通用户看到了全部订单 —— 两个层面的问题（2026-09-15 晚）

**现象**：用 `dev-login.html` 以「普通用户 谢雨欣（id=1）」进入订单页，
页头写着"用户视角：只能看到自己的订单"，但表格里 500 单全在。

#### 层面一：直接原因 —— XML 里没有 WHERE，参数被丢掉了

前端**没有错**。链路和实测如下：

| 环节 | 实际发生了什么 |
|---|---|
| ① `dev-login` 写入 `cs_user` | `{id:1, role:'USER', ...}` —— `id` 是有的 |
| ② `orders.html` | `isAgent=false` → `state.userId = me.id = 1` |
| ③ `api.js` 拼 query | `userId=1` 非空，**会拼上** |
| ④ `OrderController` | 参数绑定成功：`OrderPageDTO(page=1, limit=10, userId=1)` |
| ⑤ `OrderServiceImpl` | 原样传给 mapper，没动过 |
| ⑥ **`OrderMapper.xml`** | ❌ **SQL 里没有 WHERE，`#{userId}` 一次都没出现** |
| ⑦ MySQL | 无条件全表扫 → **500 条** |

**实测证据**（用你的库跑的，两条都证明参数被忽略）：

```
GET /api/orders?page=1&limit=3&userId=1
  → total = 500，返回的 userId 却是 118（别人的订单）

GET /api/orders?page=1&limit=3&status=RECEIVED
  → total = 500，返回的 status 却是 PENDING_PAY
```

`userId` 和 `status` **两个条件同时失效** —— 这排除了"绑定失败"的可能，
唯一解释就是 SQL 根本没引用它们。

**根因**：本文档 5.0.H 给的是**带 WHERE 的完整版**，XML 里只抄了主查询部分，
`WHERE 1 = 1` 和后面三个 `<if>` 整段漏掉了。翻回 5.0.H 对照补上即可。

#### 层面二：更严重 —— 就算加了 WHERE，这个设计仍然是越权的

补上 `AND o.user_id = #{userId}` 之后，页面「看起来」正常了：普通用户只看到自己的单。
但**任何人都能把地址栏改成 `?userId=118`**，前端传什么后端就信什么，
一秒就能看到别人的订单。这不是权限，这是 UI 装饰。

**正确做法（写进 9/17 的 JWT 拦截器一起做）**：

| 角色 | 后端应该怎么定 `userId` |
|---|---|
| `USER` | **忽略前端传的值**，强制用登录态里的 `userId` |
| `AGENT` / `ADMIN` | 允许按前端传的 `userId` 筛选；不传 = 看全部 |

关键点：**`userId` 是"授权依据"，只能来自 JWT，不能来自请求参数。**
前端传什么都只是"请求意图"，后端有权覆盖它。

对应到代码位置：拦截器解析 JWT 后放一个当前用户对象进 `ThreadLocal`，
在 `OrderServiceImpl` 里判断角色、覆盖 DTO 的 `userId` ——
**校验要放在 Service 层**，不能只靠 Controller 传参。

> 面试会问「你的项目怎么防越权」，答这条：*"列表接口的 userId 由后端从 JWT 注入，
> 前端传的会被强制覆盖；只有客服和管理员才允许按 userId 查询。"
> 这比"我在前端把输入框隐藏了"高一个数量级 —— 前端 `display:none` 不是权限。*

**同一个坑还会出现在两个地方**（写的时候一起想）：

1. `GET /api/orders/{orderNo}`（详情，9/19 做）—— 必须校验「这一单是不是当前用户的」，
   否则换个订单号就能看别人的物流和收货地址
2. `POST /api/after-sales`（申请售后）—— 必须校验订单归属，否则可以替别人退货
