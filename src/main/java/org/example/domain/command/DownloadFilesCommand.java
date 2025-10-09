package org.example.domain.command;

import org.example.application.Dto.FileDTO;
import org.example.domain.model.File;
import org.example.domain.port.StorageCommand;
import org.example.infrastructure.remote.NodeFileService;
import org.example.infrastructure.repository.FileRepository;

import java.util.ArrayList;
import java.util.List;

public class DownloadFilesCommand implements StorageCommand {

    private final NodeFileService node;
    private final FileRepository fileRepository;
    private final List<String> fileUuids;

    private final List<FileDTO> results = new ArrayList<>();

    public DownloadFilesCommand(NodeFileService node, FileRepository fileRepository, List<String> fileUuids) {
        this.node = node;
        this.fileRepository = fileRepository;
        this.fileUuids = fileUuids;
    }

    @Override
    public Boolean execute() {
        try {
            // 1. Descargar todos los contenidos en bloque desde el nodo
            List<byte[]> contents = node.downloadFiles(fileUuids);

            // 2. Reconstruir los DTOs
            for (int i = 0; i < fileUuids.size(); i++) {
                String uuid = fileUuids.get(i);
                byte[] content = (i < contents.size()) ? contents.get(i) : null;

                File metadata = fileRepository.findByUuid(uuid);
                String name = (metadata != null) ? metadata.getName() : "ERROR";

                if (content == null) {
                    System.err.println("DownloadFilesCommand: no se pudo descargar contenido para uuid=" + uuid);
                    results.add(new FileDTO("ERROR", new byte[0]));
                } else {
                    results.add(new FileDTO(name, content));
                }
            }

            return true;

        } catch (Exception e) {
            System.err.println("Error en DownloadFilesCommand: " + e.getMessage());
            return false;
        }
    }

    public List<FileDTO> getResults() {
        return results;
    }
}
