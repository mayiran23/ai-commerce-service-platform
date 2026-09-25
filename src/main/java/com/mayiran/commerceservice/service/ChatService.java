package com.mayiran.commerceservice.service;

import com.mayiran.commerceservice.dto.ChatSendDTO;
import com.mayiran.commerceservice.vo.ChatReplyVO;

public interface ChatService {
    ChatReplyVO chat(Long userId, ChatSendDTO chatSendDTO);
}
