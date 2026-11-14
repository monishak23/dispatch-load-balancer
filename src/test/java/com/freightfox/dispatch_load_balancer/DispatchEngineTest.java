package com.freightfox.dispatch_load_balancer.dispatchengine;

import com.freightfox.dispatch_load_balancer.dto.DispatchPlanResponse;
import com.freightfox.dispatch_load_balancer.dto.DispatchPlanResponse.AssignedOrder;
import com.freightfox.dispatch_load_balancer.dto.DispatchPlanResponse.UnassignedOrder;
import com.freightfox.dispatch_load_balancer.dto.DispatchPlanResponse.VehiclePlan;
import com.freightfox.dispatch_load_balancer.model.Order;
import com.freightfox.dispatch_load_balancer.model.Order.Priority;
import com.freightfox.dispatch_load_balancer.model.Vehicle;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.junit.jupiter.api.Assertions.assertTimeout;

class DispatchEngineTest {

    private final DispatchEngine engine = new DispatchEngine();

    // ---------- helpers ----------

    private Order order(String id, double lat, double lon, double weight, Priority priority) {
        return new Order(id, lat, lon, "Address of " + id, weight, priority);
    }

    private Vehicle vehicle(String id, double capacity, double lat, double lon) {
        return new Vehicle(id, capacity, lat, lon, "Address of " + id);
    }

    private VehiclePlan planFor(DispatchPlanResponse response, String vehicleId) {
        return response.dispatchPlan().stream()
                .filter(p -> p.vehicleId().equals(vehicleId))
                .findFirst()
                .orElseThrow();
    }

    private List<String> assignedIds(VehiclePlan plan) {
        return plan.assignedOrders().stream().map(AssignedOrder::orderId).toList();
    }

    private List<String> unassignedIds(DispatchPlanResponse response) {
        return response.unassignedOrders().stream().map(UnassignedOrder::orderId).toList();
    }

    private double km(String distanceText) {
        return Double.parseDouble(distanceText.replace(" km", ""));
    }

    private double assignedWeight(DispatchPlanResponse response) {
        return response.dispatchPlan().stream()
                .flatMap(p -> p.assignedOrders().stream())
                .mapToDouble(AssignedOrder::packageWeight)
                .sum();
    }

    private double unassignedWeight(DispatchPlanResponse response) {
        return response.unassignedOrders().stream()
                .mapToDouble(UnassignedOrder::packageWeight)
                .sum();
    }

    // ---------- priority and capacity ----------

    @Test
    @DisplayName("HIGH priority is assigned before LOW when capacity is scarce")
    void highPriorityWinsWhenCapacityIsScarce() {
        List<Order> orders = List.of(
                order("LOW1", 0, 1, 20, Priority.LOW),
                order("HIGH1", 0, 2, 20, Priority.HIGH));
        List<Vehicle> vehicles = List.of(vehicle("V1", 30, 0, 0));

        DispatchPlanResponse result = engine.plan(orders, vehicles);

        assertThat(assignedIds(planFor(result, "V1"))).containsExactly("HIGH1");
        assertThat(unassignedIds(result)).containsExactly("LOW1");
        assertThat(result.unassignedOrders().get(0).reason())
                .isEqualTo("No vehicle has enough remaining capacity");
    }

    @Test
    @DisplayName("Within the same priority, the heavier order is placed first")
    void heavierOrderFirstWithinSamePriority() {
        List<Order> orders = List.of(
                order("LIGHT", 0, 1, 10, Priority.MEDIUM),
                order("HEAVY", 0, 2, 25, Priority.MEDIUM));
        List<Vehicle> vehicles = List.of(vehicle("V1", 30, 0, 0));

        DispatchPlanResponse result = engine.plan(orders, vehicles);

        assertThat(assignedIds(planFor(result, "V1"))).containsExactly("HEAVY");
        assertThat(unassignedIds(result)).containsExactly("LIGHT");
    }

    @Test
    @DisplayName("An order that exactly fills the remaining capacity is accepted")
    void exactCapacityIsAccepted() {
        List<Order> orders = List.of(
                order("A", 0, 1, 10, Priority.HIGH),
                order("B", 0, 2, 20, Priority.HIGH));
        List<Vehicle> vehicles = List.of(vehicle("V1", 30, 0, 0));

        DispatchPlanResponse result = engine.plan(orders, vehicles);

        VehiclePlan plan = planFor(result, "V1");
        assertThat(plan.totalLoad()).isEqualTo(30.0);
        assertThat(plan.assignedOrders()).hasSize(2);
        assertThat(result.unassignedOrders()).isEmpty();
    }

    @Test
    @DisplayName("An order heavier than every vehicle is unassigned with a clear reason")
    void overweightOrderIsUnassigned() {
        List<Order> orders = List.of(order("BIG", 0, 1, 500, Priority.HIGH));
        List<Vehicle> vehicles = List.of(vehicle("V1", 100, 0, 0), vehicle("V2", 120, 0, 5));

        DispatchPlanResponse result = engine.plan(orders, vehicles);

        assertThat(unassignedIds(result)).containsExactly("BIG");
        assertThat(result.unassignedOrders().get(0).reason())
                .contains("exceeds the largest vehicle capacity");
        assertThat(result.dispatchPlan()).allMatch(p -> p.assignedOrders().isEmpty());
    }

