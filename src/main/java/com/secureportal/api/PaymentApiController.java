package com.secureportal.api;

import com.secureportal.auth.AppPrincipal;
import com.secureportal.course.AdminNotALearnerException;
import com.secureportal.payment.PaymentProperties;
import com.secureportal.payment.PaymentService;
import com.secureportal.payment.dto.CreateOrderRequest;
import com.secureportal.payment.dto.CreateOrderResponse;
import com.secureportal.payment.dto.PaymentConfigDto;
import com.secureportal.payment.dto.SimulatedCheckoutRequest;
import com.secureportal.payment.dto.VerifyRazorpayRequest;
import com.stripe.model.Event;
import com.stripe.model.checkout.Session;
import com.stripe.net.Webhook;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

/** Course checkout. {@code /webhooks/stripe} is the one route here a browser never calls — it's permitAll
 *  and CSRF-exempt in SecurityConfig because Stripe's own servers call it directly, with its own signature
 *  in place of a session or CSRF token. */
@RestController
@RequestMapping("/api/payments")
public class PaymentApiController {

    private static final Logger log = LoggerFactory.getLogger(PaymentApiController.class);

    private final PaymentService paymentService;
    private final PaymentProperties paymentProperties;

    public PaymentApiController(PaymentService paymentService, PaymentProperties paymentProperties) {
        this.paymentService = paymentService;
        this.paymentProperties = paymentProperties;
    }

    @GetMapping("/config")
    public PaymentConfigDto config() {
        return paymentService.getConfig();
    }

    @PostMapping("/create-order")
    public CreateOrderResponse createOrder(@Valid @RequestBody CreateOrderRequest request, @AuthenticationPrincipal AppPrincipal principal) {
        if (principal.isAdmin()) {
            throw new AdminNotALearnerException();
        }
        return paymentService.createOrder(principal.getUserId(), request.courseId(), request.provider());
    }

    @GetMapping("/stripe/confirm")
    public Map<String, Object> confirmStripeSession(@RequestParam("session_id") String sessionId,
                                                     @AuthenticationPrincipal AppPrincipal principal) {
        if (principal.isAdmin()) {
            throw new AdminNotALearnerException();
        }
        return paymentService.confirmStripeSession(principal.getUserId(), sessionId);
    }

    @PostMapping("/razorpay/verify")
    public Map<String, Object> verifyRazorpay(@Valid @RequestBody VerifyRazorpayRequest request, @AuthenticationPrincipal AppPrincipal principal) {
        paymentService.verifyRazorpayPayment(principal.getUserId(), request);
        return Map.of("success", true);
    }

    @PostMapping("/simulated/complete")
    public Map<String, Object> completeSimulated(@Valid @RequestBody SimulatedCheckoutRequest request, @AuthenticationPrincipal AppPrincipal principal) {
        paymentService.completeSimulatedPayment(principal.getUserId(), request.paymentOrderId());
        return Map.of("success", true);
    }

    @PostMapping("/webhooks/stripe")
    public ResponseEntity<String> stripeWebhook(@RequestBody String payload,
                                                @RequestHeader(value = "Stripe-Signature", required = false) String signature) {
        String secret = paymentProperties.getStripe().getWebhookSecret();
        if (secret == null || secret.isBlank() || signature == null) {
            // Not configured — the frontend's own /payment/success confirmation is still the path of
            // record; this just means the webhook backup isn't wired up yet, not a security hole.
            return ResponseEntity.ok("ignored");
        }
        Event event;
        try {
            event = Webhook.constructEvent(payload, signature, secret);
        } catch (Exception e) {
            log.warn("Stripe webhook signature did not verify", e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("bad signature");
        }
        if ("checkout.session.completed".equals(event.getType())) {
            event.getDataObjectDeserializer().getObject().ifPresent(obj -> {
                if (obj instanceof Session session && session.getMetadata() != null) {
                    String orderId = session.getMetadata().get("paymentOrderId");
                    if (orderId != null) {
                        try {
                            paymentService.fulfillStripeSession(UUID.fromString(orderId), session.getPaymentIntent());
                        } catch (IllegalArgumentException e) {
                            log.warn("Stripe webhook carried a malformed paymentOrderId: {}", orderId);
                        }
                    }
                }
            });
        }
        return ResponseEntity.ok("received");
    }
}
