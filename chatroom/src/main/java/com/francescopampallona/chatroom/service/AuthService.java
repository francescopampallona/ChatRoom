package com.francescopampallona.chatroom.service;

import com.francescopampallona.chatroom.dto.response.AuthResponse;
import com.francescopampallona.chatroom.dto.request.LoginRequest;
import com.francescopampallona.chatroom.dto.request.RegisterRequest;
import com.francescopampallona.chatroom.dto.response.UserDto;
import com.francescopampallona.chatroom.model.User;
import com.francescopampallona.chatroom.repository.UserRepository;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;


    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder, JwtService jwtService){
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    public AuthResponse register(RegisterRequest request) {
        String username = request.getUsername().trim().toLowerCase();
        String email = request.getEmail().trim().toLowerCase();
        String password = request.getPassword();

        if (userRepository.existsByUsername(username)) {
            throw new RuntimeException("Username già in uso");
        }

        if (userRepository.existsByEmail(email)) {
            throw new RuntimeException("Email già in uso");
        }

        User user = User.builder()
                .username(username)
                .email(email)
                .passwordHash(passwordEncoder.encode(password))
                .build();

        User savedUser = userRepository.save(user);

        String token = jwtService.generateToken(savedUser);

        UserDto userDto = UserDto.builder()
                .id(savedUser.getId())
                .username(savedUser.getUsername())
                .email(savedUser.getEmail())
                .build();

        return new AuthResponse(token, userDto);
    }

    public AuthResponse login(LoginRequest request) {

        String username = request.getUsername().trim().toLowerCase();
        String password = request.getPassword();

        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new BadCredentialsException("Credenziali non valide"));

        boolean passwordMatches = passwordEncoder.matches(
                password,
                user.getPasswordHash()
        );

        if (!passwordMatches) {
            throw new BadCredentialsException("Credenziali non valide");
        }

        String token = jwtService.generateToken(user);

        UserDto userDto = new UserDto(
                user.getId(),
                user.getUsername(),
                user.getEmail()
        );

        return new AuthResponse(token, userDto);
    }
}
