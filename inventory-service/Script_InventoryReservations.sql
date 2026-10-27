USE JewelryInventoryDemo;
GO

/* =========================================================================
   MIGRATION SCRIPT: Script_InventoryReservations.sql
   Target Service: inventory-service
   Target Database: JewelryInventoryDemo
   Description:
     1. Creates table dbo.inventory_reservations (if not exists)
        or verifies schema compatibility if already exists.
     2. Enforces PK, FK to dbo.inventory_items, Unique order-item constraint,
        Status/TTL/Timestamps/Completed-at check constraints.
     3. Creates Filtered Unique Index ensuring 1 physical item cannot have
        more than 1 ACTIVE reservation.
     4. Creates Filtered Index for expiration scanning and order lookup index.
     5. Preserves all existing dbo.inventory_items records and constraints.
   Safety: Safe to review and rerun (Idempotent). Uses explicit transaction.
   ========================================================================= */

SET ANSI_NULLS ON;
SET QUOTED_IDENTIFIER ON;
SET ANSI_PADDING ON;
SET ANSI_WARNINGS ON;
SET ARITHABORT ON;
SET CONCAT_NULL_YIELDS_NULL ON;
SET NUMERIC_ROUNDABORT OFF;
SET XACT_ABORT ON;
GO

BEGIN TRANSACTION;

