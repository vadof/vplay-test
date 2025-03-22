package com.vcasino.tests.services.wallet.model;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;

import java.math.BigDecimal;
import java.time.Instant;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class Wallet {
    Long id;
    BigDecimal balance;
    BigDecimal reserved;
    Instant updatedAt;
    Boolean frozen;
    Integer version;
}
