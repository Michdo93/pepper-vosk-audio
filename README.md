# Pepper Robot Live Audio Streaming & Vosk Offline STT

Eine robuste, latenzarme Lösung zur Übertragung des Mikrofonsignals des SoftBank Pepper Roboters an ein externes System (z. B. PC/Laptop) zur Offline-Spracherkennung mit **Vosk**.

## 🚀 Warum dieser Ansatz?

Die Standard-Schnittstelle `ALAudioDevice` von NAOqi leidet unter bekannten Problemen:
1. **Verbindungshänger/Sperren:** Wenn interne Android-Dienste oder abgestürzte Prozesse das Mikrofon belegen, liefert `ALAudioDevice` lautlos leere Datenpakete.
2. **Datenformat-Inkompatibilität:** Die Mikrofone laufen nativ mit 32-Bit (`s32le`), was in Java-Subskriptionen oft zu Cast-Fehlern oder Stille führt.

**Lösung:** Wir greifen das Audiosignal direkt auf Betriebssystemebene von **PulseAudio** ab und streamen es per **GStreamer (UDP)** an den Zielrechner.

---

## 🛠️ Systemarchitektur

```text
[ Pepper Hardware Mics (4-Ch s32le) ]
                  │
                  ▼
   [ PulseAudio (input-microphones) ]
                  │
                  ▼
  [ GStreamer 0.10 (pulsesrc) ]  --> (S16LE, 16kHz, Mono, Gain=2.0)
                  │
                  ▼ (UDP Stream / Port 5000)
                  │
  [ GStreamer 1.0 (udpsrc) ]     --> (Eingebunden in Java ProcessBuilder)
                  │
                  ▼
       [ Vosk Recognizer Engine ]
```

---

## 📋 Voraussetzungen

### Auf dem Laptop / Steuerungs-PC:

* Java 11+
* Maven
* Installed GStreamer 1.0 Runtime:
```bash
sudo apt install gstreamer1.0-tools gstreamer1.0-plugins-base gstreamer1.0-plugins-good
```


* Deutsches Vosk-Modell heruntergeladen (z. B. `vosk-model-de-0.21`):
[Vosk Models Download](https://alphacephei.com/vosk/models)

### Auf dem Pepper Roboter:

* SSH-Zugriff aktiviert (`nao` / `P3pp3r2!`)
* GStreamer 0.10 (auf NAOqi OS standardmäßig vorhanden)

---

## 💡 Wichtige Erkenntnisse & Troubleshoot-Guide

### 1. Mikrofon-Status auf Pepper prüfen

Sollte kein Ton ankommen, prüfe per SSH auf Pepper die Audio-Quellen:

```bash
pactl list sources short
```

Achte darauf, dass `alsa_input.PCH.input-microphones` den Status `RUNNING` hat.

### 2. Mikrofon-Pegel anpassen (Übersteuern / Zu leise)

Pepper-Mikrofone sind extrem empfindlich. Der Pegel kann live auf Pepper eingestellt werden:

```bash
# Lautstärke auf 120% setzen
pactl set-source-volume alsa_input.PCH.input-microphones 120%
```

### 3. GStreamer 0.10 vs. 1.0 Syntax

* **Pepper (GStreamer 0.10):** Verwendet `audio/x-raw-int` mit expliziter Angabe von `width=16, depth=16, endianness=1234, signed=true`.
* **Laptop (GStreamer 1.0):** Verwendet das neuere `audio/x-raw,format=S16LE`.

---

## 📦 Ausführen des Projekts

1. Pfade und IPs in `PepperAudioSystem.java` anpassen (`PEPPER_IP`, `LAPTOP_IP`, `MODEL_PATH`).
2. Projekt bauen:
```bash
mvn clean package
```


3. Anwendung starten:
```bash
java -cp target/pepper-vosk-streamer-1.0-SNAPSHOT.jar PepperAudioSystem
```
