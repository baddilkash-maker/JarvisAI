package com.jarvis.ai;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.media.AudioAttributes;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;

import androidx.core.app.NotificationCompat;

import org.json.JSONObject;
import org.vosk.Model;
import org.vosk.Recognizer;
import org.vosk.android.RecognitionListener;
import org.vosk.android.SpeechService;
import org.vosk.android.StorageService;

import java.util.Locale;

public class JarvisService extends Service implements RecognitionListener {

    private static final String CHANNEL_ID = "jarvis_listen_channel";
    private static final int NOTIFICATION_ID = 101;

    public interface StatusListener {
        void onStatusChanged(String status);
    }

    public static volatile boolean isRunning = false;
    public static volatile String currentStatus = "Jarvis is stopped";
    private static StatusListener statusListener;

    private Model model;
    private SpeechService speechService;
    private TextToSpeech tts;
    private PowerManager.WakeLock wakeLock;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private volatile boolean ttsReady = false;
    private volatile boolean isSpeaking = false;
    private volatile boolean isDestroyed = false;
    private volatile long lastDoneTime = 0;

    public static void setStatusListener(StatusListener listener) {
        statusListener = listener;
        if (listener != null) {
            listener.onStatusChanged(currentStatus);
        }
    }

    private void updateStatus(String status) {
        currentStatus = status;
        mainHandler.post(() -> {
            if (statusListener != null) {
                statusListener.onStatusChanged(status);
            }
        });
    }

    @Override
    public void onCreate() {
        super.onCreate();
        isDestroyed = false;
        isRunning = true;

        createNotificationChannel();
        startAsForeground();

        PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
        if (pm != null) {
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "JarvisAI::WakeLock");
            wakeLock.acquire();
        }

        initTTS();
        initVosk();
    }

    private void createNotificationChannel() {
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                "Jarvis Wake Word Listener",
                NotificationManager.IMPORTANCE_LOW
        );
        channel.setDescription("Keeps Jarvis listening in the background and on lock screen");
        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager != null) {
            manager.createNotificationChannel(channel);
        }
    }

    private void startAsForeground() {
        Intent intent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this, 0, intent, PendingIntent.FLAG_IMMUTABLE
        );

        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Jarvis AI Active")
                .setContentText("Listening for 'Jarvis'...")
                .setSmallIcon(android.R.drawable.ic_btn_speak_now)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .build();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE);
        } else {
            startForeground(NOTIFICATION_ID, notification);
        }
    }

    private void initTTS() {
        tts = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS && tts != null) {
                tts.setLanguage(Locale.US);
                tts.setAudioAttributes(new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANT)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build());

                tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                    @Override
                    public void onStart(String utteranceId) {}

                    @Override
                    public void onDone(String utteranceId) {
                        resumeListening();
                    }

                    @Override
                    public void onError(String utteranceId) {
                        resumeListening();
                    }
                });
                ttsReady = true;
            }
        });
    }

    private void resumeListening() {
        mainHandler.post(() -> {
            lastDoneTime = System.currentTimeMillis();
            isSpeaking = false;
            if (speechService != null && !isDestroyed) {
                speechService.setPause(false);
            }
            if (!isDestroyed) {
                updateStatus("Listening... Say 'Jarvis'");
            }
        });
    }

    private void initVosk() {
        updateStatus("Loading Jarvis voice engine (first time takes a few seconds)...");
        StorageService.unpack(
                this,
                "model-en-us",
                "model",
                loadedModel -> {
                    if (isDestroyed) {
                        loadedModel.close();
                        return;
                    }
                    try {
                        model = loadedModel;
                        Recognizer rec = new Recognizer(model, 16000.0f);
                        speechService = new SpeechService(rec, 16000.0f);
                        speechService.startListening(this);
                        updateStatus("Listening... Say 'Jarvis'");
                    } catch (Exception e) {
                        updateStatus("Error starting mic: " + e.getMessage());
                    }
                },
                exception -> updateStatus("Model load error: " + exception.getMessage())
        );
    }

    private void checkWakeWord(String hypothesis, boolean isPartial) {
        if (isDestroyed || isSpeaking) return;
        long now = System.currentTimeMillis();
        if (now - lastDoneTime < 1200) return;

        try {
            JSONObject obj = new JSONObject(hypothesis);
            String text = isPartial ? obj.optString("partial", "") : obj.optString("text", "");
            text = text.toLowerCase(Locale.ROOT).trim();

            if (text.contains("jarvis") || text.contains("jervis") || text.contains("jarvish")) {
                isSpeaking = true;
                if (speechService != null) {
                    speechService.setPause(true);
                }
                updateStatus("Jarvis: Yes sir!");
                speakYesSir();
            }
        } catch (Exception ignored) {
        }
    }

    private void speakYesSir() {
        if (ttsReady && tts != null) {
            tts.speak("Yes sir", TextToSpeech.QUEUE_FLUSH, null, "JARVIS_REPLY");
            mainHandler.postDelayed(() -> {
                if (isSpeaking && !isDestroyed) {
                    resumeListening();
                }
            }, 3500);
        } else {
            resumeListening();
        }
    }

    @Override
    public void onPartialResult(String hypothesis) {
        checkWakeWord(hypothesis, true);
    }

    @Override
    public void onResult(String hypothesis) {
        checkWakeWord(hypothesis, false);
    }

    @Override
    public void onFinalResult(String hypothesis) {
        checkWakeWord(hypothesis, false);
    }

    @Override
    public void onError(Exception exception) {
        updateStatus("Mic error: " + exception.getMessage());
    }

    @Override
    public void onTimeout() {
        if (speechService != null && !isDestroyed) {
            speechService.startListening(this);
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        isDestroyed = true;
        isRunning = false;
        mainHandler.removeCallbacksAndMessages(null);

        if (speechService != null) {
            speechService.stop();
            speechService.shutdown();
            speechService = null;
        }
        if (model != null) {
            model.close();
            model = null;
        }
        if (tts != null) {
            tts.stop();
            tts.shutdown();
            tts = null;
        }
        if (wakeLock != null && wakeLock.isHeld()) {
            wakeLock.release();
        }

        updateStatus("Jarvis is stopped");
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
