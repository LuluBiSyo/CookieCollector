package com.example.cookiecollector;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.style.ForegroundColorSpan;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

public class ResultsActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_results);

// Find the views
        TextView probabilityTextView = findViewById(R.id.probabilityTextView); // Find the new TextView
        Button restartButton = findViewById(R.id.restartButton);
        TableLayout tableLayout = findViewById(R.id.resultsTableLayout);

        // Get the data from the intent
        Intent intent = getIntent();
        String resultsData = intent.getStringExtra("key_results_data");
        // Retrieve the mean probability, provide a default value (e.g., -1.0f) if not found
        float meanProbability = intent.getFloatExtra("key_mean_probability", -1.0f);

        // Display the probability score with conditional coloring
        if (meanProbability >= 0) {
            float score = meanProbability * 100;
            String scoreText = String.format("%.1f%%", score);
            String fullText = "Parkinson's Disease Risk: " + scoreText;

            // Use a SpannableStringBuilder to apply color to only part of the text
            SpannableStringBuilder ssb = new SpannableStringBuilder(fullText);

            // Determine the color based on the score
            int scoreColor = (score >= 50) ? Color.RED : Color.GREEN;

            // Create a style span for the color
            ForegroundColorSpan colorSpan = new ForegroundColorSpan(scoreColor);

            // Find the start and end positions of the score number within the full text
            int startIndex = fullText.indexOf(scoreText);
            int endIndex = startIndex + scoreText.length();

            // Apply the color span to only the score number
            ssb.setSpan(colorSpan, startIndex, endIndex, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);

            // Set the styled text on the TextView
            probabilityTextView.setText(ssb);

        } else {
            probabilityTextView.setText("Risk score could not be calculated.");
            probabilityTextView.setTextColor(Color.BLACK); // Set default color for the error message
        }



        // Populate the table
        if (resultsData != null && !resultsData.isEmpty()) {
            populateTable(tableLayout, resultsData);
        } else {
            // Display a message if no data is found
            TextView noDataTextView = new TextView(this);
            noDataTextView.setText("No results data found.");
            noDataTextView.setPadding(10, 10, 10, 10);
            tableLayout.addView(noDataTextView);
        }

        // Set listener for the restart button
        restartButton.setOnClickListener(v -> {
            Intent restartIntent = new Intent(ResultsActivity.this, MainActivity.class);
            restartIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(restartIntent);
            finish();
        });
    }

    private void populateTable(TableLayout tableLayout, String data) {
        String[] lines = data.split("\n");

        // First line is the header
        String[] headerCells = lines[0].split(",");
        TableRow headerRow = new TableRow(this);
        for (String headerText : headerCells) {
            TextView headerTextView = createTableCell(headerText, true); // `true` for bold header
            headerRow.addView(headerTextView);
        }
        tableLayout.addView(headerRow);

        // Add the rest of the data rows
        for (int i = 1; i < lines.length; i++) {
            String[] dataCells = lines[i].split(",");
            TableRow dataRow = new TableRow(this);
            for (String cellText : dataCells) {
                TextView dataTextView = createTableCell(cellText, false); // `false` for regular text
                dataRow.addView(dataTextView);
            }
            tableLayout.addView(dataRow);
        }
    }

    // Helper method to create a TextView for a table cell
    private TextView createTableCell(String text, boolean isHeader) {
        TextView textView = new TextView(this);
        textView.setText(text);
        textView.setTextColor(Color.BLACK);
        textView.setTextSize(16); // Larger text size
        textView.setGravity(Gravity.CENTER);
        textView.setPadding(10, 10, 10, 10); // Add some padding

        if (isHeader) {
            textView.setTypeface(null, Typeface.BOLD);
        }

        return textView;
    }
}



