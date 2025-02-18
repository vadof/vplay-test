package com.vcasino.tests.common;

import com.beust.jcommander.internal.Nullable;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.internal.LinkedTreeMap;
import com.vcasino.tests.common.config.DbConfig;
import com.vcasino.tests.common.config.ServiceConfig;
import com.vcasino.tests.model.AuthenticationResponse;
import com.vcasino.tests.model.EmailTokenOptions;
import com.vcasino.tests.model.Response;
import com.vcasino.tests.model.Row;
import com.vcasino.tests.model.User;
import com.vcasino.tests.model.email.Address;
import com.vcasino.tests.model.email.Email;
import lombok.extern.slf4j.Slf4j;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.TimeZone;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;

@Slf4j
public abstract class GenericTest {

    protected Service service;
    protected ServiceConfig config;
    protected Gson gson = new Gson();
    protected EmailTokenOptions emailTokenOptions;
    protected AuthenticationResponse auth;
    protected AuthenticationResponse adminAuth;
    protected final String CHARACTERS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
    protected final String CHARACTERS_WITH_NUMBERS = CHARACTERS + "0123456789";

    protected void init(Service service) {
        log.info("Use {} service config", service);

        this.service = service;
        auth = null;
        config = loadServiceConfig(service);

        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
    }

    private ServiceConfig loadServiceConfig(Service service) {
        InputStream inputStream = getClass().getClassLoader().getResourceAsStream("configuration.json");
        if (inputStream == null) {
            throw new RuntimeException("Configuration file not found");
        }

        InputStreamReader reader = new InputStreamReader(inputStream, StandardCharsets.UTF_8);
        JsonObject jsonObject = JsonParser.parseReader(reader).getAsJsonObject();
        JsonObject serviceJson = jsonObject.getAsJsonObject(service.getName());

        DbConfig dbConfig = gson.fromJson(serviceJson.getAsJsonObject("database"), DbConfig.class);
        DbConfig registrationDbConfig = gson.fromJson(serviceJson.getAsJsonObject("registrationDatabase"), DbConfig.class);
        User admin = gson.fromJson(serviceJson.getAsJsonObject("adminUser"), User.class);

        ServiceConfig serviceConfig = new ServiceConfig();
        serviceConfig.setAdminUser(admin);
        serviceConfig.setService(service);
        serviceConfig.setAddress(serviceJson.get("address").getAsString());
        serviceConfig.setPort(serviceJson.get("port").getAsString());
        serviceConfig.setMailDevUrl(serviceJson.get("mailDevUrl").getAsString());
        serviceConfig.setDbConfig(dbConfig);
        serviceConfig.setRegistrationDbConfig(registrationDbConfig);

        return serviceConfig;
    }

    protected String performHttpGet(String endpoint) throws Exception {
        return performHttpGet(endpoint, null);
    }

    protected String performHttpGet(String endpoint, Map<String, String> attrs) throws Exception {
        return performHttpGet(endpoint, attrs, 200);
    }

    protected String performHttpGet(String endpoint, Map<String, String> attrs, int expectedCode) throws Exception {
        String url = buildUrl(endpoint);
        log.info("GET Request to {}: ", url);

        try (HttpClient client = HttpClient.newHttpClient()) {
            HttpRequest request = buildGetRequest(url, attrs);
            return performHttp(client, request, expectedCode);
        }
    }

    protected String performHttpPost(String endpoint, Object body, Map<String, String> attrs) throws Exception {
        return performHttpPost(endpoint, gson.toJson(body), attrs);
    }

    protected String performHttpPost(String endpoint, Object body, Map<String, String> attrs, int expectedCode) throws Exception {
        return performHttpPost(endpoint, gson.toJson(body), attrs, expectedCode);
    }

    protected String performHttpPost(String endpoint, String body, Map<String, String> attrs) throws Exception {
        return performHttpPost(endpoint, body, attrs, 200);
    }

