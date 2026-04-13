package com.example.syncforge.user.service;

import com.example.syncforge.user.entity.User;
import com.example.syncforge.user.mapper.UserMapper;
import org.springframework.stereotype.Service;
import org.springframework.security.crypto.password.PasswordEncoder;

@Service
public class UserService {

    private final UserMapper userMapper;
    // 编码器hash
    private final PasswordEncoder passwordEncoder;

    public UserService(UserMapper userMapper, PasswordEncoder passwordEncoder) {
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
    }

    public User getById(Long id){
        return userMapper.findById(id);
    }
    public User getByUsername(String username){
        return userMapper.findByUsername(username);
    }
    // 使用用户名查找后再进行 BCrypt 校验
    public User login(String username, String rawPassword) {
        // 找用户
        User user = userMapper.findByUsername(username);
        // 没该用户或者前端没传密码
        if (user == null || rawPassword == null) {
            return null;
        }
        // matches 内部完成hash比较 如果匹配成功返回用户对象 否则返回null
        // 自动把明文和hash比较 用明文和密文比较
        return passwordEncoder.matches(rawPassword, user.getPasswordHash()) ? user : null;
    }

    public  int create(User user){
        if (user.getPasswordHash() != null && !user.getPasswordHash().trim().isEmpty()) {
            // passwordHash不为空
            // 编码后加入 user 库中存的是密文
            user.setPasswordHash(passwordEncoder.encode(user.getPasswordHash()));
        }
        return userMapper.insert(user);
    }
}
