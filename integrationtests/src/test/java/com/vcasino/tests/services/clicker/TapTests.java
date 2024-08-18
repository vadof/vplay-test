package com.vcasino.tests.services.clicker;

import com.vcasino.tests.common.Service;
import com.vcasino.tests.model.Row;
import com.vcasino.tests.services.clicker.model.Account;
import lombok.extern.slf4j.Slf4j;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.Test;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.TimeZone;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

@Slf4j
public class TapTests extends GenericClickerTest {

    @BeforeTest
    void setup() throws Exception {
        init(Service.CLICKER);
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
    }

    @Test(description = "Tap works correctly")
    void tap() throws Exception {
        Account account = createAccount();

        sleep(1000);
        int amount = account.getAvailableTaps();
        int availableTaps = 0;
        long timestamp = Instant.now().getEpochSecond();

        Account accountAfterTap = sendTapRequest(amount, availableTaps, timestamp);

        validateResponse(account, accountAfterTap, amount);
        validateDatabaseChanges(accountAfterTap, timestamp);
    }

    @Test(description = "Taps recovering correctly")
    void tapsRecoveringCorrectly() throws Exception {
        Account account = createAccount();

        sleep(1000);
        int amount = account.getAvailableTaps();
        int availableTaps = 0;
        long timestamp = Instant.now().getEpochSecond();

        sendTapRequest(amount, availableTaps, timestamp);

        int wait = 5;
        sleep(wait * 1000);
        int expectedAvailable = wait * account.getTapsRecoverPerSec();

        account = getAccount();

        log.info("Expected number of available taps: {}, Actual: {}", expectedAvailable, account.getAvailableTaps());
        assertTrue(account.getAvailableTaps() >= expectedAvailable);
    }

    @Test(description = "If amount is more than can be taped over time, suspicious action is added")
    void cannotTapMoreThanPossible() throws Exception {
        Account account = createAccount();

        sleep(1000);
        int amount = account.getAvailableTaps();
        int availableTaps = 0;
        long timestamp = Instant.now().getEpochSecond();

        Account accountAfterFirstTap = sendTapRequest(amount, availableTaps, timestamp);

        int wait = 5;
        sleep(wait * 1000);
        int available = wait * account.getTapsRecoverPerSec();

        Account accountAfterSecondTap = sendTapRequest(available * 2, 0, timestamp + wait);

        assertEquals(accountAfterSecondTap.getBalanceCoins(), accountAfterFirstTap.getBalanceCoins());
        assertEquals(accountAfterSecondTap.getNetWorth(), accountAfterFirstTap.getNetWorth());
        assertTrue(accountAfterSecondTap.getAvailableTaps() > 0);

        Row row = getAccountFromDatabase();
        assertEquals(row.getInt("suspicious_actions_number"), 1);
    }

    @Test(description = "If 2 tap requests with the same timestamp are sent, the second request is ignored")
    void sameTimestampTapIsIgnored() throws Exception {
        Account account = createAccount();

        sleep(1000);
        int amount = account.getAvailableTaps();
        int availableTaps = 0;
        long timestamp = Instant.now().getEpochSecond();

        Account accountAfterFirstTap = sendTapRequest(amount, availableTaps, timestamp);

        int wait = 2;
        sleep(wait * 1000);
        int available = wait * account.getTapsRecoverPerSec();

        Account accountAfterSecondTap = sendTapRequest(available, 0, timestamp);

        assertEquals(accountAfterSecondTap.getBalanceCoins(), accountAfterFirstTap.getBalanceCoins());
        assertEquals(accountAfterSecondTap.getNetWorth(), accountAfterFirstTap.getNetWorth());
    }

    @Test(description = "Earn per hour is updated after tap")
    void earnPerHourIsUpdatedAfterTap() throws Exception {
        Account account = createAccount();
        sleep(1000);

        setPassiveEarnPerHourForAccount(3600);

        int amount = account.getAvailableTaps();
        int availableTaps = 0;
        long timestamp = Instant.now().getEpochSecond();

        Account accountAfterFirstTap = sendTapRequest(amount, availableTaps, timestamp);

        int wait = 2;
        sleep(wait * 1000);
        int available = wait * account.getTapsRecoverPerSec();

        Account accountAfterSecondTap = sendTapRequest(available, 0, timestamp + wait);

        BigDecimal earnedAfterSecondTap = new BigDecimal(available * account.getEarnPerTap());

        BigDecimal balanceWithoutPassiveEarn = accountAfterFirstTap.getBalanceCoins().add(earnedAfterSecondTap);
        double passiveEarnPerSec = accountAfterFirstTap.getPassiveEarnPerSec() * wait;
        BigDecimal balanceWithPassiveEarn = balanceWithoutPassiveEarn.add(new BigDecimal(passiveEarnPerSec));

        assertEquals(accountAfterSecondTap.getBalanceCoins(), balanceWithPassiveEarn);
        assertEquals(accountAfterSecondTap.getNetWorth(), balanceWithPassiveEarn);
    }



    private void validateResponse(Account account, Account accountAfterTap, int amount) {
        log.info("Validate tap response");
        assertEquals(accountAfterTap.getAvailableTaps(), 0);
        assertEquals(accountAfterTap.getMaxTaps(), account.getMaxTaps());
        assertEquals(accountAfterTap.getBalanceCoins().longValue(), new BigDecimal(amount * account.getEarnPerTap()).longValue());
        assertEquals(accountAfterTap.getNetWorth().longValue(), accountAfterTap.getBalanceCoins().longValue());
    }

    private void validateDatabaseChanges(Account accountAfterTap, long timestamp) {
        log.info("Validate database changes");

        Timestamp ts = new Timestamp(timestamp * 1000);
        Row row = getAccountFromDatabase();

        assertEquals(accountAfterTap.getBalanceCoins(), row.getBigDecimal("balance_coins"));
        assertEquals(accountAfterTap.getNetWorth(), row.getBigDecimal("net_worth"));
        assertEquals(accountAfterTap.getAvailableTaps(), row.getInt("available_taps"));
        assertEquals(ts, row.getTimestamp("last_sync_date"));
    }

    private Account sendTapRequest(Integer amount, Integer availableTaps, Long timestamp) throws Exception {
        log.info("Send tap request");
        String body = "{\"amount\": %s,\"availableTaps\": %s,\"timestamp\": %s}"
                .formatted(amount, availableTaps, timestamp);
        String res = performHttpPost("/api/v1/clicker/tap", body, getAttrsWithAuthorization(), 200);
        return fromJson(res, Account.class);
    }

}
