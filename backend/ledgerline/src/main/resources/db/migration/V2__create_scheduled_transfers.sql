CREATE TABLE scheduled_transfers (
    id UUID PRIMARY KEY,
    owner_id UUID NOT NULL REFERENCES app_users(id),
    source_account_id UUID NOT NULL REFERENCES bank_accounts(id),
    destination_account_id UUID NOT NULL REFERENCES bank_accounts(id),
    amount NUMERIC(19, 2) NOT NULL CHECK (amount > 0),
    scheduled_at TIMESTAMP WITH TIME ZONE NOT NULL,
    status VARCHAR(20) NOT NULL CHECK (status IN ('PENDING', 'COMPLETED', 'FAILED', 'CANCELLED')),
    failure_reason VARCHAR(500),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT scheduled_transfers_distinct_accounts CHECK (source_account_id <> destination_account_id)
);
CREATE INDEX scheduled_transfers_due_idx ON scheduled_transfers(status, scheduled_at);
CREATE INDEX scheduled_transfers_owner_idx ON scheduled_transfers(owner_id, scheduled_at DESC);
