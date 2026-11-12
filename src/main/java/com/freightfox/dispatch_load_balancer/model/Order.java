package com.freightfox.dispatch_load_balancer.model;

import jakarta.persistence.*;

@Entity
@Table(name = "orders")
public class Order {

    public enum Priority {
        HIGH, MEDIUM, LOW
    }

    @Id
    @Column(name = "order_id", length = 50)
    private String orderId;

    @Column(nullable = false)
    private double latitude;

    @Column(nullable = false)
    private double longitude;

    @Column(nullable = false)
    private String address;

    @Column(nullable = false)
    private double packageWeight;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Priority priority;

    protected Order() {
        // required by JPA
    }

    public Order(String orderId, double latitude, double longitude,
                 String address, double packageWeight, Priority priority) {
        this.orderId = orderId;
        this.latitude = latitude;
        this.longitude = longitude;
        this.address = address;
        this.packageWeight = packageWeight;
        this.priority = priority;
    }

    public String getOrderId() { return orderId; }
    public double getLatitude() { return latitude; }
    public double getLongitude() { return longitude; }
    public String getAddress() { return address; }
    public double getPackageWeight() { return packageWeight; }
    public Priority getPriority() { return priority; }
}