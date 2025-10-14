package com.example.cookiecollector;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.media.MediaPlayer;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.TypedValue;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;

import java.io.File;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;

public class GameActivity extends AppCompatActivity {
    UserAndStudyInfo infoData;
    TableLayout gameTableLayout;
    TableLayout cookies;
    TableLayout symbols;

    int trialsDone = 0;
    ImageView currentCircle = null;
    ImageView prevCircle = null;
    ImageView currentCookie = null;
    ImageView currentSymbol = null;
    int currentCircleNumber = -1;
//    ArrayList<Integer> leftInSequence = new ArrayList<>();
    long recentTime;
    long totalTime = 0;
    boolean running = false;

    int state;
    int INSTRUCTIONS = 0;
    int GAME = 1;
    int PAUSE = 2;
    int FINISHED = 3;

    int totalHits = 0;

    //boolean clickedCorrectCircle = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_game);

        state = INSTRUCTIONS;

        Intent intent = getIntent();
        Bundle infoDataBundle = intent.getBundleExtra("data");
        infoData = (UserAndStudyInfo) infoDataBundle.getSerializable("data");

        LinearLayout l = findViewById(R.id.fileNameAndSaveLayout);
        l.setVisibility(View.GONE);

        gameTableLayout = findViewById(R.id.gameTableLayout);
        cookies = findViewById(R.id.cookies);
        symbols = findViewById(R.id.symbols);

        float targetRadiusPx = mmToPx(infoData.getRadius());
        float halfDistancePx = mmToPx(infoData.getDistance())/2;
        symbols.setTranslationY(-(targetRadiusPx + mmToPx(3+1)));
        //Log.d("radius", ""+r);
        for(int i = 0; i < infoData.getRows(); i++){
            TableRow tr = new TableRow(this);
            TableRow mtr = new TableRow(this);
            TableRow str = new TableRow(this);
            for(int j = 0; j < infoData.getColumns(); j++){
                ImageView circle = new ImageView(this);//ic_notification_overlay
                circle.setScaleType(ImageView.ScaleType.FIT_CENTER);
                circle.setImageResource(R.drawable.circle);
                circle.setAdjustViewBounds(true);
                circle.setMaxHeight((int)(2*(targetRadiusPx+halfDistancePx)));
                circle.setMaxWidth((int)(2*(targetRadiusPx+halfDistancePx)));
                circle.setPadding((int) halfDistancePx, (int) halfDistancePx, (int) halfDistancePx, (int) halfDistancePx);
                // Controls whether the circles for cookies will be visible
                circle.setVisibility(View.INVISIBLE);
                tr.addView(circle);

                ImageView cookie = new ImageView(this);
                cookie.setScaleType(ImageView.ScaleType.FIT_CENTER);
                cookie.setImageResource(R.drawable.cookie);
                cookie.setAdjustViewBounds(true);
                cookie.setMaxHeight((int)(2*(targetRadiusPx+halfDistancePx)));
                cookie.setMaxWidth((int)(2*(targetRadiusPx+halfDistancePx)));
                // original padding which shows part of circles
//                int cookiePadding = (int) halfDistancePx + (int)(targetRadiusPx/2);
                int cookiePadding = (int) halfDistancePx;
                cookie.setPadding(cookiePadding, cookiePadding, cookiePadding, cookiePadding);
                cookie.setVisibility(View.INVISIBLE);
                mtr.addView(cookie);

                ImageView symbol = new ImageView(this);
                symbol.setScaleType(ImageView.ScaleType.FIT_CENTER);
                symbol.setImageResource(R.drawable.checkmark);
                symbol.setImageTintList(ColorStateList.valueOf(ContextCompat.getColor(this, R.color.checkmark_green)));
                symbol.setAdjustViewBounds(true);
                symbol.setMaxHeight((int)(2*(targetRadiusPx+halfDistancePx)));
                symbol.setMaxWidth((int)(2*(targetRadiusPx+halfDistancePx)));
                //symbol.setPadding(D+(int)(3*r/4), D+(int)(3*r/4), D+(int)(3*r/4), D+(int)(3*r/4));
                int symbolPadding = (int)(halfDistancePx+targetRadiusPx-(mmToPx(6)/2));
                symbol.setPadding(symbolPadding, symbolPadding, symbolPadding, symbolPadding);
//                symbol.setVisibility(View.INVISIBLE);
                symbol.setAlpha((float)0);
                str.addView(symbol);

            }
            gameTableLayout.addView(tr);
            cookies.addView(mtr);
            symbols.addView(str);
        }

