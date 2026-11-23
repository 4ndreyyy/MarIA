package com.maria.dao;

import com.maria.config.ConexionBD;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/** Consulta planes de membresía y cambia el plan asociado a una cuenta. */
public final class MembresiaDAO {
    public static final String PLAN_BASICO_ID = "BAS1";
    public static final String PLAN_PRUEBA_ID = "PRO1";

    /** Devuelve el plan que actualmente tiene asignado un usuario. */
    public Membresia obtenerParaUsuario(String idUsuario) throws SQLException {
        String sql = """
                SELECT m.ID_TIPOS, m.NOMBRE_TIPOS, m.LIMITE_TOKENS
                FROM HR.USUARIOS u
                JOIN HR.MEMBRESIAS m ON m.ID_TIPOS = u.ID_TIPOS
                WHERE u.ID_USUARIO = ?
                """;

        try (Connection conexion = ConexionBD.obtenerConexion();
             PreparedStatement ps = conexion.prepareStatement(sql)) {
            ps.setString(1, idUsuario.trim());
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new SQLException(
                            "No se encontró la membresía asociada al usuario."
                    );
                }
                return new Membresia(
                        rs.getString("ID_TIPOS").trim(),
                        rs.getString("NOMBRE_TIPOS").trim(),
                        rs.getLong("LIMITE_TOKENS")
                );
            }
        }
    }

    /**
     * Asigna un plan del catálogo a una cuenta dentro de una transacción.
     * El catálogo de MEMBRESIAS no se altera al cambiar un usuario.
     */
    public void asignar(String idUsuario, String idTipos) throws SQLException {
        try (Connection conexion = ConexionBD.obtenerConexion()) {
            conexion.setAutoCommit(false);
            try {
                String verificarPlan =
                        "SELECT 1 FROM HR.MEMBRESIAS WHERE ID_TIPOS = ?";
                try (PreparedStatement ps =
                             conexion.prepareStatement(verificarPlan)) {
                    ps.setString(1, idTipos.trim());
                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) {
                            throw new SQLException(
                                    "El plan " + idTipos.trim()
                                            + " no existe en HR.MEMBRESIAS. "
                                            + "Ejecuta docs/sql/membresia_prueba.sql."
                            );
                        }
                    }
                }

                String actualizar = """
                        UPDATE HR.USUARIOS
                        SET ID_TIPOS = ?
                        WHERE ID_USUARIO = ?
                        """;
                try (PreparedStatement ps =
                             conexion.prepareStatement(actualizar)) {
                    ps.setString(1, idTipos.trim());
                    ps.setString(2, idUsuario.trim());
                    if (ps.executeUpdate() != 1) {
                        throw new SQLException(
                                "No se encontró el usuario para cambiar su membresía."
                        );
                    }
                }
                conexion.commit();
            } catch (SQLException | RuntimeException error) {
                try {
                    conexion.rollback();
                } catch (SQLException errorRollback) {
                    error.addSuppressed(errorRollback);
                }
                throw error;
            }
        }
    }

    /** Plan devuelto por Oracle para una cuenta. */
    public record Membresia(String idTipos, String nombre, long limiteTokens) {
    }
}
