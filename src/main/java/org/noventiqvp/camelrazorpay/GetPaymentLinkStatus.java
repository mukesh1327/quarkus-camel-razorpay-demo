package org.noventiqvp.camelrazorpay;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@ApplicationScoped
public class GetPaymentLinkStatus extends RouteBuilder {

    @Inject
    @ConfigProperty(name = "razorpay.key")
    String razorpayKey;

    @Inject
    @ConfigProperty(name = "razorpay.secret")
    String razorpaySecret;

    @Override
    @SuppressWarnings("unchecked")
    public void configure() throws Exception {

        // Basic Auth header
        String authHeader = "Basic " + Base64.getEncoder()
            .encodeToString((razorpayKey + ":" + razorpaySecret).getBytes());

        from("platform-http:/get-payment-link/{plink_id}?httpMethodRestrict=GET")
            .routeId("get-payment-link-for-status")
            .log("Fetching Razorpay payment link for plink_id: ${header.plink_id}")

            .setHeader(Exchange.HTTP_METHOD, constant("GET"))
            .setHeader("Authorization", constant(authHeader))
            .setHeader(Exchange.CONTENT_TYPE, constant("application/json"))
            .removeHeaders("CamelHttp*")

            .toD("https://api.razorpay.com/v1/payment_links/${header.plink_id}?bridgeEndpoint=true")
            .unmarshal().json()

            // Log extracted fields
            .process(new PaymentsLoggerProcessor())

            // Send PaymentStatusUpdated event to Kafka
            .process(exchange -> {
                Map<String, Object> body = exchange.getMessage().getBody(Map.class);

                Map<String, Object> notes = (Map<String, Object>) body.get("notes");
                List<Map<String, Object>> payments = (List<Map<String, Object>>) body.get("payments");

                // Prepare payload
                Map<String, Object> payload = new HashMap<>();
                payload.put("Amount", body.get("amount"));
                payload.put("PaymentlinkId", body.get("id"));
                payload.put("ReferenceId", body.get("reference_id"));
                payload.put("Status", body.get("status"));

                if (notes != null) {
                    payload.put("Uhid", notes.get("UHID"));
                }

                // Use first payment if available
                if (payments != null && !payments.isEmpty()) {
                    Map<String, Object> firstPayment = payments.get(0);
                    payload.put("PaymentId", firstPayment.get("payment_id"));
                }

                // Wrap in event envelope
                Map<String, Object> eventWrapper = new HashMap<>();
                eventWrapper.put("EventType", "PaymentStatusUpdated");
                eventWrapper.put("EventEmittedAt", java.time.Instant.now().toString());
                eventWrapper.put("EventData", payload);

                // ✅ Serialize only eventWrapper, keep main route body untouched
                String eventJson = new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(eventWrapper);
                
                // Send to Kafka
                exchange.getContext().createProducerTemplate().sendBody("kafka:razorpaydemo", eventJson);
            })

            .marshal().json();
    }
}
