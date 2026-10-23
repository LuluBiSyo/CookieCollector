package com.example.cookiecollector;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.widget.Button;
import android.widget.TextView;

import java.util.Locale;

/**
 * Swipe game: cookies fall from the top of the screen and the user swipes or flicks
 * across each one to slice it.
 */
public class SwipeGameActivity extends AppCompatActivity implements FallingCookiesView.Listener {
    private static final int INSTRUCTIONS = 0;
    private static final int GAME = 1;
    private static final int PAUSE = 2;
    private static final int FINISHED = 3;

    // Falling cookies are drawn smaller than the tap-game targets: Large 16 mm -> ~10 mm, Small 12 mm -> ~8 mm
    private static final float SIZE_SCALE = 0.65f;

    // Every 3 slices in a row makes new cookies fall 10% faster, up to twice the starting speed.
    // A miss slows back down one level, so the game adapts to the player.
    private static final int SLICES_PER_LEVEL = 3;
    private static final float LEVEL_SPEEDUP = 0.9f;
    private static final long MIN_FALL_MILLIS = FallingCookiesView.START_FALL_MILLIS / 2;

    // Accuracy bonus: how close to the center the swipe passes, as a fraction of the cookie radius
    private static final float PERFECT_OFFSET = 0.25f;
    private static final float GREAT_OFFSET = 0.5f;

    UserAndStudyInfo infoData;
    FallingCookiesView fallingCookiesView;
    GameSounds sounds;
    float cookieDiameterMm;

