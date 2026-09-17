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

---

## 10. 登录接口（`POST /api/auth/login`）实施清单

对应 `API.md` 4.1。**先做这个，再做订单详情/统计** —— 后两个接口的越权过滤依赖这里的登录态。

### 10.0 施工流程总览（13 步 · 2026-09-17 从当前代码出发）

#### 起点：你现在的真实状态（9/17 13:40 核实）

| 已经好了 ✅ | 还缺 ❌ |
|---|---|
| `dto/LoginDTO`（`username` / `password`） | pom 的 4 个依赖（jjwt ×3 + spring-security-crypto） |
| `vo/UserVO`（**含 `roleText`**，字段齐） | `application.properties` 的 `jwt.secret` / `jwt.expire-hours` |
| `vo/LoginVO`（`token` + `user`，名字和 `api.js` 对齐） | `enums/RoleEnum` |
| `mapper/OrderMapper` 的 `@Mapper` 写法（照抄） | `mapper/UserMapper` + `resources/mapper/UserMapper.xml` |
| `result/Result`（`success(T)` / `error(msg)` 都有） | `utils/JwtUtil` |
| `application.properties` 的 `mybatis.mapper-locations` 已配好 | `service/AuthService` + `service/impl/AuthServiceImpl` |
| `OrderServiceImpl` 的 `@Service` + `@Autowired` 写法（照抄） | `controller/AuthController` ← **现在是个空类** |

> **那 3 个 DTO/VO 别重写** —— 字段名已经和前端契约对齐（`data.token` / `data.user`），改名字会让 `api.js` 存进 `undefined`，然后全站 401。

#### 13 步，按顺序做

| # | 动作 | 落在哪个文件 | 完成标志 |
|---|---|---|---|
| 1 | 加 4 个依赖 | `pom.xml` | 保存后 IDEA 右下角提示 "Maven 需要导入" |
| 2 | 刷新 Maven 并确认下载 | IDEA → Maven 面板 → Reload | 依赖树里有 `io.jsonwebtoken:jjwt-api:0.12.5`，三个 jar 齐全 |
| 3 | 加两行 JWT 配置 | `application.properties` | `jwt.secret=`（≥32 字符）+ `jwt.expire-hours=24` |
| 4 | 建角色枚举 | `enums/RoleEnum.java` | `RoleEnum.of("AGENT").getText()` 返回 `"客服"` |
| 5 | 建 Mapper 接口 | `mapper/UserMapper.java` | 加 `@Mapper`；方法 `User findByUsername(String username)` |
| 6 | 建 Mapper XML | `resources/mapper/UserMapper.xml` | `namespace` = 接口全限定名；**只查 7 列**（见下） |
| 7 | 建 `JwtUtil` | `utils/JwtUtil.java` | `generate(Long userId, String role)` / `parse(String token)` |
| 8 | 建 Service 接口 + 实现 | `service/AuthService.java` + `service/impl/AuthServiceImpl.java` | 5 个分支全走通；**抛异常就必须配 §10.5 坑 9 的全局处理器** |
| 9 | 把空类填上 | `controller/AuthController.java` | `@RequestMapping("/api/auth")` + `@PostMapping("/login")` + **记得 `return`** |
| 10 | 重启后端 | IDEA（红方块 → 绿三角） | 日志出现 `Tomcat started on port 8080` |
| 11 | 三条 curl 验证 | 终端 | ②③ 两条响应**逐字符一致**（见 §10.6） |
| 12 | 真页面走一遍 | `http://localhost:8081/login.html` | 跳进 `orders.html`，F12 Network 里有 `login` 请求且 200 |
| 13 | 提交 | Git | 两个 commit：①依赖+配置 ②业务代码 |

> **进度（2026-09-17 15:53 更新）**：**第 1、2、3、4、7 步已完成** ——
> ① 四个依赖写进 `pom.xml`（`dependency:tree` + `clean compile` 实测通过，见 §10.8）；
> ② `application.properties` 加了 `jwt.secret`（64 字符 → jjwt 实测选 HS512）/ `jwt.expire-hours=24`；
> ③ `utils/JwtUtil.java` 写完，`mvnw.cmd -B -o compile` → **BUILD SUCCESS**；
> ④ `enums/RoleEnum.java` 写完（三常量 + `text` 字段 + 私有构造器 + `getText()` + `of()`，
> 未命中抛 `IllegalArgumentException`）。代码已核实，见 §10.9 八。
>
> **进度（2026-09-17 16:35 更新）**：第 5、6 步**没按文档做法落地**，改成了注解风格 ——
> `UserMapper.java` 用 `@Select("select * from t_user where username = #{username}")`，
> 方法名是 `getByUsername`（不是文档写的 `findByUsername`），`UserMapper.xml` 保持空壳。
> **能跑，但要记住：以后 XML 里再写同名 `<select>` 会启动失败，见坑 7。**
> 第 8 步 `AuthServiceImpl` 写到"密码比对"这一行停下（见坑 8 的写法），
> 且已按**抛异常**的风格写了一半（`throw new AccountNotFoundException`）——
> 但项目里还没有任何全局异常处理器，**必须补上或改用方案 A，见坑 9**。
> 第 9 步 `AuthController` 第 26 行 `authService.login(loginDTO);` **没有 `return`，当前编译是红的**。
> 中途踩掉两个坑：类名笔误 `JwtUwil`（§10.5 坑 4）、`import lombok.Value` 导错包 + 第 34 行缺分号（§10.5 坑 5）。
>
> **⚠️ 当前项目编译是红的**（2026-09-17 16:12 实测）：
> `AuthController.java:[26,5] 缺少返回语句` —— 因为第 19 行 `authService.login(loginDTO)` 的
> **返回值被丢掉了**，而 `AuthService.login` 现在声明成 `void`（第 8 步还没按契约改）。
> 这半成品会让 javac 停在解析阶段，**后面写的 XML / Service 全都无法用编译验证**。
>
> **必须先定下来 §10.4 的 A / B 方案，然后把这三个文件一起收尾：**
> `AuthService`（返回类型）→ `AuthServiceImpl`（5 个分支）→ `AuthController`（`return` 那行）。
>
> 另外第 5 步 `UserMapper.java` 现在用的是 **`@Select` 注解**（方法名 `getByUsername`），
> 与第 6 步的 XML 方案**二选一**，见 §10.5 坑 7。
>
> 下一步：**第 5/6 步二选一收尾 → 第 8、9 步**。

---

