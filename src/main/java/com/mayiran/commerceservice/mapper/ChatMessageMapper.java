package com.mayiran.commerceservice.mapper;
import com.mayiran.commerceservice.entity.ChatMessage;
import com.mayiran.commerceservice.entity.ChatSession;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface ChatMessageMapper {
    //追加一条消息
    int insert(ChatMessage chatMessage);

    //按照会话取全部消息
    List<ChatMessage> getBySessionId(@Param("sessionId") String sessionId);


}
