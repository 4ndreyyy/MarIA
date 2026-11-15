# MarIA

MarIA es una aplicación de escritorio hecha con JavaFX que permite conversar con Gemini desde una interfaz gráfica. Usa Oracle Database para guardar cuentas, preferencias, conversaciones y mensajes. Incluye un modo especializado en turismo, archivos adjuntos y funciones de voz.

La aplicación está pensada para ejecutarse localmente desde Windows e IntelliJ IDEA o Maven. Para usarla se necesita configurar una clave de Gemini y una conexión Oracle válida; ninguna de esas credenciales debe publicarse en GitHub.

## Funciones

- Registro e inicio de sesión con nombre de usuario y contraseña.
- Contraseñas protegidas con PBKDF2-HMAC-SHA256, salt aleatorio y 210 000 iteraciones.
- Conversaciones independientes por usuario, guardadas en Oracle y recuperables desde el historial.
- Modo **General** y modo **Turismo**. Las consultas de Turismo se asocian además a la tabla `HR.ESPECIALIDAD`.
- Preferencias por usuario: idioma (Español, English o Français), tema (Claro u Oscuro) y tono de respuesta (Formal o Informal).
- Edición de perfil y eliminación de cuenta.
- Archivos `.txt`, `.pdf`, `.png`, `.jpg` y `.jpeg` (este último se normaliza como `.jpg`), con un límite de 10 MiB por archivo. El contenido se envía a Gemini y se guarda en Oracle.
- Dictado por micrófono: graba WAV temporal, solicita la transcripción y la coloca en el campo de pregunta para que el usuario la revise y la envíe.
- Lectura de respuestas en voz: genera WAV bajo demanda con el modelo TTS configurado y lo reproduce localmente.
- Interfaz con JavaFX, FXML, CSS, formato Markdown y controles personalizados de ventana.

## Tecnologías

| Componente | Versión/configuración del proyecto |
|---|---|
| Java | JDK 21 |
| Maven | Proyecto Maven; JavaFX Maven Plugin 0.0.8 |
| JavaFX | 21.0.13 (`controls`, `fxml`, `web`) |
| Google Gen AI SDK | `com.google.genai:google-genai:2.2.0` |
| Oracle JDBC | `ojdbc11:23.26.4.0.0` |
| CommonMark | `0.32.0` |

Identificadores actualmente escritos en el código:

- Chat: `gemini-3.5-flash-lite`.
- Transcripción: `gemini-3.5-transcribe`.
- Voz: `gemini-3.8-flash-lite-tts`, con la voz `Kore`.

La disponibilidad y el acceso a estos identificadores dependen de la cuenta y del servicio de Gemini. Si Google cambia los nombres o el acceso, actualiza las constantes/llamadas correspondientes y el registro de `HR.MODELO`.

## Organización del código

```text
src/main/java/com/maria/
├── Main.java                       # Arranque JavaFX y navegación entre ventanas
├── config/ConexionBD.java          # Apertura de conexiones JDBC
├── controller/                     # Controladores FXML y acciones de la UI
│   ├── ChatController.java
│   ├── LoginController.java
│   └── RegistroController.java
├── dao/                             # Consultas y persistencia Oracle
│   ├── ChatDAO.java
│   ├── ConfiguracionDAO.java
│   └── UsuarioDAO.java
├── demo/                            # Ejemplos de consola, no son el flujo principal
├── model/UsuarioSesion.java         # Identidad y datos de sesión
└── service/                         # Audio y herramientas invocables por Gemini
    ├── AudioRecorder.java
    ├── GeminiAudioService.java
    └── UsuarioHerramientas.java

src/main/resources/
├── interfaz.fxml                    # Ventana principal
├── login.fxml                       # Inicio de sesión
├── registro.fxml                    # Registro
└── marIA.css                        # Estilos de la interfaz
```

### Responsabilidad de los paquetes

