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

Test with curl (can be imported in postman)

```shell script
curl --location 'https://localhost:8080/create-payment-link' \
--header 'Content-Type: application/json' \
--data-raw '{
  "amount": 1000,
  "currency": "INR",
  "accept_partial": true,
  "first_min_partial_amount": 100,
  "expire_by": 1747714681,
  "reference_id": "TSsd1989",
  "description": "Payment for policy no #23456",
  "customer": {
    "name": "Gaurav Kumar",
    "contact": "+919000090000",
    "email": "gaurav.kumar@example.com"
  },
  "notify": {
    "sms": true,
    "email": true
  },
  "reminder_enable": true,
  "notes": {
    "policy_name": "Jeevan Bima"
  },
  "callback_url": "https://example-callback-url.com/",
  "callback_method": "get"
}'
```
