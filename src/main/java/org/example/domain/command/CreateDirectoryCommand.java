package org.example.domain.command;

import org.example.domain.port.StorageCommand;
import org.example.infrastructure.remote.NodeFileService;
import org.example.infrastructure.repository.FileRepository;

public class CreateDirectoryCommand implements StorageCommand {

    private final FileRepository fileRepository;

    private final String path;
    private final Long ownerId;

    public CreateDirectoryCommand( String path, Long ownerId, FileRepository fileRepository) {
        this.path = path;
        this.ownerId = ownerId;
        this.fileRepository = fileRepository;
    }

    @Override
    public Boolean execute() {
        System.out.println("Llega al execute del CreateDirectoryCommand");
        try {
            fileRepository.createDirectoryHierarchy(path, ownerId);
            System.out.println("Directorio registrado en DB: " + path);
            return true;
        } catch (Exception e) {
            System.err.println("Error al registrar directorio en DB: " + e.getMessage());
            return false;
        }
    }
}
