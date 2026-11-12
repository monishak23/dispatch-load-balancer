package com.freightfox.dispatch_load_balancer.model;

import jakarta.persistence.*;

@Entity
@Table(name = "vehicles")
public class Vehicle {

    @Id
    @Column(name = "vehicle_id", length = 50)
    private String vehicleId;

    @Column(nullable = false)
    private double capacity;

    @Column(nullable = false)
    private double currentLatitude;

    @Column(nullable = false)
    private double currentLongitude;

    @Column(nullable = false)
    private String currentAddress;

    protected Vehicle() {
        // required by JPA
    }

    public Vehicle(String vehicleId, double capacity, double currentLatitude,
                   double currentLongitude, String currentAddress) {
        this.vehicleId = vehicleId;
        this.capacity = capacity;
        this.currentLatitude = currentLatitude;
        this.currentLongitude = currentLongitude;
        this.currentAddress = currentAddress;
    }

    public String getVehicleId() { return vehicleId; }
    public double getCapacity() { return capacity; }
    public double getCurrentLatitude() { return currentLatitude; }
    public double getCurrentLongitude() { return currentLongitude; }
    public String getCurrentAddress() { return currentAddress; }
}