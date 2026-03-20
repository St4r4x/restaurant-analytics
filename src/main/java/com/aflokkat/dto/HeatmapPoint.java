package com.aflokkat.dto;

import org.bson.codecs.pojo.annotations.BsonProperty;

public class HeatmapPoint {

    @BsonProperty("lat")
    private Double lat;

    @BsonProperty("lng")
    private Double lng;

    @BsonProperty("weight")
    private Integer weight;

    public HeatmapPoint() {}

    public Double getLat() { return lat; }
    public void setLat(Double lat) { this.lat = lat; }

    public Double getLng() { return lng; }
    public void setLng(Double lng) { this.lng = lng; }

    public Integer getWeight() { return weight; }
    public void setWeight(Integer weight) { this.weight = weight; }
}
