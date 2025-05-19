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
            .log("Razorpay create response: ${body}")
            .unmarshal().json()

            .setHeader("payment_id", simple("${body[id]}"))
            .setHeader("reference_id", simple("${body[reference_id]}"))

            .setBody(constant((Object) null))
            .removeHeaders("CamelHttp*")
            .setHeader("Authorization", constant(authHeader))
            .setHeader("CamelHttpMethod", constant("GET"))
            .toD("https://api.razorpay.com/v1/payment_links/${header.payment_id}?bridgeEndpoint=true")
            .log("Razorpay get response: ${body}")
            .unmarshal().json()

            .setBody().simple(
                "{\"reference_id\": \"${header.reference_id}\", " +
                "\"payment_id\": \"${header.payment_id}\", " +
                "\"short_url\": \"${body[short_url]}\"}"
            );
    }

}


// public class SendPayLink extends RouteBuilder {

//     @Override
//     public void configure() throws Exception {

//         // Unified POST: Create payment link and fetch short_url
//         from("platform-http:/create-payment-link?httpMethodRestrict=POST")
//         .routeId("create-and-fetch-short-url")
//         .log("Received payment creation request: ${body}")
//         .setHeader("Content-Type", constant("application/json"))
//         .setHeader("Authorization", constant("Basic cnpwX3Rlc3RfbTdERXBkeE01YUNHWks6dk1oVWlGelUxNHVCSmVGRFl6Y09OOVJHCg=="))
//         .removeHeaders("CamelHttp*")
//         .to("https://api.razorpay.com/v1/payment_links?bridgeEndpoint=true")
//         .log("Razorpay create response: ${body}")
//         .unmarshal().json()
    
//         // Store needed fields
//         .setHeader("payment_id", simple("${body[id]}"))
//         .setHeader("reference_id", simple("${body[reference_id]}"))
    
//         // Fix: Reset body before GET request
//         .setBody(constant((Object) null))
//         .removeHeaders("CamelHttp*") // Optional again
//         .setHeader("Authorization", constant("Basic cnpwX3Rlc3RfbTdERXBkeE01YUNHWks6dk1oVWlGelUxNHVCSmVGRFl6Y09OOVJHCg=="))
//         .setHeader("CamelHttpMethod", constant("GET"))
    
//         .toD("https://api.razorpay.com/v1/payment_links/${header.payment_id}?bridgeEndpoint=true")  // or use .recipientList(simple("https://api.razorpay.com/v1/payment_links/${header.payment_id}?bridgeEndpoint=true"))
//         .log("Razorpay get response: ${body}")
//         .unmarshal().json()
    
//         .setBody().simple(
//             "{\"reference_id\": \"${header.reference_id}\", " +
//             "\"payment_id\": \"${header.payment_id}\", " +
//             "\"short_url\": \"${body[short_url]}\"}"
//         );
    
//     }
// }
