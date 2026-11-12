import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.Base64;
import java.util.UUID;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.MessageDigest;
import java.sql.ResultSet;




public class UsuarioDAO {
    private static final int ITERACIONES = 210_000;

    public void registrar(
            String username,
            String nombre,
            String apellidos,
            String correo,
            char[] contrasena,
            String idTipos
    ) throws SQLException {
        validar(username, "username");
        validar(nombre, "nombre");
        validar(apellidos, "apellidos");
        validar(correo, "correo");
        validar(idTipos, "membresía");

        if (contrasena == null || contrasena.length < 8) {
            throw new IllegalArgumentException(
                    "La contraseña debe tener al menos 8 caracteres.");
        }

        String idUsuario = nuevoId();
        String idConfig = nuevoId();
        String hash;

        try {
            hash = crearHash(contrasena);
        } finally {
            Arrays.fill(contrasena, '\0');
        }

        try (Connection conexion = ConexionBD.obtenerConexion()) {
            conexion.setAutoCommit(false);

            try {
                String sqlConfig = """
                        INSERT INTO HR.CONFIGURACION
                            (ID_CONFIG, IDIOMA, TONO_IA, TEMA)
                        VALUES (?, 'Español', 'Formal', 'Claro')
                        """;

                try (PreparedStatement ps = conexion.prepareStatement(sqlConfig)) {
                    ps.setString(1, idConfig);
                    ps.executeUpdate();
                }

                String sqlUsuario = """
                        INSERT INTO HR.USUARIOS
                            (ID_USUARIO, ID_CONFIG, ID_TIPOS, USERNAME,
                             NOMBRE, APELLIDOS, CORREO, CONTRASENA)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                        """;

                try (PreparedStatement ps = conexion.prepareStatement(sqlUsuario)) {
                    ps.setString(1, idUsuario);
                    ps.setString(2, idConfig);
                    ps.setString(3, idTipos);
                    ps.setString(4, username.trim());
                    ps.setString(5, nombre.trim());
                    ps.setString(6, apellidos.trim());
                    ps.setString(7, correo.trim());
                    ps.setString(8, hash);
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

    private static String nuevoId() {
        return UUID.randomUUID().toString()
                .replace("-", "")
                .toUpperCase();
    }

    private static String crearHash(char[] contrasena) throws SQLException {
        byte[] salt = new byte[16];
        new SecureRandom().nextBytes(salt);

        PBEKeySpec spec = new PBEKeySpec(
                contrasena, salt, ITERACIONES, 256);

        try {
            byte[] hash = SecretKeyFactory
                    .getInstance("PBKDF2WithHmacSHA256")
                    .generateSecret(spec)
                    .getEncoded();

            Base64.Encoder encoder = Base64.getUrlEncoder().withoutPadding();

            return "pbkdf2-sha256$" + ITERACIONES + "$"
                    + encoder.encodeToString(salt) + "$"
                    + encoder.encodeToString(hash);
        } catch (GeneralSecurityException error) {
            throw new SQLException("No se pudo proteger la contraseña.", error);
        } finally {
            spec.clearPassword();
        }
    }

    private static void validar(String valor, String campo) {
        if (valor == null || valor.isBlank()) {
            throw new IllegalArgumentException(
                    "El campo " + campo + " es obligatorio.");
        }
    }

    public UsuarioSesion autenticar(String username, char[] contrasena)
            throws SQLException {
        String sql = """
            SELECT ID_USUARIO, USERNAME, ID_CONFIG, ID_TIPOS, CONTRASENA
            FROM HR.USUARIOS
            WHERE USERNAME = ?
            """;

        try (Connection conexion = ConexionBD.obtenerConexion();
             PreparedStatement ps = conexion.prepareStatement(sql)) {

            ps.setString(1, username.trim());

            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }

                String hashGuardado = rs.getString("CONTRASENA");
                if (!verificarHash(contrasena, hashGuardado)) {
                    return null;
                }

                return new UsuarioSesion(
                        rs.getString("ID_USUARIO").trim(),
                        rs.getString("USERNAME").trim(),
                        rs.getString("ID_CONFIG").trim(),
                        rs.getString("ID_TIPOS").trim()
                );
            }
        } finally {
            if (contrasena != null) {
                Arrays.fill(contrasena, '\0');
            }
        }
    }

    private boolean verificarHash(char[] contrasena, String hashGuardado)
            throws SQLException {
        try {
            String[] partes = hashGuardado.split("\\$");

            if (partes.length != 4 || !partes[0].equals("pbkdf2-sha256")) {
                return false;
            }

            int iteraciones = Integer.parseInt(partes[1]);
            if (iteraciones < 10_000 || iteraciones > 1_000_000) {
                return false;
            }

            Base64.Decoder decoder = Base64.getUrlDecoder();
            byte[] salt = decoder.decode(partes[2]);
            byte[] hashEsperado = decoder.decode(partes[3]);

            PBEKeySpec spec = new PBEKeySpec(
                    contrasena, salt, iteraciones, hashEsperado.length * 8);

            try {
                byte[] hashCalculado = SecretKeyFactory
                        .getInstance("PBKDF2WithHmacSHA256")
                        .generateSecret(spec)
                        .getEncoded();

                return MessageDigest.isEqual(hashEsperado, hashCalculado);
            } finally {
                spec.clearPassword();
            }
        } catch (GeneralSecurityException | IllegalArgumentException error) {
            throw new SQLException("No se pudo verificar la contraseña.", error);
        }
    }


}