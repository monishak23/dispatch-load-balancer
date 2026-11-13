package com.freightfox.dispatch_load_balancer.service;

import com.freightfox.dispatch_load_balancer.dto.VehicleRequest;
import com.freightfox.dispatch_load_balancer.exception.InvalidInputException;
import com.freightfox.dispatch_load_balancer.model.Vehicle;
import com.freightfox.dispatch_load_balancer.repository.VehicleRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
public class VehicleService {

    private final VehicleRepository repository;

    public VehicleService(VehicleRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public void saveVehicles(List<VehicleRequest> requests) {
        rejectDuplicateIds(requests);
        List<Vehicle> vehicles = requests.stream().map(this::toEntity).toList();
        repository.saveAll(vehicles);
    }

    private void rejectDuplicateIds(List<VehicleRequest> requests) {
        Set<String> seen = new HashSet<>();
        for (VehicleRequest request : requests) {
            if (!seen.add(request.vehicleId())) {
                throw new InvalidInputException("Duplicate vehicleId in request: " + request.vehicleId());
            }
        }
    }

    private Vehicle toEntity(VehicleRequest r) {
        return new Vehicle(r.vehicleId(), r.capacity(), r.currentLatitude(),
                r.currentLongitude(), r.currentAddress());
    }
}