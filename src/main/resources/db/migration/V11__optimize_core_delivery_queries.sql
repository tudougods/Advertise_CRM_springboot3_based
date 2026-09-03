CREATE INDEX idx_delivery_advertiser_type_date_id
    ON advertising_delivery_records (
        advertiser_id,
        advertising_type_id,
        record_date DESC,
        id DESC
    );
