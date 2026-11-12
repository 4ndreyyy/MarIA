import com.google.genai.Client;
import com.google.genai.types.GenerateContentResponse;
import com.google.genai.types.HttpOptions;
import com.google.genai.types.HttpRetryOptions;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.TextField;
import javafx.scene.web.WebView;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class ChatController {
    @FXML private WebView chatView;
    @FXML private TextField questionField;
    @FXML private Button sendButton;

    private Client client;
    private final List<ChatMessage> messages = new ArrayList<>();
    private final Parser markdownParser = Parser.builder().build();
    private final HtmlRenderer markdownRenderer = HtmlRenderer.builder()
            .escapeHtml(true)
            .sanitizeUrls(true)
            .build();

    @FXML
    private void initialize() {
        String apiKey = System.getenv("GOOGLE_API_KEY");
        if (apiKey == null || apiKey.isBlank()) {
            apiKey = System.getenv("GEMINI_API_KEY");
        }

        if (apiKey == null || apiKey.isBlank()) {
            messages.add(new ChatMessage("assistant",
                    "No se encontró la API key. Configura `GOOGLE_API_KEY` en Run → Edit Configurations → Environment variables y vuelve a iniciar la aplicación."));
            questionField.setDisable(true);
            sendButton.setDisable(true);
            renderMessages();
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

        messages.add(new ChatMessage("assistant",
                "¡Hola! Soy MarIA. Escribe una pregunta y te responderé con formato: **negritas**, listas, títulos y bloques de código."));
        renderMessages();
    }

    @FXML
    private void sendQuestion() {
        String question = questionField.getText().trim();
        if (question.isEmpty() || client == null) return;

        questionField.clear();
        questionField.setDisable(true);
        sendButton.setDisable(true);

        messages.add(new ChatMessage("user", question));
        ChatMessage pendingAnswer =
                new ChatMessage("assistant", "*Consultando Gemini…*");
        messages.add(pendingAnswer);
        renderMessages();

        CompletableFuture.supplyAsync(() -> {
            GenerateContentResponse response = client.models.generateContent(
                    "gemini-3.5-flash-lite", question, null);
            return response.text();
        }).whenComplete((answer, error) -> Platform.runLater(() -> {
            questionField.setDisable(false);
            sendButton.setDisable(false);
            questionField.requestFocus();

            if (error != null) {
                pendingAnswer.text =
                        "No se pudo obtener la respuesta: " + rootMessage(error);
            } else {
                pendingAnswer.text = answer == null || answer.isBlank()
                        ? "Gemini no devolvió texto."
                        : answer;
            }
            renderMessages();
        }));
    }

    private void renderMessages() {
        StringBuilder html = new StringBuilder("""
                <!DOCTYPE html>
                <html><head><meta charset="UTF-8"><style>
                * { box-sizing: border-box; }
                body { margin: 0; padding: 18px 22px 28px; background: #141e2a;
                       color: #e8eef5; font: 15px/1.65 'Segoe UI', Arial, sans-serif; }
                .message { margin: 0 auto 18px; max-width: 900px; }
                .user { margin-left: auto; max-width: 82%; padding: 12px 16px;
                        border-radius: 16px 16px 4px 16px; background: #20384a; }
                .assistant { margin-right: auto; padding: 4px 8px 12px; }
                .role { margin-bottom: 5px; color: #38c5bd; font-size: 12px;
                        font-weight: 700; letter-spacing: .04em; text-transform: uppercase; }
                p { margin: 7px 0 12px; }
                h1, h2, h3 { margin: 18px 0 8px; color: #f4f7fb; line-height: 1.3; }
                h1 { font-size: 1.5em; } h2 { font-size: 1.3em; } h3 { font-size: 1.12em; }
                ul, ol { padding-left: 25px; margin: 8px 0 14px; }
                li { margin: 3px 0; }
                strong { color: #fff; }
                a { color: #5ed6d0; }
                blockquote { margin: 12px 0; padding: 3px 13px; border-left: 3px solid #18a6a6; color: #b8c7d6; }
                code { padding: 2px 5px; border-radius: 4px; background: #263545; color: #9ee7dd; font-family: Consolas, monospace; }
                pre { overflow-x: auto; padding: 13px 15px; border-radius: 9px; background: #0d141d; }
                pre code { padding: 0; background: transparent; color: #dce8f3; }
                hr { border: 0; border-top: 1px solid #344252; margin: 15px 0; }
                </style></head><body>
                """);

        for (ChatMessage message : messages) {
            if (message.role.equals("user")) {
                html.append("<div class='message user'><div class='role'>Tú</div><div>")
                        .append(escapeHtml(message.text).replace("\n", "<br>"))
                        .append("</div></div>");
            } else {
                html.append("<div class='message assistant'><div class='role'>Gemini</div><div class='content'>")
                        .append(markdownRenderer.render(
                                markdownParser.parse(message.text)))
                        .append("</div></div>");
            }
        }

        html.append("</body></html>");
        chatView.getEngine().loadContent(html.toString(), "text/html");
    }

    private String escapeHtml(String text) {
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    private String rootMessage(Throwable error) {
        Throwable root = error;
        while (root.getCause() != null) root = root.getCause();
        return root.getMessage() == null
                ? root.getClass().getSimpleName()
                : root.getMessage();
    }

    private static final class ChatMessage {
        private final String role;
        private String text;

        private ChatMessage(String role, String text) {
            this.role = role;
            this.text = text;
        }
    }
}