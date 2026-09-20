package com.mayiran.commerceservice.vo;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.io.Serializable;


@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class AfterSaleVO implements Serializable {
        private static final long serialVersionUID = 1L;

        // ---------- 工单本体（直接来自 t_after_sale）----------
        //工单号
        private String ticketNo;
        //关联的订单号
        private String orderNo;
        //申请人用户ID
        private Long userId;
        //工单类型：REFUND / EXCHANGE / REPAIR
        private String type;
        //申请原因
        private String reason;
        //工单状态：PENDING / MANUAL_REVIEW / APPROVED ...
        private String status;
        //工单状态中文（Java 映射出来的，库里没有这一列）
        private String statusText;
        //来源：AI / WEB
        private String source;
        //处理人ID（未分配时为 null）
        private Long handlerId;
        //处理备注
        private String handleRemark;
        //是否 AI 自动创建（库里是 TINYINT，这里映射成 Boolean）
        private Boolean aiGenerated;
        //AI 置信度，0~1 的小数；WEB 来源时为 null
        private BigDecimal aiConfidence;
        //创建时间
        private LocalDateTime createTime;
        //更新时间
        private LocalDateTime updateTime;

        // ---------- join 出来的关联字段（表里没有）----------
        //商品ID（来自 t_order_item）
        private Long productId;
        //商品名称（来自 t_order_item）—— 列表「商品」列
        private String productName;
        //申请人昵称（来自 t_user，join 别名 u1）
        private String userName;
        //处理人昵称（来自 t_user，join 别名 u2）—— 未分配时为 null
        private String handlerName;
    }