> **进度（2026-09-17 17:30 更新）· 端到端实测验收**
>
> 本轮把后端**真跑起来**（临时用 `--server.port=18080`，避开被占用的 8080），打了 7 组真实请求。
> 结论一句话：**成功路径全通，失败路径全断。**
>
> **✅ 已完成 —— 第 1～9 步的代码全部到位**
> - `./mvnw.cmd -B -o compile` → **BUILD SUCCESS**（只剩 `OrderVO` 那两条 `@Builder` 警告）
> - 启动成功：`Tomcat started on port 18080`；`JwtUtil 初始化完成:签名算法=HmacSHA512,有效期=24小时`
> - `user0108/123456` → **HTTP 200** `{"code":1,"data":{"token":"…","user":{…}},"msg":null}`
> - token **192 字符 / 三段**：第一段解出 `{"alg":"HS512"}`，
>   第二段解出 `{"userId":108,"role":"AGENT","iat":…,"exp":…}`，有效期正好 24 小时
> - 三种角色 `roleText` 全对：`user0001`→普通用户、`user0108`→客服、`user0092`→管理员
> - 响应的 `user` 对象里**没有 password 字段**（`UserVO` 天然把密文挡在服务端）
>
> **❌ 未完成 —— 四件事，按优先级**
> 1. **失败路径全是 HTTP 500** → 页面只会弹「请求失败」，见坑 9 / 坑 10
> 2. **`MessageConstant` 里 `PASSWORD_ERROR` / `ACCOUNT_NOT_FOUND` 还分着** → 用户名枚举漏洞，
>    现在被 500 掩盖，补了全局异常处理器就会暴露，见坑 10
> 3. **`user.getStatus() == StatusConstant.DISABLE` 是 `Integer == Integer` 引用比较**，靠缓存侥幸正确，见坑 11
> 4. **环境阻塞：8080 被 National Instruments 的服务占着**，IDEA 启动会报端口冲突，见坑 12
>
> **失败分支实测记录（这张表就是"还没完成"的证据）**
>
> | 用例 | 期望 | 实测 |
> |---|---|---|
> | `user0108` / `wrong` | `code:0` + `"用户名或密码错误"` | **HTTP 500**，Spring 默认错误体 |
> | `nobody` / `123456` | 与上一行**逐字符一致** | **HTTP 500**，与上一行只差 timestamp |
> | `""` / `""` | `code:0` + `"用户名或密码不能为空"` | **HTTP 500**，而且落进了"账号不存在"分支 |
> | 不带 body | 400 或 `code:0` | **HTTP 400**（Spring 自己拦的） |

**第 6 步的 7 列**（别 `SELECT *`）：

```sql
SELECT id, username, password, nickname, phone, role, status
FROM t_user
WHERE username = #{username}
```

`resultType` 写全限定名 `com.mayiran.commerceservice.entity.User`（查 `password` 做比对，所以映射到实体而不是 VO）。

#### 第 8 步内部：整个接口唯一的判断逻辑（5 个分支）

顺序不能换 —— 先挡空参，再查库，最后才比密码：

| 顺序 | 条件 | 返回 |
|---|---|---|
| 1 | `username` 或 `password` 为空 | `Result.error("用户名或密码不能为空")` |
| 2 | 查不到这个用户（`user == null`） | `Result.error("用户名或密码错误")` |
| 3 | `BCrypt.checkpw(明文, user.getPassword())` 为 false | `Result.error("用户名或密码错误")` ← **和 2 必须一模一样** |
| 4 | `user.getStatus() != 1`（账号被禁用） | `Result.error("账号已被禁用")` |
| 5 | 全过 | `Result.success(loginVO)` |

**2 和 3 故意合并成同一个响应** —— 分开写就等于告诉攻击者"这个用户名真实存在"，是免费的账号枚举接口。

> 分支 4 的注意点（**2026-09-17 实测更正**）：实体里 `status` 是 **`Integer`**（不是 `Byte`），
> `StatusConstant.DISABLE` 也是 `Integer` → **两个包装类型 `==` 走的是引用比较，不会拆箱**，
> 靠 Integer 缓存（-128~127）侥幸为 true。详见**坑 11**，正确写法是
> `StatusConstant.DISABLE.equals(user.getStatus())`（常量放左边，status 为 null 也安全）。

#### 卡住了按这个顺序查

§10.5 三个坑 → §6 四个坑 → Spring Boot 控制台日志（**用 IDEA 启动才有日志**）。

### 10.1 一次登录的完整链路（8 步）

| 步 | 谁 | 做什么 |
|---|---|---|
| 1 | 浏览器 | `login.html` 收集账号密码 → `Api.login(u, p)` |
| 2 | `api.js` | `POST /api/auth/login`，body 是 JSON，**不带 token**（这是唯一免登录的接口） |
| 3 | nginx | `/api/` 命中反代规则 → 转发到 `127.0.0.1:8080` |
| 4 | `AuthController` | `@RequestBody LoginDTO` 接住 → 调 `AuthService.login(dto)` |
| 5 | `UserMapper` | `findByUsername(username)` → MySQL 查出一行（含 BCrypt 密文） |
| 6 | `AuthService` | 查不到 → 直接返回失败。查到 → `BCrypt.checkpw(明文, 密文)` 比对 |
| 7 | `JwtUtil` | 比对通过 → 用 `userId` + `role` 签发 token（有效期 24h） |
| 8 | 回到浏览器 | `api.js` 把 `data.token` 存进 `localStorage`，然后 `location` 跳 `orders.html` |

**第 6 步是整条链路上唯一的"判断"**，其余都是搬运。密码比对只在这一处，别的地方不能碰密码。

### 10.2 请求

```
POST /api/auth/login
Content-Type: application/json
```

```json
{ "username": "user0108", "password": "123456" }
```

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `username` | string | 是 | 登录名，如 `user0001` / `user0108` |
| `password` | string | 是 | **明文**。库里存的是 BCrypt 密文，比对交给 `BCrypt.checkpw` |

**注意两点**：

1. 这是明文传输密码 —— 因为前端是 `http://localhost`。生产必须走 HTTPS，否则 token 和密码
   在网络上是裸奔（面试可以主动提，说明你知道边界在哪）。
2. `username` 不区分大小写这件事**不要做** —— MySQL 默认排序规则不区分，别额外加 `LOWER()`，
   但也不要写死 `binary`，保持默认即可。

### 10.3 响应（4 种情况，全部是 HTTP 200）

**① 成功** —— `code=1`，`data` 里必须有 `token` 和 `user` 两个字段：

```json
{
  "code": 1,
  "msg": null,
  "data": {
    "token": "eyJhbGciOiJIUzUxMiJ9.eyJ1c2VySWQiOjEwOCwicm9sZSI6IkFHRU5UIiwiaWF0IjoxNzg5NjMxNzUzLCJleHAiOjE3ODk3MTgxNTN9.FPsPPwWKqrRyQ35lFuotHcF_...",
    "user": {
      "id": 108,
      "username": "user0108",
      "nickname": "郭思远",
      "phone": "13000000000",
      "role": "AGENT",
      "roleText": "客服"
    }
  }
}
```

> `msg` 是 `null` 不是 `""` —— 因为 `Result.success(T)` 只设了 `code` 和 `data`。
> 前端不读 `msg`，不影响；想好看就在 `success()` 里补 `result.msg = ""`。

> **上面那串 token 是按本项目真实配置签出来的**（2026-09-17 实测，`jwt.secret` 是 64 个字符）。
> 注意第一段解出来是 `{"alg":"HS512"}`，**不是 §10.9 举例用的 HS256** ——
> 因为 jjwt 按密钥字节数自动挑算法，64 字节 → HS512（见 §10.9 四）。
> 别把 token 第一段当成"写错了"。

**② 密码错** 和 **③ 用户不存在** —— **两种情况必须返回一模一样的响应**：

```json
{ "code": 0, "msg": "用户名或密码错误", "data": null }
```

> 为什么不能分开写：如果用户不存在返回"用户不存在"，攻击者就能拿一个密码字典
> **批量试出哪些用户名真的存在**（这叫用户名枚举）。分开写等于送了一个探测接口。
> 两边都写成同一句话，攻击者就分不清。

**④ 参数为空 / 格式不对**：

```json
{ "code": 0, "msg": "用户名或密码不能为空", "data": null }
```

### 10.4 要新建的类（按这个顺序写）

