-- teramera D1 schema (SQLite-compatible, mirrors backend/src/main/java/.../SchemaInitializer.java)

CREATE TABLE IF NOT EXISTS users (
    id TEXT PRIMARY KEY,
    phone TEXT UNIQUE,
    email TEXT UNIQUE,
    name TEXT,
    avatar_url TEXT,
    created_at INTEGER NOT NULL
);

CREATE TABLE IF NOT EXISTS otp_requests (
    id TEXT PRIMARY KEY,
    phone TEXT NOT NULL,
    code_hash TEXT NOT NULL,
    expires_at INTEGER NOT NULL,
    attempts INTEGER NOT NULL DEFAULT 0,
    consumed INTEGER NOT NULL DEFAULT 0,
    created_at INTEGER NOT NULL
);

CREATE TABLE IF NOT EXISTS refresh_tokens (
    id TEXT PRIMARY KEY,
    user_id TEXT NOT NULL REFERENCES users(id),
    token_hash TEXT NOT NULL,
    expires_at INTEGER NOT NULL,
    revoked INTEGER NOT NULL DEFAULT 0,
    created_at INTEGER NOT NULL
);

CREATE TABLE IF NOT EXISTS groups (
    id TEXT PRIMARY KEY,
    name TEXT NOT NULL,
    currency TEXT NOT NULL DEFAULT 'INR',
    created_by TEXT NOT NULL REFERENCES users(id),
    created_at INTEGER NOT NULL
);

CREATE TABLE IF NOT EXISTS memberships (
    group_id TEXT NOT NULL REFERENCES groups(id),
    user_id TEXT NOT NULL REFERENCES users(id),
    role TEXT NOT NULL DEFAULT 'member',
    PRIMARY KEY (group_id, user_id)
);

CREATE TABLE IF NOT EXISTS expenses (
    id TEXT PRIMARY KEY,
    group_id TEXT REFERENCES groups(id),
    paid_by_user_id TEXT NOT NULL REFERENCES users(id),
    title TEXT NOT NULL,
    amount_minor INTEGER NOT NULL,
    split_type TEXT NOT NULL,
    currency TEXT NOT NULL DEFAULT 'INR',
    fx_rate_to_group REAL NOT NULL DEFAULT 1.0,
    created_at INTEGER NOT NULL
);

CREATE TABLE IF NOT EXISTS expense_shares (
    expense_id TEXT NOT NULL REFERENCES expenses(id),
    user_id TEXT NOT NULL REFERENCES users(id),
    share_amount_minor INTEGER NOT NULL,
    PRIMARY KEY (expense_id, user_id)
);

CREATE TABLE IF NOT EXISTS settlements (
    id TEXT PRIMARY KEY,
    group_id TEXT REFERENCES groups(id),
    payer_user_id TEXT NOT NULL REFERENCES users(id),
    paid_to_user_id TEXT NOT NULL REFERENCES users(id),
    amount_minor INTEGER NOT NULL,
    method TEXT NOT NULL,
    created_at INTEGER NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_otp_phone ON otp_requests(phone, created_at);
CREATE INDEX IF NOT EXISTS idx_expenses_group ON expenses(group_id, created_at);
CREATE INDEX IF NOT EXISTS idx_shares_expense ON expense_shares(expense_id);
