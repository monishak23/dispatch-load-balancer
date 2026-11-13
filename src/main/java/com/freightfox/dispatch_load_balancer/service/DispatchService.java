package com.freightfox.dispatch_load_balancer.service;

import com.freightfox.dispatch_load_balancer.dispatchengine.DispatchEngine;
import com.freightfox.dispatch_load_balancer.dto.DispatchPlanResponse;
import com.freightfox.dispatch_load_balancer.exception.InvalidInputException;
import com.freightfox.dispatch_load_balancer.model.Order;
import com.freightfox.dispatch_load_balancer.model.Vehicle;
import com.freightfox.dispatch_load_balancer.repository.OrderRepository;
import com.freightfox.dispatch_load_balancer.repository.VehicleRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class DispatchService {

    private final OrderRepository orderRepository;
    private final VehicleRepository vehicleRepository;
    private final DispatchEngine dispatchEngine;

    public DispatchService(OrderRepository orderRepository,
                           VehicleRepository vehicleRepository,
                           DispatchEngine dispatchEngine) {
        this.orderRepository = orderRepository;
        this.vehicleRepository = vehicleRepository;
        this.dispatchEngine = dispatchEngine;
    }

    @Transactional(readOnly = true)
    public DispatchPlanResponse getPlan() {
        List<Order> orders = orderRepository.findAll();
        List<Vehicle> vehicles = vehicleRepository.findAll();

        if (orders.isEmpty()) {
            throw new InvalidInputException("No delivery orders found. Submit orders first using POST /api/dispatch/orders.");
        }
        if (vehicles.isEmpty()) {
            throw new InvalidInputException("No vehicles found. Submit vehicles first using POST /api/dispatch/vehicles.");
        }
        return dispatchEngine.plan(orders, vehicles);
    }
}