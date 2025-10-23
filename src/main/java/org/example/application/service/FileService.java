package org.example.application.service;

import org.example.application.Dto.FileDTO;
import org.example.application.Dto.OperationResponse;
import org.example.application.queue.TaskQueue;
import org.example.domain.command.*;
import org.example.infrastructure.remote.NodeFileService;
import org.example.infrastructure.repository.FileRepository;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.logging.Level;
import java.util.logging.Logger;


public class FileService {

    private static final Logger LOGGER = Logger.getLogger(FileService.class.getName());

    private final TaskQueue taskQueue;
    private final FileRepository fileRepository;
    private final NodeSelector nodeSelector;
    private final PermissionService permissionService;
    private final NodeService nodeService;

    public FileService(TaskQueue taskQueue, FileRepository fileRepository, NodeSelector nodeSelector,
                       PermissionService permissionService, NodeService nodeService) {
        this.taskQueue = taskQueue;
        this.fileRepository = fileRepository;
        this.nodeSelector = nodeSelector;
        this.permissionService = permissionService;
        this.nodeService = nodeService;
    }

    public OperationResponse createDirectory(String path, Long ownerId) {
        try {
            // Validaciones
            if (path == null || path.trim().isEmpty()) {
                return OperationResponse.error("El path no puede estar vacío", "INVALID_PATH");
            }

            if (ownerId == null) {
                return OperationResponse.error("El ownerId no puede ser null", "INVALID_OWNER");
            }

            // Crear el comando
            if(fileRepository.createDirectoryHierarchy(path, ownerId) > 0 ){
                return OperationResponse.success("Directorio '" + path + "' creado exitosamente");
            } else {
                return OperationResponse.error("No se pudo crear el directorio '" + path + "'", "CREATE_FAILED");
            }

       } catch (Exception e) {
            return OperationResponse.error("Error inesperado: " + e.getMessage(), "UNKNOWN_ERROR");
        }
    }

    public OperationResponse uploadFile(Long directoryId, String fileName, byte[] content, Long userId) {
        try {
            // Validaciones
            if (directoryId == null) {
                return OperationResponse.error("El directoryId no puede ser null", "INVALID_DIRECTORY");
            }

            if (fileName == null || fileName.trim().isEmpty()) {
                return OperationResponse.error("El nombre del archivo no puede estar vacío", "INVALID_FILENAME");
            }

            if (content == null || content.length == 0) {
                return OperationResponse.error("El contenido del archivo no puede estar vacío", "INVALID_CONTENT");
            }

            if (userId == null) {
                return OperationResponse.error("El userId no puede ser null", "INVALID_USER");
            }

            // Verificar permisos
            if (!permissionService.canWriteToDirectory(userId, directoryId)) {
                return OperationResponse.error(
                        "El usuario no tiene permisos para escribir en este directorio",
                        "PERMISSION_DENIED"
                );
            }

            // Obtener owner del directorio
            Long ownerId = permissionService.resolveOwnerOfDirectory(directoryId);
            if (ownerId == null) {
                return OperationResponse.error("No se pudo determinar el propietario del directorio", "OWNER_NOT_FOUND");
            }

            long fileSize = content.length;

            // Seleccionar múltiples nodos para redundancia (NUEVO)
            List<Map.Entry<Long, NodeFileService>> selectedNodes = nodeSelector.selectNodesForUpload(fileSize);
            if (selectedNodes.isEmpty()) {
                LOGGER.severe("No hay nodos disponibles para upload");
                return OperationResponse.error("No hay nodos disponibles", "NO_NODES_AVAILABLE");
            }

            // Crear comando con múltiples nodos (NUEVO)
            UploadFileCommand command = new UploadFileCommand(
                    selectedNodes,
                    fileName,
                    content,
                    ownerId,
                    directoryId,
                    fileRepository,
                    nodeSelector  // Pasar nodeSelector para gestionar tareas activas
            );

            Future<Boolean> future = taskQueue.enqueue(command);
            Boolean result = future.get(100, TimeUnit.SECONDS);

            if (Boolean.TRUE.equals(result)) {
                // Actualizar métricas de cada nodo (NUEVO)
                for (Map.Entry<Long, NodeFileService> node : selectedNodes) {
                    nodeSelector.recordFileUpload(node.getKey(), fileSize);
                }

                // Log de éxito
                LOGGER.info("Archivo subido exitosamente: " + fileName + " (" + formatBytes(fileSize) +
                        ") en " + selectedNodes.size() + " nodo(s)");

                // Mostrar estadísticas (NUEVO)
                if (LOGGER.isLoggable(Level.FINE)) {
                    nodeSelector.printNodeStats();
                }

                return OperationResponse.success(
                        "Archivo '" + fileName + "' subido exitosamente con " +
                                2 + " réplicas (" + formatBytes(fileSize) + ")"
                );
            } else {
                LOGGER.warning("Fallo al subir archivo: " + fileName);
                return OperationResponse.error("No se pudo subir el archivo '" + fileName + "'", "UPLOAD_FAILED");
            }

        } catch (TimeoutException e) {
            return OperationResponse.error("Timeout al subir el archivo", "TIMEOUT");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return OperationResponse.error("Operación interrumpida", "INTERRUPTED");
        } catch (ExecutionException e) {
            return OperationResponse.error("Error al subir archivo: " + e.getCause().getMessage(), "EXECUTION_ERROR");
        } catch (SQLException e) {
            return OperationResponse.error("Error de base de datos: " + e.getMessage(), "DATABASE_ERROR");
        } catch (Exception e) {
            return OperationResponse.error("Error inesperado: " + e.getMessage(), "UNKNOWN_ERROR");
        }
    }

