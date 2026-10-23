package com.example.banking.repository;

import com.example.banking.model.BankTransaction;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.Optional;
import java.util.UUID;

public interface TransactionRepository extends JpaRepository<BankTransaction, UUID> {
    Optional<BankTransaction> findByIdempotencyKey(String key);

    @Query("select t from BankTransaction t where t.fromAccountId = :id or t.toAccountId = :id")
    Page<BankTransaction> history(@Param("id") UUID id, Pageable pageable);
}
