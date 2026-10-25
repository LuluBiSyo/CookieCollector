package com.example.cookiecollector;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.widget.Button;
import android.widget.TextView;

import java.io.File;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;

/**
 * Spiral game: the player drags a cookie out of a spiral tunnel without touching the walls.
 * Each wall contact is a bump: an alarm plays and it is counted as an error. Every touch
 * sample is saved to a CSV in the app's files folder for motor analysis.
 */
public class SpiralGameActivity extends AppCompatActivity implements SpiralTunnelView.Listener {
    private static final int INSTRUCTIONS = 0;
    private static final int GAME = 1;
    private static final int PAUSE = 2;
    private static final int FINISHED = 3;

    // Sizes follow the Target size setting: Large -> 5.6 mm cookie in an 11.6 mm tunnel, Small -> 4.2 mm in a 10.2 mm tunnel
    private static final float COOKIE_SCALE = 0.35f;
    private static final float CLEARANCE_MM = 3f; // free space on each side of the cookie
    private static final long NEXT_SPIRAL_DELAY_MILLIS = 1200;

    UserAndStudyInfo infoData;
    SpiralTunnelView spiralView;
    GameSounds sounds;
    float cookieDiameterMm;
    float tunnelWidthMm;
    int totalSpirals;

    int state = INSTRUCTIONS;
    int spiralsDone = 0;
    int score = 0;
    int streak = 0;
    int totalBumps = 0;
    long totalTime = 0;
    boolean running = false;
    final StringBuilder samples = new StringBuilder();
    final ArrayList<SpiralTunnelView.Result> results = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_spiral_game);

        infoData = (UserAndStudyInfo) getIntent().getSerializableExtra(SetupActivity.EXTRA_INFO);
        sounds = new GameSounds(this);
        totalSpirals = infoData.isWarmup() ? 1 : 3;

        cookieDiameterMm = infoData.getDiameter() * COOKIE_SCALE;
        tunnelWidthMm = cookieDiameterMm + 2 * CLEARANCE_MM;
        spiralView = findViewById(R.id.spiralTunnelView);
        spiralView.setup(tunnelWidthMm, cookieDiameterMm, samples, this);
        spiralView.newSpiral(0, true);

        displayScore();

        Button pauseButton = findViewById(R.id.pauseButton);
        pauseButton.setOnClickListener(v -> {
            if (state == GAME) {
                running = false;
                state = PAUSE;
                spiralView.pause();
                pauseButton.setText("RESUME");
            }
            else if (state == PAUSE) {
                running = true;
                state = GAME;
                spiralView.start();
                pauseButton.setText("PAUSE");
            }
        });

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.spiral_game_name);
        builder.setMessage(R.string.spiral_instructions);
        builder.setCancelable(false);
        builder.setPositiveButton("BEGIN", (dialog, which) -> {
            if (state == INSTRUCTIONS) {
                running = true;
                startClock();
                state = GAME;
                spiralView.start();
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
    public void onCollect() {
        score++;
        streak++;
        sounds.playCollect(streak);
        displayScore();
    }

    @Override
    public void onBumpStart() {
        totalBumps++;
        streak = 0;
        sounds.playAlarm();
        GameUtils.vibrate(this, 60);
        displayScore();
    }

    @Override
    public void onSpiralEscaped(SpiralTunnelView.Result r) {
        sounds.playMilestone();
        results.add(r);
        spiralsDone++;
        displayScore();
        new Handler().postDelayed(() -> {
            if (isFinishing()) return;
            // Alternate direction so both clockwise and counter-clockwise tracing are measured
            if (spiralsDone < totalSpirals) spiralView.newSpiral(spiralsDone, spiralsDone % 2 == 0);
            else finished();
        }, NEXT_SPIRAL_DELAY_MILLIS);
    }

    void displayScore() {
        TextView trialsTextView = findViewById(R.id.trialsTextView);
        int current = Math.min(spiralsDone + 1, totalSpirals);
        trialsTextView.setText(String.format(Locale.US, "Score: %d   Bumps: %d   %d/%d", score, totalBumps, current, totalSpirals));
    }

    void finished() {
        running = false;
        state = FINISHED;
        saveSamples();
        ReportData report = SpiralAnalysis.analyze(results);

        String message = String.format(Locale.US, "You escaped %d %s with %d %s in %d seconds!",
                totalSpirals, totalSpirals == 1 ? "spiral" : "spirals", totalBumps, totalBumps == 1 ? "bump" : "bumps", totalTime);
        if (totalBumps == 0) message += "\nPerfect, no bumps at all!";

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setMessage(message);
        builder.setCancelable(false);
        builder.setPositiveButton("OK", (dialog, which) -> {
            Intent intent = new Intent(this, GameReportActivity.class);
            intent.putExtra(GameReportActivity.EXTRA_REPORT, report);
            startActivity(intent);
            finish();
        });
        builder.setNegativeButton("BACK", (dialog, which) -> finish());
        GameUtils.showLarge(builder.create());
    }

    /** Writes every touch sample to spiral_<id>_<time>.csv in the app's external files folder. */
    void saveSamples() {
        UserAndStudyInfo x = infoData;
        String stamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
        File f = new File(getExternalFilesDir(null), "spiral_" + x.getId() + "_" + stamp + ".csv");
        String c = ",";
        String studyColumns = x.getId() + c + x.getGender() + c + x.getAge() + c + x.getHand() + c + x.getFinger() + c
                + String.format(Locale.US, "%.2f,%.2f,%.3f", cookieDiameterMm * spiralView.getScale(), tunnelWidthMm * spiralView.getScale(), spiralView.getScale()) + c;
        try (PrintWriter pw = new PrintWriter(f)) {
            pw.println("ID,Gender,Age,Hand,Finger,CookieDiameterMm,TunnelWidthMm,Scale,Spiral,Direction,TimeMs,Event,XMm,YMm,ArcMm,DeviationMm,Contact");
            for (String row : samples.toString().split("\n")) {
                if (!row.isEmpty()) pw.println(studyColumns + row);
            }
            Log.d("FileSaveSuccess", "Spiral samples saved to: " + f.getAbsolutePath());
        }
        catch (Exception e) {
            Log.e("FileSaveError", "Failed to save the file at: " + f.getAbsolutePath(), e);
        }
    }
}
