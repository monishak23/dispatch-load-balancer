package com.freightfox.dispatch_load_balancer.dto;

public record ApiResponse(String message, String status) {

    public static ApiResponse success(String message) {
        return new ApiResponse(message, "success");
    }

    public static ApiResponse error(String message) {
        return new ApiResponse(message, "error");
    }
}