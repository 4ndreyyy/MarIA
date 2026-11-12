import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class Main extends Application {
    @Override
    public void start(Stage stage) throws Exception {
        FXMLLoader loader = new FXMLLoader(Main.class.getResource("/interfaz.fxml"));
        Scene scene = new Scene(loader.load());

        stage.setTitle("MarIA · Chat con Gemini");
        stage.setMinWidth(560);
        stage.setMinHeight(440);
        stage.setScene(scene);
        stage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
