package com.aflokkat.sync;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * DTO mapping the NYC Open Data restaurant inspection API response.
 * One record per inspection row — multiple rows share the same camis (restaurant ID).
 * Dataset: https://data.cityofnewyork.us/resource/43nn-pn8j.json
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class NycApiRestaurantDto {

    @JsonProperty("camis")
    private String camis;

    @JsonProperty("dba")
    private String dba;

    @JsonProperty("boro")
    private String boro;

    @JsonProperty("building")
    private String building;

    @JsonProperty("street")
    private String street;

    @JsonProperty("zipcode")
    private String zipcode;

    @JsonProperty("cuisine_description")
    private String cuisineDescription;

    @JsonProperty("inspection_date")
    private String inspectionDate;

    @JsonProperty("score")
    private String score;

    @JsonProperty("grade")
    private String grade;

    @JsonProperty("latitude")
    private String latitude;

    @JsonProperty("longitude")
    private String longitude;

    public String getCamis() { return camis; }
    public void setCamis(String camis) { this.camis = camis; }

    public String getDba() { return dba; }
    public void setDba(String dba) { this.dba = dba; }

    public String getBoro() { return boro; }
    public void setBoro(String boro) { this.boro = boro; }

    public String getBuilding() { return building; }
    public void setBuilding(String building) { this.building = building; }

    public String getStreet() { return street; }
    public void setStreet(String street) { this.street = street; }

    public String getZipcode() { return zipcode; }
    public void setZipcode(String zipcode) { this.zipcode = zipcode; }

    public String getCuisineDescription() { return cuisineDescription; }
    public void setCuisineDescription(String cuisineDescription) { this.cuisineDescription = cuisineDescription; }

    public String getInspectionDate() { return inspectionDate; }
    public void setInspectionDate(String inspectionDate) { this.inspectionDate = inspectionDate; }

    public String getScore() { return score; }
    public void setScore(String score) { this.score = score; }

    public String getGrade() { return grade; }
    public void setGrade(String grade) { this.grade = grade; }

    public String getLatitude() { return latitude; }
    public void setLatitude(String latitude) { this.latitude = latitude; }

    public String getLongitude() { return longitude; }
    public void setLongitude(String longitude) { this.longitude = longitude; }
}
