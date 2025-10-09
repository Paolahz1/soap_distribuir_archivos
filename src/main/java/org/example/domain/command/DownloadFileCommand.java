package org.example.domain.command;

import org.example.domain.model.File;
import org.example.domain.port.StorageCommand;
import org.example.infrastructure.remote.NodeFileService;
import org.example.infrastructure.repository.FileRepository;

public class DownloadFileCommand implements StorageCommand {

    private final NodeFileService node;
    private final FileRepository fileRepository;
    private final String fileUuid;

    // Resultado de la descarga
    private byte[] content;
    private File metadata;

    public DownloadFileCommand( NodeFileService node, FileRepository fileRepository, String fileUuid) {
        this.node = node;
        this.fileRepository = fileRepository;
        this.fileUuid = fileUuid;
    }

    @Override
    public Boolean execute() {
        try {
            // 1. Obtener metadatos desde la DB
            metadata = fileRepository.findByUuid(fileUuid);
            if (metadata == null) {
                System.err.println("DownloadFileCommand: no existe metadata para uuid=" + fileUuid);
                return false;
            }

            // 2. Descargar bytes desde el nodo
            content = node.downloadFile(fileUuid);
            if (content == null) {
                System.err.println("DownloadFileCommand: no se pudo descargar contenido para uuid=" + fileUuid);
                return false;
            }

            System.out.println("Archivo descargado: uuid=" + fileUuid + ", name=" + metadata.getName() + ", size=" + content.length);
            return true;

        } catch (Exception e) {
            System.err.println("Error en DownloadFileCommand: " + e.getMessage());
            return false;
        }
    }

    // Getters para recuperar el resultado
    public byte[] getContent() {
        return content;
    }

    public File getMetadata() {
        return metadata;
    }
}
