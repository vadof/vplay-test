package com.vcasino.tests.services.bet.model;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class Match {
    Long id;
    Tournament tournament;
    String matchPage;
    Participant participant1;
    Participant participant2;
    String format;
    String startDate;
    String status;
    Integer winner;
    Double winProbability1;
    Double winProbability2;
    List<Market> markets;
    String createdAt;
    String modifiedAt;
}
