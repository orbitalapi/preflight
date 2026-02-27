---
spec-version: 0.1
---

# Enrich warehouse order

This test covers streaming a warehouse order event and enriching it with a product name from an API

## Query

```taxiql
stream { SurePostDeliveryEvent } as {
    productName: ProductName
    ...
}[]

```

## Data Sources

### surePostDeliveryEvents
<!-- operation: SurePostKafkaService@@surePostDeliveryEvents, mode: stream -->

Message:
```json
{
  "productId" : "PROD-1013",
  "qtyDelivered" : 72,
  "orderId" : "a29788b5-6b7a-4c30-8e9c-775c1341fef8",
  "supplierId" : "SUREPOST",
  "timestamp" : "2026-02-27T08:21:37.136364734"
}
```

Message:
```json
{
  "productId" : "PROD-1003",
  "qtyDelivered" : 20,
  "orderId" : "e69e51a4-85e6-4fe6-90a8-4faec726e316",
  "supplierId" : "SUREPOST",
  "timestamp" : "2026-02-27T08:21:39.136643659"
}
```

Message:
```json
{
  "productId" : "PROD-1003",
  "qtyDelivered" : 26,
  "orderId" : "5e3ea02a-9eaf-45c9-ab66-9150d5aeb981",
  "supplierId" : "SUREPOST",
  "timestamp" : "2026-02-27T08:21:41.136906122"
}
```

### getProduct
<!-- operation: ProductsApi@@getProduct -->

Request:
```json
{
  "productId" : "PROD-1013"
}
```

Response:
```json
{"productId":"PROD-1013","sku":"SKU-WEB013","productName":"HD Webcam with Microphone","category":"Electronics","storageLocation":"WAREHOUSE-A-02"}
```

### getProduct
<!-- operation: ProductsApi@@getProduct -->

Request:
```json
{
  "productId" : "PROD-1003"
}
```

Response:
```json
{"productId":"PROD-1003","sku":"SKU-KEY003","productName":"Mechanical Keyboard RGB","category":"Accessories","storageLocation":"WAREHOUSE-B-01"}
```

## Expected Result

```json
{
  "productName" : "HD Webcam with Microphone",
  "productId" : "PROD-1013",
  "qtyDelivered" : 72,
  "orderId" : "a29788b5-6b7a-4c30-8e9c-775c1341fef8",
  "supplierId" : "SUREPOST",
  "timestamp" : "27-Feb-2026 08:21:37"
}
```