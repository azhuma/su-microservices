CREATE TABLE IF NOT EXISTS exchange_rate (
    currency_code VARCHAR(3) PRIMARY KEY,
    numeric_code VARCHAR(3) NOT NULL,
    kzt_per_unit DECIMAL(38, 18) NOT NULL,
    rate_date DATE NOT NULL,
    fetched_on DATE NOT NULL
);

-- Upgrade the legacy RUB cache. The temporary nullable columns also make this
-- script safe for a new database and for repeated application starts.
ALTER TABLE exchange_rate ADD COLUMN IF NOT EXISTS kzt_per_unit DECIMAL(38, 18);
ALTER TABLE exchange_rate ADD COLUMN IF NOT EXISTS rub_per_unit DECIMAL(38, 18);

UPDATE exchange_rate
SET kzt_per_unit = rub_per_unit / (
    SELECT rub_per_unit FROM exchange_rate WHERE currency_code = 'KZT'
)
WHERE kzt_per_unit IS NULL;

-- A legacy snapshot without KZT cannot be rebased; reload that cache on demand.
DELETE FROM exchange_rate WHERE kzt_per_unit IS NULL;

ALTER TABLE exchange_rate ALTER COLUMN kzt_per_unit SET NOT NULL;
ALTER TABLE exchange_rate ADD CONSTRAINT IF NOT EXISTS positive_kzt_per_unit CHECK (kzt_per_unit > 0);
ALTER TABLE exchange_rate DROP COLUMN IF EXISTS rub_per_unit;