BEGIN TRY
    -- ---------------------------------------------------------------------
    -- STEP 1: CAPTURE DYNAMIC BASELINE COUNT OF EXISTING INVENTORY ITEMS
    -- ---------------------------------------------------------------------
    DECLARE @pre_count INT;
    SELECT @pre_count = COUNT(*) FROM dbo.inventory_items;

    -- ---------------------------------------------------------------------
    -- STEP 2: SCHEMA COMPATIBILITY CHECK IF TABLE ALREADY EXISTS
    -- ---------------------------------------------------------------------
    IF OBJECT_ID('dbo.inventory_reservations', 'U') IS NOT NULL
    BEGIN
        -- Verify that all required columns exist with compatible definitions
        IF NOT EXISTS (
            SELECT 1 FROM sys.columns c
            JOIN sys.types t ON c.user_type_id = t.user_type_id
            WHERE c.object_id = OBJECT_ID('dbo.inventory_reservations')
              AND c.name = 'reservation_id' AND t.name = 'bigint'
        ) OR NOT EXISTS (
            SELECT 1 FROM sys.columns c
            JOIN sys.types t ON c.user_type_id = t.user_type_id
            WHERE c.object_id = OBJECT_ID('dbo.inventory_reservations')
              AND c.name = 'order_reference' AND t.name = 'varchar' AND c.max_length = 100
        ) OR NOT EXISTS (
            SELECT 1 FROM sys.columns c
            JOIN sys.types t ON c.user_type_id = t.user_type_id
            WHERE c.object_id = OBJECT_ID('dbo.inventory_reservations')
              AND c.name = 'order_item_reference' AND t.name = 'varchar' AND c.max_length = 100
        ) OR NOT EXISTS (
            SELECT 1 FROM sys.columns c
            JOIN sys.types t ON c.user_type_id = t.user_type_id
            WHERE c.object_id = OBJECT_ID('dbo.inventory_reservations')
              AND c.name = 'product_id' AND t.name = 'int'
        ) OR NOT EXISTS (
            SELECT 1 FROM sys.columns c
            JOIN sys.types t ON c.user_type_id = t.user_type_id
            WHERE c.object_id = OBJECT_ID('dbo.inventory_reservations')
              AND c.name = 'inventory_item_id' AND t.name = 'bigint'
        ) OR NOT EXISTS (
            SELECT 1 FROM sys.columns c
            JOIN sys.types t ON c.user_type_id = t.user_type_id
            WHERE c.object_id = OBJECT_ID('dbo.inventory_reservations')
              AND c.name = 'status' AND t.name = 'varchar' AND c.max_length = 20
        ) OR NOT EXISTS (
            SELECT 1 FROM sys.columns c
            JOIN sys.types t ON c.user_type_id = t.user_type_id
            WHERE c.object_id = OBJECT_ID('dbo.inventory_reservations')
              AND c.name = 'ttl_minutes' AND t.name = 'int'
        ) OR NOT EXISTS (
            SELECT 1 FROM sys.columns c
            JOIN sys.types t ON c.user_type_id = t.user_type_id
            WHERE c.object_id = OBJECT_ID('dbo.inventory_reservations')
              AND c.name = 'reserved_at' AND t.name = 'datetime2' AND c.scale = 3
        ) OR NOT EXISTS (
            SELECT 1 FROM sys.columns c
            JOIN sys.types t ON c.user_type_id = t.user_type_id
            WHERE c.object_id = OBJECT_ID('dbo.inventory_reservations')
              AND c.name = 'expires_at' AND t.name = 'datetime2' AND c.scale = 3
        ) OR NOT EXISTS (
            SELECT 1 FROM sys.columns c
            JOIN sys.types t ON c.user_type_id = t.user_type_id
            WHERE c.object_id = OBJECT_ID('dbo.inventory_reservations')
              AND c.name = 'completed_at' AND t.name = 'datetime2' AND c.scale = 3
        ) OR NOT EXISTS (
            SELECT 1 FROM sys.columns c
            JOIN sys.types t ON c.user_type_id = t.user_type_id
            WHERE c.object_id = OBJECT_ID('dbo.inventory_reservations')
              AND c.name = 'created_at' AND t.name = 'datetime2' AND c.scale = 3
        ) OR NOT EXISTS (
            SELECT 1 FROM sys.columns c
            JOIN sys.types t ON c.user_type_id = t.user_type_id
            WHERE c.object_id = OBJECT_ID('dbo.inventory_reservations')
              AND c.name = 'updated_at' AND t.name = 'datetime2' AND c.scale = 3
        )
        BEGIN
            THROW 50001, 'Schema incompatibility detected: dbo.inventory_reservations exists but column definitions do not match expected contract.', 1;
        END;
    END
    ELSE
    BEGIN
        -- -----------------------------------------------------------------
        -- STEP 3: CREATE TABLE dbo.inventory_reservations
        -- -----------------------------------------------------------------
        CREATE TABLE dbo.inventory_reservations
        (
            reservation_id BIGINT IDENTITY(1,1) NOT NULL,
            order_reference VARCHAR(100) NOT NULL,
            order_item_reference VARCHAR(100) NOT NULL,
            product_id INT NOT NULL,
            inventory_item_id BIGINT NOT NULL,
            status VARCHAR(20) NOT NULL,
            ttl_minutes INT NOT NULL,
            reserved_at DATETIME2(3) NOT NULL
                CONSTRAINT DF_inventory_reservations_reserved_at DEFAULT (SYSUTCDATETIME()),
            expires_at DATETIME2(3) NOT NULL,
            completed_at DATETIME2(3) NULL,
            created_at DATETIME2(3) NOT NULL
                CONSTRAINT DF_inventory_reservations_created_at DEFAULT (SYSUTCDATETIME()),
            updated_at DATETIME2(3) NOT NULL
                CONSTRAINT DF_inventory_reservations_updated_at DEFAULT (SYSUTCDATETIME()),

            /* Constraints */
            CONSTRAINT PK_inventory_reservations
                PRIMARY KEY (reservation_id),

            CONSTRAINT FK_inventory_reservations_inventory_item
                FOREIGN KEY (inventory_item_id)
                REFERENCES dbo.inventory_items (inventory_item_id),

            CONSTRAINT UQ_inventory_reservations_order_item
                UNIQUE (order_reference, order_item_reference),

            CONSTRAINT CK_inventory_reservations_status
                CHECK (status IN ('ACTIVE', 'COMMITTED', 'RELEASED', 'EXPIRED')),

            CONSTRAINT CK_inventory_reservations_ttl
                CHECK (ttl_minutes > 0),

            CONSTRAINT CK_inventory_reservations_timestamps
                CHECK (expires_at > reserved_at),

            CONSTRAINT CK_inventory_reservations_completed_at
                CHECK (
                    (status = 'ACTIVE' AND completed_at IS NULL)
                    OR
                    (status IN ('COMMITTED', 'RELEASED', 'EXPIRED') AND completed_at IS NOT NULL)
                )
        );
    END;

    -- ---------------------------------------------------------------------
    -- STEP 4: VERIFY / CREATE FILTERED UNIQUE INDEX ON ACTIVE ITEM
    -- Ensures one physical item cannot have more than 1 ACTIVE reservation.
    -- ---------------------------------------------------------------------
    IF NOT EXISTS (
        SELECT 1 FROM sys.indexes
        WHERE name = 'UX_inventory_reservations_active_item'
          AND object_id = OBJECT_ID('dbo.inventory_reservations')
          AND is_unique = 1
          AND has_filter = 1
          AND filter_definition LIKE '%status%ACTIVE%'
    )
    BEGIN
        IF EXISTS (
            SELECT 1 FROM sys.indexes
            WHERE name = 'UX_inventory_reservations_active_item'
              AND object_id = OBJECT_ID('dbo.inventory_reservations')
        )
        BEGIN
            DROP INDEX UX_inventory_reservations_active_item ON dbo.inventory_reservations;
        END;

        CREATE UNIQUE INDEX UX_inventory_reservations_active_item
            ON dbo.inventory_reservations (inventory_item_id)
            WHERE status = 'ACTIVE';
    END;

    -- ---------------------------------------------------------------------
    -- STEP 5: VERIFY / CREATE FILTERED INDEX FOR EXPIRATION SCANNING
    -- ---------------------------------------------------------------------
    IF NOT EXISTS (
        SELECT 1 FROM sys.indexes
        WHERE name = 'IX_inventory_reservations_expires_at'
          AND object_id = OBJECT_ID('dbo.inventory_reservations')
          AND has_filter = 1
          AND filter_definition LIKE '%status%ACTIVE%'
    )
    BEGIN
        IF EXISTS (
            SELECT 1 FROM sys.indexes
            WHERE name = 'IX_inventory_reservations_expires_at'
              AND object_id = OBJECT_ID('dbo.inventory_reservations')
        )
        BEGIN
            DROP INDEX IX_inventory_reservations_expires_at ON dbo.inventory_reservations;
        END;

        CREATE INDEX IX_inventory_reservations_expires_at
            ON dbo.inventory_reservations (expires_at)
            WHERE status = 'ACTIVE';
    END;

    -- ---------------------------------------------------------------------
    -- STEP 6: VERIFY / CREATE ORDER REFERENCE LOOKUP INDEX
    -- ---------------------------------------------------------------------
    IF NOT EXISTS (
        SELECT 1 FROM sys.indexes
        WHERE name = 'IX_inventory_reservations_order_reference'
          AND object_id = OBJECT_ID('dbo.inventory_reservations')
    )
    BEGIN
        CREATE INDEX IX_inventory_reservations_order_reference
            ON dbo.inventory_reservations (order_reference);
    END;

    -- ---------------------------------------------------------------------
    -- STEP 7: VERIFY DATA INTEGRITY - PRESERVE INVENTORY ITEMS RECORD COUNT
    -- ---------------------------------------------------------------------
    DECLARE @post_count INT;
    SELECT @post_count = COUNT(*) FROM dbo.inventory_items;

    IF @pre_count <> @post_count
    BEGIN
        ROLLBACK TRANSACTION;
        THROW 50002, 'Data integrity error: inventory_items count changed during migration.', 1;
    END;

    COMMIT TRANSACTION;
    PRINT 'Migration Script_InventoryReservations.sql executed and committed successfully.';
