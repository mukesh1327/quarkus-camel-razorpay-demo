package org.noventiqvp.camelrazorpay;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;

import java.util.List;
import java.util.Map;

public class PaymentsLoggerProcessor implements Processor {

    @Override
    @SuppressWarnings("unchecked")
    public void process(Exchange exchange) throws Exception {
        Map<String, Object> body = exchange.getMessage().getBody(Map.class);

        StringBuilder logBuilder = new StringBuilder("[Razorpay Payments Log]\n");

        logBuilder.append("reference_id: ").append(json(body.get("reference_id"))).append("\n");
        logBuilder.append("plink_id: ").append(json(body.get("id"))).append("\n");
        logBuilder.append("short_url: ").append(json(body.get("short_url"))).append("\n");
        
        Map<String, Object> notes = (Map<String, Object>) body.get("notes");
        if (notes != null) {
            logBuilder.append("Cid: ").append(json(notes.get("CID"))).append("\n");
            logBuilder.append("shop_code: ").append(json(notes.get("shop_code"))).append("\n");
            logBuilder.append("deposit_type_code: ").append(json(notes.get("deposit_type_code"))).append("\n");
            logBuilder.append("source: ").append(json(notes.get("source"))).append("\n");
        }
        
        List<Map<String, Object>> payments = (List<Map<String, Object>>) body.get("payments");
        if (payments != null && !payments.isEmpty()) {
            int index = 0;
            for (Map<String, Object> payment : payments) {
                String prefix = payments.size() > 1 ? "payments[" + index + "]_" : "payments_";
                logBuilder.append(prefix).append("amount: ").append(json(payment.get("amount"))).append("\n");
                logBuilder.append(prefix).append("createdAt: ").append(json(payment.get("created_at"))).append("\n");
                logBuilder.append(prefix).append("method: ").append(json(payment.get("method"))).append("\n");
                logBuilder.append(prefix).append("paymentId: ").append(json(payment.get("payment_id"))).append("\n");
                logBuilder.append(prefix).append("status: ").append(json(payment.get("status"))).append("\n\n");
                index++;
            }
        } else {
            logBuilder.append("payments: []\n");
        }
        
        // Finally log the output
        System.out.println(logBuilder.toString());
    }

    private String json(Object val) {
        if (val == null) return "null";
        return "\"" + val.toString().replace("\"", "\\\"") + "\"";
    }
}
