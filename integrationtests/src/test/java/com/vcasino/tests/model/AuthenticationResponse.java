package com.vcasino.tests.model;

import lombok.Data;

@Data
public class AuthenticationResponse {
    private String token;
    private String refreshToken;
    private User user;
}