    int state = INSTRUCTIONS;
    int cookiesDone = 0;
    int totalHits = 0;
    int points = 0;
    int streak = 0;
    int bestStreak = 0;
    int level = 1;
    int topLevel = 1;
    int slicesTowardLevel = 0;
    long fallMillis = FallingCookiesView.START_FALL_MILLIS;
    long totalTime = 0;
    boolean running = false;
    final StringBuilder results = new StringBuilder("Cookie,Size,FallTime,Duration,Offset,Points,Success\n");

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_swipe_game);

        infoData = (UserAndStudyInfo) getIntent().getSerializableExtra(SetupActivity.EXTRA_INFO);
        sounds = new GameSounds(this);

        fallingCookiesView = findViewById(R.id.fallingCookiesView);
        cookieDiameterMm = infoData.getDiameter() * SIZE_SCALE;
        fallingCookiesView.setup(GameUtils.mmToPx(this, cookieDiameterMm / 2), infoData.getTotalTrials(), this);

        displayTrialsDone();

        Button pauseButton = findViewById(R.id.pauseButton);
        pauseButton.setOnClickListener(v -> {
            if (state == GAME) {
                running = false;
                state = PAUSE;
                fallingCookiesView.pause();
                pauseButton.setText("RESUME");
            }
            else if (state == PAUSE) {
                running = true;
                state = GAME;
                fallingCookiesView.start();
                pauseButton.setText("PAUSE");
            }
        });

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setMessage(R.string.swipe_instructions);
        builder.setCancelable(false);
        builder.setPositiveButton("BEGIN", (dialog, which) -> {
            if (state == INSTRUCTIONS) {
                running = true;
                startClock();
                state = GAME;
                fallingCookiesView.start();
            }
            dialog.cancel();
        });
        builder.setNegativeButton("BACK", (dialog, which) -> {
            if (state == INSTRUCTIONS) finish();
        });
        GameUtils.showLarge(builder.create());
    }

    private void startClock() {
        final Handler handler = new Handler();
        handler.post(new Runnable() {
            @Override
            public void run() {
                if (state == FINISHED) return;
                if (running) totalTime++;
                displayTrialsDone();
                handler.postDelayed(this, 1000);
            }
        });
    }

    @Override
    protected void onPause() {
        super.onPause();
        // Freeze the game when the app goes to the background
        if (state == GAME) findViewById(R.id.pauseButton).performClick();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        sounds.release();
    }

    @Override
    public void onCookieCrossed(FallingCookiesView.Cookie cookie, float x, float y) {
        totalHits++;
        streak++;
        bestStreak = Math.max(bestStreak, streak);

        float offset = cookie.minOffsetPx / fallingCookiesView.getRadiusPx();
        int earned;
        String label;
        if (offset <= PERFECT_OFFSET) { earned = 3; label = "+3 Perfect!"; }
        else if (offset <= GREAT_OFFSET) { earned = 2; label = "+2 Great!"; }
        else { earned = 1; label = "+1"; }
        points += earned;

        sounds.playHit(streak);
        boolean milestone = fallingCookiesView.getEffects().hit(x, y, fallingCookiesView.getRadiusPx(), streak, label);
        if (levelUp() || milestone) sounds.playMilestone();
        addResult(cookie, earned);
    }

    @Override
    public void onCookieMissed(FallingCookiesView.Cookie cookie) {
        streak = 0;
        slicesTowardLevel = 0;
        if (level > 1) {
            level--;
            fallMillis = Math.min(FallingCookiesView.START_FALL_MILLIS, (long) (fallMillis / LEVEL_SPEEDUP));
            fallingCookiesView.setFallMillis(fallMillis);
        }
        sounds.playMiss();
        GameUtils.vibrate(this, 40);
        addResult(cookie, 0);
    }

    /** Speeds up after enough slices in a row; returns true when the level went up. */
    private boolean levelUp() {
        if (++slicesTowardLevel < SLICES_PER_LEVEL || fallMillis <= MIN_FALL_MILLIS) return false;
        slicesTowardLevel = 0;
        level++;
        topLevel = Math.max(topLevel, level);
        fallMillis = Math.max(MIN_FALL_MILLIS, (long) (fallMillis * LEVEL_SPEEDUP));
        fallingCookiesView.setFallMillis(fallMillis);
        fallingCookiesView.getEffects().popup("Faster! Level " + level, fallingCookiesView.getWidth() / 2f,
                fallingCookiesView.getHeight() / 2f, GameEffects.WHITE, 34);
        return true;
    }

    @Override
    public void onAllCookiesDone() {
        finished();
    }

    /** One results row: fall time is the cookie's speed setting; offset is how far the cut passed from its center, in mm. */
    void addResult(FallingCookiesView.Cookie cookie, int earned) {
        boolean success = earned > 0;
        results.append(cookie.number).append(",");
        results.append(String.format(Locale.US, "%.1f", cookieDiameterMm)).append(",");
        results.append(String.format(Locale.US, "%.2f", cookie.fallMillis / 1000f)).append(",");
        results.append(String.format(Locale.US, "%.3f", cookie.fallingMillis / 1000f)).append(",");
        results.append(success ? String.format(Locale.US, "%.2f", GameUtils.pxToMm(this, cookie.minOffsetPx)) : "").append(",");
        results.append(earned).append(",");
        results.append(success).append("\n");
        cookiesDone++;
        displayTrialsDone();
    }

    void displayTrialsDone() {
        TextView trialsTextView = findViewById(R.id.trialsTextView);
        trialsTextView.setText(String.format(Locale.US, "Score: %d   Lv %d   Left: %d",
                points, level, infoData.getTotalTrials() - cookiesDone));
    }

    void finished() {
        running = false;
        state = FINISHED;
        displayTrialsDone();

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setMessage(GameUtils.finishedMessage(totalHits, infoData.getTotalTrials(), bestStreak, totalTime)
                + "\nScore: " + points + " points, top level " + topLevel + ".");
        builder.setCancelable(false);
        builder.setPositiveButton("OK", (dialog, which) -> {
            Intent intent = new Intent(this, ResultsActivity.class);
            intent.putExtra(ResultsActivity.EXTRA_RESULTS, results.toString());
            intent.putExtra(ResultsActivity.EXTRA_SHOW_RISK, false);
            startActivity(intent);
            finish();
        });
        builder.setNegativeButton("BACK", (dialog, which) -> finish());
        GameUtils.showLarge(builder.create());
    }
}
