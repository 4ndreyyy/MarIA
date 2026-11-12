import com.google.genai.Client;
import com.google.genai.types.GenerateContentResponse;

public class ChatConIa {
    public static void main(String[] args) {
        String apiKey = System.getenv("GEMINI_API_KEY");

        if (apiKey == null || apiKey.isEmpty()) {
            System.out.println("Error: La variable de entorno GEMINI_API_KEY no está configurada.");
            return;
        }

        try {
            Client client = Client.builder().apiKey(apiKey).build();

            String prompt = "¿Cuál es la diferencia entre una pila y una cola en programación?";
            System.out.println("Pregunta enviada: " + prompt + "\n");

            GenerateContentResponse response = client.models.generateContent(
                    "gemini-3.8-flash",
                    prompt,
                    null
            );

            System.out.println("Respuesta de la IA:\n" + response.text());

        } catch (Exception e) {
            System.err.println("Ocurrió un error al comunicarse con la API: " + e.getMessage());
            e.printStackTrace();
        }
    }
}