package com.aflokkat.dto;

import com.aflokkat.entity.InspectionGrade;
import com.aflokkat.entity.Status;

public class ReportRequest {

    private String restaurantId;   // required for POST; ignored for PATCH
    private InspectionGrade grade;           // required for POST; optional for PATCH (null = unchanged)
    private Status status;         // optional for POST (defaults to OPEN); optional for PATCH
    private String violationCodes; // optional
    private String notes;          // optional

    public String getRestaurantId() { return restaurantId; }
    public void setRestaurantId(String restaurantId) { this.restaurantId = restaurantId; }

    public InspectionGrade getGrade() { return grade; }
    public void setGrade(InspectionGrade grade) { this.grade = grade; }

    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }

    public String getViolationCodes() { return violationCodes; }
    public void setViolationCodes(String violationCodes) { this.violationCodes = violationCodes; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
}
