---
spec-version: 0.1
---

# Enrich warehouse order

This test covers streaming a warehouse order event and eniching it with a product name from an API

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
  "productId" : "PROD-1001",
  "qtyDelivered" : 45,
  "orderId" : "520c80f8-661e-48c7-8b4e-c9c36bd93785",
  "supplierId" : "SUREPOST",
  "timestamp" : "2026-02-26T05:54:25.719167197"
}
```

### getProduct
<!-- operation: ProductsApi@@getProduct -->

Response:
```json
{"productId":"PROD-1001","sku":"SKU-LAP001","productName":"Gaming Laptop Pro 15\"","category":"Electronics","storageLocation":"WAREHOUSE-A-02"}
```

## Expected Result

```json
{
  "productName" : "Gaming Laptop Pro 15\"",
  "productId" : "PROD-1001",
  "qtyDelivered" : 45,
  "orderId" : "520c80f8-661e-48c7-8b4e-c9c36bd93785",
  "supplierId" : "SUREPOST",
  "timestamp" : "26-Feb-2026 05:54:25"
}
```
