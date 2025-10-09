package org.example;
import jakarta.xml.ws.handler.Handler;
import jakarta.xml.ws.Binding;
import jakarta.xml.ws.Endpoint;
import org.example.Controller.FileSoapController;
import org.example.Controller.UserSoapController;
import org.example.application.queue.TaskQueue;
import org.example.application.service.*;
import org.example.infrastructure.remote.NodeFileService;
import org.example.infrastructure.repository.DbConnection;
import org.example.infrastructure.repository.FileRepository;
import org.example.infrastructure.repository.UserRepository;
import org.example.infrastructure.web.AuthTokenHandler;
import org.example.infrastructure.web.TokenManager;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class SoapBootstrap {

    public static void main(String[] args) throws SQLException {

        // 1. Conexión a BD
        Connection connection = DbConnection.getConnection();

        FileRepository fileRepository = new FileRepository(connection);

        // Lista de nodos RMI
        List<String> hosts = List.of("localhost");
        List<Integer> ports = List.of(1099);
        NodeService nodeService = new NodeService(fileRepository);
        Map<Long, NodeFileService> nodeMap = nodeService.registerDiscoveredNodes(hosts, ports);
        NodeSelector nodeSelector = new NodeSelector(nodeMap);

        // 2. Servicios de autenticación
        UserRepository userRepository = new UserRepository();
        AuthService authService = new AuthService(userRepository);
        UserSoapController userController = new UserSoapController(authService);

        // 3. Servicios de archivos
        TaskQueue taskQueue = new TaskQueue();
        PermissionService permissionService = new PermissionService(fileRepository);
        FileService fileService = new FileService(taskQueue, fileRepository, nodeSelector, permissionService, nodeService);
        FileSoapController fileController = new FileSoapController(fileService);

        // 4. Publicar endpoints SOAP
        Endpoint.publish("http://localhost:8080/ws/auth", userController);
        System.out.println("AuthEndpoint publicado en: /ws/auth");

//        Endpoint fileEndpoint = Endpoint.create(fileController);
//        // Aplica el handler ANTES de publicar
//        Binding fileBinding = fileEndpoint.getBinding();
//        List<Handler> handlerChain = new ArrayList<>();
//        fileBinding.setHandlerChain(handlerChain);

        Endpoint.publish("http://localhost:8080/ws/files", fileController);
        System.out.println("FileEndpoint publicado en: /ws/files");


    }
}
