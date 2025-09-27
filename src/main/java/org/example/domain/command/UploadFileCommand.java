package org.example.domain.command;

import org.example.domain.port.StorageCommand;
import org.example.infrastructure.remote.NodeFileService;
import org.example.infrastructure.repository.FileRepository;

public class UploadFileCommand implements StorageCommand {

    private final NodeFileService node;
    private final FileRepository fileRepository;

    private final String path;
    private final byte[] content;
    private final String ownerId;

    public UploadFileCommand(NodeFileService node, String path, byte[] content, String ownerId, FileRepository fileRepository) {
        this.node = node;
        this.path = path;
        this.content = content;
        this.ownerId = ownerId;
        this.fileRepository = fileRepository;
    }

    @Override
    public boolean execute() {
        System.out.println("Llega al execute del COMANDO");
        try {
            boolean success = node.uploadFile(path, content);
            if (success) {
                System.out.println("Upload exitoso al nodo " + " para el archivo " + path);
                //fileRepository.saveMetadata(path, ownerId, content.length);
            }
            return success;
        } catch (Exception e) {
            System.err.println("Error remoto en uploadFile: " + e.getMessage());
            return false;
        }
    }
}