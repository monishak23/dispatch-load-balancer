# Dispatch Load Balancer

A Spring Boot REST application that assigns delivery orders to a fleet of vehicles. It respects vehicle capacity, assigns HIGH priority orders first, and keeps total travel distance low. Distances are calculated with the Haversine formula.

- **Live URL:** `https://dispatch-load-balancer-production.up.railway.app/api/dispatch/plan`
- **Swagger UI:** `https://dispatch-load-balancer-production.up.railway.app/swagger-ui/index.html`

## Tech stack

- Java 17+ (developed on Java 18)
- Spring Boot 4, Spring Web, Spring Data JPA, Bean Validation
- MySQL 8
- springdoc-openapi (Swagger UI)
- Maven (wrapper included)

## How to run locally

### 1. Prerequisites

- JDK 17 or newer (`java -version`)
- MySQL 8 running locally

### 2. Create the database

```sql
CREATE DATABASE IF NOT EXISTS dispatch_db;
```

Tables (`orders`, `vehicles`) are created automatically on startup.

### 3. Set database credentials

The app reads these environment variables:

| Variable | Meaning | Default |
|---|---|---|
| `DB_USERNAME` | MySQL user | `root` |
| `DB_PASSWORD` | MySQL password | set in `application.properties` |

Linux / macOS / Git Bash:

```bash
export DB_USERNAME=root
export DB_PASSWORD=your_password
```

Windows PowerShell:

```powershell
$env:DB_USERNAME="root"
$env:DB_PASSWORD="your_password"
```

### 4. Start the application

```bash
./mvnw spring-boot:run
```

On Windows Command Prompt or PowerShell use `mvnw.cmd spring-boot:run`.

The app starts on **http://localhost:8081**. To change the port, edit `server.port` in `src/main/resources/application.properties`.

### 5. Build a jar (optional)

```bash
./mvnw clean package -DskipTests
java -jar target/dispatch-load-balancer-0.0.1-SNAPSHOT.jar
```

## API documentation

Open Swagger UI at **http://localhost:8081/swagger-ui.html**. The raw OpenAPI spec is at `/v3/api-docs`.

The flow is always the same: submit orders, submit vehicles, then request the plan.

### 1. Submit delivery orders

`POST /api/dispatch/orders`

The body is a JSON array of orders.

```json
[
  {
    "orderId": "ORD001",
    "latitude": 28.6139,
    "longitude": 77.2090,
    "address": "Connaught Place, Delhi, India",
    "packageWeight": 15,
    "priority": "HIGH"
  }
]
```

Response `200`:

```json
{ "message": "Delivery orders accepted.", "status": "success" }
```

### 2. Submit fleet details

`POST /api/dispatch/vehicles`

The body is a JSON array of vehicles.

```json
[
  {
    "vehicleId": "VEH001",
    "capacity": 100,
    "currentLatitude": 28.7041,
    "currentLongitude": 77.1025,
    "currentAddress": "Karol Bagh, Delhi, India"
  }
]
```

Response `200`:

```json
{ "message": "Vehicle details accepted.", "status": "success" }
```

### 3. Get the dispatch plan

`GET /api/dispatch/plan`

Runs the allocation on the stored orders and vehicles. The values below are illustrative; the shape is exact.

```json
{
  "dispatchPlan": [
    {
      "vehicleId": "VEH001",
      "totalLoad": 95.0,
      "totalDistance": "42.17 km",
      "assignedOrders": [
        {
          "orderId": "ORD007",
          "latitude": 28.7041,
          "longitude": 77.1025,
          "address": "Karol Bagh, Delhi, India",
          "packageWeight": 20.0,
          "priority": "HIGH"
        }
      ]
    }
  ],
  "unassignedOrders": [
    {
      "orderId": "ORD006",
      "latitude": 28.6139,
      "longitude": 77.2090,
      "address": "Connaught Place, Delhi, India",
      "packageWeight": 40.0,
      "priority": "LOW",
      "reason": "No vehicle has enough remaining capacity"
    }
  ]
}
```

- `assignedOrders` are listed in the order the vehicle should visit them.
- `totalDistance` is the length of that route in km, from the vehicle's current location through every stop.
- `unassignedOrders` lists every order that could not be placed, with the reason.

### Error handling

All errors use the same shape with an HTTP status of 400 for bad input:

```json
{ "message": "<what went wrong>", "status": "error" }
```

Handled cases:

- Missing or blank fields (`orderId`, `vehicleId`, `address`, and so on)
- `packageWeight` or `capacity` of zero or less
- Latitude outside -90 to 90, longitude outside -180 to 180
- Unknown priority (only `HIGH`, `MEDIUM`, `LOW` are accepted)
- Empty list, or a body that is not valid JSON
- Duplicate `orderId` or `vehicleId` inside one request
- Requesting the plan when no orders or no vehicles have been submitted
- Orders that cannot be assigned (reported in `unassignedOrders`, not as an error)

## How the algorithm works

The logic lives in `DispatchEngine` and has two phases.

**Phase 1: allocation (greedy)**

1. Sort orders by priority (HIGH, then MEDIUM, then LOW). Within the same priority, heavier orders come first, then by `orderId` so results are repeatable.
2. For each order, look at the vehicles that still have enough spare capacity.
3. Choose the one closest to the order, measured from that vehicle's last assigned stop (or from its starting location if it has none yet). This keeps nearby drops on the same vehicle.
4. If no vehicle can take the order, add it to `unassignedOrders` with a reason: either it is heavier than the largest vehicle, or no vehicle has enough room left.

**Phase 2: routing (nearest-neighbor)**

For each vehicle, reorder its assigned stops: start at the vehicle's current location, always drive to the closest unvisited stop next, and add up the Haversine distances along the way.

**Distance:** the Haversine formula gives the great-circle distance in km between two latitude/longitude points (Earth radius 6371 km).

**Complexity** (n orders, m vehicles, k stops on one vehicle)

- Sorting: O(n log n)
- Allocation: O(n x m)
- Routing: O(k^2) per vehicle

## Assumptions and limitations

- Weights and capacities are in the same unit (treated as kg).
- Distances are straight-line (Haversine), not road distances.
- Vehicles do not return to their starting point after the last delivery.
- Priority decides who gets space on a vehicle first. It does not decide the visiting order within a route, which is based on distance.
- Orders and vehicles are stored in MySQL. Sending an existing `orderId` or `vehicleId` again **updates** that record (upsert) instead of creating a duplicate.
- Stored data stays until it is overwritten. The plan is calculated on demand and is not saved. There is no delete endpoint, so to start fresh clear the `orders` and `vehicles` tables.
- A vehicle with no assigned orders still appears in the plan with load `0.0` and distance `0.00 km`.
- The response includes an extra `unassignedOrders` list that is not in the sample response, to make unassignable orders visible.
- The algorithm is a heuristic. It gives a good, fast answer but not a mathematically optimal one (the exact problem is the NP-hard Vehicle Routing Problem).

### Note on the sample data

The 30 sample orders weigh 645 kg in total, while the 5 sample vehicles carry 500 kg combined. At least 145 kg of orders therefore cannot be assigned, and these are expected to be LOW priority orders, listed under `unassignedOrders`.

## Project structure

```
src/main/java/com/freightfox/dispatch_load_balancer
├── controller      REST endpoints
├── service         order, vehicle and dispatch services
├── dispatchengine  allocation and routing algorithm
├── model           JPA entities (Order, Vehicle)
├── repository      Spring Data repositories
├── dto             request and response objects
├── exception       custom exception and global error handler
└── util            Haversine calculator
```
