package com.vcasino.tests.services.bet.model;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class Market {
    Long id;
    Match match;
    BigDecimal outcome;
    BigDecimal odds;
    Boolean closed;
    Integer mapNumber;
    Integer participant;
    String result;
    String createdAt;
    String updatedAt;
    String type;
}