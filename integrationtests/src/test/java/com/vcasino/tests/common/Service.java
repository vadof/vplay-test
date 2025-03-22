package com.vcasino.tests.common;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum Service {
    USER("userService"),
    CLICKER("clickerService"),
    WALLET("walletService");

    private final String name;

}
