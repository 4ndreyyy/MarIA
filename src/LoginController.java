import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.io.IOException;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.function.Consumer;

public class LoginController {
    @FXML private TextField usernameField;
    @FXML private PasswordField passwordField;
    @FXML private Label mensajeLabel;

    private Consumer<UsuarioSesion> alIniciarSesion;

    public void setAlIniciarSesion(Consumer<UsuarioSesion> accion) {
        this.alIniciarSesion = accion;
    }

    @FXML
    private void iniciarSesion() {
        char[] contrasena = passwordField.getText().toCharArray();

        try {
            UsuarioSesion usuario = new UsuarioDAO().autenticar(
                    usernameField.getText(),
                    contrasena
            );

            if (usuario == null) {
                mensajeLabel.setText("Usuario o contraseña incorrectos.");
                passwordField.clear();
                return;
            }

            if (alIniciarSesion != null) {
                alIniciarSesion.accept(usuario);
            }
        } catch (SQLException e) {
            e.printStackTrace();
            mensajeLabel.setText("No se pudo conectar con la base de datos.");
        } finally {
            Arrays.fill(contrasena, '\0');
        }
    }

    @FXML
    private void abrirRegistro() {
        try {
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/registro.fxml"));

            Stage ventana = new Stage();
            ventana.setTitle("Crear cuenta");
            ventana.initOwner(usernameField.getScene().getWindow());
            ventana.initModality(Modality.APPLICATION_MODAL);
            ventana.setResizable(false);
            ventana.setScene(new Scene(loader.load()));
            ventana.showAndWait();
        } catch (IOException | RuntimeException e) {
            mensajeLabel.setText("No se pudo abrir registro.fxml.");
        }
    }
}