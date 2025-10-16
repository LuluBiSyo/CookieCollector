package com.example.cookiecollector;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
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
        Button restartButton = findViewById(R.id.restartButton);
        TableLayout tableLayout = findViewById(R.id.resultsTableLayout);

        // Get the data from the intent
        Intent intent = getIntent();
        String resultsData = intent.getStringExtra("key_results_data");

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



