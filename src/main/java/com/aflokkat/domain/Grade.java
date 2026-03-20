package com.aflokkat.domain;

import org.bson.codecs.pojo.annotations.BsonProperty;

public class Grade {

    @BsonProperty("date")
    private String date;

    @BsonProperty("grade")
    private String grade;

    @BsonProperty("score")
    private Integer score;

    public Grade() {}

    public Grade(String date, String grade, Integer score) {
        this.date = date;
        this.grade = grade;
        this.score = score;
    }

    public String getDate() { return date; }
    public void setDate(String date) { this.date = date; }

    public String getGrade() { return grade; }
    public void setGrade(String grade) { this.grade = grade; }

    public Integer getScore() { return score; }
    public void setScore(Integer score) { this.score = score; }

    @Override
    public String toString() {
        return "Grade{date='" + date + "', grade='" + grade + "', score=" + score + '}';
    }
}
