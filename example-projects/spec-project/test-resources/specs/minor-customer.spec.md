---
spec-version: 0.1
---

# Minor customer is identified correctly

Verifies that a customer under 18 is correctly identified as not an adult.

## Query

```taxiql
find { Customer( CustomerId == "C-200" ) } as CustomerProfile
```

## Data Sources

### Customer API
<!-- operation: getCustomer -->

Response:
```json
{ "id": "C-200", "name": "Bob Junior", "age": 12 }
```

## Expected Result

```json
{ "id": "C-200", "name": "Bob Junior", "age": 12, "isAdult": false }
```
