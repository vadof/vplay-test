package com.vcasino.tests.services.bet;

import com.vcasino.tests.common.Service;
import com.vcasino.tests.services.bet.model.Participant;
import com.vcasino.tests.services.bet.model.Tournament;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.Test;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import static org.testng.AssertJUnit.assertTrue;

public class AdminTests extends GenericBetTest {

    @BeforeTest()
    void setup() {
        init(Service.BET);
    }

    @Test(description = "Add image")
    void addImageTest() throws Exception {
        createNewUser();
        authorizeAdmin();

        addImage(getAttrsWithAuthorization(false), 403, "participants");
        String key = addImage(getAttrsWithAuthorization(true), 200, "participants");
        assertTrue(key.contains("Counter-Strike.webp"));
        assertTrue(key.startsWith("participants/"));
    }

    @Test(description = "Get image keys")
    void getImageKeysTest() throws Exception {
        createNewUser();
        performHttpGet("/api/v1/bet/admin/images/participants", getAttrsWithAuthorization(false), 403);
        authorizeAdmin();

        String addedImageKey = addImage(getAttrsWithAuthorization(true), 200, "participants");
        String res = performHttpGet("/api/v1/bet/admin/images/participants", getAttrsWithAuthorization(true));

        Set<String> keys = new HashSet<>(Arrays.asList(fromJson(res, String[].class)));
        assertTrue(keys.contains(addedImageKey));
    }


    @Test(description = "Add new Tournament")
    void addTournamentTest() throws Exception {
        createNewUser();
        addTournament(getAttrsWithAuthorization(false), 403);
        authorizeAdmin();
        addTournament(getAttrsWithAuthorization(true), 200);
    }

    @Test(description = "Add new Participant")
    void addParticipantTest() throws Exception {
        createNewUser();
        addParticipant(getAttrsWithAuthorization(false), 403);
        authorizeAdmin();
        addParticipant(getAttrsWithAuthorization(true), 200);
    }

    @Test(description = "Add new Match")
    void addMatchTest() throws Exception {
        createNewUser();
        authorizeAdmin();

        var adminAttrs = getAttrsWithAuthorization(true);

        Participant participant1 = addParticipant(adminAttrs, 200);
        Participant participant2 = addParticipant(adminAttrs, 200);
        Tournament tournament = addTournament(adminAttrs, 200);

        addMatch(tournament, participant1, participant2, getAttrsWithAuthorization(false), 403);
        addMatch(tournament, participant1, participant2, adminAttrs, 200);
    }
}
