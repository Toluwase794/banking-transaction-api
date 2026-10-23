package com.example.banking.controller;

import com.example.banking.dto.Requests;
import com.example.banking.dto.Responses.*;
import com.example.banking.service.*;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/api")
public class BankingController {
    private final BankingService banking;
    private final TransferService transfers;
    public BankingController(BankingService banking, TransferService transfers) {
        this.banking = banking;
        this.transfers = transfers;
    }

    @PostMapping("/accounts")
    @Operation(summary = "Create an account with a zero opening balance")
    public ResponseEntity<AccountView> create(@Valid @RequestBody Requests.CreateAccount request) {
        AccountView account = banking.create(request.ownerName());
        return ResponseEntity.created(URI.create("/api/accounts/" + account.id())).body(account);
    }

    @GetMapping("/accounts/{id}")
    public AccountView get(@PathVariable UUID id) { return banking.get(id); }

    @GetMapping("/accounts")
    public PageView<AccountView> list(@RequestParam(defaultValue = "0") @Min(0) int page,
                                      @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return banking.list(page, size);
    }

    @PostMapping("/accounts/{id}/deposits")
    public TransactionView deposit(@PathVariable UUID id, @Valid @RequestBody Requests.Money request) {
        return banking.deposit(id, request.amount());
    }

    @PostMapping("/accounts/{id}/withdrawals")
    public TransactionView withdraw(@PathVariable UUID id, @Valid @RequestBody Requests.Money request) {
        return banking.withdraw(id, request.amount());
    }

    @PostMapping("/transfers")
    @Operation(summary = "Transfer funds atomically", description = "A repeated key with the same payload returns the original transaction. A changed payload returns 409.")
    public TransactionView transfer(@Valid @RequestBody Requests.Transfer request) {
        return transfers.transfer(request);
    }

    @GetMapping("/accounts/{id}/transactions")
    public PageView<TransactionView> history(@PathVariable UUID id,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return banking.history(id, page, size);
    }
}
