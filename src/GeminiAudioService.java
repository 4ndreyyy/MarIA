import com.google.genai.Client;
import com.google.genai.gaos.models.interactions.AudioContent;
import com.google.genai.gaos.models.interactions.AudioContentMimeType;
import com.google.genai.gaos.models.interactions.AudioResponseFormat;
import com.google.genai.gaos.models.interactions.AudioResponseFormatMimeType;
import com.google.genai.gaos.models.interactions.Content;
import com.google.genai.gaos.models.interactions.CreateModelInteraction;
import com.google.genai.gaos.models.interactions.CreateModelInteractionResponseFormat;
import com.google.genai.gaos.models.interactions.GenerationConfig;
import com.google.genai.gaos.models.interactions.Interaction;
import com.google.genai.gaos.models.interactions.InteractionsInput;
import com.google.genai.gaos.models.interactions.Model;
import com.google.genai.gaos.models.interactions.ResponseFormat;
import com.google.genai.gaos.models.interactions.SpeechConfig;
import com.google.genai.gaos.models.interactions.SpeechConfigUnion;
import com.google.genai.gaos.models.operations.CreateInteractionRequestBody;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.List;

public final class GeminiAudioService {
    public static final String TRANSCRIPTION_MODEL =
            "gemini-3.5-transcribe";

    public static final String SPEECH_MODEL =
            "gemini-3.8-flash-lite-tts";

    private static final long MAX_INLINE_AUDIO_BYTES =
            14L * 1024 * 1024;

    private GeminiAudioService() {
    }

    public static String transcribe(Client client, Path wavFile)
            throws IOException {

        byte[] audioBytes = Files.readAllBytes(wavFile);

        if (audioBytes.length > MAX_INLINE_AUDIO_BYTES) {
            throw new IllegalArgumentException(
                    "La grabación supera el límite admitido. " +
                            "Graba un mensaje más corto."
            );
        }

        String encodedAudio =
                Base64.getEncoder().encodeToString(audioBytes);

        Content audio = AudioContent.builder()
                .data(encodedAudio)
                .mimeType(AudioContentMimeType.AUDIO_WAV)
                .build();

        CreateModelInteraction request =
                CreateModelInteraction.builder()
                        .model(Model.of(TRANSCRIPTION_MODEL))
                        .input(InteractionsInput.ofContent(List.of(audio)))
                        .build();

        Interaction result = client.interactions
                .create(CreateInteractionRequestBody.of(request))
                .interaction()
                .orElseThrow(() -> new IllegalStateException(
                        "Gemini no devolvió la transcripción."
                ));

        return result.outputText().orElse("").trim();
    }

    public static byte[] synthesize(
            Client client,
            String text,
            String language
    ) {
        String languageCode = switch (language) {
            case "English" -> "en-US";
            case "Français" -> "fr-FR";
            default -> "es-MX";
        };

        String languageName = switch (language) {
            case "English" -> "English";
            case "Français" -> "français";
            default -> "español";
        };

        SpeechConfig voice = SpeechConfig.builder()
                .voice("Kore")
                .language(languageCode)
                .build();

        GenerationConfig generation = GenerationConfig.builder()
                .speechConfig(SpeechConfigUnion.of(List.of(voice)))
                .build();

        CreateModelInteraction request =
                CreateModelInteraction.builder()
                        .model(Model.of(SPEECH_MODEL))
                        .input(InteractionsInput.of(text))
                        .responseFormat(
                                CreateModelInteractionResponseFormat.of(
                                        ResponseFormat.of(
                                                AudioResponseFormat.builder()
                                                        .mimeType(
                                                                AudioResponseFormatMimeType.AUDIO_WAV
                                                        )
                                                        .build()
                                        )
                                )
                        )
                        .generationConfig(generation)
                        .build();

        Interaction result = client.interactions
                .create(CreateInteractionRequestBody.of(request))
                .interaction()
                .orElseThrow(() -> new IllegalStateException(
                        "Gemini no devolvió audio."
                ));

        String data = result.outputAudio()
                .flatMap(AudioContent::data)
                .orElseThrow(() -> new IllegalStateException(
                        "La respuesta no contiene audio."
                ));

        return Base64.getDecoder().decode(data);
    }
}