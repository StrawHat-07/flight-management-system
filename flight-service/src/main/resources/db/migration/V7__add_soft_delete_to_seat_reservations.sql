-- Add soft delete support to seat_reservations table
ALTER TABLE seat_reservations ADD COLUMN deleted_at TIMESTAMP NULL;

-- Index for querying active (non-deleted) reservations efficiently
CREATE INDEX idx_seat_reservations_deleted_at ON seat_reservations(deleted_at);
