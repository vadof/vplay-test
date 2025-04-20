package com.vcasino.tests.services.bet.model;

import com.vcasino.tests.common.Service;
import com.vcasino.tests.model.Response;
import com.vcasino.tests.model.Row;
import com.vcasino.tests.services.bet.GenericBetTest;
import com.vcasino.tests.services.bet.model.dto.MarketDto;
import com.vcasino.tests.services.bet.model.dto.MarketsByCategory;
import lombok.extern.slf4j.Slf4j;
import org.testng.Assert;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.Test;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.testng.AssertJUnit.assertEquals;
import static org.testng.AssertJUnit.assertTrue;

@Slf4j
public class BetTest extends GenericBetTest {

    @BeforeTest()
    void setup() {
        init(Service.BET);
    }

    @Test(description = "Place each type of bet")
    void placeBet() throws Exception {
        authorizeAdmin();

        Long userId = getUserId(createNewUser().getUser().getUsername());
        addBalanceToWallet(BigDecimal.valueOf(1000), userId);

        Match match = addMatch(getAttrsWithAuthorization(true));

        redisFlushCache();
        String s = performHttpGet("/api/v1/bet/matches/" + match.getId(), getDefaultAttrs());
        List<MarketsByCategory> marketsByCategory = Arrays.asList(fromJson(s, MarketsByCategory[].class));

        List<MarketDto> markets = marketsByCategory.stream()
                .flatMap(item -> item.getMarketPairs().stream())
                .flatMap(marketPair -> marketPair.getMarkets().stream())
                .toList();

        var authAttrs = getAttrsWithAuthorization();
        double finalBalance = 0;
        for (MarketDto market : markets) {
            String body = objToJson(Map.of(
                    "marketId", market.getId().toString(),
                    "odds", market.getOdds().toString(),
                    "amount", "1.00",
                    "acceptAllOddsChanges", "true"
            ));

            Response response = jsonToObject(performHttpPost("/api/v1/bet/place", body, authAttrs));
            assertTrue(response.getBoolean("betPlaced"));
            finalBalance = response.getDouble("updatedBalance");
        }


        log.info("""
                Initial Balance:   1000
                Total bets placed: {}
                Final balance:     {}
                """, markets.size(), finalBalance);

        assertEquals(1000 - markets.size(), (int) finalBalance);
    }

    @Test(description = "Get bet history")
    void getBetHistory() throws Exception {
        authorizeAdmin();

        Long userId = getUserId(createNewUser().getUser().getUsername());
        addBalanceToWallet(BigDecimal.valueOf(2), userId);

        Match match = addMatch(getAttrsWithAuthorization(true));

        redisFlushCache();
        String s = performHttpGet("/api/v1/bet/matches/" + match.getId(), getDefaultAttrs());
        List<MarketsByCategory> marketsByCategory = Arrays.asList(fromJson(s, MarketsByCategory[].class));

        List<MarketDto> markets = marketsByCategory.getFirst().getMarketPairs().getFirst().getMarkets();

        log.info("Place 2 bets");

        var authAttrs = getAttrsWithAuthorization();
        for (MarketDto market : markets) {
            String body = objToJson(Map.of(
                    "marketId", market.getId().toString(),
                    "odds", market.getOdds().toString(),
                    "amount", "1.00",
                    "acceptAllOddsChanges", "true"
            ));

            Response response = jsonToObject(performHttpPost("/api/v1/bet/place", body, authAttrs));
            assertTrue(response.getBoolean("betPlaced"));
        }

        Response response = jsonToObject(performHttpGet("/api/v1/bet/history", authAttrs));
        assertEquals(2, response.getList("data", Object.class).size());
    }

    @Test(description = "Market result updates balance")
    void marketResultUpdatesBalance() throws Exception {
        authorizeAdmin();
        var adminAttrs = getAttrsWithAuthorization(true);

        Long userId = getUserId(createNewUser().getUser().getUsername());
        addBalanceToWallet(BigDecimal.valueOf(3), userId);

        Match match = addMatch(adminAttrs);

        redisFlushCache();
        String s = performHttpGet("/api/v1/bet/matches/" + match.getId(), getDefaultAttrs());
        List<MarketsByCategory> marketsByCategory = Arrays.asList(fromJson(s, MarketsByCategory[].class));

        List<MarketDto> markets = marketsByCategory.stream()
                .flatMap(item -> item.getMarketPairs().stream())
                .flatMap(marketPair -> marketPair.getMarkets().stream())
                .limit(3)
                .toList();

        var authAttrs = getAttrsWithAuthorization();

        BigDecimal estimatedFinalBalance = markets.get(0).getOdds().add(BigDecimal.ONE);

        for (MarketDto market : markets) {
            String body = objToJson(Map.of(
                    "marketId", market.getId().toString(),
                    "odds", market.getOdds().toString(),
                    "amount", "1.00",
                    "acceptAllOddsChanges", "true"
            ));

            Response response = jsonToObject(performHttpPost("/api/v1/bet/place", body, authAttrs));
            assertTrue(response.getBoolean("betPlaced"));
        }

        String winBody = "{\"marketIds\": [%s], \"marketResult\": \"WIN\"}".formatted(markets.get(0).getId());
        String cancelledBody = "{\"marketIds\": [%s], \"marketResult\": \"CANCELLED\"}".formatted(markets.get(1).getId());
        String lossBody = "{\"marketIds\": [%s], \"marketResult\": \"LOSS\"}".formatted(markets.get(2).getId());


        performHttpPost("/api/v1/bet/admin/markets/result", winBody, adminAttrs);
        performHttpPost("/api/v1/bet/admin/markets/result", cancelledBody, adminAttrs);
        performHttpPost("/api/v1/bet/admin/markets/result", lossBody, adminAttrs);

        sleep(1000);

        BigDecimal balance = getBalance(userId);

        assertEquals(estimatedFinalBalance.doubleValue(), balance.doubleValue());
    }

    private BigDecimal getBalance(Long userId) {
        String query = "SELECT balance FROM wallet w WHERE w.id = " + userId;
        List<Row> res = executeQuery(query, "wallet");
        Assert.assertEquals(res.size(), 1);

        Row row = res.getFirst();
        return row.getBigDecimal("balance");
    }

    private void addBalanceToWallet(BigDecimal amount, Long userId) {
        log.info("Add {} VDollars to Wallet balance", amount);
        String query = "UPDATE wallet SET balance = %s WHERE id = %s".formatted(amount, userId);
        executeUpdate(query, "wallet");
    }
}
