import java.sql.SQLException;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicReference;

/** Funciones de perfil limitadas al usuario que inició sesión. */
public final class UsuarioHerramientas {
    private static volatile String idUsuarioActual;
    private static final AtomicReference<CambioPerfil> CAMBIO_PENDIENTE =
            new AtomicReference<>();

    private UsuarioHerramientas() {
    }

    public static void establecerUsuarioActual(String idUsuario) {
        idUsuarioActual = idUsuario;
    }

    public static void limpiarCambioPendiente() {
        CAMBIO_PENDIENTE.set(null);
    }

    /**
     * Consulta el perfil no sensible del usuario que inició sesión.
     */
    public static String consultarMiPerfil() {
        String idUsuario = idUsuarioActual;
        if (idUsuario == null || idUsuario.isBlank()) {
            return "No hay un usuario activo.";
        }

        try {
            UsuarioDAO.PerfilUsuario perfil =
                    new UsuarioDAO().consultarPerfil(idUsuario);

            if (perfil == null) {
                return "No se encontró el perfil del usuario activo.";
            }

            return "Perfil actual: usuario "
                    + perfil.username()
                    + ", nombre "
                    + perfil.nombre()
                    + ", apellidos "
                    + perfil.apellidos()
                    + ", correo "
                    + perfil.correo()
                    + ".";
        } catch (SQLException error) {
            throw new IllegalStateException(
                    "No se pudo consultar el perfil.",
                    error
            );
        }
    }

    /**
     * Prepara un cambio de un solo campo. No guarda nada en la base de datos.
     *
     * @param campo Campo: username, nombre, apellidos o correo.
     * @param nuevoValor Valor que el usuario pidió guardar.
     */
    public static String prepararCambioPerfil(
            String campo,
            String nuevoValor
    ) {
        String idUsuario = idUsuarioActual;

        if (idUsuario == null || idUsuario.isBlank()) {
            return "No hay un usuario activo.";
        }
        if (campo == null || nuevoValor == null || nuevoValor.isBlank()) {
            return "Indica el campo y el nuevo valor.";
        }

        try {
            UsuarioDAO dao = new UsuarioDAO();
            UsuarioDAO.PerfilUsuario actual = dao.consultarPerfil(idUsuario);

            if (actual == null) {
                return "No se encontró el perfil del usuario activo.";
            }

            String campoNormalizado =
                    campo.trim().toLowerCase(Locale.ROOT);
            String valor = nuevoValor.trim();

            UsuarioDAO.PerfilUsuario propuesto;
            String valorAnterior;

            switch (campoNormalizado) {
                case "username", "usuario", "nombre de usuario" -> {
                    campoNormalizado = "username";
                    valorAnterior = actual.username();
                    propuesto = new UsuarioDAO.PerfilUsuario(
                            valor,
                            actual.nombre(),
                            actual.apellidos(),
                            actual.correo()
                    );
                }
                case "nombre" -> {
                    valorAnterior = actual.nombre();
                    propuesto = new UsuarioDAO.PerfilUsuario(
                            actual.username(),
                            valor,
                            actual.apellidos(),
                            actual.correo()
                    );
                }
                case "apellidos" -> {
                    valorAnterior = actual.apellidos();
                    propuesto = new UsuarioDAO.PerfilUsuario(
                            actual.username(),
                            actual.nombre(),
                            valor,
                            actual.correo()
                    );
                }
                case "correo", "email" -> {
                    campoNormalizado = "correo";
                    valorAnterior = actual.correo();
                    propuesto = new UsuarioDAO.PerfilUsuario(
                            actual.username(),
                            actual.nombre(),
                            actual.apellidos(),
                            valor
                    );
                }
                default -> {
                    return "Campo no permitido. Usa username, nombre, "
                            + "apellidos o correo.";
                }
            }

            CAMBIO_PENDIENTE.set(new CambioPerfil(
                    campoNormalizado,
                    valorAnterior == null ? "" : valorAnterior,
                    valor,
                    propuesto
            ));

            return "Preparé el cambio de "
                    + campoNormalizado
                    + ". La aplicación pedirá confirmación antes de guardarlo.";

        } catch (SQLException error) {
            throw new IllegalStateException(
                    "No se pudo preparar el cambio de perfil.",
                    error
            );
        }
    }

    public static CambioPerfil tomarCambioPendiente() {
        return CAMBIO_PENDIENTE.getAndSet(null);
    }

    public record CambioPerfil(
            String campo,
            String valorAnterior,
            String valorNuevo,
            UsuarioDAO.PerfilUsuario perfilNuevo
    ) {
    }
}