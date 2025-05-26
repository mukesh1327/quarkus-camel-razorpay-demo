# Quarkus - RazorPay demo

## Run in local

Add the API key and secret for razorpay in either application.properties or in system environment variables

```shell script
# To add in system env vars (Get from RazorPay test environments)
export RAZORPAY_KEY=key
export RAZORPAY_SECRET=secret
```

Run quarkus in dev environment

```shell script
./mvnw quarkus:dev
```

## Test with curl (can be imported in postman)


**Class SendPaymentLink**

```shell script
curl --location 'https://localhost:8080/create-payment-link' \
--header 'Content-Type: application/json' \
--data-raw '{
{
    "amount": 1000,
    "currency": "INR",
    "accept_partial": false,
    "expire_by": 1748410206,
    "reference_id": "280007",
    "description": "MYDEMO",
    "customer": {
        "name": "Gaurav Kumar",
        "contact": "+919000090000",
        "email": "gaurav.kumar@example.com"
    },
    "notify": {
        "sms": true,
        "email": false
    },
    "notes": {
        "CID": "SHP12199",
        "shop_code": "MHB",
        "deposit_type_code": "MPay",
        "source":"MobileAPP"
    },
    "callback_url": "https://example-callback-url.com/",
    "callback_method": "get"
}'
```

Reference ID must be unique  

Using short URL (provided in response) make a payment in razorpay test mode with test values.  

Test payment examples values for credit or debit card payments and OTP are all available [RazorPay Official documents](https://razorpay.com/docs/payments/payments/test-card-details/)  


**Class GetPaymentLinkStatus**

```shell script
curl --location 'https://localhost:8080/get-payment-link/${PLINK_ID}'
```
 
This plink_id is got from the response of /create-payment-link endpoint
