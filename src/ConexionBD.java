import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public final class ConexionBD {
    private ConexionBD() {
    }

    public static Connection obtenerConexion() throws SQLException {
        String url = variableRequerida("DB_URL");
        String usuario = variableRequerida("DB_USER");
        String contrasena = variableRequerida("DB_PASSWORD");

        return DriverManager.getConnection(url, usuario, contrasena);
    }

    private static String variableRequerida(String nombre) {
        String valor = System.getenv(nombre);

        if (valor == null || valor.isBlank()) {
            throw new IllegalStateException(
                    "Falta configurar la variable de entorno " + nombre
            );
        }

        return valor;
    }
}