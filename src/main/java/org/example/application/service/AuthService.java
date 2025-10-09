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

    public AuthService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public Long register(String email, String rawPassword) {

        String passwordHash = hashPassword(rawPassword);
        UserResponse userResponse = userRepository.register(email, passwordHash);

        if (!userResponse.success()) {
            return null;
        }

        User user = userResponse.user();
        return user.getId();

    }

    /*
        login verifica las credenciales del usuario y genera un token si son válidas.
     */
    public Long login(String email, String rawPassword) {
        UserResponse userResponse = userRepository.findByEmail(email);

        if (!userResponse.success()) {
            return null;
        }

        User user = userResponse.user();

        if (!verifyPassword(rawPassword, user.getPasswordHash())) {
            return null;
        }
        return user.getId();
    }

    private String hashPassword(String rawPassword) {
        return Integer.toHexString(rawPassword.hashCode());
    }

    private boolean verifyPassword(String rawPassword, String storedHash) {
        return hashPassword(rawPassword).equals(storedHash);
    }
}
