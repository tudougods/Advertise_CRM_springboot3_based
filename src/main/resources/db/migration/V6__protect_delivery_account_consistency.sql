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
    WHERE id = NEW.advertiser_account_id;

    SELECT advertiser_id
    INTO delivery_advertiser_id
    FROM advertising_delivery_records
    WHERE id = NEW.advertising_delivery_record_id;

    IF account_advertiser_id IS NOT NULL
            AND delivery_advertiser_id IS NOT NULL
            AND account_advertiser_id IS DISTINCT FROM delivery_advertiser_id THEN
        RAISE EXCEPTION 'account and delivery record must belong to the same advertiser'
            USING ERRCODE = '23514';
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_account_transaction_delivery_advertiser
BEFORE INSERT OR UPDATE OF advertiser_account_id, advertising_delivery_record_id
ON advertiser_account_transactions
FOR EACH ROW
EXECUTE FUNCTION validate_account_transaction_delivery_advertiser();

CREATE OR REPLACE FUNCTION prevent_referenced_delivery_advertiser_change()
RETURNS TRIGGER AS $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM advertiser_account_transactions
        WHERE advertising_delivery_record_id = OLD.id
    ) THEN
        RAISE EXCEPTION 'referenced delivery record cannot change advertiser'
            USING ERRCODE = '23514';
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_referenced_delivery_advertiser_change
BEFORE UPDATE OF advertiser_id
ON advertising_delivery_records
FOR EACH ROW
WHEN (OLD.advertiser_id IS DISTINCT FROM NEW.advertiser_id)
EXECUTE FUNCTION prevent_referenced_delivery_advertiser_change();
