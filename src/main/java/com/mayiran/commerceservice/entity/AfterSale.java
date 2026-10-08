package com.mayiran.commerceservice.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class AfterSale implements Serializable {
    private Long id;
    //工单号
    private String ticketNo;
    //订单号
    // ⚠️ 必须小写开头。曾经写成 OrderNo，实测 MyBatis 的属性查找不区分大小写、
    //    所以"恰好"能跑通；但这违反 JavaBean 规范，而且一旦有人打开 Jackson 的
    //    字段可见性（或换成按字段名反射的框架），映射会立刻静默失效。
    //    改名后 Lombok 生成的 getOrderNo()/setOrderNo() 名字一字不变，调用方无需改动。
    private String orderNo;
    //订单中商品的Id
    private Long orderItemId;
    //用户id
    private Long userId;
    //工单类型
    private String type;
    //原因
    private String reason;
    //工单状态
    private String status;
    //是否AI创建
    private Integer aiGenerated;
    //AI置信度
    private BigDecimal aiConfidence;
    //来源
    private String source;
    //处理人的Id
    private Long handlerId;
    //处理备注
    private String handleRemark;
    //创建时间
    private LocalDateTime createTime;
    //更新时间
    private LocalDateTime updateTime;
}
