import com.google.genai.Client;
import com.google.genai.types.GenerateContentResponse;
import com.google.genai.types.HttpOptions;
import com.google.genai.types.HttpRetryOptions;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;

import java.util.concurrent.CompletableFuture;

public class ChatController {
    @FXML private TextArea chatArea;
    @FXML private TextField questionField;
    @FXML private Button sendButton;

    private Client client;

    @FXML
    private void initialize() {
        chatArea.setEditable(false);
        chatArea.setWrapText(true);

        String apiKey = System.getenv("GOOGLE_API_KEY");
        if (apiKey == null || apiKey.isBlank()) {
            apiKey = System.getenv("GEMINI_API_KEY");
        }

        if (apiKey == null || apiKey.isBlank()) {
            chatArea.setText("No se encontró la API key. Configura GOOGLE_API_KEY en Run → Edit Configurations → Environment variables y vuelve a iniciar la aplicación.");
            questionField.setDisable(true);
            sendButton.setDisable(true);
            return;
        }

        client = Client.builder()
                .apiKey(apiKey)
                .httpOptions(HttpOptions.builder()
                        .timeout(120_000)
                        .retryOptions(HttpRetryOptions.builder()
                                .attempts(3)
                                .httpStatusCodes(503, 504))
                        .build())
                .build();
    }

    @FXML
    private void sendQuestion() {
        String question = questionField.getText().trim();
        if (question.isEmpty() || client == null) return;

        questionField.clear();
        questionField.setDisable(true);
        sendButton.setDisable(true);
        chatArea.appendText("Tú:\n" + question + "\n\nGemini:\n");
        int answerStart = chatArea.getLength();
        chatArea.appendText("Consultando…\n\n");
        chatArea.positionCaret(chatArea.getLength());

        CompletableFuture.supplyAsync(() -> {
            GenerateContentResponse response = client.models.generateContent(
                    "gemini-3.5-flash-lite", question, null);
            return response.text();
        }).whenComplete((answer, error) -> Platform.runLater(() -> {
            questionField.setDisable(false);
            sendButton.setDisable(false);
            questionField.requestFocus();

            chatArea.deleteText(answerStart, chatArea.getLength());
            if (error != null) {
                chatArea.appendText("No se pudo obtener la respuesta: " + rootMessage(error) + "\n\n");
            } else {
                chatArea.appendText((answer == null || answer.isBlank()
                        ? "Gemini no devolvió texto."
                        : answer) + "\n\n");
            }
            chatArea.positionCaret(chatArea.getLength());
        }));
    }

    private String rootMessage(Throwable error) {
        Throwable root = error;
        while (root.getCause() != null) root = root.getCause();
        return root.getMessage() == null ? root.getClass().getSimpleName() : root.getMessage();
    }
}