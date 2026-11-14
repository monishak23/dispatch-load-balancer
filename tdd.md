# Technical Design Document: Dispatch Load Balancer

| | |
|---|---|
| Author | Monisha K |
| Task | Dispatch Load Balancer |
| Stack | Java, Spring Boot, MySQL |
| Live URL | https://dispatch-load-balancer-production.up.railway.app |
| Swagger | https://dispatch-load-balancer-production.up.railway.app/swagger-ui/index.html |

## 1. Purpose

Delivery orders have to be assigned to a fleet of vehicles. The assignment must respect each vehicle's capacity, handle HIGH priority orders first, and keep the total travelled distance low. This service accepts orders and vehicles over REST, stores them, and returns a dispatch plan on request.

## 2. Requirements mapping

| Requirement | Where it is handled |
|---|---|
| Order and vehicle input with all required fields | `OrderRequest`, `VehicleRequest` DTOs with Bean Validation |
| Total load within capacity | `DispatchEngine` (`VehicleLoad.canFit`) |
| High priority first | Orders sorted by the `Priority` enum order before allocation |
| Minimise total distance | Nearest vehicle choice, then nearest-neighbor stop ordering |
| Haversine formula | `HaversineCalculator` |
| Plan with load, distance, addresses | `DispatchPlanResponse` |
| Invalid input and edge cases | `GlobalExceptionHandler`, `unassignedOrders` list |
| Efficient database operations | Upsert through primary keys and `saveAll` |
| API documentation | Swagger UI and README |

## 3. Flow Diagram

<img width="1106" height="618" alt="image" src="https://github.com/user-attachments/assets/35863b43-4cb1-4cac-abb0-e112a74aac9c" />


## 4. Data model

**orders**

| Column | Type | Notes |
|---|---|---|
| `order_id` | varchar(50) | Primary key |
| `latitude`, `longitude` | double | |
| `address` | varchar | |
| `package_weight` | double | |
| `priority` | varchar(10) | `HIGH`, `MEDIUM`, `LOW` stored as text |

**vehicles**

| Column | Type | Notes |
|---|---|---|
| `vehicle_id` | varchar(50) | Primary key |
| `capacity` | double | |
| `current_latitude`, `current_longitude` | double | |
| `current_address` | varchar | |

The order table is named `orders` because `order` is a reserved word in MySQL. Tables are created by Hibernate (`ddl-auto=update`). A migration tool such as Flyway would be used in production.

### Upsert behaviour

The business ID is the primary key, so `saveAll` updates a row when the ID exists and inserts it otherwise. Re-sending an order or vehicle never creates a duplicate. Duplicate IDs inside a single request are rejected with HTTP 400.

## 5. API design

| Method | Path | Body | Success |
|---|---|---|---|
| POST | `/api/dispatch/orders` | JSON array of orders | 200, `{"message": "Delivery orders accepted.", "status": "success"}` |
| POST | `/api/dispatch/vehicles` | JSON array of vehicles | 200, `{"message": "Vehicle details accepted.", "status": "success"}` |
| GET | `/api/dispatch/plan` | none | 200, `dispatchPlan` and `unassignedOrders` |

The request body is a raw JSON array, matching the sample input in the assignment.

### Plan response

- `dispatchPlan`: one entry per vehicle, with `vehicleId`, `totalLoad`, `totalDistance` (text such as `"42.17 km"`), and `assignedOrders` in visiting order.
- `unassignedOrders`: each unplaced order with a `reason`. This list is an addition to the sample response and makes the edge case visible.

### Error model

Every error uses the same shape:

```json
{ "message": "<what went wrong>", "status": "error" }
```

| Situation | Handler | HTTP status |
|---|---|---|
| Field validation fails (missing, blank, non-positive weight, latitude or longitude out of range, empty list) | `ConstraintViolationException` or `MethodArgumentNotValidException` | 400 |
| Malformed JSON, wrong field type, unknown priority | `HttpMessageNotReadableException` | 400 |
| Duplicate ID in one request, or plan requested with no orders or vehicles | `InvalidInputException` | 400 |
| Anything unexpected | Generic handler (logged) | 500 |

## 6. Algorithm

### 6.1 Distance

The Haversine formula gives the great-circle distance between two latitude and longitude points:

```
a = sin^2(dLat / 2) + cos(lat1) * cos(lat2) * sin^2(dLon / 2)
c = 2 * atan2(sqrt(a), sqrt(1 - a))
distance = 6371 km * c
```

`a` is capped at 1.0 to stop floating-point rounding from producing `NaN` for opposite points. The method signature is `distanceKm(lat1, lat2, lon1, lon2)`.

### 6.2 Phase 1: allocation (greedy)

```
sort vehicles by vehicleId
sort orders by priority (HIGH, MEDIUM, LOW), then weight descending, then orderId

for each order in sorted order:
    candidates = vehicles where currentLoad + weight <= capacity
    if candidates is empty:
        add order to unassigned with a reason
    else:
        pick the candidate nearest to the order,
        measured from that vehicle's last assigned stop
        (or its starting location if it has no stops yet)
        assign the order, add its weight to the load, move the vehicle's "last position"
```

Reasons recorded for unassigned orders:

- the package is heavier than the largest vehicle's capacity, or
- no vehicle has enough remaining capacity.

Design choices:

- Sorting by priority is what guarantees HIGH orders get space first.
- Heavier first within a priority reduces cases where a large parcel cannot fit later.
- Measuring from the vehicle's last stop keeps nearby drops on one vehicle.
- The final `orderId` tie-break and the sorted vehicle list make the output repeatable.
- A small epsilon (1e-9) in the capacity check protects against floating-point error.

### 6.3 Phase 2: routing (nearest-neighbor)

For each vehicle, start at its current location. Repeatedly move to the closest unvisited stop, summing the Haversine distance of each leg. The vehicle does not return to its start.

**Worked example.** A vehicle at position 0 has stops at 10, 2 and 11 (units on a line).

- Visiting in the given order: 10 + 8 + 9 = 27.
- Nearest-neighbor (2, then 10, then 11): 2 + 8 + 1 = 11.

## 7. Testing

| Test class | What it covers | Needs a database |
|---|---|---|
| `HaversineCalculatorTest` | Known distances, same point, opposite points, date line, symmetry. Written first, before the implementation | No |
| `OrderServiceTest`, `VehicleServiceTest` | Mapping to entities, duplicate ID rejection (repository mocked) | No |
| `DispatchEngineTest` | Priority order, capacity limits, nearest vehicle, route ordering and distance, unassigned reasons, repeatable output, the assignment's 30 order sample data, a 5000 order performance check | No |

Run them with `./mvnw test -Dtest=DispatchEngineTest` (or the other class names). Controller-level and integration tests are not included and are listed under future work.

## 8. Deployment

- The `Dockerfile` has two stages: Maven builds the jar, then a slim Java 21 runtime image runs it.
- Hosted on Railway, built from the GitHub repository, with a Railway MySQL service.
- The database connection comes from environment variables (`SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD`, built from the MySQL service's variables). No credentials are stored in the code.
- `server.port` reads the `PORT` variable that Railway supplies.
- Railway terminates HTTPS and forwards plain HTTP. `server.forward-headers-strategy=framework` makes Swagger use the correct `https` address.
- API documentation is generated by springdoc-openapi at `/swagger-ui/index.html`.

