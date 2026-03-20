package com.aflokkat.domain;

import org.bson.codecs.pojo.annotations.BsonProperty;
import org.bson.types.ObjectId;

public class Restaurant {
    @BsonProperty("_id")
    private ObjectId id;
    
    @BsonProperty("name")
    private String name;
    
    @BsonProperty("cuisine")
    private String cuisine;
    
    @BsonProperty("borough")
    private String borough;
    
    @BsonProperty("address")
    private Address address;
    
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
