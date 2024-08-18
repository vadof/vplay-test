package com.vcasino.tests.services.clicker.model;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class Account {
    Integer level;
    BigDecimal netWorth;
    BigDecimal balanceCoins;
    Integer availableTaps;
    Integer maxTaps;
    Integer earnPerTap;
    Integer tapsRecoverPerSec;
    Integer passiveEarnPerHour;
    Double passiveEarnPerSec;
    List<SectionUpgrades> sectionUpgrades;
}
