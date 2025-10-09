package org.example.application.service;

import org.example.application.Dto.FileDTO;
import org.example.application.Dto.OperationResponse;
import org.example.application.queue.TaskQueue;
import org.example.domain.command.CreateDirectoryCommand;
import org.example.domain.command.DownloadFileCommand;
import org.example.domain.command.DownloadFilesCommand;
import org.example.domain.command.UploadFileCommand;
import org.example.infrastructure.remote.NodeFileService;
import org.example.infrastructure.repository.FileRepository;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class FileService {

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
            CreateDirectoryCommand command = new CreateDirectoryCommand(path, ownerId, fileRepository);

            // Encolar y esperar resultado con timeout
            Future<Boolean> future = taskQueue.enqueue(command);
            Boolean result = future.get(10, TimeUnit.SECONDS);

            if (Boolean.TRUE.equals(result)) {
                return OperationResponse.success("Directorio '" + path + "' creado exitosamente");
            } else {
                return OperationResponse.error("No se pudo crear el directorio '" + path + "'", "CREATE_FAILED");
            }

        } catch (TimeoutException e) {
            return OperationResponse.error("Timeout al crear el directorio", "TIMEOUT");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return OperationResponse.error("Operación interrumpida", "INTERRUPTED");
        } catch (ExecutionException e) {
            return OperationResponse.error("Error al crear directorio: " + e.getCause().getMessage(), "EXECUTION_ERROR");
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

            // Seleccionar nodo
            Map.Entry<Long, NodeFileService> entry = nodeSelector.selectNext();
            Long nodeId = entry.getKey();
            NodeFileService stub = entry.getValue();

            // Crear y ejecutar comando
            UploadFileCommand command = new UploadFileCommand(
                    nodeId, stub, fileName, content, ownerId, directoryId, fileRepository
            );

            Future<Boolean> future = taskQueue.enqueue(command);
            Boolean result = future.get(30, TimeUnit.SECONDS);

            if (Boolean.TRUE.equals(result)) {
                return OperationResponse.success(
                        "Archivo '" + fileName + "' subido exitosamente (" + content.length + " bytes)"
                );
            } else {
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

            // Obtener nodos disponibles
            List<Long> nodeIds = nodeService.getNodeIdsByFile(fileUuid);
            if (nodeIds.isEmpty()) {
                System.err.println("downloadFile: No hay nodos que contengan el archivo " + fileUuid);
                return null;
            }

            // Intentar descargar desde el primer nodo disponible
            Long nodeId = nodeIds.get(0);
            NodeFileService stub = nodeSelector.getStubById(nodeId);
            if (stub == null) {
                System.err.println("downloadFile: No se encontró stub para el nodo " + nodeId);
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
                System.err.println("downloadFiles: Lista de UUIDs vacía");
                return new FileDTO[0];
            }

            if (userId == null) {
                System.err.println("downloadFiles: userId es null");
                return new FileDTO[0];
            }

            // Filtrar archivos con permisos válidos
            List<String> permittedUuids = new ArrayList<>();
            for (String uuid : fileUuids) {
                if (permissionService.canReadFile(userId, uuid)) {
                    permittedUuids.add(uuid);
                } else {
                    System.err.println("downloadFiles: Sin permisos para el archivo " + uuid);
                }
            }

            if (permittedUuids.isEmpty()) {
                System.err.println("downloadFiles: No hay archivos con permisos válidos");
                return new FileDTO[0];
            }

            // Seleccionar nodo
            Map.Entry<Long, NodeFileService> entry = nodeSelector.selectNext();
            NodeFileService stub = entry.getValue();
            if (stub == null) {
                System.err.println("downloadFiles: No se encontró stub para el nodo seleccionado");
                return new FileDTO[0];
            }

            // Crear y ejecutar comando
            DownloadFilesCommand command = new DownloadFilesCommand(stub, fileRepository, permittedUuids);
            Future<Boolean> future = taskQueue.enqueue(command);
            Boolean success = future.get(60, TimeUnit.SECONDS);

            if (!Boolean.TRUE.equals(success)) {
                System.err.println("downloadFiles: Falló la ejecución del comando");
                return new FileDTO[0];
            }

            // Devolver resultados
            List<FileDTO> results = command.getResults();
            System.out.println("downloadFiles: Descargados " + results.size() + " archivos exitosamente");
            return results.toArray(new FileDTO[0]);

        } catch (TimeoutException e) {
            System.err.println("downloadFiles: Timeout al descargar archivos");
            return new FileDTO[0];
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.err.println("downloadFiles: Operación interrumpida");
            return new FileDTO[0];
        } catch (ExecutionException e) {
            System.err.println("downloadFiles: Error en ejecución - " + e.getCause().getMessage());
            return new FileDTO[0];
        } catch (SQLException e) {
            System.err.println("downloadFiles: Error de base de datos - " + e.getMessage());
            return new FileDTO[0];
        } catch (Exception e) {
            System.err.println("downloadFiles: Error inesperado - " + e.getMessage());
            return new FileDTO[0];
        }
    }
}