package com.example.syncforge.common;

import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

//全局拦截 Controller
//所有 Controller 抛出的异常都会被它捕获
//方法返回值会自动转成 JSON
@RestControllerAdvice
public class GlobalExceptionHandler {
// 统一异常处理
    @ExceptionHandler(IllegalArgumentException.class)
    public ApiResponse<Void> handleIllegalArgument(IllegalArgumentException ex) {
        //// 处理 IllegalArgumentException 异常（通常是参数不合法时抛出）
        return ApiResponse.error(400, ex.getMessage() == null ? "Bad request" : ex.getMessage());
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ApiResponse<Void> handleMissingParameter(MissingServletRequestParameterException ex) {
        //// 处理请求缺少参数的异常（例如 @RequestParam 缺失）
        return ApiResponse.error(400, "Missing required parameter: " + ex.getParameterName());
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ApiResponse<Void> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        // 处理参数类型不匹配异常（例如传了字符串但需要整数）
        return ApiResponse.error(400, "Invalid parameter type: " + ex.getName());
    }

    @ExceptionHandler(Exception.class)
    public ApiResponse<Void> handleException(Exception ex) {
        // 兜底异常处理（捕获所有未被处理的异常）
        return ApiResponse.error(500, "Internal server error");
    }
}
