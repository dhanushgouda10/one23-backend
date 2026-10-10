package com.one23.one23.service;

import com.one23.one23.dto.RideJoinRequest;
import com.one23.one23.model.RideRequest;
import com.one23.one23.model.RideStatus;
import com.one23.one23.model.User;
import com.one23.one23.repository.RideRequestRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RideServiceTest {

    @Mock
    private RideRequestRepository rideRequestRepository;

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @Mock
    private MatchingService matchingService;

    @InjectMocks
    private RideService rideService;

    private User user(long id, String name) {
        User user = new User();
        ReflectionTestUtils.setField(user, "id", id);
        user.setFullName(name);
        return user;
    }

    private RideRequest ride(User owner, String status, String groupId) {
        RideRequest ride = new RideRequest();
        ride.setUser(owner);
        ride.setStatus(status);
        ride.setGroupId(groupId);
        return ride;
    }

    // ---- joining ----

    @Test
    void createWaitingRide_savesNewWaitingRideWithoutGroup() {
        User alice = user(1, "Alice");
        RideJoinRequest request = new RideJoinRequest();
        request.setPickupHub("Kadugodi");
        request.setDestination("Tech Park");
        when(rideRequestRepository.save(any(RideRequest.class))).thenAnswer(inv -> inv.getArgument(0));

        RideRequest saved = rideService.createWaitingRide(request, alice);

        assertThat(saved.getStatus()).isEqualTo(RideStatus.WAITING);
        assertThat(saved.getGroupId()).isNull();
        assertThat(saved.getName()).isEqualTo("Alice");
        assertThat(saved.getPickupHub()).isEqualTo("Kadugodi");
        assertThat(saved.getDestination()).isEqualTo("Tech Park");
        assertThat(saved.getUser()).isSameAs(alice);
    }

    // ---- cancelling a waiting ride ----

    @Test
    void cancelWaitingRide_goesThroughMatchingServiceLock() {
        RideRequest ride = ride(user(1, "Alice"), RideStatus.WAITING, null);
        ride.setId(7L);
        when(matchingService.cancelIfStillWaiting(7L)).thenReturn(true);

        assertThat(rideService.cancelWaitingRide(ride)).isTrue();
        verify(matchingService).cancelIfStillWaiting(7L);
    }

    @Test
    void cancelWaitingRide_falseWhenRideWasJustMatched() {
        RideRequest ride = ride(user(1, "Alice"), RideStatus.WAITING, null);
        ride.setId(7L);
        when(matchingService.cancelIfStillWaiting(7L)).thenReturn(false);

        assertThat(rideService.cancelWaitingRide(ride)).isFalse();
    }

    @Test
    void isWaiting_onlyTrueForWaitingRides() {
        assertThat(rideService.isWaiting(ride(null, RideStatus.WAITING, null))).isTrue();
        assertThat(rideService.isWaiting(ride(null, RideStatus.MATCHED, "g1"))).isFalse();
    }

    // ---- membership ----

    @Test
    void isMemberOfGroup_trueOnlyForOwnersOfTheGroupRides() {
        User alice = user(1, "Alice");
        User bob = user(2, "Bob");
        User stranger = user(3, "Stranger");
        List<RideRequest> rides = List.of(
                ride(alice, RideStatus.MATCHED, "g1"),
                ride(bob, RideStatus.MATCHED, "g1"));

        assertThat(rideService.isMemberOfGroup(rides, alice)).isTrue();
        assertThat(rideService.isMemberOfGroup(rides, bob)).isTrue();
        assertThat(rideService.isMemberOfGroup(rides, stranger)).isFalse();
    }

    @Test
    void isActiveMember_trueWhileMatchedOrInProgressOrCompleted() {
        User alice = user(1, "Alice");
        when(rideRequestRepository.findByUser(alice))
                .thenReturn(List.of(ride(alice, RideStatus.IN_PROGRESS, "g1")));

        assertThat(rideService.isActiveMember("g1", alice)).isTrue();
    }

    @Test
    void isActiveMember_falseForWaitingCancelledOrOtherGroup() {
        User alice = user(1, "Alice");
        when(rideRequestRepository.findByUser(alice)).thenReturn(List.of(
                ride(alice, RideStatus.WAITING, "g1"),
                ride(alice, RideStatus.CANCELLED, "g1"),
                ride(alice, RideStatus.MATCHED, "other-group")));

        assertThat(rideService.isActiveMember("g1", alice)).isFalse();
    }

    @Test
    void isActiveMember_falseForNullUserOrBlankGroup() {
        assertThat(rideService.isActiveMember("g1", null)).isFalse();
        assertThat(rideService.isActiveMember(" ", user(1, "Alice"))).isFalse();
        assertThat(rideService.isActiveMember(null, user(1, "Alice"))).isFalse();
        verifyNoInteractions(rideRequestRepository);
    }

    // ---- group lifecycle ----

    @Test
    void hasRideStartedOrFinished_trueOnlyAfterStart() {
        List<RideRequest> matched = List.of(ride(null, RideStatus.MATCHED, "g1"));
        List<RideRequest> started = List.of(
                ride(null, RideStatus.MATCHED, "g1"),
                ride(null, RideStatus.IN_PROGRESS, "g1"));
        List<RideRequest> done = List.of(ride(null, RideStatus.COMPLETED, "g1"));

        assertThat(rideService.hasRideStartedOrFinished(matched)).isFalse();
        assertThat(rideService.hasRideStartedOrFinished(started)).isTrue();
        assertThat(rideService.hasRideStartedOrFinished(done)).isTrue();
    }

    @Test
    void leaveGroup_cancelsLeaver_returnsOthersToWaiting_clearsGroup_andBroadcastsDissolved() {
        User alice = user(1, "Alice");
        User bob = user(2, "Bob");
        User carol = user(3, "Carol");
        RideRequest aliceRide = ride(alice, RideStatus.MATCHED, "g1");
        RideRequest bobRide = ride(bob, RideStatus.MATCHED, "g1");
        RideRequest carolRide = ride(carol, RideStatus.MATCHED, "g1");
        List<RideRequest> rides = List.of(aliceRide, bobRide, carolRide);

        rideService.leaveGroup("g1", rides, carol);

        assertThat(carolRide.getStatus()).isEqualTo(RideStatus.CANCELLED);
        assertThat(aliceRide.getStatus()).isEqualTo(RideStatus.WAITING);
        assertThat(bobRide.getStatus()).isEqualTo(RideStatus.WAITING);
        assertThat(rides).allSatisfy(r -> assertThat(r.getGroupId()).isNull());
        verify(rideRequestRepository).saveAll(rides);
        verify(messagingTemplate).convertAndSend(eq("/topic/group/g1"), (Object) any());
    }

    @Test
    void updateGroupStatus_setsSameStatusOnEveryRide() {
        List<RideRequest> rides = List.of(
                ride(null, RideStatus.MATCHED, "g1"),
                ride(null, RideStatus.MATCHED, "g1"),
                ride(null, RideStatus.MATCHED, "g1"));

        rideService.updateGroupStatus(rides, RideStatus.IN_PROGRESS);

        assertThat(rides).allSatisfy(r -> assertThat(r.getStatus()).isEqualTo(RideStatus.IN_PROGRESS));
        verify(rideRequestRepository).saveAll(rides);
    }

    // ---- deployment-readiness fixes ----

    @Test
    void leaveGroup_resetsWaitTime_andRunsMatchingAgain() {
        User alice = user(1, "Alice");
        User bob = user(2, "Bob");
        User carol = user(3, "Carol");
        LocalDateTime longAgo = LocalDateTime.now().minusMinutes(14);
        List<RideRequest> rides = List.of(
                routedRide(alice, longAgo), routedRide(bob, longAgo), routedRide(carol, longAgo));

        rideService.leaveGroup("g1", rides, carol);

        assertThat(rides.get(0).getCreatedAt()).isAfter(longAgo);
        assertThat(rides.get(1).getCreatedAt()).isAfter(longAgo);
        assertThat(rides.get(2).getCreatedAt()).isEqualTo(longAgo); // the leaver is CANCELLED, not re-queued
        verify(matchingService, times(1)).addAndMatch(any(RideRequest.class));
    }

    private RideRequest routedRide(User owner, LocalDateTime createdAt) {
        RideRequest ride = ride(owner, RideStatus.MATCHED, "g1");
        ride.setPickupHub("Kadugodi");
        ride.setDestination("Tech Park");
        ride.setCreatedAt(createdAt);
        return ride;
    }

    @Test
    void hasOpenRide_trueForWaitingMatchedOrInProgress_falseOtherwise() {
        User alice = user(1, "Alice");
        when(rideRequestRepository.findByUser(alice)).thenReturn(List.of(
                ride(alice, RideStatus.COMPLETED, "g0"),
                ride(alice, RideStatus.CANCELLED, null)));
        assertThat(rideService.hasOpenRide(alice)).isFalse();

        when(rideRequestRepository.findByUser(alice)).thenReturn(List.of(
                ride(alice, RideStatus.COMPLETED, "g0"),
                ride(alice, RideStatus.WAITING, null)));
        assertThat(rideService.hasOpenRide(alice)).isTrue();
    }

    @Test
    void allHaveStatus_checksEveryRide() {
        List<RideRequest> matched = List.of(
                ride(null, RideStatus.MATCHED, "g1"), ride(null, RideStatus.MATCHED, "g1"));
        List<RideRequest> mixed = List.of(
                ride(null, RideStatus.MATCHED, "g1"), ride(null, RideStatus.IN_PROGRESS, "g1"));

        assertThat(rideService.allHaveStatus(matched, RideStatus.MATCHED)).isTrue();
        assertThat(rideService.allHaveStatus(mixed, RideStatus.MATCHED)).isFalse();
        assertThat(rideService.allHaveStatus(matched, RideStatus.IN_PROGRESS)).isFalse();
    }

    @Test
    void sendGroupEvent_sendsTypeToGroupTopic() {
        rideService.sendGroupEvent("g1", "STARTED");

        verify(messagingTemplate).convertAndSend("/topic/group/g1", (Object) Map.of("type", "STARTED"));
    }
}
