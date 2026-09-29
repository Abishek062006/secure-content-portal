package com.secureportal.payment;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.payment")
public class PaymentProperties {

    private Stripe stripe = new Stripe();
    private Razorpay razorpay = new Razorpay();

    /** Off unless explicitly turned on — a "pay" button that always succeeds is only ever meant for local
     *  testing without real gateway keys, never left reachable by default. */
    private boolean simulatedEnabled = false;

    public Stripe getStripe() {
        return stripe;
    }

    public void setStripe(Stripe stripe) {
        this.stripe = stripe;
    }

    public Razorpay getRazorpay() {
        return razorpay;
    }

    public void setRazorpay(Razorpay razorpay) {
        this.razorpay = razorpay;
    }

    public boolean isSimulatedEnabled() {
        return simulatedEnabled;
    }

    public void setSimulatedEnabled(boolean simulatedEnabled) {
        this.simulatedEnabled = simulatedEnabled;
    }

    public static class Stripe {
        private String apiKey = "";
        private String publishableKey = "";
        private String webhookSecret = "";

        public String getApiKey() {
            return apiKey;
        }

        public void setApiKey(String apiKey) {
            this.apiKey = apiKey;
        }

        public String getPublishableKey() {
            return publishableKey;
        }

        public void setPublishableKey(String publishableKey) {
            this.publishableKey = publishableKey;
        }

        public String getWebhookSecret() {
            return webhookSecret;
        }

        public void setWebhookSecret(String webhookSecret) {
            this.webhookSecret = webhookSecret;
        }

        public boolean isConfigured() {
            return apiKey != null && !apiKey.isBlank();
        }
    }

    public static class Razorpay {
        private String keyId = "";
        private String keySecret = "";

        public String getKeyId() {
            return keyId;
        }

        public void setKeyId(String keyId) {
            this.keyId = keyId;
        }

        public String getKeySecret() {
            return keySecret;
        }

        public void setKeySecret(String keySecret) {
            this.keySecret = keySecret;
        }

        public boolean isConfigured() {
            return keyId != null && !keyId.isBlank() && keySecret != null && !keySecret.isBlank();
        }
    }
}
