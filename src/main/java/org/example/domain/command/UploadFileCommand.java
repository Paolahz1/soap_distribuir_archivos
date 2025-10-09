package org.example.domain.command;

import org.example.domain.model.File;
import org.example.domain.port.StorageCommand;
import org.example.infrastructure.remote.NodeFileService;
import org.example.infrastructure.repository.FileRepository;

import java.util.UUID;

public class UploadFileCommand implements StorageCommand {

    private  final Long nodeId;
    private final NodeFileService node;
    private final FileRepository fileRepository;

    private final String name;
    private final byte[] content;
    private final Long ownerId;
    private final Long directoryId;

    public UploadFileCommand(Long nodeId, NodeFileService node, String name, byte[] content,
                             Long ownerId, Long directoryId, FileRepository fileRepository) {
        this.nodeId = nodeId;
        this.node = node;
        this.name = name;
        this.content = content;
        this.ownerId = ownerId;
        this.directoryId = directoryId;
        this.fileRepository = fileRepository;
    }

    /*
    Ejecuta la subida del archivo al nodo y registra su metadato en la base de datos.
     */
    @Override
    public Boolean execute() {
        try {

            String uuid = UUID.randomUUID().toString();
            boolean success = node.uploadFile(uuid, content);
            if (success) {

                File file = new File(uuid, name, content.length, ownerId, directoryId);
                System.out.println("uuid:" + file.getId() + ", name:" + name + ", size:" + content.length + ", ownerId:" + ownerId + ", directoryId:" + directoryId);
                fileRepository.uploadFile(file);
                fileRepository.registerFileNode(uuid, nodeId);
                System.out.println("Archivo registrado en DB con uuid=" + uuid);
            }
            return success;
        } catch (Exception e) {
            System.err.println("Error en UploadFileCommand: " + e.getMessage());
            return false;
        }
    }
}