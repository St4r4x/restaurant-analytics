package com.st4r4x.dto;

import org.bson.codecs.pojo.annotations.BsonProperty;

public class AtRiskEntry {

    @BsonProperty("restaurant_id")
    private String restaurantId;

    @BsonProperty("name")
    private String name;

    @BsonProperty("borough")
    private String borough;

    @BsonProperty("cuisine")
    private String cuisine;

    @BsonProperty("lastGrade")
    private String lastGrade;

    @BsonProperty("lastScore")
    private Integer lastScore;

    @BsonProperty("consecutiveBadGrades")
    private Integer consecutiveBadGrades;

    public AtRiskEntry() {}

    public String getRestaurantId() { return restaurantId; }
    public void setRestaurantId(String restaurantId) { this.restaurantId = restaurantId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getBorough() { return borough; }
    public void setBorough(String borough) { this.borough = borough; }

    public String getCuisine() { return cuisine; }
    public void setCuisine(String cuisine) { this.cuisine = cuisine; }

    public String getLastGrade() { return lastGrade; }
    public void setLastGrade(String lastGrade) { this.lastGrade = lastGrade; }

    public Integer getLastScore() { return lastScore; }
    public void setLastScore(Integer lastScore) { this.lastScore = lastScore; }

    public Integer getConsecutiveBadGrades() { return consecutiveBadGrades; }
    public void setConsecutiveBadGrades(Integer consecutiveBadGrades) { this.consecutiveBadGrades = consecutiveBadGrades; }
}
