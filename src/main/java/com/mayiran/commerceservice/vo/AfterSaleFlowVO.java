package com.mayiran.commerceservice.vo;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 工单流转记录（对应 t_after_sale_flow 表的一行）
 *
 * 每发生一次状态变更就写一条，所以一个工单会有多条，
 * 在详情页按 create_time 升序拼成一条时间线。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class AfterSaleFlowVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 流转前的状态。创建工单那一条为 null，表示"从无到有" */
    private String fromStatus;

    /** 流转后的状态 */
    private String toStatus;

    /** 操作者类型：USER / AGENT / ADMIN / SYSTEM / AI */
    private String operatorType;

    /**
     * 操作者名称。
     *
     * ⚠️ t_after_sale_flow 表里【没有】这一列，只有 operator_id。
     * AI 和 SYSTEM 的 operator_id 是 null，join 不出来，
     * 所以要在 SQL 里用 CASE 兜底成"智能客服"，其余才取 t_user.nickname。
     */
    private String operatorName;

    /** 处理备注，比如"照片已核实，确属质量问题，同意退货退款" */
    private String remark;

    /** 这条流转记录产生的时间 */
    private LocalDateTime createTime;
}
