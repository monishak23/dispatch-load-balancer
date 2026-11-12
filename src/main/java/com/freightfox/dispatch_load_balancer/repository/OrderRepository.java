package com.freightfox.dispatch_load_balancer.repository;

import com.freightfox.dispatch_load_balancer.model.Order;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderRepository extends JpaRepository<Order, String> {
}