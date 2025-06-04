package org.noventiqvp.camelrazorpay;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.HashMap;
import java.util.Map;

import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.http.base.HttpOperationFailedException;
import org.apache.camel.model.dataformat.JsonLibrary;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@ApplicationScoped
public class CreateDeposit extends RouteBuilder {

    @Inject
    @ConfigProperty(name = "app.receipt.api.url")
    String receiptApiUrl;

    @Override
    @SuppressWarnings("unchecked")
    public void configure() throws Exception {

        // Handle 409 Conflict from receipt API (duplicate)
        onException(HttpOperationFailedException.class)
            .handled(true)
            .process(exchange -> {
                HttpOperationFailedException cause = exchange.getProperty(Exchange.EXCEPTION_CAUGHT, HttpOperationFailedException.class);
                if (cause != null && cause.getStatusCode() == 409) {
                    System.out.println("409 Conflict on /create-receipt: " + cause.getMessage());

                    Map<String, Object> requestBody = exchange.getProperty("depositRequestBody", Map.class);

                    Map<String, Object> duplicateEventData = new HashMap<>();
                    duplicateEventData.put("TransactionId", requestBody.get("transaction_id"));
                    duplicateEventData.put("cid", requestBody.get("cid"));
                    duplicateEventData.put("DepositAmount", requestBody.get("deposit_amount"));
                    duplicateEventData.put("DepositTypeCode", requestBody.get("deposit_type_code"));
                    duplicateEventData.put("Currency", requestBody.get("currency_code"));
                    duplicateEventData.put("Source", requestBody.get("source"));
                    duplicateEventData.put("Status", "duplicate");

                    Map<String, Object> eventEnvelope = new HashMap<>();
                    eventEnvelope.put("EventType", "DepositAlreadyExists");
                    eventEnvelope.put("EventEmittedAt", java.time.Instant.now().toString());
                    eventEnvelope.put("EventData", duplicateEventData);

                    exchange.getContext().createProducerTemplate()
                        .sendBody("kafka:depositinfotopic", exchange.getContext().getTypeConverter().convertTo(String.class, eventEnvelope));
                } else {
                    throw cause;
                }
            });

        from("kafka:razorpaytopic?groupId=create-deposit")
            .routeId("create-deposit-status")

            .log("Received PaymentStatusUpdated event: ${body}")

            .unmarshal().json(JsonLibrary.Jackson, Map.class)

            .process(exchange -> {
                var body = exchange.getIn().getBody(Map.class);
                var eventData = (Map<String, Object>) body.get("EventData");

                String status = (String) eventData.get("Status");

                if ("paid".equalsIgnoreCase(status)) {
                    Map<String, Object> requestBody = new HashMap<>();
                    requestBody.put("cid", eventData.get("Cid"));
                    requestBody.put("deposit_amount", eventData.get("Amount"));
                    requestBody.put("deposit_type_code", eventData.get("DepositTypeCode"));
                    requestBody.put("currency_code", "INR");
                    requestBody.put("transaction_id", eventData.get("PaymentId"));
                    requestBody.put("shop_code", eventData.get("ShopCode"));
                    requestBody.put("source", eventData.get("Source"));

                    exchange.setProperty("depositRequestBody", requestBody);

                    Map<String, Object> depositCreationStatusEvent = new HashMap<>();
                    depositCreationStatusEvent.put("cid", eventData.get("Cid"));
                    depositCreationStatusEvent.put("DepositAmount", eventData.get("Amount"));
                    depositCreationStatusEvent.put("DepositTypeCode", eventData.get("DepositTypeCode"));
                    depositCreationStatusEvent.put("Currency", "INR");
                    depositCreationStatusEvent.put("TransactionId", eventData.get("PaymentId"));
                    depositCreationStatusEvent.put("shop_code", eventData.get("ShopCode"));
                    depositCreationStatusEvent.put("Source", eventData.get("Source"));

                    Map<String, Object> eventEnvelope = new HashMap<>();
                    eventEnvelope.put("EventType", "DepositCreationStatus");
                    eventEnvelope.put("EventEmittedAt", java.time.Instant.now().toString());
                    eventEnvelope.put("EventData", depositCreationStatusEvent);

                    exchange.getContext().createProducerTemplate()
                        .sendBody("kafka:depositinfotopic", exchange.getContext().getTypeConverter().convertTo(String.class, eventEnvelope));

                    exchange.setProperty("createReceipt", true);
                } else {
                    System.err.println("[DepositCreation] Payment status is not paid, status: " + status);
                    exchange.setProperty("createReceipt", false);
                }
            })

            .choice()
                .when(exchangeProperty("createReceipt").isEqualTo(true))
                    .setHeader(Exchange.CONTENT_TYPE, constant("application/json"))
                    .setHeader("Accept", constant("application/json"))
                    .setBody(exchangeProperty("depositRequestBody"))
                    .marshal().json()
                    .setHeader(Exchange.HTTP_METHOD, constant("POST"))
                    .to("{{app.receipt.api.url}}")
                    .log("Create receipt response: ${body}")
                    .unmarshal().json()
                    .process(exchange -> {
                        var response = exchange.getIn().getBody(Map.class);
                        var requestBody = (Map<String, Object>) exchange.getProperty("depositRequestBody");

                        Map<String, Object> depositCreatedEvent = new HashMap<>();
                        depositCreatedEvent.put("cid", requestBody.get("cid"));
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
                            .sendBody("kafka:depositinfotopic", exchange.getContext().getTypeConverter().convertTo(String.class, eventEnvelope));
                    })
                .otherwise()
                    .log("Skipping deposit creation since status is not 'paid'");
    }
}
