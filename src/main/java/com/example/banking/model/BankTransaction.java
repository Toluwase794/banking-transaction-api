package com.example.banking.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Entity
@Table(name = "transactions")
public class BankTransaction {
    @Id private UUID id;
    private UUID fromAccountId;
    private UUID toAccountId;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 16) private TransactionType type;
    @Column(nullable = false, precision = 19, scale = 2) private BigDecimal amount;
    @Column(nullable = false, updatable = false) private Instant createdAt;
    @Column(unique = true, length = 128) private String idempotencyKey;

    protected BankTransaction() {}

    public BankTransaction(UUID from, UUID to, TransactionType type, BigDecimal amount, String key) {
        this.id = UUID.randomUUID();
        this.fromAccountId = from;
        this.toAccountId = to;
        this.type = type;
        this.amount = amount;
        this.createdAt = Instant.now().truncatedTo(ChronoUnit.MICROS);
        this.idempotencyKey = key;
    }

    public UUID getId() { return id; }
    public UUID getFromAccountId() { return fromAccountId; }
    public UUID getToAccountId() { return toAccountId; }
    public TransactionType getType() { return type; }
    public BigDecimal getAmount() { return amount; }
    public Instant getCreatedAt() { return createdAt; }
    public String getIdempotencyKey() { return idempotencyKey; }
}
