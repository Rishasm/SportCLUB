-- Начальная проверяемая схема. Бизнес-таблицы добавляются новыми миграциями.
CREATE TABLE service_identity (
    id INTEGER PRIMARY KEY CHECK (id = 1),
    service_code VARCHAR(80) NOT NULL UNIQUE,
    schema_version INTEGER NOT NULL,
    initialized_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

INSERT INTO service_identity (id, service_code, schema_version)
VALUES (1, 'wellness-service', 1);