    protected String performHttpPost(String endpoint, String body, Map<String, String> attrs, int expectedCode) throws Exception {
        String url = buildUrl(endpoint);
        log.info("POST Request to {}: ", url);

        try (HttpClient client = HttpClient.newHttpClient()) {
            HttpRequest request = buildPostRequest(url, body, attrs);
            return performHttp(client, request, expectedCode);
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
                .POST(body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body));
        setHeaders(requestBuilder, attrs);

        return requestBuilder.build();
    }

    private void setHeaders(HttpRequest.Builder requestBuilder, Map<String, String> attrs) {
        if (attrs != null) {
            attrs.forEach(requestBuilder::header);
        }
    }

    private String buildUrl(String endpoint) {
        String address = config.getAddress();
        String port = config.getPort();
        return address + ":" + port + endpoint;
    }

    protected Map<String, String> getDefaultAttrs() {
        Map<String, String> attrs = new HashMap<>();
        attrs.put("Content-Type", "application/json");
        return attrs;
    }

    protected Map<String, String> getAttrsWithAuthorization() {
        return getAttrsWithAuthorization(false);
    }

    protected Map<String, String> getAttrsWithAuthorization(boolean admin) {
        Map<String, String> attrs = getDefaultAttrs();
        setAuthorizationHeader(attrs, admin ? adminAuth : auth);
        return attrs;
    }

    private void setAuthorizationHeader(Map<String, String> attrs, AuthenticationResponse auth) {
        assertNotNull(auth);
        assertNotNull(auth.getToken());
        attrs.put("Authorization", "Bearer " + auth.getToken());
    }

    protected void addCookieToAttrs(Map<String, String> cookies, Map<String, String> attrs) {
        List<String> cookieValues = new ArrayList<>();
        for (Map.Entry<String, String> cookie : cookies.entrySet()) {
            cookieValues.add(cookie.getKey() + "=" + cookie.getValue());
        }
        attrs.put("Cookie", String.join("; ", cookieValues));
    }

    private String performHttp(HttpClient client, HttpRequest request) throws Exception {
        return performHttp(client, request, 200);
    }

    private String performHttp(HttpClient client, HttpRequest request, int expectedCode) throws Exception {
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        String body = response.body();

        assertEquals(response.statusCode(),
                expectedCode,
                "Request finished with status code " + response.statusCode() + "\nMessage: " + body);

        log.info("Response {}: {}", response.statusCode(), body);

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
        return createNewUser(null, null, 200);
    }

    protected AuthenticationResponse createNewUser(@Nullable String username, @Nullable String password, int expectedCode) throws Exception {
        if (username == null) {
            username = generateRandomUsername();
        }

        log.info("Create new user {}", username);

        String email = expectedCode >= 400 ? generateRandomUsername() + "@test.com" : username + "@test.com";

        if (password == null) {
            password = "test1234";
        }

        String body = getBodyFromJson(Service.USER.getName() + "/auth/register.json")
                .replace("${username}", username)
                .replace("${email}", email)
                .replace("${password}", password);

        String response = performHttpPost("/api/v1/users/auth/register", body, getDefaultAttrs(), expectedCode);

        emailTokenOptions = gson.fromJson(response, EmailTokenOptions.class);

        String confirmationToken = getConfirmationTokenFromDb(username);

        return confirmEmail(confirmationToken, 200);
    }

    protected String getConfirmationTokenFromDb(String username) {
        String query =
                 """
                 SELECT t.token FROM token t
                 JOIN my_user u on t.user_id = u.id
                 WHERE u.username = '%s'
                 """.formatted(username);
        List<Row> rows = executeQuery(query, config.getRegistrationDbConfig());
        assertEquals(rows.size(), 1);

        return rows.getFirst().get("token");
    }

