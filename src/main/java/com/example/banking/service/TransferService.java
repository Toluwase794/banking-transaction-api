package com.example.banking.service;

import com.example.banking.dto.Requests;
import com.example.banking.dto.Responses.TransactionView;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

@Service
public class TransferService {
    private final BankingService banking;
    public TransferService(BankingService banking) { this.banking = banking; }

    public TransactionView transfer(Requests.Transfer request) {
        try {
            return banking.transfer(request);
        } catch (DataIntegrityViolationException collision) {
            // The failed transaction has rolled back before looking up the winning key.
            // This also covers collisions between transfers involving disjoint accounts.
            TransactionView replay = banking.findReplay(request);
            if (replay != null) return replay;
            throw collision;
        }
    }
}
