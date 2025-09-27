package org.example.application.service;

import org.example.application.queue.TaskQueue;
import org.example.domain.command.UploadFileCommand;
import org.example.infrastructure.remote.NodeFileService;
import org.example.infrastructure.repository.FileRepository;

public class FileService {

    private final TaskQueue taskQueue;
    private final FileRepository fileRepository;
    private final NodeSelector nodeSelector;

    public FileService(TaskQueue taskQueue, FileRepository fileRepository, NodeSelector nodeSelector) {
        this.taskQueue = taskQueue;
        this.fileRepository = fileRepository;
        this.nodeSelector = nodeSelector;
    }

    public void uploadFile(String path, byte[] content, String ownerId) {
        try {
            System.out.println("En fileservice. Seleccionando nodo para upload...");
            NodeFileService node = nodeSelector.selectNext(); // delegación interna

            System.out.println(node + "");
            UploadFileCommand command = new UploadFileCommand(node, path, content, ownerId, fileRepository);
            System.out.println("Comando de upload creado" + command);
            taskQueue.enqueue(command);
        } catch (Exception e) {
            System.err.println("Error al seleccionar nodo: " + e.getMessage());
        }
    }
}
