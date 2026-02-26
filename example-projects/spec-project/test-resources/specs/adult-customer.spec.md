---
spec-version: 0.1
---

# Adult customer is identified correctly

Verifies that a customer over 18 is correctly identified as an adult.

## Query

```taxiql
find { Customer( CustomerId == "C-100" ) } as CustomerProfile
```

## Data Sources

### Customer API
<!-- operation: getCustomer -->

Response:
```json
{ "id": "C-100", "name": "Alice Smith", "age": 30 }
```

## Expected Result

```json
{ "id": "C-100", "name": "Alice Smith", "age": 30, "isAdult": true }
```
