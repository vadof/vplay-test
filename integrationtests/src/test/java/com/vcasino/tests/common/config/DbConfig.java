package com.vcasino.tests.common.config;

import lombok.Data;

@Data
public class DbConfig {
    private String url;
    private String port;
    private String user;
    private String password;
}
