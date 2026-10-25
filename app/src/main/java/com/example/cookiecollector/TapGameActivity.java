package com.example.cookiecollector;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;

import java.io.File;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;

/**
 * Tap game: cookies appear one at a time in a 4x3 grid and the user taps each as quickly
 * as possible. Each touch is recorded, and the timings feed the Parkinson's risk estimate.
 */
public class TapGameActivity extends AppCompatActivity {
    private static final int INSTRUCTIONS = 0;
    private static final int GAME = 1;
    private static final int PAUSE = 2;
    private static final int FINISHED = 3;

    // A cookie that isn't tapped is skipped after this long
    private static final long TRIAL_TIMEOUT_MILLIS = 10000;
    private static final boolean SAVE_DATA = false;

    UserAndStudyInfo infoData;
    // Three grids stacked in the same place: invisible hit circles, cookies, and check/cross marks
    TableLayout circleGrid;
    TableLayout cookieGrid;
    TableLayout symbolGrid;
    EffectsOverlayView effectsView;
    GameSounds sounds;

    ImageView currentCircle = null;
    ImageView currentCookie = null;
    ImageView currentSymbol = null;

    int state = INSTRUCTIONS;
    int trialsDone = 0;
    long recentTime;
    long totalTime = 0;
    boolean running = false;
    int totalHits = 0;
    int streak = 0;
    int bestStreak = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_tap_game);

        infoData = (UserAndStudyInfo) getIntent().getSerializableExtra(SetupActivity.EXTRA_INFO);

        circleGrid = findViewById(R.id.circleGrid);
        cookieGrid = findViewById(R.id.cookieGrid);
        symbolGrid = findViewById(R.id.symbolGrid);
        effectsView = findViewById(R.id.effectsView);
        sounds = new GameSounds(this);

        buildGrids();
        displayTrialsDone();

        Button pauseButton = findViewById(R.id.pauseButton);
        pauseButton.setOnClickListener(v -> {
            if (state == GAME) {
                running = false;
                state = PAUSE;
                hideCookie();
                pauseButton.setText("RESUME");
            }
            else if (state == PAUSE) {
                running = true;
                state = GAME;
                showCookie();
                recentTime = System.currentTimeMillis();
                pauseButton.setText("PAUSE");
            }
        });

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.tap_game_name);
        builder.setMessage(R.string.instructions);
        builder.setCancelable(false);
        builder.setPositiveButton("BEGIN", (dialog, which) -> {
            if (state == INSTRUCTIONS) {
                running = true;
                startClock();
                state = GAME;
                newCircle();
            }
            dialog.cancel();
        });
        builder.setNegativeButton("BACK", (dialog, which) -> {
            if (state == INSTRUCTIONS) finish();
        });
        GameUtils.showLarge(builder.create());
    }

    private void buildGrids() {
        float targetRadiusPx = mmToPx(infoData.getRadius());
        float halfDistancePx = mmToPx(infoData.getDistance()) / 2;
        int cellSize = (int) (2 * (targetRadiusPx + halfDistancePx));
        symbolGrid.setTranslationY(-(targetRadiusPx + mmToPx(3 + 1)));

        for (int i = 0; i < infoData.getRows(); i++) {
            TableRow circleRow = new TableRow(this);
            TableRow cookieRow = new TableRow(this);
            TableRow symbolRow = new TableRow(this);
            for (int j = 0; j < infoData.getColumns(); j++) {
                // The circle is the tap target; it stays invisible
                ImageView circle = gridCell(R.drawable.circle, cellSize, (int) halfDistancePx);
                circle.setVisibility(View.INVISIBLE);
                circleRow.addView(circle);

                ImageView cookie = gridCell(R.drawable.cookie, cellSize, (int) halfDistancePx);
                cookie.setVisibility(View.INVISIBLE);
                cookieRow.addView(cookie);

                int symbolPadding = (int) (halfDistancePx + targetRadiusPx - (mmToPx(6) / 2));
                ImageView symbol = gridCell(R.drawable.checkmark, cellSize, symbolPadding);
                symbol.setAlpha(0f);
                symbolRow.addView(symbol);
            }
            circleGrid.addView(circleRow);
            cookieGrid.addView(cookieRow);
            symbolGrid.addView(symbolRow);
        }
    }

    private ImageView gridCell(int drawable, int size, int padding) {
        ImageView v = new ImageView(this);
        v.setScaleType(ImageView.ScaleType.FIT_CENTER);
        v.setImageResource(drawable);
        v.setAdjustViewBounds(true);
        v.setMaxHeight(size);
        v.setMaxWidth(size);
        v.setPadding(padding, padding, padding, padding);
        return v;
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

    boolean tappedInCircle(MotionEvent e) {
        int[] corner = new int[2];
        currentCircle.getLocationOnScreen(corner);
        float r = mmToPx(infoData.getRadius());
        float d = mmToPx(infoData.getDistance()) / 2;
        float dx = e.getRawX() - (corner[0] + r + d);
        float dy = e.getRawY() - (corner[1] + r + d);
        return Math.sqrt(dx * dx + dy * dy) <= r;
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        if (state != GAME) return true;
        if (e.getAction() == MotionEvent.ACTION_UP) {
            currentSymbol.animate().alpha(0f).setDuration(200).setListener(null);
            nextTrial();
        }
        else if (e.getAction() == MotionEvent.ACTION_DOWN) {
            boolean correct = tappedInCircle(e);
            infoData.addTouchpoint(trialsDone, pxToMm(e.getRawX()), pxToMm(e.getRawY()), System.currentTimeMillis() - recentTime, correct);
            if (correct) {
                totalHits++;
                streak++;
                bestStreak = Math.max(bestStreak, streak);
                sounds.playHit(streak);
                celebrateHit();
                showSymbol(R.drawable.checkmark, R.color.checkmark_green);
            }
            else {
                streak = 0;
                sounds.playMiss();
                effectsView.getEffects().popup("Miss", effectsView.toViewX(e.getRawX()), effectsView.toViewY(e.getRawY()) - mmToPx(6), GameEffects.RED, 26);
                showSymbol(R.drawable.crossmark, R.color.crossmark_red);
                GameUtils.vibrate(this, 40);
            }
        }
        return true;
    }

    private void showSymbol(int drawable, int color) {
        currentSymbol.setImageResource(drawable);
        currentSymbol.setImageTintList(ColorStateList.valueOf(ContextCompat.getColor(this, color)));
        currentSymbol.setAlpha(1f);
    }

    private void celebrateHit() {
        int[] loc = new int[2];
        currentCookie.getLocationOnScreen(loc);
        float x = effectsView.toViewX(loc[0] + currentCookie.getWidth() / 2f);
        float y = effectsView.toViewY(loc[1] + currentCookie.getHeight() / 2f);
        if (effectsView.getEffects().hit(x, y, mmToPx(infoData.getRadius()), streak)) sounds.playMilestone();
    }

    private void nextTrial() {
        hideCookie();
        trialsDone++;
        displayTrialsDone();
        newCircle();
    }

    void newCircle() {
        if (trialsDone == infoData.getTotalTrials()) {
            finished();
            return;
        }
        if (state == PAUSE) return;
        recentTime = System.currentTimeMillis();

        int n = infoData.getCorrectSequence(trialsDone);
        int row = n / infoData.getColumns();
        int column = n % infoData.getColumns();
        currentCircle = (ImageView) ((TableRow) circleGrid.getChildAt(row)).getChildAt(column);
        currentCookie = (ImageView) ((TableRow) cookieGrid.getChildAt(row)).getChildAt(column);
        currentSymbol = (ImageView) ((TableRow) symbolGrid.getChildAt(row)).getChildAt(column);
        showCookie();

        // Skip the cookie if it hasn't been tapped in time
        new Handler().postDelayed(() -> {
            if (state == GAME && System.currentTimeMillis() - recentTime >= TRIAL_TIMEOUT_MILLIS - 10) nextTrial();
        }, TRIAL_TIMEOUT_MILLIS);

        int[] xy = new int[2];
        currentCircle.getLocationOnScreen(xy);
        float t = mmToPx(infoData.getRadius() + infoData.getDistance() / 2);
        infoData.addToCorrectCoordinates(trialsDone, pxToMm(xy[0] + t), pxToMm(xy[1] + t));
    }

    void displayTrialsDone() {
        TextView trialsTextView = findViewById(R.id.trialsTextView);
        trialsTextView.setText(GameUtils.scoreBar(totalHits, infoData.getTotalTrials() - trialsDone, totalTime));
    }

    void finished() {
        running = false;
        state = FINISHED;
        displayTrialsDone();
        if (SAVE_DATA) save();
        ReportData report = TapAnalysis.analyze(infoData);

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setMessage(GameUtils.finishedMessage(totalHits, infoData.getTotalTrials(), bestStreak, totalTime));
        builder.setCancelable(false);
        builder.setPositiveButton("OK", (dialog, which) -> {
            Intent intent = new Intent(this, GameReportActivity.class);
            intent.putExtra(GameReportActivity.EXTRA_REPORT, report);
            startActivity(intent);
            // So the user can't go back to the finished game
            finish();
        });
        builder.setNegativeButton("BACK", (dialog, which) -> finish());
        GameUtils.showLarge(builder.create());
    }

    /** Writes every touch to a CSV in the app's external files folder. */
    void save() {
        UserAndStudyInfo x = infoData;
        Date d = Calendar.getInstance().getTime();
        String tStamp = d.getMonth() + "-" + d.getDate() + "_" + d.getHours() + "-" + d.getMinutes();
        String fileName = x.getId() + "_" + x.getRows() + "x" + x.getColumns() + "_" + x.getDiameter() + "mmDiameter_"
                + (x.getDistance() + x.getDiameter()) + "mmBetwTargetCenters_" + tStamp + ".csv";
        File f = new File(getExternalFilesDir(null), fileName);
        try (PrintWriter pw = new PrintWriter(f)) {
            pw.println("ID,Gender,Age,Hand,Finger,Rows,Columns,Radius,Diameter,DistBetwTargetCenters,TotalTrials,TrialNumber,CorrectCircle,CorrectX,CorrectY,PrevCorrectX,PrevCorrectY,UserX,UserY,Time,DistFromPrevious,Correct");
            String c = ",";
            String studyColumns = x.getId() + c + x.getGender() + c + x.getAge() + c + x.getHand() + c + x.getFinger() + c + x.getRows() + c
                    + x.getColumns() + c + x.getRadius() + c + x.getDiameter() + c + (x.getDistance() + x.getDiameter()) + c + x.getTotalTrials();
            float[] prevCoor = {-1, -1};
            for (int i = 0; i < x.getTotalTrials(); i++) {
                String trialColumns = studyColumns + c + i + c + x.getCorrectSequence(i) + c;
                float[] corrCoor = x.getCorrectCoordinates(i);
                float dist = (float) Math.sqrt(Math.pow(prevCoor[0] - corrCoor[0], 2) + Math.pow(prevCoor[1] - corrCoor[1], 2));
                String coordColumns = corrCoor[0] + c + corrCoor[1] + c + prevCoor[0] + c + prevCoor[1] + c;
                ArrayList<UserAndStudyInfo.Touchpoint> ts = x.getTouchpoints(i);
                for (UserAndStudyInfo.Touchpoint t : ts) {
                    pw.println(trialColumns + coordColumns + t.x + c + t.y + c + t.milliseconds + c + dist + c + t.correct);
                }
                if (ts.isEmpty()) pw.println(trialColumns + coordColumns + (-1) + c + (-1) + c + (-1) + c + dist + c + false);
                prevCoor = corrCoor;
            }
            Log.d("FileSaveSuccess", "Data successfully saved to: " + f.getAbsolutePath());
        }
        catch (Exception e) {
            Log.e("FileSaveError", "Failed to save the file at: " + f.getAbsolutePath(), e);
        }
    }

    float mmToPx(float mm) { return GameUtils.mmToPx(this, mm); }
    float pxToMm(float px) { return GameUtils.pxToMm(this, px); }

    void showCookie() {
        currentCookie.setVisibility(View.VISIBLE);
        int[] loc = new int[2];
        currentCookie.getLocationOnScreen(loc);
        effectsView.showHalo(loc[0] + currentCookie.getWidth() / 2f, loc[1] + currentCookie.getHeight() / 2f, mmToPx(infoData.getRadius()));
    }

    void hideCookie() {
        currentCookie.setVisibility(View.INVISIBLE);
        effectsView.hideHalo();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        sounds.release();
    }
}
