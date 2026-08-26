ALTER TABLE advertiser_account_transactions
    ADD CONSTRAINT ck_account_transactions_business_reference_type
        CHECK ((advertising_delivery_record_id IS NULL
                OR transaction_type = 'CONSUMPTION')
            AND (recharge_order_id IS NULL
                OR transaction_type = 'RECHARGE'));