        displayTrialsDone();


        Button pauseButton = findViewById(R.id.pauseButton);
        pauseButton.setText("PAUSE");
        pauseButton.setOnTouchListener(new View.OnTouchListener(){
            @Override
            public boolean onTouch(View view, MotionEvent e){
                if(e.getAction() != MotionEvent.ACTION_DOWN) return true;
                if(state == GAME){
                    running = false;
                    state = PAUSE;
                    hideCookie();
                    Button pauseButton = findViewById(R.id.pauseButton);
                    pauseButton.setText("RESUME");
                }
                else if(state == PAUSE){
                    running = true;
                    state = GAME;
                    showCookie();
                    recentTime = System.currentTimeMillis();
                    Button pauseButton = findViewById(R.id.pauseButton);
                    pauseButton.setText("PAUSE");
                }
                return false;
            }
        });

//        TextView instructionsText = findViewById(R.id.instructionsTextView);
//        DisplayMetrics displayMetrics = new DisplayMetrics();
//        getWindowManager().getDefaultDisplay().getMetrics(displayMetrics);
//        int w = displayMetrics.widthPixels;
//        instructionsText.setWidth((int)(w*3/4));

        AlertDialog.Builder builder = new AlertDialog.Builder(GameActivity.this);
        builder.setMessage(R.string.instructions);
        builder.setCancelable(false);

        builder.setPositiveButton("BEGIN", (DialogInterface.OnClickListener) (dialog, which) ->{
            if (state == INSTRUCTIONS) {
                running = true;

                final Handler handler = new Handler();
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        if (running) {
                            totalTime++;
                        }
                        displayTrialsDone();
                        handler.postDelayed(this, 1000);
                    }
                });

