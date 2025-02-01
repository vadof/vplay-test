package com.vcasino.tests.model.email;

import lombok.AccessLevel;
import lombok.Data;
import lombok.experimental.FieldDefaults;

@Data
@FieldDefaults(level = AccessLevel.PRIVATE)
public class Headers {
    String date;
    String from;
    String to;
    String messageId;
    String mimeVersion;
    String contentType;
    String contentTransferEncoding;
}
