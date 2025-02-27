package com.vcasino.tests.services.clicker;

import com.vcasino.tests.common.Service;
import com.vcasino.tests.model.Row;
import com.vcasino.tests.services.clicker.model.AccountResponse;
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
    void setup() {
        init(Service.CLICKER);
    }

    @Test(description = "Buy upgrade")
    void testBuyUpgrade() throws Exception {
        AccountResponse accountResponse = createAccount();
        accountResponse.getSectionUpgrades().sort(Comparator.comparingInt(SectionUpgrades::getOrder));
        SectionUpgrades selectedSection = accountResponse.getSectionUpgrades().getFirst();

        Upgrade toBuy = null;
        for (Upgrade upgrade : selectedSection.getUpgrades()) {
            if (upgrade.getAvailable()) {
                toBuy = upgrade;
                break;
            }
        }

        assertNotNull(toBuy, "Not found upgrade to update");
        log.info("Chosen to buy {}", toBuy);

        addCoinsToAccount(accountId, toBuy.getPrice());

        String body = "{\"upgradeName\":\"%s\"}".formatted(toBuy.getName());

        String res = performHttpPost(buildUrl("/upgrades"), body, getAttrsWithAuthorization(false));
        AccountResponse updatedAccountRes = toAccountResponse(res);

        assertEquals(updatedAccountRes.getAccount().getBalanceCoins().longValue(), 0);
        assertEquals(updatedAccountRes.getSectionUpgrades().size(), accountResponse.getSectionUpgrades().size());
        for (SectionUpgrades su : accountResponse.getSectionUpgrades()) {
            int size = updatedAccountRes.getSectionUpgrades().stream()
                    .filter(s -> s.getOrder().equals(su.getOrder()))
                    .toList().getFirst().getUpgrades().size();
            assertEquals(size, su.getUpgrades().size());
        }

        assertTrue(updatedAccountRes.getAccount().getPassiveEarnPerSec() > accountResponse.getAccount().getPassiveEarnPerSec());

        log.info("Check if upgrade was updated");

        String boughtUpgradeName = toBuy.getName();
        Optional<Upgrade> optionalUpdatedUpgrade = updatedAccountRes.getSectionUpgrades()
                .stream()
                .filter(su -> su.getSection().equals(selectedSection.getSection()))
                .findFirst().get()
                .getUpgrades().stream()
                .filter(u -> u.getName().equals(boughtUpgradeName))
                .findFirst();

        assertTrue(optionalUpdatedUpgrade.isPresent());
        Upgrade updatedUpgrade = optionalUpdatedUpgrade.get();

        assertEquals(updatedUpgrade.getLevel(), toBuy.getLevel() + 1);
        assertTrue(updatedUpgrade.getPrice() > toBuy.getPrice());
        assertTrue(updatedUpgrade.getProfitPerHour() > toBuy.getProfitPerHour());

        log.info("Validate bought upgrade in database");

        List<Row> rows = getAllUpgradesFromDatabaseByAccount(accountId);
        assertEquals(rows.size(), 1);
        Row row = rows.getFirst();
        assertEquals(row.get("name"), toBuy.getName());
        assertEquals(row.getInt("level"), toBuy.getLevel() + 1);
    }

    @Test(description = "Buy all upgrades")
    void testBuyAllUpgrades() throws Exception {
        AccountResponse accountResponse = createAccount();

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

        log.info("Start buying upgrades");

        int previousDatabaseRecordAmount = 0;
        boolean canUpdate = true;

        while (totalElements > 0 && canUpdate) {
            canUpdate = false;
            List<Upgrade> toBuy = new ArrayList<>();

            int newUpgrades = 0;
            for (SectionUpgrades section : accountResponse.getSectionUpgrades()) {
                for (Upgrade upgrade : section.getUpgrades()) {
                    if (upgrade.getAvailable()) {
                        toBuy.add(upgrade);
                        canUpdate = true;
                        if (upgrade.getLevel() == 0) {
                            newUpgrades++;
                        }
                    }
                }
            }

            log.info("Found {} upgrades to update", toBuy.size());

            for (Upgrade upgrade : toBuy) {
                String body = "{\"upgradeName\":\"%s\"}".formatted(upgrade.getName());

                String res = performHttpPost(buildUrl("/upgrades"), body, getAttrsWithAuthorization(false));
                accountResponse = toAccountResponse(res);
            }

            totalElements -= toBuy.size();


            if (newUpgrades > 0) {
                int recordAmount = getAllUpgradesFromDatabaseByAccount(accountId).size();
                assertEquals(recordAmount, previousDatabaseRecordAmount + newUpgrades);
                previousDatabaseRecordAmount = recordAmount;
            }
        }

        assertEquals(totalElements, 0,
                "Not all upgrades were bought. Left " + totalElements + " elements. Current account status: " + accountResponse.toString());

        Integer passiveEarnPerHour = 0;
        for (SectionUpgrades su : accountResponse.getSectionUpgrades()) {
            for (Upgrade u : su.getUpgrades()) {
                assertTrue(u.getMaxLevel(), "Not max level " + u);
                passiveEarnPerHour += u.getProfitPerHour();
            }
        }

        assertEquals(passiveEarnPerHour, accountResponse.getAccount().getPassiveEarnPerHour());
    }

    private List<Row> getAllUpgradesFromDatabaseByAccount(Long accountId) {
        String query = """
                SELECT u.name, u.level FROM account a
                JOIN account_upgrade au ON au.account_id = a.id
                JOIN upgrade u ON au.upgrade_name = u.name AND au.upgrade_level = u.level
                WHERE a.id = %s""".formatted(accountId);

        return executeQuery(query);
    }

}
