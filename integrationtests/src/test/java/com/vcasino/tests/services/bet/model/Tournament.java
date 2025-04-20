package com.vcasino.tests.services.bet.model;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class Tournament {
    Integer id;
    String title;
    String discipline;
    String tournamentPage;
    String image;
    String startDate;
    String endDate;
    String createdAt;
    String modifiedAt;
}
