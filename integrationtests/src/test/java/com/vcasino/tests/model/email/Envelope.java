package com.vcasino.tests.model.email;

import lombok.AccessLevel;
import lombok.Data;
import lombok.experimental.FieldDefaults;

import java.util.List;

@Data
@FieldDefaults(level = AccessLevel.PRIVATE)
public class Envelope {
    Address from;
    List<Address> to;
    String host;
    String remoteAddress;
}
