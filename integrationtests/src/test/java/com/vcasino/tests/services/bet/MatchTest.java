package com.vcasino.tests.services.bet;


import com.vcasino.tests.common.Service;
import com.vcasino.tests.services.bet.model.Match;
import com.vcasino.tests.services.bet.model.dto.MarketDto;
import com.vcasino.tests.services.bet.model.dto.MarketPairDto;
import com.vcasino.tests.services.bet.model.dto.MarketsByCategory;
import com.vcasino.tests.services.bet.model.dto.MatchDto;
import com.vcasino.tests.services.bet.model.dto.TournamentDto;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.testng.AssertJUnit.assertEquals;
import static org.testng.AssertJUnit.assertFalse;
import static org.testng.AssertJUnit.assertTrue;

public class MatchTest extends GenericBetTest {

    @BeforeTest()
    void setup() {
        init(Service.BET);
    }

    @Test(description = "Get tournaments and matches")
    void getTournamentsAndMatches() throws Exception {
        authorizeAdmin();

        var adminAttrs = getAttrsWithAuthorization(true);

        Match match = addMatch(adminAttrs);
        redisFlushCache();

        String s = performHttpGet("/api/v1/bet/matches", getDefaultAttrs());
        List<TournamentDto> response = Arrays.asList(fromJson(s, TournamentDto[].class));

        Optional<TournamentDto> tournamentOpt = response.stream()
                .filter(t -> t.getId().equals(match.getTournament().getId())).findFirst();
        assertTrue(tournamentOpt.isPresent());

        TournamentDto tournament = tournamentOpt.get();
        assertEquals(1, tournament.getMatches().size());
        MatchDto responseMatch = tournament.getMatches().getFirst();
        assertEquals(match.getId(), responseMatch.getId());

        assertEquals(match.getParticipant1().getName(), responseMatch.getParticipant1().getName());
        assertEquals(match.getParticipant2().getName(), responseMatch.getParticipant2().getName());
        assertFalse(responseMatch.getWinnerMatchMarkets().getClosed());

        List<MarketDto> markets = responseMatch.getWinnerMatchMarkets().getMarkets();
        assertEquals(2, markets.size());
        assertEquals(1.9, markets.getFirst().getOdds().doubleValue());
        assertEquals(1.9, markets.getLast().getOdds().doubleValue());
        assertEquals(match.getParticipant1().getName(), markets.getFirst().getOutcomeStr());
        assertEquals(match.getParticipant2().getName(), markets.getLast().getOutcomeStr());
    }

    @Test(description = "Get match markets")
    void getMatchMarkets() throws Exception {
        authorizeAdmin();

        Match match = addMatch(getAttrsWithAuthorization(true));
        redisFlushCache();

        String s = performHttpGet("/api/v1/bet/matches/" + match.getId(), getDefaultAttrs());
        List<MarketsByCategory> marketsByCategory = Arrays.asList(fromJson(s, MarketsByCategory[].class));

        Set<String> categories = Set.of("Match Winner", "Winner. Map 1", "Winner. Map 2",
                "Total Maps", "Total. Map 1", "Total. Map 2", "Total. Map 3", "Handicap Maps");

        assertEquals(categories.size(), marketsByCategory.size());
        for (MarketsByCategory category : marketsByCategory) {
            assertTrue(categories.contains(category.getCategory()));
            assertFalse(category.getMarketPairs().isEmpty());
            for (MarketPairDto marketPair : category.getMarketPairs()) {
                assertEquals(2, marketPair.getMarkets().size());
                assertFalse(marketPair.getClosed());
                for (MarketDto market : marketPair.getMarkets()) {
                    assertFalse(market.getClosed());
                }
            }
        }

    }

}
