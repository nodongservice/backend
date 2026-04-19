CREATE TABLE IF NOT EXISTS public_data_record_field (
    id BIGSERIAL PRIMARY KEY,
    record_id BIGINT NOT NULL,
    field_path VARCHAR(255) NOT NULL,
    field_value TEXT,
    value_type VARCHAR(32) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_public_data_record_field_record
        FOREIGN KEY (record_id)
        REFERENCES public_data_record (id)
        ON DELETE CASCADE,
    CONSTRAINT uk_public_data_record_field UNIQUE (record_id, field_path)
);

CREATE INDEX IF NOT EXISTS idx_public_data_record_field_record_id ON public_data_record_field (record_id);
CREATE INDEX IF NOT EXISTS idx_public_data_record_field_path ON public_data_record_field (field_path);
