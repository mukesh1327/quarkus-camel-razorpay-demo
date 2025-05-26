# Quarkus - RazorPay demo


## Deploy kafka in Openshift

1. Install Red Hat build of AMQ streams operator from the OperatorHub in Openshift

Use the kafka-kraft [yaml](k8s-manifests/kafka-configs/kafka-kraft.yaml) to deploy kafka with kraft  
or use kafka-zookeeper [yaml](k8s-manifests/kafka-configs/kafka-zookeeper.yaml) to deploy kafka with zookeeper  

```shell script
oc apply -f ./k8s-manifests/kafka-configs/kafka-kraft.yaml

# If kraft is used dont create zookeeper type

oc apply -f ./k8s-manifests/kafka-configs/kafka-zookeeper.yaml
```

2. Install Red Hat build of AMQ streams console operator from OperatorHub in Openshift

Use the kafka-console [yaml](k8s-manifests/kafka-configs/kafka-console.yaml) to deploy kafka 

```shell script
oc apply -f k8s-manifests/kafka-configs/kafka-console.yaml
```

## Run code in devworkspace

To run the code in Openshift devworkspace,  
Install the Openshift devspaces operator (This also installs devworkspace operator) from the OperatorHub.

Once install create a devspace cluster resource by deploying the devspaces [yaml](k8s-manifests/devworkspace/devspaces.yaml)  

```shell script
oc apply -f k8s-manifests/devworkspace/devspaces.yaml
```

After devspaces components are up and running, create the devworkspace by deploying with the [yaml](k8s-manifests/devworkspace/devworkspace.yaml)

```shell script
oc apply -f k8s-manifests/devworkspace/devworkspace.yaml
```

## Run in local

If code is running in local use route/ingress URL for kafka if deployed in openshift. Or else run the kafka in local and change the URL accordingly.  

Add the API key and secret for razorpay in either application.properties or in system environment variables

```shell script
# To add in system env vars (Get from RazorPay test environments) or use .env
export RAZORPAY_KEY=key
export RAZORPAY_SECRET=secret
export KAFKA_BOOTSTRAP=kafka-bootstrap-url
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

## Build container image for this application (podman is used)

```shell script
./mvnw clean package

# To build quarkus in native mode (any one is fine)

./mvnw package -Pnative -Dquarkus.native.container-build=true -Dquarkus.native.container-runtime=podman
```

```shell script
podman build -t quay.io/quayuser/quarkus-camel-razorpay-demo:latest -f ./src/main/docker/Dockerfile.jvm .

# To build in native mode (any one is fine)

podman build -t quay.io/quayuser/quarkus-camel-razorpay-demo:latest -f ./src/main/docker/Dockerfile.native . 
# or use Docker.native-micro
```

```shell script
podman push quay.io/quayuser/quarkus-camel-razorpay-demo:latest
```

## Deploy in openshift / K8s

To deploy in openshift 

```shell script with [route](k8s-manifests/quarkus-camel-razorpay/route.yaml)
oc apply -k k8s-manifests/quarkus-camel-razorpay/with-route
```
