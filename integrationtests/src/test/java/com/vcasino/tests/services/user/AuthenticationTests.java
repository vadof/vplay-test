package com.vcasino.tests.services.user;

import com.vcasino.tests.common.GenericTest;
import com.vcasino.tests.common.Service;
import com.vcasino.tests.model.AuthenticationResponse;
import com.vcasino.tests.model.Response;
import com.vcasino.tests.model.Row;
import com.vcasino.tests.model.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.Test;

import java.util.List;

import static org.testng.AssertJUnit.assertEquals;
import static org.testng.AssertJUnit.assertNotNull;

public class AuthenticationTests extends GenericTest {

    private static final Logger log = LoggerFactory.getLogger(AuthenticationTests.class);

    @BeforeTest
    void setup() throws Exception {
        service = Service.USER;
        init();
    }

    @Test(description = "Register user")
    void testRegisterUser() throws Exception {
        AuthenticationResponse authenticationResponse = createNewUser();

        assertNotNull(authenticationResponse.getToken());
        assertNotNull(authenticationResponse.getRefreshToken());
        assertNotNull(authenticationResponse.getUser());

        assertEquals("Test", authenticationResponse.getUser().getFirstname());
        assertEquals("User", authenticationResponse.getUser().getLastname());
        assertEquals("EST", authenticationResponse.getUser().getCountry().getCode());
        assertEquals("Estonia", authenticationResponse.getUser().getCountry().getName());
        assertNotNull(authenticationResponse.getUser().getFirstname());
        assertNotNull(authenticationResponse.getUser().getLastname());

        validateDatabaseEntry(authenticationResponse.getUser(), "USER");
    }

    @Test(description = "Create user bad passwords")
    void testRegisterUserBadPassword() throws Exception {
        List<String> badPasswords = List.of("test123", "testtes", "!P2%");

        for (String pwd : badPasswords) {
            createNewUser(null, pwd, 400);
        }
    }

    @Test(description = "Create user good passwords")
    void testCreateUserGoodPassword() throws Exception {
        List<String> goodPasswords = List.of("test1234", "mysuperpassword", "!Password123", "no_password");

        for (String pwd : goodPasswords) {
            AuthenticationResponse res = createNewUser(null, pwd, 200);
            assertNotNull(res.getToken());
            assertNotNull(res.getRefreshToken());
            assertNotNull(res.getUser());
        }
    }

    @Test(description = "Create user bad username")
    void testCreateUserBadUsername() throws Exception {
        String invalidSymbols = "!@#$%^&*()+-=[]{}'|.>,</?`~л ";

        String username = "username";
        for (Character symbol : invalidSymbols.toCharArray()) {
            createNewUser(symbol + username, null, 400);
        }
    }

    @Test(description = "Login user")
    void testLoginUser() throws Exception {
        AuthenticationResponse registerResponse = createNewUser();
        sleep(1000);
        AuthenticationResponse loginResponse = authorizeUser(registerResponse.getUser().getUsername());

        assertNotNull(loginResponse.getToken());
        assertNotNull(loginResponse.getRefreshToken());
        assertNotNull(loginResponse.getUser());

        assertEquals("Test", loginResponse.getUser().getFirstname());
        assertEquals("User", loginResponse.getUser().getLastname());
        assertEquals("EST", loginResponse.getUser().getCountry().getCode());
        assertEquals("Estonia", loginResponse.getUser().getCountry().getName());
        assertNotNull(loginResponse.getUser().getFirstname());
        assertNotNull(loginResponse.getUser().getLastname());

        assertEquals(registerResponse.getUser().getUsername(), loginResponse.getUser().getUsername());
        assertEquals(registerResponse.getUser().getLastname(), loginResponse.getUser().getLastname());
        assertEquals(registerResponse.getUser().getCountry().getName(), loginResponse.getUser().getCountry().getName());
        assertEquals(registerResponse.getUser().getCountry().getCode(), loginResponse.getUser().getCountry().getCode());
    }

    @Test(description = "Invalid login")
    void testLoginInvalid() throws Exception {
        String password = "test1234";
        AuthenticationResponse res = createNewUser(null, null, 200);
        authorizeUser(res.getUser().getUsername(), password + "1", 403);
    }

    @Test(description = "Refresh authorization token")
    void testRefreshToken() throws Exception {

        AuthenticationResponse registerResponse = createNewUser();
        sleep(1000);
        String refreshToken = registerResponse.getRefreshToken();

        String body = "{\"refreshToken\":\"" + refreshToken + "\"}";

        String responseStr = performHttpPost("/api/v1/users/auth/refreshToken", body, getDefaultAttrs());
        Response response = jsonToObject(responseStr);

        assertNotNull(response.get("token"));
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
        String s = performHttpPost("/api/v1/users/auth/admin/register", body, attrs, 200);

        Response response = jsonToObject(s);

        User user = response.get("user", User.class);

        assertNotNull(response.get("token"));
        assertNotNull(user);
        assertNotNull(response.get("refreshToken"));
        assertNotNull(user.getCountry());

        validateDatabaseEntry(user, "ADMIN");
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

    private String getAdminBody() throws Exception {
        return getAdminBody("!Password123");
    }

    private String getAdminBody(String password) throws Exception {
        int randomInt = (int) (Math.random() * 10_000_000);
        String username ="it_admin_" + randomInt;
        String email = username + "@vcasino.com";

        return getBodyFromJson(Service.USER.getName() + "/auth/register.json")
                .replace("${username}", username)
                .replace("${password}", password)
                .replace("${email}", email);
    }

    private void validateDatabaseEntry(User user, String role) {
        String query = "SELECT * FROM my_user u WHERE u.username = '%s'".formatted(user.getUsername());
        List<Row> res = executeQuery(query);

        assertEquals(1, res.size());
        Row row = res.getFirst();

        assertEquals(user.getEmail(), row.get("email"));
        assertEquals(role, row.get("role"));
    }
}
