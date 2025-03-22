package com.vcasino.tests.services.wallet;

import com.vcasino.tests.common.GenericTest;
import com.vcasino.tests.model.AuthenticationResponse;
import com.vcasino.tests.model.Row;
import com.vcasino.tests.services.clicker.model.Account;
import com.vcasino.tests.services.wallet.model.CurrencyConversionPayload;
import com.vcasino.tests.services.wallet.model.OutboxEvent;
import com.vcasino.tests.services.wallet.model.Wallet;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;

@Slf4j
public abstract class GenericWalletTest extends GenericTest {

    protected AuthenticationResponse authenticatedUser;
    protected Long walletId;

    protected Wallet createWallet() throws Exception {
        log.info("Create new Wallet");

        authenticatedUser = createNewUser();
        walletId = getUserId(authenticatedUser.getUser().getUsername());
        sleep(1000);

        return getWalletFromDatabase();
    }

    protected BigDecimal getClickerBalance() throws Exception {
        String res = performHttpGet("/api/v1/clicker/accounts", getAttrsWithAuthorization());
        return fromJson(res, Account.class).getBalanceCoins();
    }

    protected void addCoinsToClickerAccount(BigDecimal amount) throws Exception {
        log.info("Add coins to Account");
        authorizeAdmin();
        var attrs = getAttrsWithAuthorization(true);
        String body = "{\"accountId\":%s,\"addCoins\":%s}".formatted(walletId, amount);
        performHttpPost("/api/v1/clicker/admin/accounts/improve", body, attrs);
    }

    protected void addBalanceToWallet(BigDecimal amount) {
        log.info("Add {} VDollars to Wallet balance", amount);
        assertNotNull(walletId, "walletId is null");
        String query = "UPDATE wallet SET balance = %s WHERE id = %s".formatted(amount, walletId);
        executeUpdate(query);
    }

    protected <T> List<OutboxEvent<T>> getEventsFromDatabase(Long aggregateId, Class<T> payloadClass) throws Exception {
        assertNotNull(walletId, "walletId is null");
        String query = "SELECT * FROM outbox_event e WHERE e.aggregate_id = " + aggregateId;
        List<Row> res = executeQuery(query);

        log.info("Found {} events", res.size());

        T payload = null;

        assertTrue(payloadClass.equals(CurrencyConversionPayload.class));

        List<OutboxEvent<T>> events = new ArrayList<>();
        for (Row row : res) {
            if (payloadClass.equals(CurrencyConversionPayload.class)) {
                payload = fromJson(row.get("payload"), payloadClass);
            }

            events.add(OutboxEvent.<T>builder()
                    .id(row.getUUID("id"))
                    .aggregateId(row.getLong("aggregate_id"))
                    .type(row.get("type"))
                    .payload(payload)
                    .status(row.get("status"))
                    .applicant(row.get("applicant"))
                    .createdAt(row.getInstant("created_at"))
                    .modifiedAt(row.getInstant("modified_at"))
                    .version(row.getInt("version"))
                    .build());
        }

        return events;
    }

    protected Wallet getWalletFromDatabase() {
        assertNotNull(walletId, "walletId is null");
        String query = "SELECT * FROM wallet w WHERE w.id = " + walletId;
        List<Row> res = executeQuery(query);
        assertEquals(res.size(), 1);

        Row walletRow = res.getFirst();
        return Wallet.builder()
                .id(walletRow.getLong("id"))
                .balance(walletRow.getBigDecimal("balance"))
                .reserved(walletRow.getBigDecimal("reserved"))
                .updatedAt(walletRow.getInstant("updated_at"))
                .frozen(walletRow.getBoolean("frozen"))
                .version(walletRow.getInt("version"))
                .build();
    }
}