    protected AuthenticationResponse confirmEmail(String confirmationToken, int expectedCode) throws Exception {
        String body = objToJson(Map.of("confirmationToken", confirmationToken));
        String response = performHttpPost("/api/v1/users/auth/email-confirmation", body, getDefaultAttrs(), expectedCode);
        auth = gson.fromJson(response, AuthenticationResponse.class);
        return auth;
    }

    protected String getConfirmationTokenFromEmail(String email) throws Exception {
        int maxAttempts = 5;

        while (maxAttempts != 0) {
            List<Email> emails = getEmails();

            for (Email emailObj : emails) {
                if (emailObj.getTo() != null && !emailObj.getTo().isEmpty()) {
                    for (Address recipient : emailObj.getTo()) {
                        if (recipient.getAddress().equals(email)) {
                            String confirmationToken = extractConfirmationToken(emailObj.getHtml());
                            if (confirmationToken != null) {
                                return confirmationToken;
                            }
                        }
                    }
                }
            }

            sleep(3000);

            maxAttempts--;
        }

        throw new RuntimeException("No confirmation token found for email: " + email);
    }

    protected void findConfirmationTokenEmailsAmount(String email, int expectedAmount) throws Exception {
        int maxAttempts = 5;

        while (maxAttempts != 0) {
            List<Email> emails = getEmails();
            int amount = 0;

            for (Email emailObj : emails) {
                if (emailObj.getTo() != null && !emailObj.getTo().isEmpty()) {
                    for (Address recipient : emailObj.getTo()) {
                        if (recipient.getAddress().equals(email)) {
                            String confirmationToken = extractConfirmationToken(emailObj.getHtml());
                            if (confirmationToken != null) {
                                amount++;
                            }
                        }
                    }
                }
            }

            if (amount == expectedAmount) {
                log.info("Found {} emails", amount);
                return;
            } else {
                log.warn("Found {} emails, expected {}", amount, expectedAmount);
            }

            sleep(2000);

            maxAttempts--;
        }

        throw new RuntimeException(expectedAmount + " emails not found");
    }

    private List<Email> getEmails() throws Exception {
        log.info("Getting emails");

        URL url = new URL(config.getMailDevUrl());

        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("GET");

        int responseCode = connection.getResponseCode();
        System.out.println("Response Code: " + responseCode);

        if (responseCode == HttpURLConnection.HTTP_OK) {
            BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()));
            String inputLine;
            StringBuilder response = new StringBuilder();

            while ((inputLine = in.readLine()) != null) {
                response.append(inputLine);
            }
            in.close();

