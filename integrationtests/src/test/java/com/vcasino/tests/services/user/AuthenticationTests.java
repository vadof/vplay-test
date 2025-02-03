package com.vcasino.tests.services.user;

import com.beust.jcommander.internal.Nullable;
import com.vcasino.tests.common.GenericTest;
import com.vcasino.tests.common.Service;
import com.vcasino.tests.model.AuthenticationResponse;
import com.vcasino.tests.model.EmailTokenOptions;
import com.vcasino.tests.model.Response;
import com.vcasino.tests.model.Row;
import com.vcasino.tests.model.User;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.Test;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

import static org.testng.Assert.assertNotEquals;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;
import static org.testng.AssertJUnit.assertEquals;
import static org.testng.AssertJUnit.assertFalse;
import static org.testng.AssertJUnit.assertNotNull;

public class AuthenticationTests extends GenericTest {

    private static final Logger log = LoggerFactory.getLogger(AuthenticationTests.class);

    @BeforeTest
    void setup() {
        init(Service.USER);
    }

    @Test(description = "Register user")
    void testRegisterUser() throws Exception {
        UserRegisterResponse response = registerUser();
        User user = response.getUser();
        EmailTokenOptions emailTokenOptions = response.getEmailTokenOptions();

        assertEquals(user.getEmail(), emailTokenOptions.getEmail());
        assertNotNull(emailTokenOptions.getResendToken());
        assertEquals(1, (int) emailTokenOptions.getEmailsSent());
        assertTrue(emailTokenOptions.getCanResend());

        Long userId = validateUserDatabaseEntry(response.getUser(), false, "USER");
        validateEmailConfirmationTokenDatabaseEntry(userId);
    }

    @Test(description = "Register user, username is already taken by inactive user")
    void testRegisterUserUsernameIsAlreadyTakenByInactiveUser() throws Exception {
        User registeredUser = registerUser().getUser();
        registerUser(registeredUser.getUsername(), registeredUser.getEmail(), registeredUser.getPassword(), 400);
    }

    @Test(description = "Register user bad passwords")
    void testRegisterUserBadPassword() throws Exception {
        List<String> badPasswords = List.of("test123", "testtes", "!P2%", "!Password123д");

        for (String pwd : badPasswords) {
            registerUser(null, null, pwd, 400);
        }

    }

    @Test(description = "Create user bad username symbols")
    void testCreateUserBadUsernameSymbols() throws Exception {
        String invalidSymbols = "!@#$%^&*()+-=[]{}'|.>,</?`~л ";

        String username = "username";
        for (Character symbol : invalidSymbols.toCharArray()) {
            registerUser(symbol + username, null, null, 400);
        }
    }

    @Test(description = "Create user bad username length")
    void testCreateUserBadUsernameLength() throws Exception {
        registerUserWithFixedUsernameLength(17);
        registerUserWithFixedUsernameLength(1);
    }

    private void registerUserWithFixedUsernameLength(int length) throws Exception {
        for (int i = 0; i < 3; i++) {
            String username = generateRandomString(length, CHARACTERS_WITH_NUMBERS);
            registerUser(username, null, null, 400);
        }
    }

    @Test(description = "Create user bad name length")
    void testCreateUserBadNameLength() throws Exception {
        String username = generateRandomUsername();
        String name = generateRandomString(101, CHARACTERS);

        User user = new User(name, username, username + "@test.com", "test1234");

        String res = performHttpPost("/api/v1/users/auth/register", gson.toJson(user), getDefaultAttrs(), 400);
        Response response = jsonToObject(res);

        Response errors = response.getObject("errors");
        assertTrue(errors != null && errors.contains("name"));
    }

    @Test(description = "Confirm an email")
    void testConfirmEmail() throws Exception {
        UserRegisterResponse response = registerUser();
        String confirmationToken = getConfirmationTokenFromEmail(response.getUser().getEmail());
        AuthenticationResponse res = confirmEmail(confirmationToken, 200);

        assertNotNull(res.getToken());
        assertNotNull(res.getRefreshToken());

        User pendingUser = response.getUser();
        User activeUser = res.getUser();

        assertEquals(pendingUser.getName(), activeUser.getName());
        assertEquals(pendingUser.getUsername(), activeUser.getUsername());
        assertEquals(pendingUser.getEmail(), activeUser.getEmail());

        Long userId = validateUserDatabaseEntry(activeUser, true, "USER");
        validateRefreshTokenDatabaseEntry(userId);
    }