| 顺序 | 类 | 包 | 内容 |
|---|---|---|---|
| 1 | `LoginDTO` | `dto` | `String username` / `String password` |
| 2 | `UserVO` | `vo` | `id` / `username` / `nickname` / `phone` / `role` / `roleText` —— **就是不含 password 的 User** |
| 3 | `LoginVO` | `vo` | `String token` / `UserVO user` |
| 4 | `UserMapper` | `mapper` | `User findByUsername(String username)` |
| 5 | `UserMapper.xml` | `resources/mapper` | 一条 select，查 7 列**不含 `*`** |
| 6 | `JwtUtil` | `utils` | `String generate(Long userId, String role)` / `Claims parse(String token)` |
| 7 | `AuthService` + `AuthServiceImpl` | `service` / `service.impl` | `LoginVO login(LoginDTO dto)` |
| 8 | `AuthController` | `controller` | `Result<LoginVO> login(@RequestBody LoginDTO dto)` |

> **⚠️ 这里有个必须定下来的问题：`AuthService` 的返回类型**
> （2026-09-17 15:47 发现文档自相矛盾，已核实修法）
>
> 本表第 7 行写的是 `LoginVO login(LoginDTO dto)`，但 §10.0 第 8 步写的是
> 「用 `Result.error` 返回，不抛异常」—— 返回 `LoginVO` 就没办法带回错误信息，两者对不上。两个选法：
>
> | 方案 | 方法签名 | 特点 |
> |---|---|---|
> | **A（推荐）** | `Result<LoginVO> login(LoginDTO dto)` | Service 里直接 `return Result.error("用户名或密码错误")`，Controller 只做转发。少写文件、链路短，和 §10.0 的说法一致 |
> | B | `LoginVO login(LoginDTO dto)` | 失败时抛自定义业务异常，Controller 用 `@ExceptionHandler` 统一转 `Result`。更"规范"，但要额外写异常类 + 全局异常处理器 |
>
> **方案 A 已实测可行**（2026-09-17）：`Result.error(String)` 声明为 `<T> Result<T>`，
> 靠目标类型推断可以直接 `return` 给返回 `Result<LoginVO>` 的方法；编译只提示
> `未经检查或不安全的操作`（是提示，不是错误）。实测输出：
> 失败 → `code=0, msg=用户名或密码错误, data=null`；成功 → `code=1, msg=null, data=...`，
> 与 §10.3 写的一致（包括「`msg` 是 `null` 不是 `""`」这一点）。

**两个必须做到的设计点**：

- **`UserVO` 不能省，不能直接返回 `User` 实体** —— 实体里有 `password` 字段，
  直接返回等于把 BCrypt 密文吐给浏览器。密文虽然解不出明文，但它可以被离线爆破，
  这是实打实的泄露。**"返回给前端的对象永远单独建一个 VO"** 是基本素养，面试爱问。
- **`roleText` 在 Java 里转**（`AGENT` → `客服`），不要扔给前端映射 —— 换个前端就要重写一遍。
  用 `enum RoleEnum` + 一个 `getText()`，和订单状态的 `statusText` 是同一套做法。

### 10.5 十二个坑（今天最可能卡住的，按踩到顺序追加）

**坑 1：jjwt 0.12.x 是"三件套"，少一个运行时才炸**

```xml
<dependency>
  <groupId>io.jsonwebtoken</groupId>
  <artifactId>jjwt-api</artifactId>
  <version>0.12.5</version>
</dependency>
<dependency>
  <groupId>io.jsonwebtoken</groupId>
  <artifactId>jjwt-impl</artifactId>
  <version>0.12.5</version>
  <scope>runtime</scope>
</dependency>
<dependency>
  <groupId>io.jsonwebtoken</groupId>
  <artifactId>jjwt-jackson</artifactId>
  <version>0.12.5</version>
  <scope>runtime</scope>
</dependency>
```

只引 `jjwt-api` 编译能过（接口都在里面），**运行时才报 `ClassNotFoundException` / `UnsupportedJwtException`**
—— 因为它只是个接口壳，实现类在两个 runtime 包里。

**为什么选 0.12.5**（2026-09-17 核实 Maven 中央仓库）：jjwt 现在最新已到 **0.13.0**，
0.12.x 系列末版是 0.12.7。这里选 0.12.5 不是因为它最新，而是因为**本机 D 盘 Maven 仓库里
已经躺着 0.12.5 的完整三件套**，离线就能编译通过；且能查到的教程、写法都以 0.12.x 为准，
0.13.0 的 API 有没有变化没有验证过，新手期不追新。**要升版本就改这三处 `<version>`，别只改一个。**
（我此前在本节写过"0.12.6 是当前最新版"，那是核对时机较早的结论，现已修正。）

另外要说清一件事 —— **我此前在这里写过"0.12.x 和 0.9.x 老教程不兼容、照抄会报错"，这句话说过头了。**

2026-09-17 用 `javap` 直接翻了 jar 里的类，并写了一段 0.9.x 风格的代码**实测编译 + 运行**：

| 老方法 | 在 0.12.5 里 |
|---|---|
| `SignatureAlgorithm.HS256` | ✅ 还在，标了 `@Deprecated` |
| `Jwts.builder().setClaims(map)` | ✅ 还在，标了 `@Deprecated` |
| `signWith(SignatureAlgorithm, byte[])` | ✅ 还在，标了 `@Deprecated` |
| `.setExpiration(date)` | ✅ 还在，标了 `@Deprecated` |
| `Jwts.parser().setSigningKey(...)` | ✅ 还在，标了 `@Deprecated` |
| `Keys.secretKeyFor(alg)` | ✅ 还在，标了 `@Deprecated` |

**准确的说法是：老写法能编译、能运行，只是全部已弃用。** 这恰恰是它危险的地方 ——
它不会当场报错，所以你会以为没事；等哪天升版本被删掉，才在运行时炸。**新代码一律用右边这列。**

| 老写法（0.9.x，已 @Deprecated，新代码别用） | 新写法（0.12.x） |
|---|---|
| `Jwts.parser().setSigningKey(key)` | `Jwts.parser().verifyWith(key).build()` |
| `signWith(SignatureAlgorithm.HS256, str)` | `signWith(secretKey)`（算法由 key 长度推断） |
| `setClaims(map)` ⚠️ **覆盖式**，会清掉之前设的所有声明 | `.claim(k, v)` 逐个加，不覆盖 |
| `setSubject` / `setExpiration` | `subject()` / `expiration()` |

**坑 2：BCrypt 要 `spring-security-crypto`，但绝不能引 `spring-boot-starter-security`**

```xml
<dependency>
  <groupId>org.springframework.security</groupId>
  <artifactId>spring-security-crypto</artifactId>
  <!-- 不写 version：父 POM 的 spring-security-bom 已管理（7.0.7） -->
</dependency>
```

引 `spring-boot-starter-security` 会触发安全自动配置，**你现有所有接口立刻变成 401**，
而且会弹一个随机密码的登录框，排查起来很懵。`spring-security-crypto` 只是个工具包，
不带任何自动配置 —— 要的就是它。

**坑 3：`jwt.secret` 有长度要求**

HS256 的密钥必须 ≥ 256 bit（**32 字节**）。写 `jwt.secret=secret` 会在签名时报
`WeakKeyException`。生成一个：

```bash
openssl rand -base64 48
```

（Windows Git Bash 自带 openssl；没有就用一长串随机字符，别用有意义的话。）

**坑 4：类名和文件名不一致，会让 Lombok 全线停摆（2026-09-17 实际踩到）**

`utils/JwtUtil.java` 里写成了 `public class JwtUwil`（`Uwil` 是笔误），`mvnw.cmd compile` 报出 5 条错：

