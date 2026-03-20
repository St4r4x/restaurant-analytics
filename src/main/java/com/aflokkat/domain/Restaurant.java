package com.aflokkat.domain;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

import org.bson.codecs.pojo.annotations.BsonProperty;
import org.bson.types.ObjectId;

public class Restaurant {
    @BsonProperty("_id")
    private ObjectId id;

    @BsonProperty("restaurant_id")
    private String restaurantId;

    @BsonProperty("name")
    private String name;

    @BsonProperty("cuisine")
    private String cuisine;

    @BsonProperty("borough")
    private String borough;

    @BsonProperty("address")
    private Address address;

    @BsonProperty("phone")
    private String phone;

    @BsonProperty("grades")
    private List<Grade> grades;

    public Restaurant() {}

    public Restaurant(String name, String cuisine, String borough) {
        this.name = name;
        this.cuisine = cuisine;
        this.borough = borough;
    }
    
    public ObjectId getId() {
        return id;
    }
    
    public void setId(ObjectId id) {
        this.id = id;
    }
    
    public String getName() {
        return name;
    }
    
    public void setName(String name) {
        this.name = name;
    }
    
    public String getCuisine() {
        return cuisine;
    }
    
    public void setCuisine(String cuisine) {
        this.cuisine = cuisine;
    }
    
    public String getBorough() {
        return borough;
    }
    
    public void setBorough(String borough) {
        this.borough = borough;
    }
    
    public Address getAddress() {
        return address;
    }

    public void setAddress(Address address) {
        this.address = address;
    }

    public String getRestaurantId() { return restaurantId; }
    public void setRestaurantId(String restaurantId) { this.restaurantId = restaurantId; }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    public List<Grade> getGrades() { return grades; }
    public void setGrades(List<Grade> grades) { this.grades = grades; }

    // ---- Computed badge fields (not stored in MongoDB) ----

    public String getLatestGrade() {
        if (grades == null || grades.isEmpty()) return null;
        return grades.stream()
            .filter(g -> g.getDate() != null)
            .max(Comparator.comparing(Grade::getDate))
            .map(Grade::getGrade)
            .orElse(null);
    }

    public Integer getLatestScore() {
        if (grades == null || grades.isEmpty()) return null;
        return grades.stream()
            .filter(g -> g.getDate() != null && g.getScore() != null)
            .max(Comparator.comparing(Grade::getDate))
            .map(Grade::getScore)
            .orElse(null);
    }

    public String getTrend() {
        if (grades == null || grades.size() < 2) return "stable";
        List<Grade> sorted = grades.stream()
            .filter(g -> g.getDate() != null && g.getScore() != null)
            .sorted(Comparator.comparing(Grade::getDate).reversed())
            .collect(Collectors.toList());
        if (sorted.size() < 2) return "stable";
        int recent = sorted.get(0).getScore();
        int prev = sorted.get(1).getScore();
        // Lower score = better (fewer violations)
        if (recent < prev - 5) return "improving";
        if (recent > prev + 5) return "worsening";
        return "stable";
    }

    public String getBadgeColor() {
        String g = getLatestGrade();
        if (g == null || g.isEmpty()) return "red";
        switch (g) {
            case "A": return "green";
            case "B": return "yellow";
            case "C": return "orange";
            default:  return "red"; // Z, N, P, etc.
        }
    }

    public Double getLatitude() {
        if (address != null && address.getCoord() != null && address.getCoord().size() >= 2) {
            return address.getCoord().get(1); // GeoJSON: [longitude, latitude]
        }
        return null;
    }

    public Double getLongitude() {
        if (address != null && address.getCoord() != null && address.getCoord().size() >= 2) {
            return address.getCoord().get(0); // GeoJSON: [longitude, latitude]
        }
        return null;
    }
    
    @Override
    public String toString() {
        return "Restaurant{" +
                "id=" + id +
                ", name='" + name + '\'' +
                ", cuisine='" + cuisine + '\'' +
                ", borough='" + borough + '\'' +
                ", address=" + address +
                ", latitude=" + getLatitude() +
                ", longitude=" + getLongitude() +
                '}';
    }
}
