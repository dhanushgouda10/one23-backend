package com.one23.one23.service;

import com.one23.one23.model.RideRequest;
import com.one23.one23.model.RideStatus;
import com.one23.one23.repository.RideRequestRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

// Groups riders who wait at the same metro station (pickupHub) for the same destination.
//
// CONCURRENCY RULE: every change to a WAITING ride (forming a group, cancelling)
// goes through a "synchronized" method of this class. Only one thread can be inside
// these methods at a time, so two requests can never grab the same waiting rider.
// (synchronized works inside one backend instance — this app runs as one instance.)
@Service
public class MatchingService {

    private static final Logger logger = LoggerFactory.getLogger(MatchingService.class);

    // A group is formed when this many different users wait on the same route
    private static final int RIDERS_PER_GROUP = 3;

    private final SimpMessagingTemplate messagingTemplate;
    private final RideRequestRepository rideRequestRepository;

    public MatchingService(SimpMessagingTemplate messagingTemplate, RideRequestRepository rideRequestRepository) {
        this.messagingTemplate = messagingTemplate;
        this.rideRequestRepository = rideRequestRepository;
    }

    // Tries to put this (already saved) ride into a group of exactly 3.
    // The group is: this ride + the 2 riders who have waited longest on the same route.
    // Returns the 3 rides of the new group, or null if there are not enough riders yet.
    //
    // Why there is NO @Transactional here:
    // With @Transactional, Spring commits AFTER this method returns, i.e. after the
    // synchronized lock is released. A second request could then read the old
    // "WAITING" rows before our commit and put the same riders in a second group.
    // Without it, saveAll() commits on its own while we still hold the lock.
    public synchronized List<RideRequest> addAndMatch(RideRequest request) {
        requireRoute(request);
        if (request.getId() == null) {
            throw new IllegalArgumentException("Ride must be saved before matching");
        }

        String hubKey = normalize(request.getPickupHub());
        String destinationKey = normalize(request.getDestination());

        // Fresh list from the database, oldest first
        List<RideRequest> waitingOnRoute = findWaitingRidesOnRoute(hubKey, destinationKey);

        // The joiner's own ride must still be WAITING (it may have been cancelled or matched meanwhile)
        RideRequest joinerRide = findById(waitingOnRoute, request.getId());
        if (joinerRide == null) {
            return null;
        }

        List<RideRequest> group = pickGroup(joinerRide, waitingOnRoute);
        if (group.size() < RIDERS_PER_GROUP) {
            return null;
        }

        String groupId = UUID.randomUUID().toString();
        for (RideRequest ride : group) {
            ride.setStatus(RideStatus.MATCHED);
            ride.setGroupId(groupId);
        }
        rideRequestRepository.saveAll(group); // all 3 rows saved in one transaction

        // Tell connected clients "something changed, reload your rides".
        // No names or group ids are sent, so strangers learn nothing about the new group.
        messagingTemplate.convertAndSend("/topic/match", (Object) Map.of("type", "REFRESH"));
        logger.info("Group created: {} at hub: {} -> {}", groupId, hubKey, destinationKey);

        return group;
    }

    // Cancels a ride only if it is still WAITING. Returns false if it was already
    // matched (or cancelled). Runs under the same lock as addAndMatch, so a ride can
    // never be cancelled and put into a group at the same moment.
    public synchronized boolean cancelIfStillWaiting(Long rideId) {
        return rideRequestRepository.cancelIfWaiting(rideId) == 1;
    }

    // Joiner first, then the oldest waiting riders, one ride per user, max 3 rides.
    private List<RideRequest> pickGroup(RideRequest joinerRide, List<RideRequest> waitingOnRoute) {
        List<RideRequest> group = new ArrayList<>();
        Set<Long> usersInGroup = new HashSet<>();

        group.add(joinerRide);
        addUserId(usersInGroup, joinerRide);

        for (RideRequest ride : waitingOnRoute) {
            if (group.size() == RIDERS_PER_GROUP) {
                break;
            }
            if (ride.getId().equals(joinerRide.getId())) {
                continue; // already in the group
            }
            // add() returns false if this user is already in the group -> skip
            // (nobody can be grouped with themselves)
            if (ride.getUser() != null && !usersInGroup.add(ride.getUser().getId())) {
                continue;
            }
            group.add(ride);
        }
        return group;
    }

    private void addUserId(Set<Long> userIds, RideRequest ride) {
        if (ride.getUser() != null) {
            userIds.add(ride.getUser().getId());
        }
    }

    private RideRequest findById(List<RideRequest> rides, Long id) {
        for (RideRequest ride : rides) {
            if (id.equals(ride.getId())) {
                return ride;
            }
        }
        return null;
    }

    // A ride needs both a pickup hub and a destination to be matched
    private void requireRoute(RideRequest request) {
        if (request.getPickupHub() == null || request.getPickupHub().isBlank()) {
            throw new IllegalArgumentException("pickupHub is required");
        }
        if (request.getDestination() == null || request.getDestination().isBlank()) {
            throw new IllegalArgumentException("destination is required");
        }
    }

    // All WAITING rides with the same (normalized) hub + destination, oldest first
    private List<RideRequest> findWaitingRidesOnRoute(String hubKey, String destinationKey) {
        List<RideRequest> waiting = rideRequestRepository.findByStatusOrderByCreatedAtAsc(RideStatus.WAITING);

        List<RideRequest> onRoute = new ArrayList<>();
        for (RideRequest ride : waiting) {
            if (isOnRoute(ride, hubKey, destinationKey)) {
                onRoute.add(ride);
            }
        }
        return onRoute;
    }

    private boolean isOnRoute(RideRequest ride, String hubKey, String destinationKey) {
        if (ride.getPickupHub() == null || ride.getDestination() == null) {
            return false;
        }
        return normalize(ride.getPickupHub()).equals(hubKey)
                && normalize(ride.getDestination()).equals(destinationKey);
    }

    // Only used for COMPARING routes — the text saved in the database is not changed.
    // "  Manyata   Tech Park " -> "manyata tech park"
    // Locale.ROOT so lower-casing behaves the same on every server language setting.
    private String normalize(String value) {
        return value.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }
}
