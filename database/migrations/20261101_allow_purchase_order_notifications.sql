BEGIN;

ALTER TABLE public.notifications
    DROP CONSTRAINT notifications_target_type_check;

ALTER TABLE public.notifications
    ADD CONSTRAINT notifications_target_type_check
    CHECK (target_type IN (
        'STOCK',
        'WAREHOUSE',
        'PRODUCT',
        'PURCHASE_ORDER'
    ));

ALTER TABLE public.notifications
    DROP CONSTRAINT notifications_type_check;

ALTER TABLE public.notifications
    ADD CONSTRAINT notifications_type_check
    CHECK (type IN (
        'LOW_STOCK',
        'OUT_OF_STOCK',
        'OVERSTOCK',
        'WAREHOUSE_NEAR_CAPACITY',
        'WAREHOUSE_CAPACITY_FULL',
        'WAREHOUSE_CAPACITY_EXCEEDED',
        'FORECAST_ANOMALY',
        'PURCHASE_ORDER_APPROVED',
        'PURCHASE_ORDER_PARTIALLY_RECEIVED',
        'PURCHASE_ORDER_RECEIVED'
    ));

COMMIT;
