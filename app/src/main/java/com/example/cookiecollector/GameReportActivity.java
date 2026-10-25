package com.example.cookiecollector;

import android.content.Intent;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

/** End-of-game screen shared by all games: the motor analysis report on a single screen. */
public class GameReportActivity extends AppCompatActivity {
    static final String EXTRA_REPORT = "report";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_game_report);

        GameReportView view = findViewById(R.id.gameReportView);
        view.setReport((ReportData) getIntent().getSerializableExtra(EXTRA_REPORT));

        findViewById(R.id.restartButton).setOnClickListener(v -> {
            Intent restartIntent = new Intent(this, SetupActivity.class);
            restartIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(restartIntent);
            finish();
        });
    }
}
