import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.TargetDataLine;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/** Graba desde el micrófono y guarda temporalmente el audio en WAV. */
public final class AudioRecorder {
    private static final AudioFormat FORMAT =
            new AudioFormat(16_000, 16, 1, true, false);

    private final ByteArrayOutputStream captured =
            new ByteArrayOutputStream();

    private TargetDataLine line;
    private Thread reader;
    private volatile boolean recording;

    public synchronized void start() throws Exception {
        if (recording) {
            return;
        }

        DataLine.Info info =
                new DataLine.Info(TargetDataLine.class, FORMAT);

        line = (TargetDataLine) AudioSystem.getLine(info);
        line.open(FORMAT);
        captured.reset();

        recording = true;
        line.start();

        reader = new Thread(() -> {
            byte[] buffer = new byte[4096];

            while (recording) {
                int count = line.read(buffer, 0, buffer.length);

                if (count > 0) {
                    synchronized (captured) {
                        captured.write(buffer, 0, count);
                    }
                }
            }
        }, "maria-microphone-capture");

        reader.setDaemon(true);
        reader.start();
    }

    public synchronized Path stopToWav() throws Exception {
        if (!recording) {
            throw new IllegalStateException(
                    "No hay una grabación activa."
            );
        }

        recording = false;
        line.stop();
        line.close();

        if (reader != null) {
            reader.join(2_000);
        }

        byte[] pcm;

        synchronized (captured) {
            pcm = captured.toByteArray();
        }

        if (pcm.length < FORMAT.getFrameSize() * 10) {
            throw new IllegalStateException(
                    "La grabación quedó vacía o fue demasiado corta."
            );
        }

        Path wav = Files.createTempFile("maria-voz-", ".wav");
        long frames = pcm.length / FORMAT.getFrameSize();

        try (AudioInputStream audio = new AudioInputStream(
                new ByteArrayInputStream(pcm),
                FORMAT,
                frames
        )) {
            AudioSystem.write(
                    audio,
                    javax.sound.sampled.AudioFileFormat.Type.WAVE,
                    wav.toFile()
            );
        }

        return wav;
    }

    public synchronized boolean isRecording() {
        return recording;
    }
}