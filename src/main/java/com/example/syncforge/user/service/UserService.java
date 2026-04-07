package com.example.syncforge.user.service;

import com.example.syncforge.user.entity.User;
import com.example.syncforge.user.mapper.UserMapper;
import org.springframework.stereotype.Service;

@Service
public class UserService {

    private final UserMapper userMapper;

    public UserService(UserMapper userMapper) {
        this.userMapper = userMapper;
    }

    public User getById(Long id){
        return userMapper.findById(id);
    }
    public User getByUsername(String username){
        return userMapper.findByUsername(username);
    }

    public  int create(User user){
        return userMapper.insert(user);
    }
}
