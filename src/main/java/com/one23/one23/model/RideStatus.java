package com.one23.one23.model;

// The possible values of RideRequest.status (stored as plain text in the database).
//
//   WAITING     -> looking for a group
//   MATCHED     -> in a group of 3, ride not started yet
//   IN_PROGRESS -> ride started
//   COMPLETED   -> ride finished
//   CANCELLED   -> user left or cancelled
public class RideStatus {

    public static final String WAITING = "WAITING";
    public static final String MATCHED = "MATCHED";
    public static final String IN_PROGRESS = "IN_PROGRESS";
    public static final String COMPLETED = "COMPLETED";
    public static final String CANCELLED = "CANCELLED";

    private RideStatus() {
    }
}
