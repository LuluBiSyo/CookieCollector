package com.example.cookiecollector;

import android.content.Context;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Serializable;
import java.util.ArrayList;

public class UserAndStudyInfo implements Serializable {

    private static final int SMALL_RADIUS = 12;
    private static final int LARGE_RADIUS = 16;

    private String id;
    private String gender;
    private String age;
    private String hand;
    private String finger;
    private int rows;
    private int columns;
    //The diameter of the cookie displayed on the screen
    private int diameter;
    private int totalCircles;
    private int distance;
    private int trialsPerDistance;
//    private int trialsPerCircle;
    private int totalTrials;
    private int[] correctSequence;
    private float[][] correctCoordinates;
    private ArrayList<Touchpoint>[] touchpoints;


    public UserAndStudyInfo() {}

//    public UserAndStudyInfo(Context c, String id, String gender, String age, String hand, String finger, String layout, String size, String distance, String session) {
    public UserAndStudyInfo(Context c, String id, String gender, String age, String hand, String finger, String layout, String size, String session) {
        this.id = id;
        this.gender = gender;
        this.age = age;
        this.hand = hand;
        this.finger = finger;

        rows = Integer.parseInt(layout.split("x")[0]);
        columns = Integer.parseInt(layout.split("x")[1]);
        totalCircles = rows * columns;

        if(size.equals(c.getString(R.string.small))) {
            diameter = SMALL_RADIUS;
        }
        else if(size.equals(c.getString(R.string.large))) {
            diameter = LARGE_RADIUS;
        }

//        if(distance.equals(c.getString(R.string.close))) this.distance = 1;
//        else if(distance.equals(c.getString(R.string.mediumDist))) this.distance = 3;
//        else if(distance.equals(c.getString(R.string.far))) this.distance = 5;
//        else this.distance = 13;
        // old distance
//        this.distance = 20 - diameter;
//
        this.distance = 22 - diameter;

//        if(session.equals(c.getString(R.string.warmup))) trialsPerCircle = 1;
//        else trialsPerCircle = 20;

        if(session.equals(c.getString(R.string.warmup))) totalTrials = totalCircles;
        else {
            trialsPerDistance = 10;
            totalTrials = 4*trialsPerDistance + 1;
        }

        setCorrectSequence(c);
        setCorrectCoordinates(totalTrials);
        setTouchpoints(totalTrials);
    }


    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getGender() { return gender; }
    public void setGender(String gender) { this.gender = gender; }

    public String getAge() { return age; }
    public void setAge(String age) { this.age = age; }

    public String getHand() { return hand; }
    public void setHand(String hand) { this.hand = hand; }

    public String getFinger() { return finger; }
    public void setFinger(String finger) { this.finger = finger; }

    public int getRows() { return rows; }
    public void setRows(int rows){ this.rows = rows; }

    public int getColumns() { return columns; }
    public void setColumns(int columns) { this.columns = columns; }

    public float getRadius() { return (float)diameter / 2; }
    public int getDiameter() { return diameter; };
    public void setDiameter(int diameter) { this.diameter = diameter; }
    public int getTotalCircles() { return totalCircles; }
    public void setTotalCircles(int totalCircles) { this.totalCircles = totalCircles; }

    public int getDistance() { return distance; }

//    public int getTrialsPerCircle(){ return trialsPerCircle; }
//    public void setTrialsPerCircle(int trialsPerCircle) { this.trialsPerCircle = trialsPerCircle; }

    public int getTotalTrials(){ return totalTrials; }
    public void setTotalTrials(int totalTrials) { this.totalTrials = totalTrials; }

