-- Add unique_id column to device table for storing GPS device identifier (not necessarily IMEI)
ALTER TABLE device ADD COLUMN unique_id VARCHAR(100) UNIQUE;

-- Create index for faster lookups
CREATE INDEX idx_device_unique_id ON device(unique_id);
