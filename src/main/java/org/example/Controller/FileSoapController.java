package org.example.Controller;


import jakarta.jws.WebMethod;
import jakarta.jws.WebParam;
import jakarta.jws.WebService;
import org.example.application.Dto.FileDTO;
import org.example.application.Dto.OperationResponse;
import org.example.application.service.FileService;

@WebService(serviceName = "FileService")
public class FileSoapController {


    private final FileService fileService;

    public FileSoapController(FileService fileService) {
        this.fileService = fileService;
    }

    @WebMethod
    public OperationResponse createDirectory( Long userId, String path ) {
        try {

            System.out.println("createDirectory called with path: " + path + " and userId: " + userId);
            if (userId == null) {
                System.out.println("createDirectory: Usuario no autenticado");
                return OperationResponse.error("Usuario no autenticado", "UNAUTHORIZED");
            }

            // Delegar al servicio
            return fileService.createDirectory(path, userId);

        } catch (Exception e) {
            e.printStackTrace();
            return OperationResponse.error("Error al crear directorio: " + e.getMessage(), "INTERNAL_ERROR");
        }
    }

    @WebMethod
    public OperationResponse uploadFile(
            @WebParam(name = "userId") Long userId,
            @WebParam(name = "directoryId") Long directoryId,
            @WebParam(name = "fileName") String fileName,
            @WebParam(name = "content") byte[] content) {
        try {

            if (userId == null) {
                return OperationResponse.error("Usuario no autenticado", "UNAUTHORIZED");
            }

            // Delegar al servicio
            return fileService.uploadFile(directoryId, fileName, content, userId);

        } catch (Exception e) {
            e.printStackTrace();
            return OperationResponse.error("Error al subir archivo: " + e.getMessage(), "INTERNAL_ERROR");
        }
    }

    @WebMethod
    public OperationResponse uploadFiles(
            @WebParam(name = "userId") Long userId,
            @WebParam(name = "directoryId") Long directoryId,
            @WebParam(name = "files") FileDTO[] files) {
        try {

            System.out.println("uploadFiles called with directoryId: " + directoryId + " and userId: " + userId);
            if (userId == null) {
                return OperationResponse.error("Usuario no autenticado", "UNAUTHORIZED");
            }

            // Validaciones
            if (directoryId == null) {
                return OperationResponse.error("El directoryId no puede ser null", "INVALID_DIRECTORY");
            }

            if (files == null || files.length == 0) {
                return OperationResponse.error("No se recibieron archivos", "NO_FILES");
            }

            // Procesar cada archivo
            int successCount = 0;
            int failCount = 0;
            StringBuilder errors = new StringBuilder();

            for (FileDTO file : files) {
                OperationResponse result = fileService.uploadFile(
                        directoryId,
                        file.getFileName(),
                        file.getContent(),
                        userId
                );

                if (result.isSuccess()) {
                    successCount++;
                } else {
                    failCount++;
                    errors.append(file.getFileName()).append(": ").append(result.getMessage()).append("; ");
                }
            }

            // Construir respuesta final
            if (failCount == 0) {
                return OperationResponse.success(
                        "Todos los archivos subidos exitosamente (" + successCount + " archivos)"
                );
            } else if (successCount == 0) {
                return OperationResponse.error(
                        "Ningún archivo pudo ser subido. Errores: " + errors.toString(),
                        "ALL_FAILED"
                );
            } else {
                return OperationResponse.success(
                        successCount + " archivos subidos, " + failCount + " fallaron. Errores: " + errors.toString()
                );
            }

        } catch (Exception e) {
            e.printStackTrace();
            return OperationResponse.error("Error al subir archivos: " + e.getMessage(), "INTERNAL_ERROR");
        }
    }

    @WebMethod
    public FileDTO downloadFile( Long userId,  String fileUuid) {
        try {

            if (userId == null) {
                System.err.println("downloadFile: Usuario no autenticado");
                return createErrorFileDTO("Usuario no autenticado");
            }

            // Validar parámetros
            if (fileUuid == null || fileUuid.trim().isEmpty()) {
                System.err.println("downloadFile: fileUuid vacío");
                return createErrorFileDTO("fileUuid no puede estar vacío");
            }

            // Delegar al servicio
            FileDTO result = fileService.downloadFile(fileUuid, userId);

            if (result == null) {
                return createErrorFileDTO("No se pudo descargar el archivo");
            }

            return result;

        } catch (Exception e) {
            e.printStackTrace();
            return createErrorFileDTO("Error al descargar archivo: " + e.getMessage());
        }
    }

