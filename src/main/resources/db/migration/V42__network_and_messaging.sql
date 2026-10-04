-- V42: connections between members, and one-to-one messages between connected members

CREATE TABLE connections (
    id           BIGINT      NOT NULL AUTO_INCREMENT PRIMARY KEY,
    requester_id BIGINT      NOT NULL,
    receiver_id  BIGINT      NOT NULL,
    status       VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    created_at   DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    responded_at DATETIME(6) NULL,
    -- The same two people can only ever have one relationship, whichever of them asked first: two requests sent at the same
    -- moment can't both be stored, so there is never a pending request in each direction.
    pair_low     BIGINT AS (LEAST(requester_id, receiver_id)) VIRTUAL,
    pair_high    BIGINT AS (GREATEST(requester_id, receiver_id)) VIRTUAL,
    CONSTRAINT uk_connections_pair UNIQUE (pair_low, pair_high),
    CONSTRAINT connections_status_check CHECK (status IN ('PENDING', 'ACCEPTED')),
    CONSTRAINT connections_not_self_check CHECK (requester_id <> receiver_id),
    CONSTRAINT connections_requester_fk FOREIGN KEY (requester_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT connections_receiver_fk FOREIGN KEY (receiver_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE = InnoDB;

CREATE INDEX connections_receiver_idx ON connections (receiver_id, status);
CREATE INDEX connections_requester_idx ON connections (requester_id, status);

-- One conversation per pair of people; user1_id is always the smaller id.
CREATE TABLE conversations (
    id              BIGINT      NOT NULL AUTO_INCREMENT PRIMARY KEY,
    user1_id        BIGINT      NOT NULL,
    user2_id        BIGINT      NOT NULL,
    last_message_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    created_at      DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT uk_conversations_pair UNIQUE (user1_id, user2_id),
    CONSTRAINT conversations_order_check CHECK (user1_id < user2_id),
    CONSTRAINT conversations_user1_fk FOREIGN KEY (user1_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT conversations_user2_fk FOREIGN KEY (user2_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE = InnoDB;

CREATE INDEX conversations_user1_idx ON conversations (user1_id, last_message_at DESC);
CREATE INDEX conversations_user2_idx ON conversations (user2_id, last_message_at DESC);

CREATE TABLE messages (
    id              BIGINT        NOT NULL AUTO_INCREMENT PRIMARY KEY,
    conversation_id BIGINT        NOT NULL,
    sender_id       BIGINT        NOT NULL,
    content         VARCHAR(2000) NOT NULL,
    created_at      DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    read_at         DATETIME(6)   NULL,
    CONSTRAINT messages_conversation_fk FOREIGN KEY (conversation_id) REFERENCES conversations (id) ON DELETE CASCADE,
    CONSTRAINT messages_sender_fk FOREIGN KEY (sender_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE = InnoDB;

CREATE INDEX messages_conversation_idx ON messages (conversation_id, created_at);
