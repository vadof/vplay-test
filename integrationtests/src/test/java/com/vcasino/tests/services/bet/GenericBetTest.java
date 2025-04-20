package com.vcasino.tests.services.bet;

import com.vcasino.tests.common.GenericTest;
import com.vcasino.tests.services.bet.model.Match;
import com.vcasino.tests.services.bet.model.Participant;
import com.vcasino.tests.services.bet.model.Tournament;

import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

public abstract class GenericBetTest extends GenericTest {

    protected List<String> getImageKeys(String folder) throws Exception {
        String res = performHttpGet("/api/v1/bet/admin/images/" + folder, getAttrsWithAuthorization(true));
        return Arrays.asList(fromJson(res, String[].class));
    }

    protected String addImage(Map<String, String> attrs, int status, String folder) throws Exception {
        String boundary = "----WebKitFormBoundary7MA4YWxkTrZu0gW";
        var builder = new StringBuilder();

        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        URL resource = classLoader.getResource("betService/Counter-Strike.webp");
        Path filePath = Paths.get(resource.toURI());
        String fileName = filePath.getFileName().toString();
        builder.append("--").append(boundary).append("\r\n");
        builder.append("Content-Disposition: form-data; name=\"files\"; filename=\"")
                .append(fileName).append("\"\r\n");
        builder.append("Content-Type: application/octet-stream\r\n\r\n");
        builder.append(new String(java.nio.file.Files.readAllBytes(filePath))).append("\r\n");

        builder.append("--").append(boundary).append("--");

        attrs.put("Content-Type", "multipart/form-data; boundary=" + boundary);

        String res = performHttpPost("/api/v1/bet/admin/images/upload/" + folder, builder.toString(), attrs, status);
        if (status < 400) {
            return Arrays.asList(fromJson(res, String[].class)).getFirst();
        }
        return null;
    }

    protected Tournament addTournament(Map<String, String> attrs, int status) throws Exception {
        List<String> tournaments = getImageKeys("tournaments");
        String imageKey;
        if (tournaments.isEmpty()) {
            imageKey = addImage(getAttrsWithAuthorization(true), 200, "tournaments");
        } else {
            imageKey = tournaments.getFirst();
        }

        String title = generateRandomString(15, CHARACTERS + "_" + LocalDateTime.now().getYear());
        String discipline = "Counter-Strike";
        String tournamentPage = "https://" + title + ".com";
        LocalDateTime startDate = LocalDateTime.now().plusYears(10);
        LocalDateTime endDate = startDate.plusDays(10);

        String body = objToJson(Map.of(
                "title", title,
                "discipline", discipline,
                "tournamentPage", tournamentPage,
                "imageKey", imageKey,
                "startDate", startDate.toString(),
                "endDate", endDate.toString()
        ));

        String res = performHttpPost("/api/v1/bet/admin/tournaments", body, attrs, status);
        return status < 400 ? fromJson(res, Tournament.class) : null;
    }

    protected Participant addParticipant(Map<String, String> attrs, int status) throws Exception {
        List<String> participants = getImageKeys("participants");
        String imageKey;
        if (participants.isEmpty()) {
            imageKey = addImage(getAttrsWithAuthorization(true), 200, "participants");
        } else {
            imageKey = participants.getFirst();
        }

        String name = generateRandomString(10, CHARACTERS + "_CS");
        String discipline = "Counter-Strike";
        String participantPage = "https://" + name + ".com";

        String body = objToJson(Map.of(
                "name", name,
                "discipline", discipline,
                "participantPage", participantPage,
                "imageKey", imageKey
        ));

        String res = performHttpPost("/api/v1/bet/admin/participants", body, attrs, status);
        return status < 400 ? fromJson(res, Participant.class) : null;
    }

    protected Match addMatch(Tournament tournament, Participant p1, Participant p2, Map<String, String> attrs, int status) throws Exception {
        Integer tournamentId = tournament.getId();
        String participant1 = p1.getName();
        String participant2 = p2.getName();
        String matchPage = "https://" + participant1 + "_vs_" + participant2 + ".com";
        String format = "BO3";
        String winProbability1 = "0.5";
        String winProbability2 = "0.5";
        LocalDateTime startDate = LocalDateTime.parse(tournament.getStartDate()).plusDays(1);

        String body = objToJson(Map.of(
                "tournamentId", tournamentId.toString(),
                "participant1", participant1,
                "participant2", participant2,
                "matchPage", matchPage,
                "format", format,
                "winProbability1", winProbability1,
                "winProbability2", winProbability2,
                "startDate", startDate.toString()
        ));

        String res = performHttpPost("/api/v1/bet/admin/matches", body, attrs, status);
        return status < 400 ? fromJson(res, Match.class) : null;
    }

    protected Match addMatch(Map<String, String> attrs) throws Exception {
        Participant participant1 = addParticipant(attrs, 200);
        Participant participant2 = addParticipant(attrs, 200);
        Tournament tournament = addTournament(attrs, 200);

        return addMatch(tournament, participant1, participant2, attrs, 200);
    }

}
