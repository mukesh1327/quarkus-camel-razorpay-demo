package org.noventiqvp.camelrazorpay;

import jakarta.enterprise.context.ApplicationScoped;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.model.dataformat.JsonLibrary;

import java.util.Map;

@ApplicationScoped
public class RazorpayWebhookListener extends RouteBuilder {

    @Override
    @SuppressWarnings("unchecked")
    public void configure() throws Exception {

        from("platform-http:/payment-status?httpMethodRestrict=POST")
            .routeId("razorpay-webhook-listener")
            .log("Received Razorpay webhook: ${body}")
            .unmarshal().json(JsonLibrary.Jackson, Map.class)

            // Optionally verify signature (see below)
            .process(exchange -> {
                var json = exchange.getIn().getBody(Map.class);
                String event = (String) json.get("event");
                Map<String, Object> payload = (Map<String, Object>) json.get("payload");
            
                switch (event) {
                    case "payment_link.paid" -> {
                        Map<String, Object> plink = (Map<String, Object>) payload.get("payment_link");
                        String referenceId = (String) plink.get("reference_id");
                        String plinkId = (String) plink.get("id");
                        System.out.println("Payment completed: " + referenceId + " (plink: " + plinkId + ")");
                    }
                    case "payment_link.expired" -> {
                        Map<String, Object> plink = (Map<String, Object>) payload.get("payment_link");
                        System.out.println("Payment link expired: " + plink.get("id"));
                    }
                    case "payment_link.cancelled" -> {
                        Map<String, Object> plink = (Map<String, Object>) payload.get("payment_link");
                        System.out.println("Payment link cancelled: " + plink.get("id"));
                    }
                    case "payment.captured" -> {
                        Map<String, Object> payment = (Map<String, Object>) payload.get("payment");
                        System.out.println("Payment captured: " + payment.get("id"));
                    }
                    case "payment.failed" -> {
                        Map<String, Object> payment = (Map<String, Object>) payload.get("payment");
                        System.out.println("Payment failed: " + payment.get("id"));
                    }
                    case "payment.dispute.created" -> {
                        System.out.println("Dispute raised on a payment");
                    }
                    // Add other cases if needed...
                    default -> {
                        System.out.println("Unhandled event: " + event);
                    }
                }
            })
            .marshal().json(JsonLibrary.Jackson)
            .log("Returning processed JSON: ${body}");
    }
}
