\set ON_ERROR_STOP on
\set demo_prefix 'FINAL-EXPLAIN-F1'

BEGIN;

INSERT INTO advertisers (name, status, created_at, updated_at)
SELECT :'demo_prefix' || '-ADVERTISER-' || series_no,
       'ACTIVE',
       CURRENT_TIMESTAMP,
       CURRENT_TIMESTAMP
FROM GENERATE_SERIES(1, 5000) AS series_no;

SELECT MIN(id) AS target_advertiser_id
FROM advertisers
WHERE name LIKE :'demo_prefix' || '-ADVERTISER-%'
\gset

INSERT INTO advertiser_accounts (advertiser_id, balance, created_at, updated_at)
SELECT id, 1000000.00, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM advertisers
WHERE name LIKE :'demo_prefix' || '-ADVERTISER-%';

SELECT id AS target_account_id
FROM advertiser_accounts
WHERE advertiser_id = :target_advertiser_id
\gset

INSERT INTO advertiser_account_transactions (
    advertiser_account_id,
    business_no,
    transaction_type,
    amount,
    balance_after,
    remark,
    created_at
)
SELECT :target_account_id,
       :'demo_prefix' || '-TXN-' || series_no,
       CASE WHEN series_no % 2 = 0 THEN 'RECHARGE' ELSE 'CONSUMPTION' END,
       10.00,
       1000000.00,
       'F1 execution plan fixture',
       TIMESTAMPTZ '2026-01-01 00:00:00+00' + series_no * INTERVAL '1 minute'
FROM GENERATE_SERIES(1, 20000) AS series_no;

INSERT INTO recharge_orders (
    order_no,
    advertiser_account_id,
    amount,
    status,
    created_at,
    updated_at
)
SELECT :'demo_prefix' || '-ORDER-' || series_no,
       :target_account_id,
       100.00,
       'PENDING',
       TIMESTAMPTZ '2026-01-01 00:00:00+00' + series_no * INTERVAL '1 minute',
       TIMESTAMPTZ '2026-01-01 00:00:00+00' + series_no * INTERVAL '1 minute'
FROM GENERATE_SERIES(1, 20000) AS series_no;

INSERT INTO recharge_payment_callbacks (
    provider_event_id,
    recharge_order_id,
    callback_status,
    payload_hash,
    received_at
)
SELECT :'demo_prefix' || '-EVENT-' || row_number() OVER (ORDER BY recharge_order.id),
       recharge_order.id,
       'RECEIVED',
       REPEAT('a', 64),
       recharge_order.created_at
FROM recharge_orders recharge_order
WHERE recharge_order.order_no LIKE :'demo_prefix' || '-ORDER-%';

ANALYZE advertiser_accounts;
ANALYZE advertiser_account_transactions;
ANALYZE recharge_orders;
ANALYZE recharge_payment_callbacks;

SELECT id AS target_order_id
FROM recharge_orders
WHERE order_no = :'demo_prefix' || '-ORDER-10000'
\gset

\echo '=== ACCOUNT LOOKUP: advertiser id ==='
EXPLAIN (ANALYZE, BUFFERS, FORMAT TEXT)
SELECT *
FROM advertiser_accounts
WHERE advertiser_id = :target_advertiser_id
LIMIT 1;

\echo '=== TRANSACTION PAGE: account + time range ==='
EXPLAIN (ANALYZE, BUFFERS, FORMAT TEXT)
SELECT *
FROM advertiser_account_transactions
WHERE advertiser_account_id = :target_account_id
  AND created_at BETWEEN TIMESTAMPTZ '2026-01-10 00:00:00+00'
                     AND TIMESTAMPTZ '2026-01-12 23:59:59+00'
ORDER BY created_at DESC, id DESC
LIMIT 20;

\echo '=== TRANSACTION IDEMPOTENCY: business number ==='
EXPLAIN (ANALYZE, BUFFERS, FORMAT TEXT)
SELECT *
FROM advertiser_account_transactions
WHERE business_no = :'demo_prefix' || '-TXN-10000'
LIMIT 1;

\echo '=== RECHARGE ORDER LOOKUP: order number + row lock ==='
EXPLAIN (ANALYZE, BUFFERS, FORMAT TEXT)
SELECT *
FROM recharge_orders
WHERE order_no = :'demo_prefix' || '-ORDER-10000'
FOR UPDATE;

\echo '=== RECHARGE ORDER HISTORY: account + created time ==='
EXPLAIN (ANALYZE, BUFFERS, FORMAT TEXT)
SELECT *
FROM recharge_orders
WHERE advertiser_account_id = :target_account_id
ORDER BY created_at DESC
LIMIT 20;

\echo '=== CALLBACK IDEMPOTENCY: provider event id ==='
EXPLAIN (ANALYZE, BUFFERS, FORMAT TEXT)
SELECT *
FROM recharge_payment_callbacks
WHERE provider_event_id = :'demo_prefix' || '-EVENT-10000'
LIMIT 1;

\echo '=== CALLBACK AUDIT: order + received time ==='
EXPLAIN (ANALYZE, BUFFERS, FORMAT TEXT)
SELECT *
FROM recharge_payment_callbacks
WHERE recharge_order_id = :target_order_id
ORDER BY received_at DESC
LIMIT 20;

ROLLBACK;

ANALYZE advertiser_accounts;
ANALYZE advertiser_account_transactions;
ANALYZE recharge_orders;
ANALYZE recharge_payment_callbacks;
