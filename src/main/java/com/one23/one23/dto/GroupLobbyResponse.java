package com.one23.one23.dto;

import com.one23.one23.model.RideRequest;

import java.util.ArrayList;
import java.util.List;

// Response body for the Group Lobby page
public class GroupLobbyResponse {

    private String groupId;
    private String pickupHub;
    private String destination;
    private String status;
    private List<Member> members = new ArrayList<>();

    public static GroupLobbyResponse fromRides(List<RideRequest> rides) {
        GroupLobbyResponse response = new GroupLobbyResponse();

        if (rides == null || rides.isEmpty()) {
            return response;
        }

        RideRequest firstRide = rides.get(0);
        response.setGroupId(firstRide.getGroupId());
        response.setPickupHub(firstRide.getPickupHub());
        response.setDestination(firstRide.getDestination());
        response.setStatus(firstRide.getStatus());

        for (RideRequest ride : rides) {
            Member member = new Member();
            member.setFullName(ride.getName());

            // Email is optional because older rides may not have a linked user record
            member.setEmail(ride.getUser() != null ? ride.getUser().getEmail() : null);
            member.setRideStatus(ride.getStatus());
            response.getMembers().add(member);
        }

        return response;
    }

    public String getGroupId() {
        return groupId;
    }

    public void setGroupId(String groupId) {
        this.groupId = groupId;
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

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public List<Member> getMembers() {
        return members;
    }

    public void setMembers(List<Member> members) {
        this.members = members;
    }

    public static class Member {

        private String fullName;
        private String email;
        private String rideStatus;

        public String getFullName() {
            return fullName;
        }

        public void setFullName(String fullName) {
            this.fullName = fullName;
        }

        public String getEmail() {
            return email;
        }

        public void setEmail(String email) {
            this.email = email;
        }

        public String getRideStatus() {
            return rideStatus;
        }

        public void setRideStatus(String rideStatus) {
            this.rideStatus = rideStatus;
        }
    }
}
