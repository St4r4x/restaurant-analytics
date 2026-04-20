package com.st4r4x.sync;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * DTO mapping the NYC Open Data restaurant inspection API response.
 * One record per inspection row — multiple rows share the same camis (restaurant ID).
 * Dataset: https://data.cityofnewyork.us/resource/43nn-pn8j.json
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class NycApiRestaurantDto {

    // ── Restaurant-level fields (same across all inspection rows for a camis) ──

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

    @JsonProperty("phone")
    private String phone;

    @JsonProperty("cuisine_description")
    private String cuisineDescription;

    @JsonProperty("latitude")
    private String latitude;

    @JsonProperty("longitude")
    private String longitude;

    // ── Inspection-level fields (vary per row) ────────────────────────────────

    @JsonProperty("inspection_date")
    private String inspectionDate;

    @JsonProperty("inspection_type")
    private String inspectionType;

    @JsonProperty("action")
    private String action;

    @JsonProperty("violation_code")
    private String violationCode;

    @JsonProperty("violation_description")
    private String violationDescription;

    @JsonProperty("critical_flag")
    private String criticalFlag;

    @JsonProperty("score")
    private String score;

    @JsonProperty("grade")
    private String grade;

    // ── Getters / Setters ─────────────────────────────────────────────────────

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

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    public String getCuisineDescription() { return cuisineDescription; }
    public void setCuisineDescription(String cuisineDescription) { this.cuisineDescription = cuisineDescription; }

    public String getLatitude() { return latitude; }
    public void setLatitude(String latitude) { this.latitude = latitude; }

    public String getLongitude() { return longitude; }
    public void setLongitude(String longitude) { this.longitude = longitude; }

    public String getInspectionDate() { return inspectionDate; }
    public void setInspectionDate(String inspectionDate) { this.inspectionDate = inspectionDate; }

    public String getInspectionType() { return inspectionType; }
    public void setInspectionType(String inspectionType) { this.inspectionType = inspectionType; }

    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }

    public String getViolationCode() { return violationCode; }
    public void setViolationCode(String violationCode) { this.violationCode = violationCode; }

    public String getViolationDescription() { return violationDescription; }
    public void setViolationDescription(String violationDescription) { this.violationDescription = violationDescription; }

    public String getCriticalFlag() { return criticalFlag; }
    public void setCriticalFlag(String criticalFlag) { this.criticalFlag = criticalFlag; }

    public String getScore() { return score; }
    public void setScore(String score) { this.score = score; }

    public String getGrade() { return grade; }
    public void setGrade(String grade) { this.grade = grade; }
}
