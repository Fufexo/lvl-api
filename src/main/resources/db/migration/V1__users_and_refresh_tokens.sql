create table users (
    id            uuid primary key,
    gamertag      varchar(16)  not null,
    email         varchar(254) not null,
    password_hash varchar(100) not null,
    created_at    timestamptz  not null
);

-- Gamertags and emails are unique regardless of case.
create unique index users_gamertag_uk on users (lower(gamertag));
create unique index users_email_uk on users (lower(email));

-- Opaque refresh tokens, stored only as a SHA-256 hash. Each login starts a family;
-- every refresh rotates the token inside it, and reusing a spent token revokes the family.
create table refresh_tokens (
    id         uuid primary key,
    user_id    uuid        not null references users (id) on delete cascade,
    family_id  uuid        not null,
    token_hash varchar(64) not null,
    expires_at timestamptz not null,
    used_at    timestamptz,
    revoked_at timestamptz,
    created_at timestamptz not null
);

create unique index refresh_tokens_hash_uk on refresh_tokens (token_hash);
create index refresh_tokens_family_idx on refresh_tokens (family_id);
create index refresh_tokens_user_idx on refresh_tokens (user_id);
