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
public class ChatSessionVO implements Serializable {

    private String sessionId;
    private Long userId;
    private String userName;
    private String title;
    private String lastMessage;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
