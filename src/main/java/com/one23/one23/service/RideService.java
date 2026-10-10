package com.one23.one23.service;

import com.one23.one23.dto.RideJoinRequest;
import com.one23.one23.model.RideRequest;
import com.one23.one23.model.RideStatus;
import com.one23.one23.model.User;
import com.one23.one23.repository.RideRequestRepository;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

// Ride and group rules. RideController and ChatController call this class;
// it does the database work and decides what is allowed.
// (Choosing HTTP status codes stays in the controllers.)
@Service
public class RideService {

    // A user counts as a group member for chat and live location in these statuses.
    // COMPLETED is included so members can still read the chat after the ride.
    private static final Set<String> ACTIVE_GROUP_STATUSES =
            Set.of(RideStatus.MATCHED, RideStatus.IN_PROGRESS, RideStatus.COMPLETED);

    // A user may have only one ride in these statuses at a time
    private static final Set<String> OPEN_RIDE_STATUSES =
            Set.of(RideStatus.WAITING, RideStatus.MATCHED, RideStatus.IN_PROGRESS);

    private final RideRequestRepository rideRequestRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final MatchingService matchingService;

    public RideService(RideRequestRepository rideRequestRepository,
                       SimpMessagingTemplate messagingTemplate,
                       MatchingService matchingService) {
        this.rideRequestRepository = rideRequestRepository;
        this.messagingTemplate = messagingTemplate;
        this.matchingService = matchingService;
    }

    // ---- reading ----

    public List<RideRequest> findMyRides(User user) {
        return rideRequestRepository.findByUser(user);
    }

    // Returns null if no ride has this id
    public RideRequest findRide(Long id) {
        return rideRequestRepository.findById(id).orElse(null);
    }

    // All rides in a group (empty list if the group does not exist)
    public List<RideRequest> findGroupRides(String groupId) {
        return rideRequestRepository.findByGroupId(groupId);
    }

    // ---- joining and cancelling a waiting ride ----

    // True if the user already has a WAITING, MATCHED or IN_PROGRESS ride
    // (used to stop one user joining several times and matching with themselves)
    public boolean hasOpenRide(User user) {
        for (RideRequest ride : rideRequestRepository.findByUser(user)) {
            if (OPEN_RIDE_STATUSES.contains(ride.getStatus())) {
                return true;
            }
        }
        return false;
    }

    // Always creates a brand-new WAITING ride for the logged-in user (never from client JSON)
    public RideRequest createWaitingRide(RideJoinRequest joinRequest, User user) {
        RideRequest rideRequest = new RideRequest();
        rideRequest.setPickupHub(joinRequest.getPickupHub());
        rideRequest.setDestination(joinRequest.getDestination());
        rideRequest.setUser(user);
        rideRequest.setName(user.getFullName());
        rideRequest.setCreatedAt(LocalDateTime.now());
        rideRequest.setStatus(RideStatus.WAITING);
        rideRequest.setGroupId(null);
        return rideRequestRepository.save(rideRequest);
    }

    // Only a ride that is still WAITING can be cancelled this way
    public boolean isWaiting(RideRequest ride) {
        return RideStatus.WAITING.equals(ride.getStatus());
    }

    // Returns false if the ride got matched at the same moment (then it is not cancelled).
    // The check + update happens inside MatchingService's lock, see cancelIfStillWaiting.
    public boolean cancelWaitingRide(RideRequest ride) {
        return matchingService.cancelIfStillWaiting(ride.getId());
    }

    // ---- group membership ----

    public boolean isOwnedBy(RideRequest ride, User user) {
        return ride.getUser() != null && ride.getUser().getId().equals(user.getId());
    }

    // True if the user owns one of these rides (any status)
    public boolean isMemberOfGroup(List<RideRequest> rides, User user) {
        for (RideRequest ride : rides) {
            if (isOwnedBy(ride, user)) {
                return true;
            }
        }
        return false;
    }

    // Used by chat and live location: is this user in the group
    // (MATCHED, IN_PROGRESS or COMPLETED)?
    public boolean isActiveMember(String groupId, User user) {
        if (user == null || groupId == null || groupId.isBlank()) {
            return false;
        }

        for (RideRequest ride : rideRequestRepository.findByUser(user)) {
            if (groupId.equals(ride.getGroupId()) && ACTIVE_GROUP_STATUSES.contains(ride.getStatus())) {
                return true;
            }
        }
        return false;
    }

    // ---- group lifecycle ----

    // A group can only be left while it is still MATCHED
    public boolean hasRideStartedOrFinished(List<RideRequest> rides) {
        for (RideRequest ride : rides) {
            if (RideStatus.IN_PROGRESS.equals(ride.getStatus())
                    || RideStatus.COMPLETED.equals(ride.getStatus())) {
                return true;
            }
        }
        return false;
    }

    // Group dissolution:
    //  - the user who leaves becomes CANCELLED
    //  - everyone else goes back to WAITING with a fresh createdAt
    //    (so the 15-minute cleanup doesn't delete them straight away)
    //  - groupId is cleared on every ride
    //  - a DISSOLVED event is sent to /topic/group/{groupId}
    //  - matching runs again, so riders already waiting on this route are grouped immediately
    public void leaveGroup(String groupId, List<RideRequest> rides, User user) {
        RideRequest returnedRide = null;
        for (RideRequest ride : rides) {
            if (isOwnedBy(ride, user)) {
                ride.setStatus(RideStatus.CANCELLED);
            } else {
                ride.setStatus(RideStatus.WAITING);
                ride.setCreatedAt(LocalDateTime.now());
                returnedRide = ride;
            }
            ride.setGroupId(null);
        }
        rideRequestRepository.saveAll(rides);

        messagingTemplate.convertAndSend(
                "/topic/group/" + groupId,
                (Object) Map.of(
                        "type", "DISSOLVED",
                        "message", "A member left — the group was dissolved."
                )
        );

        // All rides in a group share one route, so one matching call is enough
        if (returnedRide != null && hasRoute(returnedRide)) {
            matchingService.addAndMatch(returnedRide);
        }
    }

    private boolean hasRoute(RideRequest ride) {
        return ride.getPickupHub() != null && !ride.getPickupHub().isBlank()
                && ride.getDestination() != null && !ride.getDestination().isBlank();
    }

    // True if every ride in the group has this status (used to check start / end)
    public boolean allHaveStatus(List<RideRequest> rides, String status) {
        for (RideRequest ride : rides) {
            if (!status.equals(ride.getStatus())) {
                return false;
            }
        }
        return true;
    }

    // Used for start (IN_PROGRESS) and end (COMPLETED): same status for the whole group
    public void updateGroupStatus(List<RideRequest> rides, String newStatus) {
        for (RideRequest ride : rides) {
            ride.setStatus(newStatus);
        }
        rideRequestRepository.saveAll(rides);
    }

    // Sends a simple event like {"type": "STARTED"} to everyone in the group lobby
    public void sendGroupEvent(String groupId, String type) {
        messagingTemplate.convertAndSend("/topic/group/" + groupId, (Object) Map.of("type", type));
    }
}
