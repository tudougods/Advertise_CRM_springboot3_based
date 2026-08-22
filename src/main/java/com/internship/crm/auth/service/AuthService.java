package com.internship.crm.auth.service;

import com.internship.crm.auth.dto.response.AuthResponse;
import com.internship.crm.auth.dto.request.LoginRequest;
import com.internship.crm.auth.dto.request.RegisterRequest;
import com.internship.crm.auth.exception.AuthErrorCode;
import com.internship.crm.auth.token.JwtTokenService;
import com.internship.crm.common.exception.BusinessException;
import com.internship.crm.user.dto.response.UserResponse;
import com.internship.crm.user.entity.User;
import com.internship.crm.user.entity.UserStatus;
import com.internship.crm.user.service.UserService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final UserService userService;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenService jwtTokenService;

    public AuthService(
            UserService userService,
            PasswordEncoder passwordEncoder,
            JwtTokenService jwtTokenService) {
        this.userService = userService;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenService = jwtTokenService;
    }

    @Transactional
    public UserResponse register(RegisterRequest request) {
        User user = userService.registerOperator(
                request.username(),
                request.password(),
                request.displayName(),
                request.email());
        return UserResponse.from(user);
    }

    @Transactional
    public AuthResponse login(LoginRequest request) {
        User user = userService.findByUsername(request.username().trim())
                .filter(candidate -> candidate.getStatus() == UserStatus.ACTIVE)
                .filter(candidate -> passwordEncoder.matches(request.password(), candidate.getPasswordHash()))
                .orElseThrow(() -> new BusinessException(AuthErrorCode.INVALID_CREDENTIALS));

        userService.recordSuccessfulLogin(user);
        String token = jwtTokenService.issueToken(user);
        return new AuthResponse(
                token,
                "Bearer",
                jwtTokenService.expirationSeconds(),
                UserResponse.from(user));
    }
}
