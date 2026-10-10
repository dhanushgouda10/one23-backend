package com.one23.one23.controller;

import com.one23.one23.dto.GroupLobbyResponse;
import com.one23.one23.dto.RideJoinRequest;
import com.one23.one23.dto.RideResponse;
import com.one23.one23.model.RideRequest;
import com.one23.one23.model.RideStatus;
import com.one23.one23.model.User;
import com.one23.one23.repository.UserRepository;
import com.one23.one23.service.MatchingService;
import com.one23.one23.service.RideService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

// REST endpoints for rides: join, my rides, cancel, leave group, group lobby, start, end.
// The controller checks the request and picks the HTTP status; RideService does the work.
// CORS is configured globally in CorsConfig.
@RestController
@RequestMapping("/api")
public class RideController {

    private final RideService rideService;
    private final MatchingService matchingService;
    private final UserRepository userRepository;

    public RideController(RideService rideService,
                          MatchingService matchingService,
                          UserRepository userRepository) {
        this.rideService = rideService;
        this.matchingService = matchingService;
        this.userRepository = userRepository;
    }

    // Join a ride: save a new WAITING request, then try to form a group of 3.
    // pickupHub/destination are validated by @Valid on RideJoinRequest.
    @PostMapping("/join")
    public ResponseEntity<?> createRide(@Valid @RequestBody RideJoinRequest joinRequest,
                                        Authentication authentication) {
        User user = currentUser(authentication);
        if (user == null) {
            return unauthorized();
        }

        // One open ride per user, so nobody can be matched with themselves
        if (rideService.hasOpenRide(user)) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("message",
                            "You already have an active ride. Cancel it or finish it before joining again."));
        }

        RideRequest savedRide = rideService.createWaitingRide(joinRequest, user);

        // Returns the 3 matched rides if a group was formed, otherwise null.
        // MatchingService always builds the group around savedRide, so the joiner is in it.
        List<RideRequest> matchedGroup = matchingService.addAndMatch(savedRide);

        if (matchedGroup != null) {
            return ResponseEntity.ok(RideResponse.fromList(matchedGroup));
        }
        return ResponseEntity.ok(RideResponse.from(savedRide));
    }

    // Get all rides created by logged-in user
    @GetMapping("/my-rides")
    public ResponseEntity<?> getMyRides(Authentication authentication) {
        User user = currentUser(authentication);
        if (user == null) {
            return unauthorized();
        }

        return ResponseEntity.ok(RideResponse.fromList(rideService.findMyRides(user)));
    }

    // Cancel a ride that is still WAITING. Only the owner can cancel it.
    // Matched groups must use /cancel-group instead.
    @PatchMapping("/rides/{id}/cancel")
    public ResponseEntity<?> cancelRide(@PathVariable("id") Long id, Authentication authentication) {
        User user = currentUser(authentication);
        if (user == null) {
            return unauthorized();
        }

        RideRequest ride = rideService.findRide(id);
        if (ride == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("message", "Ride not found"));
        }

        if (!rideService.isOwnedBy(ride, user)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("message", "You cannot cancel another user's ride"));
        }

        if (!rideService.isWaiting(ride)) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("message",
                            "This ride is already " + ride.getStatus() +
                                    " and can no longer be cancelled this way."));
        }

        // Can still fail if a group was formed a moment ago
        if (!rideService.cancelWaitingRide(ride)) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("message", "Your ride was just matched with a group. Use leave group instead."));
        }
        return ResponseEntity.ok(Map.of("message", "Ride cancelled successfully"));
    }

    // Leave a matched group (the group is dissolved, see RideService.leaveGroup)
    @PatchMapping("/rides/{groupId}/cancel-group")
    public ResponseEntity<?> cancelGroup(@PathVariable String groupId, Authentication authentication) {
        User user = currentUser(authentication);
        if (user == null) {
            return unauthorized();
        }

        List<RideRequest> rides = rideService.findGroupRides(groupId);
        if (rides.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        if (!rideService.isMemberOfGroup(rides, user)) {
            return notInGroup();
        }

        if (rideService.hasRideStartedOrFinished(rides)) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("message", "Ride already started or completed — cannot cancel now"));
        }

        rideService.leaveGroup(groupId, rides, user);
        return ResponseEntity.ok(
                Map.of("message", "You left the group. Other members are back in the waiting queue.")
        );
    }

    // Load one matched group for the Group Lobby page (members only)
    @GetMapping("/groups/{groupId}")
    public ResponseEntity<?> getGroup(@PathVariable String groupId, Authentication authentication) {
        User user = currentUser(authentication);
        if (user == null) {
            return unauthorized();
        }

        List<RideRequest> rides = rideService.findGroupRides(groupId);
        if (rides.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        if (!rideService.isMemberOfGroup(rides, user)) {
            return notInGroup();
        }

        return ResponseEntity.ok(GroupLobbyResponse.fromRides(rides));
    }

    // Start ride: MATCHED -> IN_PROGRESS for the whole group (members only)
    @PatchMapping("/rides/{groupId}/start")
    public ResponseEntity<?> startRide(@PathVariable String groupId, Authentication authentication) {
        return setGroupStatus(groupId, authentication,
                RideStatus.MATCHED, RideStatus.IN_PROGRESS, "STARTED",
                "Ride started successfully", "This ride can only be started while the group is MATCHED.");
    }

    // End ride: IN_PROGRESS -> COMPLETED for the whole group (members only)
    @PatchMapping("/rides/{groupId}/end")
    public ResponseEntity<?> endRide(@PathVariable String groupId, Authentication authentication) {
        return setGroupStatus(groupId, authentication,
                RideStatus.IN_PROGRESS, RideStatus.COMPLETED, "COMPLETED",
                "Ride completed successfully", "This ride can only be ended after it has started.");
    }

    // Shared by startRide() and endRide().
    // requiredStatus = the status every ride must have now, otherwise 409 Conflict.
    // eventType = sent on /topic/group/{groupId} so the other members see the change live.
    private ResponseEntity<?> setGroupStatus(String groupId, Authentication authentication,
                                             String requiredStatus, String newStatus, String eventType,
                                             String successMessage, String conflictMessage) {
        User user = currentUser(authentication);
        if (user == null) {
            return unauthorized();
        }

        List<RideRequest> rides = rideService.findGroupRides(groupId);
        if (rides.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        if (!rideService.isMemberOfGroup(rides, user)) {
            return notInGroup();
        }

        if (!rideService.allHaveStatus(rides, requiredStatus)) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("message", conflictMessage));
        }

        rideService.updateGroupStatus(rides, newStatus);
        rideService.sendGroupEvent(groupId, eventType);
        return ResponseEntity.ok(Map.of("message", successMessage));
    }

    // ---- helpers ----

    // Finds the logged-in user from the JWT-authenticated request
    private User currentUser(Authentication authentication) {
        String email = authentication.getName();
        return userRepository.findByEmail(email).orElse(null);
    }

    private ResponseEntity<?> unauthorized() {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Map.of("message", "User not found"));
    }

    private ResponseEntity<?> notInGroup() {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(Map.of("message", "You are not part of this group"));
    }
}
