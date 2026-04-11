package com.st4r4x.dto;

/**
 * Lightweight snapshot of a restaurant stored in the Redis "top restaurants" sorted set.
 * Score (latestScore) is the most recent inspection score — lower is healthier.
 */
public class TopRestaurantEntry {

    private String restaurantId;
    private String name;
    private String borough;
    private String cuisine;
    private int latestScore;

    public TopRestaurantEntry() {}

    public TopRestaurantEntry(String restaurantId, String name, String borough, String cuisine, int latestScore) {
        this.restaurantId = restaurantId;
        this.name = name;
        this.borough = borough;
        this.cuisine = cuisine;
        this.latestScore = latestScore;
    }

    public String getRestaurantId() { return restaurantId; }
    public void setRestaurantId(String restaurantId) { this.restaurantId = restaurantId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getBorough() { return borough; }
    public void setBorough(String borough) { this.borough = borough; }

    public String getCuisine() { return cuisine; }
    public void setCuisine(String cuisine) { this.cuisine = cuisine; }

    public int getLatestScore() { return latestScore; }
    public void setLatestScore(int latestScore) { this.latestScore = latestScore; }
}
