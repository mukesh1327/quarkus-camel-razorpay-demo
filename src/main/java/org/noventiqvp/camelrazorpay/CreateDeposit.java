package org.noventiqvp.camelrazorpay;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import java.util.HashMap;
import java.util.Map;

@ApplicationScoped
public class CreateDeposit extends RouteBuilder {

    @Inject
    @ConfigProperty(name = "app.receipt.api.url")
    String receiptApiUrl; // e.g. http://localhost:8080/create-receipt

    @Override
    @SuppressWarnings("unchecked")
    public void configure() throws Exception {

        from("kafka:razorpaydemo?groupId=create-deposit-group")
        .routeId("create-deposit-from-payment-status")
        .log("Received PaymentStatusUpdated event: ${body}")
        .unmarshal().json()
        .process(exchange -> {
            Map<String, Object> body = exchange.getIn().getBody(Map.class);
            Map<String, Object> eventData = (Map<String, Object>) body.get("EventData");
            if ("paid".equalsIgnoreCase((String) eventData.get("Status"))) {
                Map<String, Object> requestBody = new HashMap<>();
                requestBody.put("urn", eventData.get("Uhid"));
                requestBody.put("deposit_amount", eventData.get("Amount"));
                requestBody.put("deposit_type_code", eventData.get("DepositTypeCode"));
                requestBody.put("currency_code", "INR");
                requestBody.put("transaction_id", eventData.get("PaymentId"));
                requestBody.put("hosp_code", eventData.get("HospitalCode"));
                requestBody.put("source", eventData.get("Source"));
                exchange.setProperty("depositRequestBody", requestBody);
                exchange.setProperty("createReceipt", true);
            } else {
                exchange.setProperty("createReceipt", false);
            }
        })
        .choice()
            .when(exchangeProperty("createReceipt").isEqualTo(true))
                .setBody(exchangeProperty("depositRequestBody"))
                .setHeader(Exchange.CONTENT_TYPE, constant("application/json"))
                .setHeader("Accept", constant("application/json"))
                .setHeader(Exchange.HTTP_METHOD, constant("POST"))
                .marshal().json()
                .to("{{app.receipt.api.url}}")
                .log("Create receipt response: ${body}")
                .unmarshal().json()
                .process(exchange -> {
                    Map<String, Object> response = exchange.getIn().getBody(Map.class);
                    Map<String, Object> requestBody = (Map<String, Object>) exchange.getProperty("depositRequestBody");
                    Map<String, Object> depositCreatedEvent = new HashMap<>();
                    depositCreatedEvent.put("URN", requestBody.get("urn"));
                    depositCreatedEvent.put("DepositAmount", requestBody.get("deposit_amount"));
                    depositCreatedEvent.put("DepositTypeCode", requestBody.get("deposit_type_code"));
                    depositCreatedEvent.put("Currency", requestBody.get("currency_code"));
                    depositCreatedEvent.put("TransactionId", requestBody.get("transaction_id"));
                    depositCreatedEvent.put("HospitalCode", requestBody.get("hosp_code"));
                    depositCreatedEvent.put("Source", requestBody.get("source"));
                    depositCreatedEvent.put("RecieptId", response.get("receipt_id"));
                    depositCreatedEvent.put("Status", response.get("status"));
                    Map<String, Object> eventEnvelope = new HashMap<>();
                    eventEnvelope.put("EventType", "DepositCreated");
                    eventEnvelope.put("EventEmittedAt", java.time.Instant.now().toString());
                    eventEnvelope.put("EventData", depositCreatedEvent);
                    exchange.getContext().createProducerTemplate()
                        .sendBody("kafka:depositinfo", exchange.getContext().getTypeConverter().convertTo(String.class, eventEnvelope));
                })
            .otherwise()
                .log("Skipping deposit creation since status is not 'paid'");
    }
}
