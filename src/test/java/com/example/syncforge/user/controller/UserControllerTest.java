package com.example.syncforge.user.controller;

import com.example.syncforge.auth.JwtUtil;
import com.example.syncforge.common.ApiResponse;
import com.example.syncforge.user.dto.LoginRequest;
import com.example.syncforge.user.dto.UserResponse;
import com.example.syncforge.user.entity.User;
import com.example.syncforge.user.service.UserService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserControllerTest {

    @Mock
    private UserService userService;

    @Mock
    private JwtUtil jwtUtil;

    @InjectMocks
    private UserController userController;

    @Test
    void loginShouldReturn200WithTokenWhenCredentialsAreValid() {
        LoginRequest request = new LoginRequest();
        request.setUsername("owner");
        request.setPassword("pwd-hash");

        User user = new User();
        user.setId(1L);
        user.setUsername("owner");

        when(userService.login("owner", "pwd-hash")).thenReturn(user);
        when(jwtUtil.generateToken(user)).thenReturn("token-123");

        ApiResponse<Map<String, Object>> response = userController.login(request);

        assertEquals(200, response.getCode());
        assertNotNull(response.getData());
        assertEquals("token-123", response.getData().get("token"));
    }

    @Test
    void loginShouldReturn400WhenUsernameOrPasswordMissing() {
        LoginRequest request = new LoginRequest();
        request.setUsername("owner");

        ApiResponse<Map<String, Object>> response = userController.login(request);

        assertEquals(400, response.getCode());
    }

    @Test
    void loginShouldReturn401WhenCredentialsInvalid() {
        LoginRequest request = new LoginRequest();
        request.setUsername("owner");
        request.setPassword("wrong");

        when(userService.login("owner", "wrong")).thenReturn(null);

        ApiResponse<Map<String, Object>> response = userController.login(request);

        assertEquals(401, response.getCode());
    }

    @Test
    void createShouldReturn500WhenInsertFails() {
        User user = new User();
        user.setUsername("u1");
        user.setEmail("u1@example.com");
        user.setPasswordHash("hash");

        when(userService.create(user)).thenReturn(0);

        ApiResponse<UserResponse> response = userController.create(user);

        assertEquals(500, response.getCode());
    }

    @Test
    void createShouldDefaultStatusToOne() {
        User user = new User();
        user.setUsername("u2");
        user.setEmail("u2@example.com");
        user.setPasswordHash("hash");

        when(userService.create(user)).thenReturn(1);

        ApiResponse<UserResponse> response = userController.create(user);

        assertEquals(200, response.getCode());
        assertEquals(Integer.valueOf(1), user.getStatus());
        assertTrue(response.getData() != null);
    }
}

