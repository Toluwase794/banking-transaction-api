package com.example.banking.service;

import com.example.banking.dto.Requests;
import com.example.banking.dto.Responses.*;
import com.example.banking.exception.BankingException;
import com.example.banking.model.*;
import com.example.banking.repository.*;
import org.springframework.data.domain.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;

@Service
@Transactional
public class BankingService {
    private static final BigDecimal MAX_BALANCE = new BigDecimal("99999999999999999.99");
    private final AccountRepository accounts;
    private final TransactionRepository transactions;

    public BankingService(AccountRepository accounts, TransactionRepository transactions) {
        this.accounts = accounts;
        this.transactions = transactions;
    }

    public AccountView create(String ownerName) {
        return AccountView.from(accounts.save(new Account(ownerName)));
    }

    @Transactional(readOnly = true)
    public AccountView get(UUID id) { return AccountView.from(find(id)); }

    @Transactional(readOnly = true)
    public PageView<AccountView> list(int page, int size) {
        return PageView.from(accounts.findAll(PageRequest.of(page, size, Sort.by("createdAt", "id")))
                .map(AccountView::from));
    }

    @Transactional(readOnly = true)
    public PageView<TransactionView> history(UUID id, int page, int size) {
        find(id);
        return PageView.from(transactions.history(id, PageRequest.of(page, size,
                Sort.by(Sort.Direction.DESC, "createdAt", "id"))).map(TransactionView::from));
    }

    public TransactionView deposit(UUID id, BigDecimal amount) {
        amount = money(amount);
        Account account = lock(id);
        checkCredit(account, amount);
        account.credit(amount);
        return record(new BankTransaction(null, id, TransactionType.DEPOSIT, amount, null));
    }

    public TransactionView withdraw(UUID id, BigDecimal amount) {
        amount = money(amount);
        Account account = lock(id);
        checkFunds(account, amount);
        account.debit(amount);
        return record(new BankTransaction(id, null, TransactionType.WITHDRAWAL, amount, null));
    }

    public TransactionView transfer(Requests.Transfer request) {
        BigDecimal amount = money(request.amount());
        if (request.fromAccountId().equals(request.toAccountId())) {
            throw new BankingException(HttpStatus.BAD_REQUEST, "Source and destination must be different accounts");
        }
        var existing = transactions.findByIdempotencyKey(request.idempotencyKey());
        if (existing.isPresent()) return replay(existing.get(), request);

        // All transfers acquire locks in the same order, including opposite-direction transfers.
        UUID first = request.fromAccountId().compareTo(request.toAccountId()) < 0
                ? request.fromAccountId() : request.toAccountId();
        UUID second = first.equals(request.fromAccountId()) ? request.toAccountId() : request.fromAccountId();
        Account a = lock(first);
        Account b = lock(second);
        Account from = a.getId().equals(request.fromAccountId()) ? a : b;
        Account to = a.getId().equals(request.toAccountId()) ? a : b;

        // A concurrent request may have committed while this transaction waited for the locks.
        existing = transactions.findByIdempotencyKey(request.idempotencyKey());
        if (existing.isPresent()) return replay(existing.get(), request);
        checkFunds(from, amount);
        checkCredit(to, amount);
        from.debit(amount);
        to.credit(amount);
        return record(new BankTransaction(from.getId(), to.getId(), TransactionType.TRANSFER,
                amount, request.idempotencyKey()));
    }

    @Transactional(readOnly = true)
    public TransactionView findReplay(Requests.Transfer request) {
        return transactions.findByIdempotencyKey(request.idempotencyKey())
                .map(t -> replay(t, request)).orElse(null);
    }

    private TransactionView replay(BankTransaction t, Requests.Transfer request) {
        if (!request.fromAccountId().equals(t.getFromAccountId())
                || !request.toAccountId().equals(t.getToAccountId())
                || request.amount().compareTo(t.getAmount()) != 0) {
            throw new BankingException(HttpStatus.CONFLICT, "Idempotency key already used for a different transfer");
        }
        return TransactionView.from(t);
    }

    private TransactionView record(BankTransaction transaction) {
        return TransactionView.from(transactions.saveAndFlush(transaction));
    }

    private Account find(UUID id) {
        return accounts.findById(id).orElseThrow(() -> missing(id));
    }
    private Account lock(UUID id) {
        return accounts.findLockedById(id).orElseThrow(() -> missing(id));
    }
    private BankingException missing(UUID id) {
        return new BankingException(HttpStatus.NOT_FOUND, "Account not found: " + id);
    }
    private void checkFunds(Account a, BigDecimal amount) {
        if (a.getBalance().compareTo(amount) < 0)
            throw new BankingException(HttpStatus.CONFLICT, "Insufficient funds");
    }
    private void checkCredit(Account a, BigDecimal amount) {
        if (a.getBalance().add(amount).compareTo(MAX_BALANCE) > 0)
            throw new BankingException(HttpStatus.CONFLICT, "Account balance limit exceeded");
    }
    private BigDecimal money(BigDecimal amount) {
        if (amount == null || amount.signum() <= 0 || amount.compareTo(MAX_BALANCE) > 0)
            throw new BankingException(HttpStatus.BAD_REQUEST, "Amount must be positive and fit DECIMAL(19,2)");
        try { return amount.setScale(2, RoundingMode.UNNECESSARY); }
        catch (ArithmeticException e) {
            throw new BankingException(HttpStatus.BAD_REQUEST, "Amount must have at most two decimal places");
        }
    }
}
