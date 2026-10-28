-- ============================================================================
-- Additive Workflow Migration Script: JewelryOrderDemo
-- Purpose: Safely update existing orders and order_items tables for idempotency,
--          retry metadata, and extended workflow state machines.
-- Target DBMS: Microsoft SQL Server
-- Re-runnable: Yes
-- Non-destructive: Does NOT drop tables or delete data.
-- ============================================================================

USE JewelryOrderDemo;
GO

SET NOCOUNT ON;
SET XACT_ABORT ON;
GO

-- Step 1: Add missing columns if they do not exist
IF NOT EXISTS (
    SELECT 1 
    FROM sys.columns 
    WHERE object_id = OBJECT_ID('dbo.orders') 
      AND name = 'idempotency_key'
)
BEGIN
    ALTER TABLE dbo.orders 
        ADD idempotency_key VARCHAR(100) NULL;
    PRINT 'Added column idempotency_key to dbo.orders.';
END
ELSE
BEGIN
    PRINT 'Column idempotency_key already exists in dbo.orders.';
END
GO

IF NOT EXISTS (
    SELECT 1 
    FROM sys.columns 
    WHERE object_id = OBJECT_ID('dbo.order_items') 
      AND name = 'retry_count'
)
BEGIN
    ALTER TABLE dbo.order_items 
        ADD retry_count INT NOT NULL 
            CONSTRAINT DF_order_items_retry_count DEFAULT 0;
    PRINT 'Added column retry_count to dbo.order_items.';
END
ELSE
BEGIN
    PRINT 'Column retry_count already exists in dbo.order_items.';
END
GO

IF NOT EXISTS (
    SELECT 1 
    FROM sys.columns 
    WHERE object_id = OBJECT_ID('dbo.order_items') 
      AND name = 'next_retry_at'
)
BEGIN
    ALTER TABLE dbo.order_items 
        ADD next_retry_at DATETIME2(3) NULL;
    PRINT 'Added column next_retry_at to dbo.order_items.';
END
ELSE
BEGIN
    PRINT 'Column next_retry_at already exists in dbo.order_items.';
END
GO

IF NOT EXISTS (
    SELECT 1 
    FROM sys.columns 
    WHERE object_id = OBJECT_ID('dbo.order_items') 
      AND name = 'last_error_code'
)
BEGIN
    ALTER TABLE dbo.order_items 
        ADD last_error_code VARCHAR(50) NULL;
    PRINT 'Added column last_error_code to dbo.order_items.';
END
ELSE
BEGIN
    PRINT 'Column last_error_code already exists in dbo.order_items.';
END
GO

-- Step 2: Create indexes and constraints (columns now exist in catalog)
BEGIN TRANSACTION;
BEGIN TRY
    -- 2.1 dbo.orders: Filtered unique index on (user_id, idempotency_key)
    IF NOT EXISTS (
        SELECT 1 
        FROM sys.indexes 
        WHERE object_id = OBJECT_ID('dbo.orders') 
          AND name = 'UX_orders_user_idempotency_key'
    )
    BEGIN
        CREATE UNIQUE NONCLUSTERED INDEX UX_orders_user_idempotency_key
            ON dbo.orders (user_id, idempotency_key)
            WHERE idempotency_key IS NOT NULL;
        PRINT 'Created index UX_orders_user_idempotency_key on dbo.orders.';
    END

    -- 2.2 dbo.orders: Index on (status, created_at)
    IF NOT EXISTS (
        SELECT 1 
        FROM sys.indexes 
        WHERE object_id = OBJECT_ID('dbo.orders') 
          AND name = 'IX_orders_status_created_at'
    )
    BEGIN
        CREATE NONCLUSTERED INDEX IX_orders_status_created_at
            ON dbo.orders (status, created_at);
        PRINT 'Created index IX_orders_status_created_at on dbo.orders.';
    END

    -- 2.3 dbo.orders: Update CK_orders_status to include workflow states
    IF EXISTS (
        SELECT 1 
        FROM sys.check_constraints 
        WHERE parent_object_id = OBJECT_ID('dbo.orders') 
          AND name = 'CK_orders_status'
    )
    BEGIN
        ALTER TABLE dbo.orders DROP CONSTRAINT CK_orders_status;
        PRINT 'Dropped existing CK_orders_status constraint.';
    END

    ALTER TABLE dbo.orders WITH CHECK 
        ADD CONSTRAINT CK_orders_status CHECK (
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
        );
    PRINT 'Created updated CK_orders_status constraint on dbo.orders.';

    -- 2.4 dbo.order_items: Add CK_order_items_retry_count
    IF NOT EXISTS (
        SELECT 1 
        FROM sys.check_constraints 
        WHERE parent_object_id = OBJECT_ID('dbo.order_items') 
          AND name = 'CK_order_items_retry_count'
    )
    BEGIN
        ALTER TABLE dbo.order_items WITH CHECK 
            ADD CONSTRAINT CK_order_items_retry_count CHECK (retry_count >= 0);
        PRINT 'Created CK_order_items_retry_count constraint on dbo.order_items.';
    END

    -- 2.5 dbo.order_items: Update CK_order_items_status to include workflow states
    IF EXISTS (
        SELECT 1 
        FROM sys.check_constraints 
        WHERE parent_object_id = OBJECT_ID('dbo.order_items') 
          AND name = 'CK_order_items_status'
    )
    BEGIN
        ALTER TABLE dbo.order_items DROP CONSTRAINT CK_order_items_status;
        PRINT 'Dropped existing CK_order_items_status constraint.';
    END

    ALTER TABLE dbo.order_items WITH CHECK 
        ADD CONSTRAINT CK_order_items_status CHECK (
            status IN (
                'PENDING_RESERVATION',
                'RESERVED',
                'COMMITTED',
                'RELEASED',
                'RESERVATION_FAILED',
                'EXPIRED',
                'RECONCILIATION_REQUIRED'
            )
        );
    PRINT 'Created updated CK_order_items_status constraint on dbo.order_items.';

    -- 2.6 dbo.order_items: Index for recovery queries
    IF NOT EXISTS (
        SELECT 1 
        FROM sys.indexes 
        WHERE object_id = OBJECT_ID('dbo.order_items') 
          AND name = 'IX_order_items_status_next_retry'
    )
    BEGIN
        CREATE NONCLUSTERED INDEX IX_order_items_status_next_retry
            ON dbo.order_items (status, next_retry_at);
        PRINT 'Created index IX_order_items_status_next_retry on dbo.order_items.';
    END

    COMMIT TRANSACTION;
    PRINT 'Step 2 completed successfully.';
END TRY
BEGIN CATCH
    IF @@TRANCOUNT > 0
        ROLLBACK TRANSACTION;
    PRINT 'Step 2 failed. Transaction rolled back.';
    THROW;
END CATCH;
GO

PRINT '=== VERIFICATION SUMMARY ===';
GO
