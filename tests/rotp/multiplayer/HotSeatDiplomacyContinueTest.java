package rotp.multiplayer;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;
import rotp.model.game.GameSession;
import rotp.model.tech.TechLibrary;
import rotp.multiplayer.hotseat.HotSeatController;
import rotp.multiplayer.hotseat.HotSeatSetup;
import rotp.ui.RotPUI;
import rotp.ui.diplomacy.*;
import rotp.ui.multiplayer.HotSeatDesktop;

@EnabledIfSystemProperty(named = "rotp.integration", matches = "true")
class HotSeatDiplomacyContinueTest {
    @TempDir static Path directory;
    @BeforeAll static void initialize() throws Exception { HotSeatTestFixture.initialize(directory); }

    @Test void spyingThreatResponseCanAdvanceReportPlayback() {
        var game = planningGame(2);
        try {
            var message = new ThreatForSpying(DialogueManager.WARNING_ESPIONAGE);
            message.init();
            assertTrue(game.galaxy().empire(2).isAIControlled());
            message.diplomat(game.galaxy().empire(2));
            GameSession.performingTurn(true);
            game.pauseNextTurnProcessing("spying report");
            assertTrue(GameSession.isSuspended());
            message.select(0);
            assertFalse(GameSession.isSuspended());
        } finally { GameSession.performingTurn(false); game.resumeNextTurnProcessing(); }
    }

    @Test void technologyReplyCanAdvanceReportPlayback() {
        var game = planningGame();
        try {
            var reply = DiplomaticReply.answer(true, "Accepted");
            reply.resumeTurn(true);
            var message = DiplomacyRequestReply.create(game.galaxy().empire(1), reply);
            GameSession.performingTurn(true);
            game.pauseNextTurnProcessing("technology report");
            assertTrue(GameSession.isSuspended());
            message.select(0);
            assertFalse(GameSession.isSuspended());
        } finally { GameSession.performingTurn(false); game.resumeNextTurnProcessing(); }
    }

    @Test void freeTechnologyGiftReplyCanContinueDuringPlanning() throws Exception {
        var game = HotSeatTestFixture.start(1, 0, 1);
        game.startHotSeatGame(game.options(), new HotSeatSetup(List.of(
                new HotSeatSetup.Assignment("human-0", "Alice", 0),
                new HotSeatSetup.Assignment("human-1", "Bob", 1))));
        HotSeatController[] controller = new HotSeatController[1];
        HotSeatTestFixture.onEdt(() -> {
            controller[0] = new HotSeatController(game, new HotSeatDesktop(),
                    new HotSeatTurnTest.ImmediateChoices());
            try { HotSeatTestFixture.set(GameSession.class, game, "hotSeatController", controller[0]); }
            catch (Exception failure) { throw new AssertionError(failure); }
            controller[0].start();
        });
        try {
            HotSeatTurnTest.awaitPlanning(game);
            HotSeatTestFixture.onEdt(() -> {
                assertTrue(controller[0].canEdit(0));
                var reply = DiplomaticReply.answer(true, "Gift accepted");
                var message = DiplomacyRequestReply.create(game.galaxy().empire(1), reply);
                DiplomaticMessage.reply(message);
                assertTrue(RotPUI.instance().selectedPanel() instanceof rotp.ui.tech.DiplomaticMessageUI);
                message.select(0);
                assertFalse(RotPUI.instance().selectedPanel() instanceof rotp.ui.tech.DiplomaticMessageUI,
                        "Continue must leave the gift acknowledgement");
            });
        } finally { HotSeatTestFixture.onEdt(controller[0]::close); }
    }

    @Test void technologyCounterofferCanBeDeclinedDuringTurnPlayback() {
        var game = planningGame(2);
        try {
            var human = game.galaxy().empire(0);
            var ai = game.galaxy().empire(2);
            var tech = TechLibrary.current().tech(human.tech().allKnownTechs().get(0));
            var reply = DiplomaticReply.answer(true, "Technology requested");
            var menu = DiplomacyTechOfferMenu.create(human, ai, reply, tech);
            GameSession.performingTurn(true);
            game.pauseNextTurnProcessing("technology counteroffer");
            assertTrue(GameSession.isSuspended());
            menu.select(menu.numReplies() - 1);
            assertFalse(GameSession.isSuspended(), "Forget it must advance the turn");
        } finally { GameSession.performingTurn(false); game.resumeNextTurnProcessing(); }
    }

    @Test void requestedTechnologyCounterMenuCanBeCancelledDuringPlanning() throws Exception {
        var game = HotSeatTestFixture.start(2, 0, 1);
        game.startHotSeatGame(game.options(), new HotSeatSetup(List.of(
                new HotSeatSetup.Assignment("human-0", "Alice", 0),
                new HotSeatSetup.Assignment("human-1", "Bob", 1))));
        HotSeatController[] controller = new HotSeatController[1];
        HotSeatTestFixture.onEdt(() -> {
            controller[0] = new HotSeatController(game, new HotSeatDesktop(),
                    new HotSeatTurnTest.ImmediateChoices());
            try { HotSeatTestFixture.set(GameSession.class, game, "hotSeatController", controller[0]); }
            catch (Exception failure) { throw new AssertionError(failure); }
            controller[0].start();
        });
        try {
            HotSeatTurnTest.awaitPlanning(game);
            HotSeatTestFixture.onEdt(() -> {
                var human = game.galaxy().empire(0);
                var ai = game.galaxy().empire(2);
                var tech = TechLibrary.current().tech(human.tech().allKnownTechs().get(0));
                var reply = DiplomaticReply.answer(true, "Counteroffer requested");
                var menu = DiplomacyTechCounterMenu.create(human, ai, reply, tech);
                RotPUI.instance().selectRacesPanel();
                menu.select(menu.numReplies() - 1);
                assertTrue(RotPUI.instance().selectedPanel() instanceof rotp.ui.tech.DiplomaticMessageUI,
                        "Forget it must return to the diplomacy menu");
            });
        } finally { HotSeatTestFixture.onEdt(controller[0]::close); }
    }

    private static GameSession planningGame() { return planningGame(1); }

    private static GameSession planningGame(int opponents) {
        var game = HotSeatTestFixture.start(opponents, 0, 1);
        game.startHotSeatGame(game.options(), new HotSeatSetup(List.of(
                new HotSeatSetup.Assignment("human-0", "Alice", 0),
                new HotSeatSetup.Assignment("human-1", "Bob", 1))));
        var snapshot = game.hotSeatState().snapshot();
        assertTrue(game.hotSeatState().confirmHandoff(snapshot.revision()));
        return game;
    }
}
