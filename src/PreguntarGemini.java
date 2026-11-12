import com.google.genai.Client;
import com.google.genai.types.GenerateContentResponse;
import com.google.genai.types.HttpOptions;
import com.google.genai.types.HttpRetryOptions;

import java.util.Scanner;

    public class PreguntarGemini {
        public static void main(String[] args) {
            String apiKey = System.getenv("GOOGLE_API_KEY");

            if (apiKey == null || apiKey.isBlank()) {
                System.out.println("No se encontró la variable GOOGLE_API_KEY.");
                return;
            }

            Client client = Client.builder()
                    .apiKey(apiKey)
                    .httpOptions(
                            HttpOptions.builder()
                                    .timeout(60_000) // milisegundos
                                    .retryOptions(HttpRetryOptions.builder().attempts(1))
                                    .build()
                    )
                    .build();

            try (Scanner scanner = new Scanner(System.in)) {
                while (true) {
                    System.out.print("\nEscribe tu pregunta (o 'salir' para terminar): ");
                    String pregunta = scanner.nextLine().trim();

                    if (pregunta.equalsIgnoreCase("salir")) {
                        System.out.println("¡Hasta luego!");
                        break;
                    }

                    if (pregunta.isBlank()) {
                        continue;
                    }

                    System.out.println("Consultando Gemini...");

                    try {
                        GenerateContentResponse respuesta = client.models.generateContent(
                                "gemini-3.5-flash-lite",
                                pregunta,
                                null
                        );

                        System.out.println("\nGemini: " + respuesta.text());
                    } catch (Exception e) {
                        System.out.println("Falló la consulta a Gemini:");
                        e.printStackTrace();
                    }
                }
            }

        }
    }

