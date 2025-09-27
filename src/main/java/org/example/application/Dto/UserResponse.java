package org.example.application.Dto;

import org.example.domain.model.User;

public record UserResponse(boolean success, String message, User user) {}
