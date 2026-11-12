import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.io.ByteArrayInputStream;
import java.sql.Types;

/** Guarda y consulta conversaciones y mensajes de Oracle. */
public final class ChatDAO {
    private static final String CODIGO_MODELO = "gemini-3.5-flash-lite";

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

    /** Devuelve los chats que pertenecen al usuario, del más reciente al más antiguo. */
    public List<ChatResumen> listarChats(String idUsuario) throws SQLException {
        String sql = """
                SELECT ID_CHATS,
                       NVL(TITULO, 'Chat sin título') AS TITULO,
                       TO_CHAR(
                           NVL(FECHA_MODIFICACION, FECHA),
                           'DD/MM/YYYY HH24:MI'
                       ) AS FECHA_TEXTO
                FROM HR.CHATS
                WHERE ID_USUARIO = ?
                ORDER BY NVL(FECHA_MODIFICACION, FECHA) DESC
                """;

        List<ChatResumen> chats = new ArrayList<>();

        try (Connection conexion = ConexionBD.obtenerConexion();
             PreparedStatement ps = conexion.prepareStatement(sql)) {
            ps.setString(1, idUsuario.trim());

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    chats.add(new ChatResumen(
                            rs.getString("ID_CHATS").trim(),
                            rs.getString("TITULO"),
                            rs.getString("FECHA_TEXTO")
                    ));
                }
            }
        }

        return chats;
    }

    /** Lee mensajes solo si el chat pertenece al usuario indicado. */
    public List<MensajeGuardado> listarMensajes(
            String idChat,
            String idUsuario
    ) throws SQLException {
        String sql = """
                SELECT m.EMISOR, m.CONTENIDO, m.NOMBRE_ARCHIVO
                FROM HR.MENSAJES m
                JOIN HR.CHATS c ON c.ID_CHATS = m.ID_CHATS
                WHERE c.ID_CHATS = ?
                  AND c.ID_USUARIO = ?
                ORDER BY m.FECHA, m.ROWID
                """;

        List<MensajeGuardado> mensajes = new ArrayList<>();

        try (Connection conexion = ConexionBD.obtenerConexion();
             PreparedStatement ps = conexion.prepareStatement(sql)) {
            ps.setString(1, idChat.trim());
            ps.setString(2, idUsuario.trim());

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String emisor = rs.getString("EMISOR");
                    String contenido = leerClob(rs.getCharacterStream("CONTENIDO"));
                    String nombreArchivo = rs.getString("NOMBRE_ARCHIVO");

                    if (nombreArchivo != null && !nombreArchivo.isBlank()) {
                        contenido += "\n\n📎 " + nombreArchivo;
                    }
                    mensajes.add(new MensajeGuardado(
                            emisor == null ? "" : emisor.trim(),
                            contenido
                    ));
                }
            }
        }

        return mensajes;
    }

    public void guardarMensajeConArchivo(
            String idChat,
            String emisor,
            String receptor,
            String texto,
            String nombreArchivo,
            String mimeType,
            String extension,
            byte[] contenidoArchivo
    ) throws SQLException {
        if (contenidoArchivo == null || contenidoArchivo.length == 0) {
            throw new SQLException("El archivo está vacío.");
        }

        if (nombreArchivo == null || nombreArchivo.length() > 100) {
            throw new SQLException(
                    "El nombre del archivo no puede superar 100 caracteres."
            );
        }

        if (texto == null) {
            texto = "";
        }

        String idMensaje = nuevoId();
        String idArchivo = nuevoId();

        String sqlMensaje = """
            INSERT INTO HR.MENSAJES
                (ID_MENSAJE, TIPO_MENSAJE, EMISOR, RECEPTOR,
                 ARCHIVO_ADJUNTO, TIPO_ARCHIVO, NOMBRE_ARCHIVO,
                 ID_CHATS, CONTENIDO, FECHA)
            VALUES (?, ?, ?, ?, 'S', ?, ?, ?, ?, SYSDATE)
            """;

        String sqlArchivo = """
            INSERT INTO HR.ARCHIVOADJ
                (ID_AADJ, ID_MENSAJE, TIPO, RUTA, CONTENIDO)
            VALUES (?, ?, ?, ?, ?)
            """;

        try (Connection conexion = ConexionBD.obtenerConexion()) {
            conexion.setAutoCommit(false);

            try {
                try (PreparedStatement ps =
                             conexion.prepareStatement(sqlMensaje)) {
                    ps.setString(1, idMensaje);
                    ps.setString(2, extension);
                    ps.setString(3, emisor);
                    ps.setString(4, receptor);
                    ps.setString(5, mimeType);
                    ps.setString(6, nombreArchivo);
                    ps.setString(7, idChat);

                    if (texto.isBlank()) {
                        ps.setNull(8, Types.CLOB);
                    } else {
                        ps.setCharacterStream(
                                8,
                                new java.io.StringReader(texto),
                                texto.length()
                        );
                    }

                    ps.executeUpdate();
                }

                try (PreparedStatement ps =
                             conexion.prepareStatement(sqlArchivo)) {
                    ps.setString(1, idArchivo);
                    ps.setString(2, idMensaje);
                    ps.setString(3, mimeType);
                    ps.setNull(4, Types.VARCHAR);
                    ps.setBinaryStream(
                            5,
                            new ByteArrayInputStream(contenidoArchivo),
                            contenidoArchivo.length
                    );
                    ps.executeUpdate();
                }

                try (PreparedStatement ps = conexion.prepareStatement(
                        "UPDATE HR.CHATS " +
                                "SET FECHA_MODIFICACION = SYSDATE " +
                                "WHERE ID_CHATS = ?")) {
                    ps.setString(1, idChat);
                    ps.executeUpdate();
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

    private static String leerClob(Reader lector) throws SQLException {
        if (lector == null) {
            return "";
        }

        StringBuilder texto = new StringBuilder();
        char[] bloque = new char[4096];

        try (Reader reader = lector) {
            int cantidad;
            while ((cantidad = reader.read(bloque)) != -1) {
                texto.append(bloque, 0, cantidad);
            }
        } catch (IOException error) {
            throw new SQLException("No se pudo leer el contenido del mensaje.", error);
        }

        return texto.toString();
    }

    private static String nuevoId() {
        return UUID.randomUUID()
                .toString()
                .replace("-", "")
                .toUpperCase();
    }

    public record ChatResumen(String idChat, String titulo, String fecha) {
        @Override
        public String toString() {
            return titulo + "  ·  " + fecha;
        }
    }

    public record MensajeGuardado(String emisor, String contenido) {
    }
}