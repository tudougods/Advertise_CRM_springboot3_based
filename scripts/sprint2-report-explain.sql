\set ON_ERROR_STOP on
\set demo_prefix 'REPORT-EXPLAIN-C5'

BEGIN;

INSERT INTO advertisers (name, status, created_at, updated_at)
SELECT :'demo_prefix' || '-' || series_no,
       'ACTIVE',
       CURRENT_TIMESTAMP,
       CURRENT_TIMESTAMP
FROM GENERATE_SERIES(1, 20) AS series_no;

SELECT MIN(id) AS target_advertiser_id
FROM advertisers
WHERE name LIKE :'demo_prefix' || '-%'
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
SELECT 'REPORT-EXPLAIN-' || advertiser.id || '-' || series_no,
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
CROSS JOIN GENERATE_SERIES(1, 3000) AS series_no
JOIN advertising_types advertising_type
  ON advertising_type.code = CASE series_no % 4
      WHEN 0 THEN 'SEARCH'
      WHEN 1 THEN 'DISPLAY'
      WHEN 2 THEN 'VIDEO'
      ELSE 'SOCIAL'
  END
WHERE advertiser.name LIKE :'demo_prefix' || '-%';

ANALYZE advertising_delivery_records;

\echo '=== OVERVIEW: advertiser + date + advertising type ==='
EXPLAIN (ANALYZE, BUFFERS, FORMAT TEXT)
SELECT SUM(record.impressions),
       SUM(record.clicks),
       SUM(record.conversions),
       SUM(record.spend)
FROM advertising_delivery_records record
JOIN advertising_types advertising_type
  ON advertising_type.id = record.advertising_type_id
WHERE record.record_date BETWEEN DATE '2026-07-01' AND DATE '2026-07-31'
  AND record.advertiser_id = :target_advertiser_id
  AND UPPER(advertising_type.code) = 'SEARCH';

\echo '=== TREND: date range ==='
EXPLAIN (ANALYZE, BUFFERS, FORMAT TEXT)
SELECT DATE_TRUNC('day', record.record_date::TIMESTAMP)::DATE AS period_start,
       SUM(record.impressions),
       SUM(record.clicks),
       SUM(record.conversions),
       SUM(record.spend)
FROM advertising_delivery_records record
WHERE record.record_date BETWEEN DATE '2026-07-01' AND DATE '2026-07-31'
GROUP BY 1
ORDER BY 1;

\echo '=== BY ADVERTISER: advertising type + date range ==='
EXPLAIN (ANALYZE, BUFFERS, FORMAT TEXT)
SELECT record.advertiser_id,
       SUM(record.impressions),
       SUM(record.clicks),
       SUM(record.conversions),
       SUM(record.spend)
FROM advertising_delivery_records record
JOIN advertising_types advertising_type
  ON advertising_type.id = record.advertising_type_id
WHERE record.record_date BETWEEN DATE '2026-07-01' AND DATE '2026-07-31'
  AND UPPER(advertising_type.code) = 'SEARCH'
GROUP BY record.advertiser_id
ORDER BY SUM(record.spend) DESC, record.advertiser_id ASC;

ROLLBACK;

ANALYZE advertising_delivery_records;
