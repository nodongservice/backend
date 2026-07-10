ALTER TABLE user_profile
    ADD COLUMN certification_entries_json TEXT NOT NULL DEFAULT '[]',
    ADD COLUMN language_entries_json TEXT NOT NULL DEFAULT '[]',
    ADD COLUMN portfolio_entries_json TEXT NOT NULL DEFAULT '[]',
    ADD COLUMN award_entries_json TEXT NOT NULL DEFAULT '[]',
    ADD COLUMN training_entries_json TEXT NOT NULL DEFAULT '[]';
