package org.example.infrastructure.repository;

import org.example.domain.model.File;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;

import static com.sun.xml.ws.spi.db.BindingContextFactory.LOGGER;

public class FileRepository {

    private final Connection connection;

    public FileRepository(Connection connection) {
        this.connection = connection;
    }

    /**
     * Obtiene el UUID de un archivo dado su path completo y nombre
     * @param directoryPath Path completo del directorio (ej: "/documentos/2025")
     * @param fileName Nombre del archivo (ej: "informe.pdf")
     * @return UUID del archivo si existe, null si no se encuentra
     * @throws SQLException si ocurre un error de base de datos
     */
    public String getFileUuidByPath(String directoryPath, String fileName) throws SQLException {
        String sql = "{CALL get_file_uuid_by_path(?, ?)}";

        try (CallableStatement stmt = connection.prepareCall(sql)) {
            stmt.setString(1, directoryPath);
            stmt.setString(2, fileName);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    String uuid = rs.getString("file_uuid");
                    System.out.println("Archivo encontrado: " + fileName + " → UUID: " + uuid);
                    return uuid;
                }
            }
        } catch (SQLException e) {
            System.err.println("ERROR getFileUuidByPath: " + e.getMessage());
            throw e;
        }

        System.err.println("Archivo no encontrado: " + fileName + " en " + directoryPath);
        return null;
    }


    // 1. Verificar si el usuario es owner de la carpeta
    public boolean isDirectoryOwner(Long userId, Long directoryId) throws SQLException {
        String sql = "SELECT COUNT(*) FROM Directory WHERE id = ? AND owner_id = ?";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setLong(1, directoryId);
            stmt.setLong(2, userId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1) > 0;
                }
            }
        }
        return false;
    }

    // 1. Verificar si el usuario es dueño del archivo
    public boolean isFileOwner(Long userId, String fileUuid) throws SQLException {
        String sql = "{CALL is_file_owner(?, ?)}";
        try (CallableStatement stmt = connection.prepareCall(sql)) {
            stmt.setLong(1, userId);
            stmt.setString(2, fileUuid);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    System.out.println("Debug isFileOwner: userId=" + userId + ", fileUuid=" + fileUuid + ", is_owner=" + rs.getBoolean("is_owner"));
                    return rs.getBoolean("is_owner");
                }
            }
        }
        return false;
    }
    // 2. Verificar si la carpeta fue compartida con el usuario
    public boolean isDirectorySharedWith(Long userId, Long directoryId) throws SQLException {
        String sql = "SELECT COUNT(*) FROM Directory_Share WHERE directory_id = ? AND shared_with_user_id = ?";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setLong(1, directoryId);
            stmt.setLong(2, userId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1) > 0;
                }
            }
        }
        return false;
    }

    // 3. Obtener el owner real de la carpeta
    public Long getDirectoryOwner(Long directoryId) throws SQLException {
        String sql = "SELECT owner_id FROM Directory WHERE id = ?";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setLong(1, directoryId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getLong("owner_id");
                }
            }
        }
        return null; // si no existe
    }



    // 2. Verificar si el archivo fue compartido con el usuario
    public boolean isFileSharedWith(Long userId, String fileUuid) throws SQLException {
        String sql = "{CALL is_file_shared_with(?, ?)}";
        try (CallableStatement stmt = connection.prepareCall(sql)) {
            stmt.setLong(1, userId);
            stmt.setString(2, fileUuid);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getBoolean("is_shared");
                }
            }
        }
        return false;
    }

    // 3. Obtener el ID del directorio que contiene el archivo
    public Long getDirectoryIdByFile(String fileUuid) throws SQLException {
        String sql = "{CALL get_directory_id_by_file(?)}";
        try (CallableStatement stmt = connection.prepareCall(sql)) {
            stmt.setString(1, fileUuid);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getLong("directory_id");
                }
            }
        }
        return null;
    }


    // Crea la jerarquía de directorios dada una ruta como "dir1/dir2/dir3"
    public Long createDirectoryHierarchy(String path, Long ownerId) throws SQLException {
        String[] parts = path.split("/");
        Long parentId = null;
        Long currentId = null;

        for (String part : parts) {
            if (part == null || part.trim().isEmpty()) continue;

            // Buscar si ya existe
            String selectSql = "SELECT id FROM Directory WHERE name = ? AND owner_id = ? AND " +
                    (parentId == null ? "father_id IS NULL" : "father_id = ?");
            try (PreparedStatement stmt = connection.prepareStatement(selectSql)) {
                stmt.setString(1, part);
                stmt.setLong(2, ownerId);
                if (parentId != null) stmt.setLong(3, parentId);

                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        currentId = rs.getLong("id");
                    } else {
                        // Insertar si no existe
                        String insertSql = "INSERT INTO Directory(name, owner_id, father_id) VALUES (?, ?, ?)";
                        try (PreparedStatement ins = connection.prepareStatement(insertSql, Statement.RETURN_GENERATED_KEYS)) {
                            ins.setString(1, part);
                            ins.setLong(2, ownerId);
                            if (parentId != null) ins.setLong(3, parentId);
                            else ins.setNull(3, Types.BIGINT);
                            ins.executeUpdate();
                            try (ResultSet keys = ins.getGeneratedKeys()) {
                                if (keys.next()) {
                                    currentId = keys.getLong(1);
                                }
                            }
                        }
                    }
                }
            }
            parentId = currentId;
        }
        return currentId; // id del último directorio creado o encontrado
    }

    // Insertar un nuevo archivo en la base de datos
    public boolean uploadFile(File file) throws SQLException {
        String sql = "{CALL insert_file(?, ?, ?, ?, ?)}";
        try (CallableStatement stmt = connection.prepareCall(sql)) {
            stmt.setString(1, file.getId());
            stmt.setString(2, file.getName());
            stmt.setLong(3, file.getSize());
            stmt.setLong(4, file.getOwnerId());     // convertir correctamente
            stmt.setLong(5, file.getDirectoryId()); // convertir correctamente
            stmt.execute();
        }
        return true;
    }

    public File findByUuid(String fileUuid) throws SQLException {
        String sql = "SELECT uuid, name, size, directory_id, owner_id FROM File WHERE uuid = ?";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, fileUuid);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return new File(
                            rs.getString("uuid"),
                            rs.getString("name"),
                            rs.getLong("size"),
                            rs.getLong("owner_id"),
                            rs.getLong("directory_id")
                    );
                }
            }
        }
        return null; // si no existe
    }



    /**
     * Mueve un archivo de un directorio a otro usando paths.
     */
    public boolean moveFileByPath(String sourcePath, String fileName, String destinationPath, Long userId) throws SQLException {
        String sql = "{CALL move_file_by_path(?, ?, ?, ?)}";
        try (CallableStatement stmt = connection.prepareCall(sql)) {
            stmt.setString(1, sourcePath);
            stmt.setString(2, fileName);
            stmt.setString(3, destinationPath);
            stmt.setLong(4, userId);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    boolean success = rs.getBoolean("success");
                    String message = rs.getString("message");
                    String errorCode = rs.getString("error_code");

                    if (!success) {
                        LOGGER.warning("Error al mover archivo: " + message + " (code: " + errorCode + ")");
                        return false;
                    }

                    String fileUuid = rs.getString("file_uuid");
                    LOGGER.info("Archivo movido exitosamente: " + fileUuid);
                    return true;
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "ERROR moveFileByPath", e);
            throw e;
        }
        return false;
    }


    /**
     * Mueve un directorio completo a otro directorio
     * @param sourcePath Path completo del directorio a mover (ej: "/proyectos/2024")
     * @param destinationPath Path del directorio destino (ej: "/archivos")
     * @param userId ID del usuario que realiza la operación
     * @return true si se movió exitosamente, false en caso contrario
     */
    public boolean moveDirectoryByPath(String sourcePath, String destinationPath, Long userId) throws SQLException {
        String sql = "{CALL move_directory_by_path(?, ?, ?)}";

        try (CallableStatement stmt = connection.prepareCall(sql)) {
            stmt.setString(1, sourcePath);
            stmt.setString(2, destinationPath);
            stmt.setLong(3, userId);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    boolean success = rs.getBoolean("success");
                    String message = rs.getString("message");
                    String errorCode = rs.getString("error_code");

                    if (!success) {
                        System.err.println("Error al mover directorio: " + message + " (code: " + errorCode + ")");
                        return false;
                    }

                    Long directoryId = rs.getLong("directory_id");
                    Long newParentId = (Long) rs.getObject("new_parent_id");

                    System.out.println("Directorio movido exitosamente: " + message);
                    System.out.println("  - Directory ID: " + directoryId);
                    System.out.println("  - New Parent ID: " + newParentId);

                    return true;
                }
            }
        } catch (SQLException e) {
            System.err.println("ERROR moveDirectoryByPath: " + e.getMessage());
            throw e;
        }

        return false;
    }

    /**
     * Renombra un archivo usando paths.
     */
    public boolean renameFileByPath(String directoryPath, String oldFileName, String newFileName, Long userId) throws SQLException {
        String sql = "{CALL rename_file_by_path(?, ?, ?, ?)}";
        try (CallableStatement stmt = connection.prepareCall(sql)) {
            stmt.setString(1, directoryPath);
            stmt.setString(2, oldFileName);
            stmt.setString(3, newFileName);
            stmt.setLong(4, userId);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    boolean success = rs.getBoolean("success");
                    String message = rs.getString("message");
                    String errorCode = rs.getString("error_code");

                    if (!success) {
                        LOGGER.warning("Error al renombrar archivo: " + message + " (code: " + errorCode + ")");
                        return false;
                    }

                    String fileUuid = rs.getString("file_uuid");
                    LOGGER.info("Archivo renombrado exitosamente: " + fileUuid);
                    return true;
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "ERROR renameFileByPath", e);
            throw e;
        }
        return false;
    }


    /**
     * Renombra un directorio usando paths.
     */
    public boolean renameDirectoryByPath(String directoryPath, String newName, Long userId) throws SQLException {
        String sql = "{CALL rename_directory_by_path(?, ?, ?)}";
        try (CallableStatement stmt = connection.prepareCall(sql)) {
            stmt.setString(1, directoryPath);
            stmt.setString(2, newName);
            stmt.setLong(3, userId);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    boolean success = rs.getBoolean("success");
                    String message = rs.getString("message");
                    String errorCode = rs.getString("error_code");

                    if (!success) {
                        LOGGER.warning("Error al renombrar directorio: " + message + " (code: " + errorCode + ")");
                        return false;
                    }

                    Long directoryId = rs.getLong("directory_id");
                    LOGGER.info("Directorio renombrado exitosamente: " + directoryId);
                    return true;
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "ERROR renameDirectoryByPath", e);
            throw e;
        }
        return false;
    }



    /**
     * Elimina un archivo usando paths.
     */
    public boolean deleteFileByPath(String directoryPath, String fileName, Long userId) throws SQLException {
        String sql = "{CALL delete_file_by_path(?, ?, ?)}";
        try (CallableStatement stmt = connection.prepareCall(sql)) {
            stmt.setString(1, directoryPath);
            stmt.setString(2, fileName);
            stmt.setLong(3, userId);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    boolean success = rs.getBoolean("success");
                    String message = rs.getString("message");
                    String errorCode = rs.getString("error_code");

                    if (!success) {
                        LOGGER.warning("Error al eliminar archivo: " + message + " (code: " + errorCode + ")");
                        return false;
                    }

                    String fileUuid = rs.getString("file_uuid");
                    LOGGER.info("Archivo eliminado exitosamente: " + fileUuid);
                    return true;
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "ERROR deleteFileByPath", e);
            throw e;
        }
        return false;
    }


    /**
     * Comparte un archivo con un usuario.
     */
    public boolean shareFileWithUser(String directoryPath, String fileName, Long ownerId, String shareWithEmail) throws SQLException {
        String sql = "{CALL share_file_with_user(?, ?, ?, ?)}";
        try (CallableStatement stmt = connection.prepareCall(sql)) {
            stmt.setString(1, directoryPath);
            stmt.setString(2, fileName);
            stmt.setLong(3, ownerId);
            stmt.setString(4, shareWithEmail);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    boolean success = rs.getBoolean("success");
                    String message = rs.getString("message");
                    String errorCode = rs.getString("error_code");

                    if (!success) {
                        LOGGER.warning("Error al compartir archivo: " + message + " (code: " + errorCode + ")");
                        return false;
                    }

                    String fileUuid = rs.getString("file_uuid");
                    LOGGER.info("Archivo compartido exitosamente: " + fileUuid + " con " + shareWithEmail);
                    return true;
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "ERROR shareFileWithUser", e);
            throw e;
        }
        return false;
    }

    /**
     * Comparte un directorio con un usuario.
     */
    public boolean shareDirectoryWithUser(String directoryPath, Long ownerId, String shareWithEmail) throws SQLException {
        String sql = "{CALL share_directory_with_user(?, ?, ?)}";
        try (CallableStatement stmt = connection.prepareCall(sql)) {
            stmt.setString(1, directoryPath);
            stmt.setLong(2, ownerId);
            stmt.setString(3, shareWithEmail);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    boolean success = rs.getBoolean("success");
                    String message = rs.getString("message");
                    String errorCode = rs.getString("error_code");

                    if (!success) {
                        LOGGER.warning("Error al compartir directorio: " + message + " (code: " + errorCode + ")");
                        return false;
                    }

                    Long directoryId = rs.getLong("directory_id");
                    LOGGER.info("Directorio compartido exitosamente: " + directoryId + " con " + shareWithEmail);
                    return true;
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "ERROR shareDirectoryWithUser", e);
            throw e;
        }
        return false;
    }



    /**
     * Ejecuta delete_directory_by_id y retorna archivos eliminados
     * USABLE DIRECTAMENTE DESDE FileRepository
     */

    public List<File> deleteDirectoryById(Long directoryId, Long userId) throws SQLException {
        String sql = "{CALL delete_directory_by_id(?, ?)}";
        List<File> filesDeleted = new ArrayList<>();

        try (CallableStatement stmt = connection.prepareCall(sql)) {
            stmt.setLong(1, directoryId);
            stmt.setLong(2, userId);

            // Ejecutar y procesar resultados
            boolean hasResults = stmt.execute();

            if (hasResults) {
                // PRIMER RESULT SET: Información de resumen
                try (ResultSet rs = stmt.getResultSet()) {
                    if (rs.next()) {
                        boolean success = rs.getBoolean("success");
                        String message = rs.getString("message");
                        String errorCode = rs.getString("error_code");
                        int directoriesDeleted = rs.getInt("directories_deleted");
                        int filesDeletedCount = rs.getInt("files_deleted");

                        LOGGER.info("Resumen delete_directory: " + message);

                        if (!success) {
                            LOGGER.warning("Error: " + errorCode + " - " + message);
                            return filesDeleted;
                        }
                    }
                }

                // SEGUNDO RESULT SET: Detalle de archivos eliminados
                if (stmt.getMoreResults()) {
                    try (ResultSet rs = stmt.getResultSet()) {
                        while (rs.next()) {
                            String uuid = rs.getString("uuid");
                            String name = rs.getString("name");
                            long size = rs.getLong("size");
                            Long ownerId = rs.getLong("owner_id");
                            Long dirId = rs.getLong("directory_id");

                            File file = new File(uuid, name, size, ownerId, dirId);
                            filesDeleted.add(file);

                            LOGGER.fine("Archivo eliminado: " + name +
                                    " (uuid=" + uuid + ", size=" + size + ")");
                        }
                    }
                }
            }

        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Error en executeDeleteDirectory", e);
            throw e;
        }

        return filesDeleted;
    }


    public List<File> getAllFilesInDirectory(Long directoryId) throws SQLException {
        String sql = "{CALL get_all_files_in_directory(?)}";
        List<File> files = new ArrayList<>();

        try (CallableStatement stmt = connection.prepareCall(sql)) {
            stmt.setLong(1, directoryId);

            boolean hasResults = stmt.execute();

            if (hasResults) {
                try (ResultSet rs = stmt.getResultSet()) {
                    while (rs.next()) {
                        String uuid = rs.getString("uuid");
                        String name = rs.getString("name");
                        long size = rs.getLong("size");
                        Long ownerId = rs.getLong("owner_id");
                        Long dirId = rs.getLong("directory_id");

                        File file = new File(uuid, name, size, ownerId, dirId);
                        files.add(file);

                        LOGGER.fine("Archivo encontrado: " + name +
                                " (uuid=" + uuid + ", size=" + size + ", dir=" + dirId + ")");
                    }
                }
            }

        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Error en getAllFilesInDirectory", e);
            throw e;
        }

        return files;
    }


    // --------- METODOS RELACIONADOS A LOS NODOS DE ALMACENAMIENTO


    public Long upsertNode(String ip, int port) throws SQLException {
        String selectSql = "CALL upsert_node(?, ?)";
        try (CallableStatement stmt = connection.prepareCall(selectSql)) {
            stmt.setString(1, ip);
            stmt.setInt(2, port);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getLong("node_id"); // el alias que pusimos en el procedure
                }
            }
        }
        return null;
    }


    public String registerFileNode(String fileUuid, Long nodeId) throws SQLException {
        String sql = "{CALL register_file_node(?, ?)}";
        try (CallableStatement stmt = connection.prepareCall(sql)) {
            stmt.setString(1, fileUuid);
            stmt.setLong(2, nodeId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("status"); // devuelve "inserted" o "exists"
                }
            }
        }
        return null;
    }

    public List<Long> getNodesByFile(String fileUuid) throws SQLException {
        String sql = "{CALL get_nodes_by_file(?)}";
        List<Long> nodeIds = new ArrayList<>();

        try (CallableStatement stmt = connection.prepareCall(sql)) {
            stmt.setString(1, fileUuid);
            System.out.println("Debug getNodesByFile: ejecutando procedimiento para fileUuid=" + fileUuid);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    long nodeId = rs.getLong("id");
                    System.out.println("Debug getNodesByFile: fileUuid=" + fileUuid + ", found nodeId=" + nodeId);
                    nodeIds.add(nodeId);
                }
            }
        }

        return nodeIds;
    }


    /**
     * Obtiene el espacio total usado por un nodo (suma de tamaños de archivos).
     * @param nodeId ID del nodo
     * @return Espacio usado en bytes
     */
    public long getNodeSpaceUsed(Long nodeId) throws SQLException {
        String sql = "{CALL get_node_space_used(?)}";

        try (CallableStatement stmt = connection.prepareCall(sql)) {
            stmt.setLong(1, nodeId);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getLong("space_used");
                }
            }
        } catch (SQLException e) {
            System.err.println("ERROR getNodeSpaceUsed: " + e.getMessage());
            throw e;
        }

        return 0;
    }

    /**
     * Cuenta cuántos archivos tiene un nodo.
     * @param nodeId ID del nodo
     * @return Cantidad de archivos
     */
    public int countFilesByNode(Long nodeId) throws SQLException {
        String sql = "{CALL count_files_by_node(?)}";

        try (CallableStatement stmt = connection.prepareCall(sql)) {
            stmt.setLong(1, nodeId);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("file_count");
                }
            }
        } catch (SQLException e) {
            System.err.println("ERROR countFilesByNode: " + e.getMessage());
            throw e;
        }

        return 0;
    }

    /**
     * Obtiene la capacidad total de un nodo (free_space).
     * @param nodeId ID del nodo
     * @return Capacidad en bytes
     */
    public long getNodeCapacity(Long nodeId) throws SQLException {
        String sql = "{CALL get_node_capacity(?)}";

        try (CallableStatement stmt = connection.prepareCall(sql)) {
            stmt.setLong(1, nodeId);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getLong("capacity");
                }
            }
        } catch (SQLException e) {
            System.err.println("ERROR getNodeCapacity: " + e.getMessage());
            throw e;
        }

        return 0;
    }

}
