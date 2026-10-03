import com.jcraft.jsch.*;
import org.vosk.Model;
import org.vosk.Recognizer;

import java.io.InputStream;

/**
 * Pepper Live Audio Streaming & Offline Speech Recognition via Vosk.
 * Umgeht Pepper's NAOqi ALAudioDevice-Limits durch direkten PulseAudio-GStreamer Stream.
 */
public class PepperAudioSystem {

    private static final String PEPPER_IP = "192.168.0.41";
    private static final String LAPTOP_IP = "192.168.0.146"; // Eigene IP im selben Subnetz
    private static final String USER = "nao";
    private static final String PASS = "P3pp3r2!";
    private static final String MODEL_PATH = "/home/ros/vosk-model-de-0.21";
    private static final int UDP_PORT = 5000;

    public static void main(String[] args) {
        new PepperAudioSystem().startService();
    }

    public void startService() {
        System.out.println("Lade Vosk-Modell aus: " + MODEL_PATH);
        
        try (Model model = new Model(MODEL_PATH)) {
            // 1. Remote GStreamer Pipeline via SSH auf Pepper triggern
            startRemoteAudioSender();
            System.out.println("SSH: GStreamer 0.10 auf Pepper gestartet.");

            // 2. Lokale GStreamer 1.0 Pipeline als Subprozess starten
            String[] localCmd = {
                "gst-launch-1.0", "-q",
                "udpsrc", "port=" + UDP_PORT, "!",
                "audio/x-raw,format=S16LE,channels=1,rate=16000,layout=interleaved", "!",
                "fdsink"
            };

            Process process = new ProcessBuilder(localCmd).start();
            InputStream audioStream = process.getInputStream();

            // 3. Vosk Recognizer (16kHz Mono)
            try (Recognizer recognizer = new Recognizer(model, 16000f)) {
                byte[] buffer = new byte[4096];
                int nbytes;

                System.out.println("\n=========================================");
                System.out.println(">>> Pepper hört zu... (Spreche jetzt!) <<<");
                System.out.println("=========================================\n");

                while ((nbytes = audioStream.read(buffer)) >= 0) {
                    if (recognizer.acceptWaveForm(buffer, nbytes)) {
                        handleSpeechResult(recognizer.getResult());
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Fehler im Audio-System: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void handleSpeechResult(String json) {
        if (!json.contains("\"text\" : \"")) return;

        String text = json.split("\"text\" : \"")[1].split("\"")[0].trim();
        if (text.isEmpty()) return;

        System.out.println("Verstanden: [" + text + "]");

        // Beispielszenarien / Sprachbefehle
        if (text.contains("hallo pepper")) {
            System.out.println("-> AKTION: Pepper winkt.");
        } else if (text.contains("wie geht es dir")) {
            System.out.println("-> AKTION: Pepper führt eine Freuden-Geste aus.");
        } else if (text.contains("tanz für mich")) {
            System.out.println("-> AKTION: Tanz-Animation starten.");
        } else if (text.contains("stopp") || text.contains("anhalten")) {
            System.out.println("-> AKTION: Alle Bewegungen abbrechen.");
        } else if (text.contains("schlafenszeit")) {
            System.out.println("-> AKTION: Pepper geht in Ruhemodus (ALMotion.rest).");
        }
    }

    private void startRemoteAudioSender() throws Exception {
        JSch jsch = new JSch();
        Session session = jsch.getSession(USER, PEPPER_IP, 22);
        session.setPassword(PASS);
        session.setConfig("StrictHostKeyChecking", "no");
        session.connect();

        // GStreamer 0.10 Pipeline für Pepper OS
        String cmd = "gst-launch-0.10 pulsesrc device=alsa_input.PCH.input-microphones ! " +
                     "audioconvert ! volume volume=2.0 ! audioresample ! " +
                     "\"audio/x-raw-int,width=16,depth=16,endianness=1234,signed=true,channels=1,rate=16000\" ! " +
                     "udpsink host=" + LAPTOP_IP + " port=" + UDP_PORT;

        ChannelExec channel = (ChannelExec) session.openChannel("exec");
        channel.setCommand(cmd);
        channel.setErrStream(System.err);
        channel.connect();

        Thread.sleep(1000); // Warten bis Socket bereit ist
    }
}
