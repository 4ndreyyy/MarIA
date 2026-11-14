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
import com.google.genai.types.GenerateContentConfig;
import com.google.genai.types.Tool;
import java.lang.reflect.Method;
import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.util.Duration;
import javafx.scene.paint.Color;
import javafx.scene.control.ListCell;
import javafx.scene.Node;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import netscape.javascript.JSObject;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import java.util.concurrent.atomic.AtomicReference;





public class ChatController {
    @FXML
    private WebView chatView;
    @FXML
    private TextField questionField;
    @FXML
    private Button microphoneButton;
    @FXML
    private Button sendButton;
    @FXML
    private ListView<ChatDAO.ChatResumen> historyList;
    @FXML
    private Label attachmentLabel;
    @FXML
    private BorderPane rootPane;
    @FXML
    private VBox headerBox;
    @FXML
    private VBox historyBox;
    @FXML
    private VBox inputArea;

    @FXML
    private Button maximizeButton;
    @FXML
    private HBox windowTitleBar;

    private double arrastreOffsetX;
    private double arrastreOffsetY;
    @FXML
    private Label appTitleLabel;
    @FXML
    private Label subtitleLabel;
    @FXML
    private Label historyTitleLabel;
    @FXML
    private Label footerLabel;

    @FXML
    private Button historyToggleButton;
    @FXML
    private Label modeLabel;
    @FXML
    private Label recentLabel;

    private boolean historialVisible = true;
    private Timeline animacionHistorial;

    @FXML
    private ComboBox<String> specialtyCombo;
    @FXML
    private Button settingsButton;
    @FXML
    private Button newChatButton;
    @FXML
    private Button attachButton;
    @FXML
    private Button removeButton;

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


    private final AudioRecorder audioRecorder = new AudioRecorder();
    private final AtomicReference<Clip> activeAudioClip =
            new AtomicReference<>();
    private final List<ChatMessage> messages = new ArrayList<>();
    private final Parser markdownParser = Parser.builder().build();
    private final HtmlRenderer markdownRenderer =
            HtmlRenderer.builder()
                    .escapeHtml(true)
                    .sanitizeUrls(true)
                    .build();

