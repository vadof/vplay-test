package com.vcasino.tests.services.clicker.model;

import lombok.Data;

@Data
public class Upgrade {
    String name;
    Integer level;
    String section;
    Integer profitPerHour;
    Integer profitPerHourDelta;
    Integer price;
    Condition condition;
    Boolean maxLevel;
    Boolean available;
}
