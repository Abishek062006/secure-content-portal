package com.secureportal.payment;

import com.razorpay.RazorpayClient;
import com.secureportal.config.AppProperties;
import com.secureportal.course.Course;
import com.secureportal.course.CourseAccessType;
import com.secureportal.course.CoursePricing;
import com.secureportal.course.LearningService;
import com.secureportal.payment.dto.CreateOrderResponse;
import com.secureportal.payment.dto.PaymentConfigDto;
import com.secureportal.payment.dto.VerifyRazorpayRequest;
import com.stripe.Stripe;
import com.stripe.model.checkout.Session;
import com.stripe.param.checkout.SessionCreateParams;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Course checkout: Stripe, Razorpay, and (opt-in only, see {@link PaymentProperties#isSimulatedEnabled()})
 *  a simulated gateway for testing without real keys. The server always computes the amount itself from
 *  {@link CoursePricing} — nothing here trusts a price the client sends. */
@Service
public class PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);

    private final PaymentOrderRepository orders;
    private final LearningService learningService;
    private final PaymentProperties paymentProperties;
    private final AppProperties appProperties;

    public PaymentService(PaymentOrderRepository orders, LearningService learningService,
                          PaymentProperties paymentProperties, AppProperties appProperties) {
        this.orders = orders;
        this.learningService = learningService;
        this.paymentProperties = paymentProperties;
        this.appProperties = appProperties;
    }

    public PaymentConfigDto getConfig() {
        List<PaymentProvider> available = new ArrayList<>();
        if (paymentProperties.getStripe().isConfigured()) {
            available.add(PaymentProvider.STRIPE);
        }
        if (paymentProperties.getRazorpay().isConfigured()) {
            available.add(PaymentProvider.RAZORPAY);
        }
        // Simulated is never implied by the others being unconfigured — it only ever appears when someone
        // has deliberately turned it on (PAYMENT_SIMULATED_ENABLED=true), so a deployment that simply
        // forgot to set real keys fails closed instead of quietly letting everyone "pay" for free.
        if (paymentProperties.isSimulatedEnabled()) {
            available.add(PaymentProvider.SIMULATED);
        }
        return new PaymentConfigDto(paymentProperties.getStripe().isConfigured(), paymentProperties.getStripe().getPublishableKey(),
                paymentProperties.getRazorpay().isConfigured(), paymentProperties.getRazorpay().getKeyId(),
                paymentProperties.isSimulatedEnabled(), available);
    }

    @Transactional
    public CreateOrderResponse createOrder(Long userId, UUID courseId, PaymentProvider provider) {
        Course course = learningService.visibleCourse(courseId, false);
        if (course.getAccessType() == CourseAccessType.REGISTER) {
            throw new PaymentException("This course is by approval, not by payment — send a request instead.");
        }
        if (provider == PaymentProvider.SIMULATED && !paymentProperties.isSimulatedEnabled()) {
            throw new PaymentException("The simulated gateway isn't enabled.");
        }

        CoursePricing pricing = course.getPricing();
        int finalPrice = pricing.finalPriceRupees(Instant.now());
        if (finalPrice <= 0) {
            throw new PaymentException("This course is free — enroll directly instead of paying for it.");
        }

        PaymentOrder order = orders.save(new PaymentOrder(userId, courseId, finalPrice, provider));

        String gatewayOrderId;
        String checkoutUrl = null;
        String keyId = null;
        switch (provider) {
            case STRIPE -> {
                Stripe.apiKey = paymentProperties.getStripe().getApiKey();
                String successUrl = appProperties.getFrontendUrl() + "/payment/success?session_id={CHECKOUT_SESSION_ID}&course_id=" + courseId;
                String cancelUrl = appProperties.getFrontendUrl() + "/courses/" + courseId + "?payment=cancelled";
                SessionCreateParams params = SessionCreateParams.builder()
                        .setMode(SessionCreateParams.Mode.PAYMENT)
                        .setSuccessUrl(successUrl)
                        .setCancelUrl(cancelUrl)
                        .putMetadata("paymentOrderId", order.getId().toString())
                        .putMetadata("userId", userId.toString())
                        .putMetadata("courseId", courseId.toString())
                        .addLineItem(SessionCreateParams.LineItem.builder()
                                .setQuantity(1L)
                                .setPriceData(SessionCreateParams.LineItem.PriceData.builder()
                                        .setCurrency("inr")
                                        .setUnitAmount((long) finalPrice * 100)
                                        .setProductData(SessionCreateParams.LineItem.PriceData.ProductData.builder()
                                                .setName(course.getTitle())
                                                .setDescription("GradientNovaAI course enrollment")
                                                .build())
                                        .build())
                                .build())
                        .build();
                try {
                    Session session = Session.create(params);
                    gatewayOrderId = session.getId();
                    checkoutUrl = session.getUrl();
                } catch (Exception e) {
                    log.error("Stripe checkout session creation failed for course {}", courseId, e);
                    throw new PaymentException("Couldn't start Stripe checkout. Try again shortly.");
                }
            }
            case RAZORPAY -> {
                keyId = paymentProperties.getRazorpay().getKeyId();
                try {
                    RazorpayClient client = new RazorpayClient(keyId, paymentProperties.getRazorpay().getKeySecret());
                    JSONObject request = new JSONObject();
                    request.put("amount", finalPrice * 100);
                    request.put("currency", "INR");
                    request.put("receipt", order.getId().toString());
                    gatewayOrderId = client.orders.create(request).get("id");
                } catch (Exception e) {
                    log.error("Razorpay order creation failed for course {}", courseId, e);
                    throw new PaymentException("Couldn't start Razorpay checkout. Try again shortly.");
                }
            }
            default -> {
                gatewayOrderId = "SIM_ORDER_" + order.getId();
                checkoutUrl = appProperties.getFrontendUrl() + "/courses/" + courseId + "?simulated_checkout=" + order.getId();
            }
        }

        order.setOrderId(gatewayOrderId);
        return new CreateOrderResponse(order.getId(), courseId, course.getTitle(), finalPrice, order.getCurrency(),
                provider, gatewayOrderId, checkoutUrl, keyId);
    }

    @Transactional
    public void verifyRazorpayPayment(Long userId, VerifyRazorpayRequest req) {
        PaymentOrder order = ownedOrder(req.paymentOrderId(), userId);
        JSONObject options = new JSONObject();
        options.put("razorpay_order_id", req.razorpayOrderId());
        options.put("razorpay_payment_id", req.razorpayPaymentId());
        options.put("razorpay_signature", req.razorpaySignature());
        boolean valid;
        try {
            valid = com.razorpay.Utils.verifyPaymentSignature(options, paymentProperties.getRazorpay().getKeySecret());
        } catch (Exception e) {
            log.error("Razorpay signature verification threw for order {}", order.getId(), e);
            valid = false;
        }
        if (!valid) {
            order.fail();
            throw new PaymentException("That payment couldn't be verified.");
        }
        order.complete(req.razorpayPaymentId());
        enroll(order);
    }

    @Transactional
    public void completeSimulatedPayment(Long userId, UUID paymentOrderId) {
        if (!paymentProperties.isSimulatedEnabled()) {
            throw new PaymentException("The simulated gateway isn't enabled.");
        }
        PaymentOrder order = ownedOrder(paymentOrderId, userId);
        order.complete("SIM_" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        enroll(order);
    }

    /** Called from the Stripe webhook once its signature has already been verified by the controller. */
    @Transactional
    public void fulfillStripeSession(UUID paymentOrderId, String stripePaymentId) {
        orders.findById(paymentOrderId).ifPresent(order -> {
            if (order.getStatus() != PaymentStatus.COMPLETED) {
                order.complete(stripePaymentId);
                enroll(order);
            }
        });
    }

    /** The frontend's own success-page check, for when the webhook hasn't landed yet (or isn't
     *  configured) — asks Stripe directly whether the session was actually paid before enrolling anyone. */
    @Transactional
    public Map<String, Object> confirmStripeSession(Long userId, String stripeSessionId) {
        if (stripeSessionId == null || stripeSessionId.isBlank()) {
            throw new PaymentException("Missing payment session.");
        }
        if (!paymentProperties.getStripe().isConfigured()) {
            throw new PaymentException("Stripe isn't configured.");
        }
        Stripe.apiKey = paymentProperties.getStripe().getApiKey();
        Session session;
        try {
            session = Session.retrieve(stripeSessionId);
        } catch (Exception e) {
            log.error("Could not retrieve Stripe session {}", stripeSessionId, e);
            throw new PaymentException("Couldn't reach Stripe to confirm this payment.");
        }
        if (!"paid".equals(session.getPaymentStatus())) {
            throw new PaymentException("That payment hasn't completed yet.");
        }
        String orderIdStr = session.getMetadata() == null ? null : session.getMetadata().get("paymentOrderId");
        if (orderIdStr == null) {
            throw new PaymentException("This session isn't one of ours.");
        }
        PaymentOrder order = ownedOrder(UUID.fromString(orderIdStr), userId);
        Course course = learningService.visibleCourse(order.getCourseId(), false);
        if (order.getStatus() != PaymentStatus.COMPLETED) {
            order.complete(session.getPaymentIntent());
            enroll(order);
        }
        return Map.of("courseId", course.getId().toString(), "courseTitle", course.getTitle(),
                "paymentIntentId", session.getPaymentIntent() == null ? "" : session.getPaymentIntent());
    }

    private PaymentOrder ownedOrder(UUID id, Long userId) {
        PaymentOrder order = orders.findById(id).orElseThrow(() -> new PaymentAccessException("That order no longer exists."));
        if (!order.getUserId().equals(userId)) {
            throw new PaymentAccessException("That order doesn't belong to you.");
        }
        return order;
    }

    private void enroll(PaymentOrder order) {
        Course course = learningService.visibleCourse(order.getCourseId(), false);
        learningService.enroll(order.getUserId(), course);
        log.info("Enrolled user {} in course {} via {} payment {}", order.getUserId(), order.getCourseId(),
                order.getProvider(), order.getId());
    }
}
