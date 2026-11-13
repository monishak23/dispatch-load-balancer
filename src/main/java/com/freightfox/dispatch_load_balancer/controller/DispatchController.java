package com.freightfox.dispatch_load_balancer.controller;

import com.freightfox.dispatch_load_balancer.dto.ApiResponse;
import com.freightfox.dispatch_load_balancer.dto.DispatchPlanResponse;
import com.freightfox.dispatch_load_balancer.dto.OrderRequest;
import com.freightfox.dispatch_load_balancer.dto.VehicleRequest;
import com.freightfox.dispatch_load_balancer.service.DispatchService;
import com.freightfox.dispatch_load_balancer.service.OrderService;
import com.freightfox.dispatch_load_balancer.service.VehicleService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Dispatch", description = "Submit orders and vehicles, then get the optimized dispatch plan")
@Validated
@RestController
@RequestMapping("/api/dispatch")
public class DispatchController {

    private final OrderService orderService;
    private final VehicleService vehicleService;
    private final DispatchService dispatchService;

    public DispatchController(OrderService orderService,
                              VehicleService vehicleService,
                              DispatchService dispatchService) {
        this.orderService = orderService;
        this.vehicleService = vehicleService;
        this.dispatchService = dispatchService;
    }

    @Operation(summary = "Submit delivery orders",
            description = "Accepts a JSON array of orders. An existing orderId is updated instead of duplicated.")
    @PostMapping("/orders")
    public ResponseEntity<ApiResponse> addOrders(
            @RequestBody @NotEmpty(message = "must not be empty")
            List<@NotNull(message = "must not be null") @Valid OrderRequest> orders) {
        orderService.saveOrders(orders);
        return ResponseEntity.ok(ApiResponse.success("Delivery orders accepted."));
    }

    @Operation(summary = "Submit fleet details",
            description = "Accepts a JSON array of vehicles. An existing vehicleId is updated instead of duplicated.")
    @PostMapping("/vehicles")
    public ResponseEntity<ApiResponse> addVehicles(
            @RequestBody @NotEmpty(message = "must not be empty")
            List<@NotNull(message = "must not be null") @Valid VehicleRequest> vehicles) {
        vehicleService.saveVehicles(vehicles);
        return ResponseEntity.ok(ApiResponse.success("Vehicle details accepted."));
    }

    @Operation(summary = "Get the dispatch plan",
            description = "HIGH priority orders are assigned first, each to the nearest vehicle with spare capacity. "
                    + "Stops are ordered by nearest-neighbor. Orders that do not fit are listed in unassignedOrders with a reason.")
    @GetMapping("/plan")
    public ResponseEntity<DispatchPlanResponse> getPlan() {
        return ResponseEntity.ok(dispatchService.getPlan());
    }
}