                state = GAME;
//                TextView instructionsTextView = findViewById(R.id.instructionsTextView);
//                instructionsTextView.setVisibility(View.INVISIBLE);
                newCircle();
            }
            dialog.cancel();
        });
        builder.setNegativeButton("BACK", (DialogInterface.OnClickListener) (dialog, which) ->{
            if(state == INSTRUCTIONS){
                finish();
            }
        });

        AlertDialog alertDialog = builder.create();
        alertDialog.show();

        TextView textView = (TextView) alertDialog.findViewById(android.R.id.message);
        textView.setTextSize(22);

        Button begin = (Button) alertDialog.findViewById(android.R.id.button1);
        begin.setTextSize(18);
        Button back = (Button) alertDialog.findViewById(android.R.id.button2);
        back.setTextSize(18);
    }


    boolean tappedInCircle(MotionEvent e){

        if(e.getAction() != MotionEvent.ACTION_DOWN) return true;
        int[] circleLocation = new int[2]; //pixels
        currentCircle.getLocationOnScreen(circleLocation);

        float r = mmToPx(infoData.getRadius());
        float d = mmToPx(infoData.getDistance())/2;

        circleLocation[0] += (r+d);
        circleLocation[1] += (r+d);
        return inCircle(circleLocation, new float[] {e.getRawX(), e.getRawY()});
    }
    boolean inCircle(int[] cL, float[] tL){ //Everything in pixels; cl = circleLocation is the center; tL = tappedLocation

        double radius = mmToPx(infoData.getRadius());

        double distance = Math.sqrt((tL[0]-cL[0])*(tL[0]-cL[0]) + (tL[1]-cL[1])*(tL[1]-cL[1]));
        return distance<=radius;

    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        if(e.getAction() == MotionEvent.ACTION_UP){
            if(state == GAME){
                currentSymbol.animate().alpha(0f).setDuration(200).setListener(null);
//                currentSymbol.setVisibility(View.INVISIBLE);
//                currentSymbol.setAlpha((float)1);
                hideCookie();
                currentCircleNumber = -1;
                trialsDone++;
                displayTrialsDone();
                newCircle();
            }
        }
        else if(e.getAction() == MotionEvent.ACTION_DOWN) {
            if (state == GAME) {
                Context c = getApplicationContext();
                boolean correct = tappedInCircle(e);
                infoData.addTouchpoint(trialsDone, pxToMm(e.getRawX(), c), pxToMm(e.getRawY(), c), System.currentTimeMillis() - recentTime, correct);
                if(correct){
                    MediaPlayer sound = MediaPlayer.create(getApplicationContext(), R.raw.success_sound_99);
                    sound.start();
                    currentSymbol.setImageResource(R.drawable.checkmark);
                    currentSymbol.setImageTintList(ColorStateList.valueOf(ContextCompat.getColor(this, R.color.checkmark_green)));
//                    currentSymbol.setVisibility(View.VISIBLE);
                    currentSymbol.setAlpha((float)1);
//                    Vibrator v = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
//                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) v.vibrate(VibrationEffect.createOneShot(40, VibrationEffect.DEFAULT_AMPLITUDE));
//                    else v.vibrate(40);
                    totalHits++;
                }
                else{
                    //Zapsplat sound effect
                    MediaPlayer sound = MediaPlayer.create(getApplicationContext(), R.raw.miss_sound);
                    sound.start();
                    currentSymbol.setImageResource(R.drawable.crossmark);
                    currentSymbol.setImageTintList(ColorStateList.valueOf(ContextCompat.getColor(this, R.color.crossmark_red)));
//                    currentSymbol.setVisibility(View.VISIBLE);
                    currentSymbol.setAlpha((float)1);
                    Vibrator v = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) v.vibrate(VibrationEffect.createOneShot(40, VibrationEffect.DEFAULT_AMPLITUDE));
                    else v.vibrate(40);
                }
            }

        }
        return true;
    }

    void newCircle(){
        if(trialsDone == infoData.getTotalTrials()){
            finished();
            return;
        }
        createNewCircle();
    }

    void createNewCircle(){
        if(state == PAUSE) return;
        recentTime = System.currentTimeMillis();

        int n = infoData.getCorrectSequence(trialsDone);

        TableRow row = (TableRow) gameTableLayout.getChildAt(n/infoData.getColumns());
        prevCircle = (ImageView) currentCircle;
        currentCircle = (ImageView) row.getChildAt(n%infoData.getColumns());
        TableRow mrow = (TableRow) cookies.getChildAt(n/infoData.getColumns());
        currentCookie = (ImageView) mrow.getChildAt(n%infoData.getColumns());
        TableRow srow = (TableRow) symbols.getChildAt(n/infoData.getColumns());
        currentSymbol = (ImageView) srow.getChildAt(n%infoData.getColumns());
        showCookie();


        final Handler handler = new Handler();
        handler.postDelayed(new Runnable() {
            @Override
            public void run(){
                if(state == FINISHED) handler.removeCallbacks(this::run);
                if(state == GAME){
                    //if(currentCircleNumber!=-1 && circle==currentCircle && tappedInCircle(e)){
                    if(System.currentTimeMillis()-recentTime >= 9990){
//                                circle.setImageTintList(ColorStateList.valueOf(ContextCompat.getColor(circle.getContext(), R.color.green)));
                        hideCookie();
                        currentCircleNumber = -1;
                        trialsDone++;
                        displayTrialsDone();
                        newCircle();
                    }
                }

            }
        }, 10000);


        Context c = getApplicationContext();
        int[] xy = new int[2];
        currentCircle.getLocationOnScreen(xy);
        float t = mmToPx(infoData.getRadius()+infoData.getDistance()/2);
        infoData.addToCorrectCoordinates(trialsDone, pxToMm(xy[0] + t, c), pxToMm(xy[1] + t, c));
        currentCircleNumber = n;
    }


    void displayTrialsDone(){
        TextView trialsTextView = findViewById(R.id.trialsTextView);
        int s = (int) (totalTime%60);
        int m = (int) (totalTime/60);
        String sec;
        String min;
        if(s == 0) sec = "00";
        else if(s < 10) sec = "0" + s;
        else sec = s+"";
        if(m == 0) min = "00";
        else if(m < 10) min = "0" + m;
        else min = m+"";
        trialsTextView.setText("Time: " + min + ":" + sec + "   Trials left: " + (infoData.getTotalTrials() - trialsDone));
    }

    void finished(){
        displayTrialsDone();
        running = false;
        state = FINISHED;
        save();

        AlertDialog.Builder builder = new AlertDialog.Builder(GameActivity.this);
        String s = "";
        int minutes = (int) (totalTime / 60);
        int seconds = (int) (totalTime % 60);
        String timeString;

        if (minutes > 0) {
            String minuteWord = (minutes == 1) ? "minute" : "minutes";
            String secondWord = (seconds == 1) ? "second" : "seconds";
            timeString = String.format("%d %s and %d %s", minutes, minuteWord, seconds, secondWord);
        } else {
            timeString = String.format("%d seconds", seconds);
        }

        if (totalHits == 1) {
            s = "You collected 1 cookie in " + timeString + ".";
        } else {
            s = "You collected " + totalHits + " cookies in " + timeString + "! Well done!";
        }

        // Create results string that will be passed to the result activity
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append("Trial, Distance, Size of Cooke, Duration(s), Success");
        stringBuilder.append("\n");

        float[] prevCoor = {-1, -1};
        for(int i = 0; i < infoData.getTotalTrials(); i++){
            int cir = infoData.getCorrectSequence(i);
            float[] corrCoor = infoData.getCorrectCoordinates(i);
            float dist = (float) Math.sqrt(Math.pow(prevCoor[0]-corrCoor[0], 2) + Math.pow(prevCoor[1]-corrCoor[1], 2));
            ArrayList<UserAndStudyInfo.Touchpoint> ts = infoData.getTouchpoints(i);
            for(int j = 0; j < ts.size(); j++){
                UserAndStudyInfo.Touchpoint t = ts.get(j);
                boolean correct = t.correct;

                stringBuilder.append(i)
                        .append(", ")
                        .append(dist)
                        .append(", ")
                        .append(infoData.getDiameter())
                        .append(", ")
                        .append(t.milliseconds/(float)1000)
                        .append(", ")
                        .append(correct)
                        .append("\n");
            }
            prevCoor = corrCoor;
        }


        builder.setMessage(s);
        builder.setCancelable(false);
        builder.setPositiveButton("OK", (DialogInterface.OnClickListener) (dialog, which) ->{
            // Create an Intent to launch ResultsActivity
            Intent intent = new Intent(GameActivity.this, ResultsActivity.class);

            // Convert StringBuilder to String and add it as an extra
            String resultsData = stringBuilder.toString();
            intent.putExtra("key_results_data", resultsData);

            startActivity(intent);

            // Finish GameActivity so the user can't go back to it
            finish();
        });

        builder.setNegativeButton("BACK", (DialogInterface.OnClickListener) (dialog, which) ->{
            finish();
        });

        AlertDialog alertDialog = builder.create();
        alertDialog.show();

        TextView textView = (TextView) alertDialog.findViewById(android.R.id.message);
        textView.setTextSize(22);

        Button ok = (Button) alertDialog.findViewById(android.R.id.button1);
        ok.setTextSize(18);
        Button back = (Button) alertDialog.findViewById(android.R.id.button2);
        back.setTextSize(18);
    }

    void save(){
        try{
            UserAndStudyInfo x = infoData;
            Date d = Calendar.getInstance().getTime();
            String tStamp = d.getMonth()+"-"+d.getDate()+"_"+d.getHours()+"-"+d.getMinutes();
            String fileName = x.getId()+"_"+x.getRows()+"x"+x.getColumns()+"_"+x.getDiameter()+"mmDiameter_"+(x.getDistance()+x.getDiameter())+"mmBetwTargetCenters_"+tStamp+".csv";
            TextView tv = findViewById(R.id.textView7);
            tv.setText(fileName);


            File dirs[] = getExternalFilesDirs(null);
            File f;
            if(dirs.length > 1) f = new File(dirs[1], fileName);
            f = new File(getApplicationContext().getExternalFilesDir(null), fileName);

            PrintWriter pw = new PrintWriter(f);
            pw.println("ID,Gender,Age,Hand,Finger,Rows,Columns,Radius,Diameter,DistBetwTargetCenters,TotalTrials,TrialNumber,CorrectCircle,CorrectX,CorrectY,PrevCorrectX,PrevCorrectY,UserX,UserY,Time,DistFromPrevious,Correct");
            String c = ",";
            String first10 = x.getId()+c+x.getGender()+c+x.getAge()+c+x.getHand()+c+x.getFinger()+c+ x.getRows()+c+
                             x.getColumns()+c+x.getRadius()+c+x.getDiameter()+c+(x.getDistance()+x.getDiameter())+c+x.getTotalTrials();
            //UserAndStudyInfo.Touchpoint prevT = new UserAndStudyInfo.Touchpoint(pxToMm(Resources.getSystem().getDisplayMetrics().widthPixels/2, getApplicationContext()),
            //        pxToMm(Resources.getSystem().getDisplayMetrics().heightPixels/2+5, getApplicationContext()), 0);
            //float[] prevCoor = [pxToMm(Resources.getSystem().getDisplayMetrics().widthPixels/2, getApplicationContext()),
            //        pxToMm(Resources.getSystem().getDisplayMetrics().heightPixels/2+5]

            //Context con = getApplicationContext();
            //int[] xy = new int[2];
            //((ImageView)(((TableRow)(gameTableLayout.getChildAt(1)))).getChildAt(1)).getLocationOnScreen(xy);
            //float[] prevCoor = {pxToMm(xy[0]+(mmToPx(infoData.getRadius()/2)), con), pxToMm(xy[1]+(mmToPx(infoData.getRadius()/2)), con)};
            float[] prevCoor = {-1, -1};
            for(int i = 0; i < infoData.getTotalTrials(); i++){
                int cir = x.getCorrectSequence(i);
                float[] corrCoor = x.getCorrectCoordinates(i);
                float dist = (float) Math.sqrt(Math.pow(prevCoor[0]-corrCoor[0], 2) + Math.pow(prevCoor[1]-corrCoor[1], 2));
                ArrayList<UserAndStudyInfo.Touchpoint> ts = x.getTouchpoints(i);
                for(int j = 0; j < ts.size(); j++){
                    UserAndStudyInfo.Touchpoint t = ts.get(j);
                    //boolean correct = (j == ts.size()-1);
                    //boolean correct = false;
                    //if(j==ts.size()-1 && inCircle(new int[] {(int) mmToPx(corrCoor[0]), (int) mmToPx(corrCoor[1])},
                    //                              new float[] {mmToPx(t.x), mmToPx(t.y)})) correct = true;
                    boolean correct = t.correct;
                    pw.println(first10+c+i+c+cir+c+corrCoor[0]+c+corrCoor[1]+c+prevCoor[0]+c+prevCoor[1]+c+t.x+c+t.y+c+t.milliseconds+c+dist+c+correct);
                }

                if(ts.size() == 0) pw.println(first10+c+i+c+cir+c+corrCoor[0]+c+corrCoor[1]+c+prevCoor[0]+c+prevCoor[1]+c+(-1)+c+(-1)+c+(-1)+c+dist+c+(false));

                prevCoor = corrCoor;
            }
            pw.close();

            Log.d("Saved File Location", f.getPath());

            TextView t = findViewById(R.id.textView7);
            t.setText("");
        }
        catch(Exception e){
            TextView t = findViewById(R.id.textView7);
            //t.setText("Something went wrong.");
            return;
        }
        LinearLayout l = findViewById(R.id.fileNameAndSaveLayout);
        l.setVisibility(View.INVISIBLE);
    }

    float mmToPx(float mm) {
        return mm*(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_MM, 1, getResources().getDisplayMetrics()));
    }
    float pxToMm(final float px, final Context context) {
        final DisplayMetrics dm = context.getResources().getDisplayMetrics();
        float n = px / TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_MM, 1, dm);
        return n;
    }

    void showCookie() {
        currentCookie.setVisibility(View.VISIBLE);
    }
    void hideCookie(){
        currentCookie.setVisibility(View.INVISIBLE);

    }
}
