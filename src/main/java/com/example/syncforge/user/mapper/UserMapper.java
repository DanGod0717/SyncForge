package com.example.syncforge.user.mapper;

import com.example.syncforge.user.entity.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface  UserMapper {
    User findById(@Param("id") Long id);

    User findByUsername(@Param("username") String username);

    User findByUsernameAndPasswordHash(@Param("username") String username,
                                       @Param("passwordHash") String passwordHash);

    int insert(User user);
}