    @Test(description = "Resend email fast interval")
    void testResendEmailFastInterval() throws Exception {
        EmailTokenOptions emailTokenOptions = registerUser().getEmailTokenOptions();
        String body = gson.toJson(emailTokenOptions);
        performHttpPost("/api/v1/users/auth/email-confirmation-resend", body, getDefaultAttrs(), 400);
    }

    @Test(description = "Resend email")
    void testResendEmail() throws Exception {
        UserRegisterResponse response = registerUser();
        EmailTokenOptions emailTokenOptions = response.getEmailTokenOptions();
        String token = getConfirmationTokenFromEmail(emailTokenOptions.getEmail());

        for (Integer i = 2; i <= 3; i++) {
            updateEmailConfirmationTokenOptionsTime(token);
            String body = gson.toJson(emailTokenOptions);
            String resendResponse = performHttpPost("/api/v1/users/auth/email-confirmation-resend", body, getDefaultAttrs(), 200);
            emailTokenOptions = fromJson(resendResponse, EmailTokenOptions.class);

            assertEquals(i, emailTokenOptions.getEmailsSent());
            assertEquals(i != 3, (boolean) emailTokenOptions.getCanResend());

            findConfirmationTokenEmailsAmount(emailTokenOptions.getEmail(), i);
        }

        updateEmailConfirmationTokenOptionsTime(token);
        String body = gson.toJson(emailTokenOptions);
        performHttpPost("/api/v1/users/auth/email-confirmation-resend", body, getDefaultAttrs(), 400);
    }

    private void updateEmailConfirmationTokenOptionsTime(String token) {
        String emailResendOptionsQuery = "SELECT * FROM token t WHERE t.token = '%s'".formatted(token);
        Row row = executeQuery(emailResendOptionsQuery).getFirst();
        Map<String, Object> options = fromJson(row.get("options"), Map.class);

        options.put("sentAt", Instant.now().minusSeconds(60 * 10).truncatedTo(ChronoUnit.SECONDS).toString());
        String updatedOptions = gson.toJson(options);

        String updateOptionsQuery = "UPDATE token SET options = '%s' WHERE token = '%s'".formatted(updatedOptions, token);

        executeUpdate(updateOptionsQuery);
    }

    @Test(description = "Confirm an email token expired")
    void testConfirmEmailTokenExpired() throws Exception {
        UserRegisterResponse response = registerUser();
        String confirmationToken = getConfirmationTokenFromEmail(response.getUser().getEmail());

        String query = "UPDATE token SET expiry_date = '%s' WHERE token = '%s'".formatted(
                Timestamp.from(Instant.now().minusSeconds(60 * 60 * 24).truncatedTo(ChronoUnit.SECONDS)), confirmationToken);
        executeUpdate(query);

        confirmEmail(confirmationToken, 401);
    }

    @Test(description = "Confirm an email twice with same token")
    void testConfirmEmailTwiceSameToken() throws Exception {
        UserRegisterResponse response = registerUser();
        String confirmationToken = getConfirmationTokenFromEmail(response.getUser().getEmail());
        confirmEmail(confirmationToken, 200);
        confirmEmail(confirmationToken, 403);
    }

    @Test(description = "Login user")
    void testLoginUser() throws Exception {
        String username = generateRandomUsername();
        User createdUser = createNewUser(username, null, 200).getUser();
        AuthenticationResponse loggedInUser = authorizeUser(createdUser.getUsername());

        assertNotNull(loggedInUser.getToken());
        assertNotNull(loggedInUser.getRefreshToken());
        assertNotNull(loggedInUser.getUser());

        assertEquals(createdUser.getEmail(), loggedInUser.getUser().getEmail());
        assertEquals(createdUser.getUsername(), loggedInUser.getUser().getUsername());
        assertEquals(createdUser.getName(), loggedInUser.getUser().getName());
    }

    @Test(description = "Login inactive user")
    void testLoginInactiveUser() throws Exception {
        String username = generateRandomUsername();
        User createdUser = registerUser(username, null, null, 200).getUser();
        authorizeUser(createdUser.getUsername(), createdUser.getPassword(), 401);
    }

    @Test(description = "Invalid login")
    void testLoginInvalid() throws Exception {
        String password = "test1234";
        AuthenticationResponse res = createNewUser(null, password, 200);
        authorizeUser(res.getUser().getUsername(), password + "1", 401);
    }

