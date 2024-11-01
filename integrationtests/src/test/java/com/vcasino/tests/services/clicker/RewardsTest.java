package com.vcasino.tests.services.clicker;

import com.vcasino.tests.common.Service;
import com.vcasino.tests.model.Response;
import com.vcasino.tests.model.Row;
import com.vcasino.tests.services.clicker.model.Account;
import com.vcasino.tests.services.clicker.model.streak.DayReward;
import com.vcasino.tests.services.clicker.model.streak.StreakInfo;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.Test;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;

@Slf4j
public class RewardsTest extends GenericClickerTest {
    private final DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("HH:mm:ss");
    private final DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    @BeforeTest
    void setup() throws Exception {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
        init(Service.CLICKER);
        authorizeAdmin();
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

        performHttpPost(buildUrl("/rewards/streaks"), null, getAttrsWithAuthorization(), 400);
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

    @Test(description = "Get video info")
    void testGetVideoInfo() throws Exception {
        String videoId = "jNQXAC9IVRw";
        VideoInfo videoInfo = getVideoInfo(videoId, "YouTube", 200);
        assertEquals(videoId, videoInfo.getId());
        assertEquals(videoInfo.getDuration().toSecondOfDay(), 19);

        getVideoInfo(videoId, "Telegram", 400);
        getVideoInfo(videoId, "RandomService", 400);
        getVideoInfo(videoId + videoId, "Youtube", 400);
    }

    @Test(description = "Get video properties")
    void testGetVideoProperties() throws Exception {
        String s = performHttpGet(buildUrl("/admin/rewards/properties"), getAttrsWithAuthorization(true), 200);
        Map<String, List<String>> properties = fromJson(s, Map.class);

        assertTrue(properties.containsKey("Watch"));
        assertTrue(properties.containsKey("Subscribe"));

        assertFalse(properties.get("Watch").isEmpty());
        assertFalse(properties.get("Subscribe").isEmpty());

        assertTrue(properties.get("Watch").contains("YouTube"));
        assertTrue(properties.get("Subscribe").contains("YouTube"));
        assertTrue(properties.get("Subscribe").contains("Telegram"));
    }

    @Test(description = "Add youtube watch reward")
    void testAddYoutubeWatchReward() throws Exception {
        String id = "jNQXAC9IVRw";
        String rewardType = "watch";
        String service = "youtube";

        AddRewardRequest request = sendAddRewardRequest(id, rewardType, service, 200);
        validateRewardInDatabase("https://www.youtube.com/watch?v=" + request.getId(), request, 19);
    }

    @Test(description = "Add youtube subscribe reward")
    void testAddYoutubeSubscribeReward() throws Exception {
        String id = "jawed";
        String rewardType = "subscribe";
        String service = "youtube";

        AddRewardRequest request = sendAddRewardRequest(id, rewardType, service, 200);
        validateRewardInDatabase("https://www.youtube.com/@" + request.getId(), request, null);
    }

    @Test(description = "Add telegram subscribe reward")
    void testAddTelegramSubscribeReward() throws Exception {
        String rewardType = "subscribe";
        String service = "Telegram";

        AddRewardRequest request = sendAddRewardRequest(rewardType, service, 200);

        validateRewardInDatabase("https://t.me/" + request.getId(), request, null);
    }

    @Test(description = "Try to add telegram watch reward")
    void testTryAddTelegramWatchReward() throws Exception {
        String rewardType = "watch";
        String service = "Telegram";

        sendAddRewardRequest(rewardType, service, 400);
    }

    @Test(description = "Get reward info")
    void testGetRewards() throws Exception {
        createAccount();
        List<String> links = List.of("https://www.youtube.com/@jawed", "https://t.me/vclicker", "https://www.youtube.com/watch?v=jNQXAC9IVRw");

        List<AddRewardRequest> requests = List.of(
                sendAddRewardRequest("jawed", "Subscribe", "YouTube", 200),
                sendAddRewardRequest("vclicker", "Subscribe", "Telegram", 200),
                sendAddRewardRequest("jNQXAC9IVRw", "Watch", "YouTube", 200)
        );

        String s = performHttpGet(buildUrl("/rewards"), getAttrsWithAuthorization(), 200);
        List<Response> responses = jsonToListObjects(s);
        int found = 0;
        for (Response response : responses) {
            for (int i = 0; i < requests.size(); i++) {
                AddRewardRequest request = requests.get(i);
                if (request.getRewardName().equals(response.get("name"))) {
                    assertFalse(response.getBoolean("received"));
                    assertEquals(response.get("link"), links.get(i));
                    assertEquals(response.get("type"), request.getRewardType());
                    assertEquals(response.get("service"), request.getService());
                    found++;
                    break;
                }
            }
        }

        assertEquals(found, 3);
    }

    @Test(description = "Get rewards, yesterday's reward is not visible")
    void testGetRewardsNotVisible() throws Exception {
        createAccount();
        LocalDateTime start = LocalDateTime.now().minusDays(1);
        AddRewardRequest request = sendAddRewardRequest("jawed", "Subscribe", "YouTube", 200, start);

        String s = performHttpGet(buildUrl("/rewards"), getAttrsWithAuthorization(), 200);
        List<Response> responses = jsonToListObjects(s);
        boolean found = false;
        for (Response response : responses) {
            if (response.get("name").equals(request.getRewardName())) {
                found = true;
                break;
            }
        }

        assertFalse(found);
    }

    @Test(description = "Receive reward")
    void testReceiveReward() throws Exception {
        Account account = createAccount();
        AddRewardRequest request = sendAddRewardRequest("jNQXAC9IVRw", "Watch", "YouTube", 200);

        int rewardId = getRewardIdByName(request.getRewardName());
        changeRewardDuration(rewardId, 10);

        LocalDateTime clickTime = LocalDateTime.now().minusSeconds(10);
        Account updatedAccount = receiveReward(rewardId, clickTime, 200);

        assertEquals(updatedAccount.getBalanceCoins().intValue(),
                account.getBalanceCoins().intValue() + request.getRewardCoins());
    }

    @Test(description = "Receive reward twice")
    void testReceiveRewardTwice() throws Exception {
        createAccount();
        AddRewardRequest request = sendAddRewardRequest("jNQXAC9IVRw", "Watch", "YouTube", 200);

        int rewardId = getRewardIdByName(request.getRewardName());
        changeRewardDuration(rewardId, 10);

        LocalDateTime clickTime = LocalDateTime.now().minusSeconds(10);
        receiveReward(rewardId, clickTime, 200);
        receiveReward(rewardId, clickTime, 400);
    }

    @Test(description = "Receive reward with wrong id")
    void testReceiveRewardWrongId() throws Exception {
        createAccount();
        AddRewardRequest request = sendAddRewardRequest("jNQXAC9IVRw", "Watch", "YouTube", 200);

        int rewardId = getRewardIdByName(request.getRewardName()) + 976;

        LocalDateTime clickTime = LocalDateTime.now();
        receiveReward(rewardId, clickTime, 403);
    }

    @Test(description = "Receive reward - click before reward appeared")
    void testReceiveRewardBeforeItHasAppeared() throws Exception {
        createAccount();
        LocalDateTime start = LocalDateTime.now().plusDays(1);
        AddRewardRequest request = sendAddRewardRequest("jNQXAC9IVRw", "Watch", "YouTube",
                200, start);

        int rewardId = getRewardIdByName(request.getRewardName());
        changeRewardDuration(rewardId, 10);

        LocalDateTime clickTime = LocalDateTime.now().minusSeconds(15);
        receiveReward(rewardId, clickTime, 400);
    }

    @Test(description = "Receive reward - click after reward expired")
    void testReceiveRewardAfterRewardExpired() throws Exception {
        createAccount();
        LocalDateTime start = LocalDateTime.now().minusDays(1);
        AddRewardRequest request = sendAddRewardRequest("jNQXAC9IVRw", "Watch", "YouTube",
                200, start);

        int rewardId = getRewardIdByName(request.getRewardName());
        changeRewardDuration(rewardId, 10);

        LocalDateTime clickTime = request.getEnd();
        receiveReward(rewardId, clickTime, 400);
    }

    @Test(description = "Receive reward - click before current date")
    void testReceiveRewardClickBeforeCurrentDate() throws Exception {
        createAccount();
        LocalDateTime now = LocalDateTime.now();
        AddRewardRequest request = sendAddRewardRequest("jNQXAC9IVRw", "Watch", "YouTube",
                200, now);

        int rewardId = getRewardIdByName(request.getRewardName());
        changeRewardDuration(rewardId, 10);

        LocalDateTime clickTime = now.plusMinutes(1);
        receiveReward(rewardId, clickTime, 400);
    }

    @Test(description = "Receive reward - click was a day ago")
    void testReceiveRewardClickADayAgo() throws Exception {
        createAccount();
        LocalDateTime start = LocalDateTime.now().minusDays(1);
        AddRewardRequest request = sendAddRewardRequest("jNQXAC9IVRw", "Watch", "YouTube",
                200, start);

        int rewardId = getRewardIdByName(request.getRewardName());
        changeRewardDuration(rewardId, 10);

        LocalDateTime clickTime = request.getStart().plusMinutes(1);
        receiveReward(rewardId, clickTime, 400);
    }

    @Test(description = "Receive reward without watching video")
    void testReceiveRewardWithoutWatchingVideo() throws Exception {
        createAccount();
        AddRewardRequest request = sendAddRewardRequest("jNQXAC9IVRw", "Watch", "YouTube",
                200);

        int rewardId = getRewardIdByName(request.getRewardName());
        changeRewardDuration(rewardId, 120);

        LocalDateTime clickTime = LocalDateTime.now();
        receiveReward(rewardId, clickTime, 400);
        clickTime = clickTime.minusMinutes(1);
        receiveReward(rewardId, clickTime, 400);
    }

    private Account receiveReward(Integer rewardId, LocalDateTime clickTime, int expectedCode) throws Exception {
        String body = objToJson(Map.of("rewardId", String.valueOf(rewardId), "clickTime", clickTime.format(dateTimeFormatter)));
        String s = performHttpPost(buildUrl("/rewards"), body, getAttrsWithAuthorization(), expectedCode);
        return toAccount(s);
    }

    private int getRewardIdByName(String rewardName) {
        String query = "SELECT r.id FROM reward r WHERE r.name = '%s'".formatted(rewardName);
        List<Row> rows = executeQuery(query);
        assertEquals(rows.size(), 1);
        return rows.getFirst().getInt("id");
    }

    private void changeRewardDuration(int rewardId, Integer duration) {
        String query = "UPDATE reward SET duration_seconds = %s WHERE id = %s".formatted(duration, rewardId);
        executeUpdate(query);
    }

    private void validateRewardInDatabase(String expectedLink, AddRewardRequest request, Integer duration) {
        String query = "SELECT * FROM reward r WHERE r.name = '%s'".formatted(request.getRewardName());
        List<Row> rows = executeQuery(query);
        assertEquals(1, rows.size());
        Row row = rows.getFirst();

        assertEquals(row.get("type"), request.getRewardType().toUpperCase());
        assertEquals(row.get("link"), expectedLink);

        if (duration != null) {
            assertEquals(row.getInt("duration_seconds"), duration);
        } else {
            assertNull(row.get("duration_seconds"));
        }

        assertEquals(row.get("service_name"), request.getService().toUpperCase());
        assertEquals(row.getInt("reward_coins"), request.getRewardCoins());
        assertEquals(row.getTimestamp("valid_from"), Timestamp.valueOf(request.getStart()));
        assertEquals(row.getTimestamp("ends_in"), Timestamp.valueOf(request.getEnd()));
    }


    private VideoInfo getVideoInfo(String videoId, String service, int expectedCode) throws Exception {
        String url = buildUrl("/admin/rewards/video-info", "videoId", videoId, "service", service);

        if (expectedCode != 200) {
            performHttpGet(url, getAttrsWithAuthorization(true), expectedCode);
            return null;
        } else {
            String s = performHttpGet(url, getAttrsWithAuthorization(true), expectedCode);
            Response response = jsonToObject(s);
            return new VideoInfo(response.get("id"), LocalTime.parse(response.get("duration"), dateFormatter));
        }
    }

    private AddRewardRequest sendAddRewardRequest(String rewardType, String service, int expectedCode) throws Exception {
        return sendAddRewardRequest("vclicker", rewardType, service, expectedCode);
    }

    private AddRewardRequest sendAddRewardRequest(String id, String rewardType, String service, int expectedCode) throws Exception {
        return sendAddRewardRequest(id, rewardType, service, expectedCode, null);
    }

    private AddRewardRequest sendAddRewardRequest(String id, String rewardType, String service, int expectedCode,
                                                  LocalDateTime start) throws Exception {
        log.info("Adding new reward");

        if (start == null) {
            start = LocalDateTime.now();
        }
        LocalDateTime end = start.plusDays(1);

        start = start.withHour(0).withMinute(0).withSecond(0).withNano(0);
        end = end.withHour(0).withMinute(0).withSecond(0).withNano(0);

        String name = "Test_" + (int) (Math.random() * 10_000_000);

        AddRewardRequest body = AddRewardRequest.builder()
                .id(id)
                .rewardType(rewardType)
                .service(service)
                .rewardCoins(10000)
                .rewardName(name)
                .start(start)
                .end(end)
                .build();

        performHttpPost(buildUrl("/admin/rewards"), body.toString(), getAttrsWithAuthorization(true), expectedCode);

        return body;
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
        String s = performHttpGet(buildUrl("/rewards/streaks"), getAttrsWithAuthorization());
        return gson.fromJson(s, StreakInfo.class);
    }

    private Account receiveStreakReward() throws Exception {
        String s = performHttpPost(buildUrl("/rewards/streaks"), null, getAttrsWithAuthorization());
        return toAccount(s);
    }

    @Data
    @AllArgsConstructor
    static class VideoInfo {
        String id;
        LocalTime duration;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    static class AddRewardRequest {
        String id;
        String rewardType;
        String service;
        Integer rewardCoins;
        String rewardName;
        LocalDateTime start;
        LocalDateTime end;

        @Override
        public String toString() {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");
            return """
                    {
                        "id": "%s",
                        "rewardType": "%s",
                        "service": "%s",
                        "rewardCoins": %s,
                        "rewardName": "%s",
                        "dateRange": {
                            "start": "%s",
                            "end": "%s"
                        }
                    }""".formatted(id, rewardType ,service, rewardCoins,
                    rewardName, start.format(formatter), end.format(formatter));
        }
    }

}
