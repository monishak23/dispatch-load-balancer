package com.freightfox.dispatch_load_balancer.dto;

import com.freightfox.dispatch_load_balancer.model.Order.Priority;

import java.util.List;

public record DispatchPlanResponse(List<VehiclePlan> dispatchPlan,
                                   List<UnassignedOrder> unassignedOrders) {

    public record VehiclePlan(String vehicleId, double totalLoad, String totalDistance,
                              List<AssignedOrder> assignedOrders) {
    }

    public record AssignedOrder(String orderId, double latitude, double longitude,
                                String address, double packageWeight, Priority priority) {
    }

    public record UnassignedOrder(String orderId, double latitude, double longitude,
                                  String address, double packageWeight, Priority priority,
                                  String reason) {
    }
}