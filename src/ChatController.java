import com.google.genai.Client;
import com.google.genai.types.GenerateContentResponse;
import com.google.genai.types.HttpOptions;
import com.google.genai.types.HttpRetryOptions;
import javafx.application.Platform;
import javafx.concurrent.Worker;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.TextField;
import javafx.scene.web.WebView;
import javafx.stage.Modality;
import javafx.stage.Stage;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;
import javafx.scene.control.ListView;
import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import com.google.genai.types.Content;
import com.google.genai.types.Part;
import javafx.scene.control.Label;
import javafx.stage.FileChooser;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.nio.file.Path;
import java.util.Locale;


public class ChatController {
    @FXML private WebView chatView;
    @FXML private TextField questionField;
    @FXML private Button sendButton;
    @FXML private ListView<ChatDAO.ChatResumen> historyList;
    @FXML private Label attachmentLabel;

    private Path archivoSeleccionado;
    private static final long MAX_BYTES_ARCHIVO = 10L * 1024 * 1024;


    private int versionCargaChat;
    private Client client;
    private final ChatDAO chatDAO = new ChatDAO();
    private String idChatActual;
    private UsuarioSesion usuarioSesion;

    private final List<ChatMessage> messages = new ArrayList<>();
    private final Parser markdownParser = Parser.builder().build();
    private final HtmlRenderer markdownRenderer =
            HtmlRenderer.builder()
                    .escapeHtml(true)
                    .sanitizeUrls(true)
                    .build();

