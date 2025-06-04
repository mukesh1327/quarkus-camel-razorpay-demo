package org.noventiqvp.receiptgenerate;

import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.noventiqvp.receiptgenerate.dto.ReceiptRequest;
import org.noventiqvp.receiptgenerate.dto.ReceiptResponse;

import java.time.Year;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Path("/create-receipt")
public class ReceiptResource {

    // In-memory set to track used transaction IDs
    private static final Set<String> usedTransactionIds = ConcurrentHashMap.newKeySet();

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response createReceipt(ReceiptRequest request) {
        if (request.transaction_id == null || request.transaction_id.isBlank()) {
            return Response.status(Response.Status.BAD_REQUEST)
                .entity("{\"error\": \"Transaction ID is required.\"}")
                .build();
        }

        // Check for duplicate transaction ID
        if (usedTransactionIds.contains(request.transaction_id)) {
            return Response.status(Response.Status.CONFLICT)
                .entity("{\"error\": \"Duplicate transaction_id. Receipt already generated.\"}")
                .build();
        }

        // Generate receipt ID
        String hospCode = request.shop_code != null ? request.shop_code : "XXX";
        String financialYear = getFinancialYear();
        String receiptNumber = String.format("%04d", new Random().nextInt(10000));
        String receiptId = String.format("%s/%s/DP/%s", hospCode, financialYear, receiptNumber);

        // Store transaction ID
        usedTransactionIds.add(request.transaction_id);

        ReceiptResponse response = new ReceiptResponse(receiptId, "Success");
        return Response.ok(response).build();
    }

    private String getFinancialYear() {
        int year = Year.now().getValue();
        return String.format("%02d-%02d", year % 100, (year + 1) % 100);
    }
}
