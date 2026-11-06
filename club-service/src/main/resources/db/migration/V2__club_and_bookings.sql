CREATE TABLE trainers (
    id UUID PRIMARY KEY,
    name VARCHAR(160) NOT NULL,
    specialty VARCHAR(160) NOT NULL,
    bio VARCHAR(1000) NOT NULL,
    initials VARCHAR(8) NOT NULL
);

CREATE TABLE club_classes (
    id UUID PRIMARY KEY,
    title VARCHAR(160) NOT NULL,
    class_type VARCHAR(24) NOT NULL CHECK (class_type IN ('STRENGTH', 'FUNCTIONAL', 'STRETCH')),
    trainer_id UUID NOT NULL REFERENCES trainers(id),
    room VARCHAR(100) NOT NULL,
    starts_at TIMESTAMP WITH TIME ZONE NOT NULL,
    ends_at TIMESTAMP WITH TIME ZONE NOT NULL,
    capacity INTEGER NOT NULL CHECK (capacity > 0),
    CHECK (ends_at > starts_at)
);

CREATE INDEX idx_club_classes_start ON club_classes(starts_at);

CREATE TABLE bookings (
    id UUID PRIMARY KEY,
    class_id UUID NOT NULL REFERENCES club_classes(id),
    member_id UUID NOT NULL,
    status VARCHAR(20) NOT NULL CHECK (status IN ('CONFIRMED', 'CANCELLED')),
    booked_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (class_id, member_id)
);

CREATE INDEX idx_bookings_member ON bookings(member_id, status);
CREATE INDEX idx_bookings_class ON bookings(class_id, status);