    public FileDTO downloadFile(String fileUuid, Long userId) {
        try {
            // Validaciones
            if (fileUuid == null || fileUuid.trim().isEmpty()) {
                System.err.println("downloadFile: fileUuid vacío");
                return null;
            }

            if (userId == null) {
                System.err.println("downloadFile: userId es null");
                return null;
            }

            // Verificar permisos
            if (!permissionService.canReadFile(userId, fileUuid)) {
                System.err.println("downloadFile: El usuario " + userId + " no tiene permisos para leer el archivo " + fileUuid);
                return null;
            }

            // Obtener nodos disponibles (puede haber múltiples por redundancia)
            List<Long> nodeIds = nodeService.getNodeIdsByFile(fileUuid);
            if (nodeIds.isEmpty()) {
                LOGGER.warning("No hay nodos para archivo: " + fileUuid);
                return null;
            }

            LOGGER.fine("Archivo encontrado en " + nodeIds.size() + " nodo(s)");

            // Intentar descargar desde el primer nodo disponible
            Long nodeId = nodeIds.get(0);
            NodeFileService stub = nodeSelector.getStubById(nodeId);
            if (stub == null) {
                LOGGER.warning("No se encontró stub para nodo: " + nodeId);
                return null;
            }

            // Crear y ejecutar comando
            DownloadFileCommand command = new DownloadFileCommand(stub, fileRepository, fileUuid);
            Future<Boolean> future = taskQueue.enqueue(command);
            Boolean success = future.get(30, TimeUnit.SECONDS);

            if (!Boolean.TRUE.equals(success)) {
                System.err.println("downloadFile: Falló la descarga del archivo " + fileUuid);
                return null;
            }

            // Construir DTO
            FileDTO dto = new FileDTO();
            dto.setFileName(command.getMetadata().getName());
            dto.setContent(command.getContent());

            System.out.println("downloadFile: Archivo descargado exitosamente - " + dto.getFileName());
            return dto;

        } catch (TimeoutException e) {
            System.err.println("downloadFile: Timeout al descargar archivo");
            return null;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.err.println("downloadFile: Operación interrumpida");
            return null;
        } catch (ExecutionException e) {
            System.err.println("downloadFile: Error en ejecución - " + e.getCause().getMessage());
            return null;
        } catch (SQLException e) {
            System.err.println("downloadFile: Error de base de datos - " + e.getMessage());
            return null;
        } catch (Exception e) {
            System.err.println("downloadFile: Error inesperado - " + e.getMessage());
            return null;
        }
    }


