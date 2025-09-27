package org.example.Controller;

import jakarta.jws.WebMethod;
import jakarta.jws.WebService;
import org.example.application.Dto.AuthResponse;

@WebService(serviceName = "UnifiedService")
public class UnifiedSoapController {

    private final UserSoapController userController;
    private final FileSoapController fileController;

    public UnifiedSoapController(UserSoapController userController, FileSoapController fileController) {
        this.userController = userController;
        this.fileController = fileController;
    }
    @WebMethod
    public AuthResponse registerUser(String email, String password) {
        return userController.register(email, password);
    }

    @WebMethod
    public AuthResponse loginUser(String email, String password) {
        return userController.login(email, password);
    }

    /*
    File operations
    Aquí se colocan los métodos relacionados con la gestión de archivos
     */
    @WebMethod
    public String uploadFile(String path, byte[] content, String ownerId){
        System.out.println("LLega a UnifiedSoapController");
            return  fileController.uploadFile(path, content, ownerId);
    }
}
