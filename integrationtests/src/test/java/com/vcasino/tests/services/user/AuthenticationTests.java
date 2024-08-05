package com.vcasino.tests.services.user;

import com.vcasino.tests.common.GenericTest;
import com.vcasino.tests.common.Service;
import com.vcasino.tests.model.AuthenticationResponse;
import com.vcasino.tests.model.Response;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.Test;

import static org.testng.AssertJUnit.assertEquals;
import static org.testng.AssertJUnit.assertNotNull;

public class AuthenticationTests extends GenericTest {

    @BeforeTest
    void setup() throws Exception {
        service = Service.USER;
        init();
    }

    @Test(description = "Register user")
    void registerUser() throws Exception {
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
    }

    @Test(description = "Login user")
    void loginUser() throws Exception {
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

    @Test(description = "Refresh authorization token")
    void refreshToken() throws Exception {

        AuthenticationResponse registerResponse = createNewUser();
        sleep(1000);
        String refreshToken = registerResponse.getRefreshToken();

        String body = "{\"refreshToken\":\"" + refreshToken + "\"}";

        String responseStr = performHttpPost("/api/v1/users/auth/refreshToken", body, getDefaultAttrs());
        Response response = jsonToObject(responseStr);

        assertNotNull(response.get("refreshToken"));
        assertNotNull(response.get("token"));
    }

}
