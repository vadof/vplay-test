package com.vcasino.tests.services.clicker.model.streak;

import lombok.Data;

import java.util.List;

@Data
public class StreakInfo {
    List<DayReward> rewardsByDays;
    StreakState state;
}
