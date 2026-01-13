package com.careconnect.model;

public class ConversationState {
    // Enum to track exactly what we are waiting for
    public enum Step {
        START,
        AWAITING_NAME,
        AWAITING_SPECIALIZATION,
        AWAITING_DATE,
        AWAITING_TIME
    }

    private Step currentStep = Step.START;
    private String name;
    private String specialization;
    private String date;
    private String time;

    // Getters and Setters
    public Step getCurrentStep() { return currentStep; }
    public void setCurrentStep(Step currentStep) { this.currentStep = currentStep; }
    
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    
    public String getSpecialization() { return specialization; }
    public void setSpecialization(String specialization) { this.specialization = specialization; }
    
    public String getDate() { return date; }
    public void setDate(String date) { this.date = date; }
    
    public String getTime() { return time; }
    public void setTime(String time) { this.time = time; }
}