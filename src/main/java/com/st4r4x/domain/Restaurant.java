package com.st4r4x.domain;

import java.util.List;
import java.time.Instant;

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
    private List<InspectionRecord> grades;

    @BsonProperty("osm_phone")
    private String osmPhone;

    @BsonProperty("osm_website")
    private String osmWebsite;

    @BsonProperty("osm_opening_hours")
    private String osmOpeningHours;

    @BsonProperty("osm_enriched_at")
    private Instant osmEnrichedAt;

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

    public List<InspectionRecord> getGrades() { return grades; }
    public void setGrades(List<InspectionRecord> grades) { this.grades = grades; }

    public String getOsmPhone() { return osmPhone; }
    public void setOsmPhone(String osmPhone) { this.osmPhone = osmPhone; }

    public String getOsmWebsite() { return osmWebsite; }
    public void setOsmWebsite(String osmWebsite) { this.osmWebsite = osmWebsite; }

    public String getOsmOpeningHours() { return osmOpeningHours; }
    public void setOsmOpeningHours(String osmOpeningHours) { this.osmOpeningHours = osmOpeningHours; }

    public Instant getOsmEnrichedAt() { return osmEnrichedAt; }
    public void setOsmEnrichedAt(Instant osmEnrichedAt) { this.osmEnrichedAt = osmEnrichedAt; }

    @Override
    public String toString() {
        return "Restaurant{" +
                "id=" + id +
                ", name='" + name + '\'' +
                ", cuisine='" + cuisine + '\'' +
                ", borough='" + borough + '\'' +
                ", address=" + address +
                '}';
    }
}
