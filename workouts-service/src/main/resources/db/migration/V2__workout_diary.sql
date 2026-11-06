CREATE TABLE exercise_catalog (
    id VARCHAR(60) PRIMARY KEY,
    name VARCHAR(160) NOT NULL,
    muscle_group VARCHAR(100) NOT NULL,
    equipment VARCHAR(100) NOT NULL,
    description VARCHAR(500) NOT NULL
);

INSERT INTO exercise_catalog (id, name, muscle_group, equipment, description) VALUES
('goblet-squat', 'Приседания с гантелью', 'Ноги', 'Гантель', 'Приседания с гантелью перед грудью.'),
('dumbbell-bench', 'Жим гантелей лёжа', 'Грудь', 'Гантели и скамья', 'Жим гантелей на горизонтальной скамье.'),
('lat-pulldown', 'Тяга верхнего блока', 'Спина', 'Блочный тренажёр', 'Вертикальная тяга рукояти блока к верхней части груди.'),
('romanian-deadlift', 'Румынская тяга', 'Ноги и спина', 'Штанга', 'Наклон с отведением таза назад и контролируемым возвращением.'),
('lateral-raise', 'Подъём гантелей в стороны', 'Плечи', 'Гантели', 'Подъём гантелей в стороны без рывков.'),
('seated-row', 'Горизонтальная тяга блока', 'Спина', 'Блочный тренажёр', 'Тяга рукояти блока к корпусу сидя.'),
('leg-press', 'Жим ногами', 'Ноги', 'Тренажёр', 'Разгибание ног на платформе тренажёра.'),
('dumbbell-shoulder-press', 'Жим гантелей сидя', 'Плечи', 'Гантели и скамья', 'Жим гантелей вверх из положения сидя.'),
('biceps-curl', 'Сгибание рук с гантелями', 'Руки', 'Гантели', 'Сгибание рук с неподвижным положением локтей.'),
('triceps-pushdown', 'Разгибание рук на блоке', 'Руки', 'Блочный тренажёр', 'Разгибание рук с рукоятью верхнего блока.'),
('bodyweight-lunge', 'Выпады', 'Ноги', 'Собственный вес', 'Выпады с собственным весом или дополнительным отягощением.'),
('push-up', 'Отжимания', 'Грудь и руки', 'Собственный вес', 'Отжимания от пола или возвышения.'),
('hip-thrust', 'Ягодичный мост', 'Ноги', 'Скамья и отягощение', 'Подъём таза с опорой верхней части спины.'),
('crunch', 'Скручивания', 'Корпус', 'Коврик', 'Контролируемое сгибание корпуса лёжа.'),
('calf-raise', 'Подъём на носки', 'Ноги', 'Собственный вес', 'Подъём на носки стоя с контролируемым опусканием.');

CREATE TABLE workout_programs (
    id UUID PRIMARY KEY,
    owner_id UUID,
    name VARCHAR(100) NOT NULL,
    description VARCHAR(500) NOT NULL,
    is_template BOOLEAN NOT NULL,
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CHECK ((is_template = TRUE AND owner_id IS NULL) OR (is_template = FALSE AND owner_id IS NOT NULL))
);

CREATE TABLE workout_program_exercises (
    program_id UUID NOT NULL REFERENCES workout_programs(id),
    position_no INTEGER NOT NULL,
    exercise_id VARCHAR(60) NOT NULL REFERENCES exercise_catalog(id),
    sets_count INTEGER NOT NULL CHECK (sets_count BETWEEN 1 AND 6),
    planned_reps INTEGER NOT NULL CHECK (planned_reps BETWEEN 1 AND 100),
    weight_kg DECIMAL(8,2) NOT NULL CHECK (weight_kg BETWEEN 0 AND 500),
    PRIMARY KEY (program_id, position_no),
    UNIQUE (program_id, exercise_id)
);

INSERT INTO workout_programs (id, name, description, is_template) VALUES
('a1111111-1111-4111-8111-111111111111', 'Всё тело · Тренировка A', 'Базовая программа на всё тело. Рабочие веса подбираются индивидуально.', TRUE),
('a2222222-2222-4222-8222-222222222222', 'Всё тело · Тренировка B', 'Вторая тренировка на всё тело с другим набором упражнений.', TRUE);

INSERT INTO workout_program_exercises (program_id, position_no, exercise_id, sets_count, planned_reps, weight_kg) VALUES
('a1111111-1111-4111-8111-111111111111', 1, 'goblet-squat', 3, 12, 0),
('a1111111-1111-4111-8111-111111111111', 2, 'dumbbell-bench', 3, 10, 0),
('a1111111-1111-4111-8111-111111111111', 3, 'lat-pulldown', 3, 12, 0),
('a1111111-1111-4111-8111-111111111111', 4, 'romanian-deadlift', 3, 10, 0),
('a1111111-1111-4111-8111-111111111111', 5, 'lateral-raise', 3, 12, 0),
('a2222222-2222-4222-8222-222222222222', 1, 'leg-press', 3, 12, 0),
('a2222222-2222-4222-8222-222222222222', 2, 'seated-row', 3, 12, 0),
('a2222222-2222-4222-8222-222222222222', 3, 'dumbbell-shoulder-press', 3, 10, 0),
('a2222222-2222-4222-8222-222222222222', 4, 'bodyweight-lunge', 3, 10, 0),
('a2222222-2222-4222-8222-222222222222', 5, 'crunch', 3, 15, 0);

CREATE TABLE workout_member_locks (
    member_id UUID PRIMARY KEY
);

CREATE TABLE workout_sessions (
    id UUID PRIMARY KEY,
    member_id UUID NOT NULL,
    program_id UUID NOT NULL REFERENCES workout_programs(id),
    program_name VARCHAR(100) NOT NULL,
    status VARCHAR(20) NOT NULL CHECK (status IN ('ACTIVE', 'COMPLETED')),
    started_at TIMESTAMP WITH TIME ZONE NOT NULL,
    finished_at TIMESTAMP WITH TIME ZONE,
    duration_minutes INTEGER,
    weight_kg DECIMAL(6,2),
    estimated_calories INTEGER
);
CREATE INDEX idx_workout_sessions_member ON workout_sessions(member_id, status, started_at);

CREATE TABLE workout_session_exercises (
    id UUID PRIMARY KEY,
    session_id UUID NOT NULL REFERENCES workout_sessions(id),
    position_no INTEGER NOT NULL,
    exercise_id VARCHAR(60) NOT NULL,
    name VARCHAR(160) NOT NULL,
    muscle_group VARCHAR(100) NOT NULL,
    previous_weight_kg DECIMAL(8,2),
    previous_reps INTEGER,
    previous_completed_at TIMESTAMP WITH TIME ZONE,
    UNIQUE (session_id, position_no)
);

CREATE TABLE workout_sets (
    id UUID PRIMARY KEY,
    session_exercise_id UUID NOT NULL REFERENCES workout_session_exercises(id),
    set_number INTEGER NOT NULL,
    weight_kg DECIMAL(8,2) NOT NULL CHECK (weight_kg BETWEEN 0 AND 500),
    reps INTEGER NOT NULL CHECK (reps BETWEEN 1 AND 100),
    completed BOOLEAN NOT NULL DEFAULT FALSE,
    UNIQUE (session_exercise_id, set_number)
);

