ALTER TABLE club_classes ADD COLUMN description VARCHAR(1500) NOT NULL DEFAULT '';
ALTER TABLE club_classes ADD COLUMN technique VARCHAR(1500) NOT NULL DEFAULT '';
ALTER TABLE club_classes ADD COLUMN level VARCHAR(20) NOT NULL DEFAULT 'ALL_LEVELS';
ALTER TABLE club_classes ADD COLUMN cancelled BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE club_classes ADD COLUMN demo BOOLEAN NOT NULL DEFAULT FALSE;
UPDATE club_classes SET demo = TRUE;
UPDATE club_classes SET description = CASE class_type
    WHEN 'STRENGTH' THEN 'Силовое занятие с разминкой и разбором техники базовых движений.'
    WHEN 'STRETCH' THEN 'Мягкая работа над подвижностью и гибкостью в комфортном темпе.'
    ELSE 'Круговая тренировка на выносливость и координацию.'
END WHERE description = '';
UPDATE club_classes SET technique = 'Начни без дополнительного веса. Стопы полностью на полу. Согни колени и отведи таз назад. Вернись в исходное положение плавно.'
WHERE title = 'Силовая база';

ALTER TABLE bookings ADD COLUMN member_name VARCHAR(80);
ALTER TABLE trainers ADD COLUMN account_id UUID UNIQUE;

CREATE TABLE club_notices (
    id UUID PRIMARY KEY,
    member_id UUID,
    title VARCHAR(160) NOT NULL,
    body VARCHAR(1000) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX idx_club_notices_member_time ON club_notices(member_id, created_at DESC);
