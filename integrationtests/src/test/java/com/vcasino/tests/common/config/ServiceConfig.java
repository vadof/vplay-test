package com.vcasino.tests.common.config;

import com.vcasino.tests.common.Service;
import com.vcasino.tests.model.User;
import lombok.Data;

import java.util.Map;

@Data
public class ServiceConfig {
    String address;
    String port;
    String mailDevUrl;
    Service service;
    DbConfig dbConfig;
    Map<String, DbConfig> additionalDbConfigs;
    RedisConfig redisConfig;
    User adminUser;
}