```
[ERROR] JwtUtil.java:[3,8] 类 JwtUwil 是公共的, 应在名为 JwtUwil.java 的文件中声明
[ERROR] OrderController.java:[28,9] 找不到符号   符号: 变量 log
[ERROR] OrderServiceImpl.java:[24,42] 找不到符号   符号: 方法 getPage()
[ERROR] OrderServiceImpl.java:[24,65] 找不到符号   符号: 方法 getLimit()
[ERROR] OrderServiceImpl.java:[27,16] 无法推断 PageResult<> 的类型参数
```

**后面 4 条跟 `JwtUtil` 一点关系都没有，却全是它引起的。** 原理：

javac 编译分五步：解析 → 进入（建符号表）→ **注解处理（Lombok 在这一步改语法树）** → 分析（类型检查）→ 生成字节码。
"公共类名必须与文件名一致"是**进入阶段**就能发现的错误。一旦出现，javac 会**跳过注解处理轮次**
直接进分析阶段 —— 于是 Lombok 本该生成的 `log` 字段、`getPage()` / `getLimit()`、双参构造器
统统不存在，于是一串"找不到符号"。

**识别方法**：要是多个**互不相关**的类同时报"缺少 Lombok 生成的方法 / 字段"，
别一个个去查，先扫一眼错误列表里有没有「类 X 是公共的, 应在名为 X.java 的文件中声明」这一条 ——
它就是元凶。**只改这一个地方，其余 4 条会一起消失**（已实测：改完 `JwtUwil` → `JwtUtil`，
`mvnw.cmd clean compile` 立刻 BUILD SUCCESS）。

所以看到 `@Slf4j` 报 `找不到符号 变量 log`，**不要急着怀疑 Lombok 没配好**，先看有没有这条。

**坑 5：`@Value` 有两个同名类，自动补全极易导错包（2026-09-17 实际踩到）**

`JwtUtil.java` 第 6 行被 IDEA 自动补成了 `import lombok.Value;` →
构造器参数上的 `@Value` 报「批注接口不适用于此类型的声明」，同时参数名 `secret` / `expireHours`
被标成「找不到符号」。

| | `lombok.Value`（错的那个） | `org.springframework.beans.factory.annotation.Value`（要的那个） |
|---|---|---|
| 贴在哪儿 | **只能贴类**（`@Target(TYPE)`，和 `@Data` 一家的不可变类注解） | 类 / 方法 / **方法参数** |
| 干什么 | 编译期生成 getter、全参构造器、`equals` | 从配置文件读值注入 |

**两个 `Value` 名字一样、包不一样，所以「有 import 但 import 错了」时编译器的报错长得不像 import 的问题。**
记住：**写 `@Value` 时不要在补全列表里直接回车选第一条**，认准 `org.springframework` 前缀的那个。

**同源现象（和坑 4 一个道理）**：这类错误一旦发生，`@Slf4j` 的 `log` 也会跟着报「找不到符号」——
还是"有错就跳过注解处理轮次"那条规则。

**这次的排查有个额外教训**：第一轮编译**只报了 `JwtUtil.java:[34,82] 需要';'` 一条**，
`lombok.Value` 的错被吞掉了 —— 因为**语法错误会让 javac 在解析阶段就停下，整个类型不做语义分析**。
所以定位这类问题要**先把语法错误修掉再编译一次**，不能只看第一次的输出。

**坑 6：`@Select` 里的 SQL 报红波浪线 —— 那是 IDEA 的检查，不是编译错误（2026-09-17 实际踩到）**

现象：`mapper/UserMapper.java` 里
`@Select("select * from t_user where username = #{username}")` 的 `t_user`、`username`
被画上波浪线，看着像"表不存在"。

**先分清三个互不相干的检查者**（这是这次真正学到的东西）：

| 检查者 | 它看什么 | 本次实测结果 |
|---|---|---|
| Java 编译器（javac） | 只看 Java 语法。**字符串里是 SQL，它根本不管** | `mvnw.cmd -B -o compile` → UserMapper.java **零错误零警告** |
| IDEA 的 SQL 检查 | 把 `@Select` 的字符串当 SQL，拿**本地数据库缓存**去解析表名/列名 | **报波浪线** ← 就是它 |
| MyBatis 运行时 | 真连 MySQL 执行 | ✅ 实测返回 `108 / user0108 / 郭思远 / AGENT / 1` |

**它为什么解析不到**：`.idea/dataSources/<uuid>.xml` 里 **`<schema>` 节点 11 个、`<table>` 节点 0 个**，
`commerce_service` 只出现一次（只有库名，下面一张表都没有）。IDEA 在一个"空库"里找 `t_user`，
当然找不到。**跟你的 SQL 写得对不对无关。**

顺带一个容易误判的细节：IDEA 数据源里配的 JDBC URL 是 `jdbc:mysql://localhost:3306`
（**不带库名**），而 `application.properties` 里是 `.../commerce_service?...`。两者不一致时更容易出这种事。

**怎么消掉（30 秒）**：打开右侧 **Database** 面板 → 双击 `@localhost` 连上 → 展开 `commerce_service`
→ 表列出来之后波浪线自己就没了。如果展开是空的，点工具栏的 **Refresh / Synchronize**。
（可选：把数据源的 URL 改成 `jdbc:mysql://localhost:3306/commerce_service` 更稳。）

**也可以直接不管** —— 它不影响编译、不影响启动、不影响运行。但建议修掉：
将来写动态 SQL 时，这个检查能帮你抓表名列名的错别字，白送的能力别关。

**坑 7：`@Select` 注解和 XML 只能二选一，两边都写会在启动时炸（本次未踩，提前避坑）**

`resources/mapper/UserMapper.xml` 的 `namespace` 已经指向 `...mapper.UserMapper`（空壳）。
如果**同时**在接口上用 `@Select`、又在 XML 里写 `<select id="getByUsername">`，
启动时会抛：

```
Mapped Statements collection already contains value for
  com.mayiran.commerceservice.mapper.UserMapper.getByUsername
```

因为 MyBatis 把「注解里的 SQL」和「XML 里的 SQL」都注册成同一个 statement id，撞车了。
**不是覆盖、不是后者生效，是直接启动失败。**

| 选法 | 怎么做 |
|---|---|
| **用 XML（推荐）** | 删掉 `UserMapper.java` 的 `@Select` 和第 5 行 `import ...annotations.Select;`，SQL 搬到 XML 的 `<select>` 里（`id` 必须与方法名一字不差） |
| **用注解** | 保持现状，`UserMapper.xml` 就一直空着（或直接删掉这个文件） |

**推荐 XML 的理由**：项目里 `OrderMapper` 已经是 XML 风格（动态 SQL `<if>` 也在 XML 里），
两套风格混用会让后面的人（包括三个月后的你）困惑。

**坑 8：密码比对只能用 `matches(明文, 密文)`，参数别写反（2026-09-17 实测）**

数据库 `t_user.password` 存的是 **BCrypt 密文，60 个字符**（`$2a$10$wW6FiEDcYu...`），
哈希不可逆 → **不能 `equals`、不能解密**，只能"拿明文重新算一遍看能不能对上"。

```java
// AuthServiceImpl 字段（只需一个实例，别每次 new）
private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

// 比对：明文在前，密文在后
if (!passwordEncoder.matches(password, user.getPassword())) { ... }
```

实测结果（用种子数据里那串真实密文跑的）：

| 调用 | 结果 |
|---|---|
| `matches("123456", dbHash)` | **true** ✅ |
| `matches(dbHash, "123456")` 参数写反 | false，且日志打印 `Encoded password does not look like BCrypt` |
| `matches("123456 ", dbHash)` 多一个空格 | false |
| `matches("123456", dbHash + " ")` 密文尾部多空格 | false |

