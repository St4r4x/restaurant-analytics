package com.st4r4x.domain;

import org.bson.codecs.pojo.annotations.BsonProperty;

public class Grade {

    @BsonProperty("date")
    private String date;

    @BsonProperty("grade")
    private String grade;

    @BsonProperty("score")
    private Integer score;

    @BsonProperty("inspection_type")
    private String inspectionType;

    @BsonProperty("action")
    private String action;

    @BsonProperty("violation_code")
    private String violationCode;

    @BsonProperty("violation_description")
    private String violationDescription;

    @BsonProperty("critical_flag")
    private String criticalFlag;

    public Grade() {}

    public String getDate() { return date; }
    public void setDate(String date) { this.date = date; }

    public String getGrade() { return grade; }
    public void setGrade(String grade) { this.grade = grade; }

    public Integer getScore() { return score; }
    public void setScore(Integer score) { this.score = score; }

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

    @Override
    public String toString() {
        return "Grade{date='" + date + "', grade='" + grade + "', score=" + score
                + ", inspectionType='" + inspectionType + "'}";
    }
}
