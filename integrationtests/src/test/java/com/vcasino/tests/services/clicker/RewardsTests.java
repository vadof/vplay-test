package com.vcasino.tests.services.clicker;

import com.vcasino.tests.common.Service;
import com.vcasino.tests.services.clicker.model.Account;
import com.vcasino.tests.services.clicker.model.streak.DayReward;
import com.vcasino.tests.services.clicker.model.streak.StreakInfo;
import lombok.extern.slf4j.Slf4j;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.TimeZone;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

@Slf4j
public class RewardsTests extends GenericClickerTest {
    @BeforeTest
    void setup() throws Exception {
        init(Service.CLICKER);
    }

    @Test(description = "Get rewards info")
    void testGetStreakInfo() throws Exception {
        createAccount();
        StreakInfo streakInfo = getStreakInfo();

        assertEquals(streakInfo.getState().getDay(), 1);
        assertTrue(streakInfo.getState().getAvailable());

        List<DayReward> rewards = streakInfo.getRewardsByDays();
        DayReward lastReward = null;
        for (int i = 0; i < rewards.size(); i++) {
            DayReward reward = rewards.get(i);
            assertEquals(reward.getDay(), i + 1);
            if (i != 0) {
                assertTrue(lastReward.getReward() < reward.getReward(),
                        "%s must be less than %s".formatted(lastReward, reward));
            }
            lastReward = reward;
        }
    }

    @Test(description = "Receive streak reward")
    void testReceiveStreakReward() throws Exception {
        createAccount();
        StreakInfo streakInfo = getStreakInfo();
        Account account = receiveStreakReward();

        assertEquals(account.getBalanceCoins().intValue(), getRewardByDay(streakInfo, 1));

        streakInfo = getStreakInfo();

        assertEquals(streakInfo.getState().getDay(), 1);
        assertFalse(streakInfo.getState().getAvailable());

        performHttpPost("/api/v1/clicker/rewards/streaks", null, getAttrsWithAuthorization(), 400);
    }

    @Test(description = "Receive all streak reward")
    void testReceiveAllStreakReward() throws Exception {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
        createAccount();
        StreakInfo streakInfo = getStreakInfo();
        List<DayReward> rewards = streakInfo.getRewardsByDays();

        Account account;
        long expectedRewardSum = 0;
        for (int i = 0; i < rewards.size(); i++) {
            expectedRewardSum += getRewardByDay(streakInfo, i + 1);
            if (i != 0) {
                setStreakLastReceivedToYesterday();
            }

            log.info("Receive reward for Day#{}", i + 1);
            account = receiveStreakReward();

            assertEquals(account.getBalanceCoins().longValue(), expectedRewardSum);
        }
    }

    @Test(description = "When all streak rewards received, streak resets")
    void testStreakResets() throws Exception {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
        createAccount();
        StreakInfo streakInfo = getStreakInfo();
        List<DayReward> rewards = streakInfo.getRewardsByDays();

        Account account = null;
        for (int i = 0; i < rewards.size(); i++) {
            if (i != 0) {
                setStreakLastReceivedToYesterday();
            }
            account = receiveStreakReward();
        }

        Long expectedBalance = account.getBalanceCoins().longValue() + getRewardByDay(streakInfo, 1);
        setStreakLastReceivedToYesterday();
        account = receiveStreakReward();

        assertEquals(account.getBalanceCoins().longValue(), expectedBalance);
    }

    private void setStreakLastReceivedToYesterday() {
        LocalDate yesterday = LocalDate.now().minusDays(1);
        String query = """
                UPDATE streak s
                SET last_received_date = '%s'
                WHERE account_id = %s;""".formatted(yesterday, accountId);
        executeUpdate(query);
    }

    private Integer getRewardByDay(StreakInfo streakInfo, Integer day) {
        return streakInfo.getRewardsByDays().stream()
                .filter(r -> r.getDay().equals(day))
                .findFirst().get()
                .getReward();
    }

    private StreakInfo getStreakInfo() throws Exception {
        String s = performHttpGet("/api/v1/clicker/rewards/streaks", getAttrsWithAuthorization());
        return gson.fromJson(s, StreakInfo.class);
    }

    private Account receiveStreakReward() throws Exception {
        String s = performHttpPost("/api/v1/clicker/rewards/streaks", null, getAttrsWithAuthorization());
        return toAccount(s);
    }

}
