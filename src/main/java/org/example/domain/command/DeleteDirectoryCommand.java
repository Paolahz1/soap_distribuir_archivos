package org.example.domain.command;

import org.example.application.service.NodeSelector;
import org.example.domain.model.File;
import org.example.domain.port.StorageCommand;
import org.example.infrastructure.remote.NodeFileService;
import org.example.infrastructure.repository.FileRepository;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Comando para eliminar un directorio y todo su contenido de forma recursiva.
 *
 * Flujo:
 * 1. Obtener archivos del directorio
 * 2. Obtener nodos para cada archivo
 * 3. Eliminar de nodos RMI
 * 4. Eliminar de BD
 */
public class DeleteDirectoryCommand implements StorageCommand<Integer> {

    private static final Logger LOGGER = Logger.getLogger(DeleteDirectoryCommand.class.getName());

    private final FileRepository fileRepository;
    private final NodeSelector nodeSelector;
    private final Long directoryId;
    private final Long userId;

    public DeleteDirectoryCommand(Long directoryId, Long userId,
                                  FileRepository fileRepository,
                                  NodeSelector nodeSelector) {
        this.directoryId = directoryId;
        this.userId = userId;
        this.fileRepository = fileRepository;
        this.nodeSelector = nodeSelector;
    }

    @Override
    public Integer execute() {
        int totalFilesDeletedFromNodes = 0;

        try {
            LOGGER.info("DeleteDirectoryCommand INICIANDO: directoryId=" + directoryId +
                    ", userId=" + userId);

            // ========================================
            // PASO 1: OBTENER ARCHIVOS (ANTES de eliminar de BD)
            // ========================================
            List<File> allFilesInDirectory;
            try {
                allFilesInDirectory = fileRepository.getAllFilesInDirectory(directoryId);
                LOGGER.info("DeleteDirectoryCommand: " +
                        (allFilesInDirectory == null ? 0 : allFilesInDirectory.size()) +
                        " archivos encontrados");
            } catch (SQLException e) {
                LOGGER.log(Level.SEVERE, "Error obteniendo archivos del directorio", e);
                return -1;
            }

            // Si no hay archivos, solo eliminar el directorio vacío
            if (allFilesInDirectory == null || allFilesInDirectory.isEmpty()) {
                LOGGER.info("Directorio está vacío, solo eliminar de BD");
                try {
                    fileRepository.deleteDirectoryById(directoryId, userId);
                } catch (SQLException e) {
                    LOGGER.log(Level.WARNING, "Error eliminando directorio vacío", e);
                }
                return 0;
            }

            // ========================================
            // PASO 2: OBTENER NODOS PARA CADA ARCHIVO (ANTES de eliminar de BD)
            // ========================================
            Map<String, List<Long>> fileUuidToNodes = new HashMap<>();

            for (File file : allFilesInDirectory) {
                try {
                    List<Long> nodeIds = fileRepository.getNodesByFile(file.getId());
                    fileUuidToNodes.put(file.getId(), nodeIds);
                    LOGGER.fine("Archivo " + file.getId() + " en " + nodeIds.size() + " nodos");
                } catch (SQLException e) {
                    LOGGER.log(Level.WARNING, "Error obteniendo nodos para archivo", e);
                    fileUuidToNodes.put(file.getId(), new ArrayList<>());
                }
            }

            // ========================================
            // PASO 3: ELIMINAR DE NODOS RMI
            // ========================================
            for (File file : allFilesInDirectory) {
                try {
                    String fileUuid = file.getId();
                    List<Long> nodeIds = fileUuidToNodes.get(fileUuid);

                    LOGGER.info("Procesando archivo: " + file.getName() +
                            " (uuid=" + fileUuid + ")");

                    if (nodeIds == null || nodeIds.isEmpty()) {
                        LOGGER.fine("Archivo no estaba en ningún nodo");
                        totalFilesDeletedFromNodes++;
                        continue;
                    }

                    int successfulDeletions = 0;
                    int failedDeletions = 0;

                    for (Long nodeId : nodeIds) {
                        try {
                            NodeFileService stub = nodeSelector.getStubById(nodeId);

                            if (stub == null) {
                                LOGGER.warning("No stub para Node-" + nodeId);
                                failedDeletions++;
                                continue;
                            }

                            String fileIdWithUser = file.getOwnerId() + "-" + fileUuid;
                            LOGGER.info("→ RMI: deleteFile('" + fileIdWithUser +
                                    "') en Node-" + nodeId);

                            boolean deleted = stub.deleteFile(fileIdWithUser);

                            if (deleted) {
                                successfulDeletions++;
                                nodeSelector.recordFileDeletion(nodeId, file.getSize());
                                LOGGER.info("✓ Eliminado de Node-" + nodeId);
                            } else {
                                failedDeletions++;
                                LOGGER.warning("✗ deleteFile retornó false");
                            }

                        } catch (Exception e) {
                            failedDeletions++;
                            LOGGER.log(Level.SEVERE, "Excepción en Node-" + nodeId, e);
                        }
                    }

                    if (successfulDeletions > 0 || nodeIds.isEmpty()) {
                        totalFilesDeletedFromNodes++;
                    }

                } catch (Exception e) {
                    LOGGER.log(Level.SEVERE, "Error procesando archivo", e);
                }
            }

            // ========================================
            // PASO 4: ELIMINAR DE BD (AHORA SÍ)
            // ========================================
            try {
                fileRepository.deleteDirectoryById(directoryId, userId);
                LOGGER.info("Directorio eliminado de BD");
            } catch (SQLException e) {
                LOGGER.log(Level.WARNING, "Error eliminando de BD", e);
            }

            LOGGER.info("✓ COMPLETADO: " + totalFilesDeletedFromNodes +
                    " archivos limpiados de nodos");

            return totalFilesDeletedFromNodes;

        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error CRÍTICO en DeleteDirectoryCommand", e);
            return -1;
        }
    }
}