    @Test(description = "Refresh authorization token")
    void testRefreshToken() throws Exception {
        AuthenticationResponse registerResponse = createNewUser();
        String refreshToken = registerResponse.getRefreshToken();

        String body = "{\"refreshToken\":\"" + refreshToken + "\"}";

        String responseStr = performHttpPost("/api/v1/users/auth/refreshToken", body, getDefaultAttrs());
        Response response = jsonToObject(responseStr);

        assertNotNull(response.get("token"));
        assertEquals(refreshToken, response.get("refreshToken"));
    }

    @Test(description = "Refresh authorization token also renews refreshToken if expiration is less than 2 hours")
    void testRefreshTokenAlsoRenewsRefreshToken() throws Exception {
        AuthenticationResponse registerResponse = createNewUser();
        String refreshToken = registerResponse.getRefreshToken();

        String query = "UPDATE token SET expiry_date = '%s' WHERE token = '%s'".formatted(
                Timestamp.from(Instant.now().minusSeconds(60 * 90).truncatedTo(ChronoUnit.SECONDS)), refreshToken);
        executeUpdate(query);

        String body = "{\"refreshToken\":\"" + refreshToken + "\"}";

        String responseStr = performHttpPost("/api/v1/users/auth/refreshToken", body, getDefaultAttrs());
        Response response = jsonToObject(responseStr);

        assertNotNull(response.get("token"));
        assertNotEquals(response.get("refreshToken"), refreshToken);
    }

    @Test(description = "Only admin can register another admin")
    void registerAdmin() throws Exception {
        createNewUser();

        String body = getAdminBody();

        log.info("Try to create admin without any authorization");
        var attrs = getDefaultAttrs();

        performHttpPost("/api/v1/users/auth/admin/register", body, attrs, 401);

        log.info("Try to create admin with user authorization");

        attrs = getAttrsWithAuthorization(false);
        performHttpPost("/api/v1/users/auth/admin/register", body, attrs, 403);

        log.info("Create admin with admin authentication");

        authorizeAdmin();
        attrs = getAttrsWithAuthorization(true);
        performHttpPost("/api/v1/users/auth/admin/register", body, attrs, 201);

        User admin = fromJson(body, User.class);
        AuthenticationResponse adminAuthRes = authorizeUser(admin.getUsername(), admin.getPassword(), 200);

        assertNotNull(adminAuthRes.getToken());
        assertNotNull(adminAuthRes.getRefreshToken());
        assertEquals(admin.getName(), adminAuthRes.getUser().getName());
        assertEquals(admin.getEmail(), adminAuthRes.getUser().getEmail());
        assertEquals(admin.getUsername(), adminAuthRes.getUser().getUsername());

        validateUserDatabaseEntry(admin, true, "ADMIN");
    }

    @Test(description = "Register admin bad password")
    void testRegisterAdminBadPassword() throws Exception {
        authorizeAdmin();
        var attrs = getAttrsWithAuthorization(true);

        List<String> badPasswords = List.of("test1234", "MySuperPassword!@#", "mysuperpassword",
                "Password1234", "No_Passworddddd", "!Password123л");

        for (String pwd : badPasswords) {
            String body = getAdminBody(pwd);
            performHttpPost("/api/v1/users/auth/admin/register", body, attrs, 400);
        }
    }

    @Test(description = "Delete pending user")
    void testDeletePendingUser() throws Exception {
        EmailTokenOptions emailTokenOptions = registerUser().getEmailTokenOptions();
        performHttpPost("/api/v1/users/auth/delete-pending-user", emailTokenOptions, getDefaultAttrs(), 200);
    }

    @Test(description = "Delete pending user invalid token")
    void testDeletePendingUserInvalidToken() throws Exception {
        EmailTokenOptions emailTokenOptions = registerUser().getEmailTokenOptions();
        emailTokenOptions.setResendToken(emailTokenOptions.getResendToken() + "a");
        performHttpPost("/api/v1/users/auth/delete-pending-user", emailTokenOptions, getDefaultAttrs(), 403);
    }

    @Test(description = "Delete pending user invalid email")
    void testDeletePendingUserInvalidEmail() throws Exception {
        EmailTokenOptions emailTokenOptions = registerUser().getEmailTokenOptions();
        emailTokenOptions.setEmail("a" + emailTokenOptions.getEmail());
        performHttpPost("/api/v1/users/auth/delete-pending-user", emailTokenOptions, getDefaultAttrs(), 403);
    }

