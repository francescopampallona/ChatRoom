package com.francescopampallona.chatroom.controller;

import com.francescopampallona.chatroom.dto.response.AuthResponse;
import com.francescopampallona.chatroom.dto.request.LoginRequest;
import com.francescopampallona.chatroom.dto.request.RegisterRequest;
import com.francescopampallona.chatroom.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@CrossOrigin(origins = "http://localhost:4200")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService){
        this.authService = authService;
    }

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(
            @Valid @RequestBody RegisterRequest request
    ) {
        return ResponseEntity.ok(authService.register(request));
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(
            @Valid @RequestBody LoginRequest loginRequest
            ){
        return ResponseEntity.ok(authService.login(loginRequest));
    }
}
