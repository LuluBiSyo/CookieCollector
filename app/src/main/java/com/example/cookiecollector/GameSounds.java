package com.example.cookiecollector;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.SoundPool;

/**
 * Low-latency game sounds shared by both games. The hit sound rises in pitch
 * as the player's streak grows, so a run of hits sounds like a climbing scale.
 */
class GameSounds {
    private final SoundPool pool;
    private final int hit;
    private final int miss;
    private final int milestone;

    GameSounds(Context c) {
        pool = new SoundPool.Builder()
                .setMaxStreams(4)
                .setAudioAttributes(new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_GAME)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build())
                .build();
        hit = pool.load(c, R.raw.success_sound_99, 1);
        //Zapsplat sound effect
        miss = pool.load(c, R.raw.miss_sound, 1);
        milestone = pool.load(c, R.raw.success_sound, 1);
    }

    void playHit(int streak) {
        // Up a little with each hit in a row, capped at 1.5x
        float rate = Math.min(1.5f, 1f + 0.05f * Math.max(0, streak - 1));
        pool.play(hit, 1, 1, 1, 0, rate);
    }

    void playMiss() { pool.play(miss, 1, 1, 1, 0, 1); }

    void playMilestone() { pool.play(milestone, 1, 1, 2, 0, 1); }

    void release() { pool.release(); }
}
