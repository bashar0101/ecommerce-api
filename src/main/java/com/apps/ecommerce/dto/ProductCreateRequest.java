package com.apps.ecommerce.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record ProductCreateRequest(
                @NotBlank(message = "name is required") String name,
                @NotNull @DecimalMin(value = "0.0", inclusive = false) BigDecimal price,
                @NotNull @Min(0) Integer stock,
                String description

) {

}
