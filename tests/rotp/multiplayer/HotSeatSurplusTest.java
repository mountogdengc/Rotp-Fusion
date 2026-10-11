package rotp.multiplayer;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;
import rotp.multiplayer.hotseat.HotSeatSetup;

@EnabledIfSystemProperty(named = "rotp.integration", matches = "true")
class HotSeatSurplusTest {
    @TempDir static Path directory;
    @BeforeAll static void initialize() throws Exception { HotSeatTestFixture.initialize(directory); }

    @Test void eachHumanKeepsTheirOwnSurplusDestination() {
        var game = HotSeatTestFixture.start(1, 0, 1);
        game.startHotSeatGame(game.options(), new HotSeatSetup(List.of(
                new HotSeatSetup.Assignment("human-0", "Alice", 0),
                new HotSeatSetup.Assignment("human-1", "Bob", 1))));
        var alice = game.galaxy().empire(0);
        var bob = game.galaxy().empire(1);
        boolean aliceBefore = alice.divertColonyExcessToResearch();
        boolean bobBefore = bob.divertColonyExcessToResearch();

        alice.toggleColonyExcessToResearch();

        assertEquals(!aliceBefore, alice.divertColonyExcessToResearch());
        assertEquals(bobBefore, bob.divertColonyExcessToResearch());
    }
}
