package org.noventiqvp.camelrazorpay;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

@ApplicationScoped
@SuppressWarnings("unchecked")
public class SendPayLink extends RouteBuilder {

    @Inject
    @ConfigProperty(name = "razorpay.key")
    String razorpayKey;

    @Inject
    @ConfigProperty(name = "razorpay.secret")
    String razorpaySecret;

    @Override
    public void configure() throws Exception {
        
        ObjectMapper mapper = new ObjectMapper();

        // Basic Auth header
        String authHeader = "Basic " + Base64.getEncoder()
            .encodeToString((razorpayKey + ":" + razorpaySecret).getBytes());

        // Handle Razorpay error response
        onException(org.apache.camel.http.base.HttpOperationFailedException.class)
            .handled(true)
            .process(exchange -> {
                var cause = exchange.getProperty(Exchange.EXCEPTION_CAUGHT, org.apache.camel.http.base.HttpOperationFailedException.class);
                String errorBody = cause.getResponseBody();
                int statusCode = cause.getStatusCode();
                System.err.println("[RAZORPAY ERROR] HTTP " + statusCode + ": " + errorBody);

                exchange.getMessage().setHeader(Exchange.CONTENT_TYPE, "application/json");
                exchange.getMessage().setBody("{\"error\":\"Razorpay error: " + errorBody.replace("\"", "\\\"") + "\"}");
                exchange.getMessage().setHeader(Exchange.HTTP_RESPONSE_CODE, statusCode);
            });

        from("platform-http:/create-payment-link?httpMethodRestrict=POST")
            .routeId("create-and-fetch-payment-link")

            .log("Received payment creation request: ${body}")
            .unmarshal().json()

            // Send PaymentLinkRequestSent to Kafka before calling Razorpay
            .process(exchange -> {
                Map<String, Object> input = exchange.getMessage().getBody(Map.class);
                Map<String, Object> notes = (Map<String, Object>) input.get("notes");

                Map<String, Object> payload = new HashMap<>();
                payload.put("amount", input.get("amount"));
                payload.put("currency", input.get("currency"));
                payload.put("Description", input.get("description"));
                payload.put("ReferenceId", input.get("reference_id"));
                payload.put("ExpireBy", input.get("expire_by"));

                payload.put("CustomerName", ((Map<?, ?>) input.get("customer")).get("name"));
                payload.put("CustomerEmail", ((Map<?, ?>) input.get("customer")).get("email"));
                payload.put("CustomerContact", ((Map<?, ?>) input.get("customer")).get("contact"));

                if (notes != null) {
                    payload.put("Cid", notes.get("CID"));
                    payload.put("HospitalCode", notes.get("shop_code"));
                    payload.put("Source", notes.get("source"));
                    payload.put("DepositTypeCode", notes.get("deposit_type_code"));
                }

                Map<String, Object> event = new HashMap<>();
                event.put("EventType", "PaymentLinkRequestSent");
                event.put("EventEmittedAt", java.time.Instant.now().toString());
                event.put("EventData", payload);
                String eventJson = mapper.writeValueAsString(event);

                log.info("Sending JSON to Kafka: {}", eventJson);

                exchange.getContext().createProducerTemplate().sendBody("kafka:razorpaytopic", eventJson);
            })

            .setHeader("Authorization", constant(authHeader))
            .setHeader("Content-Type", constant("application/json"))
            .marshal().json() // re-marshals body for Razorpay call
            .removeHeaders("CamelHttp*")
            .to("https://api.razorpay.com/v1/payment_links?bridgeEndpoint=true")
            .unmarshal().json()

            // Send PaymentLinkSent to Kafka after successful Razorpay response
            .process(exchange -> {
                Map<String, Object> body = exchange.getMessage().getBody(Map.class);
                Map<String, Object> notes = (Map<String, Object>) body.get("notes");

                Map<String, Object> payload = new HashMap<>();
                payload.put("Amount", body.get("amount"));
                payload.put("PaymentlinkId", body.get("id"));
                payload.put("ReferenceId", body.get("reference_id"));
                payload.put("Status", body.get("status"));

                if (notes != null) {
                    payload.put("Cid", notes.get("CID"));
                    payload.put("HospitalCode", notes.get("shop_code"));
                    payload.put("Source", notes.get("source"));
                    payload.put("DepositTypeCode", notes.get("deposit_type_code"));
                }

                Map<String, Object> event = new HashMap<>();
                event.put("EventType", "PaymentLinkSent");
                event.put("EventEmittedAt", java.time.Instant.now().toString());
                event.put("EventData", payload);

                String eventJson = mapper.writeValueAsString(event);

                log.info("Sending JSON to Kafka: {}", eventJson);

                exchange.getContext().createProducerTemplate().sendBody("kafka:razorpaytopic", eventJson);
            })

            // Optional: Log structured output
            .process(new PaymentsLoggerProcessor())

            .marshal().json(); // return response to frontend
    }
}
