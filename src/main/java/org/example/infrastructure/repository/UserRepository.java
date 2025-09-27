package org.example.infrastructure.repository;

import org.example.application.Dto.UserResponse;
import org.example.domain.model.User;

import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;

public class UserRepository {
    /**
     * Registra un usuario en la base de datos.
     *
     */
    public UserResponse register(String email, String passwordHash) {
        String call = "{CALL RegisterUser(?, ?, ?)}";

        try (Connection conn = DbConnection.getConnection();
             CallableStatement stmt = conn.prepareCall(call)) {

            stmt.setString(1, email);            // p_email
            stmt.setString(2, passwordHash);     // p_password_hash
            stmt.registerOutParameter(3, java.sql.Types.BIGINT);  // p_user_id (output)

            // Ejecutamos el procedimiento almacenado
            stmt.executeUpdate();

            // Obtener el ID del usuario generado
            long userId = stmt.getLong(3);

            if (userId == -1) {
                return new UserResponse(false, "Email duplicado o error interno", null);
            }

            User user = new User(userId, email, passwordHash);
            return new UserResponse(true, "Registro exitoso", user);

        } catch (SQLException e) {
            return new UserResponse(false, "Error al registrar el usuario: " + e.getMessage(), null);
        }
    }

    /**
     * Busca un usuario por su email usando el procedure FindUserByEmail.
     */
    public UserResponse findByEmail(String email) {
        String call = "{CALL FindUserByEmail(?)}";

        try (Connection conn = DbConnection.getConnection();
             CallableStatement stmt = conn.prepareCall(call)) {

            stmt.setString(1, email);
            ResultSet rs = stmt.executeQuery();

            if (rs.next()) {
                User user = new User(
                        rs.getLong("id"),
                        rs.getString("email"),
                        rs.getString("password_hash")
                );

                return new UserResponse(true, "Usuario encontrado", user);
            }else {
                return new UserResponse(false, "Usuario no encontrado", null);
            }

        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }
}
