package com.example.cookiecollector;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.RadioGroup;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Button startGameButton = findViewById(R.id.startGameButton);
        startGameButton.setOnClickListener(new OnClickListener(){
            @Override
            public void onClick(View view) {
                EditText idEditText = findViewById(R.id.editTextUserID);
                RadioGroup genderRadioGroup = findViewById(R.id.genderRadioGroup);
                EditText ageEditText = findViewById(R.id.editTextUserAge);
                RadioGroup handRadioGroup = findViewById(R.id.handRadioGroup);
                RadioGroup fingerRadioGroup = findViewById(R.id.fingerRadioGroup);
                RadioGroup layoutRadioGroup = findViewById(R.id.layoutRadioGroup);
                RadioGroup sizeRadioGroup = findViewById(R.id.sizeRadioGroup);
//                RadioGroup distanceRadioGroup = findViewById(R.id.distanceRadioGroup);
                RadioGroup sessionRadioGroup = findViewById(R.id.sessionRadioGroup);

                String id = idEditText.getText().toString();
                String gender = ((RadioButton)findViewById(genderRadioGroup.getCheckedRadioButtonId())).getText().toString();
                String age = ageEditText.getText().toString();
                String hand = ((RadioButton)findViewById(handRadioGroup.getCheckedRadioButtonId())).getText().toString();
                String finger = ((RadioButton)findViewById(fingerRadioGroup.getCheckedRadioButtonId())).getText().toString();
//                String layout = ((RadioButton)findViewById(layoutRadioGroup.getCheckedRadioButtonId())).getText().toString();
                String layout = "4x3";
                String size = ((RadioButton)findViewById(sizeRadioGroup.getCheckedRadioButtonId())).getText().toString();
//                String distance = ((RadioButton)findViewById(distanceRadioGroup.getCheckedRadioButtonId())).getText().toString();
                String session = ((RadioButton)findViewById(sessionRadioGroup.getCheckedRadioButtonId())).getText().toString();

//                UserAndStudyInfo infoData = new UserAndStudyInfo(getApplicationContext(), id, gender, age, hand, finger, layout, size, distance, session);
                UserAndStudyInfo infoData = new UserAndStudyInfo(getApplicationContext(), id, gender, age, hand, finger, layout, size, session);

                Bundle infoDataBundle = new Bundle();
                infoDataBundle.putSerializable("data", infoData);
                Intent intent = new Intent(getApplicationContext(), GameActivity.class);
                intent.putExtra("data", infoDataBundle);

                startActivity(intent);
            }
        });


    }
}