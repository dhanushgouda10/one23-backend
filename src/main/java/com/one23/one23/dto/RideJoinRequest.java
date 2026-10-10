package com.one23.one23.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.NotBlank;

// Request body for POST /api/join.
// Only has the fields a client may set, so a client can never set id/status/groupId/user.
// "location" is also accepted as the JSON name for pickupHub.
public class RideJoinRequest {

    @NotBlank(message = "Pickup hub is required")
    @JsonAlias({"location", "pickupHub"})
    private String pickupHub;

    @NotBlank(message = "Destination is required")
    private String destination;

    public RideJoinRequest() {
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
}
