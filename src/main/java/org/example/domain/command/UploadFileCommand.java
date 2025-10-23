package org.example.domain.command;

import org.example.application.service.NodeSelector;
import org.example.domain.model.File;
import org.example.domain.port.StorageCommand;
import org.example.infrastructure.remote.NodeFileService;
import org.example.infrastructure.repository.FileRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Comando para subir archivo con redundancia a múltiples nodos.
 * Gestiona tareas activas y realiza rollback en caso de fallo.
 */
public class UploadFileCommand implements StorageCommand<Boolean> {

    private static final Logger LOGGER = Logger.getLogger(UploadFileCommand.class.getName());

    private final List<Map.Entry<Long, NodeFileService>> nodes;
    private final FileRepository fileRepository;
    private final NodeSelector nodeSelector;
    private final String name;
    private final byte[] content;
    private final Long ownerId;
    private final Long directoryId;

    /**
     * Constructor con múltiples nodos para replicación.
     */
    public UploadFileCommand(
            List<Map.Entry<Long, NodeFileService>> nodes,
            String name,
            byte[] content,
            Long ownerId,
            Long directoryId,
            FileRepository fileRepository,
            NodeSelector nodeSelector) {
        this.nodes = nodes;
        this.name = name;
        this.content = content;
        this.ownerId = ownerId;
        this.directoryId = directoryId;
        this.fileRepository = fileRepository;
        this.nodeSelector = nodeSelector;
    }

    @Override
    public Boolean execute() {
        String uuid = null;
        List<Long> successfulNodes = new ArrayList<>();

        try {

            uuid = UUID.randomUUID().toString();

            File file = new File(uuid, name, content.length, ownerId, directoryId);
            if(!fileRepository.uploadFile(file)){
                LOGGER.severe("FALLO: No se pudo registrar la metadata en la BD");
                return false;
            }

            // CAMBIO CRÍTICO: Prefijo el userId al UUID
            String fileIdWithUser = ownerId + "-" + uuid;


            // 1. Subir a todos los nodos seleccionados
            for (int i = 0; i < nodes.size(); i++) {
                Map.Entry<Long, NodeFileService> entry = nodes.get(i);
                Long nodeId = entry.getKey();
                NodeFileService stub = entry.getValue();
                String nodeType = (i == 0) ? "PRIMARIO" : "REPLICA-" + i;

                try {

                    // CAMBIO: Usar fileIdWithUser en lugar de solo uuid
                    boolean success = stub.uploadFile(fileIdWithUser, content);

                    if (success) {
                        successfulNodes.add(nodeId);
                    } else {
                        LOGGER.warning("✗ Node-" + nodeId + " (" + nodeType + "): FALLÓ");
                    }

                } catch (Exception e) {
                    LOGGER.log(Level.WARNING, "✗ Node-" + nodeId + " (" + nodeType + "): EXCEPCIÓN", e);
                }
            }

            // 2. Verificar éxito mínimo
            if (successfulNodes.isEmpty()) {
                LOGGER.severe("FALLO TOTAL: No se pudo subir a ningún nodo");
                return false;
            }


            // 4. Registrar cada nodo exitoso en File_Node
            // IMPORTANTE: En File_Node almacenamos el UUID SIN prefijo
            for (Long nodeId : successfulNodes) {
                try {
                    fileRepository.registerFileNode(uuid, nodeId);
                } catch (Exception e) {
                    LOGGER.log(Level.WARNING, "Error registrando File_Node para Node-" + nodeId, e);
                }
            }

            return true;

        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error crítico en upload de " + name, e);
            return false;

        } finally {
            // Decrementar tareas activas
            for (Map.Entry<Long, NodeFileService> entry : nodes) {
                try {
                    nodeSelector.completeTask(entry.getKey());
                } catch (Exception e) {
                    LOGGER.log(Level.WARNING, "Error al decrementar tareas activas", e);
                }
            }
        }
    }


}