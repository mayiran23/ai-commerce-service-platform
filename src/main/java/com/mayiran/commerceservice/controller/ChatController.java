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
import org.springframework.web.bind.annotation.*;

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
