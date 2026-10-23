package com.example.banking;

import com.example.banking.dto.Requests.Transfer;
import com.example.banking.model.BankTransaction;
import com.example.banking.repository.*;
import com.example.banking.service.*;
import com.fasterxml.jackson.databind.*;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.IntFunction;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class BankingApiTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired BankingService banking;
    @Autowired TransferService transfers;
    @Autowired AccountRepository accounts;
    @MockitoSpyBean TransactionRepository transactions;
    @Autowired EntityManager entityManager;

    @BeforeEach void cleanDatabase() {
        transactions.deleteAll();
        accounts.deleteAll();
    }

    @Test void createsRetrievesAndListsAccounts() throws Exception {
        var response = mvc.perform(post("/api/accounts").contentType(MediaType.APPLICATION_JSON)
                .content("{\"ownerName\":\"  Ada Lovelace  \"}"))
                .andExpect(status().isCreated()).andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.ownerName").value("Ada Lovelace"))
                .andExpect(jsonPath("$.balance").value(0)).andReturn();
        String id = json.readTree(response.getResponse().getContentAsString()).get("id").asText();
        mvc.perform(get("/api/accounts/" + id)).andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id));
        mvc.perform(get("/api/accounts")).andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test void depositWithdrawalAndHistory() throws Exception {
        UUID id = account("Ada", "500.00");
        mvc.perform(post("/api/accounts/" + id + "/deposits").contentType(MediaType.APPLICATION_JSON)
                .content("{\"amount\":200.00}")).andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("DEPOSIT"));
        balance(id, "700.00");
        mvc.perform(post("/api/accounts/" + id + "/withdrawals").contentType(MediaType.APPLICATION_JSON)
                .content("{\"amount\":100.00}")).andExpect(status().isOk());
        balance(id, "600.00");
        mvc.perform(get("/api/accounts/" + id + "/transactions").param("size", "2"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.content[0].type").value("WITHDRAWAL"));
    }

    @Test void rejectsOverdraftWithoutChangingHistory() throws Exception {
        UUID id = account("Ada", "500.00");
        mvc.perform(post("/api/accounts/" + id + "/withdrawals").contentType(MediaType.APPLICATION_JSON)
                .content("{\"amount\":700.00}")).andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
        balance(id, "500.00");
        assertThat(transactions.count()).isEqualTo(1);
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1", "1.001", "100000000000000000.00", "null"})
    void rejectsInvalidAmounts(String amount) throws Exception {
        UUID id = account("Ada", "10.00");
        for (String operation : List.of("deposits", "withdrawals")) {
            mvc.perform(post("/api/accounts/" + id + "/" + operation).contentType(MediaType.APPLICATION_JSON)
                    .content("{\"amount\":" + amount + "}")).andExpect(status().isBadRequest());
        }
        UUID to = account("Grace", "0");
        mvc.perform(post("/api/transfers").contentType(MediaType.APPLICATION_JSON)
                .content("{\"fromAccountId\":\"" + id + "\",\"toAccountId\":\"" + to
                        + "\",\"amount\":" + amount + ",\"idempotencyKey\":\"invalid\"}"))
                .andExpect(status().isBadRequest());
        balance(id, "10.00");
    }

    @Test void validatesNamesJsonIdsAndPagination() throws Exception {
        for (String body : List.of("{}", "{\"ownerName\":\" \"}", "{\"ownerName\":\"Ada\",\"balance\":100}",
                "{\"ownerName\":\"" + "a".repeat(121) + "\"}", "{")) {
            mvc.perform(post("/api/accounts").contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest());
        }
        mvc.perform(get("/api/accounts/not-a-uuid")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/accounts").param("page", "-1")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/accounts").param("size", "101")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/accounts").param("size", "0")).andExpect(status().isBadRequest());
    }

    @Test void missingAccountsReturn404() throws Exception {
        UUID missing = UUID.randomUUID();
        mvc.perform(get("/api/accounts/" + missing)).andExpect(status().isNotFound());
        mvc.perform(get("/api/accounts/" + missing + "/transactions")).andExpect(status().isNotFound());
        mvc.perform(post("/api/accounts/" + missing + "/deposits").contentType(MediaType.APPLICATION_JSON)
                .content("{\"amount\":1}")).andExpect(status().isNotFound());
        UUID from = account("Ada", "10");
        mvc.perform(post("/api/transfers").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(transfer(from, missing, "1", "missing"))))
                .andExpect(status().isNotFound());
        balance(from, "10");
    }

    @Test void transferIsAtomicAndIdempotent() throws Exception {
        UUID from = account("Ada", "500");
        UUID to = account("Grace", "0");
        String request = json.writeValueAsString(transfer(from, to, "150", "transfer-001"));
        String first = mvc.perform(post("/api/transfers").contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isOk()).andExpect(jsonPath("$.type").value("TRANSFER"))
                .andReturn().getResponse().getContentAsString();
        String second = mvc.perform(post("/api/transfers").contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(json.readTree(second)).isEqualTo(json.readTree(first));
        balance(from, "350");
        balance(to, "150");
        assertThat(banking.history(to, 0, 20).totalElements()).isEqualTo(1);
        assertThat(transactions.count()).isEqualTo(2);
    }

    @Test void changedPayloadForExistingKeyConflicts() throws Exception {
        UUID from = account("Ada", "500");
        UUID to = account("Grace", "0");
        transfers.transfer(transfer(from, to, "150", "same-key"));
        mvc.perform(post("/api/transfers").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(transfer(from, to, "151", "same-key"))))
                .andExpect(status().isConflict());
        balance(from, "350");
        balance(to, "150");
    }

    @Test void rejectsSameAccountAndInvalidTransferKey() throws Exception {
        UUID id = account("Ada", "500");
        for (Transfer request : List.of(transfer(id, id, "1", "self"),
                transfer(id, UUID.randomUUID(), "1", " "),
                transfer(id, UUID.randomUUID(), "1", "invalid key"))) {
            mvc.perform(post("/api/transfers").contentType(MediaType.APPLICATION_JSON)
                    .content(json.writeValueAsString(request))).andExpect(status().isBadRequest());
        }
        balance(id, "500");
    }

    @Test void insufficientTransferDoesNotCreditRecipient() {
        UUID from = account("Ada", "50");
        UUID to = account("Grace", "10");
        assertThatThrownBy(() -> transfers.transfer(transfer(from, to, "51", "overdraft")))
                .hasMessage("Insufficient funds");
        balance(from, "50");
        balance(to, "10");
        assertThat(transactions.count()).isEqualTo(2);
    }

    @Test void balanceOverflowRollsBackTransfer() {
        UUID from = account("Ada", "10");
        UUID to = account("Grace", "99999999999999999.99");
        assertThatThrownBy(() -> transfers.transfer(transfer(from, to, "0.01", "overflow")))
                .hasMessage("Account balance limit exceeded");
        balance(from, "10");
        balance(to, "99999999999999999.99");
    }

    @Test void databaseFailureAfterBalanceFlushRollsBackBothAccounts() {
        UUID from = account("Ada", "500");
        UUID to = account("Grace", "10");
        doAnswer(invocation -> {
            entityManager.flush(); // Force both modified balances into SQL before the failure.
            throw new DataAccessResourceFailureException("Simulated history storage failure");
        }).when(transactions).saveAndFlush(any(BankTransaction.class));
        try {
            assertThatThrownBy(() -> transfers.transfer(transfer(from, to, "150", "rollback")))
                    .isInstanceOf(DataAccessResourceFailureException.class);
        } finally { reset(transactions); }
        balance(from, "500");
        balance(to, "10");
        assertThat(transactions.count()).isEqualTo(2);
    }

    @Test void concurrentWithdrawalsNeverOverdraw() throws Exception {
        UUID id = account("Ada", "100");
        List<Boolean> results = concurrent(8, n -> {
            try { banking.withdraw(id, new BigDecimal("30")); return true; }
            catch (com.example.banking.exception.BankingException e) {
                assertThat(e.getMessage()).isEqualTo("Insufficient funds");
                return false;
            }
        });
        assertThat(results.stream().filter(Boolean::booleanValue).count()).isEqualTo(3);
        balance(id, "10");
        assertThat(transactions.count()).isEqualTo(4);
    }

    @Test void concurrentDuplicateTransfersMoveMoneyExactlyOnce() throws Exception {
        UUID from = account("Ada", "500");
        UUID to = account("Grace", "0");
        var results = concurrent(8, n -> transfers.transfer(transfer(from, to, "150", "concurrent")).id());
        assertThat(new HashSet<>(results)).hasSize(1);
        balance(from, "350");
        balance(to, "150");
        assertThat(transactions.count()).isEqualTo(2);
    }

    @Test void concurrentSameKeyAcrossDisjointAccountsRollsBackLoser() throws Exception {
        UUID a = account("A", "100"); UUID b = account("B", "0");
        UUID c = account("C", "100"); UUID d = account("D", "0");
        var results = concurrent(2, n -> {
            try {
                transfers.transfer(transfer(n == 0 ? a : c, n == 0 ? b : d, "10", "global-key"));
                return true;
            } catch (com.example.banking.exception.BankingException e) {
                assertThat(e.getMessage()).contains("different transfer");
                return false;
            }
        });
        assertThat(results).containsExactlyInAnyOrder(true, false);
        assertThat(banking.get(a).balance().add(banking.get(c).balance())).isEqualByComparingTo("190");
        assertThat(banking.get(b).balance().add(banking.get(d).balance())).isEqualByComparingTo("10");
        assertThat(transactions.count()).isEqualTo(3);
    }

    @Test void oppositeDirectionTransfersDoNotDeadlock() throws Exception {
        UUID a = account("A", "100"); UUID b = account("B", "100");
        concurrent(8, n -> transfers.transfer(transfer(n % 2 == 0 ? a : b,
                n % 2 == 0 ? b : a, "1", "opposite-" + n)));
        balance(a, "100"); balance(b, "100");
        assertThat(transactions.count()).isEqualTo(10);
    }

    @Test void exposesOpenApiAndSwagger() throws Exception {
        mvc.perform(get("/v3/api-docs")).andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/transfers']").exists());
        mvc.perform(get("/swagger-ui/index.html")).andExpect(status().isOk());
    }

    UUID account(String owner, String opening) {
        UUID id = banking.create(owner).id();
        if (new BigDecimal(opening).signum() > 0) banking.deposit(id, new BigDecimal(opening));
        return id;
    }
    void balance(UUID id, String expected) {
        assertThat(banking.get(id).balance()).isEqualByComparingTo(expected);
    }
    Transfer transfer(UUID from, UUID to, String amount, String key) {
        return new Transfer(from, to, new BigDecimal(amount), key);
    }
    <T> List<T> concurrent(int count, IntFunction<T> operation) throws Exception {
        try (var pool = Executors.newFixedThreadPool(count)) {
            CountDownLatch ready = new CountDownLatch(count);
            CountDownLatch start = new CountDownLatch(1);
            List<Future<T>> futures = new ArrayList<>();
            for (int i = 0; i < count; i++) {
                int index = i;
                futures.add(pool.submit(() -> {
                    ready.countDown();
                    if (!start.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("Start timed out");
                    return operation.apply(index);
                }));
            }
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            List<T> results = new ArrayList<>();
            for (Future<T> future : futures) results.add(future.get(30, TimeUnit.SECONDS));
            return results;
        }
    }
}
