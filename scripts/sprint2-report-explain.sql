\set ON_ERROR_STOP on
\set demo_prefix 'REPORT-EXPLAIN-C5'

-- Identity sequences are not transactional, so use a disposable acceptance database.
DO $$
BEGIN
    IF current_database() = 'advertiser_crm' THEN
        RAISE EXCEPTION 'Refusing to run performance fixtures in the shared advertiser_crm database';
    END IF;
END
$$;

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

\echo '=== DELIVERY PAGE: advertiser + date + advertising type ==='
EXPLAIN (ANALYZE, BUFFERS, FORMAT TEXT)
SELECT record.id,
       record.external_record_no,
       record.record_date,
       record.impressions,
       record.clicks,
       record.conversions,
       record.spend
FROM advertising_delivery_records record
WHERE record.record_date BETWEEN DATE '2026-07-01' AND DATE '2026-07-31'
  AND record.advertiser_id = :target_advertiser_id
  AND record.advertising_type_id = (
      SELECT id FROM advertising_types WHERE code = 'SEARCH'
  )
ORDER BY record.record_date DESC, record.id DESC
LIMIT 20;

\echo '=== OVERVIEW: advertiser + date + advertising type ==='
EXPLAIN (ANALYZE, BUFFERS, FORMAT TEXT)
SELECT COALESCE(SUM(record.impressions), 0)::BIGINT AS impressions,
       COALESCE(SUM(record.clicks), 0)::BIGINT AS clicks,
       COALESCE(SUM(record.conversions), 0)::BIGINT AS conversions,
       ROUND(COALESCE(SUM(record.spend), 0), 2) AS spend,
       COALESCE(ROUND(
           SUM(record.clicks)::NUMERIC / NULLIF(SUM(record.impressions), 0), 4
       ), 0.0000) AS ctr,
       COALESCE(ROUND(
           SUM(record.conversions)::NUMERIC / NULLIF(SUM(record.clicks), 0), 4
       ), 0.0000) AS cvr,
       COALESCE(ROUND(
           SUM(record.spend) / NULLIF(SUM(record.clicks), 0), 2
       ), 0.00) AS cpc
FROM advertising_delivery_records record
JOIN advertising_types advertising_type
  ON advertising_type.id = record.advertising_type_id
WHERE record.record_date BETWEEN DATE '2026-07-01' AND DATE '2026-07-31'
  AND record.advertiser_id = :target_advertiser_id
  AND UPPER(advertising_type.code) = 'SEARCH';

\echo '=== TREND: date range ==='
EXPLAIN (ANALYZE, BUFFERS, FORMAT TEXT)
SELECT DATE_TRUNC('day', record.record_date::TIMESTAMP)::DATE AS period_start,
       COALESCE(SUM(record.impressions), 0)::BIGINT AS impressions,
       COALESCE(SUM(record.clicks), 0)::BIGINT AS clicks,
       COALESCE(SUM(record.conversions), 0)::BIGINT AS conversions,
       ROUND(COALESCE(SUM(record.spend), 0), 2) AS spend,
       COALESCE(ROUND(
           SUM(record.clicks)::NUMERIC / NULLIF(SUM(record.impressions), 0), 4
       ), 0.0000) AS ctr,
       COALESCE(ROUND(
           SUM(record.conversions)::NUMERIC / NULLIF(SUM(record.clicks), 0), 4
       ), 0.0000) AS cvr,
       COALESCE(ROUND(
           SUM(record.spend) / NULLIF(SUM(record.clicks), 0), 2
       ), 0.00) AS cpc
FROM advertising_delivery_records record
JOIN advertising_types advertising_type
  ON advertising_type.id = record.advertising_type_id
WHERE record.record_date BETWEEN DATE '2026-07-01' AND DATE '2026-07-31'
GROUP BY 1
ORDER BY 1;

\echo '=== BY ADVERTISER COUNT: advertising type + date range ==='
EXPLAIN (ANALYZE, BUFFERS, FORMAT TEXT)
SELECT COUNT(DISTINCT record.advertiser_id)
FROM advertising_delivery_records record
JOIN advertising_types advertising_type
  ON advertising_type.id = record.advertising_type_id
WHERE record.record_date BETWEEN DATE '2026-07-01' AND DATE '2026-07-31'
  AND UPPER(advertising_type.code) = 'SEARCH';

\echo '=== BY ADVERTISER PAGE: advertising type + date range ==='
EXPLAIN (ANALYZE, BUFFERS, FORMAT TEXT)
SELECT advertiser.id AS advertiser_id,
       advertiser.name AS advertiser_name,
       COALESCE(SUM(record.impressions), 0)::BIGINT AS impressions,
       COALESCE(SUM(record.clicks), 0)::BIGINT AS clicks,
       COALESCE(SUM(record.conversions), 0)::BIGINT AS conversions,
       ROUND(COALESCE(SUM(record.spend), 0), 2) AS spend,
       COALESCE(ROUND(
           SUM(record.clicks)::NUMERIC / NULLIF(SUM(record.impressions), 0), 4
       ), 0.0000) AS ctr,
       COALESCE(ROUND(
           SUM(record.conversions)::NUMERIC / NULLIF(SUM(record.clicks), 0), 4
       ), 0.0000) AS cvr,
       COALESCE(ROUND(
           SUM(record.spend) / NULLIF(SUM(record.clicks), 0), 2
       ), 0.00) AS cpc
FROM advertising_delivery_records record
JOIN advertisers advertiser
  ON advertiser.id = record.advertiser_id
JOIN advertising_types advertising_type
  ON advertising_type.id = record.advertising_type_id
WHERE record.record_date BETWEEN DATE '2026-07-01' AND DATE '2026-07-31'
  AND UPPER(advertising_type.code) = 'SEARCH'
GROUP BY advertiser.id, advertiser.name
ORDER BY spend DESC, advertiser.id ASC
LIMIT 20
OFFSET 0;

\echo '=== BY ADVERTISING TYPE: advertiser + date range ==='
EXPLAIN (ANALYZE, BUFFERS, FORMAT TEXT)
SELECT advertising_type.id AS advertising_type_id,
       advertising_type.code AS advertising_type_code,
       advertising_type.name AS advertising_type_name,
       COALESCE(SUM(record.impressions), 0)::BIGINT AS impressions,
       COALESCE(SUM(record.clicks), 0)::BIGINT AS clicks,
       COALESCE(SUM(record.conversions), 0)::BIGINT AS conversions,
       ROUND(COALESCE(SUM(record.spend), 0), 2) AS spend,
       COALESCE(ROUND(
           SUM(record.clicks)::NUMERIC / NULLIF(SUM(record.impressions), 0), 4
       ), 0.0000) AS ctr,
       COALESCE(ROUND(
           SUM(record.conversions)::NUMERIC / NULLIF(SUM(record.clicks), 0), 4
       ), 0.0000) AS cvr,
       COALESCE(ROUND(
           SUM(record.spend) / NULLIF(SUM(record.clicks), 0), 2
       ), 0.00) AS cpc
FROM advertising_delivery_records record
JOIN advertising_types advertising_type
  ON advertising_type.id = record.advertising_type_id
WHERE record.record_date BETWEEN DATE '2026-07-01' AND DATE '2026-07-31'
  AND record.advertiser_id = :target_advertiser_id
GROUP BY advertising_type.id, advertising_type.code, advertising_type.name
ORDER BY spend DESC, advertising_type.code ASC;

ROLLBACK;

ANALYZE advertising_delivery_records;