> **判断"我是不是写反了"的信号**：控制台出现 `警告: Encoded password does not look like BCrypt`
> 就是参数顺序反了（它在拿明文当密文解析）。
>
> 另外 `new BCryptPasswordEncoder()` 每次 `encode("123456")` 出来的密文**都不一样**（内含随机盐），
> 但每一个都能用 `matches("123456", ...)` 验过 —— 所以**不能拿密文和密文比较**。

> 顺带一条独立跑的小坑：`BCryptPasswordEncoder` 构造时要读 `org.apache.commons.logging.LogFactory`，
> 如果写一个 `main()` 单独测它会报 `NoClassDefFoundError`，补上 `spring-jcl` 就行。
> **在真实项目里 `spring-boot-starter-web` 自带 `spring-jcl`，不用管这件事。**

**为什么不能"把明文用同样的算法加密一遍，然后和数据库比字符串"（2026-09-17 实测）**

这个思路**方向是对的**，BCrypt 下却会 100% 失败 —— 因为 BCrypt 每次都生成**随机盐**，
同一句话 `encode()` 两次结果完全不同：

```
第 1 次 encode("123456") : $2a$10$f7fkJrYNTa5LhexaGXWK3uxFNnUIblqZ5JfHOjthTSesVEZKSDkRa
第 2 次 encode("123456") : $2a$10$zgILJ3IyE9NDt9dO7uYJsO9V8HH9CPNQaKsFXdrF29UbvkDNHv0tm
```

**60 个字符是三段拼起来的**（`$2a$10$` + 22 字符盐 + 31 字符哈希），**盐是明文写在密文里的**：

```
完整密文 : $2a$10$wW6FiEDcYu.6H2txuF9EGuhp6jNmGR4gbg33/wB3oeqPCwYizNhC2
前缀     : $2a$10$                    （7）
盐       : wW6FiEDcYu.6H2txuF9EGu     （22）
哈希值   : hp6jNmGR4gbg33/wB3oeqPCwYizNhC2 （31）
```

所以**只要用密文里那一把盐重算，结果就能和密文逐字符相同** —— 实测：

| 写法 | 结果 |
|---|---|
| `BCrypt.hashpw("123456", DB_HASH)` | 原样得到 `DB_HASH` → **相等** ✅ |
| 手动抠前 29 字符当盐 (`$2a$10$wW6FiEDcYu.6H2txuF9EGu`) | 同上 ✅ |
| `BCrypt.hashpw("123456", BCrypt.gensalt(10))` 用**新随机盐** | **不相等** ❌ ← 「加密一遍再比」失败的原因 |
| `BCrypt.hashpw("123457", DB_HASH)` 密码错、同盐 | 不相等 ✅（能正确识别错误密码） |

**对照 MD5**（老教程为什么能写 `dbPwd.equals(md5(input))`）：MD5 无盐、结果确定，
`md5("123456")` 两次都是 `e10adc3949ba59abbe56e057f20f883e`。
但 MD5 现在不能用：无盐 → 彩虹表一击命中；太快 → 一张显卡每秒几十亿次。
**BCrypt 把盐写进密文，就是为了让你不用自己管盐。**

> `matches()` 内部做的事 = 「取密文里的盐 → 用同盐重算 → 比较」，也就是把上面第三段那行手写代码封了一层。
> 它比手写多两件事（`javap` 核实）：① `BCRYPT_PATTERN` 先校验密文格式（不符合只 warn + 返回 false，不抛异常）；
> ② 用 `equalsNoEarlyReturn`（内部是 `MessageDigest.isEqual`）做**等长比较**，防时序攻击 ——
> 普通 `String.equals` 发现第一个不同字符就返回，攻击者能靠响应时间逐字节猜出密文长度差异。

**坑 9：抛异常必须有"人"接，否则前端只能看到「请求失败」（当前代码的真实阻塞点）**

`AuthServiceImpl` 里 `throw new AccountNotFoundException(...)`，但全项目
**没有任何 `@ControllerAdvice` / `@ExceptionHandler`**（`grep` 过，0 处）。
异常一路冒到 Spring 的默认错误处理 → HTTP **500**，响应体是
`{timestamp, status, error, path}` 这种格式，**里面没有 `code` 字段**。

而 `frontend/assets/api.js` 的 `unwrap()` 是这么判的：

```js
if (result.code !== 1) {
  var err = new Error(result.msg || '请求失败');   // ← result.msg 是 undefined
  throw err;                                       // → 页面显示「请求失败」
}
```

`undefined !== 1` 恒成立，`result.msg` 也是 `undefined` → **页面永远显示「请求失败」**，
你精心写的 `"密码错误"` 一个字都到不了用户眼前。

两个解决方向（**二选一，见 §10.0 第 8 步的方案 A/B**）：

| | 做法 | 代价 |
|---|---|---|
| **A** | Service 不抛异常，直接 `return Result.error("用户名或密码错误")` | Service 返回类型改成 `Result<LoginVO>`；已写的 `throw` 要删掉 |
| **B（推荐，配合现有代码）** | 保留抛异常，新建 `handler/GlobalExceptionHandler`（`@RestControllerAdvice` + 几个 `@ExceptionHandler`），把异常翻译成 `Result.error(msg)` | 多一个文件（约 30 行）；但后面拦截器的 401、参数校验都要靠它，早晚得加 |

> 无论选哪个，**业务错误都必须返回 HTTP 200 + `{code:0,msg:...}`**，
> 因为前端只看 `code`、不看 HTTP 状态码。

**坑 10：失败路径 500 的真实日志 —— 以及它和「用户名枚举」的关系（2026-09-17 实测）**

服务端日志原文（`throw` 出去的文案其实"送出去"了，**但只送进了日志，没送给前端**）：

```
ERROR ... [dispatcherServlet] ... threw exception
  [Request processing failed: java.lang.Exception: 密码错误] with root cause
java.lang.Exception: 密码错误
ERROR ... [Request processing failed:
  javax.security.auth.login.AccountNotFoundException: 账号不存在] with root cause
```

而同一时刻前端收到的是：

```json
{"timestamp":"2026-09-17T09:21:26.480Z","status":500,
 "error":"Internal Server Error","path":"/api/auth/login"}
```

两个连带结论：

1. **前端永远看不到 `"密码错误"`**，只会弹「请求失败」—— 详见坑 9 的 `unwrap()` 分析。
2. **②（密码错）和 ③（用户不存在）目前响应体逐字符一致**（只差 timestamp），所以用户名枚举
   漏洞**现在碰巧没暴露** —— 但这是被"500"掩盖的假安全。**一旦按方案 B 把 `msg` 透出来，
   `"密码错误"` vs `"账号不存在"` 立刻变成可用的账号探测接口。**
   → 所以补全局异常处理器的同时，**必须把两个常量合并成 `LOGIN_FAILED = "用户名或密码错误"`**。

**坑 11：`Integer == Integer` 比的是对象地址，不是数值（2026-09-17 实测）**

实体里是 `private Integer status;`，`StatusConstant.DISABLE` 也是 `Integer`。
**两边都是包装类型时 `==` 走引用比较**（JLS 15.21.3），实测：

| 表达式 | 结果 | 说明 |
|---|---|---|
| `Integer.valueOf(0) == StatusConstant.DISABLE` | **true** | 0 落在 Integer 缓存（-128~127）内，是同一个对象 |
| `Integer.valueOf(1) == StatusConstant.DISABLE` | false | 想要的结果 |
| `Integer.valueOf(300) == Integer.valueOf(300)` | **false** | 超出缓存 → 两个对象 → 明明相等却 false |
| `(int) 300 == Integer.valueOf(300)` | true | 只有一边是基本类型时才拆箱 |
| `null == StatusConstant.DISABLE` | false | 不抛 NPE，但也不命中 |