- `controller` enlaza los FXML con la lógica de presentación. `ChatController` administra los mensajes, historial, especialidad, preferencias, adjuntos y audio.
- `dao` contiene operaciones JDBC parametrizadas y cierra conexiones/recursos con `try-with-resources`.
- `config` obtiene los parámetros de Oracle desde variables de entorno.
- `model` contiene los datos que viajan entre autenticación y la ventana principal.
- `service` reúne funciones reutilizables que no pertenecen directamente a una vista.
- `demo` contiene ejemplos aislados de consola; la aplicación gráfica inicia en `com.maria.Main`.

## Requisitos

1. Windows 10/11 y JDK 21 instalado.
2. Maven disponible en IntelliJ o en la terminal (`mvn -v`).
3. Oracle Database accesible desde el equipo, con el esquema `HR` y las tablas/relaciones que usa la aplicación.
4. Una clave de API de Google AI Studio con acceso a los modelos configurados.
5. Micrófono y dispositivos de audio de Windows para dictado y lectura en voz.

## Configuración local

La aplicación lee estas variables de entorno:

| Variable | Uso | Ejemplo de formato |
|---|---|---|
| `GOOGLE_API_KEY` | Clave de Gemini. Se intenta leer primero. | `tu_clave` |
| `GEMINI_API_KEY` | Alternativa si no existe `GOOGLE_API_KEY`. | `tu_clave` |
| `DB_URL` | URL JDBC de Oracle. | `jdbc:oracle:thin:@//localhost:1521/XEPDB1` |
| `DB_USER` | Usuario/esquema Oracle, normalmente `HR` en esta configuración. | `HR` |
| `DB_PASSWORD` | Contraseña Oracle de ese usuario. | `tu_contraseña` |

Los ejemplos son marcadores; reemplázalos localmente. En Windows puedes definir las variables desde **Editar las variables de entorno de la cuenta** y reiniciar IntelliJ para que las herede. También puedes definirlas en **Run → Edit Configurations → Environment variables** para la configuración de ejecución de IntelliJ.

En PowerShell, para una terminal actual (los valores no quedan persistidos al cerrar esa terminal):

```powershell
$env:GOOGLE_API_KEY = "REEMPLAZA_LOCALMENTE"
$env:DB_URL = "jdbc:oracle:thin:@//localhost:1521/XEPDB1"
$env:DB_USER = "HR"
$env:DB_PASSWORD = "REEMPLAZA_LOCALMENTE"
mvn javafx:run
```

No pegues una clave real en este README, en el código fuente, en una captura pública ni en un commit. Si una clave llegó a publicarse, revócala y genera otra. El archivo `.env` está ignorado por Git, pero el código Java no lo carga automáticamente: las variables deben estar disponibles en el proceso que inicia la aplicación.

## Base de datos Oracle

El código se conecta al esquema `HR`. No incluye un script de creación de esquema: las tablas, restricciones, tipos, secuencias/permisos necesarios deben estar preparados previamente en Oracle. La aplicación genera identificadores de 32 caracteres hexadecimales con UUID para los registros que inserta.

Tablas consultadas o modificadas por los DAO:

| Tabla | Uso principal en MarIA |
|---|---|
| `HR.USUARIOS` | Credenciales protegidas, datos de perfil, membresía e ID de configuración. |
| `HR.CONFIGURACION` | Idioma, tema y tono de IA por usuario. |
| `HR.CHATS` | Propietario, modelo, título y fechas de cada conversación. |
| `HR.MENSAJES` | Emisor, receptor, contenido, fecha y datos descriptivos del adjunto. |
| `HR.ARCHIVOADJ` | Binario del archivo adjunto y su tipo/nombre de almacenamiento. |
| `HR.ESPECIALIDAD` | Relación de consultas hechas en modo Turismo con chat, usuario y mensaje. |
| `HR.MODELO` | El DAO busca el `ID_MODELO` cuya `CODIGO_API` sea `gemini-3.5-flash-lite`. |

