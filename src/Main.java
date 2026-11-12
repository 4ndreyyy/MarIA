
public class Main {
    public static void main(String[] args) {
        String apiKey = System.getenv("GEMINI_API_KEY");

        if (apiKey == null || apiKey.isEmpty()) {
            System.out.println("Error: La API key no está configurada.");
            return;
        }

        System.out.println("¡API Key cargada correctamente!");
        // Usa tu apiKey aquí para inicializar tu cliente o servicio
    }
}