package com.mayiran.commerceservice.dto;

import lombok.Data;

@Data
public class ChatSendDTO {
    //会话号
    private String sessionId;

    //用户问的这句话
    private String message;
}
