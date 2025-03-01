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
public class UserInformation {
    Long id;
    String name;
    String username;
    String email;
    String role;
    String oAuthProvider;
    String invitedBy;
    Long usersInvited;
    String registerDate;
    Boolean active;
    Boolean frozen;
}
