-- Add TIMEOUT status to bookings table
-- This status is used when a booking's payment window expires

ALTER TABLE bookings 
MODIFY COLUMN status ENUM('PENDING', 'CONFIRMED', 'FAILED', 'TIMEOUT') NOT NULL DEFAULT 'PENDING';