    @WebMethod
    public FileDTO[] downloadFiles( Long userId,  String[] fileUuids) {
        try {
            if (userId == null) {
                System.err.println("downloadFiles: Usuario no autenticado");
                return new FileDTO[]{createErrorFileDTO("Usuario no autenticado")};
            }

            // Validar parámetros
            if (fileUuids == null || fileUuids.length == 0) {
                System.err.println("downloadFiles: Lista de UUIDs vacía");
                return new FileDTO[]{createErrorFileDTO("Lista de UUIDs vacía")};
            }

            // Delegar al servicio
            FileDTO[] results = fileService.downloadFiles(fileUuids, userId);

            if (results == null || results.length == 0) {
                return new FileDTO[]{createErrorFileDTO("No se pudieron descargar los archivos")};
            }

            return results;

        } catch (Exception e) {
            e.printStackTrace();
            return new FileDTO[]{createErrorFileDTO("Error al descargar archivos: " + e.getMessage())};
        }

    }


    /**
     * Mueve un archivo de un directorio a otro.
     */

    @WebMethod
    public OperationResponse moveFileByPath(String sourcePath, String fileName, String destinationPath, Long userId) {
        try {
            // Validaciones
            if (sourcePath == null || sourcePath.trim().isEmpty()) {
                return OperationResponse.error("El path origen no puede estar vacío", "INVALID_SOURCE_PATH");
            }
            if (fileName == null || fileName.trim().isEmpty()) {
                return OperationResponse.error("El nombre del archivo no puede estar vacío", "INVALID_FILENAME");
            }
            if (destinationPath == null || destinationPath.trim().isEmpty()) {
                return OperationResponse.error("El path destino no puede estar vacío", "INVALID_DESTINATION_PATH");
            }
            if (userId == null) {
                return OperationResponse.error("El userId no puede ser null", "INVALID_USER");
            }

            return fileService.moveFileByPath(sourcePath, fileName, destinationPath, userId);

        } catch (Exception e) {
            e.printStackTrace();
            return OperationResponse.error("Error al mover archivos: " + e.getMessage(), "INTERNAL_ERROR");

        }
    }


    @WebMethod
    public OperationResponse moveDirectoryByPath(String sourcePath, String destinationPath, Long userId) {
        try {
            // Validaciones
            if (sourcePath == null || sourcePath.trim().isEmpty()) {
                return OperationResponse.error("El path origen no puede estar vacío", "INVALID_SOURCE_PATH");
            }
            if (destinationPath == null || destinationPath.trim().isEmpty()) {
                return OperationResponse.error("El path destino no puede estar vacío", "INVALID_DESTINATION_PATH");
            }
            if (userId == null) {
                return OperationResponse.error("El userId no puede ser null", "INVALID_USER");
            }

            return fileService.moveDirectoryByPath( sourcePath,  destinationPath,  userId);

        } catch (Exception e) {
            e.printStackTrace();
            return OperationResponse.error("Error al mover directorio: " + e.getMessage(), "INTERNAL_ERROR");

        }
    }

    @WebMethod
    public OperationResponse renameFileByPath(String directoryPath, String oldFileName, String newFileName, Long userId) {
        try {
            // Validaciones
            if (directoryPath == null || directoryPath.trim().isEmpty()) {
                return OperationResponse.error("El path  no puede estar vacío", "INVALID_SOURCE_PATH");
            }
            if (oldFileName == null || oldFileName.trim().isEmpty()) {
                return OperationResponse.error("El nombre del archivo no puede estar vacío", "INVALID_FILENAME");
            }
            if (newFileName == null || newFileName.trim().isEmpty()) {
                return OperationResponse.error("El nombre del archivo newFileName no puede estar vacío", "INVALID_FILENAME");
            }
            if (userId == null) {
                return OperationResponse.error("El userId no puede ser null", "INVALID_USER");
            }

            return fileService.renameFileByPath( directoryPath,  oldFileName,  newFileName,  userId);

        } catch (Exception e) {
            e.printStackTrace();
            return OperationResponse.error("Error al rename archivos: " + e.getMessage(), "INTERNAL_ERROR");

        }
    }

