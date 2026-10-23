package com.example.cookiecollector;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.widget.Button;
import android.widget.TextView;

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

    UserAndStudyInfo infoData;
    FallingCookiesView fallingCookiesView;
    GameSounds sounds;
    float cookieDiameterMm;

    int state = INSTRUCTIONS;
    int cookiesDone = 0;
    int totalHits = 0;
    int streak = 0;
    int bestStreak = 0;
    long totalTime = 0;
    boolean running = false;
    final StringBuilder results = new StringBuilder("Cookie,Size,Duration,Success\n");

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
        sounds.playHit(streak);
        if (fallingCookiesView.getEffects().hit(x, y, fallingCookiesView.getRadiusPx(), streak)) sounds.playMilestone();
        addResult(cookie, true);
    }

    @Override
    public void onCookieMissed(FallingCookiesView.Cookie cookie) {
        streak = 0;
        sounds.playMiss();
        GameUtils.vibrate(this, 40);
        addResult(cookie, false);
    }

    @Override
    public void onAllCookiesDone() {
        finished();
    }

    void addResult(FallingCookiesView.Cookie cookie, boolean success) {
        results.append(cookie.number).append(",");
        results.append(String.format("%.1f", cookieDiameterMm)).append(",");
        results.append(String.format("%.3f", cookie.fallingMillis / 1000f)).append(",");
        results.append(success).append("\n");
        cookiesDone++;
        displayTrialsDone();
    }

    void displayTrialsDone() {
        TextView trialsTextView = findViewById(R.id.trialsTextView);
        trialsTextView.setText(GameUtils.scoreBar(totalHits, infoData.getTotalTrials() - cookiesDone, totalTime));
    }

    void finished() {
        running = false;
        state = FINISHED;
        displayTrialsDone();

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setMessage(GameUtils.finishedMessage(totalHits, infoData.getTotalTrials(), bestStreak, totalTime));
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
