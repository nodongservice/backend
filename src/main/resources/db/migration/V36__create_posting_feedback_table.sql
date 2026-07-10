CREATE TABLE IF NOT EXISTS posting_feedback (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    posting_id BIGINT NOT NULL,
    reaction VARCHAR(16) NOT NULL,
    comment VARCHAR(1000) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_posting_feedback_user FOREIGN KEY (user_id) REFERENCES app_user (id) ON DELETE CASCADE,
    CONSTRAINT fk_posting_feedback_posting FOREIGN KEY (posting_id) REFERENCES pd_kepad_recruitment (id) ON DELETE RESTRICT,
    CONSTRAINT ck_posting_feedback_reaction CHECK (reaction IN ('LIKE', 'DISLIKE'))
);

CREATE INDEX IF NOT EXISTS idx_posting_feedback_posting_created_at
    ON posting_feedback (posting_id, created_at DESC, id DESC);

CREATE INDEX IF NOT EXISTS idx_posting_feedback_reaction_created_at
    ON posting_feedback (reaction, created_at DESC, id DESC);

CREATE INDEX IF NOT EXISTS idx_posting_feedback_daily_counts
    ON posting_feedback (created_at DESC);
