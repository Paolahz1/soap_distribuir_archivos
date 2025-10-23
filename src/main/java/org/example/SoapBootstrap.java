package org.example;

import jakarta.xml.ws.Endpoint;
import org.example.Controller.FileSoapController;
import org.example.Controller.UserSoapController;
import org.example.application.queue.TaskQueue;
import org.example.application.service.*;
import org.example.infrastructure.remote.NodeFileService;
import org.example.infrastructure.repository.DbConnection;
import org.example.infrastructure.repository.FileRepository;
import org.example.infrastructure.repository.UserRepository;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;

public class SoapBootstrap {

    public static void main(String[] args) throws SQLException {

        // ========================================
        // CONFIGURAR TIMEOUTS DE RMI (CRÍTICO)
        // ========================================
        // Aumentar timeout de conexión a 30 segundos
        System.setProperty("sun.rmi.transport.tcp.responseTimeout", "30000");

        // Aumentar timeout de handshake a 30 segundos
        System.setProperty("sun.rmi.transport.tcp.handshakeTimeout", "30000");

        // Aumentar timeout de conectividad a 60 segundos
        System.setProperty("sun.rmi.transport.tcp.connectTimeout", "60000");

        // Socket timeout para lecturas RMI
        System.setProperty("sun.rmi.transport.tcp.soTimeout", "60000");

        // Pool de conexiones
        System.setProperty("sun.rmi.transport.tcp.maxConnectionThreads", "100");

        System.out.println("═".repeat(60));
        System.out.println("RMI Timeouts Configurados:");
        System.out.println("  - responseTimeout: 30000ms (30s)");
        System.out.println("  - handshakeTimeout: 30000ms (30s)");
        System.out.println("  - connectTimeout: 60000ms (60s)");
        System.out.println("  - soTimeout: 60000ms (60s)");
        System.out.println("═".repeat(60));

        // 1. Conexión a BD
        Connection connection = DbConnection.getConnection();
        FileRepository fileRepository = new FileRepository(connection);

        // 2. Descubrir nodos RMI
        List<String> hosts = List.of("localhost");
        List<Integer> ports = List.of(1200);

        System.out.println("\nDescubriendo nodos RMI...");
        NodeService nodeService = new NodeService(fileRepository);
        Map<Long, NodeFileService> nodeMap = nodeService.registerDiscoveredNodes(hosts, ports);
        NodeSelector nodeSelector = new NodeSelector(nodeMap, fileRepository);

        // 3. Servicios de autenticación
        UserRepository userRepository = new UserRepository();
        AuthService authService = new AuthService(userRepository);
        UserSoapController userController = new UserSoapController(authService);

        // 4. Servicios de archivos
        TaskQueue taskQueue = new TaskQueue();
        PermissionService permissionService = new PermissionService(fileRepository);
        FileService fileService = new FileService(taskQueue, fileRepository, nodeSelector, permissionService, nodeService);
        FileSoapController fileController = new FileSoapController(fileService);

        // 5. Publicar endpoints SOAP
        System.out.println("\nPublicando SOAP endpoints...");
        Endpoint.publish("http://localhost:8080/ws/auth", userController);
        System.out.println("✓ AuthEndpoint publicado en: http://localhost:8080/ws/auth");

        Endpoint.publish("http://localhost:8080/ws/files", fileController);
        System.out.println("✓ FileEndpoint publicado en: http://localhost:8080/ws/files");

        System.out.println("\n" + "═".repeat(60));
        System.out.println("SOAP SERVER LISTO");
        System.out.println("═".repeat(60));
    }
}