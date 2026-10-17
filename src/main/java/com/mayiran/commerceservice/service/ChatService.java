package com.mayiran.commerceservice.service;

import com.mayiran.commerceservice.dto.ChatSendDTO;
import com.mayiran.commerceservice.dto.ChatSessionDTO;
import com.mayiran.commerceservice.vo.ChatMessageVO;
import com.mayiran.commerceservice.vo.ChatReplyVO;
import com.mayiran.commerceservice.vo.ChatSessionVO;
import com.mayiran.commerceservice.vo.ChatSessionIdVO;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

public interface ChatService {
    ChatReplyVO chat(Long userId, ChatSendDTO chatSendDTO);

    /**
     * 流式对话：边生成边把 AI 的输出推给前端。
     *
     * 为什么返回 SseEmitter 而不是 Flux：
     *   项目用的是 spring-boot-starter-web（Spring MVC），没引 WebFlux。
     *   SseEmitter 是 MVC 自带的异步返回类型，加它不用改依赖。
     *
     * @param userId 必须由调用方在**当前线程**从 JWT 取好再传进来，
     *               因为方法内部会切线程，届时 ThreadLocal 已失效
     */
    SseEmitter chatStream(Long userId, ChatSendDTO chatSendDTO);

    ChatSessionIdVO getSessionId(ChatSessionDTO dto);

    List<ChatSessionVO> listSessions(Long userId);

    List<ChatMessageVO> getHistory(Long userId, String sessionId);
}
