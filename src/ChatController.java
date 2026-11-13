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
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.layout.VBox;
import javafx.scene.layout.BorderPane;
import javafx.scene.paint.Color;
import javafx.scene.control.ListCell;


public class ChatController {
    @FXML private WebView chatView;
    @FXML private TextField questionField;
    @FXML private Button sendButton;
    @FXML private ListView<ChatDAO.ChatResumen> historyList;
    @FXML private Label attachmentLabel;
    @FXML private BorderPane rootPane;
    @FXML private VBox headerBox;
    @FXML private VBox historyBox;
    @FXML private VBox inputArea;

    @FXML private Label appTitleLabel;
    @FXML private Label subtitleLabel;
    @FXML private Label historyTitleLabel;
    @FXML private Label footerLabel;

    @FXML private Button settingsButton;
    @FXML private Button newChatButton;
    @FXML private Button attachButton;
    @FXML private Button removeButton;

    private String idiomaActual = "Español";
    private String temaActual = "Oscuro";
    private String tonoIAActual = "Formal";


    private String fondoChatActual = "#141e2a";
    private String colorTextoChatActual = "#e8eef5";
    private String burbujaUsuarioActual = "#20384a";
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
    private void abrirPreferencias() {
        if (usuarioSesion == null) {
            return;
        }

        try {
            ConfiguracionDAO dao = new ConfiguracionDAO();
            ConfiguracionDAO.Preferencias actual =
                    dao.obtener(usuarioSesion.idConfig());

            ComboBox<String> idioma = new ComboBox<>();
            idioma.getItems().addAll("Español", "English", "Français");
            idioma.setValue(actual.idioma());

            ComboBox<String> tema = new ComboBox<>();
            tema.getItems().addAll("Claro", "Oscuro");
            tema.setValue(actual.tema());

            ComboBox<String> tono = new ComboBox<>();
            tono.getItems().addAll("Formal", "Informal");
            tono.setValue(actual.tonoIA());

            VBox formulario = new VBox(
                    8,
                    new Label("Idioma"),
                    idioma,
                    new Label("Tema"),
                    tema,
                    new Label("Tono de Gemini"),
                    tono
            );
            formulario.setStyle("-fx-padding: 16;");

            ButtonType guardar = new ButtonType(
                    "Guardar",
                    ButtonBar.ButtonData.OK_DONE
            );
            ButtonType cancelar = new ButtonType(
                    "Cancelar",
                    ButtonBar.ButtonData.CANCEL_CLOSE
            );

            Dialog<ButtonType> dialogo = new Dialog<>();
            dialogo.setTitle("Preferencias");
            dialogo.initOwner(chatView.getScene().getWindow());
            dialogo.getDialogPane().getButtonTypes().addAll(guardar, cancelar);
            dialogo.getDialogPane().setContent(formulario);

            dialogo.showAndWait().ifPresent(resultado -> {
                if (resultado == guardar) {
                    try {
                        dao.actualizar(
                                usuarioSesion.idConfig(),
                                idioma.getValue(),
                                tono.getValue(),
                                tema.getValue()
                        );

                        aplicarPreferencias(
                                idioma.getValue(),
                                tono.getValue(),
                                tema.getValue()
                        );

                        new Alert(
                                Alert.AlertType.INFORMATION,
                                "Preferencias guardadas."
                        ).showAndWait();

                    } catch (SQLException error) {
                        new Alert(
                                Alert.AlertType.ERROR,
                                "No se pudieron guardar las preferencias: "
                                        + error.getMessage()
                        ).showAndWait();
                    }
                }
            });

        } catch (SQLException error) {
            new Alert(
                    Alert.AlertType.ERROR,
                    "No se pudieron cargar las preferencias: "
                            + error.getMessage()
            ).showAndWait();
        }
    }

