CREATE TABLE accounts (
    id UUID PRIMARY KEY,
    account_number VARCHAR(36) NOT NULL UNIQUE,
    owner_name VARCHAR(120) NOT NULL,
    balance DECIMAL(19,2) NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT nonnegative_balance CHECK (balance >= 0)
);

CREATE TABLE transactions (
    id UUID PRIMARY KEY,
    from_account_id UUID REFERENCES accounts(id),
    to_account_id UUID REFERENCES accounts(id),
    type VARCHAR(16) NOT NULL,
    amount DECIMAL(19,2) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    idempotency_key VARCHAR(128) UNIQUE,
    CONSTRAINT positive_amount CHECK (amount > 0),
    CONSTRAINT valid_transaction CHECK (
        (type = 'DEPOSIT' AND from_account_id IS NULL AND to_account_id IS NOT NULL AND idempotency_key IS NULL)
        OR (type = 'WITHDRAWAL' AND from_account_id IS NOT NULL AND to_account_id IS NULL AND idempotency_key IS NULL)
        OR (type = 'TRANSFER' AND from_account_id IS NOT NULL AND to_account_id IS NOT NULL
            AND from_account_id <> to_account_id AND idempotency_key IS NOT NULL)
    )
);
CREATE INDEX transactions_from_history ON transactions (from_account_id, created_at DESC, id DESC);
CREATE INDEX transactions_to_history ON transactions (to_account_id, created_at DESC, id DESC);
