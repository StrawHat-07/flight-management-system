-- Seat reservations table for tracking temporary inventory holds
-- Source of truth for all active reservations (replaces Redis-based tracking)

CREATE TABLE seat_reservations (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    booking_id VARCHAR(50) NOT NULL,
    flight_id VARCHAR(36) NOT NULL,
    seats INT NOT NULL,
    expires_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    
    UNIQUE KEY uk_booking_flight (booking_id, flight_id),
    INDEX idx_expires_at (expires_at),
    INDEX idx_flight_id (flight_id),
    
    CONSTRAINT fk_reservation_flight 
        FOREIGN KEY (flight_id) REFERENCES flights(flight_id)
        ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
