package com.secureportal.payment;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface PaymentOrderRepository extends JpaRepository<PaymentOrder, UUID> {

    List<PaymentOrder> findByUserIdAndCourseId(Long userId, UUID courseId);
}
