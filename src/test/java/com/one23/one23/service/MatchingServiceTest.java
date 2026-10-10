package com.one23.one23.service;

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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MatchingServiceTest {

    @Mock
    private RideRequestRepository rideRequestRepository;

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @InjectMocks
    private MatchingService matchingService;

    // A saved WAITING ride with its own id and its own user (user id = ride id).
    // minutesAgo = how long this rider has been waiting.
    private RideRequest ride(long id, String pickupHub, String destination, int minutesAgo) {
        User user = new User();
        ReflectionTestUtils.setField(user, "id", id);

        RideRequest ride = new RideRequest();
        ride.setId(id);
        ride.setUser(user);
        ride.setPickupHub(pickupHub);
        ride.setDestination(destination);
        ride.setStatus(RideStatus.WAITING);
        ride.setCreatedAt(LocalDateTime.now().minusMinutes(minutesAgo));
        return ride;
    }

    private void waitingInDatabase(RideRequest... rides) {
        when(rideRequestRepository.findByStatusOrderByCreatedAtAsc(RideStatus.WAITING))
                .thenReturn(List.of(rides));
    }

    // ---- input checks ----

    @Test
    void addAndMatch_rejectsBlankPickupHub() {
        assertThatThrownBy(() -> matchingService.addAndMatch(ride(1, "", "Airport", 0)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("pickupHub is required");

        verifyNoInteractions(rideRequestRepository, messagingTemplate);
    }

    @Test
    void addAndMatch_rejectsBlankDestination() {
        assertThatThrownBy(() -> matchingService.addAndMatch(ride(1, "Downtown", " ", 0)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("destination is required");
    }

    @Test
    void addAndMatch_rejectsRideThatWasNotSaved() {
        RideRequest unsaved = ride(1, "Downtown", "Airport", 0);
        unsaved.setId(null);

        assertThatThrownBy(() -> matchingService.addAndMatch(unsaved))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(rideRequestRepository);
    }

    // ---- not enough riders ----

    @Test
    void addAndMatch_returnsNullWhenFewerThanThreeWaiting() {
        RideRequest a = ride(1, "Downtown", "Airport", 5);
        RideRequest joiner = ride(2, "Downtown", "Airport", 0);
        waitingInDatabase(a, joiner);

        assertThat(matchingService.addAndMatch(joiner)).isNull();
        verifyNoInteractions(messagingTemplate);
        verify(rideRequestRepository, never()).saveAll(any());
    }

    @Test
    void addAndMatch_ignoresRidesAtDifferentHubOrDestination() {
        RideRequest joiner = ride(1, "Downtown", "Airport", 0);
        waitingInDatabase(
                ride(2, "Downtown", "Airport", 3),
                ride(3, "Downtown", "Mall", 2),    // different destination
                ride(4, "Uptown", "Airport", 1),   // different hub
                joiner);

        assertThat(matchingService.addAndMatch(joiner)).isNull();
    }

    @Test
    void addAndMatch_neverGroupsTheSameUserTwice() {
        RideRequest aliceRide1 = ride(1, "Downtown", "Airport", 5);
        RideRequest aliceRide2 = ride(2, "Downtown", "Airport", 4);
        aliceRide2.setUser(aliceRide1.getUser()); // same user, two rides (e.g. double click)
        RideRequest bobRide = ride(3, "Downtown", "Airport", 0);
        waitingInDatabase(aliceRide1, aliceRide2, bobRide);

        // Only 2 different users are waiting, so no group is formed
        assertThat(matchingService.addAndMatch(bobRide)).isNull();
        verify(rideRequestRepository, never()).saveAll(any());
    }

    @Test
    void addAndMatch_returnsNullWhenJoinersRideIsNoLongerWaiting() {
        // The joiner's ride was cancelled meanwhile, so the database does not return it
        RideRequest joiner = ride(9, "Downtown", "Airport", 0);
        waitingInDatabase(
                ride(1, "Downtown", "Airport", 3),
                ride(2, "Downtown", "Airport", 2),
                ride(3, "Downtown", "Airport", 1));

        assertThat(matchingService.addAndMatch(joiner)).isNull();
        verify(rideRequestRepository, never()).saveAll(any());
    }

    // ---- group formed ----

    @Test
    void addAndMatch_matchesThreeRiders_ignoringCaseAndExtraSpaces_andBroadcasts() {
        RideRequest a = ride(1, "Downtown", "Manyata Tech Park", 2);
        RideRequest b = ride(2, " DOWNTOWN ", "manyata   TECH park", 1);
        RideRequest joiner = ride(3, "downtown", "Manyata Tech Park ", 0);
        waitingInDatabase(a, b, joiner);

        List<RideRequest> group = matchingService.addAndMatch(joiner);

        assertThat(group).containsExactlyInAnyOrder(a, b, joiner);
        assertThat(group).allSatisfy(ride -> assertThat(ride.getStatus()).isEqualTo(RideStatus.MATCHED));
        assertThat(group.stream().map(RideRequest::getGroupId).distinct()).hasSize(1);
        // The text saved in the database is NOT changed by normalization
        assertThat(b.getDestination()).isEqualTo("manyata   TECH park");

        verify(rideRequestRepository).saveAll(group);
        // Only a "refresh" signal is broadcast — no names or group ids
        verify(messagingTemplate).convertAndSend("/topic/match", (Object) Map.of("type", "REFRESH"));
    }

    @Test
    void addAndMatch_joinerIsAlwaysInGroup_withTheTwoLongestWaitingRiders() {
        // 5 riders already waiting (possible after someone left a group), joiner is newest
        RideRequest oldest = ride(1, "Downtown", "Airport", 10);
        RideRequest second = ride(2, "Downtown", "Airport", 8);
        RideRequest third = ride(3, "Downtown", "Airport", 6);
        RideRequest fourth = ride(4, "Downtown", "Airport", 4);
        RideRequest joiner = ride(5, "Downtown", "Airport", 0);
        waitingInDatabase(oldest, second, third, fourth, joiner);

        List<RideRequest> group = matchingService.addAndMatch(joiner);

        assertThat(group).hasSize(3);
        assertThat(group).containsExactlyInAnyOrder(joiner, oldest, second);
        assertThat(third.getStatus()).isEqualTo(RideStatus.WAITING);
        assertThat(fourth.getStatus()).isEqualTo(RideStatus.WAITING);
    }

    // ---- cancelling ----

    @Test
    void cancelIfStillWaiting_trueOnlyWhenTheDatabaseUpdatedOneRow() {
        when(rideRequestRepository.cancelIfWaiting(1L)).thenReturn(1);
        when(rideRequestRepository.cancelIfWaiting(2L)).thenReturn(0); // already matched

        assertThat(matchingService.cancelIfStillWaiting(1L)).isTrue();
        assertThat(matchingService.cancelIfStillWaiting(2L)).isFalse();
    }

    // ---- many people joining the same route at the same time ----

    @Test
    void addAndMatch_manyJoinsAtTheSameTime_formOnlyCorrectGroupsOfThree() throws Exception {
        int riders = 30;

        // A tiny fake "database": the repository mock returns the rides
        // that are WAITING at the moment it is asked.
        List<RideRequest> table = new CopyOnWriteArrayList<>();
        for (int i = 1; i <= riders; i++) {
            table.add(ride(i, "Kadugodi", "Tech Park", 0));
        }
        when(rideRequestRepository.findByStatusOrderByCreatedAtAsc(RideStatus.WAITING)).thenAnswer(inv -> {
            List<RideRequest> waiting = new ArrayList<>();
            for (RideRequest r : table) {
                if (RideStatus.WAITING.equals(r.getStatus())) {
                    waiting.add(r);
                }
            }
            Thread.sleep(2); // make overlapping reads likely if the lock did not work
            return waiting;
        });

        // Every rider joins at the same moment from a different thread
        ExecutorService pool = Executors.newFixedThreadPool(riders);
        CountDownLatch startTogether = new CountDownLatch(1);
        List<Future<List<RideRequest>>> results = new ArrayList<>();
        for (RideRequest joiner : table) {
            results.add(pool.submit(() -> {
                startTogether.await();
                return matchingService.addAndMatch(joiner);
            }));
        }
        startTogether.countDown();

        Map<String, Integer> groupSizes = new HashMap<>();
        for (int i = 0; i < riders; i++) {
            List<RideRequest> group = results.get(i).get(10, TimeUnit.SECONDS);
            if (group != null) {
                assertThat(group).hasSize(3);
                assertThat(group).contains(table.get(i)); // the joiner is in the group it receives
            }
        }
        pool.shutdown();

        for (RideRequest r : table) {
            assertThat(r.getStatus()).isEqualTo(RideStatus.MATCHED);
            groupSizes.merge(r.getGroupId(), 1, Integer::sum);
        }
        // 30 riders -> exactly 10 groups, every group has exactly 3 riders
        assertThat(groupSizes).hasSize(riders / 3);
        assertThat(groupSizes.values()).allMatch(size -> size == 3);
    }
}
