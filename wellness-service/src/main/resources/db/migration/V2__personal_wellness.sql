CREATE TABLE wellness_member_settings (
    member_id UUID PRIMARY KEY,
    water_goal_ml INTEGER NOT NULL DEFAULT 2000 CHECK (water_goal_ml BETWEEN 250 AND 10000)
);

CREATE TABLE wellness_days (
    member_id UUID NOT NULL,
    entry_date DATE NOT NULL,
    sleep_minutes INTEGER CHECK (sleep_minutes BETWEEN 15 AND 1440),
    weight_kg NUMERIC(5,2) CHECK (weight_kg BETWEEN 30 AND 300),
    height_cm NUMERIC(5,2) CHECK (height_cm BETWEEN 100 AND 250),
    PRIMARY KEY (member_id, entry_date),
    CHECK ((weight_kg IS NULL AND height_cm IS NULL) OR (weight_kg IS NOT NULL AND height_cm IS NOT NULL))
);

CREATE TABLE wellness_water_entries (
    id UUID PRIMARY KEY,
    member_id UUID NOT NULL,
    entry_date DATE NOT NULL,
    amount_ml INTEGER NOT NULL CHECK (amount_ml BETWEEN 1 AND 2000),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE INDEX wellness_water_member_date ON wellness_water_entries (member_id, entry_date, created_at);

UPDATE service_identity SET schema_version = 2 WHERE id = 1;
