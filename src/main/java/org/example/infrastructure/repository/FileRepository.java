package org.example.infrastructure.repository;

import org.example.domain.model.File;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class FileRepository {

    private final Connection connection;

    public FileRepository(Connection connection) {
        this.connection = connection;
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


    // NODOS
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



}
