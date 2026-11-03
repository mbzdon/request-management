CREATE TABLE requests
(
    id                 UUID PRIMARY KEY,
    name               VARCHAR(255)             NOT NULL,
    body               TEXT                     NOT NULL,
    state              VARCHAR(20)              NOT NULL,
    publication_number BIGINT,
    created_at         TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at         TIMESTAMP WITH TIME ZONE NOT NULL,
    version            BIGINT                   NOT NULL,
    CONSTRAINT uk_requests_publication_number UNIQUE (publication_number)
);

CREATE INDEX idx_requests_created_at ON requests (created_at DESC, id);
CREATE INDEX idx_requests_state_created_at ON requests (state, created_at DESC, id);

CREATE EXTENSION IF NOT EXISTS pg_trgm;
CREATE INDEX idx_requests_name_trgm ON requests USING gin (lower(name) gin_trgm_ops);

CREATE TABLE request_history
(
    request_id  UUID                     NOT NULL REFERENCES requests (id),
    entry_index INTEGER                  NOT NULL,
    from_state  VARCHAR(20)              NOT NULL,
    to_state    VARCHAR(20)              NOT NULL,
    action      VARCHAR(20)              NOT NULL,
    reason      TEXT,
    occurred_at TIMESTAMP WITH TIME ZONE NOT NULL,
    PRIMARY KEY (request_id, entry_index)
);

CREATE SEQUENCE publication_number_seq START WITH 1 INCREMENT BY 1;
