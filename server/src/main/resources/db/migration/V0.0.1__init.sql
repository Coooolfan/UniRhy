CREATE TABLE public.account
(
    id       BIGSERIAL PRIMARY KEY,
    name     TEXT    NOT NULL,
    password TEXT    NOT NULL,
    email    TEXT    NOT NULL,
    admin    BOOLEAN NOT NULL,
    CONSTRAINT account_unique_name
        UNIQUE (name),
    CONSTRAINT account_unique_email
        UNIQUE (email)
);