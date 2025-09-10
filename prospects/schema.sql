-- Schema for the prospects test database
-- This creates a simple table structure for testing

CREATE TABLE IF NOT EXISTS test_data (
    id SERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    value INTEGER DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS regex_patterns (
    id SERIAL PRIMARY KEY,
    pattern VARCHAR(255) NOT NULL,
    description TEXT,
    is_active BOOLEAN DEFAULT true,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Create an index for better query performance
CREATE INDEX IF NOT EXISTS idx_test_data_name ON test_data(name);
CREATE INDEX IF NOT EXISTS idx_regex_patterns_active ON regex_patterns(is_active);