package org.example.Controller;

import jakarta.jws.WebMethod;
import jakarta.jws.WebService;

import org.example.application.Dto.AuthResponse;
import org.example.application.service.AuthService;

@WebService(serviceName = "UserService")
public class UserSoapController {

    private final AuthService authService;

    public UserSoapController(AuthService authService) {
        this.authService = authService;
    }

    @WebMethod
    public Long register(String email, String password) {
        return authService.register(email, password);
    }

    @WebMethod
    public Long login(String email, String password) {
        return authService.login(email, password);
    }
}
