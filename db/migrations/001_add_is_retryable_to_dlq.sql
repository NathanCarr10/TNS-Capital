-- Migration: Add is_retryable column to dlq_messages table
-- Purpose: Distinguish between retryable (business logic) and non-retryable (not-found) failures
-- This allows admins to understand which DLQ messages can be safely replayed

ALTER TABLE dlq_messages
ADD COLUMN IF NOT EXISTS is_retryable BOOLEAN DEFAULT true NOT NULL;

-- Update existing records: mark as retryable by default (safe assumption)
-- In practice, non-retryable errors are marked with "NON_RETRYABLE_" prefix in failure_type
UPDATE dlq_messages
SET is_retryable = false
WHERE failure_type LIKE 'NON_RETRYABLE_%'
  AND is_retryable = true;

-- Add comment for the new column
COMMENT ON COLUMN dlq_messages.is_retryable IS 'true = retryable (business logic error, can be safely replayed), false = non-retryable (not-found error, impossible scenario)';

-- Add index to support filtering by retryability
CREATE INDEX IF NOT EXISTS idx_dlq_retryable ON dlq_messages (is_retryable);

-- Update existing combined index to include retryability
-- (Note: This is informational; the old index will remain for backward compatibility)
CREATE INDEX IF NOT EXISTS idx_dlq_status_retryable ON dlq_messages (status, is_retryable, created_on DESC);
