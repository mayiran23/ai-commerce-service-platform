package com.mayiran.commerceservice.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class AfterSaleEligibilityVO implements Serializable {
    /** 能不能申请售后 */
    private Boolean eligible;

    /** 结果码，取值见 EligibilityCode：OK / OVER_DEADLINE / NOT_RECEIVED / NOT_QUALITY */
    private String code;

    /** 给用户看的中文说明，AI 可以直接引用或改写 */
    private String message;

    /** 是否建议转人工审核 —— AI 靠这个字段决定"自己办"还是"交给人" */
    private Boolean suggestManualReview;

    /** 退货截止时间；没有就是 null */
    private LocalDateTime refundDeadline;
}