    @Test
    @DisplayName("No orders: every vehicle is listed with zero load and zero distance")
    void noOrdersGivesEmptyRoutes() {
        DispatchPlanResponse result = engine.plan(List.of(), List.of(vehicle("V1", 100, 0, 0)));

        VehiclePlan plan = planFor(result, "V1");
        assertThat(plan.totalLoad()).isZero();
        assertThat(plan.totalDistance()).isEqualTo("0.00 km");
        assertThat(plan.assignedOrders()).isEmpty();
        assertThat(result.unassignedOrders()).isEmpty();
    }

    // ---------- nearest vehicle and routing ----------

    @Test
    @DisplayName("An order goes to the nearest vehicle that has room")
    void orderGoesToNearestVehicle() {
        Vehicle delhi = vehicle("DELHI", 100, 28.6139, 77.2090);
        Vehicle chennai = vehicle("CHENNAI", 100, 13.0827, 80.2707);
        List<Order> orders = List.of(
                order("NEAR_DELHI", 28.7041, 77.1025, 10, Priority.HIGH),
                order("NEAR_CHENNAI", 13.0674, 80.2376, 10, Priority.HIGH));

        DispatchPlanResponse result = engine.plan(orders, List.of(delhi, chennai));

        assertThat(assignedIds(planFor(result, "DELHI"))).containsExactly("NEAR_DELHI");
        assertThat(assignedIds(planFor(result, "CHENNAI"))).containsExactly("NEAR_CHENNAI");
    }

    @Test
    @DisplayName("If the nearest vehicle is full, the order moves to the next nearest")
    void fullVehicleIsSkipped() {
        Vehicle small = vehicle("SMALL", 10, 0, 0);
        Vehicle farther = vehicle("FARTHER", 100, 0, 5);
        List<Order> orders = List.of(
                order("FIRST", 0, 0.1, 10, Priority.HIGH),
                order("SECOND", 0, 0.2, 10, Priority.HIGH));

        DispatchPlanResponse result = engine.plan(orders, List.of(small, farther));

        assertThat(assignedIds(planFor(result, "SMALL"))).containsExactly("FIRST");
        assertThat(assignedIds(planFor(result, "FARTHER"))).containsExactly("SECOND");
    }

    @Test
    @DisplayName("Stops are reordered with nearest-neighbor and the distance matches")
    void routeIsNearestNeighborAndDistanceIsCorrect() {
        // Along the equator: vehicle at 0, stops at 10, 2 and 11 degrees of longitude.
        // Nearest-neighbor visits 2, then 10, then 11 = 11 degrees of travel.
        // Visiting in the given order (10, 2, 11) would be 27 degrees.
        List<Order> orders = List.of(
                order("A", 0, 10, 5, Priority.HIGH),
                order("B", 0, 2, 5, Priority.HIGH),
                order("C", 0, 11, 5, Priority.HIGH));
        List<Vehicle> vehicles = List.of(vehicle("V1", 100, 0, 0));

        DispatchPlanResponse result = engine.plan(orders, vehicles);

        VehiclePlan plan = planFor(result, "V1");
        assertThat(assignedIds(plan)).containsExactly("B", "A", "C");
        // one degree of longitude on the equator is about 111.19 km, so 11 degrees is about 1223.14 km
        assertThat(km(plan.totalDistance())).isCloseTo(1223.14, within(0.05));
    }

    @Test
    @DisplayName("Orders at the same location add no distance")
    void sameLocationGivesZeroDistance() {
        List<Order> orders = List.of(
                order("A", 28.6139, 77.2090, 5, Priority.HIGH),
                order("B", 28.6139, 77.2090, 5, Priority.MEDIUM));
        List<Vehicle> vehicles = List.of(vehicle("V1", 100, 28.6139, 77.2090));

        DispatchPlanResponse result = engine.plan(orders, vehicles);

        assertThat(planFor(result, "V1").totalDistance()).isEqualTo("0.00 km");
    }

    // ---------- general properties ----------

    @Test
    @DisplayName("The same input always produces the same plan")
    void resultIsDeterministic() {
        List<Order> orders = new ArrayList<>(sampleOrders());
        List<Vehicle> vehicles = sampleVehicles();

        DispatchPlanResponse first = engine.plan(orders, vehicles);
        DispatchPlanResponse second = engine.plan(orders, vehicles);

        assertThat(second).isEqualTo(first);
    }

    @Test
    @DisplayName("Assigned plus unassigned weight always equals the total order weight")
    void noOrderIsLostOrDuplicated() {
        List<Order> orders = sampleOrders();

        DispatchPlanResponse result = engine.plan(orders, sampleVehicles());

        double total = orders.stream().mapToDouble(Order::getPackageWeight).sum();
        assertThat(assignedWeight(result) + unassignedWeight(result)).isCloseTo(total, within(1e-9));

        long assignedCount = result.dispatchPlan().stream().mapToLong(p -> p.assignedOrders().size()).sum();
        assertThat(assignedCount + result.unassignedOrders().size()).isEqualTo(orders.size());
    }