    public FileDTO[] downloadFiles(String[] fileUuids, Long userId) {
        try {
            // Validaciones
            if (fileUuids == null || fileUuids.length == 0) {
                LOGGER.warning("downloadFiles: lista vacía");
                return new FileDTO[0];
            }

            if (userId == null) {
                LOGGER.warning("downloadFiles: userId es null");
                return new FileDTO[0];
            }

            // Filtrar archivos con permisos válidos
            List<String> permittedUuids = new ArrayList<>();
            for (String uuid : fileUuids) {
                if (permissionService.canReadFile(userId, uuid)) {
                    permittedUuids.add(uuid);
                } else {
                    LOGGER.fine("Sin permisos para: " + uuid);
                }
            }

            if (permittedUuids.isEmpty()) {
                LOGGER.warning("downloadFiles: ningún archivo con permisos");
                return new FileDTO[0];
            }

            // NUEVO: Agrupar archivos por nodo donde están
            Map<Long, List<String>> filesByNode = new HashMap<>();

            for (String uuid : permittedUuids) {
                try {
                    // Obtener nodos donde está este archivo
                    List<Long> nodeIds = nodeService.getNodeIdsByFile(uuid);

                    if (nodeIds.isEmpty()) {
                        LOGGER.warning("Archivo " + uuid + " no está en ningún nodo");
                        continue;
                    }

                    // Usar el primer nodo disponible para este archivo
                    Long nodeId = nodeIds.get(0);
                    filesByNode.computeIfAbsent(nodeId, k -> new ArrayList<>()).add(uuid);

                    LOGGER.fine("Archivo " + uuid + " encontrado en Node-" + nodeId);

                } catch (SQLException e) {
                    LOGGER.log(Level.WARNING, "Error al obtener nodos para archivo: " + uuid, e);
                }
            }

            if (filesByNode.isEmpty()) {
                LOGGER.warning("Ninguno de los archivos está disponible en los nodos");
                return new FileDTO[0];
            }

            // Descargar archivos agrupados por nodo
            List<FileDTO> allResults = new ArrayList<>();

            for (Map.Entry<Long, List<String>> entry : filesByNode.entrySet()) {
                Long nodeId = entry.getKey();
                List<String> uuidsForNode = entry.getValue();

                LOGGER.fine("Descargando " + uuidsForNode.size() + " archivo(s) desde Node-" + nodeId);

                try {
                    // Obtener stub del nodo
                    NodeFileService stub = nodeSelector.getStubById(nodeId);
                    if (stub == null) {
                        LOGGER.warning("No se encontró stub para Node-" + nodeId);
                        // Intentar con siguiente nodo para estos archivos
                        continue;
                    }

                    // Crear y ejecutar comando
                    DownloadFilesCommand command = new DownloadFilesCommand(stub, fileRepository, uuidsForNode);
                    Future<Boolean> future = taskQueue.enqueue(command);
                    Boolean success = future.get(60, TimeUnit.SECONDS);

                    if (Boolean.TRUE.equals(success)) {
                        List<FileDTO> nodeResults = command.getResults();
                        allResults.addAll(nodeResults);
                        LOGGER.info("Descargados " + nodeResults.size() + " archivo(s) desde Node-" + nodeId);
                    } else {
                        LOGGER.warning("Fallo al descargar desde Node-" + nodeId);
                    }

                } catch (TimeoutException e) {
                    LOGGER.log(Level.WARNING, "Timeout descargando desde Node-" + nodeId, e);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    LOGGER.log(Level.WARNING, "Descarga interrumpida en Node-" + nodeId, e);
                } catch (ExecutionException e) {
                    LOGGER.log(Level.SEVERE, "Error en ejecución desde Node-" + nodeId, e);
                }
            }

            LOGGER.info("Total de archivos descargados: " + allResults.size() + " de " + permittedUuids.size());
            return allResults.toArray(new FileDTO[0]);

        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Error BD en descargas múltiples", e);
            return new FileDTO[0];
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error inesperado en descargas múltiples", e);
            return new FileDTO[0];
        }
    }

