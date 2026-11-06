CREATE TABLE service_identity (
    id INTEGER PRIMARY KEY CHECK (id = 1),
    service_code VARCHAR(80) NOT NULL,
    schema_version INTEGER NOT NULL
);
INSERT INTO service_identity VALUES (1, 'nutrition-service', 1);

CREATE TABLE foods (
    id VARCHAR(40) PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    kcal NUMERIC(7,2) NOT NULL CHECK (kcal >= 0),
    protein NUMERIC(6,2) NOT NULL CHECK (protein >= 0),
    fat NUMERIC(6,2) NOT NULL CHECK (fat >= 0),
    carbs NUMERIC(6,2) NOT NULL CHECK (carbs >= 0)
);

-- Approximate reference values per 100 g for the specified preparation.
INSERT INTO foods VALUES
    ('apple', 'Яблоко с кожурой, сырое', 52, 0.26, 0.17, 13.81),
    ('banana', 'Банан, сырой', 89, 1.09, 0.33, 22.84),
    ('orange', 'Апельсин, сырой', 47, 0.94, 0.12, 11.75),
    ('blueberry', 'Голубика, сырая', 57, 0.74, 0.33, 14.49),
    ('cucumber', 'Огурец с кожурой, сырой', 15, 0.65, 0.11, 3.63),
    ('tomato', 'Томат, сырой', 18, 0.88, 0.20, 3.89),
    ('carrot', 'Морковь, сырая', 41, 0.93, 0.24, 9.58),
    ('broccoli', 'Брокколи, варёная', 35, 2.38, 0.41, 7.18),
    ('potato', 'Картофель без кожуры, варёный', 87, 1.87, 0.10, 20.13),
    ('oats', 'Овсяные хлопья, сухие', 379, 13.15, 6.52, 67.70),
    ('rice-dry', 'Рис белый длиннозёрный, сухой', 365, 7.13, 0.66, 80.00),
    ('rice-cooked', 'Рис белый длиннозёрный, варёный', 130, 2.69, 0.28, 28.17),
    ('buckwheat', 'Гречка, варёная', 92, 3.38, 0.62, 19.94),
    ('pasta', 'Спагетти, варёные', 158, 5.80, 0.93, 30.86),
    ('lentils', 'Чечевица, варёная', 116, 9.02, 0.38, 20.13),
    ('chickpeas', 'Нут, варёный', 164, 8.86, 2.59, 27.42),
    ('egg', 'Яйцо, варёное вкрутую', 155, 12.58, 10.61, 1.12),
    ('chicken', 'Куриная грудка без кожи, запечённая', 165, 31.02, 3.57, 0),
    ('salmon', 'Лосось атлантический, запечённый', 206, 22.10, 12.35, 0),
    ('milk', 'Молоко 2%', 50, 3.30, 1.98, 4.80),
    ('yogurt', 'Йогурт натуральный, из цельного молока', 61, 3.47, 3.25, 4.66),
    ('almonds', 'Миндаль', 579, 21.15, 49.93, 21.55),
    ('avocado', 'Авокадо, сырое', 160, 2.00, 14.66, 8.53),
    ('olive-oil', 'Оливковое масло', 884, 0, 100, 0);

CREATE TABLE nutrition_entries (
    id UUID PRIMARY KEY,
    member_id UUID NOT NULL,
    entry_date DATE NOT NULL,
    meal VARCHAR(16) NOT NULL CHECK (meal IN ('BREAKFAST','LUNCH','DINNER','SNACK')),
    food_id VARCHAR(40) NOT NULL REFERENCES foods(id),
    grams INTEGER NOT NULL CHECK (grams BETWEEN 1 AND 3000),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX nutrition_entries_by_day ON nutrition_entries(member_id, entry_date, created_at);
