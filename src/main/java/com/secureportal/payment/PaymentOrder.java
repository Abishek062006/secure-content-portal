package com.secureportal.payment;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/** One purchase attempt for one course. {@code orderId} is the gateway's own reference (a Stripe Checkout
 *  Session id, a Razorpay order id, or our own "SIM_ORDER_..." for the simulated path); {@code paymentId}
 *  is filled in once the gateway confirms it was actually paid. */
@Entity
@Table(name = "payment_orders")
public class PaymentOrder {

    @Id
    private UUID id = UUID.randomUUID();

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "course_id", nullable = false)
    private UUID courseId;

    @Column(name = "amount_rupees", nullable = false)
    private int amountRupees;

    @Column(nullable = false, length = 10)
    private String currency = "INR";

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaymentProvider provider;

    @Column(name = "order_id", length = 255)
    private String orderId;

    @Column(name = "payment_id", length = 255)
    private String paymentId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaymentStatus status = PaymentStatus.PENDING;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    protected PaymentOrder() {
        // for JPA
    }

    public PaymentOrder(Long userId, UUID courseId, int amountRupees, PaymentProvider provider) {
        this.userId = userId;
        this.courseId = courseId;
        this.amountRupees = amountRupees;
        this.provider = provider;
    }

    public void setOrderId(String orderId) {
        this.orderId = orderId;
        this.updatedAt = Instant.now();
    }

    public void complete(String paymentId) {
        this.paymentId = paymentId;
        this.status = PaymentStatus.COMPLETED;
        this.updatedAt = Instant.now();
    }

    public void fail() {
        this.status = PaymentStatus.FAILED;
        this.updatedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public UUID getCourseId() {
        return courseId;
    }

    public int getAmountRupees() {
        return amountRupees;
    }

    public String getCurrency() {
        return currency;
    }

    public PaymentProvider getProvider() {
        return provider;
    }

    public String getOrderId() {
        return orderId;
    }

    public String getPaymentId() {
        return paymentId;
    }

    public PaymentStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
