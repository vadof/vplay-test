package com.vcasino.tests.services.user;

import com.vcasino.tests.common.GenericTest;
import com.vcasino.tests.common.Service;
import com.vcasino.tests.model.AuthenticationResponse;
import com.vcasino.tests.model.Row;
import com.vcasino.tests.model.User;
import com.vcasino.tests.services.user.model.UserInformation;
import com.vcasino.tests.services.user.model.UserStatistics;
import lombok.extern.slf4j.Slf4j;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import static org.testng.AssertJUnit.assertEquals;
import static org.testng.AssertJUnit.assertNull;

@Slf4j
public class StatisticsTests extends GenericTest {

    @BeforeTest
    void setup() {
        init(Service.USER);
    }

    @Test(description = "Get statistics takes correct values from db")
    void testGetStatistics() throws Exception {
        Long registeredUsers = getRegisteredUsers();
        Long registeredUsersWithOAuth = getRegisteredUsersWithOAuth();
        Long registeredUsersInvitedByOthers = getRegisteredUsersInvitedByOthers();
        Long registeredUsersToday = getRegisteredUsersToday();
        Long registeredUsersLastWeek = getRegisteredUsersLastWeek();
        Long registeredUsersLastMonth = getRegisteredUsersLastMonth();
        Long activeUsers = getActiveUsers();
        Long frozenUsers = getFrozenUsers();

        compareStatistics(registeredUsers, registeredUsersWithOAuth, registeredUsersInvitedByOthers,
                registeredUsersToday, registeredUsersLastWeek, registeredUsersLastMonth, activeUsers, frozenUsers);

        log.info("Add more users");

        AuthenticationResponse toFreeze = createNewUser();
        freezeUser(toFreeze.getUser().getUsername());
        frozenUsers += 1;

        createNewUser();
        activeUsers += 2;

        registerUser();
        registeredUsers += 3;
        registeredUsersToday += 3;
        registeredUsersLastWeek += 3;
        registeredUsersLastMonth += 3;

        compareStatistics(registeredUsers, registeredUsersWithOAuth, registeredUsersInvitedByOthers,
                registeredUsersToday, registeredUsersLastWeek, registeredUsersLastMonth, activeUsers, frozenUsers);
    }

    @Test
    void testGetUserInformation() throws Exception {
        log.info("Test active user information");
        User user1 = createNewUser().getUser();
        UserInformation information1 = getUserInformation(null, user1.getUsername(), 200);
        compareInformation(information1, user1, null, 0L, true, false);

        log.info("Test inactive user information");
        User user2 = registerUser();
        Long userId = getUserId(user2.getUsername());
        UserInformation information2 = getUserInformation(userId, null, 200);
        compareInformation(information2, user2, null, 0L, false, false);

        log.info("Test frozen user information");
        freezeUser(user2.getUsername());
        UserInformation information3 = getUserInformation(userId, null, 200);
        compareInformation(information3, user2, null, 0L, false, true);

        log.info("Test users referral information");
        String username = generateRandomUsername();
        User user3 = new User("Test User", username, username + "@test.com", "test1234");
        String url = "/api/v1/users/auth/register?ref=" + user1.getUsername();
        performHttpPost(url, user3, getDefaultAttrs());

        information1 = getUserInformation(null, user1.getUsername(), 200);
        compareInformation(information1, user1, null, 1L, true, false);

        information3 = getUserInformation(null, user3.getUsername(), 200);
        compareInformation(information3, user3, user1.getUsername(), 0L, false, false);

        log.info("Throws exception if user not found");
        getUserInformation(null, user1.getUsername() + "_", 404);
    }

    private void compareStatistics(Long registeredUsers, Long registeredUsersWithOAuth, Long registeredUsersInvitedByOthers,
                                   Long registeredUsersToday, Long registeredUsersLastWeek, Long registeredUsersLastMonth,
                                   Long activeUsers, Long frozenUsers) throws Exception {
        UserStatistics statistics = getStatistics();
        assertEquals(registeredUsers, statistics.getRegisteredUsers());
        assertEquals(registeredUsersWithOAuth, statistics.getRegisteredUsersWithOAuth());
        assertEquals(registeredUsersInvitedByOthers, statistics.getRegisteredUsersInvitedByOthers());
        assertEquals(registeredUsersToday, statistics.getRegisteredUsersToday());
        assertEquals(registeredUsersLastWeek, statistics.getRegisteredUsersLastWeek());
        assertEquals(registeredUsersLastMonth, statistics.getRegisteredUsersLastMonth());
        assertEquals(activeUsers, statistics.getActiveUsers());
        assertEquals(frozenUsers, statistics.getFrozenUsers());
    }

