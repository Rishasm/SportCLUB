CREATE TABLE member_accounts (
    id UUID PRIMARY KEY,
    name VARCHAR(80) NOT NULL,
    email VARCHAR(254) NOT NULL UNIQUE,
    password_hash VARCHAR(100) NOT NULL,
    role VARCHAR(20) NOT NULL CHECK (role IN ('MEMBER', 'ADMIN')),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CHECK (email = LOWER(email))
);

CREATE TABLE membership_plans (
    id VARCHAR(40) PRIMARY KEY,
    code VARCHAR(40) NOT NULL UNIQUE,
    name VARCHAR(80) NOT NULL,
    description VARCHAR(300) NOT NULL,
    price_rub INTEGER NOT NULL CHECK (price_rub >= 0),
    duration_days INTEGER NOT NULL CHECK (duration_days > 0),
    features VARCHAR(600) NOT NULL
);

INSERT INTO membership_plans (id, code, name, description, price_rub, duration_days, features)
VALUES
    ('start', 'START', 'Старт', 'Для самостоятельных тренировок в своём темпе.', 2500, 30,
     'Тренажёрный зал|Кардиозона|Групповые занятия по записи'),
    ('rhythm', 'RHYTHM', 'Ритм', 'Регулярные тренировки и больше возможностей клуба.', 3900, 30,
     'Всё из тарифа «Старт»|Все часы работы клуба|Вводная встреча с тренером'),
    ('focus', 'FOCUS', 'Фокус', 'Больше внимания твоим целям и тренировочному плану.', 6500, 30,
     'Всё из тарифа «Ритм»|2 персональных занятия|Разбор программы тренировок');

CREATE TABLE member_memberships (
    id UUID PRIMARY KEY,
    member_id UUID NOT NULL REFERENCES member_accounts(id),
    plan_id VARCHAR(40) NOT NULL REFERENCES membership_plans(id),
    starts_at TIMESTAMP WITH TIME ZONE NOT NULL,
    valid_until TIMESTAMP WITH TIME ZONE NOT NULL,
    CHECK (valid_until > starts_at)
);

CREATE INDEX idx_memberships_member_validity ON member_memberships (member_id, valid_until);

