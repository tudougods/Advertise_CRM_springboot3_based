\set ON_ERROR_STOP on
\set fixture_prefix 'SPRINT3-PERF-B1'

-- This script writes a large fixture. It must only run against a disposable database.
DO $$
BEGIN
    IF current_database() <> 'advertiser_crm_perf' THEN
        RAISE EXCEPTION
            'Performance fixtures require the disposable advertiser_crm_perf database; current database is %',
            current_database();
    END IF;
END
$$;

BEGIN;

-- Make the baseline reproducible even after V11 has been applied. ROLLBACK restores it.
DROP INDEX IF EXISTS idx_delivery_advertiser_type_date_id;

INSERT INTO advertisers (name, status, created_at, updated_at)
SELECT :'fixture_prefix' || '-ADVERTISER-' || series_no,
       'ACTIVE',
       CURRENT_TIMESTAMP,
       CURRENT_TIMESTAMP
FROM GENERATE_SERIES(1, 10) AS series_no;

SELECT MIN(id) AS target_advertiser_id
FROM advertisers
WHERE name LIKE :'fixture_prefix' || '-ADVERTISER-%'
\gset

INSERT INTO advertising_delivery_records (
    external_record_no,
    advertiser_id,
    advertising_type_id,
    record_date,
    impressions,
    clicks,
    conversions,
    spend,
    created_at,
    updated_at
)
SELECT :'fixture_prefix' || '-' || advertiser.id || '-' || series_no,
       advertiser.id,
       advertising_type.id,
       DATE '2025-01-01' + ((series_no - 1) % 730)::INTEGER,
       1000 + series_no,
       series_no % 200,
       series_no % 20,
       ((series_no % 1000) + 1)::NUMERIC(19, 2),
       CURRENT_TIMESTAMP,
       CURRENT_TIMESTAMP
FROM advertisers advertiser
CROSS JOIN GENERATE_SERIES(1, 50000) AS series_no
JOIN advertising_types advertising_type
  ON advertising_type.code = CASE series_no % 4
      WHEN 0 THEN 'SEARCH'
      WHEN 1 THEN 'DISPLAY'
      WHEN 2 THEN 'VIDEO'
      ELSE 'SOCIAL'
  END
WHERE advertiser.name LIKE :'fixture_prefix' || '-ADVERTISER-%';

ANALYZE advertising_delivery_records;

\echo '=== BEFORE: delivery page with advertiser + type + date ==='
EXPLAIN (ANALYZE, BUFFERS, FORMAT TEXT)
SELECT record.id,
       record.external_record_no,
       record.record_date,
       record.spend
FROM advertising_delivery_records record
WHERE record.advertiser_id = :target_advertiser_id
  AND record.advertising_type_id = (
      SELECT id FROM advertising_types WHERE code = 'SEARCH'
  )
  AND record.record_date BETWEEN DATE '2025-01-01' AND DATE '2026-12-31'
ORDER BY record.record_date DESC, record.id DESC
LIMIT 20;

\echo '=== BEFORE: trend aggregate with unused advertising type join ==='
EXPLAIN (ANALYZE, BUFFERS, FORMAT TEXT)
SELECT DATE_TRUNC('day', record.record_date::TIMESTAMP)::DATE AS period_start,
       SUM(record.impressions)::BIGINT AS impressions,
       ROUND(SUM(record.spend), 2) AS spend
FROM advertising_delivery_records record
JOIN advertising_types advertising_type
  ON advertising_type.id = record.advertising_type_id
WHERE record.record_date BETWEEN DATE '2026-07-01' AND DATE '2026-07-31'
GROUP BY 1
ORDER BY 1;

CREATE INDEX idx_delivery_advertiser_type_date_id
    ON advertising_delivery_records (
        advertiser_id,
        advertising_type_id,
        record_date DESC,
        id DESC
    );
ANALYZE advertising_delivery_records;

\echo '=== AFTER: delivery page with composite filter/order index ==='
EXPLAIN (ANALYZE, BUFFERS, FORMAT TEXT)
SELECT record.id,
       record.external_record_no,
       record.record_date,
       record.spend
FROM advertising_delivery_records record
WHERE record.advertiser_id = :target_advertiser_id
  AND record.advertising_type_id = (
      SELECT id FROM advertising_types WHERE code = 'SEARCH'
  )
  AND record.record_date BETWEEN DATE '2025-01-01' AND DATE '2026-12-31'
ORDER BY record.record_date DESC, record.id DESC
LIMIT 20;

\echo '=== AFTER: trend aggregate without unused advertising type join ==='
EXPLAIN (ANALYZE, BUFFERS, FORMAT TEXT)
SELECT DATE_TRUNC('day', record.record_date::TIMESTAMP)::DATE AS period_start,
       SUM(record.impressions)::BIGINT AS impressions,
       ROUND(SUM(record.spend), 2) AS spend
FROM advertising_delivery_records record
WHERE record.record_date BETWEEN DATE '2026-07-01' AND DATE '2026-07-31'
GROUP BY 1
ORDER BY 1;

ROLLBACK;
ANALYZE advertising_delivery_records;