**现在这行代码是"靠缓存侥幸正确"**：status 只有 0/1，都在缓存里。不算逻辑错，但不该靠运气：

```java
if (StatusConstant.DISABLE.equals(user.getStatus())) {   // 常量放左边，null 也安全
```

> 别写成 `user.getStatus().equals(StatusConstant.DISABLE)` —— status 为 null 时会 NPE。

**坑 12：8080 被 National Instruments 的服务占着（会让 IDEA 启动直接失败）**

```
netstat -ano | findstr :8080        → TCP 0.0.0.0:8080 LISTENING 11792
tasklist /FO CSV /SVC               → "ApplicationWebServer.exe","11792","NIApplicationWebServer"
curl http://localhost:8080/api/auth/login
                                    → 404，响应头是 Server: Embedthis-http（不是你的 Spring Boot）
```

`NIApplicationWebServer` 是 **National Instruments**（LabVIEW / Multisim 那家）装的 Windows 服务，
默认监听 8080。**所以在 IDEA 里启动后端会报 `Port 8080 was already in use`。**

管理员身份运行 CMD / PowerShell：

```
sc stop NIApplicationWebServer
sc config NIApplicationWebServer start= demand
```

第一条立刻停掉，第二条把启动类型改成「手动」→ 重启后不再自动抢 8080。
不用 NI 软件的话这样改没副作用；要用时 NI 自己的程序会拉起它，也可手动 `sc start NIApplicationWebServer`。

> 备选是把 Spring Boot 换端口，但得同步改 nginx 反代目标和前端 `API_BASE`，**不推荐**。
> 想临时验证又不想动 8080，启动参数加 `--server.port=18080` 就行。
>
> **顺带一个命令行环境的坑**：`./mvnw.cmd` 用的是 **`C:\Users\mayiran\.m2`**，里面
> `jjwt-impl` / `jjwt-jackson` / `jackson-databind` **只有 `.pom` 没有 `.jar`**
> （runtime scope，编译期用不到，所以 `compile` 一直是绿的）→ 命令行 `spring-boot:run`
> 会在**运行期**缺类。IDEA 用的是 D 盘仓库（`conf/settings.xml` 配的
> `D:\B24090110\My_maven\apache-maven-3.9.4\MVN_repo`，jar 齐全），**IDEA 启动不受影响**。
> 要让命令行也能跑就加 `-Dmaven.repo.local=D:/B24090110/My_maven/apache-maven-3.9.4/MVN_repo`，
> 且**不能加 `-o`** —— D 盘仓库的 `_remote.repositories` 记的是 `alimaven` 镜像，
> 离线模式下 Maven 会判 `present, but unavailable`。

### 10.6 验证（三条 curl，全过才算完成）

```bash
# ① 正确密码 → 应该出 token
curl -s -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"user0108","password":"123456"}'

# ② 错密码 → {"code":0,"msg":"用户名或密码错误"}
curl -s -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"user0108","password":"wrong"}'

# ③ 不存在的用户 → 响应必须和 ② 一模一样（逐字符对比）
curl -s -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"nobody","password":"123456"}'
```

② 和 ③ 的输出**必须完全一致** —— 这是"防用户名枚举"这条设计唯一能自测的方式。

账号参考：`user0001` = USER 谢雨欣（3 单）；`user0108` = AGENT 郭思远。
库里 200 个用户共用同一个 BCrypt hash，**明文统一 `123456`**。

### 10.7 前端契约（后端字段名不能改）

`frontend/assets/api.js` 第 116-122 行写死了：

```js
lsSet('cs_token', data.token);              // ← 必须叫 token
lsSet('cs_user', JSON.stringify(data.user)); // ← 必须叫 user
```

后续每个请求由 `api.js` 自动带上 `Authorization: Bearer <token>`
（`TOKEN_HEADER` / `TOKEN_PREFIX` 两个常量）。所以后端字段名写成 `accessToken` 或者
`userInfo`，前端会存进去一个 `undefined`，然后所有接口 401 —— 而且报错信息不会告诉你原因。
**接口文档写什么名字就用什么名字。**

---

### 10.8 依赖落地记录（2026-09-17 已完成）

#### 实际写进 `pom.xml` 的四个依赖

| 依赖 | 版本 | scope | 说明 |
|---|---|---|---|
| `io.jsonwebtoken:jjwt-api` | 0.12.5 | compile | 你代码里 `import` 的 `Jwts` / `Claims` 都在这 |
| `io.jsonwebtoken:jjwt-impl` | 0.12.5 | runtime | 实现类，编译期用不到 |
| `io.jsonwebtoken:jjwt-jackson` | 0.12.5 | runtime | token 的 payload 靠它转 JSON |
| `org.springframework.security:spring-security-crypto` | **不写** | compile | 父 POM 的 `spring-security-bom` 管理 → 实际解析到 **7.0.7** |

#### 实测验证（`mvnw.cmd dependency:tree`）

```
+- io.jsonwebtoken:jjwt-api:jar:0.12.5:compile
+- io.jsonwebtoken:jjwt-impl:jar:0.12.5:runtime
+- io.jsonwebtoken:jjwt-jackson:jar:0.12.5:runtime
|  \- com.fasterxml.jackson.core:jackson-databind:jar:2.21.5:runtime
\- org.springframework.security:spring-security-crypto:jar:7.0.7:compile
```

**这里有个 Spring Boot 4 的关键点**：Spring Boot 4 把默认 JSON 库换成了 **Jackson 3**
（groupId 是 `tools.jackson`，classpath 里有 `tools.jackson.core:jackson-databind:3.1.5`），
而 `jjwt-jackson` 需要的是老的 **Jackson 2**（`com.fasterxml.jackson.core`）。

两者**能共存**，因为 groupId 和包名都不同，互不干扰。更巧的是 Spring Boot 4.0.8 的
`spring-boot-dependencies` 里**同时管理着两套 Jackson 的 BOM**：

- `jackson-2-bom.version = 2.21.5` → 管 `com.fasterxml.jackson.*`
- `jackson-bom.version = 3.1.5` → 管 `tools.jackson.*`

所以 `jjwt-jackson` 传递依赖进来的 Jackson 2 会被自动提升到 **2.21.5**
（而不是 jjwt 自带的老版本 2.12.7.1）。**你不用手写版本，也别去 `<exclusions>` 排除它。**

#### 顺手修掉的一处：`JwtUtil.java` 类名笔误

`public class JwtUwil` → `public class JwtUtil`（详见 §10.5 坑 4）。
就这一处，让编译报出 5 条错；改完立刻 `BUILD SUCCESS`。

#### 环境提醒：本机有两套 Maven 仓库

| 谁在用 | 本地仓库位置 | 原因 |
|---|---|---|
| **IDEA** | `D:\B24090110\My_maven\apache-maven-3.9.4\MVN_repo` | `conf\settings.xml` 第 55 行配了 `localRepository` |
| **命令行 `mvnw.cmd`** | `C:\Users\mayiran\.m2\repository` | `~/.m2/settings.xml` **不存在**，只能落到默认位置 |

后果：同一个依赖可能被下载两份；偶尔会出现"命令行能编译、IDEA 报错"（或反过来）的困惑，
因为两边看到的依赖不是同一份。**想让两边一致**，新建 `C:\Users\mayiran\.m2\settings.xml`：

