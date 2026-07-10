ALTER TABLE user_profile
    ADD COLUMN education_entries_json TEXT NOT NULL DEFAULT '[]',
    ADD COLUMN career_entries_json TEXT NOT NULL DEFAULT '[]',
    ADD COLUMN project_entries_json TEXT NOT NULL DEFAULT '[]';
