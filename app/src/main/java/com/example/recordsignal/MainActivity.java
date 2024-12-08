package com.example.recordsignal;

import android.Manifest;
import android.content.pm.PackageManager;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.os.Environment;
import android.widget.Button;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

public class MainActivity extends AppCompatActivity {
    private static final int SAMPLE_RATE = 48000; // 48 kHz
    private static final int REQUEST_PERMISSION_CODE = 1000;
    private static final double FL = 17000; // Lowest frequency (Hz)
    private static final double FH = 20000; // Highest frequency (Hz)
    private static final double T = 0.2; // Chirp duration (seconds)
    private static final double AMPLITUDE = 1.0;

    private boolean isRecording = false;
    private Thread chirpThread, recordingThread;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Button button = findViewById(R.id.button);
        button.setOnClickListener(v -> {
            if (isRecording) {
                stopRecording();
                button.setText("start");
            } else {
                startRecording();
                button.setText("stop");
            }
        });

        requestPermissionsIfNeeded();
    }

    private void startRecording() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "Audio recording permission required", Toast.LENGTH_SHORT).show();
            return;
        }

        isRecording = true;
        playChirpSignalInLoop();
        startAudioRecording();
    }

    private void stopRecording() {
        isRecording = false;
        Toast.makeText(this, "Recording stopped and saved", Toast.LENGTH_SHORT).show();
    }

    private void playChirpSignalInLoop() {
        chirpThread = new Thread(() -> {
            int bufferSize = AudioTrack.getMinBufferSize(SAMPLE_RATE,
                    AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT);
            AudioTrack audioTrack = new AudioTrack(AudioTrack.MODE_STREAM, SAMPLE_RATE,
                    AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT,
                    bufferSize, AudioTrack.MODE_STREAM);

            audioTrack.play();
            short[] chirpSignal = generateChirpSignal();

            while (isRecording) {
                audioTrack.write(chirpSignal, 0, chirpSignal.length);
            }
            audioTrack.stop();
            audioTrack.release();
        });
        chirpThread.start();
    }

    private void startAudioRecording() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "Audio recording permission not granted", Toast.LENGTH_SHORT).show();
            return;
        }

        recordingThread = new Thread(() -> {
            int bufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE,
                    AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
            AudioRecord audioRecord = new AudioRecord(MediaRecorder.AudioSource.MIC,
                    SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT, bufferSize);

            short[] audioBuffer = new short[bufferSize];
            File file = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "recorded_signal.pcm");

            try (FileOutputStream outputStream = new FileOutputStream(file)) {
                audioRecord.startRecording();
                while (isRecording) {
                    int read = audioRecord.read(audioBuffer, 0, audioBuffer.length);
                    if (read > 0) {
                        for (int i = 0; i < read; i++) {
                            outputStream.write(audioBuffer[i] & 0xFF);
                            outputStream.write((audioBuffer[i] >> 8) & 0xFF);
                        }
                    }
                }
                audioRecord.stop();
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                audioRecord.release();
            }
        });
        recordingThread.start();
    }


    private short[] generateChirpSignal() {
        int numSamples = (int) (T * SAMPLE_RATE);
        short[] signal = new short[numSamples];
        double dt = 1.0 / SAMPLE_RATE;

        for (int i = 0; i < numSamples; i++) {
            double t = i * dt;
            double sample = AMPLITUDE * Math.cos(Math.PI * (FH - FL) * t * t / T + 2 * Math.PI * FL * t);
            signal[i] = (short) (sample * Short.MAX_VALUE);
        }
        return signal;
    }

    private void requestPermissionsIfNeeded() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED ||
                ActivityCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{
                    Manifest.permission.RECORD_AUDIO,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
            }, REQUEST_PERMISSION_CODE);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_PERMISSION_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Permissions granted", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "Permissions denied", Toast.LENGTH_SHORT).show();
            }
        }
    }
}
