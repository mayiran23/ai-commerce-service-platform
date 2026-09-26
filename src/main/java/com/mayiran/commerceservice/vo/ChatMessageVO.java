package com.mayiran.commerceservice.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@AllArgsConstructor
@Builder
@NoArgsConstructor
public class ChatMessageVO implements Serializable {
    /** 谁说的:user / assistant */
    private String role;

    /** 消息正文 */
    private String content;

    /** AI 判定的意图,user 的消息为 null */
    private String intent;

    /** 置信度 0~1,user 的消息为 null */
    private BigDecimal confidence;

    /** 这次回复花了多少毫秒,user 的消息为 null */
    private Integer latencyMs;

    private LocalDateTime createTime;

    /** AI 调用工具的记录,解析后的数组;没调工具就是空数组,不是 null */
    private List<ToolCallVO> tools;
}