    /**
     * Mueve un archivo de un directorio a otro.
     */
    public OperationResponse moveFileByPath(String sourcePath, String fileName, String destinationPath, Long userId) {
        try {

            String fileUuid = fileRepository.getFileUuidByPath(sourcePath, fileName);
            if (fileUuid == null) {
                System.err.println("shareFileWithUser: archivo no encontrado - " + fileName);
                return OperationResponse.error("Archivo no encontrado", "FILE_NOT_FOUND");
            }

            // Verificar permisos
            if (!permissionService.isOwnerFile(userId, fileUuid)) {
                return OperationResponse.error(" no tiene permisos para  move  el archivo", "PERMISSION_DENIED");
            }

            // Ejecutar en BD
            boolean success = fileRepository.moveFileByPath(sourcePath, fileName, destinationPath, userId);

            if (success) {
                LOGGER.info("Archivo movido: " + fileName + " de " + sourcePath + " a " + destinationPath);
                return OperationResponse.success("Archivo movido exitosamente");
            } else {
                LOGGER.warning("Fallo al mover archivo: " + fileName);
                return OperationResponse.error("No se pudo mover el archivo", "MOVE_FAILED");
            }

        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Error BD en moveFileByPath", e);
            return OperationResponse.error("Error de base de datos: " + e.getMessage(), "DATABASE_ERROR");
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error inesperado en moveFileByPath", e);
            return OperationResponse.error("Error inesperado: " + e.getMessage(), "UNKNOWN_ERROR");
        }
    }


    /**
     * Renombra un archivo.
     */
    public OperationResponse renameFileByPath(String directoryPath, String oldFileName, String newFileName, Long userId) {
        try {

            String fileUuid = fileRepository.getFileUuidByPath(directoryPath, oldFileName);
            if (fileUuid == null) {
                System.err.println("shareFileWithUser: archivo no encontrado - " + oldFileName);
                return OperationResponse.error("Archivo no encontrado", "FILE_NOT_FOUND");
            }

            // Verificar permisos
            if (!permissionService.isOwnerFile(userId, fileUuid)) {
                return OperationResponse.error(" no tiene permisos para  rename  el archivo", "PERMISSION_DENIED");
            }

            // Ejecutar en BD
            boolean success = fileRepository.renameFileByPath(directoryPath, oldFileName, newFileName, userId);

            if (success) {
                LOGGER.info("Archivo renombrado: " + oldFileName + " -> " + newFileName);
                return OperationResponse.success("Archivo renombrado exitosamente");
            } else {
                LOGGER.warning("Fallo al renombrar archivo: " + oldFileName);
                return OperationResponse.error("No se pudo renombrar el archivo", "RENAME_FAILED");
            }

        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Error BD en renameFileByPath", e);
            return OperationResponse.error("Error de base de datos: " + e.getMessage(), "DATABASE_ERROR");
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error inesperado en renameFileByPath", e);
            return OperationResponse.error("Error inesperado: " + e.getMessage(), "UNKNOWN_ERROR");
        }
    }

    /**
     * Renombra un directorio.
     */
    public OperationResponse renameDirectoryByPath(String directoryPath, String newName, Long userId) {
        try {

            // todo falta validar permisos

            // Ejecutar en BD
            boolean success = fileRepository.renameDirectoryByPath(directoryPath, newName, userId);

            if (success) {
                LOGGER.info("Directorio renombrado: " + directoryPath + " -> " + newName);
                return OperationResponse.success("Directorio renombrado exitosamente");
            } else {
                LOGGER.warning("Fallo al renombrar directorio: " + directoryPath);
                return OperationResponse.error("No se pudo renombrar el directorio", "RENAME_FAILED");
            }

        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Error BD en renameDirectoryByPath", e);
            return OperationResponse.error("Error de base de datos: " + e.getMessage(), "DATABASE_ERROR");
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error inesperado en renameDirectoryByPath", e);
            return OperationResponse.error("Error inesperado: " + e.getMessage(), "UNKNOWN_ERROR");
        }
    }

