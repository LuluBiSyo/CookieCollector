package com.example.cookiecollector;

import android.content.Context;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Serializable;
import java.util.ArrayList;

/** Participant details and study settings, plus every touch recorded during the tap game. */
public class UserAndStudyInfo implements Serializable {

    private static final int SMALL_DIAMETER_MM = 12;
    private static final int LARGE_DIAMETER_MM = 16;
    private static final int FORMAL_TRIALS_PER_DISTANCE = 10;

    private final String id;
    private final String gender;
    private final String age;
    private final String hand;
    private final String finger;
    private final int rows;
    private final int columns;
    // Diameter of the cookie on screen, in mm
    private int diameter;
    private final int totalCircles;
    // Gap between neighbouring targets' edges, in mm
    private final int distance;
    private final int totalTrials;
    private int[] correctSequence;
    private float[][] correctCoordinates;
    private ArrayList<Touchpoint>[] touchpoints;

    public UserAndStudyInfo(Context c, String id, String gender, String age, String hand, String finger, String layout, String size, String session) {
        this.id = id;
        this.gender = gender;
        this.age = age;
        this.hand = hand;
        this.finger = finger;

        rows = Integer.parseInt(layout.split("x")[0]);
        columns = Integer.parseInt(layout.split("x")[1]);
        totalCircles = rows * columns;

        if (size.equals(c.getString(R.string.small))) diameter = SMALL_DIAMETER_MM;
        else if (size.equals(c.getString(R.string.large))) diameter = LARGE_DIAMETER_MM;

        distance = 22 - diameter;

        // Warmup visits every circle once; formal uses a pre-generated 41-trial sequence
        if (session.equals(c.getString(R.string.warmup))) totalTrials = totalCircles;
        else totalTrials = 4 * FORMAL_TRIALS_PER_DISTANCE + 1;

        setCorrectSequence(c);
        correctCoordinates = new float[totalTrials][2];
        setTouchpoints(totalTrials);
    }

    public String getId() { return id; }
    public String getGender() { return gender; }
    public String getAge() { return age; }
    public String getHand() { return hand; }
    public String getFinger() { return finger; }
    public int getRows() { return rows; }
    public int getColumns() { return columns; }

    public float getRadius() { return (float) diameter / 2; }
    public int getDiameter() { return diameter; }
    public int getTotalCircles() { return totalCircles; }
    public int getDistance() { return distance; }
    public int getTotalTrials() { return totalTrials; }
    public boolean isWarmup() { return totalTrials == totalCircles; }

    public int getCorrectSequence(int index) { return correctSequence[index]; }

    private void setCorrectSequence(Context c) {
        correctSequence = new int[totalTrials];
        if (totalTrials == totalCircles) {
            // Random order of all circles
            ArrayList<Integer> a = new ArrayList<>();
            for (int i = 0; i < correctSequence.length; i++) a.add(i);
            for (int i = 0; i < correctSequence.length; i++) correctSequence[i] = a.remove((int) (Math.random() * a.size()));
        }
        else {
            // Pick one of the first 1000 sequences in the assets file
            try (BufferedReader br = new BufferedReader(new InputStreamReader(c.getAssets().open(totalCircles + "circles41trials3.txt")))) {
                int randomIndex = (int) (Math.random() * 1000);
                for (int i = 0; i < randomIndex; i++) br.readLine();
                String[] s = br.readLine().split(" ");
                for (int i = 0; i < totalTrials; i++) correctSequence[i] = Integer.parseInt(s[i]);
            }
            catch (IOException e) { e.printStackTrace(); }
        }
    }

    public float[] getCorrectCoordinates(int index) { return correctCoordinates[index]; }
    public void addToCorrectCoordinates(int trial, float x, float y) {
        correctCoordinates[trial][0] = x;
        correctCoordinates[trial][1] = y;
    }

    public ArrayList<Touchpoint> getTouchpoints(int index) { return touchpoints[index]; }
    @SuppressWarnings("unchecked")
    private void setTouchpoints(int trials) {
        touchpoints = new ArrayList[trials];
        for (int i = 0; i < trials; i++) touchpoints[i] = new ArrayList<>();
    }
    public void addTouchpoint(int trial, float x, float y, long milliseconds, boolean correct) {
        touchpoints[trial].add(new Touchpoint(x, y, milliseconds, correct));
    }

    static class Touchpoint implements Serializable {
        float x;
        float y;
        long milliseconds;
        boolean correct;
        Touchpoint(float x, float y, long milliseconds, boolean correct) {
            this.x = x;
            this.y = y;
            this.milliseconds = milliseconds;
            this.correct = correct;
        }
    }
}
