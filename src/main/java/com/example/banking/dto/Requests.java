package com.example.banking.dto;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.util.UUID;

public final class Requests {
    private Requests() {}

    public record CreateAccount(@NotBlank @Size(max = 120) String ownerName) {}

    public record Money(@NotNull @DecimalMin("0.01") @Digits(integer = 17, fraction = 2) BigDecimal amount) {}

    public record Transfer(
            @NotNull UUID fromAccountId,
            @NotNull UUID toAccountId,
            @NotNull @DecimalMin("0.01") @Digits(integer = 17, fraction = 2) BigDecimal amount,
            @NotBlank @Size(max = 128) @Pattern(regexp = "[A-Za-z0-9._:-]+",
                    message = "must contain only letters, numbers, dots, underscores, colons or hyphens") String idempotencyKey) {}
}
