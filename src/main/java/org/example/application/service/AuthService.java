package org.example.application.service;

import org.example.application.Dto.AuthResponse;
import org.example.application.Dto.UserResponse;
import org.example.domain.model.User;
import org.example.infrastructure.repository.UserRepository;
import org.example.infrastructure.web.TokenManager;

public class AuthService {

    /*
     Utilizamos final porque estas dependencias no deben cambiar
     una vez que el servicio ha sido instanciado.
     */
    private final UserRepository userRepository;
    private final TokenManager tokenManager;

    public AuthService(UserRepository userRepository, TokenManager cookieManager) {
        this.userRepository = userRepository;
        this.tokenManager = cookieManager;
    }

    public AuthResponse register(String email, String rawPassword) {

        String passwordHash = hashPassword(rawPassword);
        UserResponse userResponse = userRepository.register(email, passwordHash);

        if (!userResponse.success()) {
            return new AuthResponse(false, userResponse.message(), null);
        }

        User user = userResponse.user();
        String token = tokenManager.generateToken(user.getId(), user.getEmail());
        return new AuthResponse(true, "Registro exitoso", token);

    }

    /*
        login verifica las credenciales del usuario y genera un token si son válidas.
     */
    public AuthResponse login(String email, String rawPassword) {
        UserResponse userResponse = userRepository.findByEmail(email);

        if (!userResponse.success()) {
            return new AuthResponse(false, "Usuario no encontrado", null);
        }

        User user = userResponse.user();

        if (!verifyPassword(rawPassword, user.getPasswordHash())) {
            return new AuthResponse(false, "Credenciales inválidas",  null);
        }

        String token = tokenManager.generateToken(user.getId(), user.getEmail());

        return new AuthResponse(true, "Login exitoso", token);
    }

    private String hashPassword(String rawPassword) {
        return Integer.toHexString(rawPassword.hashCode());
    }

    private boolean verifyPassword(String rawPassword, String storedHash) {
        return hashPassword(rawPassword).equals(storedHash);
    }
}
