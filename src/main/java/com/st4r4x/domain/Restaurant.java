package com.st4r4x.domain;

import java.util.List;

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