El registro actual crea preferencias iniciales con Español, Formal y Claro y usa el identificador de membresía `BAS1`; ese registro debe existir y ser válido para la FK de `HR.USUARIOS.ID_TIPOS`. El código también espera una fila de `HR.MODELO` con el código API indicado arriba; si no existe, no puede crear una conversación.

En los adjuntos, el mensaje se inserta en `HR.MENSAJES` y el contenido binario en `HR.ARCHIVOADJ` dentro de una transacción. En texto normal, el contenido se guarda en el campo CLOB de `MENSAJES`. En modo Turismo se agrega un registro de especialidad vinculado al mensaje del usuario. Al eliminar la cuenta, la aplicación elimina el usuario; las dependencias deben tener las reglas de cascada apropiadas. La configuración se elimina solamente si deja de estar vinculada a otros usuarios.

## Ejecutar

### Desde IntelliJ IDEA

1. Abre la carpeta que contiene `pom.xml` como proyecto Maven.
2. Espera a que IntelliJ importe las dependencias; si aparece la opción, selecciona **Reload All Maven Projects**.
3. Configura las cuatro variables de entorno en la configuración de ejecución que inicie `com.maria.Main`.
4. Ejecuta `com.maria.Main`, o usa el panel Maven y el objetivo `javafx:run`.

### Desde terminal

Con JDK, Maven y las variables de entorno configurados, desde la raíz del repositorio ejecuta:

```powershell
mvn javafx:run
```

La primera ejecución de Maven puede tardar mientras descarga dependencias. La aplicación abre el inicio de sesión; después de autenticar, abre la ventana principal.

## Flujo general

1. `Main` carga `/login.fxml`.
2. `LoginController` llama a `UsuarioDAO.autenticar`; si es correcto, devuelve `UsuarioSesion`.
3. `Main` abre `/interfaz.fxml` y pasa la sesión a `ChatController`.
4. `ChatController` carga preferencias y conversaciones del usuario.
5. Al enviar una consulta, crea el chat si hace falta, persiste la pregunta, solicita respuesta a Gemini y guarda la respuesta. La llamada externa y operaciones de base de datos se realizan en tareas asíncronas para evitar bloquear la interfaz.
6. El historial puede cargar mensajes previamente guardados; la propiedad del chat se valida por usuario en las consultas.

## Solución de problemas

| Síntoma | Qué revisar |
|---|---|
| No se encontró la API key | Define `GOOGLE_API_KEY` o `GEMINI_API_KEY` en el proceso de IntelliJ y reinicia la aplicación. |
| `ORA-01017` | Revisa `DB_USER`, `DB_PASSWORD` y que `DB_URL` apunte al servicio correcto. |
| No existe código en `HR.MODELO` | Inserta/valida una fila cuyo `CODIGO_API` sea exactamente `gemini-3.5-flash-lite`; el ID se obtiene de esa tabla. |
| JavaFX runtime components are missing | Ejecuta el proyecto Maven con `javafx:run` o configura la ejecución con los módulos JavaFX. |
| Gemini responde 404 o modelo no disponible | Comprueba el identificador usado por el código y el acceso habilitado para tu proyecto; revisa que el modelo esté disponible en tu cuenta. |
| Micrófono no inicia o queda una transcripción vacía | Revisa el dispositivo de entrada de Windows, permisos, volumen y duración de la grabación. |
| No se guarda una respuesta | Revisa la conexión, restricciones y tipos de columna de Oracle; la interfaz muestra el error raíz cuando está disponible. |

## Seguridad y datos

