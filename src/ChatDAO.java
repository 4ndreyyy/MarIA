import java.io.StringReader;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

/** Guarda conversaciones y mensajes de texto en Oracle. */
public final class ChatDAO {
    private static final String CODIGO_MODELO = "gemini-3.5-flash";

    public String crearChat(String idUsuario, String primeraPregunta)
            throws SQLException {
        String idModelo = buscarIdModelo();
        String idChat = nuevoId();
        String titulo = primeraPregunta == null
                ? "Nuevo chat"
                : primeraPregunta.trim();

        if (titulo.isEmpty()) {
            titulo = "Nuevo chat";
        }
        if (titulo.length() > 150) {
            titulo = titulo.substring(0, 150);
        }

        String sql = """
                INSERT INTO HR.CHATS
                    (ID_CHATS, ID_USUARIO, ID_MODELO, TITULO, FECHA, FECHA_MODIFICACION)
                VALUES (?, ?, ?, ?, SYSDATE, SYSDATE)
                """;

        try (Connection conexion = ConexionBD.obtenerConexion();
             PreparedStatement ps = conexion.prepareStatement(sql)) {
            ps.setString(1, idChat);
            ps.setString(2, idUsuario.trim());
            ps.setString(3, idModelo);
            ps.setString(4, titulo);
            ps.executeUpdate();
        }

        return idChat;
    }

    public void guardarMensaje(
            String idChat,
            String emisor,
            String receptor,
            String contenido
    ) throws SQLException {
        if (contenido == null) {
            contenido = "";
        }

        String sql = """
                INSERT INTO HR.MENSAJES
                    (ID_MENSAJE, TIPO_MENSAJE, EMISOR, RECEPTOR,
                     ARCHIVO_ADJUNTO, ID_CHATS, CONTENIDO, FECHA)
                VALUES (?, '.txt', ?, ?, 'N', ?, ?, SYSDATE)
                """;

        try (Connection conexion = ConexionBD.obtenerConexion();
             PreparedStatement ps = conexion.prepareStatement(sql)) {
            ps.setString(1, nuevoId());
            ps.setString(2, emisor);
            ps.setString(3, receptor);
            ps.setString(4, idChat);
            ps.setCharacterStream(
                    5,
                    new StringReader(contenido),
                    contenido.length()
            );
            ps.executeUpdate();

            try (PreparedStatement actualizar =
                         conexion.prepareStatement(
                                 "UPDATE HR.CHATS " +
                                         "SET FECHA_MODIFICACION = SYSDATE " +
                                         "WHERE ID_CHATS = ?")) {
                actualizar.setString(1, idChat);
                actualizar.executeUpdate();
            }
        }
    }

    private String buscarIdModelo() throws SQLException {
        String sql =
                "SELECT ID_MODELO FROM HR.MODELO WHERE CODIGO_API = ?";

        try (Connection conexion = ConexionBD.obtenerConexion();
             PreparedStatement ps = conexion.prepareStatement(sql)) {
            ps.setString(1, CODIGO_MODELO);

            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new SQLException(
                            "No existe en HR.MODELO el código "
                                    + CODIGO_MODELO
                    );
                }

                String idModelo = rs.getString("ID_MODELO").trim();

                if (rs.next()) {
                    throw new SQLException(
                            "Hay más de un registro en HR.MODELO para "
                                    + CODIGO_MODELO
                    );
                }

                return idModelo;
            }
        }
    }

    private static String nuevoId() {
        return UUID.randomUUID()
                .toString()
                .replace("-", "")
                .toUpperCase();
    }
}