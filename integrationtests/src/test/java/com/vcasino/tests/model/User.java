package com.vcasino.tests.model;

import lombok.Data;

@Data
public class User {
    String firstname;
    String lastname;
    String username;
    String email;
    Country country;
}
