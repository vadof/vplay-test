package com.vcasino.tests.common;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.internal.LinkedTreeMap;
import com.vcasino.tests.common.config.DbConfig;
import com.vcasino.tests.common.config.ServiceConfig;
import com.vcasino.tests.model.AuthenticationResponse;
import com.vcasino.tests.model.Response;
import lombok.extern.slf4j.Slf4j;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

import static org.testng.AssertJUnit.assertEquals;

@Slf4j
public abstract class GenericTest {

    protected Service service;
    protected ServiceConfig serviceConfig;
    protected String jwtToken;
    protected Gson gson = new Gson();
    protected AuthenticationResponse auth;

    protected void init(Service service) throws Exception {
        this.service = service;
        init();
    }

    protected void init() throws Exception {
        log.info("Create client");

        loadServiceConfig();

        log.info("Client created");
    }

    private void loadServiceConfig() {
        InputStream inputStream = getClass().getClassLoader().getResourceAsStream("configuration.json");
        if (inputStream == null) {
            throw new RuntimeException("Configuration file not found");
        }

        InputStreamReader reader = new InputStreamReader(inputStream, StandardCharsets.UTF_8);
        JsonObject jsonObject = JsonParser.parseReader(reader).getAsJsonObject();
        JsonObject serviceJson = jsonObject.getAsJsonObject(service.getName());

        DbConfig dbConfig = gson.fromJson(serviceJson.getAsJsonObject("database"), DbConfig.class);

        serviceConfig = new ServiceConfig();
        serviceConfig.setService(service);
        serviceConfig.setAddress(serviceJson.get("address").getAsString());
        serviceConfig.setPort(serviceJson.get("port").getAsString());
        serviceConfig.setDbConfig(dbConfig);
    }

    protected String performHttpGet(String endpoint) throws Exception {
        return performHttpGet(endpoint, null);
    }

    protected String performHttpGet(String endpoint, Map<String, String> attrs) throws Exception {
        String url = buildUrl(endpoint);
        log.info("GET Request to {}: ", url);

        try (HttpClient client = HttpClient.newHttpClient()) {
            HttpRequest request = buildGetRequest(url, attrs);
            return performHttp(client, request);
        }
    }

    protected String performHttpPost(String endpoint, String body) throws Exception {
        return performHttpPost(endpoint, body, null);
    }

    protected String performHttpPost(String endpoint, String body, Map<String, String> attrs) throws Exception {
        String url = buildUrl(endpoint);
        log.info("POST Request to {}: ", url);

        try (HttpClient client = HttpClient.newHttpClient()) {
            HttpRequest request = buildPostRequest(url, body, attrs);
            return performHttp(client, request);
        }
    }

    private HttpRequest buildGetRequest(String url, Map<String, String> attrs) {
        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                .uri(URI.create(url));
        setHeaders(requestBuilder, attrs);
        return requestBuilder.build();
    }

    private HttpRequest buildPostRequest(String url, String body, Map<String, String> attrs) {
        log.info("Use body:\n{}", body);

        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .POST(HttpRequest.BodyPublishers.ofString(body));
        setHeaders(requestBuilder, attrs);

        return requestBuilder.build();
    }

    private void setHeaders(HttpRequest.Builder requestBuilder, Map<String, String> attrs) {
        if (attrs != null) {
            attrs.forEach(requestBuilder::header);
        }
    }

    private String buildUrl(String endpoint) {
        String address = serviceConfig.getAddress();
        String port = serviceConfig.getPort();
        return address + ":" + port + endpoint;
    }

    protected Map<String, String> getDefaultAttrs() {
        Map<String, String> attrs = new HashMap<>();
        attrs.put("Content-Type", "application/json");
        return attrs;
    }

    protected void setAuthorizationHeader(Map<String, String> attrs) {
        attrs.put("Authorization", "Bearer " + jwtToken);
    }

    private String performHttp(HttpClient client, HttpRequest request) throws Exception {
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        String body = response.body();

        assertEquals("Request finished with status code " + response.statusCode() + "\nMessage: " + body,
                200,
                response.statusCode());

        log.info("Response: {}", body);

        return body;
    }

    public String getBodyFromJson(String filePath) throws Exception {
        filePath = String.join("/", getResourceFolderPath(), filePath);
        return Files.readString(Paths.get(filePath));
    }

    private String getResourceFolderPath() {
        return "src/test/resources";
    }

    protected AuthenticationResponse createNewUser() throws Exception {
        int randomInt = (int) (Math.random() * 10_000_000);
        String uniqueUsername = "it_user_" + randomInt;
        String uniqueEmail = uniqueUsername + "@test.com";

        log.info("Create new user {}", uniqueUsername);

        String body = getBodyFromJson(Service.USER.getName() + "/auth/register.json")
                .replace("${username}", uniqueUsername)
                .replace("${email}", uniqueEmail);

        String response = performHttpPost("/api/v1/users/auth/register", body, getDefaultAttrs());

        auth = gson.fromJson(response, AuthenticationResponse.class);

        return auth;
    }

    protected AuthenticationResponse authorizeUser(String username) throws Exception {
        log.info("Authorize user {}", username);

        String body = "{\"username\":\"" + username + "\",\"password\":\"test1234\"}";
        String response = performHttpPost("/api/v1/users/auth/login", body, getDefaultAttrs());
        auth = gson.fromJson(response, AuthenticationResponse.class);

        return auth;
    }

    protected void sleep(long ms) throws Exception {
        log.info("Sleep for {} milliseconds", ms);
        Thread.sleep(ms);
    }

    protected Response jsonToObject(String s) {
        log.info("Convert {}", s);
        return new Response(gson.fromJson(s, LinkedTreeMap.class));
    }
}
