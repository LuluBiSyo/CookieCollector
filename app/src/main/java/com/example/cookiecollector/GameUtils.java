package com.example.cookiecollector;

import android.content.Context;
import android.os.Build;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.util.TypedValue;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;

/** Helpers shared by the tap and swipe games. */
final class GameUtils {
    private GameUtils() {}

    static float mmToPx(Context c, float mm) {
        return mm * TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_MM, 1, c.getResources().getDisplayMetrics());
    }

    static float pxToMm(Context c, float px) {
        return px / TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_MM, 1, c.getResources().getDisplayMetrics());
    }

    /** Text for the bar at the top of both games. */
    static String scoreBar(int score, int left, long seconds) {
        return String.format("Score: %d   Left: %d   %02d:%02d", score, left, seconds / 60, seconds % 60);
    }

    /** Message for the dialog shown when a game ends. */
    static String finishedMessage(int hits, int total, int bestStreak, long totalSeconds) {
        int minutes = (int) (totalSeconds / 60);
        int seconds = (int) (totalSeconds % 60);
        String time;
        if (minutes > 0) {
            time = String.format("%d %s and %d %s", minutes, minutes == 1 ? "minute" : "minutes",
                    seconds, seconds == 1 ? "second" : "seconds");
        } else {
            time = String.format("%d seconds", seconds);
        }
        String s = (hits == 1)
                ? "You collected 1 cookie in " + time + "."
                : String.format("You collected %d of %d cookies in %s! Well done!", hits, total, time);
        if (bestStreak >= 2) s += "\nBest streak: " + bestStreak + " in a row.";
        return s;
    }

    /** Shows the dialog with large text, which older participants read more easily. */
    static void showLarge(AlertDialog dialog) {
        dialog.show();
        ((TextView) dialog.findViewById(android.R.id.message)).setTextSize(22);
        ((Button) dialog.findViewById(android.R.id.button1)).setTextSize(18);
        ((Button) dialog.findViewById(android.R.id.button2)).setTextSize(18);
    }

    static void vibrate(Context c, long millis) {
        Vibrator v = (Vibrator) c.getSystemService(Context.VIBRATOR_SERVICE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) v.vibrate(VibrationEffect.createOneShot(millis, VibrationEffect.DEFAULT_AMPLITUDE));
        else v.vibrate(millis);
    }
}