- No incluyas claves API, contraseña Oracle, contraseñas personales ni datos reales en el repositorio.
- Al usar Gemini, el texto de la consulta y el contenido del adjunto se transmiten al servicio de Google para generar la respuesta.
- Los adjuntos se guardan en la base de datos Oracle. El WAV de dictado se crea en un archivo temporal y se elimina después de la transcripción; el audio sintetizado también es temporal y se reproduce localmente.
- `UsuarioDAO` almacena contraseñas con PBKDF2-HMAC-SHA256 y salt aleatorio; nunca almacena la contraseña como texto plano.
- `.gitignore` excluye `.env`, `.idea`, `target`, `out` y archivos WAV. Revisa los archivos staged antes de publicar el repositorio.

## Documentación extendida

El manual técnico en Word describe los paquetes, configuración, tablas, flujos de uso y solución de problemas: [docs/Manual tecnico de MarIA.docx](docs/Manual%20tecnico%20de%20MarIA.docx).

## Catálogo de clases y métodos

Esta sección explica las clases Java presentes en `src/main/java/com/maria`, el sentido funcional de sus nombres y el propósito de cada método declarado. Los nombres siguen convenciones de Java: clases y records usan `UpperCamelCase`, métodos y variables usan `lowerCamelCase`; los paquetes en minúsculas separan responsabilidades. Las explicaciones del nombre describen su significado observable, no una intención histórica que el código no documenta.

### Cómo leer los nombres

- `Controller`: recibe eventos de una vista JavaFX/FXML y coordina la presentación.
- `DAO` (*Data Access Object*): concentra las consultas y escrituras JDBC de una entidad o área de datos.
- `Service`: agrupa una operación reutilizable que no pertenece a una vista, como audio o herramientas del asistente.
- `model`: contiene estructuras que representan datos de la aplicación; `record` crea objetos de datos inmutables con accesores generados por Java.
- `config`: concentra configuración e integración con recursos externos. `demo`: ejemplos independientes para probar Gemini desde consola.

Los nombres de métodos también describen su acción: `obtener` recupera datos, `guardar`/`actualizar` persisten cambios, `listar` devuelve colecciones, `abrir` muestra una vista y `aplicar` lleva una preferencia al estado visual.

### `com.maria.Main`

Clase principal de arranque. `Main` es un nombre convencional y breve para el punto de entrada de una aplicación Java; aquí inicia JavaFX y coordina el cambio de ventana entre login y chat.

- **`start(Stage stage)`**: Carga `login.fxml`, configura la ventana de acceso, conecta el callback de inicio de sesión y muestra la pantalla.
- **`abrirChat(Stage ventanaLogin, UsuarioSesion usuario)`**: Carga `interfaz.fxml`, entrega la sesión a `ChatController`, crea y muestra la ventana del chat y cierra la ventana de login. Muestra un diálogo si falla la carga.
- **`main(String[] args)`**: Lanza el ciclo de vida de JavaFX mediante `Application.launch`.

### `com.maria.config.ConexionBD`

`ConexionBD` significa conexión con la base de datos. El sufijo `BD` abrevia base de datos; la clase centraliza el acceso JDBC a Oracle.

- **`obtenerConexion()`**: Lee `DB_URL`, `DB_USER` y `DB_PASSWORD`, y devuelve una conexión JDBC creada por `DriverManager`.
- **`variableRequerida(String nombre)`**: Comprueba que una variable de entorno exista y no esté vacía; si falta, comunica cuál debe configurarse.
- **`ConexionBD()`**: Constructor privado: evita crear instancias de una clase que solo ofrece un método estático.

### `com.maria.controller.LoginController`

`LoginController` sigue la convención JavaFX MVC: `Controller` coordina la vista `login.fxml`; `Login` identifica la pantalla de acceso.

- **`setAlIniciarSesion(Consumer<UsuarioSesion> accion)`**: Recibe la acción que debe ejecutarse cuando las credenciales sean válidas; `Main` la usa para abrir el chat.
- **`iniciarSesion()`**: Obtiene usuario y contraseña del formulario, llama a `UsuarioDAO.autenticar`, muestra el resultado y borra el arreglo de contraseña al terminar.
- **`abrirRegistro()`**: Abre `registro.fxml` como ventana modal vinculada a la ventana de login.

