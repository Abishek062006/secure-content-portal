-- One row per checkout attempt. Enrollment happens when status becomes COMPLETED, not on row creation —
-- see PaymentService.
CREATE TABLE payment_orders (
    id            BINARY(16)   NOT NULL PRIMARY KEY,
    user_id       BIGINT       NOT NULL,
    course_id     BINARY(16)   NOT NULL,
    amount_rupees INT          NOT NULL,
    currency      VARCHAR(10)  NOT NULL DEFAULT 'INR',
    provider      VARCHAR(20)  NOT NULL,
    order_id      VARCHAR(255),
    payment_id    VARCHAR(255),
    status        VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    created_at    DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at    DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT payment_orders_user_fk   FOREIGN KEY (user_id)   REFERENCES users (id),
    CONSTRAINT payment_orders_course_fk FOREIGN KEY (course_id) REFERENCES courses (id) ON DELETE CASCADE,
    CONSTRAINT payment_orders_provider_check CHECK (provider IN ('STRIPE', 'RAZORPAY', 'SIMULATED')),
    CONSTRAINT payment_orders_status_check CHECK (status IN ('PENDING', 'COMPLETED', 'FAILED'))
) ENGINE = InnoDB;

CREATE INDEX payment_orders_user_course_idx ON payment_orders (user_id, course_id);