    /**
     * Mueve un directorio completo a otro directorio.
     */
    public OperationResponse moveDirectoryByPath(String sourcePath, String destinationPath, Long userId) {
        try {


            // todo obtener directoryId desde sourcePath y validar permisos
//            if (!permissionService.isOwnerDirectory(userId, directoryId)) {
//                return OperationResponse.error(
//                        "El usuario no tiene permisos para eliminar este directorio",
//                        "PERMISSION_DENIED"
//                );
//            }

            if (sourcePath == null || sourcePath.trim().isEmpty()) {
                return OperationResponse.error("El path origen no puede estar vacío", "INVALID_SOURCE_PATH");
            }
            if (destinationPath == null || destinationPath.trim().isEmpty()) {
                return OperationResponse.error("El path destino no puede estar vacío", "INVALID_DESTINATION_PATH");
            }
            if (userId == null) {
                return OperationResponse.error("El userId no puede ser null", "INVALID_USER");
            }

            // Ejecutar en BD
            boolean success = fileRepository.moveDirectoryByPath(sourcePath, destinationPath, userId);

            if (success) {
                LOGGER.info("Directorio movido: " + sourcePath + " a " + destinationPath);
                return OperationResponse.success("Directorio movido exitosamente");
            } else {
                LOGGER.warning("Fallo al mover directorio: " + sourcePath);
                return OperationResponse.error("No se pudo mover el directorio", "MOVE_FAILED");
            }

        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Error BD en moveDirectoryByPath", e);
            return OperationResponse.error("Error de base de datos: " + e.getMessage(), "DATABASE_ERROR");
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error inesperado en moveDirectoryByPath", e);
            return OperationResponse.error("Error inesperado: " + e.getMessage(), "UNKNOWN_ERROR");
        }
    }

    /**
     * Elimina un archivo por path.
     */
    // TODO
    public OperationResponse deleteFileByPath(String directoryPath, String fileName, Long userId) {
        try {

            // 1. Obtener el archivo por path
            String fileUuid = fileRepository.getFileUuidByPath(directoryPath, fileName);
            if (fileUuid == null) {
                return OperationResponse.error("Archivo no encontrado", "FILE_NOT_FOUND");
            }

            // 2. Obtener nodos donde está replicado
            List<Long> nodeIds = fileRepository.getNodesByFile(fileUuid);

            // 3. Crear comando para eliminar
            DeleteFileCommand command = new DeleteFileCommand(fileUuid, nodeIds, fileRepository, nodeSelector);
            Future<Boolean> future = taskQueue.enqueue(command);
            Boolean result = future.get(30, TimeUnit.SECONDS);

            // 4. Eliminar metadata de BD
            if (Boolean.TRUE.equals(result)) {
                fileRepository.deleteFileByPath(directoryPath, fileName, userId);
                return OperationResponse.success("Archivo eliminado exitosamente");
            } else {
                return OperationResponse.error("No se pudo eliminar el archivo", "DELETE_FAILED");
            }

        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Error BD en deleteFileByPath", e);
            return OperationResponse.error("Error de base de datos: " + e.getMessage(), "DATABASE_ERROR");
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error inesperado en deleteFileByPath", e);
            return OperationResponse.error("Error inesperado: " + e.getMessage(), "UNKNOWN_ERROR");
        }
    }

    /**
     * Elimina un directorio y su contenido en cascada.
     */

