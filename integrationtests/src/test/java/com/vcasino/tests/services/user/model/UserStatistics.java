package com.vcasino.tests.services.user.model;

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
public class UserStatistics {
    Long registeredUsers;
    Long registeredUsersWithOAuth;
    Long registeredUsersInvitedByOthers;
    Long registeredUsersToday;
    Long registeredUsersLastWeek;
    Long registeredUsersLastMonth;
    Long activeUsers;
    Long frozenUsers;
}
