package com.one23.one23.dto;

import com.one23.one23.model.RideRequest;

import java.util.ArrayList;
import java.util.List;

// Response body for the Group Lobby page (GET /api/groups/{groupId}).
// Fields are filled once in fromRides(); the getters are what Jackson turns into JSON.
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

        // All rides in a group share the same route and status, so the first one is enough
        RideRequest firstRide = rides.get(0);
        response.groupId = firstRide.getGroupId();
        response.pickupHub = firstRide.getPickupHub();
        response.destination = firstRide.getDestination();
        response.status = firstRide.getStatus();

        for (RideRequest ride : rides) {
            Member member = new Member();
            member.fullName = ride.getName();
            // Email is optional because older rides may not have a linked user record
            member.email = ride.getUser() != null ? ride.getUser().getEmail() : null;
            member.rideStatus = ride.getStatus();
            response.members.add(member);
        }

        return response;
    }

    public String getGroupId() {
        return groupId;
    }

    public String getPickupHub() {
        return pickupHub;
    }

    public String getDestination() {
        return destination;
    }

    public String getStatus() {
        return status;
    }

    public List<Member> getMembers() {
        return members;
    }

    // One person in the group
    public static class Member {

        private String fullName;
        private String email;
        private String rideStatus;

        public String getFullName() {
            return fullName;
        }

        public String getEmail() {
            return email;
        }

        public String getRideStatus() {
            return rideStatus;
        }
    }
}
