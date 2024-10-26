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

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;

public class UpgradeTests extends GenericClickerTest {

    private static final Logger log = LoggerFactory.getLogger(AccountTests.class);

    @BeforeTest()
    void setup() throws Exception {
        init(Service.CLICKER);
    }

    @Test(description = "Buy upgrade")
    void buyUpgrade() throws Exception {
        Account account = createAccount();
        account.getSectionUpgrades().sort(Comparator.comparingInt(SectionUpgrades::getOrder));
        SectionUpgrades selectedSection = account.getSectionUpgrades().getFirst();

        Upgrade toUpdate = null;
        for (Upgrade upgrade : selectedSection.getUpgrades()) {
            if (upgrade.getAvailable()) {
                toUpdate = upgrade;
                break;
            }
        }
        assertNotNull(toUpdate, "Not found upgrade to update");
        log.info("Chosen to buy {}", toUpdate);

        addCoinsToAccount(accountId, toUpdate.getPrice());

        String body = "{\"upgradeName\":\"%s\", \"upgradeLevel\":%s}"
                .formatted(toUpdate.getName(), toUpdate.getLevel());

        String res = performHttpPost("/api/v1/clicker/upgrades", body, getAttrsWithAuthorization(false));
        Account updatedAccount = toAccount(res);

        assertEquals(updatedAccount.getBalanceCoins().longValue(), 0);
        assertEquals(updatedAccount.getSectionUpgrades().size(), account.getSectionUpgrades().size());
        for (SectionUpgrades su : account.getSectionUpgrades()) {
            int size = updatedAccount.getSectionUpgrades()
                    .stream().filter(s -> s.getOrder().equals(su.getOrder()))
                    .toList().getFirst().getUpgrades().size();
            assertEquals(size, su.getUpgrades().size());
        }

        assertTrue(updatedAccount.getPassiveEarnPerSec() > account.getPassiveEarnPerSec());

        log.info("Check if upgrade was updated");

        String toUpdateName = toUpdate.getName();
        Optional<Upgrade> optionalUpdatedUpgrade = updatedAccount.getSectionUpgrades()
                .stream()
                .filter(su -> su.getSection().equals(selectedSection.getSection()))
                .findFirst().get()
                .getUpgrades().stream()
                .filter(u -> u.getName().equals(toUpdateName))
                .findFirst();

        assertTrue(optionalUpdatedUpgrade.isPresent());
        Upgrade updatedUpgrade = optionalUpdatedUpgrade.get();

        assertEquals(updatedUpgrade.getLevel(), toUpdate.getLevel() + 1);
        assertTrue(updatedUpgrade.getPrice() > toUpdate.getPrice());
        assertTrue(updatedUpgrade.getProfitPerHour() > toUpdate.getProfitPerHour());
    }

    @Test(description = "Buy all upgrades")
    void buyAllUpgrades() throws Exception {
        Account account = createAccount();

        log.info("Get all upgrades and their total price");

        String query = """
                SELECT
                    COUNT(*) AS total_elements,
                    SUM(u.price) AS total_price
                FROM
                    upgrade u
                WHERE
                    u.price IS NOT NULL;
                """;

        List<Row> rows = executeQuery(query);
        assertEquals(rows.size(), 1);
        Long totalPrice = rows.getFirst().getLong("total_price");
        Long totalElements = rows.getFirst().getLong("total_elements");

        log.info("Total elements: {}, Total price: {}", totalPrice, totalElements);

        addCoinsToAccount(accountId, totalPrice);

        log.info("Starting upgrades");

        boolean canUpdate = true;
        while (totalElements > 0 && canUpdate) {
            canUpdate = false;
            List<Upgrade> toUpdate = new ArrayList<>();

            for (SectionUpgrades section : account.getSectionUpgrades()) {
                for (Upgrade upgrade : section.getUpgrades()) {
                    if (upgrade.getAvailable()) {
                        toUpdate.add(upgrade);
                        canUpdate = true;
                    }
                }
            }

            log.info("Found {} upgrades to update", toUpdate.size());

            for (Upgrade upgrade : toUpdate) {
                String body = "{\"upgradeName\":\"%s\", \"upgradeLevel\":%s}"
                        .formatted(upgrade.getName(), upgrade.getLevel());

                String res = performHttpPost("/api/v1/clicker/upgrades", body, getAttrsWithAuthorization(false));
                account = toAccount(res);
            }

            totalElements -= toUpdate.size();
        }

        assertEquals(totalElements, 0,
                "Not all upgrades were bought. Left " + totalElements + " elements. Current account status: " + account.toString());

        Integer passiveEarnPerHour = 0;
        for (SectionUpgrades su : account.getSectionUpgrades()) {
            for (Upgrade u : su.getUpgrades()) {
                assertTrue(u.getMaxLevel(), "Not max level " + u);
                passiveEarnPerHour += u.getProfitPerHour();
            }
        }

        assertEquals(passiveEarnPerHour, account.getPassiveEarnPerHour());
    }

}
