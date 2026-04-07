package com.example.syncforge.user.controller;


import com.example.syncforge.common.ApiResponse;
import com.example.syncforge.user.entity.User;
import com.example.syncforge.user.service.UserService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/users")
public class UserController {
    private final UserService userService;
    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/{id}")
    public ApiResponse<User> getByID(@PathVariable("id") Long id){
        User user = userService.getById(id);
        if (user == null) {
            return ApiResponse.error(404, "User not found");
        }
        return ApiResponse.success(user);
    }

    @GetMapping("/by-username/{username}")
    public ApiResponse<User> getByUsername(@PathVariable("username") String username){
        User user = userService.getByUsername(username);
        if (user == null) {
            return ApiResponse.error(404, "User not found");
        }
        return ApiResponse.success(user);
    }

    @PostMapping
    public ApiResponse<User> create(@RequestBody User user) {
        int affected = userService.create(user);
        if (affected <= 0) {
            return ApiResponse.error(500, "Create user failed");
        }
        return ApiResponse.success(user);
    }
}
