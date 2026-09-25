package com.mayiran.commerceservice.service.impl;

import com.mayiran.commerceservice.constant.MessageConstant;
import com.mayiran.commerceservice.dto.ChatSendDTO;
import com.mayiran.commerceservice.entity.ChatMessage;
import com.mayiran.commerceservice.entity.ChatSession;
import com.mayiran.commerceservice.exception.ChatSessionNotFoundException;
import com.mayiran.commerceservice.mapper.ChatMessageMapper;
import com.mayiran.commerceservice.mapper.ChatSessionMapper;
import com.mayiran.commerceservice.service.ChatService;
import com.mayiran.commerceservice.vo.ChatReplyVO;
import com.mayiran.commerceservice.vo.ToolCallVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class ChatServiceImpl implements ChatService {
    @Autowired
    private ChatSessionMapper chatSessionMapper;
    @Autowired
    private ChatMessageMapper chatMessageMapper;
    @Autowired
    private RestClient aiRestClient;
    @Autowired
    private ObjectMapper objectMapper;

    @Override
    public ChatReplyVO chat(Long userId, ChatSendDTO chatSendDTO) {
        //1:拿到一个合法的会话号
        String sessionId=chatSendDTO.getSessionId();
        //判断会话号是否为空
        if(sessionId==null||sessionId.isBlank()){
            //为空则创建会话号
            sessionId=createSession(userId);
        }else {
            ChatSession session =chatSessionMapper.getBySessionId(sessionId);
            if(session==null||!session.getUserId().equals(userId)){
                throw new ChatSessionNotFoundException(MessageConstant.SESSION_NOT_FOUND);
            }
        }
        //2:先把用户这句话落库
        chatMessageMapper.insert(ChatMessage.builder()
                .sessionId(sessionId)
                .role("user")
                .content(chatSendDTO.getMessage())
                .createTime(LocalDateTime.now())
                .build());

        //3:调Python AI服务,失败就降级
        ChatReplyVO reply;

        try{
            Map<String,Object> body=new HashMap<>();
            body.put("userId",userId);
            body.put("sessionId",sessionId);
            body.put("message",chatSendDTO.getMessage());

            reply=aiRestClient.post()
                    .uri("/chat")
                    .body(body)
                    .retrieve()
                    .body(ChatReplyVO.class);
        }catch (Exception e){
            //AI挂了不能把整个对话功能带崩
            log.error("AI服务调用失败,sessionId:{}",sessionId,e);
            reply=ChatReplyVO.fallback("客服繁忙,请稍后再试或转人工");
        }
        if(reply==null){
            reply=ChatReplyVO.fallback("客服繁忙,请稍后再试或转人工");
        }

        //4:把AI的回复也落库
        chatMessageMapper.insert(ChatMessage.builder()
                .sessionId(sessionId)
                .role("assistant")
                .intent(reply.getIntent())
                .content(reply.getAnswer())
                .toolCalls(toJson(reply.getTools()))
                        .confidence(reply.getConfidence()==null? BigDecimal.ZERO:reply.getConfidence())
                        .latencyMs(reply.getLatencyMs())
                .createTime(LocalDateTime.now())
                .build());

        //5:会话号必须返回给前端

        reply.setSessionId(sessionId);
        return reply;
    }

    //对象转JSON字符串
    private String toJson(Object obj) {
        if(obj==null){
            return null;
        }
        try{
            return objectMapper.writeValueAsString(obj);
        }catch (Exception e){
            log.warn("工具调用记录序列化失败,已忽略",e);
            return null;
        }
    }

    /**
     * 生成会话号,格式S+yyyyMMddHHmmss
     * @param userId
     * @return
     */
    private String createSession(Long userId) {
        String sessionId="S"+ LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        LocalDateTime now=LocalDateTime.now();
        chatSessionMapper.insert(ChatSession.builder()
                .sessionId(sessionId)
                .userId(userId)
                .createTime(now)
                .updateTime(now)
                .build());
        return sessionId;
    }
}