    // ---------- the assignment's sample data ----------

    @Test
    @DisplayName("Sample data: no vehicle exceeds capacity")
    void sampleData_capacityNeverExceeded() {
        List<Vehicle> vehicles = sampleVehicles();
        Map<String, Double> capacity = new HashMap<>();
        vehicles.forEach(v -> capacity.put(v.getVehicleId(), v.getCapacity()));

        DispatchPlanResponse result = engine.plan(sampleOrders(), vehicles);

        for (VehiclePlan plan : result.dispatchPlan()) {
            double sum = plan.assignedOrders().stream().mapToDouble(AssignedOrder::packageWeight).sum();
            assertThat(plan.totalLoad()).isCloseTo(sum, within(1e-9));
            assertThat(plan.totalLoad()).isLessThanOrEqualTo(capacity.get(plan.vehicleId()));
        }
    }

    @Test
    @DisplayName("Sample data: 645 kg of orders vs 500 kg of capacity leaves at least 145 kg unassigned")
    void sampleData_overflowIsReported() {
        DispatchPlanResponse result = engine.plan(sampleOrders(), sampleVehicles());

        assertThat(unassignedWeight(result)).isGreaterThanOrEqualTo(145.0);
        assertThat(result.unassignedOrders()).allMatch(u -> u.reason() != null && !u.reason().isBlank());
    }

    @Test
    @DisplayName("Sample data: HIGH and MEDIUM orders are all assigned, only LOW ones are left out")
    void sampleData_onlyLowPriorityIsUnassigned() {
        DispatchPlanResponse result = engine.plan(sampleOrders(), sampleVehicles());

        assertThat(result.unassignedOrders())
                .allMatch(u -> u.priority() == Priority.LOW);
    }

    // ---------- performance ----------

    @Test
    @DisplayName("5000 orders across 50 vehicles are planned quickly")
    void largeDatasetIsHandledQuickly() {
        Random random = new Random(42);
        List<Order> orders = new ArrayList<>();
        for (int i = 0; i < 5000; i++) {
            Priority priority = Priority.values()[random.nextInt(3)];
            orders.add(order("ORD" + i,
                    28.0 + random.nextDouble(), 77.0 + random.nextDouble(),
                    1 + random.nextInt(10), priority));
        }
        List<Vehicle> vehicles = new ArrayList<>();
        for (int i = 0; i < 50; i++) {
            vehicles.add(vehicle("VEH" + i, 1000, 28.0 + random.nextDouble(), 77.0 + random.nextDouble()));
        }

        DispatchPlanResponse result = assertTimeout(Duration.ofSeconds(5), () -> engine.plan(orders, vehicles));

        assertThat(result.unassignedOrders()).isEmpty();
        long assigned = result.dispatchPlan().stream().mapToLong(p -> p.assignedOrders().size()).sum();
        assertThat(assigned).isEqualTo(5000);
    }

    // ---------- sample data builders (same 30 orders and 5 vehicles as the assignment) ----------

    private static final double[][] LOCATIONS = {
            {28.6139, 77.2090},  // 0 Connaught Place
            {28.7041, 77.1025},  // 1 Karol Bagh
            {28.5355, 77.3910},  // 2 Sector 18, Noida
            {28.4595, 77.0266}   // 3 Cyber Hub, Gurgaon
    };

    private List<Order> sampleOrders() {
        int[] location = {0, 0, 1, 2, 3, 0, 1, 2, 3, 0, 1, 2, 3, 0, 1, 2, 3, 0, 1, 2, 0, 1, 2, 3, 0, 1, 2, 3, 0, 1};
        double[] weight = {15, 10, 20, 25, 30, 40, 20, 25, 15, 30, 20, 10, 25, 15, 20,
                30, 15, 10, 20, 30, 25, 20, 30, 15, 20, 25, 20, 15, 30, 20};
        String priority = "HMLHMLHHMLHMHLHLMHLHMLHLMHMLHL"; // H = HIGH, M = MEDIUM, L = LOW

        List<Order> orders = new ArrayList<>();
        for (int i = 0; i < 30; i++) {
            Priority p = switch (priority.charAt(i)) {
                case 'H' -> Priority.HIGH;
                case 'M' -> Priority.MEDIUM;
                default -> Priority.LOW;
            };
            orders.add(order(String.format("ORD%03d", i + 1),
                    LOCATIONS[location[i]][0], LOCATIONS[location[i]][1], weight[i], p));
        }
        return orders;
    }

    private List<Vehicle> sampleVehicles() {
        return List.of(
                vehicle("VEH001", 100, LOCATIONS[1][0], LOCATIONS[1][1]),
                vehicle("VEH002", 80, LOCATIONS[2][0], LOCATIONS[2][1]),
                vehicle("VEH003", 120, LOCATIONS[3][0], LOCATIONS[3][1]),
                vehicle("VEH004", 90, LOCATIONS[0][0], LOCATIONS[0][1]),
                vehicle("VEH005", 110, LOCATIONS[1][0], LOCATIONS[1][1]));
    }
}