    public int[] getCorrectSequence() { return this.correctSequence; }
    public int getCorrectSequence(int index) { return correctSequence[index]; }
    public void setCorrectSequence(Context c){
        correctSequence = new int[totalTrials];
        if(totalTrials == totalCircles){
            ArrayList<Integer> a = new ArrayList<>();
            for(int i = 0; i < correctSequence.length; i++) a.add(i);
            for(int i = 0; i < correctSequence.length; i++) correctSequence[i] = a.remove((int)(Math.random() * a.size()));
        }
        else{
            try {
                BufferedReader br = new BufferedReader(new InputStreamReader(c.getAssets().open(totalCircles+"circles41trials3.txt")));
                int randomIndex = (int)(Math.random() * 1000);
                for(int i = 0; i < randomIndex; i++) br.readLine();
                String[] s = br.readLine().split(" ");
                for(int i = 0; i < 41; i++){
                    correctSequence[i] = Integer.parseInt(s[i]);
                }
            }
            catch (IOException e) { e.printStackTrace(); }
        }
    }

/*
    public void setCorrectSequence() {
        correctSequence = new int[totalTrials];
        if(totalTrials == totalCircles){
            ArrayList<Integer> a = new ArrayList<>();
            for(int i = 0; i < correctSequence.length; i++) a.add(i);
            for(int i = 0; i < correctSequence.length; i++) correctSequence[i] = a.remove((int)(Math.random() * a.size()));
        }
        else{
            setFormalCorrectSequence();
        }
    }
    private void setFormalCorrectSequence() {
        correctSequence = new int[totalTrials];
        int[] S = new int[trialsPerDistance*4];
        int[] firstHalf = new int[2*trialsPerDistance + 1];
        int[] secondHalf = new int[2*trialsPerDistance + 1];
        while(true) {

            generateRandomS(S);
            int[] firstS = new int[2*trialsPerDistance];
            for(int i = 0; i < firstS.length; i++) firstS[i] = S[2*trialsPerDistance-1-i];
            int[] secondS = new int[2*trialsPerDistance];
            for(int i = 0; i < secondS.length; i++) secondS[i] = S[2*trialsPerDistance+i];

            boolean S_works = false;
            HashSet<Integer> middle = new HashSet<>();

            while(true) {
                if(middle.size() >= totalCircles) break;
                int m = (int)(Math.random() * totalCircles);
                middle.add(m);
                correctSequence[2*trialsPerDistance] = m;
                firstHalf[0] = m;
                secondHalf[0] = m;
                if(fillCorrectSequence(firstS, firstHalf, 1) && fillCorrectSequence(secondS, secondHalf, 1)) {
                    S_works = true;
                    break;
                }

            }

            if(S_works) break;
        }
        for(int i = 0; i < firstHalf.length; i++) correctSequence[i] = firstHalf[firstHalf.length-i-1];
        for(int i = 1; i < secondHalf.length; i++) correctSequence[2*trialsPerDistance+i] = secondHalf[i];
    }
    private void generateRandomS(int[] S){
        ArrayList<Integer> a = new ArrayList<>();
        for(int i = 0; i < 4; i++) for(int j = 0; j < trialsPerDistance; j++) a.add(i);
        for(int i = 0; i < S.length; i++) S[i] = a.remove((int)(Math.random() * a.size()));
    }
    private boolean fillCorrectSequence(int[] S, int[] P, int index){ //index represents the next of correctSequence that needs to be filled
        if(index == P.length) return true;
//		Log.d("index", ""+index);
//		Log.d("stuff", S[index-1] + " " + correctSequence[index-1]);
        int requiredD = S[index-1];
        int prevCircle = P[index-1];
        ArrayList<Integer> next = new ArrayList<>();
        for(int i = 0; i < totalCircles; i++) next.add(i);
        while(true){
            if(next.size() <= 0) return false;
            int i = (int)(Math.random() * next.size());
            int n = next.remove(i);

//			Log.d("n", index + ": " + n+"");
            if(rightDistance(requiredD, prevCircle, n)) {
                P[index] = n;
                if(fillCorrectSequence(S, P, index+1)) return true;
            }
        }

    }
    private int[][][] distPos = {{{2, 0}, {0, 2}, {-2, 0}, {0, -2}},
            {{1, 0}, {0, 1}, {-1, 0}, {0, -1}},
            {{1, 2}, {2, 1}, {2, -1}, {1, -2}, {-1, -2}, {-2, -1}, {-2, 1}, {-1, 2}},
            {{1, 1}, {1, -1}, {-1, -1}, {-1, 1}}};
    private boolean rightDistance(int dist, int prevCircle, int next){
        int pr = prevCircle/columns;
        int pc = prevCircle%columns;
        int nr = next/columns;
        int nc = next%columns;
        for(int i = 0; i < distPos[dist].length; i++){
            if(pr-nr==distPos[dist][i][0] && pc-nc==distPos[dist][i][1]) return true;
        }
        return false;
    }
*/
//    public void addToCorrectSequence(int trial, int circle) { correctSequence[trial] = circle; }



    public float[][] getCorrectCoordinates() { return this.correctCoordinates; }
    public float[] getCorrectCoordinates(int index) { return correctCoordinates[index]; }
    public void setCorrectCoordinates(int trials) { correctCoordinates = new float[trials][2]; }
    public void addToCorrectCoordinates(int trial, float x, float y) {
        correctCoordinates[trial][0] = x;
        correctCoordinates[trial][1] = y;
    }

    public ArrayList<Touchpoint>[] getTouchpoints() { return this.touchpoints; }
    public ArrayList<Touchpoint> getTouchpoints(int index) { return touchpoints[index]; }
    public void setTouchpoints(int trials) {
        touchpoints = new ArrayList[trials];
        for(int i = 0; i < trials; i++) touchpoints[i] = new ArrayList<>();
    }
    public void addTouchpoint(int trial, float x, float y, long milliseconds, boolean correct) { touchpoints[trial].add(new Touchpoint(x, y, milliseconds, correct)); }
    public void makeCorrect(int trial){
        touchpoints[trial].get(touchpoints[trial].size()-1).correct = true;
    }


    static class Touchpoint{
        float x;
        float y;
        long milliseconds;
        boolean correct;
        Touchpoint(float x, float y, long milliseconds, boolean correct){
            this.x = x;
            this.y = y;
            this.milliseconds = milliseconds;
            this.correct = correct;
        }
    }
}
