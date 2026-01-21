-- Flight Service Schema Migration V4
-- Rename src -> source, dest -> destination
-- Add auto-increment id column

-- Add auto-increment id column
ALTER TABLE flights ADD COLUMN id BIGINT AUTO_INCREMENT FIRST, ADD UNIQUE KEY uk_id (id);

-- Rename columns for better naming
ALTER TABLE flights CHANGE COLUMN src source VARCHAR(50) NOT NULL;
ALTER TABLE flights CHANGE COLUMN dest destination VARCHAR(50) NOT NULL;

-- Update indexes
DROP INDEX idx_src_dest ON flights;
CREATE INDEX idx_source_destination ON flights (source, destination);
