CREATE TABLE app_users (
    id UUID PRIMARY KEY,
    email VARCHAR(320) NOT NULL UNIQUE,
    password_hash VARCHAR(100) NOT NULL,
    role VARCHAR(20) NOT NULL CHECK (role IN ('ADMIN', 'CUSTOMER', 'AUDITOR')),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE bank_accounts (
    id UUID PRIMARY KEY,
    owner_id UUID NOT NULL REFERENCES app_users(id),
    account_number VARCHAR(32) NOT NULL UNIQUE,
    currency VARCHAR(3) NOT NULL,
    balance NUMERIC(19, 2) NOT NULL DEFAULT 0 CHECK (balance >= 0),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX bank_accounts_owner_id_idx ON bank_accounts(owner_id);

CREATE TABLE ledger_transactions (
    id UUID PRIMARY KEY,
    reference VARCHAR(36) NOT NULL UNIQUE,
    type VARCHAR(20) NOT NULL CHECK (type IN ('DEPOSIT', 'WITHDRAWAL', 'TRANSFER')),
    actor_id UUID NOT NULL REFERENCES app_users(id),
    source_account_id UUID REFERENCES bank_accounts(id),
    destination_account_id UUID REFERENCES bank_accounts(id),
    amount NUMERIC(19, 2) NOT NULL CHECK (amount > 0),
    description VARCHAR(140) NOT NULL,
    idempotency_key VARCHAR(100) NOT NULL,
    request_fingerprint VARCHAR(64) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ledger_transactions_actor_key_unique UNIQUE (actor_id, idempotency_key)
);

CREATE TABLE ledger_entries (
    id UUID PRIMARY KEY,
    transaction_id UUID NOT NULL REFERENCES ledger_transactions(id),
    account_ref VARCHAR(64) NOT NULL,
    entry_type VARCHAR(6) NOT NULL CHECK (entry_type IN ('DEBIT', 'CREDIT')),
    amount NUMERIC(19, 2) NOT NULL CHECK (amount > 0),
    account_balance_after NUMERIC(19, 2),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX ledger_entries_transaction_id_idx ON ledger_entries(transaction_id);
CREATE INDEX ledger_entries_account_ref_created_at_idx ON ledger_entries(account_ref, created_at DESC);

CREATE TABLE audit_events (
    id UUID PRIMARY KEY,
    actor_id UUID REFERENCES app_users(id),
    action VARCHAR(50) NOT NULL,
    resource_type VARCHAR(50) NOT NULL,
    resource_id VARCHAR(64),
    details VARCHAR(500) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX audit_events_created_at_idx ON audit_events(created_at DESC);