    @WebMethod
    public OperationResponse  renameDirectoryByPath(String directoryPath, String newName, Long userId)
    {
        try {
            // Validaciones
            if (directoryPath == null || directoryPath.trim().isEmpty()) {
                return OperationResponse.error("El path  no puede estar vacío", "INVALID_SOURCE_PATH");
            }

            if (newName == null || newName.trim().isEmpty()) {
                return OperationResponse.error("El nombre del archivo newName no puede estar vacío", "INVALID_FILENAME");
            }
            if (userId == null) {
                return OperationResponse.error("El userId no puede ser null", "INVALID_USER");
            }

            return fileService.renameDirectoryByPath( directoryPath,  newName,  userId);

        } catch (Exception e) {
            e.printStackTrace();
            return OperationResponse.error("Error al rename directorio: " + e.getMessage(), "INTERNAL_ERROR");

        }
    }


    // TODO
    @WebMethod
    public OperationResponse deleteFileByPath(String directoryPath, String fileName, Long userId) {
        try {
            // Validaciones
            if (directoryPath == null || directoryPath.trim().isEmpty()) {
                return OperationResponse.error("El path no puede estar vacío", "INVALID_PATH");
            }
            if (fileName == null || fileName.trim().isEmpty()) {
                return OperationResponse.error("El nombre del archivo no puede estar vacío", "INVALID_FILENAME");
            }
            if (userId == null) {
                return OperationResponse.error("El userId no puede ser null", "INVALID_USER");
            }

            return fileService.deleteFileByPath(directoryPath, fileName, userId);

        } catch (Exception e) {
            e.printStackTrace();
            return OperationResponse.error("Error al delete archivo: " + e.getMessage(), "INTERNAL_ERROR");

        }
    }

    // TODO
    @WebMethod
    public OperationResponse deleteDirectoryById(Long directoryId, Long userId) {
        try {
            // Validaciones
            if (directoryId == null) {
                return OperationResponse.error("El directoryId no puede ser null", "INVALID_DIRECTORY");
            }
            if (userId == null) {
                return OperationResponse.error("El userId no puede ser null", "INVALID_USER");
            }

            return fileService.deleteDirectoryById(directoryId, userId);
        } catch (Exception e) {
            e.printStackTrace();
            return OperationResponse.error("Error al delete directory: " + e.getMessage(), "INTERNAL_ERROR");

        }
    }


    @WebMethod
    public OperationResponse shareFileWithUser(String directoryPath, String fileName, Long ownerId, String shareWithEmail) {
        try {
            // Validaciones
            if (directoryPath == null || directoryPath.trim().isEmpty()) {
                return OperationResponse.error("El path no puede estar vacío", "INVALID_PATH");
            }
            if (fileName == null || fileName.trim().isEmpty()) {
                return OperationResponse.error("El nombre del archivo no puede estar vacío", "INVALID_FILENAME");
            }
            if (ownerId == null) {
                return OperationResponse.error("El ownerId no puede ser null", "INVALID_OWNER");
            }
            if (shareWithEmail == null || shareWithEmail.trim().isEmpty()) {
                return OperationResponse.error("El email no puede estar vacío", "INVALID_EMAIL");
            }

            return fileService.shareFileWithUser( directoryPath,  fileName,  ownerId,  shareWithEmail);
        } catch (Exception e) {
            e.printStackTrace();
            return OperationResponse.error("Error al share file: " + e.getMessage(), "INTERNAL_ERROR");

        }
    }

    @WebMethod

    public OperationResponse shareDirectoryWithUser(String directoryPath, Long ownerId, String shareWithEmail) {
        try {
            // Validaciones
            if (directoryPath == null || directoryPath.trim().isEmpty()) {
                return OperationResponse.error("El path no puede estar vacío", "INVALID_PATH");
            }
            if (ownerId == null) {
                return OperationResponse.error("El ownerId no puede ser null", "INVALID_OWNER");
            }
            if (shareWithEmail == null || shareWithEmail.trim().isEmpty()) {
                return OperationResponse.error("El email no puede estar vacío", "INVALID_EMAIL");
            }

            return fileService.shareDirectoryWithUser( directoryPath,  ownerId,  shareWithEmail);

        } catch (Exception e) {
            e.printStackTrace();
            return OperationResponse.error("Error al share file: " + e.getMessage(), "INTERNAL_ERROR");

        }
    }


    /**
     * Método auxiliar para crear un FileDTO de error
     */
    private FileDTO createErrorFileDTO(String errorMessage) {
        FileDTO error = new FileDTO();
        error.setFileName("ERROR");
        error.setContent(null);
        return error;
    }
}