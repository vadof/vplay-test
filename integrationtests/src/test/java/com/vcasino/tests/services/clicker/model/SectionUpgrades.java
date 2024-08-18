package com.vcasino.tests.services.clicker.model;

import lombok.Data;

import java.util.List;

@Data
public class SectionUpgrades {
    Integer order;
    String section;
    List<Upgrade> upgrades;
}
