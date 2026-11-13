package com.freightfox.dispatch_load_balancer.dispatchengine;

import com.freightfox.dispatch_load_balancer.dto.DispatchPlanResponse;
import com.freightfox.dispatch_load_balancer.dto.DispatchPlanResponse.AssignedOrder;
import com.freightfox.dispatch_load_balancer.dto.DispatchPlanResponse.UnassignedOrder;
import com.freightfox.dispatch_load_balancer.dto.DispatchPlanResponse.VehiclePlan;
import com.freightfox.dispatch_load_balancer.model.Order;
import com.freightfox.dispatch_load_balancer.model.Vehicle;
import com.freightfox.dispatch_load_balancer.util.HaversineCalculator;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Step 1 (allocation): orders are processed by priority (HIGH, MEDIUM, LOW), heavier first
 * within a priority. Each order goes to the nearest vehicle that still has room, measured
 * from the vehicle's last assigned stop (or its starting position if it has none yet).
 *
 * Step 2 (routing): each vehicle's stops are reordered with nearest-neighbor, starting
 * from the vehicle's current location. Vehicles do not return to their start point.
 */
@Component
public class DispatchEngine {

    private static final double EPSILON = 1e-9;

    public DispatchPlanResponse plan(List<Order> orders, List<Vehicle> vehicles) {
        List<VehicleLoad> loads = vehicles.stream()
                .sorted(Comparator.comparing(Vehicle::getVehicleId))
                .map(VehicleLoad::new)
                .toList();

        double maxCapacity = vehicles.stream().mapToDouble(Vehicle::getCapacity).max().orElse(0);

        List<Order> sortedOrders = orders.stream()
                .sorted(Comparator.comparing(Order::getPriority)
                        .thenComparing(Comparator.comparingDouble(Order::getPackageWeight).reversed())
                        .thenComparing(Order::getOrderId))
                .toList();

        List<UnassignedOrder> unassigned = new ArrayList<>();
        for (Order order : sortedOrders) {
            VehicleLoad best = findNearestVehicleWithRoom(order, loads);
            if (best == null) {
                unassigned.add(new UnassignedOrder(order.getOrderId(), order.getLatitude(),
                        order.getLongitude(), order.getAddress(), order.getPackageWeight(),
                        order.getPriority(), reasonFor(order, maxCapacity)));
            } else {
                best.add(order);
            }
        }

        List<VehiclePlan> plans = loads.stream().map(this::buildVehiclePlan).toList();
        return new DispatchPlanResponse(plans, unassigned);
    }

    private VehicleLoad findNearestVehicleWithRoom(Order order, List<VehicleLoad> loads) {
        VehicleLoad best = null;
        double bestDistance = Double.MAX_VALUE;
        for (VehicleLoad load : loads) {
            if (!load.canFit(order)) {
                continue;
            }
            double distance = HaversineCalculator.distanceKm(
                    load.lastLat, order.getLatitude(),
                    load.lastLon, order.getLongitude());
            if (distance < bestDistance) {
                bestDistance = distance;
                best = load;
            }
        }
        return best;
    }

    private String reasonFor(Order order, double maxCapacity) {
        if (order.getPackageWeight() > maxCapacity + EPSILON) {
            return String.format(Locale.US, "Package weight %.2f exceeds the largest vehicle capacity (%.2f)",
                    order.getPackageWeight(), maxCapacity);
        }
        return "No vehicle has enough remaining capacity";
    }

    private VehiclePlan buildVehiclePlan(VehicleLoad load) {
        Vehicle vehicle = load.vehicle;
        List<Order> remaining = new ArrayList<>(load.orders);
        List<AssignedOrder> route = new ArrayList<>(remaining.size());
        double currentLat = vehicle.getCurrentLatitude();
        double currentLon = vehicle.getCurrentLongitude();
        double totalDistance = 0;

        while (!remaining.isEmpty()) {
            int bestIndex = 0;
            double bestDistance = Double.MAX_VALUE;
            for (int i = 0; i < remaining.size(); i++) {
                Order candidate = remaining.get(i);
                double distance = HaversineCalculator.distanceKm(
                        currentLat, candidate.getLatitude(),
                        currentLon, candidate.getLongitude());
                if (distance < bestDistance) {
                    bestDistance = distance;
                    bestIndex = i;
                }
            }
            Order next = remaining.remove(bestIndex);
            route.add(new AssignedOrder(next.getOrderId(), next.getLatitude(), next.getLongitude(),
                    next.getAddress(), next.getPackageWeight(), next.getPriority()));
            totalDistance += bestDistance;
            currentLat = next.getLatitude();
            currentLon = next.getLongitude();
        }
        String distanceText = String.format(Locale.US, "%.2f km", totalDistance);
        return new VehiclePlan(vehicle.getVehicleId(), load.load, distanceText, route);
    }

    /** Mutable working state for one vehicle while allocation is running. */
    private static class VehicleLoad {
        private final Vehicle vehicle;
        private final List<Order> orders = new ArrayList<>();
        private double load = 0;
        private double lastLat;
        private double lastLon;

        VehicleLoad(Vehicle vehicle) {
            this.vehicle = vehicle;
            this.lastLat = vehicle.getCurrentLatitude();
            this.lastLon = vehicle.getCurrentLongitude();
        }

        boolean canFit(Order order) {
            return load + order.getPackageWeight() <= vehicle.getCapacity() + EPSILON;
        }

        void add(Order order) {
            orders.add(order);
            load += order.getPackageWeight();
            lastLat = order.getLatitude();
            lastLon = order.getLongitude();
        }
    }
}