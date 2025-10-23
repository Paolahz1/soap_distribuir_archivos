package org.example.application.service;

import org.example.infrastructure.repository.FileRepository;

import java.sql.SQLException;

public class PermissionService {

    private final FileRepository fileRepository ;

    public PermissionService(FileRepository fileRepository) {
        this.fileRepository = fileRepository;
    }

    public boolean canWriteToDirectory(Long userId, Long directoryId) throws SQLException {
        // 1. Verificar si userId es owner de la carpeta
        if (fileRepository.isDirectoryOwner(userId, directoryId)) {
            return true;
        }

        // 2. Verificar si la carpeta fue compartida con userId
        return fileRepository.isDirectorySharedWith(userId, directoryId);
    }


    public boolean isOwnerDirectory(Long userId, Long directoryId) throws SQLException {
        // 1. Verificar si userId es owner de la carpeta
        if (fileRepository.isDirectoryOwner(userId, directoryId)) {
            return true;
        }
        return  false;
    }

    public Long resolveOwnerOfDirectory(Long directoryId) throws SQLException {
        return fileRepository.getDirectoryOwner(directoryId);
    }

    public  boolean isOwnerFile(Long userId, String fileUuid) throws SQLException {
        return fileRepository.isFileOwner(userId, fileUuid);
    }


    public boolean canReadFile(Long userId, String fileUuid) throws SQLException {
        // 1. Verificar si el usuario es dueño del archivo
        System.out.println("PermissionService: Verificando si el usuario " + userId + " es dueño del archivo " + fileRepository.isFileOwner(userId, fileUuid));
        if (fileRepository.isFileOwner(userId, fileUuid)) {
            return true;
        }

        // 2. Verificar si el archivo fue compartido con el usuario
        if (fileRepository.isFileSharedWith(userId, fileUuid)) {
            return true;
        }

        // 3. Verificar si el usuario tiene acceso al directorio que contiene el archivo
        Long directoryId = fileRepository.getDirectoryIdByFile(fileUuid);
        if (directoryId != null) {
            if (fileRepository.isDirectoryOwner(userId, directoryId) || fileRepository.isDirectorySharedWith(userId, directoryId)) {
                return true;
            }
        }

        // 4. Si ninguna condición se cumple, no tiene permiso
        return false;
    }

}
