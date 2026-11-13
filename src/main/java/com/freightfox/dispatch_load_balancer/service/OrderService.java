package com.freightfox.dispatch_load_balancer.service;

import com.freightfox.dispatch_load_balancer.dto.OrderRequest;
import com.freightfox.dispatch_load_balancer.exception.InvalidInputException;
import com.freightfox.dispatch_load_balancer.model.Order;
import com.freightfox.dispatch_load_balancer.repository.OrderRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
public class OrderService {

    private final OrderRepository repository;

    public OrderService(OrderRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public void saveOrders(List<OrderRequest> requests) {
        rejectDuplicateIds(requests);
        List<Order> orders = requests.stream().map(this::toEntity).toList();
        repository.saveAll(orders); // existing IDs are updated, new IDs are inserted
    }

    private void rejectDuplicateIds(List<OrderRequest> requests) {
        Set<String> seen = new HashSet<>();
        for (OrderRequest request : requests) {
            if (!seen.add(request.orderId())) {
                throw new InvalidInputException("Duplicate orderId in request: " + request.orderId());
            }
        }
    }

    private Order toEntity(OrderRequest r) {
        return new Order(r.orderId(), r.latitude(), r.longitude(),
                r.address(), r.packageWeight(), r.priority());
    }
}