package org.example;

import org.example.Controller.FileSoapController;
import org.example.Controller.UnifiedSoapController;
import org.example.application.queue.TaskQueue;
import org.example.application.service.FileService;
import org.example.application.service.NodeSelector;
import org.example.infrastructure.remote.NodeFileService;
import org.example.infrastructure.remote.NodeRegistryClient;
import org.example.infrastructure.repository.FileRepository;

import java.util.List;
public class FileUploadTest {

    public static void main(String[] args) throws InterruptedException {
        byte[] content = "Hola mundo".getBytes();
        String path = "test.txt";
        String ownerId = "user123";

        FileRepository fileRepository = new FileRepository();
        TaskQueue taskQueue = new TaskQueue(); // procesamiento automático
        List<NodeFileService> stubs = NodeRegistryClient.discoverNodes(List.of("localhost"), 1099);
        NodeSelector selector = new NodeSelector(stubs);
        FileService fileService = new FileService(taskQueue, fileRepository, selector);
        FileSoapController fileController = new FileSoapController(fileService);
        UnifiedSoapController unified = new UnifiedSoapController(null, fileController);

        System.out.println("LLega a UnifiedSoapController");
        System.out.println("fue delegado a  a FileSoapController");

        String result = unified.uploadFile(path, content, ownerId);
        System.out.println("Resultado: " + result);

        // 🔴 Mantener el proceso vivo para que el hilo daemon ejecute
        Thread.sleep(2000); // 2 segundos
    }
}
