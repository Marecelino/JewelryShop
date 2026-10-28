-- ============================================================================
-- Baseline Database Setup Script: JewelryOrderDemo
-- Target: Fresh / Clean Setup of Order Service Database
-- DBMS: Microsoft SQL Server
-- ============================================================================

IF NOT EXISTS (SELECT 1 FROM sys.databases WHERE name = 'JewelryOrderDemo')
BEGIN
    CREATE DATABASE JewelryOrderDemo;
END
GO

USE JewelryOrderDemo;
GO

-- 1. Table: dbo.orders
IF NOT EXISTS (SELECT 1 FROM sys.tables WHERE name = 'orders' AND schema_id = SCHEMA_ID('dbo'))
BEGIN
    CREATE TABLE dbo.orders (
        order_id BIGINT IDENTITY(1,1) NOT NULL,
        order_reference VARCHAR(100) NOT NULL,
        user_id BIGINT NOT NULL,
        status VARCHAR(30) NOT NULL CONSTRAINT DF_orders_status DEFAULT ('PENDING_RESERVATION'),
        total_amount DECIMAL(18,2) NOT NULL,
        idempotency_key VARCHAR(100) NULL,
        created_at DATETIME2(3) NOT NULL CONSTRAINT DF_orders_created_at DEFAULT (SYSUTCDATETIME()),
        updated_at DATETIME2(3) NOT NULL CONSTRAINT DF_orders_updated_at DEFAULT (SYSUTCDATETIME()),

        CONSTRAINT PK_orders PRIMARY KEY CLUSTERED (order_id),
        CONSTRAINT UQ_orders_order_reference UNIQUE NONCLUSTERED (order_reference),
        CONSTRAINT CK_orders_user_id CHECK (user_id > 0),
        CONSTRAINT CK_orders_total_amount CHECK (total_amount >= 0),
        CONSTRAINT CK_orders_status CHECK (
            status IN (
                'PENDING_RESERVATION',
                'AWAITING_PAYMENT',
                'CONFIRMED',
                'CANCELLED',
                'RESERVATION_FAILED',
                'CANCELLATION_PENDING',
                'RESERVATION_EXPIRED',
                'RECONCILIATION_REQUIRED'
            )
        )
    );

    CREATE UNIQUE NONCLUSTERED INDEX UX_orders_user_idempotency_key
        ON dbo.orders (user_id, idempotency_key)
        WHERE idempotency_key IS NOT NULL;

    CREATE NONCLUSTERED INDEX IX_orders_user_created_at
        ON dbo.orders (user_id, created_at DESC);

    CREATE NONCLUSTERED INDEX IX_orders_status_created_at
        ON dbo.orders (status, created_at);
END
GO

-- 2. Table: dbo.order_items
IF NOT EXISTS (SELECT 1 FROM sys.tables WHERE name = 'order_items' AND schema_id = SCHEMA_ID('dbo'))
BEGIN
    CREATE TABLE dbo.order_items (
        order_item_id BIGINT IDENTITY(1,1) NOT NULL,
        order_id BIGINT NOT NULL,
        order_item_reference VARCHAR(100) NOT NULL,
        product_id INT NOT NULL,
        product_name_snapshot NVARCHAR(100) NOT NULL,
        unit_price DECIMAL(18,2) NOT NULL,
        reservation_id BIGINT NULL,
        inventory_item_id BIGINT NULL,
        status VARCHAR(30) NOT NULL CONSTRAINT DF_order_items_status DEFAULT ('PENDING_RESERVATION'),
        retry_count INT NOT NULL CONSTRAINT DF_order_items_retry_count DEFAULT 0,
        next_retry_at DATETIME2(3) NULL,
        last_error_code VARCHAR(50) NULL,
        created_at DATETIME2(3) NOT NULL CONSTRAINT DF_order_items_created_at DEFAULT (SYSUTCDATETIME()),
        updated_at DATETIME2(3) NOT NULL CONSTRAINT DF_order_items_updated_at DEFAULT (SYSUTCDATETIME()),

        CONSTRAINT PK_order_items PRIMARY KEY CLUSTERED (order_item_id),
        CONSTRAINT FK_order_items_orders FOREIGN KEY (order_id) REFERENCES dbo.orders (order_id),
        CONSTRAINT UQ_order_items_order_line UNIQUE NONCLUSTERED (order_id, order_item_reference),
        CONSTRAINT CK_order_items_product_id CHECK (product_id > 0),
        CONSTRAINT CK_order_items_unit_price CHECK (unit_price >= 0),
        CONSTRAINT CK_order_items_retry_count CHECK (retry_count >= 0),
        CONSTRAINT CK_order_items_reservation_ids CHECK (
            (reservation_id IS NULL AND inventory_item_id IS NULL)
            OR
            (reservation_id IS NOT NULL AND inventory_item_id IS NOT NULL)
        ),
        CONSTRAINT CK_order_items_status CHECK (
            status IN (
                'PENDING_RESERVATION',
                'RESERVED',
                'COMMITTED',
                'RELEASED',
                'RESERVATION_FAILED',
                'EXPIRED',
                'RECONCILIATION_REQUIRED'
            )
        )
    );

    CREATE UNIQUE NONCLUSTERED INDEX UX_order_items_reservation_id
        ON dbo.order_items (reservation_id)
        WHERE reservation_id IS NOT NULL;

    CREATE NONCLUSTERED INDEX IX_order_items_order_id
        ON dbo.order_items (order_id);

    CREATE NONCLUSTERED INDEX IX_order_items_status_next_retry
        ON dbo.order_items (status, next_retry_at);
END
GO
