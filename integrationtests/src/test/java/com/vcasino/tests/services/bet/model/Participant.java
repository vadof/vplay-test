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
public class Participant {
    Integer id;
    String name;
    String shortName;
    String discipline;
    String image;
    String participantPage;
    String createdAt;
    String modifiedAt;
}
