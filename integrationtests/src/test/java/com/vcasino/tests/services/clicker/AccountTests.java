package com.vcasino.tests.services.clicker;

import com.vcasino.tests.common.Service;
import com.vcasino.tests.model.Row;
import com.vcasino.tests.services.clicker.model.Account;
import com.vcasino.tests.services.clicker.model.AccountResponse;
import com.vcasino.tests.services.clicker.model.Level;
import com.vcasino.tests.services.clicker.model.SectionUpgrades;
import com.vcasino.tests.services.clicker.model.Upgrade;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.Test;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;

public class AccountTests extends GenericClickerTest {

    private static final Logger log = LoggerFactory.getLogger(AccountTests.class);

    @BeforeTest()
    void setup() {
        init(Service.CLICKER);
    }

    @Test(description = "Create clicker account")
    void testCreateClickerAccountTest() throws Exception {
        AccountResponse accountResponse = createAccount();

        compareWithDatabase(accountResponse.getAccount(), authenticatedUser.getUser().getUsername());
        validateSectionUpgrades(accountResponse.getSectionUpgrades());
        validateNoSectionsInDatabase(accountId);
    }

    @Test(description = "Get levels")
    void testGetLevels() throws Exception {
        createAccount();
        String res = performHttpGet(buildUrl("/accounts/levels"), getAttrsWithAuthorization());
        List<Level> levels = Arrays.stream(gson.fromJson(res, Level[].class))
                .sorted(Comparator.comparingInt(Level::getValue))
                .toList();

        assertFalse(levels.isEmpty());
        Long netWorth = null;
        Integer value = null;

        for (int i = 0; i < levels.size(); i++) {
            Level level = levels.get(i);
            assertNotNull(level.getName());
            assertNotNull(level.getNetWorth());
            assertNotNull(level.getValue());

            if (i != 0) {
                assertTrue(netWorth < level.getNetWorth());
                assertEquals(level.getValue(), value + 1);
            }
            netWorth = level.getNetWorth();
            value = level.getValue();
        }
    }

    @Test(description = "After receiving an account, the balance is updated depending on passive income")
    void testBalanceIsUpdatedAfterReceivingAccount() throws Exception {
        createAccount();
        setPassiveEarnPerHourForAccount(36000);

        int wait = 2;
        sleep(wait * 1000);

        Account account = getAccount();

        BigDecimal expectedBalanced = new BigDecimal(account.getPassiveEarnPerSec() * wait);

        assertTrue(account.getBalanceCoins().compareTo(expectedBalanced) >= 0);
        assertTrue(account.getNetWorth().compareTo(expectedBalanced) >= 0);
    }

    @Test(description = "Add coins to account using admin auth")
    void testAdminAddCoinsToAccount() throws Exception {
        createAccount();
        long amount = 10000L;
        addCoinsToAccount(accountId, amount);
        Account account = getAccount();

        assertEquals(amount, account.getNetWorth().longValue());
        assertEquals(account.getLevel(), 2);
    }

    private void compareWithDatabase(Account account, String username) {
        log.info("Compare response with database record");

        Row row = getAccountFromDatabase();
        assertEquals(row.getInt("level"), account.getLevel());
        Row level = getLevelFromDatabase(account.getLevel());
        assertNotNull(row.getLong("id"));
        assertEquals(row.get("username"), username);
        assertEquals(row.getBigDecimal("net_worth"), account.getNetWorth());
        assertEquals(row.getBigDecimal("balance_coins"), account.getBalanceCoins());
        assertEquals(row.getInt("available_taps"), account.getAvailableTaps());
        assertEquals(level.getInt("max_taps"), account.getMaxTaps());
        assertEquals(level.getInt("earn_per_tap"), account.getEarnPerTap());
        assertEquals(level.getInt("taps_recover_per_sec"), account.getTapsRecoverPerSec());
        assertEquals(row.getInt("passive_earn_per_hour"), account.getPassiveEarnPerHour());
        assertNotNull(row.getTimestamp("last_sync_date"));
        assertEquals((int) row.getInt("suspicious_actions_number"), 0);
        assertFalse(row.getBoolean("frozen"));
    }

    protected Row getLevelFromDatabase(Integer level) {
        String query = "SELECT * FROM level l WHERE l.value = " + level;
        List<Row> res = executeQuery(query);
        assertEquals(res.size(), 1);
        return res.getFirst();
    }

    private void validateSectionUpgrades(List<SectionUpgrades> sectionUpgradesList) {
        log.info("Validate section upgrades");
        assertNotNull(sectionUpgradesList);
        assertFalse(sectionUpgradesList.isEmpty());

        Set<Integer> orders = sectionUpgradesList.stream()
                .map(SectionUpgrades::getOrder)
                .collect(Collectors.toSet());

        assertEquals(sectionUpgradesList.size(), orders.size());
        assertTrue(orders.contains(0));
        assertTrue(orders.contains(sectionUpgradesList.size() - 1));

        for (SectionUpgrades sectionUpgrades : sectionUpgradesList) {
            for (Upgrade upgrade : sectionUpgrades.getUpgrades()) {
                assertEquals(upgrade.getLevel(), 0);
                assertEquals(sectionUpgrades.getSection(), upgrade.getSection());
                assertNotNull(upgrade.getAvailable());
            }
        }


    }

    private void validateNoSectionsInDatabase(Long accountId) {
        String query = """
                SELECT * FROM account a
                JOIN account_upgrade au ON au.account_id = a.id
                WHERE a.id = %s""".formatted(accountId);

        List<Row> rows = executeQuery(query);
        assertTrue(rows.isEmpty());
    }

}