### `com.maria.controller.RegistroController`

`RegistroController` controla el formulario de registro. `Registro` expresa la tarea de creación de cuenta.

- **`registrar()`**: Lee los campos, delega el alta a `UsuarioDAO.registrar` con la membresía `BAS1`, informa errores o éxito, limpia el formulario y borra la contraseña en memoria.

### `com.maria.controller.ChatController`

`ChatController` coordina la ventana principal de conversación. Es el controlador más amplio porque el FXML principal contiene chat, historial, tema, especialidad, adjuntos y controles de audio/ventana.

- **`initialize()`**: Inicializa controles JavaFX, modos General/Turismo, selección del historial, puente WebView-audio, cliente Gemini y mensaje de bienvenida. Desactiva el envío si falta la clave API.
- **`minimizarVentana()`**: Minimiza la ventana actual.
- **`alternarMaximizarVentana()`**: Maximiza o restaura la ventana y actualiza el símbolo de su botón.
- **`cerrarVentana()`**: Cierra la ventana principal actual.
- **`iniciarArrastreVentana(MouseEvent evento)`**: Guarda la posición inicial para arrastrar la ventana personalizada; un doble clic alterna maximizar/restaurar.
- **`arrastrarVentana(MouseEvent evento)`**: Mueve la ventana personalizada mientras se arrastra su barra de título, excepto cuando está maximizada.
- **`eventoEnBotonDeTitulo(MouseEvent evento)`**: Detecta si el clic ocurrió dentro de un botón de la barra personalizada para no iniciar un arrastre accidental.
- **`ventanaActual()`**: Obtiene el `Stage` que contiene el panel raíz de esta vista.
- **`obtenerExtension(Path archivo)`**: Valida extensiones admitidas (`txt`, `pdf`, `png`, `jpg`) y normaliza `.jpeg` a `.jpg`.
- **`obtenerMimeType(Path archivo)`**: Asocia la extensión validada con su tipo MIME para enviarlo a Gemini y almacenarlo.
- **`abrirPreferencias()`**: Construye el diálogo de idioma, tema y tono; permite guardar opciones en Oracle o iniciar el proceso de eliminación de cuenta.
- **`aplicarPreferencias(String idioma, String tonoIA, String tema)`**: Actualiza el estado y la apariencia de la interfaz, traduce etiquetas disponibles y vuelve a dibujar los mensajes.
- **`aplicarEstiloDialogo(DialogPane panel, String tema)`**: Aplica la hoja `marIA.css` y la variante visual clara u oscura al diálogo.
- **`estilizarCombo(ComboBox<String> combo)`**: Aplica al selector el estilo correspondiente al tema actual.
- **`estilizarCombo(ComboBox<String> combo, String tema)`**: Configura colores de las opciones, selección y valor mostrado de un `ComboBox` para el tema indicado.
- **`actualizarEstiloEspecialidad(String especialidad)`**: Activa o quita la clase CSS `tourism-mode` según el modo seleccionado.
- **`alternarHistorial()`**: Oculta o despliega la barra lateral de conversaciones con una animación de anchura y opacidad.
- **`eliminarCuenta()`**: Solicita confirmación explícita, delega la eliminación al DAO y cierra la aplicación si Oracle confirma la operación.
- **`sendQuestion()`**: Valida pregunta/archivo, añade instrucciones de tono y Turismo, muestra el estado de espera y procesa la llamada en segundo plano. Guarda el chat/mensaje en Oracle, almacena el adjunto cuando aplica y registra en `ESPECIALIDAD` solo las preguntas del modo Turismo. Maneja llamadas de herramientas de perfil y actualiza la vista al concluir.
- **`toggleRecording()`**: Inicia o detiene la captura del micrófono; al detenerla solicita la transcripción a Gemini en segundo plano y coloca el texto en el campo de pregunta para que el usuario lo revise.
- **`resetMicrophoneButton()`**: Restaura el texto del botón de dictado según el idioma actual y quita el estilo de grabación.
- **`AudioBridge.play(int messageIndex)`**: Método expuesto al JavaScript del WebView: sintetiza bajo demanda la respuesta seleccionada, guarda el WAV temporal en caché de sesión y lo reproduce.
- **`reproducirAudio(Path wav)`**: Detiene el audio anterior, abre el WAV en un `Clip` y reproduce el nuevo; libera el clip al finalizar.
- **`crearConfigPerfil()`**: Registra con la SDK de Gemini las funciones invocables para consultar perfil y proponer un cambio de perfil.
- **`confirmarCambioPerfil(CambioPerfil cambio, ChatMessage respuestaVisible)`**: Muestra el cambio propuesto y pide confirmación; si se acepta lo persiste, actualiza la sesión y guarda el resultado en el chat.
- **`adjuntarArchivo()`**: Abre el selector de archivos y valida tamaño máximo de 10 MiB, extensión permitida y nombre de hasta 100 caracteres.
- **`quitarArchivo()`**: Descarta el archivo seleccionado y actualiza su etiqueta.
- **`mostrarAviso(String mensaje)`**: Presenta un aviso de validación relacionado con archivos.
- **`renderMessages()`**: Genera HTML/CSS para los mensajes, aplica tema y especialidad, convierte Markdown seguro y muestra animación/acción de audio en respuestas.
- **`escapeHtml(String text)`**: Escapa caracteres HTML especiales antes de insertar texto de usuario en la vista WebView.
- **`rootMessage(Throwable error)`**: Busca la causa más profunda de una excepción y devuelve su mensaje para mostrar un error útil.
- **`nuevoChat()`**: Restablece los controles y mensajes para iniciar una conversación nueva, sin chat seleccionado.
- **`cargarConversaciones()`**: Consulta los chats del usuario en segundo plano y actualiza la lista de historial en el hilo JavaFX.
- **`cargarChat(ChatResumen chat)`**: Carga mensajes y tema/especialidad de la conversación elegida, valida que corresponda al usuario y presenta su contenido.
- **`abrirRegistro()`**: Abre el formulario de registro como ventana modal desde la pantalla principal si el FXML la invoca.
- **`setUsuarioSesion(UsuarioSesion usuarioSesion)`**: Asigna la sesión, establece el usuario para las herramientas de Gemini, lee preferencias y carga el historial.
- **`ChatMessage(String role, String text)`**: Construye un mensaje de la interfaz con rol, texto y espacio opcional para el WAV asociado.
- **`DatosChat(List<MensajeGuardado> mensajes, String tema)`**: Agrupa temporalmente el resultado asíncrono de cargar mensajes y especialidad.