    @FXML
    private void initialize() {
        historyList.getSelectionModel()
                .selectedItemProperty()
                .addListener((observable, anterior, seleccion) -> {
                    if (seleccion != null
                            && !seleccion.idChat().equals(idChatActual)) {
                        cargarChat(seleccion);
                    }
                });

        chatView.getEngine().getLoadWorker().stateProperty().addListener(
                (observable, anterior, estado) -> {
                    if (estado == Worker.State.SUCCEEDED) {
                        chatView.getEngine().executeScript(
                                "window.scrollTo(0, document.body.scrollHeight);"
                        );
                    }
                }
        );

        String apiKey = System.getenv("GOOGLE_API_KEY");
        if (apiKey == null || apiKey.isBlank()) {
            apiKey = System.getenv("GEMINI_API_KEY");
        }

        if (apiKey == null || apiKey.isBlank()) {
            messages.add(new ChatMessage(
                    "assistant",
                    "No se encontró la API key. Configura `GOOGLE_API_KEY` " +
                            "en Run → Edit Configurations → Environment variables " +
                            "y vuelve a iniciar la aplicación."
            ));
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

        messages.add(new ChatMessage(
                "assistant",
                "¡Hola! Soy MarIA. Escribe una pregunta y te responderé " +
                        "con formato: **negritas**, listas, títulos y bloques de código."
        ));
        renderMessages();
    }

    private String obtenerExtension(Path archivo) {
        String nombre = archivo.getFileName()
                .toString()
                .toLowerCase(Locale.ROOT);

        int punto = nombre.lastIndexOf('.');
        if (punto < 0) {
            throw new IllegalArgumentException(
                    "El archivo debe tener una extensión."
            );
        }

        String extension = nombre.substring(punto);

        if (extension.equals(".jpeg")) {
            return ".jpg";
        }

        if (extension.equals(".txt")
                || extension.equals(".pdf")
                || extension.equals(".png")
                || extension.equals(".jpg")) {
            return extension;
        }

        throw new IllegalArgumentException(
                "Solo se permiten archivos TXT, PDF, PNG o JPG."
        );
    }

    private String obtenerMimeType(Path archivo) {
        return switch (obtenerExtension(archivo)) {
            case ".txt" -> "text/plain";
            case ".pdf" -> "application/pdf";
            case ".png" -> "image/png";
            case ".jpg" -> "image/jpeg";
            default -> throw new IllegalArgumentException(
                    "Tipo de archivo no compatible."
            );
        };
    }

    @FXML
    private void sendQuestion() {
        String textoEscrito = questionField.getText().trim();
        Path archivo = archivoSeleccionado;

        if (client == null || (textoEscrito.isEmpty() && archivo == null)) {
            return;
        }

        String pregunta = textoEscrito.isEmpty()
                ? "Analiza el archivo adjunto."
                : textoEscrito;

        String textoVisible = pregunta;
        if (archivo != null) {
            textoVisible += "\n\n📎 " + archivo.getFileName();
        }

        questionField.clear();
        questionField.setDisable(true);
        sendButton.setDisable(true);
        archivoSeleccionado = null;
        attachmentLabel.setText("Sin archivo adjunto");

        messages.add(new ChatMessage("user", textoVisible));
        ChatMessage pendingAnswer =
                new ChatMessage("assistant", "*Consultando Gemini…*");
        messages.add(pendingAnswer);
        renderMessages();

        CompletableFuture.supplyAsync(() -> {
            try {
                if (usuarioSesion == null) {
                    throw new IllegalStateException(
                            "No hay una sesión de usuario activa."
                    );
                }

                if (idChatActual == null) {
                    idChatActual = chatDAO.crearChat(
                            usuarioSesion.idUsuario(),
                            pregunta
                    );
                }

                GenerateContentResponse response;

                if (archivo == null) {
                    chatDAO.guardarMensaje(
                            idChatActual,
                            "USUARIO",
                            "IA",
                            pregunta
                    );

                    response = client.models.generateContent(
                            "gemini-3.5-flash-lite",
                            pregunta,
                            null
                    );
                } else {
                    byte[] bytes = Files.readAllBytes(archivo);
                    String mimeType = obtenerMimeType(archivo);
                    String extension = obtenerExtension(archivo);
                    String nombreArchivo = archivo.getFileName().toString();

                    chatDAO.guardarMensajeConArchivo(
                            idChatActual,
                            "USUARIO",
                            "IA",
                            pregunta,
                            nombreArchivo,
                            mimeType,
                            extension,
                            bytes
                    );

                    Content contenido;

                    if (mimeType.equals("text/plain")) {
                        String textoArchivo =
                                new String(bytes, StandardCharsets.UTF_8);

                        contenido = Content.fromParts(
                                Part.fromText(pregunta),
                                Part.fromText(
                                        "Contenido del archivo "
                                                + nombreArchivo
                                                + ":\n"
                                                + textoArchivo
                                )
                        );
                    } else {
                        contenido = Content.fromParts(
                                Part.fromText(pregunta),
                                Part.fromBytes(bytes, mimeType)
                        );
                    }

                    response = client.models.generateContent(
                            "gemini-3.5-flash-lite",
                            contenido,
                            null
                    );
                }

                String respuesta = response.text();

                if (respuesta != null && !respuesta.isBlank()) {
                    chatDAO.guardarMensaje(
                            idChatActual,
                            "GEMINI",
                            "US",
                            respuesta
                    );
                }

                return respuesta;

            } catch (SQLException | IOException error) {
                throw new CompletionException(error);
            }
        }).whenComplete((answer, error) -> Platform.runLater(() -> {
            questionField.setDisable(false);
            sendButton.setDisable(false);
            questionField.requestFocus();

            if (error != null) {
                pendingAnswer.text =
                        "No se pudo completar o guardar la consulta: "
                                + rootMessage(error);
            } else {
                pendingAnswer.text =
                        answer == null || answer.isBlank()
                                ? "Gemini no devolvió texto."
                                : answer;
            }

            renderMessages();
            cargarConversaciones();
        }));
    }

    @FXML
    private void adjuntarArchivo() {
        FileChooser selector = new FileChooser();
        selector.setTitle("Seleccionar archivo");

        selector.getExtensionFilters().add(
                new FileChooser.ExtensionFilter(
                        "Archivos compatibles",
                        "*.txt", "*.pdf", "*.png", "*.jpg", "*.jpeg"
                )
        );

        File archivo = selector.showOpenDialog(
                questionField.getScene().getWindow()
        );

        if (archivo == null) {
            return;
        }

        try {
            long tamano = Files.size(archivo.toPath());

            if (tamano > MAX_BYTES_ARCHIVO) {
                mostrarAviso("El archivo supera el límite de 10 MiB.");
                return;
            }

            if (archivo.getName().length() > 100) {
                mostrarAviso(
                        "El nombre del archivo debe tener 100 caracteres o menos."
                );
                return;
            }

            obtenerExtension(archivo.toPath());
            archivoSeleccionado = archivo.toPath();
            attachmentLabel.setText("📎 " + archivo.getName());

        } catch (IOException | IllegalArgumentException error) {
            mostrarAviso("No se pudo leer ese archivo: " + error.getMessage());
        }
    }

    @FXML
    private void quitarArchivo() {
        archivoSeleccionado = null;
        attachmentLabel.setText("Sin archivo adjunto");
    }

    private void mostrarAviso(String mensaje) {
        Alert alerta = new Alert(Alert.AlertType.WARNING);
        alerta.setTitle("Archivo adjunto");
        alerta.setHeaderText(null);
        alerta.setContentText(mensaje);
        alerta.showAndWait();
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
                html.append(
                                "<div class='message user'><div class='role'>Tú</div><div>"
                        )
                        .append(
                                escapeHtml(message.text)
                                        .replace("\n", "<br>")
                        )
                        .append("</div></div>");
            } else {
                html.append(
                                "<div class='message assistant'>" +
                                        "<div class='role'>Gemini</div><div class='content'>"
                        )
                        .append(
                                markdownRenderer.render(
                                        markdownParser.parse(message.text)
                                )
                        )
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
        while (root.getCause() != null) {
            root = root.getCause();
        }

        return root.getMessage() == null
                ? root.getClass().getSimpleName()
                : root.getMessage();
    }

    @FXML
    private void nuevoChat() {
        versionCargaChat++;
        idChatActual = null;
        archivoSeleccionado = null;
        attachmentLabel.setText("Sin archivo adjunto");

        historyList.getSelectionModel().clearSelection();

        questionField.setDisable(false);
        sendButton.setDisable(false);

        messages.clear();
        messages.add(new ChatMessage(
                "assistant",
                "¡Hola! Soy MarIA. Escribe una pregunta y te responderé."
        ));

        renderMessages();
    }

    private void cargarConversaciones() {
        if (usuarioSesion == null) {
            return;
        }

        String idUsuario = usuarioSesion.idUsuario();
        String idChatSeleccionado = idChatActual;

        CompletableFuture.supplyAsync(() -> {
            try {
                return chatDAO.listarChats(idUsuario);
            } catch (SQLException error) {
                throw new CompletionException(error);
            }
        }).whenComplete((chats, error) -> Platform.runLater(() -> {
            if (error != null) {
                messages.add(new ChatMessage(
                        "assistant",
                        "No se pudo cargar el historial: " + rootMessage(error)
                ));
                renderMessages();
                return;
            }

            historyList.getItems().setAll(chats);

            if (idChatSeleccionado != null) {
                for (int i = 0; i < chats.size(); i++) {
                    if (chats.get(i).idChat().equals(idChatSeleccionado)) {
                        historyList.getSelectionModel().select(i);
                        break;
                    }
                }
            }
        }));
    }

    private void cargarChat(ChatDAO.ChatResumen chat) {
        if (usuarioSesion == null) {
            return;
        }

        int version = ++versionCargaChat;
        String idUsuario = usuarioSesion.idUsuario();

        questionField.setDisable(true);
        sendButton.setDisable(true);

        CompletableFuture.supplyAsync(() -> {
            try {
                return chatDAO.listarMensajes(chat.idChat(), idUsuario);
            } catch (SQLException error) {
                throw new CompletionException(error);
            }
        }).whenComplete((mensajesGuardados, error) ->
                Platform.runLater(() -> {
                    if (version != versionCargaChat) {
                        return;
                    }

                    questionField.setDisable(false);
                    sendButton.setDisable(false);

                    if (error != null) {
                        messages.add(new ChatMessage(
                                "assistant",
                                "No se pudo abrir esta conversación: "
                                        + rootMessage(error)
                        ));
                        renderMessages();
                        return;
                    }

                    idChatActual = chat.idChat();
                    messages.clear();

                    for (ChatDAO.MensajeGuardado mensaje : mensajesGuardados) {
                        boolean esUsuario =
                                mensaje.emisor().equalsIgnoreCase("USUARIO");

                        messages.add(new ChatMessage(
                                esUsuario ? "user" : "assistant",
                                mensaje.contenido()
                        ));
                    }

                    renderMessages();
                })
        );
    }

    @FXML
    private void abrirRegistro() {
        try {
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/registro.fxml")
            );

            Stage ventana = new Stage();
            ventana.setTitle("Crear cuenta");
            ventana.initOwner(chatView.getScene().getWindow());
            ventana.initModality(Modality.APPLICATION_MODAL);
            ventana.setResizable(false);
            ventana.setScene(new Scene(loader.load()));
            ventana.showAndWait();

        } catch (IOException | RuntimeException error) {
            Alert alerta = new Alert(Alert.AlertType.ERROR);
            alerta.setTitle("Error");
            alerta.setHeaderText("No se pudo abrir el registro");
            alerta.setContentText(
                    "Revisa que registro.fxml esté en src/main/resources."
            );
            alerta.showAndWait();
        }
    }

    public void setUsuarioSesion(UsuarioSesion usuarioSesion) {
        this.usuarioSesion = usuarioSesion;
        cargarConversaciones();
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