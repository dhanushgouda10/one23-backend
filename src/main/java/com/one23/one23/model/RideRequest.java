package com.one23.one23.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import java.time.LocalDateTime;

// Entity representing a ride request (one row = one user's ride).
// Rides in the same group share the same groupId.
// Indexes match the actual queries (status, group_id, user_id, cleanup by created_at + status).
@Entity
@Table(
        name = "ride_request",
        indexes = {
                @Index(name = "idx_ride_request_status", columnList = "status"),
                @Index(name = "idx_ride_request_group_id", columnList = "group_id"),
                @Index(name = "idx_ride_request_user_id", columnList = "user_id"),
                @Index(name = "idx_ride_request_created_at_status", columnList = "created_at, status")
        }
)
public class RideRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;

    @NotBlank(message = "Pickup hub is required")
    @Column(name = "pickup_hub")
    private String pickupHub;

    @NotBlank(message = "Destination is required")
    private String destination;

    private LocalDateTime createdAt;

    private String groupId;

    private String status;

    // Link ride to registered user (optional for older data)
    @ManyToOne
    @JoinColumn(name = "user_id", nullable = true)
    private User user;

    public RideRequest() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getPickupHub() {
        return pickupHub;
    }

    public void setPickupHub(String pickupHub) {
        this.pickupHub = pickupHub;
    }

    public String getDestination() {
        return destination;
    }

    public void setDestination(String destination) {
        this.destination = destination;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public String getGroupId() {
        return groupId;
    }

    public void setGroupId(String groupId) {
        this.groupId = groupId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }
}
