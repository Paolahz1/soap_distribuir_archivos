package org.example;

import jakarta.xml.ws.Endpoint;
import org.example.Controller.FileSoapController;
import org.example.Controller.UnifiedSoapController;
import org.example.Controller.UserSoapController;
import org.example.application.queue.TaskQueue;
import org.example.application.service.AuthService;
import org.example.application.service.FileService;
import org.example.application.service.NodeSelector;
import org.example.infrastructure.remote.NodeFileService;
import org.example.infrastructure.remote.NodeRegistryClient;
import org.example.infrastructure.repository.FileRepository;
import org.example.infrastructure.repository.UserRepository;
import org.example.infrastructure.web.TokenManager;

import java.util.List;

public class SoapBootstrap {

    public static void main(String[] args) {
        // 1. Descubrir nodos RMI distribuidos
        List<String> nodeHosts = List.of("localhost");
        int registryPort = 1099;
        List<NodeFileService> stubs = NodeRegistryClient.discoverNodes(nodeHosts, registryPort);
        NodeSelector nodeSelector = new NodeSelector(stubs);

        // 2. Servicios de autenticación
        UserRepository userRepository = new UserRepository();
        TokenManager tokenManager = new TokenManager();
        AuthService authService = new AuthService(userRepository, tokenManager);
        UserSoapController userController = new UserSoapController(authService);

        // 3. Servicios de archivos
        FileRepository fileRepository = new FileRepository();
        TaskQueue taskQueue = new TaskQueue();
        FileService fileService = new FileService(taskQueue, fileRepository, nodeSelector);
        FileSoapController fileController = new FileSoapController(fileService);

        // 4. Controlador unificado
        UnifiedSoapController unifiedController = new UnifiedSoapController(userController, fileController);

        // 5. Publicar servicio SOAP
        Endpoint.publish("http://localhost:8080/ws", unifiedController);
        System.out.println("Servicio SOAP publicado en: http://localhost:8080/ws/unified");
    }
}