### `com.maria.dao.UsuarioDAO`

`UsuarioDAO` significa objeto de acceso a datos de usuario: `DAO` es la convención *Data Access Object*. Encapsula SQL de cuentas y seguridad de credenciales.

- **`registrar(...)`**: Valida campos y contraseña, genera IDs y hash PBKDF2, crea preferencias y usuario dentro de una transacción; revierte cambios si falla.
- **`consultarPerfil(String idUsuario)`**: Recupera username, nombre, apellidos y correo del usuario identificado.
- **`actualizarPerfil(...)`**: Actualiza datos de perfil y comprueba que se haya actualizado exactamente una cuenta.
- **`autenticar(String username, char[] contrasena)`**: Busca la cuenta, verifica el hash de la contraseña y devuelve `UsuarioSesion` o `null`; borra el arreglo de contraseña.
- **`eliminarCuenta(String idUsuario)`**: Elimina el usuario en transacción, confía en las reglas Oracle para datos en cascada y borra la configuración solo si queda huérfana.
- **`crearHash(char[] contrasena)`**: Genera salt aleatorio y hash PBKDF2-HMAC-SHA256 (210 000 iteraciones, 256 bits) en formato codificado.
- **`verificarHash(char[] contrasena, String hashGuardado)`**: Lee parámetros/salt del hash almacenado y compara el resultado con comparación de tiempo constante.
- **`validar(String valor, String campo)`**: Rechaza campos obligatorios nulos o vacíos.
- **`nuevoId()`**: Genera un UUID hexadecimal en mayúsculas y sin guiones para las claves de las tablas.
- **`PerfilUsuario(username, nombre, apellidos, correo)`**: Record con datos no secretos de perfil; Java genera sus accesores.

