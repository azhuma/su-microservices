CREATE TABLE IF NOT EXISTS exchange_rate (
    currency_code VARCHAR(3) PRIMARY KEY,
    numeric_code VARCHAR(3) NOT NULL,
    kzt_per_unit DECIMAL(38, 18) NOT NULL CHECK (kzt_per_unit > 0),
    rate_date DATE NOT NULL,
    fetched_on DATE NOT NULL
);
