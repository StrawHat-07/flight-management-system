-- Booking Service Schema Migration V1
-- Creates the bookings table for storing booking information

CREATE TABLE IF NOT EXISTS bookings (
    booking_id VARCHAR(36) PRIMARY KEY,
    user_id VARCHAR(36) NOT NULL,
    flight_type ENUM('DIRECT', 'COMPUTED') NOT NULL,
    flight_identifier VARCHAR(255) NOT NULL,
    no_of_seats INT NOT NULL,
    total_price DECIMAL(10, 2) NOT NULL,
    status ENUM('PENDING', 'CONFIRMED', 'FAILED') NOT NULL DEFAULT 'PENDING',
    idempotency_key VARCHAR(100) UNIQUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_user_id (user_id),
    INDEX idx_status (status),
    INDEX idx_idempotency (idempotency_key),
    INDEX idx_flight_identifier (flight_identifier)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Booking flights junction table (for computed flights with multiple legs)
CREATE TABLE IF NOT EXISTS booking_flights (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    booking_id VARCHAR(36) NOT NULL,
    flight_id VARCHAR(36) NOT NULL,
    leg_order INT NOT NULL,
    FOREIGN KEY (booking_id) REFERENCES bookings(booking_id) ON DELETE CASCADE,
    INDEX idx_booking_id (booking_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
