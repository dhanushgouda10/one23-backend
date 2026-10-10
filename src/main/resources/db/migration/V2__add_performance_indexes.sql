-- Adds the indexes declared on the entities (RideRequest, ChatMessage —
-- see @Table(indexes = ...) on each) as real, versioned DDL. Unlike V1,
-- this migration DOES run against the existing (baselined) production
-- database, since these indexes don't exist there yet.
--
-- Backs these previously-full-table-scan queries:
--   - ride_request.status              -> MatchingService.addAndMatch() on every /api/join
--   - ride_request.group_id            -> group lobby / start / end / cancel-group
--   - ride_request.user_id             -> GET /api/my-rides
--   - ride_request.created_at + status -> CleanupScheduler, runs every minute
--   - chat_messages.group_id + timestamp -> chat history load + every chat message

CREATE INDEX IF NOT EXISTS idx_ride_request_status
    ON ride_request (status);

CREATE INDEX IF NOT EXISTS idx_ride_request_group_id
    ON ride_request (group_id);

CREATE INDEX IF NOT EXISTS idx_ride_request_user_id
    ON ride_request (user_id);

CREATE INDEX IF NOT EXISTS idx_ride_request_created_at_status
    ON ride_request (created_at, status);

CREATE INDEX IF NOT EXISTS idx_chat_messages_group_id_timestamp
    ON chat_messages (group_id, timestamp);