    @Test(description = "Confirm username after OAuth2 registration")
    void testConfirmUsernameAfterOAuthRegistration() throws Exception {
        OAuth2Mock oAuth2Mock = mockOauth2Registration();

        String body = objToJson(Map.of("username", oAuth2Mock.getUsername()));
        Map<String, String> attrs = getDefaultAttrs();
        addCookieToAttrs(Map.of("confirmationToken", oAuth2Mock.getConfirmationToken()), attrs);

        String response = performHttpPost("/api/v1/users/auth/username-confirmation", body, attrs, 200);
        AuthenticationResponse authResponse = gson.fromJson(response, AuthenticationResponse.class);

        assertNotNull(authResponse.getToken());
        assertNotNull(authResponse.getRefreshToken());

        User createdUser = authResponse.getUser();

        assertEquals(oAuth2Mock.getUsername(), createdUser.getUsername());
        assertEquals(oAuth2Mock.getEmail(), createdUser.getEmail());

        String query = "SELECT * FROM my_user u WHERE u.username = '%s'".formatted(createdUser.getUsername());
        List<Row> res = executeQuery(query);

        assertEquals(1, res.size());
        Row row = res.getFirst();

        assertEquals(createdUser.getUsername(), row.get("username"));
        assertEquals(createdUser.getEmail(), row.get("email"));
        assertEquals(createdUser.getName(), row.get("name"));
        assertNull(row.get("password"));
        assertNotNull(row.getTimestamp("register_date"));
        assertEquals("USER", row.get("role"));
        assertTrue(row.getBoolean("active"));
        assertFalse(row.getBoolean("frozen"));
        assertEquals("GOOGLE", row.get("oauth_provider"));
        assertEquals(oAuth2Mock.getOauthProviderId(), row.get("oauth_provider_id"));
        assertNotNull(row.getTimestamp("modified_at"));
    }

    @Test(description = "Confirm username after OAuth2 registration, username already taken")
    void testConfirmUsernameAfterOAuthRegistrationUsernameAlreadyTaken() throws Exception {
        User existingUser = createNewUser().getUser();
        OAuth2Mock oAuth2Mock = mockOauth2Registration();

        String body = objToJson(Map.of("username", existingUser.getUsername()));
        Map<String, String> attrs = getDefaultAttrs();
        addCookieToAttrs(Map.of("confirmationToken", oAuth2Mock.getConfirmationToken()), attrs);

        performHttpPost("/api/v1/users/auth/username-confirmation", body, attrs, 400);
    }

    @Test(description = "Confirm username after OAuth2 registration, bad username length")
    void testConfirmUsernameAfterOAuthRegistrationBadUsernameLength() throws Exception {
        OAuth2Mock oAuth2Mock = mockOauth2Registration();

        Map<String, String> attrs = getDefaultAttrs();
        addCookieToAttrs(Map.of("confirmationToken", oAuth2Mock.getConfirmationToken()), attrs);

        String body = objToJson(Map.of("username", generateRandomString(17, CHARACTERS_WITH_NUMBERS)));
        performHttpPost("/api/v1/users/auth/username-confirmation", body, attrs, 400);

        body = objToJson(Map.of("username", generateRandomString(1, CHARACTERS_WITH_NUMBERS)));
        performHttpPost("/api/v1/users/auth/username-confirmation", body, attrs, 400);
    }

    private OAuth2Mock mockOauth2Registration() {
        String username = generateRandomUsername();
        String email = username + "@gmail.com";
        Instant registerDate = Instant.now();
        Random random = new Random();
        StringBuilder oauthProviderId = new StringBuilder(20);
        for (int i = 0; i < 20; i++) {
            oauthProviderId.append(i == 0 ? 1 : random.nextInt(10));
        }

        String userQuery = """
                        INSERT INTO my_user (name, email, username, oauth_provider, oauth_provider_id, register_date, role, active, frozen)
                        VALUES ('Test Oauth2 User', '%s', '%s', 'GOOGLE', '%s', '%s', 'USER', false, false);
                        """
                .formatted(email, username, oauthProviderId, registerDate);

        executeInsert(userQuery);
        Long userId = getUserId(username);

        String confirmationToken = UUID.randomUUID().toString();
        String tokenQuery = """
                INSERT INTO token (user_id, token, expiry_date, type)
                VALUES (%s, '%s', '%s', 'USERNAME_CONFIRMATION')
                """.formatted(userId, confirmationToken, registerDate.plusSeconds(60 * 60));

        executeInsert(tokenQuery);

        return new OAuth2Mock(confirmationToken, username, email, oauthProviderId.toString());
    }

