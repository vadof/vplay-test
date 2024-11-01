package com.vcasino.tests.common;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.internal.LinkedTreeMap;
import com.vcasino.tests.common.config.DbConfig;
import com.vcasino.tests.common.config.ServiceConfig;
import com.vcasino.tests.model.AuthenticationResponse;
import com.vcasino.tests.model.Response;
import com.vcasino.tests.model.Row;
import com.vcasino.tests.model.User;
import lombok.extern.slf4j.Slf4j;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;

@Slf4j
public abstract class GenericTest {

    protected Service service;
    protected ServiceConfig config;
    protected Gson gson = new Gson();
    protected AuthenticationResponse auth;
    protected AuthenticationResponse adminAuth;

    protected void init(Service service) throws Exception {
        this.service = service;
        init();
    }

    protected void init() throws Exception {
        log.info("Create client");

        auth = null;
        config = loadServiceConfig(service);

        log.info("Client created");
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
        User admin = gson.fromJson(serviceJson.getAsJsonObject("adminUser"), User.class);

        ServiceConfig serviceConfig = new ServiceConfig();
        serviceConfig.setAdminUser(admin);
        serviceConfig.setService(service);
        serviceConfig.setAddress(serviceJson.get("address").getAsString());
        serviceConfig.setPort(serviceJson.get("port").getAsString());
        serviceConfig.setDbConfig(dbConfig);

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

        String body = objToJson(Map.of("username", username, "password", "test1234"));
        String response = performHttpPost("/api/v1/users/auth/login", body, getDefaultAttrs());
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

    private Connection getConnection(ServiceConfig config) throws SQLException {
        return DriverManager.getConnection(
                config.getDbConfig().getUrl(),
                config.getDbConfig().getUser(),
                config.getDbConfig().getPassword()
        );
    }

    protected <T> T fromJson(String jsonString, Class<T> clazz) {
        return gson.fromJson(jsonString, clazz);
    }

    protected <T> T fromJson(Object obj, Class<T> clazz) {
        return gson.fromJson(gson.toJson(obj), clazz);
    }

    protected List<Row> executeQuery(String query) {
        return executeQuery(query, config);
    }

    private List<Row> executeQuery(String query, ServiceConfig config) {
        List<Row> rows = new ArrayList<>();

        log.info("Execute query {}", query);

        try (Connection connection = getConnection(config);
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
        executeUpdate(query, config);
    }

    private void executeUpdate(String query, ServiceConfig config) {
        log.info("Execute update {}", query);

        try (Connection connection = getConnection(config);
             Statement statement = connection.createStatement()) {

            int affectedRows = statement.executeUpdate(query);
            assertEquals(affectedRows, 1);
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    protected Long getUserId(String username) {
        ServiceConfig userServiceConfig = loadServiceConfig(Service.USER);
        String query = "SELECT * FROM my_user u WHERE u.username = '%s'".formatted(username);
        List<Row> row = executeQuery(query, userServiceConfig);
        assertEquals(row.size(), 1);
        return row.getFirst().getLong("id");
    }

    protected String readFile(String serviceName, String className, String fileName) throws Exception {
        String resourceName = serviceName + "/" + className + "/" + fileName;
        URI uri = this.getClass().getClassLoader().getResource(resourceName).toURI();
        return Files.readString(Path.of(uri));
    }
}
