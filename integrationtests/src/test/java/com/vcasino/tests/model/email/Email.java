package com.vcasino.tests.model.email;

import lombok.AccessLevel;
import lombok.Data;
import lombok.experimental.FieldDefaults;

import java.util.List;

@Data
@FieldDefaults(level = AccessLevel.PRIVATE)
public class Email {
    String html;
    Headers headers;
    String subject;
    String messageId;
    String priority;
    List<Address> from;
    List<Address> to;
    String date;
    String id;
    String time;
    Boolean read;
    Envelope envelope;
    String source;
    Integer size;
    String sizeHuman;
    List<Object> attachments;
    List<Object> calculatedBcc;
}