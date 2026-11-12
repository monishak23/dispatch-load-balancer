package com.freightfox.dispatch_load_balancer.dto;

import com.freightfox.dispatch_load_balancer.model.Order.Priority;
import jakarta.validation.constraints.*;

public record OrderRequest(
        @NotBlank(message = "is required") String orderId,
        @NotNull(message = "is required")
        @DecimalMin(value = "-90.0", message = "must be between -90 and 90")
        @DecimalMax(value = "90.0", message = "must be between -90 and 90") Double latitude,
        @NotNull(message = "is required")
        @DecimalMin(value = "-180.0", message = "must be between -180 and 180")
        @DecimalMax(value = "180.0", message = "must be between -180 and 180") Double longitude,
        @NotBlank(message = "is required") String address,
        @NotNull(message = "is required")
        @Positive(message = "must be greater than 0") Double packageWeight,
        @NotNull(message = "is required") Priority priority
) {
}