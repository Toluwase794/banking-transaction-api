package com.example.banking.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Entity
@Table(name = "accounts")
public class Account {
    @Id private UUID id;
    @Column(nullable = false, unique = true, length = 36) private String accountNumber;
    @Column(nullable = false, length = 120) private String ownerName;
    @Column(nullable = false, precision = 19, scale = 2) private BigDecimal balance;
    @Column(nullable = false, updatable = false) private Instant createdAt;

    protected Account() {}

    public Account(String ownerName) {
        this.id = UUID.randomUUID();
        this.accountNumber = UUID.randomUUID().toString();
        this.ownerName = ownerName.strip();
        this.balance = new BigDecimal("0.00");
        this.createdAt = Instant.now().truncatedTo(ChronoUnit.MICROS);
    }

    public UUID getId() { return id; }
    public String getAccountNumber() { return accountNumber; }
    public String getOwnerName() { return ownerName; }
    public BigDecimal getBalance() { return balance; }
    public Instant getCreatedAt() { return createdAt; }
    public void credit(BigDecimal amount) { balance = balance.add(amount); }
    public void debit(BigDecimal amount) { balance = balance.subtract(amount); }
}
