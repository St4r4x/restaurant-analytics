package com.aflokkat.aggregation;

import org.bson.codecs.pojo.annotations.BsonProperty;

public class BoroughCuisineScore {
    @BsonProperty("_id")
    private String borough;
    
    @BsonProperty("avgScore")
    private Double avgScore;
    
    public BoroughCuisineScore() {}
    
    public BoroughCuisineScore(String borough, Double avgScore) {
        this.borough = borough;
        this.avgScore = avgScore;
    }
    
    public String getBorough() {
        return borough;
    }
    
    public void setBorough(String borough) {
        this.borough = borough;
    }
    
    public Double getAvgScore() {
        return avgScore;
    }
    
    public void setAvgScore(Double avgScore) {
        this.avgScore = avgScore;
    }
    
    @Override
    public String toString() {
        return String.format("%-20s | Avg score: %6.2f", borough, avgScore);
    }
}
