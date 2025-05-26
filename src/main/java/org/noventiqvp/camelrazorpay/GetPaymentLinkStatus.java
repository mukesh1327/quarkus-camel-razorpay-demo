package org.noventiqvp.camelrazorpay;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.Base64;

@ApplicationScoped
public class GetPaymentLinkStatus extends RouteBuilder {

    @Inject
    @ConfigProperty(name = "razorpay.key")
    String razorpayKey;

    @Inject
    @ConfigProperty(name = "razorpay.secret")
    String razorpaySecret;

    @Override
    public void configure() throws Exception {

        // Basic Auth header
        String authHeader = "Basic " + Base64.getEncoder()
            .encodeToString((razorpayKey + ":" + razorpaySecret).getBytes());

        from("platform-http:/get-payment-link/{plink_id}?httpMethodRestrict=GET")
            .routeId("get-payment-link-for-status")
            .log("Fetching Razorpay payment link for plink_id: ${header.plink_id}")

            // Set headers for Razorpay API call
            .setHeader(Exchange.HTTP_METHOD, constant("GET"))
            .setHeader("Authorization", constant(authHeader))
            .setHeader(Exchange.CONTENT_TYPE, constant("application/json"))

            // Remove Camel internal headers that might interfere
            .removeHeaders("CamelHttp*")

            // Call Razorpay API with path parameter plink_id
            .toD("https://api.razorpay.com/v1/payment_links/${header.plink_id}?bridgeEndpoint=true")

            // Log the full response body
            .log("Razorpay payment link response: ${body}")

            // Unmarshal JSON so we can access specific fields
            .unmarshal().json()

            // Log selected fields in pretty format
            .process(new PaymentsLoggerProcessor())

            // Set HTTP response code to 200
            .setHeader(Exchange.HTTP_RESPONSE_CODE, constant(200))

            // Optional: marshal back to JSON if needed
            .marshal().json();
    }
}