            return Arrays.asList(gson.fromJson(response.toString(), Email[].class));
        } else {
            throw new RuntimeException("Failed to retrieve emails. Response Code: " + responseCode);
        }
    }

    private String extractConfirmationToken(String html) {
        String regex = "confirmationToken=([a-fA-F0-9-]+)";
        Pattern pattern = Pattern.compile(regex);
        Matcher matcher = pattern.matcher(html);

        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }

    protected String generateRandomUsername() {
        int randomInt = (int) (Math.random() * 10_000_000);
        return "it_user_" + randomInt;
    }

    protected String generateRandomString(int length, String characters) {
        Random random = new Random();

        StringBuilder sb = new StringBuilder(length);

        for (int j = 0; j < length; j++) {
            int index = random.nextInt(characters.length());
            sb.append(characters.charAt(index));
        }

        return sb.toString();
    }

    protected AuthenticationResponse authorizeUser(String username) throws Exception {
        return authorizeUser(username, "test1234", 200);
    }

    protected AuthenticationResponse authorizeUser(String username, @Nullable String password, int expectedCode) throws Exception {
        log.info("Authorize user {}", username);

        if (password == null) {
            password = "test1234";
        }

        String body = objToJson(Map.of("username", username, "password", password));
        String response = performHttpPost("/api/v1/users/auth/login", body, getDefaultAttrs(), expectedCode);
        auth = gson.fromJson(response, AuthenticationResponse.class);

        return auth;
    }

    protected AuthenticationResponse authorizeAdmin() throws Exception {
        log.info("Authorize admin");

        String username = config.getAdminUser().getUsername();
        String password = config.getAdminUser().getPassword();

        String body = objToJson(Map.of("username", username, "password", password));
        String response = performHttpPost("/api/v1/users/auth/login", body, getDefaultAttrs());
        adminAuth = gson.fromJson(response, AuthenticationResponse.class);

        return adminAuth;
    }

    protected String objToJson(Map<String, String> obj) {
        return gson.toJson(obj);
    }

    protected void sleep(long ms) throws Exception {
        log.info("Sleep for {} milliseconds", ms);
        Thread.sleep(ms);
    }

    protected Response jsonToObject(String s) {
        log.info("Convert {}", s);
        return new Response(gson.fromJson(s, LinkedTreeMap.class));
    }

    protected List<Response> jsonToListObjects(String s) {
        log.info("Convert {}", s);
        List<LinkedTreeMap<String, ?>> list = gson.fromJson(s, List.class);
        List<Response> responses = new ArrayList<>();

        for (LinkedTreeMap<String, ?> map : list) {
            responses.add(new Response(map));
        }
        return responses;
    }

    private Connection getConnection(DbConfig config) throws SQLException {
        return DriverManager.getConnection(
                config.getUrl(),
                config.getUser(),
                config.getPassword()
        );
    }

    protected <T> T fromJson(String jsonString, Class<T> clazz) {
        return gson.fromJson(jsonString, clazz);
    }

    protected <T> T fromJson(Object obj, Class<T> clazz) {
        return gson.fromJson(gson.toJson(obj), clazz);
    }

    protected List<Row> executeQuery(String query) {
        return executeQuery(query, config.getDbConfig());
    }

    private List<Row> executeQuery(String query, DbConfig dbConfig) {
        List<Row> rows = new ArrayList<>();

        log.info("Execute query {}", query);

        try (Connection connection = getConnection(dbConfig);
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(query)) {

            ResultSetMetaData metaData = resultSet.getMetaData();
            int columnCount = metaData.getColumnCount();

            while (resultSet.next()) {
                Map<String, Object> rowMap = new HashMap<>();
                for (int i = 1; i <= columnCount; i++) {
                    String columnName = metaData.getColumnName(i);
                    Object value = resultSet.getObject(i);
                    rowMap.put(columnName, value);
                }
                Row row = new Row(rowMap);
                rows.add(row);
            }

        } catch (SQLException e) {
            e.printStackTrace();
        }

        return rows;
    }

    protected void executeUpdate(String query) {
        executeUpdate(query, config.getDbConfig());
    }

    private void executeUpdate(String query, DbConfig config) {
        log.info("Execute update {}", query);

        try (Connection connection = getConnection(config);
             Statement statement = connection.createStatement()) {

            int affectedRows = statement.executeUpdate(query);
            assertEquals(affectedRows, 1);
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    protected void executeInsert(String query) {
        executeInsert(query, config.getDbConfig());
    }

    private void executeInsert(String query, DbConfig config) {
        log.info("Execute insert {}", query);

        try (Connection connection = getConnection(config);
             Statement statement = connection.createStatement()) {

            int affectedRows = statement.executeUpdate(query);
            assertEquals(affectedRows, 1);
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    protected Long getUserId(String username) {
        String query = "SELECT * FROM my_user u WHERE u.username = '%s'".formatted(username);
        List<Row> row = executeQuery(query, config.getRegistrationDbConfig());
        assertEquals(row.size(), 1);
        return row.getFirst().getLong("id");
    }

    protected String readFile(String serviceName, String className, String fileName) throws Exception {
        String resourceName = serviceName + "/" + className + "/" + fileName;
        URI uri = this.getClass().getClassLoader().getResource(resourceName).toURI();
        return Files.readString(Path.of(uri));
    }
}
