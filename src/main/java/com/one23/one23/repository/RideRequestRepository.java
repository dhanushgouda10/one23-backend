package com.one23.one23.repository;

import com.one23.one23.model.RideRequest;
import com.one23.one23.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

// Repository for ride request database operations
public interface RideRequestRepository extends JpaRepository<RideRequest, Long> {

    // Used by CleanupScheduler. Returns the number of rows deleted.
    long deleteByCreatedAtBeforeAndStatusIn(LocalDateTime time, List<String> statuses);

    // Used by MatchingService: waiting rides, oldest first (first come, first served)
    List<RideRequest> findByStatusOrderByCreatedAtAsc(String status);

    // Used by my-rides API
    List<RideRequest> findByUser(User user);

    // Used by group lobby, start, end and cancel-group
    List<RideRequest> findByGroupId(String groupId);

    // Cancels the ride ONLY if it is still WAITING, in one SQL statement.
    // Returns 1 if it was cancelled, 0 if it was not WAITING any more (e.g. just matched).
    @Modifying
    @Transactional
    @Query("UPDATE RideRequest r SET r.status = 'CANCELLED' WHERE r.id = :id AND r.status = 'WAITING'")
    int cancelIfWaiting(@Param("id") Long id);
}
