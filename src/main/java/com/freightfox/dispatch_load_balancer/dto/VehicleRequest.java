package com.freightfox.dispatch_load_balancer.dto;

import jakarta.validation.constraints.*;

public record VehicleRequest(
        @NotBlank(message = "is required") String vehicleId,
        @NotNull(message = "is required")
        @Positive(message = "must be greater than 0") Double capacity,
        @NotNull(message = "is required")
        @DecimalMin(value = "-90.0", message = "must be between -90 and 90")
        @DecimalMax(value = "90.0", message = "must be between -90 and 90") Double currentLatitude,
        @NotNull(message = "is required")
        @DecimalMin(value = "-180.0", message = "must be between -180 and 180")
        @DecimalMax(value = "180.0", message = "must be between -180 and 180") Double currentLongitude,
        @NotBlank(message = "is required") String currentAddress
) {
}