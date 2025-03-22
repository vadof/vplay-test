package com.vcasino.tests.services.wallet;

import com.vcasino.tests.common.Service;
import com.vcasino.tests.model.Response;
import com.vcasino.tests.services.clicker.model.Account;
import com.vcasino.tests.services.wallet.model.CurrencyConversionPayload;
import com.vcasino.tests.services.wallet.model.OutboxEvent;
import com.vcasino.tests.services.wallet.model.Wallet;
import lombok.extern.slf4j.Slf4j;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.Test;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.testng.AssertJUnit.assertEquals;
import static org.testng.AssertJUnit.assertNotNull;

@Slf4j
public class CurrencyConversionTest extends GenericWalletTest {

    private final String VCOINS_TO_VDOLLARS_URL = "/api/v1/clicker/currency/vcoins/vdollars";
    private final String VDOLLARS_TO_VCOINS_URL = "/api/v1/clicker/currency/vdollars/vcoins";

    @BeforeTest
    void setup() {
        init(Service.WALLET);
    }

    @Test(description = "Convert VCoins to VDollars")
    void convertVCoinsToVDollars() throws Exception {
        createWallet();
        Random random = new Random();

        List<BigDecimal> randomValues = new ArrayList<>(5);
        BigDecimal convertTotalVCoins = BigDecimal.ZERO;
        BigDecimal convertTotalVCoinsRounded = BigDecimal.ZERO;
        for (int i = 0; i < 5; i++) {
            BigDecimal randomValue = new BigDecimal(random.nextInt(100000, 1000001));
            randomValues.add(randomValue);
            convertTotalVCoins = convertTotalVCoins.add(randomValue);
            convertTotalVCoinsRounded = convertTotalVCoinsRounded.add(roundToNearestThousand(randomValue));
        }

        BigDecimal expectedAccountBalance = convertTotalVCoins.subtract(convertTotalVCoinsRounded);
        BigDecimal expectedWalletBalance = convertTotalVCoinsRounded.divide(new BigDecimal("100000"), 2, RoundingMode.DOWN);

        log.info("Convert total: {} VCoins | Expected Account balance: {} VCoins | Expected Wallet balance: {} VDollars",
                convertTotalVCoins, expectedAccountBalance, expectedWalletBalance);

        addCoinsToClickerAccount(convertTotalVCoins);

        Account account = null;
        for (BigDecimal coins : randomValues) {
            String body = objToJson(Map.of("amount", coins.toString()));
            String s = performHttpPost(VCOINS_TO_VDOLLARS_URL, body, getAttrsWithAuthorization());
            account = fromJson(s, Account.class);
        }

        sleep(5000);

        assertEquals(0, expectedAccountBalance.compareTo(account.getBalanceCoins()));

        Wallet walletFromDatabase = getWalletFromDatabase();
        assertEquals(0, expectedWalletBalance.compareTo(walletFromDatabase.getBalance()));

        List<OutboxEvent<CurrencyConversionPayload>> outboxEvents = getEventsFromDatabase(walletId, CurrencyConversionPayload.class);
        assertEquals(5, outboxEvents.size());

        BigDecimal totalEventSum = BigDecimal.ZERO;

        for (OutboxEvent<CurrencyConversionPayload> event : outboxEvents) {
            assertEquals(event.getType(), "CURRENCY_CONVERSION");
            assertEquals(event.getStatus(), "COMPLETED");
            assertEquals(event.getApplicant(), "CLICKER");
            assertNotNull(event.getCreatedAt());
            assertNotNull(event.getModifiedAt());
            assertEquals("VCoin", event.getPayload().getFrom());
            assertEquals("VDollar", event.getPayload().getTo());
            totalEventSum = totalEventSum.add(event.getPayload().getAmount());
        }

        assertEquals(0, totalEventSum.compareTo(convertTotalVCoinsRounded));
    }

    @Test(description = "Convert VCoins to VDollars Not Enough Coins")
    void convertVCoinsToVDollarsNotEnoughCoins() throws Exception {
        createWallet();

        BigDecimal coins = new BigDecimal("100000");
        addCoinsToClickerAccount(coins.subtract(new BigDecimal("10000")));

        String body = objToJson(Map.of("amount", coins.toString()));
        performHttpPost(VCOINS_TO_VDOLLARS_URL, body, getAttrsWithAuthorization(), 400);
    }

