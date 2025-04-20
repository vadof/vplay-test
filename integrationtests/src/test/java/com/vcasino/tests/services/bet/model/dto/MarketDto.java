package com.vcasino.tests.services.bet.model.dto;

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
public class MarketDto {
    Long id;
    BigDecimal outcome;
    String outcomeStr;
    Boolean closed;
    BigDecimal odds;
    Integer mapNumber;
    String type;
}
