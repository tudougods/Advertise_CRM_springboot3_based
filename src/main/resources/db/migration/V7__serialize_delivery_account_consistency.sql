CREATE OR REPLACE FUNCTION validate_account_transaction_delivery_advertiser()
RETURNS TRIGGER AS $$
DECLARE
    account_advertiser_id BIGINT;
    delivery_advertiser_id BIGINT;
BEGIN
    IF NEW.advertising_delivery_record_id IS NULL THEN
        RETURN NEW;
    END IF;

    SELECT advertiser_id
    INTO account_advertiser_id
    FROM advertiser_accounts
    WHERE id = NEW.advertiser_account_id
    FOR UPDATE;

    SELECT advertiser_id
    INTO delivery_advertiser_id
    FROM advertising_delivery_records
    WHERE id = NEW.advertising_delivery_record_id
    FOR UPDATE;

    IF account_advertiser_id IS NOT NULL
            AND delivery_advertiser_id IS NOT NULL
            AND account_advertiser_id IS DISTINCT FROM delivery_advertiser_id THEN
        RAISE EXCEPTION 'account and delivery record must belong to the same advertiser'
            USING ERRCODE = '23514';
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;
