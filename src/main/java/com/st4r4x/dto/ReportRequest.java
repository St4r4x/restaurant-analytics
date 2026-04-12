package com.st4r4x.dto;

import com.st4r4x.entity.Grade;
import com.st4r4x.entity.Status;

public class ReportRequest {

    private String restaurantId;   // required for POST; ignored for PATCH
    private Grade grade;           // required for POST; optional for PATCH (null = unchanged)
    private Status status;         // optional for POST (defaults to OPEN); optional for PATCH
    private String violationCodes; // optional
    private String notes;          // optional

    public String getRestaurantId() { return restaurantId; }
    public void setRestaurantId(String restaurantId) { this.restaurantId = restaurantId; }

    public Grade getGrade() { return grade; }
    public void setGrade(Grade grade) { this.grade = grade; }

    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }

    public String getViolationCodes() { return violationCodes; }
    public void setViolationCodes(String violationCodes) { this.violationCodes = violationCodes; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
}
