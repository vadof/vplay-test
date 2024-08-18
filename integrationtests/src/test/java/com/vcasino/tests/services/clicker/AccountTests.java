package com.vcasino.tests.services.clicker;

import com.vcasino.tests.common.Service;
import com.vcasino.tests.model.Row;
import com.vcasino.tests.services.clicker.model.Account;
import com.vcasino.tests.services.clicker.model.SectionUpgrades;
import com.vcasino.tests.services.clicker.model.Upgrade;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.Test;

import java.math.BigDecimal;
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
    void setup() throws Exception {
        init(Service.CLICKER);
    }

    @Test(description = "Create clicker account")
    void createClickerAccount() throws Exception {
        Account account = createAccount();

        compareWithDatabase(account);
        validateSectionUpgrades(account.getSectionUpgrades());
    }

    @Test(description = "After receiving an account, the balance is updated depending on passive income")
    void balanceIsUpdatedAfterReceivingAccount() throws Exception {
        createAccount();
        setPassiveEarnPerHourForAccount(36000);

        int wait = 2;
        sleep(wait * 1000);

        Account account = getAccount();

        BigDecimal expectedBalanced = new BigDecimal(account.getPassiveEarnPerSec() * wait);

        assertTrue(account.getBalanceCoins().compareTo(expectedBalanced) >= 0);
        assertTrue(account.getNetWorth().compareTo(expectedBalanced) >= 0);
    }

    private void compareWithDatabase(Account account) {
        log.info("Compare response with database record");

        Row row = getAccountFromDatabase();
        assertNotNull(row.getLong("user_id"));
        assertEquals(row.getInt("level"), account.getLevel());
        assertEquals(row.getBigDecimal("net_worth"), account.getNetWorth());
        assertEquals(row.getBigDecimal("balance_coins"), account.getBalanceCoins());
        assertEquals(row.getInt("available_taps"), account.getAvailableTaps());
        assertEquals(row.getInt("max_taps"), account.getMaxTaps());
        assertEquals(row.getInt("earn_per_tap"), account.getEarnPerTap());
        assertEquals(row.getInt("taps_recover_per_sec"), account.getTapsRecoverPerSec());
        assertEquals(row.getInt("passive_earn_per_hour"), account.getPassiveEarnPerHour());
        assertNotNull(row.getTimestamp("last_sync_date"));
        assertEquals((int) row.getInt("suspicious_actions_number"), 0);
        assertFalse(row.getBoolean("frozen"));
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

}
