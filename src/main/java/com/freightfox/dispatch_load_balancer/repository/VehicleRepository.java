package com.freightfox.dispatch_load_balancer.repository;

import com.freightfox.dispatch_load_balancer.model.Vehicle;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VehicleRepository extends JpaRepository<Vehicle, String> {
}