    // TODO
    public OperationResponse deleteDirectoryById(Long directoryId, Long userId) {
        try {

            // Verificar permisos
            if (!permissionService.isOwnerDirectory(userId, directoryId)) {
                return OperationResponse.error(
                        "El usuario no tiene permisos para eliminar este directorio",
                        "PERMISSION_DENIED"
                );
            }

            // 3. Crear comando para eliminar directorio
            DeleteDirectoryCommand command = new DeleteDirectoryCommand(directoryId, userId, fileRepository, nodeSelector);
            // Ejecutar en cola
            Future<Integer> future = taskQueue.enqueue(command);
            Integer filesDeletedFromNodes = future.get(60, TimeUnit.SECONDS);

            if (filesDeletedFromNodes >= 0) {
                LOGGER.info("Directorio eliminado: " + filesDeletedFromNodes + " archivos limpiados");
                return OperationResponse.success("Directorio eliminado exitosamente (" +
                        filesDeletedFromNodes + " archivos)");
            } else {
                return OperationResponse.error("No se pudo eliminar el directorio", "DELETE_FAILED");
            }

        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Error BD en deleteDirectoryById", e);
            return OperationResponse.error("Error de base de datos: " + e.getMessage(), "DATABASE_ERROR");
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error inesperado en deleteDirectoryById", e);
            return OperationResponse.error("Error inesperado: " + e.getMessage(), "UNKNOWN_ERROR");
        }
    }



    /**
     * Comparte un archivo con otro usuario.
     */
    public OperationResponse shareFileWithUser(String directoryPath, String fileName, Long ownerId, String shareWithEmail) {
        try {

            String fileUuid = fileRepository.getFileUuidByPath(directoryPath, fileName);
            if (fileUuid == null) {
                System.err.println("shareFileWithUser: archivo no encontrado - " + fileName);
                return OperationResponse.error("Archivo no encontrado", "FILE_NOT_FOUND");
            }

            // Verificar permisos
            if (!permissionService.isOwnerFile(ownerId, fileUuid)) {
                return OperationResponse.error(" no tiene permisos para compartir  el archivo", "PERMISSION_DENIED");
            }

            // Ejecutar en BD
            boolean success = fileRepository.shareFileWithUser(directoryPath, fileName, ownerId, shareWithEmail);

            if (success) {
                return OperationResponse.success("Archivo compartido exitosamente con " + shareWithEmail);
            } else {
                return OperationResponse.error("No se pudo compartir el archivo", "SHARE_FAILED");
            }

        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Error BD en shareFileWithUser", e);
            return OperationResponse.error("Error de base de datos: " + e.getMessage(), "DATABASE_ERROR");
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error inesperado en shareFileWithUser", e);
            return OperationResponse.error("Error inesperado: " + e.getMessage(), "UNKNOWN_ERROR");
        }
    }

    /**
     * Comparte un directorio con otro usuario.
     */
    public OperationResponse shareDirectoryWithUser(String directoryPath, Long ownerId, String shareWithEmail) {
        try {

            // todo Verificar permisos
//            if (!permissionService.isOwnerDirectory(ownerId, directoryPath)) {
//                return OperationResponse.error(" no tiene permisos para compartir  el archivo", "PERMISSION_DENIED");
//            }

            // Ejecutar en BD
            boolean success = fileRepository.shareDirectoryWithUser(directoryPath, ownerId, shareWithEmail);

            if (success) {
                LOGGER.info("Directorio compartido: " + directoryPath + " con " + shareWithEmail);
                return OperationResponse.success("Directorio compartido exitosamente con " + shareWithEmail);
            } else {
                LOGGER.warning("Fallo al compartir directorio: " + directoryPath);
                return OperationResponse.error("No se pudo compartir el directorio", "SHARE_FAILED");
            }

        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Error BD en shareDirectoryWithUser", e);
            return OperationResponse.error("Error de base de datos: " + e.getMessage(), "DATABASE_ERROR");
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error inesperado en shareDirectoryWithUser", e);
            return OperationResponse.error("Error inesperado: " + e.getMessage(), "UNKNOWN_ERROR");
        }
    }

    // Método auxiliar
    private String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.2f KB", bytes / 1024.0);
        if (bytes < 1024 * 1024 * 1024) return String.format("%.2f MB", bytes / 1024.0 / 1024.0);
        return String.format("%.2f GB", bytes / 1024.0 / 1024.0 / 1024.0);
    }
}