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
    protected Long userId;

    protected Account createAccount() throws Exception {
        log.info("Create new Account");

        authenticatedUser = createNewUser();
        userId = getUserId(authenticatedUser.getUser().getUsername());
        sleep(1000);

        String res = performHttpGet("/api/v1/clicker/accounts", getAttrsWithAuthorization());
        return fromJson(res, Account.class);
    }

    protected Row getAccountFromDatabase() {
        assertNotNull(userId, "userId is null");
        String query = "SELECT * FROM account a WHERE a.user_id = " + userId;
        List<Row> res = executeQuery(query);
        assertEquals(res.size(), 1);
        return res.getFirst();
    }

    protected Account getAccount() throws Exception {
        log.info("Get Account");
        String res = performHttpGet("/api/v1/clicker/accounts", getAttrsWithAuthorization());
        return fromJson(res, Account.class);
    }

    protected void setPassiveEarnPerHourForAccount(Integer value) {
        log.info("Set {} passive_earn_per_hour for Account with user_id: {}", value, userId);
        String query = "UPDATE account a SET passive_earn_per_hour = %s WHERE a.user_id = %s"
                .formatted(value, userId);
        executeUpdate(query);
    }
}
