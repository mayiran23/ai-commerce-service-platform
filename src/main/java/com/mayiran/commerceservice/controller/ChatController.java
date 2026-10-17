package com.mayiran.commerceservice.controller;

import com.mayiran.commerceservice.context.UserContext;
import com.mayiran.commerceservice.dto.ChatSendDTO;
import com.mayiran.commerceservice.dto.ChatSessionDTO;
import com.mayiran.commerceservice.result.Result;
import com.mayiran.commerceservice.service.ChatService;
import com.mayiran.commerceservice.vo.ChatMessageVO;
import com.mayiran.commerceservice.vo.ChatReplyVO;
import com.mayiran.commerceservice.vo.ChatSessionVO;
import com.mayiran.commerceservice.vo.ChatSessionIdVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/chat")
public class ChatController {
    @Autowired
    private ChatService chatService;


    /**
     * 开启会话
     * @param chatSendDTO
     * @return
     */
    @PostMapping
    public Result<ChatReplyVO> chat(@RequestBody ChatSendDTO chatSendDTO){
        //关键:userId从JWT拦截器放进UserContext的值里取,绝不用前端传的
        Long userId= UserContext.getUserId();
        log.info("收到对话请求userId:{},sessionId:{}",userId,chatSendDTO.getSessionId());

        return Result.success(chatService.chat(userId, chatSendDTO));
    }

    /**
     * 流式对话（SSE）。
     *
     * 返回的不是 JSON 而是一条 text/event-stream 长连接，前端要边读边渲染。
     *
     * ⚠️ 这里必须在**当前线程**把 userId 取出来再传下去。
     *    UserContext 底层是 ThreadLocal，方法内部会切到线程池执行，
     *    切过去之后 getUserId() 就是 null 了 —— 这是 SSE 最容易踩的坑，
     *    而且症状很隐蔽：日志里只会看到"查不到订单"，看不出是 userId 丢了。
     */
    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chatStream(@RequestBody ChatSendDTO chatSendDTO) {
        Long userId = UserContext.getUserId();
        log.info("收到流式对话请求userId:{},sessionId:{}", userId, chatSendDTO.getSessionId());
        return chatService.chatStream(userId, chatSendDTO);
    }

    /**
     * 查询会话
     * @param userId
     * @return
     */
    @GetMapping("/sessions")
    public Result<List<ChatSessionVO>> getSession(Long userId){
        log.info("查询会话请求userId:{}",userId);
        return Result.success(chatService.listSessions(userId));
    }

    @PostMapping("/sessions")
    public Result<ChatSessionIdVO> getSessionId(@RequestBody ChatSessionDTO dto){
        log.info("获取会话Id:{}", dto.getUserId());
        ChatSessionIdVO vo=chatService.getSessionId(dto);
        return Result.success(vo);
    }

    @GetMapping("/history")
    public Result<List<ChatMessageVO>> getHistory(String sessionId){
        Long userId = UserContext.getUserId();
        log.info("查询历史消息请求:当前用户:{}",userId);
        return Result.success(chatService.getHistory(userId,sessionId));
    }
}
