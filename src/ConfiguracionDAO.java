import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class ConfiguracionDAO {

    public Preferencias obtener(String idConfig) throws SQLException {
        String sql = """
                SELECT IDIOMA, TONO_IA, TEMA
                FROM HR.CONFIGURACION
                WHERE ID_CONFIG = ?
                """;

        try (Connection conexion = ConexionBD.obtenerConexion();
             PreparedStatement ps = conexion.prepareStatement(sql)) {
            ps.setString(1, idConfig.trim());

            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new SQLException(
                            "No se encontró la configuración del usuario."
                    );
                }

                return new Preferencias(
                        rs.getString("IDIOMA").trim(),
                        rs.getString("TONO_IA").trim(),
                        rs.getString("TEMA").trim()
                );
            }
        }
    }

    public void actualizar(
            String idConfig,
            String idioma,
            String tonoIA,
            String tema
    ) throws SQLException {
        validarOpciones(idioma, tonoIA, tema);

        String sql = """
                UPDATE HR.CONFIGURACION
                SET IDIOMA = ?, TONO_IA = ?, TEMA = ?
                WHERE ID_CONFIG = ?
                """;

        try (Connection conexion = ConexionBD.obtenerConexion();
             PreparedStatement ps = conexion.prepareStatement(sql)) {
            ps.setString(1, idioma);
            ps.setString(2, tonoIA);
            ps.setString(3, tema);
            ps.setString(4, idConfig.trim());

            if (ps.executeUpdate() != 1) {
                throw new SQLException(
                        "No se pudo actualizar la configuración del usuario."
                );
            }
        }
    }

    private void validarOpciones(
            String idioma,
            String tonoIA,
            String tema
    ) {
        if (!idioma.equals("Español")
                && !idioma.equals("English")
                && !idioma.equals("Français")) {
            throw new IllegalArgumentException("Idioma no permitido.");
        }

        if (!tonoIA.equals("Formal") && !tonoIA.equals("Informal")) {
            throw new IllegalArgumentException("Tono de IA no permitido.");
        }

        if (!tema.equals("Claro") && !tema.equals("Oscuro")) {
            throw new IllegalArgumentException("Tema no permitido.");
        }
    }

    public record Preferencias(
            String idioma,
            String tonoIA,
            String tema
    ) {
    }
}