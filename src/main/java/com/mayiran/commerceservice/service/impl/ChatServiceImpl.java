package com.mayiran.commerceservice.service.impl;

import com.mayiran.commerceservice.constant.MessageConstant;
import com.mayiran.commerceservice.context.UserContext;
import com.mayiran.commerceservice.dto.ChatSendDTO;
import com.mayiran.commerceservice.dto.ChatSessionDTO;
import com.mayiran.commerceservice.entity.ChatMessage;
import com.mayiran.commerceservice.entity.ChatSession;
import com.mayiran.commerceservice.exception.ChatSessionNotFoundException;
import com.mayiran.commerceservice.mapper.ChatMessageMapper;
import com.mayiran.commerceservice.mapper.ChatSessionMapper;
import com.mayiran.commerceservice.service.ChatService;
import com.mayiran.commerceservice.vo.ChatReplyVO;
import com.mayiran.commerceservice.vo.ChatSessionVO;
import com.mayiran.commerceservice.vo.ChatSessionIdVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

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

    @Override
    public ChatSessionIdVO getSessionId(ChatSessionDTO dto) {
        Long userId=UserContext.getUserId();

        String sessionId=createSession(userId);
        log.info("新建会话成功,userId:{},sessionId:{}",userId,sessionId);
        return ChatSessionIdVO.builder()
                .sessionId(sessionId)
                .build();
    }

    @Override
    public List<ChatSessionVO> listSessions(Long userId) {
        //角色同样从UserContext取
        String role = UserContext.getRole();
        boolean canQueryOthers="AGENT".equals(role)||"ADMIN".equals(role);

        Long targetUserId=canQueryOthers?userId:UserContext.getUserId();
        log.info("查询会话列表,当前用户:{},角色:{},实际查询userId:{}",UserContext.getUserId(),role,targetUserId);

        List<ChatSessionVO> sessions = chatSessionMapper.listByUserId(targetUserId);
        if(sessions==null||sessions.isEmpty()){
            return Collections.emptyList();
        }
        //库里存的是整段原文,列表只放一小截
        for(ChatSessionVO vo:sessions){
            vo.setTitle(summary(vo.getTitle(),30));
            vo.setLastMessage(summary(vo.getLastMessage(),40));
        }
        return sessions;
    }

    /**
     * 截取字符串
     * @param text
     * @param max
     * @return
     */
    private String summary(String text, int max) {
        if(text==null){
            return null;
        }

        String flat=text.replaceAll("\\s+"," ").trim();
        if(flat.isEmpty()){
            return null;
        }

        return flat.length()<=max?flat:flat.substring(0,max)+"...";
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
        String sessionId="S"
                + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
                +String.format("%04d", ThreadLocalRandom.current().nextInt(10000));
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
