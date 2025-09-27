package org.example.Controller;

import jakarta.jws.WebMethod;
import jakarta.jws.WebService;
import org.example.application.queue.TaskQueue;
import org.example.application.service.FileService;
import org.example.application.service.NodeSelector;
import org.example.infrastructure.repository.FileRepository;

@WebService(serviceName = "FileService")
public class FileSoapController {

    private final FileService fileService;

    public FileSoapController(FileService fileService) {
        this.fileService = fileService;
    }

    @WebMethod
    public String uploadFile(String path, byte[] content, String ownerId)
    {
        System.out.println("fue delegado a  a FileSoapController");
        try {
            fileService.uploadFile(path, content, ownerId);
            return "Upload encolado correctamente";
        } catch (Exception e) {
            return "Error al encolar upload: " + e.getMessage();
        }
    }
}
