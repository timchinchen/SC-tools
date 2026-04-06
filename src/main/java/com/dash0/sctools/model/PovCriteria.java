package com.dash0.sctools.model;

import java.time.LocalDateTime;

/**
 * Model representing an evaluation criterion for a Proof of Value (POV).
 */
public class PovCriteria {

    private long id;
    private long povId;
    private String name;
    private String description;
    private String status;
    private int weight;
    private String notes;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public PovCriteria() {
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public long getPovId() {
        return povId;
    }

    public void setPovId(long povId) {
        this.povId = povId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public int getWeight() {
        return weight;
    }

    public void setWeight(int weight) {
        this.weight = weight;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
