package com.example.banking.dto;

import com.example.banking.model.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;

public final class Responses {
    private Responses() {}

    public record AccountView(UUID id, String accountNumber, String ownerName, BigDecimal balance, Instant createdAt) {
        public static AccountView from(Account a) {
            return new AccountView(a.getId(), a.getAccountNumber(), a.getOwnerName(), a.getBalance(), a.getCreatedAt());
        }
    }

    public record TransactionView(UUID id, UUID fromAccountId, UUID toAccountId, TransactionType type,
                                  BigDecimal amount, Instant createdAt, String idempotencyKey) {
        public static TransactionView from(BankTransaction t) {
            return new TransactionView(t.getId(), t.getFromAccountId(), t.getToAccountId(), t.getType(),
                    t.getAmount(), t.getCreatedAt(), t.getIdempotencyKey());
        }
    }

    public record PageView<T>(List<T> content, int page, int size, long totalElements, int totalPages) {
        public static <T> PageView<T> from(Page<T> p) {
            return new PageView<>(p.getContent(), p.getNumber(), p.getSize(), p.getTotalElements(), p.getTotalPages());
        }
    }
}