    @FXML
    private void initialize() {
        if (!rootPane.getStyleClass().contains("theme-dark")) {
            rootPane.getStyleClass().add("theme-dark");
        }
        estilizarCombo(specialtyCombo);
        specialtyCombo.getItems().setAll("General", "Turismo");
        specialtyCombo.setValue("General");

        actualizarEstiloEspecialidad("General");

        specialtyCombo.valueProperty().addListener(
                (observable, anterior, actual) ->
                        actualizarEstiloEspecialidad(actual)
        );

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
                        JSObject window = (JSObject) chatView.getEngine()
                                .executeScript("window");

                        window.setMember("mariaAudio", new AudioBridge());
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
                        .apiVersion("v1beta")
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

    @FXML
    private void minimizarVentana() {
        ventanaActual().setIconified(true);
    }

    @FXML
    private void alternarMaximizarVentana() {
        Stage ventana = ventanaActual();

        boolean maximizada = !ventana.isMaximized();
        ventana.setMaximized(maximizada);
        maximizeButton.setText(maximizada ? "❐" : "□");
    }

    @FXML
    private void cerrarVentana() {
        ventanaActual().close();
    }

    @FXML
    private void iniciarArrastreVentana(MouseEvent evento) {
        if (eventoEnBotonDeTitulo(evento)) {
            return;
        }

        if (evento.getClickCount() == 2) {
            alternarMaximizarVentana();
            return;
        }

        Stage ventana = ventanaActual();

        if (!ventana.isMaximized()) {
            arrastreOffsetX = evento.getScreenX() - ventana.getX();
            arrastreOffsetY = evento.getScreenY() - ventana.getY();
        }
    }

    @FXML
    private void arrastrarVentana(MouseEvent evento) {
        if (eventoEnBotonDeTitulo(evento)) {
            return;
        }

        Stage ventana = ventanaActual();

        if (!ventana.isMaximized()) {
            ventana.setX(evento.getScreenX() - arrastreOffsetX);
            ventana.setY(evento.getScreenY() - arrastreOffsetY);
        }
    }

    private boolean eventoEnBotonDeTitulo(MouseEvent evento) {
        if (!(evento.getTarget() instanceof Node objetivo)) {
            return false;
        }

        Node nodo = objetivo;

        while (nodo != null && nodo != windowTitleBar) {
            if (nodo instanceof Button) {
                return true;
            }
            nodo = nodo.getParent();
        }

        return false;
    }

    private Stage ventanaActual() {
        return (Stage) rootPane.getScene().getWindow();
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
            estilizarCombo(idioma);

            ComboBox<String> tema = new ComboBox<>();
            tema.getItems().addAll("Claro", "Oscuro");
            tema.setValue(actual.tema());
            estilizarCombo(tema);

            ComboBox<String> tono = new ComboBox<>();
            tono.getItems().addAll("Formal", "Informal");
            tono.setValue(actual.tonoIA());
            estilizarCombo(tono);

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
            var panelDialogo = dialogo.getDialogPane();
            aplicarEstiloDialogo(panelDialogo, tema.getValue());

            tema.valueProperty().addListener((observable, anterior, nuevoTema) -> {
                aplicarEstiloDialogo(panelDialogo, nuevoTema);
                estilizarCombo(idioma, nuevoTema);
                estilizarCombo(tema, nuevoTema);
                estilizarCombo(tono, nuevoTema);
            });
            Button borrarCuenta = new Button("Eliminar mi cuenta");
            borrarCuenta.getStyleClass().add("danger-button");
            borrarCuenta.setOnAction(evento -> {
                dialogo.close();
                eliminarCuenta();
            });
            formulario.getChildren().add(borrarCuenta);

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

        rootPane.getStyleClass().removeAll("theme-dark", "theme-light");
        rootPane.getStyleClass().add(oscuro ? "theme-dark" : "theme-light");
        estilizarCombo(specialtyCombo);

        String fondoChat = oscuro ? "#0e1725" : "#f7f9fa";
        String colorTexto = oscuro ? "#e8eef5" : "#243744";
        String burbujaUsuario = oscuro ? "#1d3448" : "#e1efee";

        historyList.setCellFactory(lista -> new ListCell<>() {
            @Override
            protected void updateItem(ChatDAO.ChatResumen chat, boolean vacio) {
                super.updateItem(chat, vacio);
                setStyle("");

                if (vacio || chat == null) {
                    setText(null);
                    setGraphic(null);
                } else {
                    Label textoChat = new Label(chat.toString());
                    textoChat.setWrapText(true);
                    textoChat.setMaxWidth(200);
                    textoChat.setTextFill(Color.web(colorTexto));

                    setText(null);
                    setGraphic(textoChat);
                }
            }
        });

        switch (idioma) {
            case "English" -> {
                subtitleLabel.setText("Your Gemini assistant • Ask anything");
                historyTitleLabel.setText("My conversations");
                modeLabel.setText("ACTIVE MODE");
                recentLabel.setText("RECENT");
                newChatButton.setText("＋ New chat");
                settingsButton.setText("⚙  Preferences");
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
                modeLabel.setText("MODE ACTIF");
                recentLabel.setText("RÉCENTS");
                newChatButton.setText("＋ Nouveau chat");
                settingsButton.setText("⚙  Préférences");
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
                modeLabel.setText("MODO ACTIVO");
                recentLabel.setText("RECIENTES");
                newChatButton.setText("＋ Nuevo chat");
                settingsButton.setText("⚙  Preferencias");
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

    private void aplicarEstiloDialogo(
            javafx.scene.control.DialogPane panel,
            String tema
    ) {
        var hojaEstilos = getClass().getResource("/marIA.css");

        if (hojaEstilos != null) {
            String url = hojaEstilos.toExternalForm();

            if (!panel.getStylesheets().contains(url)) {
                panel.getStylesheets().add(url);
            }
        }

        panel.getStyleClass().remove("maria-dialog-dark");
        panel.getStyleClass().remove("maria-dialog-light");

        panel.getStyleClass().add(
                "Oscuro".equals(tema)
                        ? "maria-dialog-dark"
                        : "maria-dialog-light"
        );
    }

    private void estilizarCombo(ComboBox<String> combo) {
        estilizarCombo(combo, temaActual);
    }

    private void estilizarCombo(ComboBox<String> combo, String tema) {
        boolean oscuro = "Oscuro".equals(tema);

        String fondoOpciones = oscuro ? "#1b2b3d" : "#edf2f4";
        String fondoSeleccion = oscuro ? "#2b465c" : "#d7e5e9";
        String colorTexto = oscuro ? "#e8f1f5" : "#263946";

        combo.setCellFactory(lista -> new ListCell<>() {
            @Override
            protected void updateItem(String item, boolean vacio) {
                super.updateItem(item, vacio);

                setText(vacio || item == null ? null : item);
                setTextFill(Color.web(colorTexto));

                String fondo = isSelected()
                        ? fondoSeleccion
                        : fondoOpciones;

                setStyle(
                        "-fx-background-color: " + fondo + ";"
                                + " -fx-padding: 8 10 8 10;"
                );
            }
        });

        combo.setButtonCell(new ListCell<>() {
            @Override
            protected void updateItem(String item, boolean vacio) {
                super.updateItem(item, vacio);

                setText(vacio || item == null ? null : item);
                setTextFill(Color.web(colorTexto));
                setStyle("-fx-background-color: transparent;");
            }
        });
    }

    private void actualizarEstiloEspecialidad(String especialidad) {
        if (rootPane == null) {
            return;
        }

        rootPane.getStyleClass().remove("tourism-mode");

        if ("Turismo".equals(especialidad)) {
            rootPane.getStyleClass().add("tourism-mode");
        }
    }

    @FXML
    private void alternarHistorial() {
        if (historyBox == null) {
            return;
        }

        boolean mostrar = !historialVisible;
        historialVisible = mostrar;

        if (animacionHistorial != null) {
            animacionHistorial.stop();
        }

        if (mostrar) {
            historyBox.setManaged(true);
            historyBox.setVisible(true);
            historyBox.setMinWidth(0);
            historyBox.setMaxWidth(Double.MAX_VALUE);
            historyBox.setPrefWidth(0);
            historyBox.setOpacity(0);
        } else {
            historyBox.setMinWidth(0);
            historyBox.setMaxWidth(Double.MAX_VALUE);
        }

        double anchoInicial = historyBox.getPrefWidth();
        double opacidadInicial = historyBox.getOpacity();
        double anchoFinal = mostrar ? 250 : 0;
        double opacidadFinal = mostrar ? 1 : 0;

        animacionHistorial = new Timeline(
                new KeyFrame(
                        Duration.ZERO,
                        new KeyValue(historyBox.prefWidthProperty(), anchoInicial),
                        new KeyValue(historyBox.opacityProperty(), opacidadInicial)
                ),
                new KeyFrame(
                        Duration.millis(220),
                        new KeyValue(
                                historyBox.prefWidthProperty(),
                                anchoFinal,
                                Interpolator.EASE_BOTH
                        ),
                        new KeyValue(
                                historyBox.opacityProperty(),
                                opacidadFinal,
                                Interpolator.EASE_BOTH
                        )
                )
        );

        animacionHistorial.setOnFinished(evento -> {
            historyBox.setVisible(mostrar);
            historyBox.setManaged(mostrar);
            historyBox.setOpacity(1);
            historyBox.setMinWidth(210);
            historyBox.setMaxWidth(285);
            historyBox.setPrefWidth(250);
        });

        animacionHistorial.play();
    }

    @FXML
    private void eliminarCuenta() {
        if (usuarioSesion == null) {
            return;
        }

        ButtonType confirmar = new ButtonType(
                "Sí, eliminar mi cuenta",
                ButtonBar.ButtonData.OK_DONE
        );

        Alert confirmacion = new Alert(Alert.AlertType.CONFIRMATION);
        confirmacion.setTitle("Eliminar cuenta");
        confirmacion.setHeaderText(
                "¿Eliminar la cuenta de " + usuarioSesion.username() + "?"
        );
        confirmacion.setContentText(
                "Se eliminarán también sus chats, mensajes y archivos. "
                        + "Esta acción no se puede deshacer."
        );
        confirmacion.getButtonTypes().setAll(confirmar, ButtonType.CANCEL);

        if (confirmacion.showAndWait().orElse(ButtonType.CANCEL) != confirmar) {
            return;
        }

        try {
            new UsuarioDAO().eliminarCuenta(usuarioSesion.idUsuario());

            Alert exito = new Alert(Alert.AlertType.INFORMATION);
            exito.setTitle("Cuenta eliminada");
            exito.setHeaderText(null);
            exito.setContentText(
                    "La cuenta y sus datos asociados fueron eliminados. "
                            + "La aplicación se cerrará."
            );
            exito.showAndWait();

            Platform.exit();

        } catch (SQLException error) {
            Alert alerta = new Alert(Alert.AlertType.ERROR);
            alerta.setTitle("No se pudo eliminar la cuenta");
            alerta.setHeaderText(null);
            alerta.setContentText(error.getMessage());
            alerta.showAndWait();
        }
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

        boolean modoTurismo =
                "Turismo".equals(specialtyCombo.getValue());

        String instruccionTono = tonoIAActual.equals("Informal")
                ? "Responde con un tono informal y cercano.\n\n"
                : "Responde con un tono formal y respetuoso.\n\n";

        String instruccionTurismo = modoTurismo
                ? "Eres MarIA, una asistente especializada en turismo. "
                  + "Ayuda con destinos, itinerarios, transporte, alojamiento "
                  + "y cultura. Si un dato puede cambiar, como precios, horarios "
                  + "o requisitos, aclara que debe verificarse en fuentes oficiales "
                  + "y no inventes datos.\n\n"
                : "";

        String instruccionesPerfil =
                "\n\nPuedes consultar el perfil del usuario activo con consultarMiPerfil. "
                        + "Para cambiar username, nombre, apellidos o correo, llama "
                        + "prepararCambioPerfil solo si el usuario lo pide explícitamente. "
                        + "No afirmes que el cambio se guardó: la aplicación solicitará "
                        + "confirmación. Nunca solicites ni muestres contraseñas.";

        // Esta variable se asigna una sola vez porque se usa dentro de supplyAsync.
        String preguntaParaGemini =
                instruccionTono
                        + instruccionTurismo
                        + pregunta
                        + instruccionesPerfil;

        UsuarioHerramientas.limpiarCambioPendiente();

        String textoVisible = pregunta;
        if (archivo != null) {
            textoVisible += "\n\n📎 " + archivo.getFileName();
        }
        microphoneButton.setDisable(true);
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

                String idMensajeUsuario;
                byte[] bytes = null;
                String mimeType = null;
                String extension = null;
                String nombreArchivo = null;

                if (archivo == null) {
                    idMensajeUsuario = chatDAO.guardarMensaje(
                            idChatActual,
                            "USUARIO",
                            "IA",
                            pregunta
                    );
                } else {
                    bytes = Files.readAllBytes(archivo);
                    mimeType = obtenerMimeType(archivo);
                    extension = obtenerExtension(archivo);
                    nombreArchivo = archivo.getFileName().toString();

                    idMensajeUsuario = chatDAO.guardarMensajeConArchivo(
                            idChatActual,
                            "USUARIO",
                            "IA",
                            pregunta,
                            nombreArchivo,
                            mimeType,
                            extension,
                            bytes
                    );
                }

                if (modoTurismo) {
                    chatDAO.guardarEspecialidad(
                            idChatActual,
                            usuarioSesion.idUsuario(),
                            "Turismo",
                            idMensajeUsuario
                    );
                }

                GenerateContentResponse response;

                if (archivo == null) {
                    response = client.models.generateContent(
                            "gemini-3.5-flash-lite",
                            preguntaParaGemini,
                            crearConfigPerfil()
                    );
                } else {
                    Content contenido;

                    if ("text/plain".equals(mimeType)) {
                        String textoArchivo =
                                new String(bytes, StandardCharsets.UTF_8);

                        contenido = Content.fromParts(
                                Part.fromText(preguntaParaGemini),
                                Part.fromText(
                                        "Contenido del archivo "
                                                + nombreArchivo
                                                + ":\n"
                                                + textoArchivo
                                )
                        );
                    } else {
                        contenido = Content.fromParts(
                                Part.fromText(preguntaParaGemini),
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
            microphoneButton.setDisable(true);

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

            UsuarioHerramientas.CambioPerfil cambio =
                    UsuarioHerramientas.tomarCambioPendiente();

            if (cambio != null) {
                confirmarCambioPerfil(cambio, pendingAnswer);
            }

            renderMessages();
            cargarConversaciones();
        }));
    }

    @FXML
    private void toggleRecording() {
        if (client == null) {
            return;
        }

        if (!audioRecorder.isRecording()) {
            try {
                audioRecorder.start();
                microphoneButton.setText("⏹ Detener grabación");
                microphoneButton.getStyleClass().add("recording-button");
            } catch (Exception error) {
                mostrarAviso(
                        "No se pudo iniciar el micrófono: " +
                                error.getMessage()
                );
            }
            return;
        }

        Path wav;

        try {
            wav = audioRecorder.stopToWav();
        } catch (Exception error) {
            resetMicrophoneButton();
            mostrarAviso(
                    "No se pudo guardar la grabación: " +
                            error.getMessage()
            );
            return;
        }

        microphoneButton.setDisable(true);
        microphoneButton.setText("Transcribiendo…");
        questionField.setDisable(true);

        CompletableFuture.supplyAsync(() -> {
            try {
                return GeminiAudioService.transcribe(client, wav);
            } catch (Exception error) {
                throw new CompletionException(error);
            } finally {
                try {
                    Files.deleteIfExists(wav);
                } catch (IOException ignored) {
                }
            }
        }).whenComplete((transcript, error) ->
                Platform.runLater(() -> {
                    questionField.setDisable(false);
                    microphoneButton.setDisable(false);
                    resetMicrophoneButton();

                    if (error != null) {
                        mostrarAviso(
                                "No se pudo transcribir el audio: " +
                                        rootMessage(error)
                        );
                        return;
                    }

                    if (transcript == null || transcript.isBlank()) {
                        mostrarAviso(
                                "No se detectó voz. Intenta grabar de nuevo."
                        );
                        return;
                    }

                    String anterior = questionField.getText().trim();

                    questionField.setText(anterior.isEmpty()
                            ? transcript
                            : anterior + " " + transcript);

                    questionField.positionCaret(
                            questionField.getText().length()
                    );
                    questionField.requestFocus();
                })
        );
    }

    private void resetMicrophoneButton() {
        microphoneButton.setText(switch (idiomaActual) {
            case "English" -> "🎙 Dictate";
            case "Français" -> "🎙 Dicter";
            default -> "🎙 Dictar";
        });

        microphoneButton.getStyleClass().remove("recording-button");
    }

    /** El WebView llama este método cuando pulsan la bocina. */
    public final class AudioBridge {
        public void play(int messageIndex) {
            if (messageIndex < 0 || messageIndex >= messages.size()) {
                return;
            }

            ChatMessage message = messages.get(messageIndex);

            if (!"assistant".equals(message.role)
                    || message.text == null
                    || message.text.isBlank()
                    || message.text.startsWith("*Consultando Gemini")) {
                return;
            }

            CompletableFuture.runAsync(() -> {
                try {
                    Path wav = message.audioFile;

                    if (wav == null || !Files.exists(wav)) {
                        byte[] audio = GeminiAudioService.synthesize(
                                client,
                                message.text,
                                idiomaActual
                        );

                        wav = Files.createTempFile(
                                "maria-respuesta-",
                                ".wav"
                        );

                        wav.toFile().deleteOnExit();
                        Files.write(wav, audio);
                        message.audioFile = wav;
                    }

                    reproducirAudio(wav);
                } catch (Exception error) {
                    Platform.runLater(() ->
                            mostrarAviso(
                                    "No se pudo reproducir la respuesta: " +
                                            error.getMessage()
                            )
                    );
                }
            });
        }
    }

    private synchronized void reproducirAudio(Path wav)
            throws Exception {

        Clip anterior = activeAudioClip.getAndSet(null);

        if (anterior != null) {
            anterior.stop();
            anterior.close();
        }

        Clip clip = AudioSystem.getClip();

        try (AudioInputStream input =
                     AudioSystem.getAudioInputStream(wav.toFile())) {
            clip.open(input);
        }

        clip.addLineListener(event -> {
            if (event.getType()
                    == javax.sound.sampled.LineEvent.Type.STOP) {
                clip.close();
                activeAudioClip.compareAndSet(clip, null);
            }
        });

        activeAudioClip.set(clip);
        clip.start();
    }

    private GenerateContentConfig crearConfigPerfil() {
        try {
            Method consultar = UsuarioHerramientas.class.getMethod(
                    "consultarMiPerfil"
            );
            Method prepararCambio = UsuarioHerramientas.class.getMethod(
                    "prepararCambioPerfil",
                    String.class,
                    String.class
            );

            return GenerateContentConfig.builder()
                    .tools(Tool.builder().functions(consultar, prepararCambio))
                    .build();

        } catch (NoSuchMethodException error) {
            throw new IllegalStateException(
                    "No se pudieron configurar las funciones de perfil.",
                    error
            );
        }
    }

    private void confirmarCambioPerfil(
            UsuarioHerramientas.CambioPerfil cambio,
            ChatMessage respuestaVisible
    ) {
        ButtonType confirmar = new ButtonType(
                "Guardar cambio",
                ButtonBar.ButtonData.OK_DONE
        );

        Alert alerta = new Alert(Alert.AlertType.CONFIRMATION);
        alerta.setTitle("Confirmar cambio de perfil");
        alerta.setHeaderText("¿Quieres guardar este cambio?");
        alerta.setContentText(
                cambio.campo() + ": "
                        + cambio.valorAnterior()
                        + " → "
                        + cambio.valorNuevo()
        );
        alerta.getButtonTypes().setAll(confirmar, ButtonType.CANCEL);

        String resultado;

        if (alerta.showAndWait().orElse(ButtonType.CANCEL) == confirmar) {
            try {
                UsuarioDAO.PerfilUsuario perfil = cambio.perfilNuevo();

                new UsuarioDAO().actualizarPerfil(
                        usuarioSesion.idUsuario(),
                        perfil.username(),
                        perfil.nombre(),
                        perfil.apellidos(),
                        perfil.correo()
                );

                usuarioSesion = new UsuarioSesion(
                        usuarioSesion.idUsuario(),
                        perfil.username(),
                        usuarioSesion.idConfig(),
                        usuarioSesion.idTipos()
                );

                resultado = "Perfil actualizado correctamente.";

            } catch (SQLException error) {
                resultado = "No se pudo actualizar el perfil: "
                        + rootMessage(error);
            }
        } else {
            resultado = "Cambio cancelado. No se modificó el perfil.";
        }

        respuestaVisible.text = resultado;
        messages.add(new ChatMessage("assistant", resultado));
        renderMessages();

        try {
            chatDAO.guardarMensaje(
                    idChatActual,
                    "GEMINI",
                    "US",
                    resultado
            );
        } catch (SQLException error) {
            new Alert(
                    Alert.AlertType.WARNING,
                    "El resultado se mostró, pero no se pudo guardar en el chat: "
                            + error.getMessage()
            ).showAndWait();
        }
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

        boolean modoTurismo = specialtyCombo != null
                && "Turismo".equals(specialtyCombo.getValue());

        String acento = modoTurismo ? "#f2a176" : "#43c8bd";
        String etiquetaTurismo = modoTurismo
                ? "<span class='specialty-badge'>✦ TURISMO</span>"
                : "";

        String etiquetaUsuario = switch (idiomaActual) {
            case "English" -> "You";
            case "Français" -> "Vous";
            default -> "Tú";
        };

        String fondoCodigo = temaActual.equals("Oscuro")
                ? "#101923"
                : "#e9f0f1";

        String plantillaHtml = """
        <!DOCTYPE html>
        <html>
        <head>
            <meta charset="UTF-8">
            <meta name="viewport" content="width=device-width, initial-scale=1">
            <style>
            * { box-sizing: border-box; }
            html { scroll-behavior: smooth; }

            body {
                margin: 0;
                min-height: 100vh;
                padding: 28px 30px 38px;
                background: __FONDO_CHAT__;
                color: __COLOR_TEXTO__;
                font: 15px/1.7 'Segoe UI', Arial, sans-serif;
            }

            .conversation {
                width: min(100%, 940px);
                margin: 0 auto;
            }

            .message {
                width: fit-content;
                max-width: 100%;
                margin: 0 0 28px;
                animation: message-appear .3s ease-out both;
            }

            .message.user {
                max-width: min(78%, 690px);
                margin-left: auto;
                padding: 14px 18px 15px;
                border: 1px solid rgba(130,170,190,.14);
                border-radius: 20px 20px 5px 20px;
                background: __BURBUJA_USUARIO__;
            }

            .user .role {
                margin-bottom: 4px;
                color: __ACENTO__;
                font-size: 10px;
                font-weight: 800;
                letter-spacing: .13em;
                text-transform: uppercase;
            }

            .assistant {
                display: flex;
                align-items: flex-start;
                gap: 13px;
                width: 100%;
                padding: 2px 0 3px;
            }

            .avatar {
                display: grid;
                flex: 0 0 34px;
                width: 34px;
                height: 34px;
                place-items: center;
                border-radius: 12px;
                background: linear-gradient(145deg, __ACENTO__, #168f9b);
                color: #092027;
                font-size: 17px;
                font-weight: 800;
            }

            .assistant-body {
                min-width: 0;
                flex: 1;
                padding-top: 2px;
            }

            .message-meta {
                display: flex;
                align-items: center;
                gap: 9px;
                min-height: 25px;
                margin-bottom: 7px;
            }

            .role {
                color: __ACENTO__;
                font-size: 11px;
                font-weight: 800;
                letter-spacing: .12em;
                text-transform: uppercase;
            }

            .engine {
                color: #899bac;
                font-size: 9px;
                font-weight: 700;
                letter-spacing: .1em;
            }

            .specialty-badge {
                padding: 3px 8px;
                border: 1px solid rgba(242,161,118,.28);
                border-radius: 20px;
                background: rgba(242,161,118,.10);
                color: #f2a176;
                font-size: 9px;
                font-weight: 800;
                letter-spacing: .06em;
            }

            .content { overflow-wrap: anywhere; }
            p { margin: 6px 0 13px; }

            h1, h2, h3 {
                margin: 21px 0 9px;
                color: __COLOR_TEXTO__;
                line-height: 1.3;
            }

            h1 { font-size: 1.55em; }
            h2 { font-size: 1.3em; }
            h3 { font-size: 1.12em; }

            ul, ol { padding-left: 25px; margin: 8px 0 14px; }
            li { margin: 4px 0; padding-left: 2px; }
            strong { color: __COLOR_TEXTO__; }
            a { color: __ACENTO__; text-decoration: none; }
            a:hover { text-decoration: underline; }

            blockquote {
                margin: 13px 0;
                padding: 4px 14px;
                border-left: 3px solid __ACENTO__;
                color: #aebfcb;
            }

            code {
                padding: 2px 6px;
                border-radius: 6px;
                background: __FONDO_CODIGO__;
                color: __ACENTO__;
                font-family: Consolas, monospace;
                font-size: .92em;
            }

            pre {
                overflow-x: auto;
                margin: 12px 0;
                padding: 15px 17px;
                border: 1px solid rgba(135,160,180,.16);
                border-radius: 13px;
                background: __FONDO_CODIGO__;
            }

            pre code {
                padding: 0;
                background: transparent;
                color: __COLOR_TEXTO__;
            }

            hr {
                height: 1px;
                border: 0;
                background: rgba(135,160,180,.22);
                margin: 18px 0;
            }

            .typing {
                display: flex;
                align-items: center;
                gap: 6px;
                min-height: 24px;
                color: #91a3b5;
                font-size: 13px;
            }

            .typing-dot {
                width: 6px;
                height: 6px;
                border-radius: 50%;
                background: __ACENTO__;
                animation: dot-pulse 1.1s infinite ease-in-out;
            }

            .typing-dot:nth-child(2) { animation-delay: .14s; }
            .typing-dot:nth-child(3) {
                animation-delay: .28s;
                margin-right: 5px;
            }

            .speak-button {
                margin-top: 8px;
                padding: 6px 10px;
                border: 1px solid rgba(125,160,185,.24);
                border-radius: 9px;
                background: rgba(125,160,185,.08);
                color: __COLOR_TEXTO__;
                font: 12px 'Segoe UI', Arial, sans-serif;
                cursor: pointer;
            }

            .speak-button:hover {
                background: rgba(67,200,189,.16);
            }

            @keyframes message-appear {
                from { opacity: 0; transform: translateY(8px); }
                to { opacity: 1; transform: translateY(0); }
            }

            @keyframes dot-pulse {
                0%, 60%, 100% {
                    opacity: .35;
                    transform: translateY(0);
                }
                30% {
                    opacity: 1;
                    transform: translateY(-4px);
                }
            }

            @media (max-width: 650px) {
                body { padding: 20px 16px 28px; }
                .message.user { max-width: 92%; }
            }

            @media (prefers-reduced-motion: reduce) {
                *, *::before, *::after {
                    animation-duration: .01ms !important;
                    scroll-behavior: auto !important;
                }
            }
            </style>
        </head>
        <body>
            <main class="conversation">
        """;

        StringBuilder html = new StringBuilder(
                plantillaHtml
                        .replace("__FONDO_CHAT__", fondoChat)
                        .replace("__COLOR_TEXTO__", colorTextoChat)
                        .replace("__BURBUJA_USUARIO__", burbujaUsuario)
                        .replace("__ACENTO__", acento)
                        .replace("__FONDO_CODIGO__", fondoCodigo)
        );

        for (int messageIndex = 0;
             messageIndex < messages.size();
             messageIndex++) {

            ChatMessage message = messages.get(messageIndex);

            if ("user".equals(message.role)) {
                html.append("<section class='message user'>")
                        .append("<div class='role'>")
                        .append(etiquetaUsuario)
                        .append("</div><div>")
                        .append(escapeHtml(message.text).replace("\n", "<br>"))
                        .append("</div></section>");
            } else {
                html.append("<section class='message assistant'>")
                        .append("<div class='avatar'>✦</div>")
                        .append("<div class='assistant-body'>")
                        .append("<div class='message-meta'>")
                        .append("<span class='role'>MarIA</span>")
                        .append("<span class='engine'>GEMINI</span>")
                        .append(etiquetaTurismo)
                        .append("</div>");

                boolean consultando = message.text != null
                        && message.text.startsWith("*Consultando Gemini");

                if (consultando) {
                    String textoConsultando = switch (idiomaActual) {
                        case "English" -> "Thinking";
                        case "Français" -> "Réflexion en cours";
                        default -> "Pensando";
                    };

                    html.append("<div class='typing'>")
                            .append("<span class='typing-dot'></span>")
                            .append("<span class='typing-dot'></span>")
                            .append("<span class='typing-dot'></span>")
                            .append(escapeHtml(textoConsultando))
                            .append("</div>");
                } else {
                    html.append("<div class='content'>")
                            .append(markdownRenderer.render(
                                    markdownParser.parse(
                                            message.text == null ? "" : message.text
                                    )
                            ))
                            .append("</div>")
                            .append("<button class='speak-button' ")
                            .append("title='Escuchar respuesta' ")
                            .append("onclick='window.mariaAudio.play(")
                            .append(messageIndex)
                            .append(")'>Escuchar audio</button>");
                }

                html.append("</div></section>");
            }
        }

        html.append("</main></body></html>");
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
        specialtyCombo.setDisable(false);
        specialtyCombo.setValue("General");
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
        specialtyCombo.setDisable(true);

        CompletableFuture.supplyAsync(() -> {
            try {
                List<ChatDAO.MensajeGuardado> mensajesGuardados =
                        chatDAO.listarMensajes(chat.idChat(), idUsuario);

                String tema = chatDAO.obtenerEspecialidad(
                        chat.idChat(),
                        idUsuario
                );

                return new DatosChat(mensajesGuardados, tema);
            } catch (SQLException error) {
                throw new CompletionException(error);
            }
        }).whenComplete((datos, error) -> Platform.runLater(() -> {
            if (version != versionCargaChat) {
                return;
            }

            questionField.setDisable(false);
            sendButton.setDisable(false);

            if (error != null) {
                specialtyCombo.setDisable(false);
                messages.add(new ChatMessage(
                        "assistant",
                        "No se pudo abrir esta conversación: "
                                + rootMessage(error)
                ));
                renderMessages();
                return;
            }

            idChatActual = chat.idChat();

            specialtyCombo.setValue(
                    "Turismo".equalsIgnoreCase(datos.tema())
                            ? "Turismo"
                            : "General"
            );
            specialtyCombo.setDisable(true);

            messages.clear();

            for (ChatDAO.MensajeGuardado mensaje : datos.mensajes()) {
                boolean esUsuario =
                        mensaje.emisor().equalsIgnoreCase("USUARIO");

                messages.add(new ChatMessage(
                        esUsuario ? "user" : "assistant",
                        mensaje.contenido()
                ));
            }

            renderMessages();
        }));
    }

    private record DatosChat(
            List<ChatDAO.MensajeGuardado> mensajes,
            String tema
    ) {
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
        UsuarioHerramientas.establecerUsuarioActual(usuarioSesion.idUsuario());

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
        private volatile Path audioFile;


        private ChatMessage(String role, String text) {
            this.role = role;
            this.text = text;
        }
    }
}