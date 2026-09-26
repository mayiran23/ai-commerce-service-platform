package com.mayiran.commerceservice.service;

import com.mayiran.commerceservice.dto.ChatSendDTO;
import com.mayiran.commerceservice.dto.ChatSessionDTO;
import com.mayiran.commerceservice.vo.ChatReplyVO;
import com.mayiran.commerceservice.vo.ChatSessionVO;
import com.mayiran.commerceservice.vo.ChatSessionIdVO;

import java.util.List;

public interface ChatService {
    ChatReplyVO chat(Long userId, ChatSendDTO chatSendDTO);

    ChatSessionIdVO getSessionId(ChatSessionDTO dto);

    List<ChatSessionVO> listSessions(Long userId);
}
