import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.Parent;
import javafx.stage.Stage;

import java.io.IOException;

public class Main extends Application {
    @Override
    public void start(Stage stage) throws IOException {
        FXMLLoader loader = new FXMLLoader(
                Main.class.getResource("/login.fxml"));

        Parent login = loader.load();

        LoginController controller = loader.getController();
        controller.setAlIniciarSesion(usuario -> abrirChat(stage, usuario));

        stage.setTitle("MarIA · Iniciar sesión");
        stage.setScene(new Scene(login));
        stage.setResizable(false);
        stage.show();
    }

    private void abrirChat(Stage stage, UsuarioSesion usuario) {
        try {
            FXMLLoader loader = new FXMLLoader(
                    Main.class.getResource("/interfaz.fxml"));

            Parent chat = loader.load();

            ChatController controller = loader.getController();
            controller.setUsuarioSesion(usuario);

            stage.setTitle("MarIA · " + usuario.username());
            stage.setResizable(true);
            stage.setScene(new Scene(chat));
            stage.centerOnScreen();
        } catch (IOException | RuntimeException e) {
            e.printStackTrace();
            new Alert(Alert.AlertType.ERROR,
                    "No se pudo abrir la ventana de MarIA.").showAndWait();
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}
