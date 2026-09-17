package com.mayiran.commerceservice.mapper;

import com.mayiran.commerceservice.entity.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface UserMapper {
    /**
     * 根据用户名查询用户
     * @param username
     */
    @Select("select id, username, password, nickname, phone, role, status from t_user where username = #{username}")
    User getByUsername(String username);
}
