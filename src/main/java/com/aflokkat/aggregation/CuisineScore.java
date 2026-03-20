package com.aflokkat.aggregation;

import org.bson.codecs.pojo.annotations.BsonProperty;

public class CuisineScore {
    @BsonProperty("_id")
    private String cuisine;
    
    @BsonProperty("avgScore")
    private Double avgScore;
    
    @BsonProperty("count")
    private Integer count;
    
    public CuisineScore() {}
    
    public CuisineScore(String cuisine, Double avgScore, Integer count) {
        this.cuisine = cuisine;
        this.avgScore = avgScore;
        this.count = count;
    }
    
    public String getCuisine() {
        return cuisine;
    }
    
    public void setCuisine(String cuisine) {
        this.cuisine = cuisine;
    }
    
    public Double getAvgScore() {
        return avgScore;
    }
    
    public void setAvgScore(Double avgScore) {
        this.avgScore = avgScore;
    }
    
    public Integer getCount() {
        return count;
    }
    
    public void setCount(Integer count) {
        this.count = count;
    }
    
    @Override
    public String toString() {
        return String.format("%-40s | Score moy: %6.2f | Count: %4d", cuisine, avgScore, count);
    }
}
