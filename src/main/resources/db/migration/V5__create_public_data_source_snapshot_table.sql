CREATE TABLE IF NOT EXISTS public_data_source_snapshot (
    source_type VARCHAR(64) PRIMARY KEY,
    latest_revision VARCHAR(200) NOT NULL,
    latest_file_name VARCHAR(255) NOT NULL,
    latest_modified_date VARCHAR(10) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);