    private void compareInformation(UserInformation information, User user, String invitedBy, Long usersInvited,
                                    Boolean active, Boolean frozen) {
        Long userId = getUserId(user.getUsername());
        LocalDate today = Instant.now().atZone(ZoneId.systemDefault()).toLocalDate();

        assertEquals(userId, information.getId());
        assertEquals(user.getName(), information.getName());
        assertEquals(user.getEmail(), information.getEmail());
        assertEquals("USER", information.getRole());
        assertNull(information.getOAuthProvider());
        assertEquals(invitedBy, information.getInvitedBy());
        assertEquals(usersInvited, information.getUsersInvited());
        assertEquals(today, Instant.parse(information.getRegisterDate()).atZone(ZoneId.systemDefault()).toLocalDate());
        assertEquals(active, information.getActive());
        assertEquals(frozen, information.getFrozen());
    }

    private Long getRegisteredUsers() {
        String query = "SELECT COUNT(*) as amount FROM my_user";
        return getAmountFromRows(query);
    }

    private Long getRegisteredUsersWithOAuth() {
        String query = "SELECT COUNT(*) as amount FROM my_user WHERE oauth_provider IS NOT NULL";
        return getAmountFromRows(query);
    }

    private Long getRegisteredUsersInvitedByOthers() {
        String query = "SELECT COUNT(*) as amount FROM my_user WHERE invited_by IS NOT NULL";
        return getAmountFromRows(query);
    }

    private Long getRegisteredUsersToday() {
        String query = "SELECT COUNT(*) as amount FROM my_user WHERE register_date::date = CURRENT_DATE";
        return getAmountFromRows(query);
    }

    private Long getRegisteredUsersLastWeek() {
        String query = "SELECT COUNT(*) as amount FROM my_user WHERE register_date >= CURRENT_DATE - INTERVAL '7' DAY";
        return getAmountFromRows(query);
    }

    private Long getRegisteredUsersLastMonth() {
        String query = "SELECT COUNT(*) as amount FROM my_user WHERE register_date >= CURRENT_DATE - INTERVAL '30' DAY";
        return getAmountFromRows(query);
    }

    private Long getActiveUsers() {
        String query = "SELECT COUNT(*) as amount FROM my_user WHERE active = TRUE";
        return getAmountFromRows(query);
    }

    private Long getFrozenUsers() {
        String query = "SELECT COUNT(*) as amount FROM my_user WHERE frozen = TRUE";
        return getAmountFromRows(query);
    }

    private Long getAmountFromRows(String query) {
        List<Row> rows = executeQuery(query);
        return rows.getFirst().getLong("amount");
    }

    private UserStatistics getStatistics() throws Exception {
        authorizeAdmin();
        String s = performHttpGet("/api/v1/users/admin/statistics", getAttrsWithAuthorization(true));
        return fromJson(s, UserStatistics.class);
    }

    private UserInformation getUserInformation(Long id, String username, int expectedCode) throws Exception {
        String url = "/api/v1/users/admin/statistics/user";
        if (id != null) {
            url += "?id=" + id;
        } else {
            url += "?username=" + username;
        }

        authorizeAdmin();
        String s = performHttpGet(url, getAttrsWithAuthorization(true), expectedCode);
        return fromJson(s, UserInformation.class);
    }

    private User registerUser() throws Exception {
        String username = generateRandomUsername();
        String email = username + "@test.com";
        User user = new User("Test User", username, email, "test1234");
        performHttpPost("/api/v1/users/auth/register", gson.toJson(user), getDefaultAttrs());
        return user;
    }

    private void freezeUser(String username) {
        String query = "UPDATE my_user SET frozen = TRUE WHERE username = '%s'".formatted(username);
        executeUpdate(query);
    }

}
