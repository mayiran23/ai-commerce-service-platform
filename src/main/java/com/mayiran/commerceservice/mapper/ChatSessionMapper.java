package com.mayiran.commerceservice.mapper;

import com.mayiran.commerceservice.entity.ChatSession;
import com.mayiran.commerceservice.vo.ChatSessionVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface ChatSessionMapper {
    //新建会话
    //插入后把数据库生成的id回填到对象的id字段
    int insert(ChatSession chatSession);

    //按会话号进行查询
    ChatSession getBySessionId(@Param("sessionId") String sessionId);

    List<ChatSessionVO> listByUserId(@Param("userId") Long userId);
}
