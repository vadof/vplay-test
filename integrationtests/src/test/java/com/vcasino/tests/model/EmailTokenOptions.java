package com.vcasino.tests.model;

import lombok.AccessLevel;
import lombok.Data;
import lombok.experimental.FieldDefaults;

@Data
@FieldDefaults(level = AccessLevel.PRIVATE)
public class EmailTokenOptions {
    String email;
    String resendToken;
    Integer emailsSent;
    Boolean canResend;
}
