package com.example.cookiecollector;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.media.SoundPool;

/**
 * Low-latency game sounds shared by the games. The hit sound rises in pitch
 * as the player's streak grows, so a run of hits sounds like a climbing scale.
 */
class GameSounds {
    private static final int ALARM_SAMPLE_RATE = 22050;
    private static final int ALARM_MILLIS = 300;

    private final SoundPool pool;
    private final int hit;
    private final int miss;
    private final int milestone;
    private AudioTrack alarm;

    GameSounds(Context c) {
        AudioAttributes attributes = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build();
        pool = new SoundPool.Builder()
                .setMaxStreams(4)
                .setAudioAttributes(attributes)
                .build();
        hit = pool.load(c, R.raw.success_sound_99, 1);
        //Zapsplat sound effect
        miss = pool.load(c, R.raw.miss_sound, 1);
        milestone = pool.load(c, R.raw.success_sound, 1);
    }

    void playHit(int streak) {
        pool.play(hit, 1, 1, 1, 0, streakRate(streak));
    }

    /** A quieter version of the hit sound, for small pickups that come often. */
    void playCollect(int streak) {
        pool.play(hit, 0.3f, 0.3f, 1, 0, streakRate(streak));
    }

    void playMiss() { pool.play(miss, 1, 1, 1, 0, 1); }

    void playMilestone() { pool.play(milestone, 1, 1, 2, 0, 1); }

    /** A short two-tone buzzer, built in code so it needs no sound file. */
    void playAlarm() {
        if (alarm == null) alarm = buildAlarm();
        if (alarm.getPlayState() == AudioTrack.PLAYSTATE_PLAYING) alarm.stop();
        alarm.reloadStaticData();
        alarm.play();
    }

    void release() {
        pool.release();
        if (alarm != null) alarm.release();
    }

    // Up a little with each hit in a row, capped at 1.5x
    private static float streakRate(int streak) {
        return Math.min(1.5f, 1f + 0.05f * Math.max(0, streak - 1));
    }

    private static AudioTrack buildAlarm() {
        int n = ALARM_SAMPLE_RATE * ALARM_MILLIS / 1000;
        short[] pcm = new short[n];
        double fadeSamples = ALARM_SAMPLE_RATE * 0.01;
        for (int i = 0; i < n; i++) {
            double t = (double) i / ALARM_SAMPLE_RATE;
            // Alternate between two pitches every 75 ms, like a buzzer
            double f = ((int) (t / 0.075)) % 2 == 0 ? 880 : 620;
            double envelope = Math.min(1, Math.min(i, n - i) / fadeSamples);
            pcm[i] = (short) (Math.signum(Math.sin(2 * Math.PI * f * t)) * 0.3 * envelope * Short.MAX_VALUE);
        }
        AudioTrack track = new AudioTrack(
                new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_GAME)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build(),
                new AudioFormat.Builder()
                        .setSampleRate(ALARM_SAMPLE_RATE)
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build(),
                n * 2, AudioTrack.MODE_STATIC, AudioManager.AUDIO_SESSION_ID_GENERATE);
        track.write(pcm, 0, n);
        return track;
    }
}