### `com.maria.dao.ConfiguracionDAO`

`ConfiguracionDAO` accede a la tabla de preferencias `CONFIGURACION`; el sufijo DAO señala la capa SQL.

- **`obtener(String idConfig)`**: Lee idioma, tono y tema para el ID de configuración; falla si no encuentra fila.
- **`actualizar(String idConfig, String idioma, String tonoIA, String tema)`**: Valida opciones permitidas y actualiza exactamente la fila del usuario.
- **`validarOpciones(String idioma, String tonoIA, String tema)`**: Limita idioma, tono y tema a los valores que admite la interfaz.
- **`Preferencias(idioma, tonoIA, tema)`**: Record inmutable de las preferencias recuperadas de Oracle; sus accesores los genera Java.

### `com.maria.dao.ChatDAO`

`ChatDAO` es el DAO para conversaciones, mensajes, adjuntos y etiquetas de especialidad. Separa las consultas Oracle del controlador.

- **`crearChat(String idUsuario, String primeraPregunta)`**: Obtiene el ID del modelo configurado, usa la primera pregunta como título y crea un registro de chat.
- **`guardarMensaje(String idChat, String emisor, String receptor, String contenido)`**: Guarda un mensaje de texto en CLOB y actualiza la fecha de modificación del chat.
- **`listarChats(String idUsuario)`**: Devuelve títulos y fechas de las conversaciones del usuario, de la más reciente a la más antigua.
- **`listarMensajes(String idChat, String idUsuario)`**: Recupera los mensajes ordenados de una conversación solo cuando pertenece al usuario; agrega el nombre del adjunto si existe.
- **`guardarMensajeConArchivo(...)`**: Guarda mensaje y binario en `MENSAJES`/`ARCHIVOADJ` en una transacción, junto con sus metadatos y la fecha del chat.
- **`guardarEspecialidad(String idChat, String idUsuario, String tema, String idMensaje)`**: Inserta la asociación del mensaje con el tema de especialidad. El controlador lo llama en modo Turismo.
- **`obtenerEspecialidad(String idChat, String idUsuario)`**: Obtiene el tema más reciente de especialidad asociado a ese chat y propietario; devuelve `null` si no hay registro.
- **`buscarIdModelo()`**: Busca el ID de `HR.MODELO` para `gemini-3.5-flash-lite` y detecta ausencia o duplicados.
- **`leerClob(Reader lector)`**: Lee el contenido CLOB por bloques y convierte errores de lectura a `SQLException`.
- **`nuevoId()`**: Genera identificadores UUID sin guiones en mayúsculas.
- **`ChatResumen(idChat, titulo, fecha)`**: Record para presentar una fila del historial; su `toString()` devuelve título y fecha para la lista JavaFX.
- **`ChatResumen.toString()`**: Formatea el renglón del historial como `título · fecha`.
- **`MensajeGuardado(emisor, contenido)`**: Record de los datos que el controlador necesita al reconstruir una conversación.

### `com.maria.model.UsuarioSesion`

`UsuarioSesion` nombra el estado de identidad de una sesión autenticada. Está en `model` porque transporta datos del dominio entre controlador y DAO.

- **`UsuarioSesion(idUsuario, username, idConfig, idTipos)`**: Record inmutable. Java genera accesores para los cuatro componentes; no guarda la contraseña.

