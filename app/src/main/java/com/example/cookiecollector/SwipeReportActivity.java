package com.example.cookiecollector;

import android.content.Intent;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

/** End-of-game screen for the swipe game: the swipe kinematics report on a single screen. */
public class SwipeReportActivity extends AppCompatActivity {
    static final String EXTRA_REPORT = "swipeReport";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_swipe_report);

        SwipeReportView view = findViewById(R.id.swipeReportView);
        view.setReport((SwipeAnalysis.Report) getIntent().getSerializableExtra(EXTRA_REPORT));

        findViewById(R.id.restartButton).setOnClickListener(v -> {
            Intent restartIntent = new Intent(this, SetupActivity.class);
            restartIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(restartIntent);
            finish();
        });
    }
}
