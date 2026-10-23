package com.example.cookiecollector;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.RadioGroup;

/** First screen: participant details, study settings and which game to play. */
public class SetupActivity extends AppCompatActivity {
    static final String EXTRA_INFO = "info";

    // The grid is fixed at 4 rows x 3 columns; the trial sequences in assets/ are built for it
    private static final String LAYOUT = "4x3";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_setup);

        Button startGameButton = findViewById(R.id.startGameButton);
        startGameButton.setOnClickListener(view -> {
            String id = ((EditText) findViewById(R.id.editTextUserID)).getText().toString();
            String age = ((EditText) findViewById(R.id.editTextUserAge)).getText().toString();
            String gender = checkedText(R.id.genderRadioGroup);
            String hand = checkedText(R.id.handRadioGroup);
            String finger = checkedText(R.id.fingerRadioGroup);
            String size = checkedText(R.id.sizeRadioGroup);
            String session = checkedText(R.id.sessionRadioGroup);

            UserAndStudyInfo infoData = new UserAndStudyInfo(getApplicationContext(), id, gender, age, hand, finger, LAYOUT, size, session);

            int game = ((RadioGroup) findViewById(R.id.gameRadioGroup)).getCheckedRadioButtonId();
            Class<?> gameClass = game == R.id.swipeGameRadioButton ? SwipeGameActivity.class
                    : game == R.id.spiralGameRadioButton ? SpiralGameActivity.class
                    : TapGameActivity.class;
            Intent intent = new Intent(getApplicationContext(), gameClass);
            intent.putExtra(EXTRA_INFO, infoData);
            startActivity(intent);
        });
    }

    private String checkedText(int radioGroupId) {
        RadioGroup group = findViewById(radioGroupId);
        return ((RadioButton) findViewById(group.getCheckedRadioButtonId())).getText().toString();
    }
}
