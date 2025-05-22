package org.noventiqvp.camelrazorpay;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.apache.camel.builder.RouteBuilder;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import java.util.Base64;

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

        from("platform-http:/create-payment-link?httpMethodRestrict=POST")
            .routeId("create-and-fetch-short-url")
            .log("Received payment creation request: ${body}")
            .setHeader("Content-Type", constant("application/json"))
            .setHeader("Authorization", constant(authHeader)) // Injected from config
            .removeHeaders("CamelHttp*")
            .to("https://api.razorpay.com/v1/payment_links?bridgeEndpoint=true")
            .log("Razorpay get response: ${body}")
            .unmarshal().json()

            .setBody().simple(
                "{\"reference_id\": \"${body[reference_id]}\", " +
                "\"plink_id\": \"${body[id]}\", " +
                "\"short_url\": \"${body[short_url]}\"}"
            );
    }

}