### `com.maria.service.AudioRecorder`

`AudioRecorder` describe directamente el servicio que captura el micrófono y produce un archivo WAV temporal.

- **`start()`**: Abre el dispositivo de captura mono a 16 kHz, reinicia el buffer y arranca un hilo lector.
- **`stopToWav()`**: Detiene la captura, verifica que haya audio y escribe los datos PCM a un WAV temporal; devuelve su ruta.
- **`isRecording()`**: Indica si la grabación sigue activa.

### `com.maria.service.GeminiAudioService`

El nombre combina el proveedor/modelo (`Gemini`) con su responsabilidad (`AudioService`): operaciones de voz separadas de la interfaz.

- **`transcribe(Client client, Path wavFile)`**: Lee el WAV, verifica el límite de audio, lo codifica y solicita la transcripción al modelo configurado.
- **`synthesize(Client client, String text, String language)`**: Solicita una respuesta de voz WAV en el idioma indicado y decodifica el audio Base64 devuelto.
- **`GeminiAudioService()`**: Constructor privado: el servicio se usa mediante métodos estáticos.

### `com.maria.service.UsuarioHerramientas`

`UsuarioHerramientas` reúne funciones acotadas que Gemini puede invocar para consultar o proponer cambios al perfil activo. `Herramientas` expresa que son operaciones disponibles al asistente.

- **`establecerUsuarioActual(String idUsuario)`**: Define el ID de usuario que las herramientas pueden consultar durante la sesión.
- **`limpiarCambioPendiente()`**: Elimina una propuesta de cambio anterior para que no se reutilice en una nueva consulta.
- **`consultarMiPerfil()`**: Devuelve los datos no sensibles del perfil activo mediante `UsuarioDAO`; no expone contraseñas.
- **`prepararCambioPerfil(String campo, String nuevoValor)`**: Valida y construye una propuesta de cambio para username, nombre, apellidos o correo; no escribe en Oracle y requiere confirmación posterior.
- **`tomarCambioPendiente()`**: Entrega y limpia atómicamente la propuesta pendiente.
- **`CambioPerfil(campo, valorAnterior, valorNuevo, perfilNuevo)`**: Record inmutable con la propuesta para mostrar confirmación y persistirla después.
- **`UsuarioHerramientas()`**: Constructor privado para impedir instancias de esta clase de utilidades estáticas.

### `com.maria.demo.ChatConIa`

`ChatConIa` significa chat con IA; `demo` indica que es un programa de demostración, no parte del flujo JavaFX principal.

- **`main(String[] args)`**: Comprueba `GEMINI_API_KEY`, envía un prompt de ejemplo al modelo y muestra la respuesta o el error en consola.

### `com.maria.demo.PreguntarGemini`

`PreguntarGemini` describe el ejemplo mínimo de consulta por consola al modelo.

- **`main(String[] args)`**: Lee `GOOGLE_API_KEY`, configura cliente/timeout, acepta preguntas en un bucle hasta escribir `salir` y muestra cada respuesta.

### Archivos de paquete y recursos visuales

Los archivos `package-info.java` describen los paquetes `com.maria`, `config`, `controller`, `dao`, `demo`, `model` y `service`; no declaran clases ejecutables ni métodos. En `src/main/resources`, `login.fxml`, `registro.fxml` e `interfaz.fxml` describen la estructura y enlazan eventos con métodos `@FXML`; `marIA.css` define los estilos. `pom.xml` declara dependencias, versión de Java y el punto de entrada Maven/JavaFX.

### Métodos de callback internos

`ChatController` crea celdas personalizadas para listas JavaFX. Sus métodos anónimos `updateItem(...)` reciben cada elemento visible y actualizan texto, gráfico y colores de una celda; forman parte de las fábricas de celdas de preferencias y selectores, no son métodos públicos del controlador.