    private UserRegisterResponse registerUser() throws Exception {
        String username = generateRandomUsername();
        String email = username + "@test.com";
        String password = "test1234";

        return registerUser(username, email, password, 200);
    }

    private UserRegisterResponse registerUser(@Nullable String username, @Nullable String email, @Nullable String password, int expectedCode) throws Exception {
        if (username == null) username = generateRandomUsername();
        if (email == null) email = username + "@test.com";
        if (password == null) password = "test1234";

        String name = "Test User";
        User user = new User(name, username, email, password);
        String response = performHttpPost("/api/v1/users/auth/register", gson.toJson(user), getDefaultAttrs(), expectedCode);

        return new UserRegisterResponse(user, fromJson(response, EmailTokenOptions.class));
    }

    private String getAdminBody() throws Exception {
        return getAdminBody("!Password1234");
    }

    private String getAdminBody(String password) throws Exception {
        int randomInt = (int) (Math.random() * 10_000_000);
        String username = "it_admin_" + randomInt;
        String email = username + "@vcasino.com";

        return getBodyFromJson(Service.USER.getName() + "/auth/register.json")
                .replace("${username}", username)
                .replace("${password}", password)
                .replace("${email}", email);
    }

    private Long validateUserDatabaseEntry(User user, Boolean active, String role) {
        String query = "SELECT * FROM my_user u WHERE u.username = '%s'".formatted(user.getUsername());
        List<Row> res = executeQuery(query);

        assertEquals(1, res.size());
        Row row = res.getFirst();
        checkUserFields(row, user, active, role);

        return row.getLong("id");
    }

    private void validateEmailConfirmationTokenDatabaseEntry(Long userId) {
        String query = "SELECT * FROM token t WHERE t.user_id = '%s'".formatted(userId);
        List<Row> res = executeQuery(query);

        assertEquals(1, res.size());
        Row row = res.getFirst();

        assertNotNull(row.getLong("id"));
        assertNotNull(row.get("token"));
        assertEquals("EMAIL_CONFIRMATION", row.get("type"));
        assertNotNull(row.get("options"));

        Map<String, Object> options = fromJson(row.get("options"), Map.class);

        assertNotNull(options.get("resendToken"));
        assertEquals(1.0, options.get("emailsSent"));
        assertNotNull(options.get("sentAt"));
    }

    private void validateRefreshTokenDatabaseEntry(Long userId) {
        String query = "SELECT * FROM token t WHERE t.user_id = '%s'".formatted(userId);
        List<Row> res = executeQuery(query);

        assertEquals(1, res.size());
        Row row = res.getFirst();

        assertNotNull(row.getLong("id"));
        assertNotNull(row.get("token"));
        assertEquals("REFRESH", row.get("type"));
        assertNull(row.get("options"));
    }

    private void checkUserFields(Row dbRow, User user, Boolean active, String role) {
        assertNotNull(dbRow.getLong("id"));
        assertEquals(user.getUsername(), dbRow.get("username"));
        assertEquals(user.getEmail(), dbRow.get("email"));
        assertEquals(user.getName(), dbRow.get("name"));
        assertNotEquals(user.getPassword(), dbRow.get("password"));
        assertNotNull(dbRow.getTimestamp("register_date"));
        assertEquals(role, dbRow.get("role"));
        assertEquals(active, dbRow.getBoolean("active"));
        assertFalse(dbRow.getBoolean("frozen"));
        assertNull(dbRow.get("oauth_provider"));
        assertNull(dbRow.get("oauth_provider_id"));

        if (active && role.equals("USER")) {
            assertNotNull(dbRow.getTimestamp("modified_at"));
        } else {
            assertNull(dbRow.getTimestamp("modified_at"));
        }
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @FieldDefaults(level = AccessLevel.PRIVATE)
    private static class UserRegisterResponse {
        User user;
        EmailTokenOptions emailTokenOptions;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @FieldDefaults(level = AccessLevel.PRIVATE)
    private static class OAuth2Mock {
        String confirmationToken;
        String username;
        String email;
        String oauthProviderId;
    }
}
