package com.example.cookiecollector;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView; // Import TextView

import androidx.appcompat.app.AppCompatActivity;

public class ResultsActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_results);

        // Find the views
        Button restartButton = findViewById(R.id.restartButton);
        TextView resultsTextView = findViewById(R.id.resultsTextView);

        // Get the Intent that started this activity
        Intent intent = getIntent();
        // Retrieve the string data using the same key
        String resultsData = intent.getStringExtra("key_results_data");

        // Set the text of the TextView to the results data
        if (resultsData != null && !resultsData.isEmpty()) {
            resultsTextView.setText(resultsData);
        } else {
            resultsTextView.setText("No results data found.");
        }

        // Set an OnClickListener for the restart button
        restartButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Create an Intent to go back to MainActivity
                Intent intent = new Intent(ResultsActivity.this, MainActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(intent);
                finish(); // Finish the current ResultsActivity
            }
        });
    }
}


