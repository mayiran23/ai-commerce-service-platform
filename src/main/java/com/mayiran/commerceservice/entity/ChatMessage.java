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
public class ChatMessage implements Serializable {
    private Long id;
    private String sessionId;
    // 角色：user / assistant / tool
    private String role;
    // AI 判定的意图。user 角色的消息这里是 null
    private String intent;
    private String content;
    // AI 调用工具的记录。表里是 JSON 列，Java 侧当字符串存取即可
    private String toolCalls;
    private BigDecimal confidence;
    // 响应耗时，做离线评估时要用
    private Integer latencyMs;
    private LocalDateTime createTime;
}
