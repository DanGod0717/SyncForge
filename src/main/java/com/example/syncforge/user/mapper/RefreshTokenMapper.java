package com.example.syncforge.user.mapper;

import com.example.syncforge.user.entity.RefreshToken;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;

@Mapper
public interface RefreshTokenMapper {
    int insert(RefreshToken refreshToken);

    RefreshToken findByTokenJti(@Param("tokenJti") String tokenJti);

    int revokeByTokenJti(@Param("tokenJti") String tokenJti,
                         @Param("revokedAt") LocalDateTime revokedAt);
}

