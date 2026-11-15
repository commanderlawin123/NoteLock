package com.notelock.app;

import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioTrack;

/** Synthesises a soft piano-like tone, so the app needs no sound files. */
public final class TonePlayer {
    private static final int SAMPLE_RATE = 44100;
    private static final double DURATION_SEC = 1.6;
    private static final double[] HARMONICS = {1.0, 0.5, 0.3, 0.18, 0.1, 0.06};

    private AudioTrack track;

    public synchronized void play(int midi) {
        stop();
        double freq = 440.0 * Math.pow(2, (midi - 69) / 12.0);
        int n = (int) (SAMPLE_RATE * DURATION_SEC);
        short[] buf = new short[n];

        double norm = 0;
        for (double a : HARMONICS) norm += a;
        int fadeOut = SAMPLE_RATE / 20; // 50 ms

        for (int i = 0; i < n; i++) {
            double t = (double) i / SAMPLE_RATE;
            double env = Math.min(1.0, t / 0.005) * Math.exp(-t * 2.5);
            if (i > n - fadeOut) env *= (double) (n - i) / fadeOut;
            double s = 0;
            for (int h = 0; h < HARMONICS.length; h++) {
                double hf = freq * (h + 1);
                if (hf >= SAMPLE_RATE / 2.0) break;
                // Higher harmonics die away faster, like a struck string.
                s += HARMONICS[h] * Math.exp(-t * h * 1.5) * Math.sin(2 * Math.PI * hf * t);
            }
            buf[i] = (short) (s / norm * env * 0.9 * Short.MAX_VALUE);
        }

        track = new AudioTrack.Builder()
                .setAudioAttributes(new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build())
                .setAudioFormat(new AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(SAMPLE_RATE)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build())
                .setTransferMode(AudioTrack.MODE_STATIC)
                .setBufferSizeInBytes(n * 2)
                .build();
        track.write(buf, 0, n);
        track.play();
    }

    public synchronized void stop() {
        if (track != null) {
            try {
                track.stop();
            } catch (IllegalStateException ignored) {
            }
            track.release();
            track = null;
        }
    }
}
