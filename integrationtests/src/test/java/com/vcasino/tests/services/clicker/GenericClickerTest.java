package com.vcasino.tests.services.clicker;

import com.vcasino.tests.common.GenericTest;
import com.vcasino.tests.model.AuthenticationResponse;
import com.vcasino.tests.model.Row;
import com.vcasino.tests.services.clicker.model.Account;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;

@Slf4j
public abstract class GenericClickerTest extends GenericTest {

    protected AuthenticationResponse authenticatedUser;
    protected Long accountId;

    protected Account createAccount() throws Exception {
        log.info("Create new Account");

        authenticatedUser = createNewUser();
        accountId = getUserId(authenticatedUser.getUser().getUsername());
        sleep(1000);

        String res = performHttpGet("/api/v1/clicker/accounts", getAttrsWithAuthorization());
        return fromJson(res, Account.class);
    }

    protected void addCoinsToAccount(Long accountId, Integer amount) throws Exception {
        addCoinsToAccount(accountId, Integer.toUnsignedLong(amount));
    }

    protected void addCoinsToAccount(Long accountId, Long amount) throws Exception {
        log.info("Add coins to account");
        authorizeAdmin();
        var attrs = getAttrsWithAuthorization(true);
        String body = "{\"accountId\":%s,\"addCoins\":%s}".formatted(accountId, amount);
        performHttpPost("/api/v1/clicker/admin/improve", body, attrs);
    }

    protected Row getAccountFromDatabase() {
        assertNotNull(accountId, "accountId is null");
        String query = "SELECT * FROM account a WHERE a.id = " + accountId;
        List<Row> res = executeQuery(query);
        assertEquals(res.size(), 1);
        return res.getFirst();
    }

    protected Account getAccount() throws Exception {
        log.info("Get Account");
        String res = performHttpGet("/api/v1/clicker/accounts", getAttrsWithAuthorization());
        return toAccount(res);
    }

    protected void setPassiveEarnPerHourForAccount(Integer value) {
        log.info("Set {} passive_earn_per_hour for Account#: {}", value, accountId);
        String query = "UPDATE account a SET passive_earn_per_hour = %s WHERE a.id = %s"
                .formatted(value, accountId);
        executeUpdate(query);
    }

    protected Account toAccount(String s) throws Exception {
        return fromJson(s, Account.class);
    }
}
