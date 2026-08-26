ALTER TABLE recharge_orders
    ADD CONSTRAINT ck_recharge_orders_terminal_fields
        CHECK ((status = 'SUCCESS'
                AND provider_transaction_no IS NOT NULL
                AND paid_at IS NOT NULL)
            OR (status <> 'SUCCESS'
                AND provider_transaction_no IS NULL
                AND paid_at IS NULL));
