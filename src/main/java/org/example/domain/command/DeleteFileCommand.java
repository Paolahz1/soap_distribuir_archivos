package org.example.domain.command;

import org.example.application.service.NodeSelector;
import org.example.domain.model.File;
import org.example.domain.port.StorageCommand;
import org.example.infrastructure.remote.NodeFileService;
import org.example.infrastructure.repository.FileRepository;

import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

public class DeleteFileCommand implements StorageCommand<Boolean> {

    private static final Logger LOGGER = Logger.getLogger(DeleteFileCommand.class.getName());

    private final FileRepository fileRepository;
    private final NodeSelector nodeSelector;
    private final String fileUuid;
    private final List<Long> nodeIds; // Nodos donde está replicado

    public DeleteFileCommand(String fileUuid, List<Long> nodeIds,
                             FileRepository fileRepository, NodeSelector nodeSelector) {
        this.fileUuid = fileUuid;
        this.nodeIds = nodeIds;
        this.fileRepository = fileRepository;
        this.nodeSelector = nodeSelector;
    }

    @Override
    public Boolean execute() {
        try {
            // 1. Obtener metadatos del archivo
            File file = fileRepository.findByUuid(fileUuid);
            if (file == null) {
                LOGGER.warning("DeleteFileCommand: no existe archivo con uuid=" + fileUuid);
                return false;
            }

            long fileSize = file.getSize();
            int successfulDeletions = 0;
            int failedDeletions = 0;

            // 2. Intentar eliminar de cada nodo donde está replicado
            for (Long nodeId : nodeIds) {
                try {
                    NodeFileService stub = nodeSelector.getStubById(nodeId);
                    if (stub == null) {
                        LOGGER.warning("DeleteFileCommand: no se encontró stub para Node-" + nodeId);
                        failedDeletions++;
                        continue;
                    }

                    // Construir el fileId con prefijo de usuario (mismo formato usado en upload)
                    String fileIdWithUser = file.getOwnerId() + "-" + fileUuid;
                    boolean deleted = stub.deleteFile(fileIdWithUser);

                    if (deleted) {
                        successfulDeletions++;
                        nodeSelector.recordFileDeletion(nodeId, fileSize);
                        LOGGER.fine("Archivo eliminado de Node-" + nodeId);
                    } else {
                        failedDeletions++;
                        LOGGER.warning("Fallo al eliminar archivo de Node-" + nodeId);
                    }

                } catch (Exception e) {
                    failedDeletions++;
                    LOGGER.log(Level.WARNING, "Error eliminando de Node-" + nodeId, e);
                }
            }

            // 3. Registrar resultado (considerar éxito si se eliminó de al menos un nodo)
            if (successfulDeletions > 0) {
                LOGGER.info("DeleteFileCommand completado: " + successfulDeletions +
                        " eliminaciones exitosas, " + failedDeletions + " fallidas");
                return true;
            } else {
                LOGGER.severe("DeleteFileCommand falló: no se pudo eliminar de ningún nodo");
                return false;
            }

        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error crítico en DeleteFileCommand", e);
            return false;
        }
    }
}