```xml
<settings>
  <localRepository>D:\B24090110\My_maven\apache-maven-3.9.4\MVN_repo</localRepository>
</settings>
```

不建也能跑（两边各自工作），只是多占一份磁盘空间。

---

### 10.9 JWT 令牌的组成与 `JwtUtil` 规格（2026-09-17）

#### 一、令牌 = 三段，用 `.` 连接

```
eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9 . eyJ1c2VySWQiOjEsInJvbGUiOiJVU0VSIn0 . dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk
             Header                              Payload                                    Signature
```

| 段 | 内容 | 怎么来的 |
|---|---|---|
| **Header** | `{"alg":"HS256","typ":"JWT"}` | 固定模板，Base64URL 编码 |
| **Payload** | `{"userId":1,"role":"USER","iat":...,"exp":...}` | 你放进 `.claim()` 的东西，Base64URL 编码 |
| **Signature** | 二进制签名再 Base64URL | `HMAC-SHA256( 第一段 + "." + 第二段, 密钥 )` |

> 上表是**通用**举例（HS256 / 带 `typ`）。**本项目实际签出来的 Header 是 `{"alg":"HS512"}`**，
> 第一段对应 `eyJhbGciOiJIUzUxMiJ9` —— 因为密钥是 64 个字符（见本节四）。
> 自己签出来的 token 跟这个例子不完全一样是正常的，别当成 bug。

#### 二、必须记住的一条：前两段不加密

实测（不需要任何密钥）：

```bash
echo 'eyJ1c2VySWQiOjEsInJvbGUiOiJVU0VSIiwiaWF0IjoxNzU4MDg4MDAwLCJleHAiOjE3NTgxNzQ0MDB9' | base64 -d
# → {"userId":1,"role":"USER","iat":1758088000,"exp":1758174400}
```

由此推出三条硬规矩：

- **Payload 里绝不能放**：密码、手机号、身份证、任何密文。谁拿到 token 都能读。
- **密钥的唯一作用是防篡改**，不是防读取。改了 payload 一个字符，第三段立刻对不上，`parse()` 抛异常。
- 「JWT 里放 userId 是安全的」的准确含义是：**别人能看到 `userId=1`，但改不成 `userId=118`**。

> 这也解释了为什么 Base64URL 要单独发明：把 `+` `/` 换成 `-` `_`、去掉末尾 `=` 填充，
> 因为 token 要塞进 HTTP 头，不能出现这些字符。

#### 三、Payload 放什么（本项目）

| claim | 类型 | 值从哪来 | 用途 |
|---|---|---|---|
| `userId` | Long | `user.getId()` | 拦截器 → `UserContext`；Service 用它覆盖 `DTO.userId` |
| `role` | String | `user.getRole()` ← **code，不是 roleText** | USER 只看自己的单；AGENT/ADMIN 看全部 |
| `iat` | Date | `.issuedAt(new Date())` | 签发时间 |
| `exp` | Date | `.expiration(...)` | 过期时间；过期后 `parse()` 抛 `ExpiredJwtException` |

**不放**：username / nickname / phone —— 它们会变，需要时拿 `userId` 查库更准。

> ⚠️ **`role` 必须传 `user.getRole()`（`"AGENT"`），不能传 `roleText`（`"客服"`）。**
> 传错**不会报任何错**，只会让所有 `"AGENT".equals(role)` 判断静默失败 ——
> 客服登录后只看到自己那几单，页面一切正常，最难查的那种 bug。

**关于 `sub`**：RFC 里 `sub` 是给"主题"用的，但它是 `String` 类型。userId 是 Long，
塞进去取出来还要 `Long.parseLong`。**直接 `claim("userId", userId)` 更省事**，两种都对。

#### 四、HS256 的密钥：长度是你唯一要管的事

`Keys.hmacShaKeyFor(byte[])` **按字节长度自动选算法**（jjwt 源码里从高到低判断，命中即返回）：

| 密钥字节数 | 选中算法 |
|---|---|
| ≥ 64 | HS512 |
| ≥ 48 | HS384 |
| ≥ 32 | HS256 |
| < 32 | 抛 `WeakKeyException`（启动就失败） |

**所以 `openssl rand -base64 48` 得到 64 个字符 → jjwt 会选 HS512，不是 HS256。** 这是正常的，也完全安全 —— **不要为了"用 HS256"去缩短密钥**。

想确认最终用了哪个算法：把生成 token 的第一段 `base64 -d` 一下，`alg` 字段就是答案。

> **面试话术**：HS256 / HS512 都是**对称**算法（签发和验证用同一把密钥），适合单体服务；
> RS256 是**非对称**（私钥签、公钥验），适合多服务共享验签、或客户端要验证签名的场景。
> 本项目一个服务自己签发自己验证，HS 系列是正确选择。

#### 五、`JwtUtil` 规格

**类设计：`@Component`，不是静态工具类** —— 它要读配置。

| 成员 | 类型 | 来源 |
|---|---|---|
| `key` | `SecretKey`（**不是 String**） | 构造器里由 `secret` 算出来 |
| `expireHours` | `long` | `@Value("${jwt.expire-hours}")` |

| 方法 | 签名 | 里面做什么 |
|---|---|---|
| 生成 | `String generate(Long userId, String role)` | builder → claim → issuedAt → expiration → signWith(key) → compact() |
| 解析 | `Claims parse(String token)` | parser → verifyWith(key) → parseSignedClaims → getPayload() |
| 便捷 | `Long getUserId(String token)` | `parse(token).get("userId", Long.class)` |
| 便捷 | `String getRole(String token)` | `parse(token).get("role", String.class)` |

**0.12.5 的 API —— 新写法 / 老写法对照**：

> 再强调一次：右列的老写法**不会报错，只是已弃用**。列出来是为了让你**认出**"这段代码是旧的"，
> 不是"这样写会崩"。我实测过：0.9.x 风格写出来的代码在 0.12.5 下编译通过、签发解析都正常。

| 目的 | 正确写法（0.12.x） | 老写法（0.9.x，已弃用） |
|---|---|---|
| 造密钥 | `Keys.hmacShaKeyFor(secret.getBytes(UTF_8))` | `Keys.secretKeyFor(HS256)`（已废弃） |
| 签发 | `Jwts.builder()...signWith(key).compact()` | `signWith(SignatureAlgorithm.HS256, "字符串")` |
| 解析 | `Jwts.parser().verifyWith(key).build().parseSignedClaims(token)` | `.setSigningKey(...)` / `.parseClaimsJws(...)` |
| 设时间 | `subject()` / `expiration()` / `issuedAt()` | `setSubject()` / `setExpiration()` |

**`signWith(key)` 不用传算法** —— key 自己带着算法信息。`Keys.hmacShaKeyFor(64字节)` 造出来的是
HmacSHA512 的 key，所以签出来是 HS512。

> ⚠️ 但**别理解成"HS512 的 key 只能签 HS512"** —— 实测可以跨。用老写法
> `signWith(SignatureAlgorithm.HS256, 同一个 64 字节 key)` 能签出合法的 HS256 token：
> jjwt 只校验"key 长度 ≥ 该算法要求"，**不校验 key 的算法名**。

**实测：两种写法到底差在哪**（2026-09-17 跑出来的真数据）

