package com.vcasino.tests.services.clicker;

import com.vcasino.tests.common.Service;
import com.vcasino.tests.model.Response;
import com.vcasino.tests.model.Row;
import com.vcasino.tests.services.clicker.model.Account;
import com.vcasino.tests.services.clicker.model.streak.DailyReward;
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
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;

@Slf4j
public class TasksTest extends GenericClickerTest {
    private final DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("HH:mm:ss");
    private final DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    @BeforeTest
    void setup() throws Exception {
        init(Service.CLICKER);
        authorizeAdmin();
    }

    @Test(description = "Get streak info")
    void testGetStreakInfo() throws Exception {
        createAccount();
        StreakInfo streakInfo = getStreakInfo();

        assertEquals(streakInfo.getState().getDay(), 1);
        assertTrue(streakInfo.getState().getAvailable());

        List<DailyReward> rewards = streakInfo.getRewardsByDays();
        DailyReward lastReward = null;
        for (int i = 0; i < rewards.size(); i++) {
            DailyReward reward = rewards.get(i);
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

        performHttpPost(buildUrl("/tasks/streaks"), null, getAttrsWithAuthorization(), 400);
    }

    @Test(description = "Receive all streak reward")
    void testReceiveAllStreakReward() throws Exception {
        createAccount();
        StreakInfo streakInfo = getStreakInfo();
        List<DailyReward> rewards = streakInfo.getRewardsByDays();

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
        createAccount();
        StreakInfo streakInfo = getStreakInfo();
        List<DailyReward> rewards = streakInfo.getRewardsByDays();

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

    @Test(description = "Get tasks properties")
    void testGetTasksProperties() throws Exception {
        String s = performHttpGet(buildUrl("/admin/tasks/properties"), getAttrsWithAuthorization(true), 200);
        List<SupportedTaskServices> properties = Arrays.asList(fromJson(s, SupportedTaskServices[].class));

        assertEquals(properties.size(), 2);

        Optional<SupportedTaskServices> watch = properties.stream().filter(p -> p.taskType.equals("Watch")).findFirst();
        Optional<SupportedTaskServices> subscribe = properties.stream().filter(p -> p.taskType.equals("Subscribe")).findFirst();

        assertTrue(watch.isPresent());
        assertTrue(subscribe.isPresent());

        assertTrue(watch.get().services.contains("YouTube"));
        assertTrue(subscribe.get().services.contains("YouTube"));
        assertTrue(subscribe.get().services.contains("Telegram"));
    }

    @Test(description = "Add youtube watch task")
    void testAddYoutubeWatchTask() throws Exception {
        String id = "jNQXAC9IVRw";
        String taskType = "watch";
        String service = "youtube";

        AddTaskRequest request = sendAddTaskRequest(id, taskType, service, 200);
        validateRewardInDatabase("https://www.youtube.com/watch?v=" + request.getId(), request, 19);
    }

    @Test(description = "Add youtube subscribe task")
    void testAddYoutubeSubscribeReward() throws Exception {
        String id = "jawed";
        String taskType = "subscribe";
        String service = "youtube";

        AddTaskRequest request = sendAddTaskRequest(id, taskType, service, 200);
        validateRewardInDatabase("https://www.youtube.com/@" + request.getId(), request, null);
    }

    @Test(description = "Add telegram subscribe task")
    void testAddTelegramSubscribeReward() throws Exception {
        String taskType = "subscribe";
        String service = "Telegram";

        AddTaskRequest request = sendAddTaskRequest(taskType, service, 200);

        validateRewardInDatabase("https://t.me/" + request.getId(), request, null);
    }

    @Test(description = "Try to add telegram watch task")
    void testTryAddTelegramWatchReward() throws Exception {
        String taskType = "watch";
        String service = "Telegram";

        sendAddTaskRequest(taskType, service, 400);
    }

    @Test(description = "Get task info")
    void testGetTasks() throws Exception {
        createAccount();
        List<String> links = List.of("https://www.youtube.com/@jawed", "https://t.me/vclicker", "https://www.youtube.com/watch?v=jNQXAC9IVRw");

        List<AddTaskRequest> requests = List.of(
                sendAddTaskRequest("jawed", "Subscribe", "YouTube", 200),
                sendAddTaskRequest("vclicker", "Subscribe", "Telegram", 200),
                sendAddTaskRequest("jNQXAC9IVRw", "Watch", "YouTube", 200)
        );

        String s = performHttpGet(buildUrl("/tasks"), getAttrsWithAuthorization(), 200);
        List<Response> responses = jsonToListObjects(s);
        int found = 0;
        for (Response response : responses) {
            for (int i = 0; i < requests.size(); i++) {
                AddTaskRequest request = requests.get(i);
                if (request.getTaskName().equals(response.get("name"))) {
                    assertFalse(response.getBoolean("received"));
                    assertEquals(response.get("link"), links.get(i));
                    assertEquals(response.get("type"), request.getTaskType());
                    assertEquals(response.get("service"), request.getService());
                    found++;
                    break;
                }
            }
        }

        assertEquals(found, 3);
    }

    @Test(description = "Get tasks, yesterday's task is not visible")
    void testGetTasksNotVisible() throws Exception {
        createAccount();
        LocalDateTime start = LocalDateTime.now().minusDays(1);
        AddTaskRequest request = sendAddTaskRequest("jawed", "Subscribe", "YouTube", 200, start);

        String s = performHttpGet(buildUrl("/tasks"), getAttrsWithAuthorization(), 200);
        List<Response> responses = jsonToListObjects(s);
        boolean found = false;
        for (Response response : responses) {
            if (response.get("name").equals(request.getTaskName())) {
                found = true;
                break;
            }
        }

        assertFalse(found);
    }

    @Test(description = "Receive task reward")
    void testReceiveTaskReward() throws Exception {
        Account account = createAccount().getAccount();
        AddTaskRequest request = sendAddTaskRequest("jNQXAC9IVRw", "Watch", "YouTube", 200);

        int taskId = getTaskIdByName(request.getTaskName());
        changeTaskDuration(taskId, 10);

        LocalDateTime clickTime = LocalDateTime.now().minusSeconds(10);
        Account updatedAccount = receiveTaskReward(taskId, clickTime, 200);

        assertEquals(updatedAccount.getBalanceCoins().intValue(),
                account.getBalanceCoins().intValue() + request.getRewardCoins());
    }

    @Test(description = "Receive task reward twice")
    void testReceiveTaskRewardTwice() throws Exception {
        createAccount();
        AddTaskRequest request = sendAddTaskRequest("jNQXAC9IVRw", "Watch", "YouTube", 200);

        int taskId = getTaskIdByName(request.getTaskName());
        changeTaskDuration(taskId, 10);

        LocalDateTime clickTime = LocalDateTime.now().minusSeconds(10);
        receiveTaskReward(taskId, clickTime, 200);
        receiveTaskReward(taskId, clickTime, 400);
    }

    @Test(description = "Receive task reward with wrong id")
    void testReceiveTaskRewardWrongId() throws Exception {
        createAccount();
        AddTaskRequest request = sendAddTaskRequest("jNQXAC9IVRw", "Watch", "YouTube", 200);

        int taskId = getTaskIdByName(request.getTaskName()) + 976;

        LocalDateTime clickTime = LocalDateTime.now();
        receiveTaskReward(taskId, clickTime, 403);
    }

    @Test(description = "Receive task reward - click before task appeared")
    void testReceiveTaskRewardBeforeItHasAppeared() throws Exception {
        createAccount();
        LocalDateTime start = LocalDateTime.now().plusDays(1);
        AddTaskRequest request = sendAddTaskRequest("jNQXAC9IVRw", "Watch", "YouTube",
                200, start);

        int taskId = getTaskIdByName(request.getTaskName());
        changeTaskDuration(taskId, 10);

        LocalDateTime clickTime = LocalDateTime.now().minusSeconds(15);
        receiveTaskReward(taskId, clickTime, 400);
    }

    @Test(description = "Receive task reward - click after task expired")
    void testReceiveTaskRewardAfterRewardExpired() throws Exception {
        createAccount();
        LocalDateTime start = LocalDateTime.now().minusDays(1);
        AddTaskRequest request = sendAddTaskRequest("jNQXAC9IVRw", "Watch", "YouTube",
                200, start);

        int taskId = getTaskIdByName(request.getTaskName());
        changeTaskDuration(taskId, 10);

        LocalDateTime clickTime = request.getEnd();
        receiveTaskReward(taskId, clickTime, 400);
    }

    @Test(description = "Receive task reward - click before current date")
    void testReceiveTaskRewardClickBeforeCurrentDate() throws Exception {
        createAccount();
        LocalDateTime now = LocalDateTime.now();
        AddTaskRequest request = sendAddTaskRequest("jNQXAC9IVRw", "Watch", "YouTube",
                200, now);

        int taskId = getTaskIdByName(request.getTaskName());
        changeTaskDuration(taskId, 10);

        LocalDateTime clickTime = now.plusMinutes(1);
        receiveTaskReward(taskId, clickTime, 400);
    }

    @Test(description = "Receive task reward - click was a day ago")
    void testReceiveTaskRewardClickADayAgo() throws Exception {
        createAccount();
        LocalDateTime start = LocalDateTime.now().minusDays(1);
        AddTaskRequest request = sendAddTaskRequest("jNQXAC9IVRw", "Watch", "YouTube",
                200, start);

        int taskId = getTaskIdByName(request.getTaskName());
        changeTaskDuration(taskId, 10);

        LocalDateTime clickTime = request.getStart().plusMinutes(1);
        receiveTaskReward(taskId, clickTime, 400);
    }

    @Test(description = "Receive task reward without watching video")
    void testReceiveTaskRewardWithoutWatchingVideo() throws Exception {
        createAccount();
        AddTaskRequest request = sendAddTaskRequest("jNQXAC9IVRw", "Watch", "YouTube",
                200);

        int taskId = getTaskIdByName(request.getTaskName());
        changeTaskDuration(taskId, 120);

        LocalDateTime clickTime = LocalDateTime.now();
        receiveTaskReward(taskId, clickTime, 400);
        clickTime = clickTime.minusMinutes(1);
        receiveTaskReward(taskId, clickTime, 400);
    }

    private Account receiveTaskReward(Integer taskId, LocalDateTime clickTime, int expectedCode) throws Exception {
        String body = objToJson(Map.of("taskId", String.valueOf(taskId), "clickTime", clickTime.format(dateTimeFormatter)));
        String s = performHttpPost(buildUrl("/tasks"), body, getAttrsWithAuthorization(), expectedCode);
        return toAccount(s);
    }

    private int getTaskIdByName(String taskName) {
        String query = "SELECT t.id FROM task t WHERE t.name = '%s'".formatted(taskName);
        List<Row> rows = executeQuery(query);
        assertEquals(rows.size(), 1);
        return rows.getFirst().getInt("id");
    }

    private void changeTaskDuration(int taskId, Integer duration) {
        String query = "UPDATE task SET duration_seconds = %s WHERE id = %s".formatted(duration, taskId);
        executeUpdate(query);
    }

    private void validateRewardInDatabase(String expectedLink, AddTaskRequest request, Integer duration) {
        String query = "SELECT * FROM task t WHERE t.name = '%s'".formatted(request.getTaskName());
        List<Row> rows = executeQuery(query);
        assertEquals(1, rows.size());
        Row row = rows.getFirst();

        assertEquals(row.get("type"), request.getTaskType().toUpperCase());
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
        String url = buildUrl("/admin/tasks/video-info", "videoId", videoId, "service", service);

        if (expectedCode != 200) {
            performHttpGet(url, getAttrsWithAuthorization(true), expectedCode);
            return null;
        } else {
            String s = performHttpGet(url, getAttrsWithAuthorization(true), expectedCode);
            Response response = jsonToObject(s);
            return new VideoInfo(response.get("id"), LocalTime.parse(response.get("duration"), dateFormatter));
        }
    }

    private AddTaskRequest sendAddTaskRequest(String taskType, String service, int expectedCode) throws Exception {
        return sendAddTaskRequest("vclicker", taskType, service, expectedCode);
    }

    private AddTaskRequest sendAddTaskRequest(String id, String taskType, String service, int expectedCode) throws Exception {
        return sendAddTaskRequest(id, taskType, service, expectedCode, null);
    }

    private AddTaskRequest sendAddTaskRequest(String id, String taskType, String service, int expectedCode,
                                              LocalDateTime start) throws Exception {
        log.info("Adding new task");

        if (start == null) {
            start = LocalDateTime.now();
        }
        LocalDateTime end = start.plusDays(1);

        start = start.withHour(0).withMinute(0).withSecond(0).withNano(0);
        end = end.withHour(0).withMinute(0).withSecond(0).withNano(0);

        String name = "Test_" + (int) (Math.random() * 10_000_000);

        AddTaskRequest body = AddTaskRequest.builder()
                .id(id)
                .taskType(taskType)
                .service(service)
                .rewardCoins(10000)
                .taskName(name)
                .start(start)
                .end(end)
                .build();

        performHttpPost(buildUrl("/admin/tasks"), body.toString(), getAttrsWithAuthorization(true), expectedCode);

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
        String s = performHttpGet(buildUrl("/tasks/streaks"), getAttrsWithAuthorization());
        return gson.fromJson(s, StreakInfo.class);
    }

    private Account receiveStreakReward() throws Exception {
        String s = performHttpPost(buildUrl("/tasks/streaks"), null, getAttrsWithAuthorization());
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
    static class AddTaskRequest {
        String id;
        String taskType;
        String service;
        Integer rewardCoins;
        String taskName;
        LocalDateTime start;
        LocalDateTime end;

        @Override
        public String toString() {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");
            return """
                    {
                        "id": "%s",
                        "taskType": "%s",
                        "service": "%s",
                        "rewardCoins": %s,
                        "taskName": "%s",
                        "dateRange": {
                            "start": "%s",
                            "end": "%s"
                        }
                    }""".formatted(id, taskType,service, rewardCoins,
                    taskName, start.format(formatter), end.format(formatter));
        }
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    static class SupportedTaskServices {
        String taskType;
        List<String> services;
    }

}
