import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

import java.io.IOException;

public class Main extends Application {

    @Override
    public void start(Stage stage) throws IOException {
        // Login con la barra normal de Windows.
        stage.initStyle(StageStyle.DECORATED);
        stage.setFullScreen(false);

        FXMLLoader loader = new FXMLLoader(
                Main.class.getResource("/login.fxml")
        );

        Parent login = loader.load();

        LoginController controller = loader.getController();
        controller.setAlIniciarSesion(
                usuario -> abrirChat(stage, usuario)
        );

        stage.setTitle("MarIA · Iniciar sesión");
        stage.setScene(new Scene(login));
        stage.setResizable(false);
        stage.show();
    }

    private void abrirChat(Stage ventanaLogin, UsuarioSesion usuario) {
        try {
            FXMLLoader loader = new FXMLLoader(
                    Main.class.getResource("/interfaz.fxml")
            );

            Parent chat = loader.load();

            ChatController controller = loader.getController();
            controller.setUsuarioSesion(usuario);

            // Ventana independiente con botones nativos de Windows.
            Stage ventanaChat = new Stage(StageStyle.UNDECORATED);
            ventanaChat.setTitle("MarIA · " + usuario.username());
            ventanaChat.setResizable(true);
            ventanaChat.setFullScreen(false);
            ventanaChat.setMaximized(false);
            ventanaChat.setMinWidth(940);
            ventanaChat.setMinHeight(680);
            ventanaChat.setScene(new Scene(chat, 1080, 700));
            ventanaChat.setWidth(1120);
            ventanaChat.setHeight(700);
            ventanaChat.show();
            ventanaChat.centerOnScreen();

            ventanaLogin.close();

        } catch (IOException | RuntimeException error) {
            error.printStackTrace();

            new Alert(
                    Alert.AlertType.ERROR,
                    "No se pudo abrir la ventana de MarIA:\n"
                            + error.getMessage()
            ).showAndWait();
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}