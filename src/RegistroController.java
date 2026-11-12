import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;

import java.sql.SQLException;
import java.util.Arrays;

public class RegistroController {
    @FXML private TextField usernameField;
    @FXML private TextField nombreField;
    @FXML private TextField apellidosField;
    @FXML private TextField correoField;
    @FXML private PasswordField passwordField;
    @FXML private Label mensajeLabel;

    @FXML
    private void registrar() {
        char[] contrasena = passwordField.getText().toCharArray();

        try {
            UsuarioDAO usuarioDAO = new UsuarioDAO();
            usuarioDAO.registrar(
                    usernameField.getText(),
                    nombreField.getText(),
                    apellidosField.getText(),
                    correoField.getText(),
                    contrasena,
                    "BAS1"
            );

            mensajeLabel.setText("Usuario registrado correctamente.");
            usernameField.clear();
            nombreField.clear();
            apellidosField.clear();
            correoField.clear();
            passwordField.clear();

        } catch (IllegalArgumentException e) {
            mensajeLabel.setText(e.getMessage());

        } catch (SQLException e) {
            e.printStackTrace();

            if (e.getErrorCode() == 1) {
                mensajeLabel.setText(
                        "Ese nombre de usuario o correo ya está registrado.");
            } else {
                mensajeLabel.setText(
                        "Error Oracle " + e.getErrorCode() + ": " + e.getMessage());
            }
        } finally {
            Arrays.fill(contrasena, '\0');
        }
    }
}