| 密钥长度 | 老写法 `signWith(HS256, bytes)` | 新写法 `signWith(key)` |
|---|---|---|
| 64 字符（512bit） | `{"alg":"HS256"}`，签名段 **32** 字节 | `{"alg":"HS512"}`，签名段 **64** 字节 |
| 32 字符（256bit） | `{"alg":"HS256"}`，签名段 32 字节 | `{"alg":"HS256"}`，签名段 32 字节 |
| 20 字符（160bit） | ❌ `WeakKeyException` | ❌ `WeakKeyException` |

签名段的字节数就是算法的**指纹**：32 字节 = SHA-256，48 = SHA-384，64 = SHA-512。
（base64 之后长度约为其 4/3，所以 43 字符左右的签名段 = HS256。）

**还做了交叉验证**：老写法签发的 HS256 token，用新写法 `verifyWith(同一个 key)` 能验签成功，
反过来也成立。**结论：只要密钥 ≥32 字符，两种写法签出的 token 互相都认。**
所以"用哪一版"不是安全问题，是**工程习惯**问题 —— 老写法有弃用警告、`setClaims` 会覆盖、
泛型 `Map` 容易打错 key，新写法这几样都没有。

#### 六、写 `JwtUtil` 时的三个坑

**坑 1：不要在字段声明处算 key。**

```java
@Value("${jwt.secret}") private String secret;
private SecretKey key = Keys.hmacShaKeyFor(secret.getBytes());   // ❌ secret 这时还是 null → NPE
```

正确做法：**构造器注入**（推荐，`key` 还能是 `final`），或 `@PostConstruct` 里初始化。
`@Value` 是在对象构造**之后**才注入的，任何"声明处就用它"的写法都会拿到 null。

**坑 2：`claims.get("userId")` 直接强转 `(Long)` 会 `ClassCastException`。**

Jackson 把 JSON 里的小整数还原成 `Integer`，不是 `Long`。必须用带类型参数的重载：

```java
claims.get("userId", Long.class)     // ✅ jjwt 内部帮你转换
(Long) claims.get("userId")          // ❌ Integer cannot be cast to Long
```

**坑 3：`jwt.secret` 配不上会启动失败**，报 `Could not resolve placeholder 'jwt.secret'`。
`application.properties` 已被 `.gitignore` 忽略（所以密码安全），但**换机器 / clone 下来必须重新配**。

#### 七、`JwtUtil` 无法单独验证

它没有 HTTP 入口，只能被 `AuthService` 调用。所以验证分两步：

1. **写完先保证编译通过**：`./mvnw.cmd -B compile` → `BUILD SUCCESS`
2. **真正的验证放到登录 curl 那一步**：拿到 token 后，`base64 -d` 第一段看到
   `"alg":"HS512"` 就说明签发正常；token 能不能通过校验，要等第 4 步拦截器做完才有接口可测

> 别为了"先验证 JwtUtil"去写一个临时 `main` 方法 —— Spring 容器不启动，`@Value`
> 注入不生效，你得手动 `new` 再手动塞 secret，反而多花 20 分钟。

#### 八、`RoleEnum` 已完成，以及一条实测出来的语法规则（2026-09-17）

`enums/RoleEnum.java` 已写完并通过编译核对：三常量（`USER` / `AGENT` / `ADMIN`，各带中文）、
`private final String text`、私有构造器、`getText()`、静态 `of(String code)`（未命中抛
`IllegalArgumentException("未知角色: " + code)`）。

**实测出来的三条硬规则**（项目外真编译，见 `.workbuddy/tmp-roleenum/`）：

| 规则 | 违反时的 javac 报错 |
|---|---|
| 常量必须写在类的**最上面** | `此处需要枚举常量` |
| 常量之间用**逗号**，整列表末尾用**分号** | `此处不需要枚举常量`（写成 `A; B;`）/ `需要',', '}'或';'`（漏分号） |
| 只要有**带参构造器**，每个常量都必须带括号 | `需要: String 找到: 没有参数` |

**另一个容易搞反的点**：枚举构造器**不写修饰符就等于 `private`**。手写上 `public` 会报
`此处不允许使用修饰符public`。这条"构造器私有"正是"只可能有三个实例"的实现方式 ——
所以 `RoleEnum.AGENT == RoleEnum.of("AGENT")` 为 `true`，比较可以直接用 `==`。

**为什么不用自带的 `valueOf()`**：传非法值时它抛
`No enum constant com.mayiran.commerceservice.enums.RoleEnum.AGNET`（一大串英文类名），
自己包一层 `of()` 才能给出 `未知角色: AGNET` 这种能直接看懂的报错。

#### 九、登录成功后怎么签发 token（在哪一层调 `generate`）

`AuthService.login()` 的返回值是 `User`（**不含 token**），所以 token 在 **`AuthController`** 里生成。
`JwtUtil` 是 `@Component`，Controller 直接 `@Autowired` 注入即可。

```java
@Autowired
private JwtUtil jwtUtil;

@PostMapping("/login")
public Result<LoginVO> login(@RequestBody LoginDTO loginDTO) throws Exception {
    User user = authService.login(loginDTO);

    // ① 签发：用户 id + 角色 code
    String token = jwtUtil.generate(user.getId(), user.getRole());

    // ② 组装返回给前端的用户信息（这里的 password 天然不会带出去，UserVO 里没有这个字段）
    UserVO userVO = UserVO.builder()
            .id(user.getId())
            .username(user.getUsername())
            .nickname(user.getNickname())
            .phone(user.getPhone())
            .role(user.getRole())                              // code，前端靠它跳页
            .roleText(RoleEnum.of(user.getRole()).getText())   // 中文，只用于显示
            .build();

    // ③ 包成前端约定的形状
    return Result.success(new LoginVO(token, userVO));
}
```

**`generate(Long userId, String role)` 两个参数怎么给**：

| 参数 | 传什么 | 不能传什么 |
|---|---|---|
| `userId` | `user.getId()`（`Long`） | ❌ `username` —— 拦截器要用它覆盖前端传的 userId，必须能唯一标识 |
| `role` | `user.getRole()`，即 `"USER"` / `"AGENT"` / `"ADMIN"` | ❌ `roleText`（`"客服"`）—— 前端 `login.html` 拿 `role === 'USER'` 判跳页，中文判断不出来 |

**实测：用项目真实 `jwt.secret` 签出来的 token**（把项目里的 `JwtUtil.java` 原样编译后跑，
用 `user0108` 即 id=108 / AGENT）：

```
长度 192 字符，3 段，分隔符是「.」
第 1 段 20 字符（Header） ：{"alg":"HS512"}
第 2 段 84 字符（Payload）:{"userId":108,"role":"AGENT","iat":1789635816,"exp":1789722216}
第 3 段 86 字符（签名）   ：HMAC-SHA512 的二进制结果再 Base64URL

解析回来：userId=108（Java 类型 Long）、role=AGENT、有效期 24.0 小时
```

**四条注意点**：

1. **payload 是明文**（Base64 只是编码不是加密），任何拿到 token 的人都能解出 `userId` 和 `role`。
   所以**不要把密码、手机号、身份证塞进 claim**。JWT 保证的是"**防篡改**"，不是"防偷看"。
2. **第一段是 `HS512` 不是 `HS256`** —— 密钥 64 字符 = 512 bit，jjwt 按长度自动选的。别以为写错了。
3. **token 不用存数据库**（无状态）。前端存在浏览器本地，后续请求放在
   `Authorization: Bearer <token>`。后端只在拦截器里 `parse` 校验，不查库。
4. **别从请求参数里读 userId** —— 这是防越权的关键：拦截器解析 token 得到 userId 后，
   **强制覆盖**前端传来的同名字段（否则普通用户改个参数就能看别人的订单，就是之前踩过的那个坑）。


