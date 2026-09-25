package com.mayiran.commerceservice.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ChatReplyVO implements Serializable {
    private String sessionId;

    private String answer;

    private String intent;

    private BigDecimal confidence;

    private Integer latencyMs;

    private List<ToolCallVO> tools;


    /**
     * AI服务不可用时的降级回复
     * 大模型挂掉是常态,但用户不该看到"500服务器错误"
     * @param message
     * @return
     */
    public static ChatReplyVO fallback(String message){
        return ChatReplyVO.builder()
                .answer(message)
                .intent("FALLBACK")
                .confidence(BigDecimal.ZERO)
                .latencyMs(0)
                .tools(List.of())
                .build();
    }
}