END TRY
BEGIN CATCH
    IF @@TRANCOUNT > 0
        ROLLBACK TRANSACTION;
    THROW;
END CATCH;
GO

/* =========================================================================
   VERIFICATION QUERIES WITH EXPLICIT PASS/FAIL CRITERIA
   ========================================================================= */

-- 1. Verify Columns and Types
SELECT
    CASE WHEN COUNT(*) = 12 THEN 'PASS' ELSE 'FAIL' END AS columns_check,
    COUNT(*) AS total_columns
FROM sys.columns
WHERE object_id = OBJECT_ID('dbo.inventory_reservations');

-- 2. Verify Primary Key
SELECT
    CASE WHEN COUNT(*) = 1 THEN 'PASS' ELSE 'FAIL' END AS pk_check,
    name AS pk_name
FROM sys.key_constraints
WHERE parent_object_id = OBJECT_ID('dbo.inventory_reservations')
  AND type = 'PK'
GROUP BY name;

-- 3. Verify Foreign Key to inventory_items
SELECT
    CASE WHEN COUNT(*) = 1 THEN 'PASS' ELSE 'FAIL' END AS fk_check,
    name AS fk_name
FROM sys.foreign_keys
WHERE parent_object_id = OBJECT_ID('dbo.inventory_reservations')
  AND referenced_object_id = OBJECT_ID('dbo.inventory_items')
GROUP BY name;

-- 4. Verify Unique Constraint on (order_reference, order_item_reference)
SELECT
    CASE WHEN COUNT(*) = 1 THEN 'PASS' ELSE 'FAIL' END AS unique_business_key_check,
    name AS uq_name
FROM sys.key_constraints
WHERE parent_object_id = OBJECT_ID('dbo.inventory_reservations')
  AND type = 'UQ'
GROUP BY name;

-- 5. Verify Check Constraints (Status, TTL, Timestamps, CompletedAt)
SELECT
    CASE WHEN COUNT(*) >= 4 THEN 'PASS' ELSE 'FAIL' END AS check_constraints_count,
    COUNT(*) AS total_checks
FROM sys.check_constraints
WHERE parent_object_id = OBJECT_ID('dbo.inventory_reservations');

-- 6. Verify Filtered Unique Index on active item
SELECT
    CASE WHEN is_unique = 1 AND has_filter = 1 AND filter_definition LIKE '%status%ACTIVE%'
         THEN 'PASS' ELSE 'FAIL' END AS filtered_unique_active_item_index_check,
    name,
    filter_definition
FROM sys.indexes
WHERE object_id = OBJECT_ID('dbo.inventory_reservations')
  AND name = 'UX_inventory_reservations_active_item';

-- 7. Verify Inventory Items Baseline Row Count Preservation
SELECT
    CASE WHEN COUNT(*) = 23 THEN 'PASS' ELSE 'WARN_COUNT_CHANGED' END AS demo_records_baseline_check,
    COUNT(*) AS current_inventory_items_count
FROM dbo.inventory_items;
GO
