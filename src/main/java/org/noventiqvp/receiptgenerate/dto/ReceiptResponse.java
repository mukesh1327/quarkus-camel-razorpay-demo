package org.noventiqvp.receiptgenerate.dto;


public class ReceiptResponse {
    public String receipt_id;
    public String status;

    public ReceiptResponse(String receipt_id, String status) {
        this.receipt_id = receipt_id;
        this.status = status;
    }
}