    private void aplicarPreferencias(
            String idioma,
            String tonoIA,
            String tema
    ) {
        idiomaActual = idioma;
        tonoIAActual = tonoIA;
        temaActual = tema;

        boolean oscuro = tema.equals("Oscuro");

        String fondo = oscuro ? "#101722" : "#edf3f5";
        String fondoCabecera = oscuro ? "#162331" : "#d9eeee";
        String fondoHistorial = oscuro ? "#172431" : "#ffffff";
        String colorTexto = oscuro ? "#f4f7fb" : "#172431";
        String fondoChat = oscuro ? "#141e2a" : "#f4f6f8";
        String burbujaUsuario = oscuro ? "#20384a" : "#d9eeee";

        rootPane.setStyle("-fx-background-color: " + fondo + ";");
        headerBox.setStyle("-fx-background-color: " + fondoCabecera + ";");
        historyBox.setStyle(
                "-fx-background-color: " + fondoHistorial
                        + "; -fx-padding: 12; -fx-background-radius: 10;"
        );
        inputArea.setStyle("-fx-background-color: " + fondo + ";");

        appTitleLabel.setStyle(
                "-fx-font-size: 26px; -fx-font-weight: bold; -fx-text-fill: "
                        + colorTexto + ";"
        );
        subtitleLabel.setStyle("-fx-text-fill: " + colorTexto + ";");
        historyTitleLabel.setStyle(
                "-fx-font-size: 15px; -fx-font-weight: bold; -fx-text-fill: "
                        + colorTexto + ";"
        );
        footerLabel.setStyle("-fx-text-fill: " + colorTexto + ";");
        attachmentLabel.setStyle("-fx-text-fill: " + colorTexto + ";");

        historyList.setCellFactory(lista -> new ListCell<>() {
            @Override
            protected void updateItem(ChatDAO.ChatResumen chat, boolean vacio) {
                super.updateItem(chat, vacio);
                setText(vacio || chat == null ? null : chat.toString());
                setTextFill(Color.web(colorTexto));
                setStyle("-fx-background-color: " + fondoHistorial + ";");
            }
        });

        switch (idioma) {
            case "English" -> {
                subtitleLabel.setText("Your Gemini assistant • Ask anything");
                historyTitleLabel.setText("My conversations");
                newChatButton.setText("＋ New chat");
                settingsButton.setText("Preferences");
                attachButton.setText("Attach file");
                removeButton.setText("Remove");
                sendButton.setText("Send");
                questionField.setPromptText("Write your question…");
                if (archivoSeleccionado == null) {
                    attachmentLabel.setText("No file attached");
                }
                footerLabel.setText(
                        "The API key is read from the Windows environment variables."
                );
            }
            case "Français" -> {
                subtitleLabel.setText("Votre assistant Gemini • Posez une question");
                historyTitleLabel.setText("Mes conversations");
                newChatButton.setText("＋ Nouveau chat");
                settingsButton.setText("Préférences");
                attachButton.setText("Joindre un fichier");
                removeButton.setText("Supprimer");
                sendButton.setText("Envoyer");
                questionField.setPromptText("Écrivez votre question…");
                if (archivoSeleccionado == null) {
                    attachmentLabel.setText("Aucun fichier joint");
                }
                footerLabel.setText(
                        "La clé API est lue depuis les variables d’environnement Windows."
                );
            }
            default -> {
                subtitleLabel.setText(
                        "Tu asistente con Gemini  •  Pregunta lo que quieras"
                );
                historyTitleLabel.setText("Mis conversaciones");
                newChatButton.setText("＋ Nuevo chat");
                settingsButton.setText("Preferencias");
                attachButton.setText("Adjuntar archivo");
                removeButton.setText("Quitar");
                sendButton.setText("Enviar");
                questionField.setPromptText("Escribe tu pregunta…");
                if (archivoSeleccionado == null) {
                    attachmentLabel.setText("Sin archivo adjunto");
                }
                footerLabel.setText(
                        "La clave API se lee desde las variables de entorno de Windows."
                );
            }
        }

        fondoChatActual = fondoChat;
        colorTextoChatActual = colorTexto;
        burbujaUsuarioActual = burbujaUsuario;
        renderMessages();
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
        String instruccionTono = tonoIAActual.equals("Informal")
                ? "Responde con un tono informal y cercano.\n\n"
                : "Responde con un tono formal y respetuoso.\n\n";

        String preguntaParaGemini = instruccionTono + pregunta;


        if (client == null || (textoEscrito.isEmpty() && archivo == null)) {
            return;
        }


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

        client.models.generateContent(
                "gemini-3.5-flash",
                preguntaParaGemini,
                null
        );
        Part.fromText(preguntaParaGemini);

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
        String fondoChat = fondoChatActual;
        String colorTextoChat = colorTextoChatActual;
        String burbujaUsuario = burbujaUsuarioActual;

        String plantillaHtml = """
        <!DOCTYPE html>
        <html><head><meta charset="UTF-8"><style>
        * { box-sizing: border-box; }
        body { margin: 0; padding: 18px 22px 28px;
               background: __FONDO_CHAT__;
               color: __COLOR_TEXTO__;
               font: 15px/1.65 'Segoe UI', Arial, sans-serif; }
        .message { margin: 0 auto 18px; max-width: 900px; }
        .user { margin-left: auto; max-width: 82%; padding: 12px 16px;
                border-radius: 16px 16px 4px 16px;
                background: __BURBUJA_USUARIO__; }
        .assistant { margin-right: auto; padding: 4px 8px 12px; }
        .role { margin-bottom: 5px; color: #38c5bd; font-size: 12px;
                font-weight: 700; letter-spacing: .04em;
                text-transform: uppercase; }
        p { margin: 7px 0 12px; }
        h1, h2, h3 { margin: 18px 0 8px; color: #f4f7fb; line-height: 1.3; }
        h1 { font-size: 1.5em; }
        h2 { font-size: 1.3em; }
        h3 { font-size: 1.12em; }
        ul, ol { padding-left: 25px; margin: 8px 0 14px; }
        li { margin: 3px 0; }
        strong { color: #fff; }
        a { color: #5ed6d0; }
        blockquote { margin: 12px 0; padding: 3px 13px;
                     border-left: 3px solid #18a6a6; color: #b8c7d6; }
        code { padding: 2px 5px; border-radius: 4px;
               background: #263545; color: #9ee7dd;
               font-family: Consolas, monospace; }
        pre { overflow-x: auto; padding: 13px 15px;
              border-radius: 9px; background: #0d141d; }
        pre code { padding: 0; background: transparent; color: #dce8f3; }
        hr { border: 0; border-top: 1px solid #344252; margin: 15px 0; }
        </style></head><body>
        """;

        StringBuilder html = new StringBuilder(
                plantillaHtml
                        .replace("__FONDO_CHAT__", fondoChat)
                        .replace("__COLOR_TEXTO__", colorTextoChat)
                        .replace("__BURBUJA_USUARIO__", burbujaUsuario)
        );

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

        try {
            ConfiguracionDAO.Preferencias preferencias =
                    new ConfiguracionDAO().obtener(usuarioSesion.idConfig());

            aplicarPreferencias(
                    preferencias.idioma(),
                    preferencias.tonoIA(),
                    preferencias.tema()
            );
        } catch (SQLException error) {
            new Alert(
                    Alert.AlertType.ERROR,
                    "No se pudieron cargar las preferencias: "
                            + error.getMessage()
            ).showAndWait();
        }

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