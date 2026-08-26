ALTER TABLE recharge_orders
    ADD CONSTRAINT uk_recharge_orders_id_account
        UNIQUE (id, advertiser_account_id);

ALTER TABLE advertiser_account_transactions
    ADD CONSTRAINT fk_account_transactions_recharge_order_account
        FOREIGN KEY (recharge_order_id, advertiser_account_id)
        REFERENCES recharge_orders (id, advertiser_account_id)
        ON DELETE RESTRICT;
