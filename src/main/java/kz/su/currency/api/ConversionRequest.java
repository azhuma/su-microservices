package kz.su.currency.api;

import java.math.BigDecimal;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ConversionRequest(
        @NotBlank @Size(max = 16) String from,
        @NotBlank @Size(max = 16) String to,
        @NotNull @DecimalMin("0") @Digits(integer = 18, fraction = 8) BigDecimal amount) {
}
