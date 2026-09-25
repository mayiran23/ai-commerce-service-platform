package com.mayiran.commerceservice.controller;

import com.mayiran.commerceservice.context.UserContext;
import com.mayiran.commerceservice.dto.ChatSendDTO;
import com.mayiran.commerceservice.result.Result;
import com.mayiran.commerceservice.service.ChatService;
import com.mayiran.commerceservice.vo.ChatReplyVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/api/chat")
public class ChatController {
    @Autowired
    private ChatService chatService;


    @PostMapping
    public Result<ChatReplyVO> chat(@RequestBody ChatSendDTO chatSendDTO){
        //关键:userId从JWT拦截器放进UserContext的值里取,绝不用前端传的
        Long userId= UserContext.getUserId();
        log.info("收到对话请求userId:{},sessionId:{}",userId,chatSendDTO.getSessionId());

        return Result.success(chatService.chat(userId, chatSendDTO));
    }
}
