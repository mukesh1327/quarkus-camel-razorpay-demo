package org.noventiqvp.camelrazorpay;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.apache.camel.builder.RouteBuilder;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import java.util.Base64;
import org.apache.camel.Exchange;

@ApplicationScoped
public class SendPayLink extends RouteBuilder {

    @Inject
    @ConfigProperty(name = "razorpay.key")
    String razorpayKey;

    @Inject
    @ConfigProperty(name = "razorpay.secret")
    String razorpaySecret;

    @Override
    public void configure() throws Exception {

        // Base64 encode at runtime
        String authHeader = "Basic " + Base64.getEncoder()
            .encodeToString((razorpayKey + ":" + razorpaySecret).getBytes());

        // Razorpay error logging
        onException(org.apache.camel.http.base.HttpOperationFailedException.class)
            .handled(true)
            .process(exchange -> {
                org.apache.camel.http.base.HttpOperationFailedException cause =
                    exchange.getProperty(Exchange.EXCEPTION_CAUGHT, org.apache.camel.http.base.HttpOperationFailedException.class);

                String errorBody = cause.getResponseBody();
                int statusCode = cause.getStatusCode();

                System.err.println("[RAZORPAY ERROR] HTTP " + statusCode + ": " + errorBody);

                exchange.getMessage().setHeader(Exchange.CONTENT_TYPE, "application/json");
                exchange.getMessage().setBody(
                    "{\"error\":\"Razorpay error: " + errorBody.replace("\"", "\\\"") + "\"}"
                );
                exchange.getMessage().setHeader(Exchange.HTTP_RESPONSE_CODE, statusCode);
            });

        from("platform-http:/create-payment-link?httpMethodRestrict=POST")
            .routeId("create-and-fetch-payment-link")
            .log("Received payment creation request: ${body}")
            .setHeader("Content-Type", constant("application/json"))
            .setHeader("Authorization", constant(authHeader)) // Injected from config
            .removeHeaders("CamelHttp*")
            .to("https://api.razorpay.com/v1/payment_links?bridgeEndpoint=true")
            // Log full raw response from Razorpay (as String)
            .log("Raw Razorpay response: ${body}")

            // Unmarshal JSON to Map so we can access fields easily
            .unmarshal().json()

            // Log the extracted subset fields for debug
            .process(new PaymentsLoggerProcessor())

            // Set body to full original Razorpay response (as JSON string)
            .marshal().json()  // convert back to JSON string for response
            ;
    }

}
