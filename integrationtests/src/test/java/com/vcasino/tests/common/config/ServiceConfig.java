package com.vcasino.tests.common.config;

import com.vcasino.tests.common.Service;
import lombok.Data;

@Data
public class ServiceConfig {
    String address;
    String port;
    Service service;
    DbConfig dbConfig;
}