    @Test(description = "Convert VCoins to VDollars minimum amount hasn't been reached")
    void convertVCoinsToVDollarsMinimumAmount() throws Exception {
        createWallet();
        BigDecimal coins = new BigDecimal("50000");

        addCoinsToClickerAccount(new BigDecimal("100000"));

        String body = objToJson(Map.of("amount", coins.toString()));

        performHttpPost(VCOINS_TO_VDOLLARS_URL, body, getAttrsWithAuthorization(), 400);
    }

    @Test(description = "Convert VDollars to VCoins")
    void convertVDollarsToVCoins() throws Exception {
        createWallet();
        Random random = new Random();

        List<BigDecimal> randomValues = new ArrayList<>(5);
        BigDecimal convertTotalVDollars = BigDecimal.ZERO;
        for (int i = 0; i < 5; i++) {
            BigDecimal randomValue = new BigDecimal(random.nextInt(100001, 1000001))
                    .divide(new BigDecimal("100000"), 2, RoundingMode.DOWN);
            randomValues.add(randomValue);
            convertTotalVDollars = convertTotalVDollars.add(randomValue);
        }

        BigDecimal expectedAccountBalance = convertTotalVDollars.multiply(new BigDecimal("90000"));
        BigDecimal expectedWalletBalance = BigDecimal.ZERO;

        log.info("Convert total: {} VDollars | Expected Account balance: {} VCoins | Expected Wallet balance: {} VDollars",
                convertTotalVDollars, expectedAccountBalance, expectedWalletBalance);

        addBalanceToWallet(convertTotalVDollars);

        Account account = null;
        BigDecimal updatedWalletBalance = BigDecimal.ZERO;
        for (BigDecimal coins : randomValues) {
            String body = objToJson(Map.of("amount", coins.toString()));
            Response res = jsonToObject(performHttpPost(VDOLLARS_TO_VCOINS_URL, body, getAttrsWithAuthorization()));
            account = res.get("account", Account.class);
            updatedWalletBalance = res.get("updatedWalletBalance", BigDecimal.class);
        }

        sleep(5000);

        assertEquals(0, expectedAccountBalance.compareTo(account.getBalanceCoins()));

        Wallet walletFromDatabase = getWalletFromDatabase();
        assertEquals(0, expectedWalletBalance.compareTo(walletFromDatabase.getBalance()));
        assertEquals(0, expectedWalletBalance.compareTo(updatedWalletBalance));

        List<OutboxEvent<CurrencyConversionPayload>> outboxEvents = getEventsFromDatabase(walletId, CurrencyConversionPayload.class);
        assertEquals(5, outboxEvents.size());

        BigDecimal totalEventSum = BigDecimal.ZERO;

        for (OutboxEvent<CurrencyConversionPayload> event : outboxEvents) {
            assertEquals(event.getType(), "CURRENCY_CONVERSION");
            assertEquals(event.getStatus(), "COMPLETED");
            assertEquals(event.getApplicant(), "CLICKER");
            assertNotNull(event.getCreatedAt());
            assertNotNull(event.getModifiedAt());
            assertEquals("VDollar", event.getPayload().getFrom());
            assertEquals("VCoin", event.getPayload().getTo());
            totalEventSum = totalEventSum.add(event.getPayload().getAmount());
        }

        assertEquals(0, totalEventSum.compareTo(convertTotalVDollars));
    }

    private BigDecimal roundToNearestThousand(BigDecimal amount) {
        return amount.divide(new BigDecimal("1000"), 0, RoundingMode.DOWN)
                .multiply(new BigDecimal("1000"));
    }

    @Test(description = "Convert VDollars to VCoins Not Enough VDollars")
    void convertVDollarsToVCoinsNotEnoughVDollars() throws Exception {
        createWallet();
        BigDecimal dollars = BigDecimal.ONE;
        addBalanceToWallet(new BigDecimal("0.99"));

        String body = objToJson(Map.of("amount", dollars.toString()));
        performHttpPost(VDOLLARS_TO_VCOINS_URL, body, getAttrsWithAuthorization(), 400);
    }

    @Test(description = "Convert VDollars to VCoins minimum amount hasn't been reached")
    void convertVDollarsToVCoinsMinimumAmount() throws Exception {
        createWallet();
        BigDecimal dollars = new BigDecimal("0.99");
        addBalanceToWallet(new BigDecimal("10.00"));

        String body = objToJson(Map.of("amount", dollars.toString()));

        performHttpPost(VCOINS_TO_VDOLLARS_URL, body, getAttrsWithAuthorization(), 400);
    }
}
