package com.vcasino.tests.services.wallet.model;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;

import java.time.Instant;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class OutboxEvent<T> {
    UUID id;
    Long aggregateId;
    String type;
    T payload;
    String status;
    String applicant;
    Instant createdAt;
    Instant modifiedAt;
    Integer version;
}
