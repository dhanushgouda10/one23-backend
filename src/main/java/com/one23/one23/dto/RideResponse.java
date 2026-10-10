package com.one23.one23.dto;

import com.one23.one23.model.RideRequest;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

// Ride data sent to the frontend (/api/join, /api/my-rides, /topic/match).
// Does not include the User object, so other riders' emails/ids are never exposed.
public class RideResponse {

    private Long id;
    private String name;
    private String pickupHub;
    private String destination;
    private LocalDateTime createdAt;
    private String groupId;
    private String status;

    public static RideResponse from(RideRequest ride) {
        RideResponse response = new RideResponse();
        response.id = ride.getId();
        response.name = ride.getName();
        response.pickupHub = ride.getPickupHub();
        response.destination = ride.getDestination();
        response.createdAt = ride.getCreatedAt();
        response.groupId = ride.getGroupId();
        response.status = ride.getStatus();
        return response;
    }

    // Converts a list of rides
    public static List<RideResponse> fromList(List<RideRequest> rides) {
        List<RideResponse> responses = new ArrayList<>();
        for (RideRequest ride : rides) {
            responses.add(RideResponse.from(ride));
        }
        return responses;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getPickupHub() {
        return pickupHub;
    }

    public String getDestination() {
        return destination;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public String getGroupId() {
        return groupId;
    }

    public String getStatus() {
        return status;
    }
}
