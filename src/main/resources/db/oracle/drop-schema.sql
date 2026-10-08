-- Drops everything create-schema.sql creates, child tables before the lot_stages table they reference.
-- Indexes and constraints go with their tables.

DROP TABLE lot_stage_events PURGE;
DROP TABLE lots PURGE;
DROP TABLE scans PURGE;
DROP TABLE lot_stage_next_wip_locations PURGE;
DROP TABLE lot_stage_restricted_next_stages PURGE;
DROP TABLE lot_stage_wip_locations PURGE;
DROP TABLE lot_stage_next_stages PURGE;
DROP TABLE lot_stages PURGE;
