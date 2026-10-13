-- Double-entry ledger. Money is stored in minor units (céntimos / cents) as bigint.
--
-- accounts:       user accounts (one per currency) and system accounts (treasury, FX clearing).
-- transfers:      one business operation (welcome bonus, transfer between own accounts, P2P).
-- ledger_entries: the postings of a transfer. Per currency, the entries of a transfer add up to zero.

create table accounts (
    id            uuid primary key,
    owner_id      uuid references users (id) on delete cascade,
    kind          varchar(8)  not null,
    code          varchar(32),
    currency      varchar(3)  not null,
    balance_minor bigint      not null default 0,
    created_at    timestamptz not null,
    constraint accounts_kind_ck check (kind in ('USER', 'SYSTEM')),
    constraint accounts_currency_ck check (currency in ('PEN', 'USD')),
    constraint accounts_owner_ck check ((kind = 'USER') = (owner_id is not null)),
    constraint accounts_code_ck check ((kind = 'SYSTEM') = (code is not null)),
    -- Last line of defense: a user account can never go below zero, whatever the code does.
    constraint accounts_user_balance_ck check (kind = 'SYSTEM' or balance_minor >= 0)
);

create unique index accounts_owner_currency_uk on accounts (owner_id, currency) where owner_id is not null;
create unique index accounts_code_uk on accounts (code) where code is not null;

create table transfers (
    id                  uuid primary key,
    kind                varchar(16)   not null,
    initiated_by        uuid references users (id) on delete cascade,
    idempotency_key     varchar(64),
    request_hash        varchar(64),
    source_account_id   uuid          not null references accounts (id),
    target_account_id   uuid          not null references accounts (id),
    source_amount_minor bigint        not null,
    source_currency     varchar(3)    not null,
    target_amount_minor bigint        not null,
    target_currency     varchar(3)    not null,
    fx_rate             numeric(18, 8),
    note                varchar(140),
    created_at          timestamptz   not null,
    constraint transfers_kind_ck check (kind in ('WELCOME', 'INTERNAL', 'P2P')),
    constraint transfers_amounts_ck check (source_amount_minor > 0 and target_amount_minor > 0),
    constraint transfers_accounts_ck check (source_account_id <> target_account_id)
);

-- A retried request with the same Idempotency-Key never creates a second transfer.
create unique index transfers_idempotency_uk on transfers (initiated_by, idempotency_key)
    where idempotency_key is not null;

create table ledger_entries (
    id                  uuid primary key,
    transfer_id         uuid         not null references transfers (id),
    account_id          uuid         not null references accounts (id),
    amount_minor        bigint       not null,
    currency            varchar(3)   not null,
    balance_after_minor bigint       not null,
    description         varchar(160) not null,
    created_at          timestamptz  not null,
    constraint ledger_entries_amount_ck check (amount_minor <> 0)
);

-- Keyset pagination of an account's movements, newest first.
create index ledger_entries_account_idx on ledger_entries (account_id, created_at desc, id desc);
create index ledger_entries_transfer_idx on ledger_entries (transfer_id);

-- System accounts: the treasury funds welcome bonuses; FX clearing balances currency exchanges.
insert into accounts (id, kind, code, currency, created_at) values
    ('00000000-0000-7000-8000-000000000001', 'SYSTEM', 'TREASURY_PEN', 'PEN', now()),
    ('00000000-0000-7000-8000-000000000002', 'SYSTEM', 'TREASURY_USD', 'USD', now()),
    ('00000000-0000-7000-8000-000000000003', 'SYSTEM', 'FX_PEN', 'PEN', now()),
    ('00000000-0000-7000-8000-000000000004', 'SYSTEM', 'FX_USD', 'USD', now());
