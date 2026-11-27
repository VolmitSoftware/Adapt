package art.arcane.adapt.client.insight;

import art.arcane.adapt.client.protocol.ServerFrame;
import art.arcane.adapt.client.config.ClientConfig;
import art.arcane.adapt.client.net.ClientNet;
import art.arcane.adapt.client.net.ClientTransport;
import art.arcane.adapt.client.protocol.ClientProtocol;
import art.arcane.adapt.client.ui.RecordingDraw;
import art.arcane.adapt.client.ui.UiCamera;
import art.arcane.adapt.client.ui.UiEntityPose;
import art.arcane.adapt.client.ui.UiWorld;
import art.arcane.adapt.client.ui.kit.FakeHost;
import art.arcane.adapt.api.insight.InsightBond;
import art.arcane.adapt.api.insight.InsightKind;
import art.arcane.adapt.client.qa.UiProbe;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InsightHudTest {
    private final AtomicLong clock = new AtomicLong(1_000L);
    private final FakeWorld world = new FakeWorld();
    private final ClientNet net = offline();
    private final InsightHud hud = new InsightHud(new FakeHost(320, 240), world, net, clock::get);

    @AfterEach
    void stopReporting() {
        UiProbe.insight(null);
    }

    @Test
    void theProbeReportsWhereThePlateIs() {
        world.entities.put(5, new UiEntityPose(0D, 63D, 6D, 0.6D, 1.95D));
        hud.state().target(zombie());
        UiProbe.snapshot();

        render();

        assertTrue(UiProbe.snapshot().contains("\"insight\":{\"target\":5,\"plate\":{\"x\":"));
    }

    @Test
    void enabledInsightOffersItsCapabilityAndDisablingWithdrawsIt() {
        assertTrue((net.sinks().capabilities() & ClientProtocol.INSIGHT) != 0);

        hud.enabled(false);
        assertEquals(0, net.sinks().capabilities() & ClientProtocol.INSIGHT);
        assertFalse(hud.settings().enabled());

        hud.enabled(true);
        assertTrue((net.sinks().capabilities() & ClientProtocol.INSIGHT) != 0);
    }

    @Test
    void aTargetUnderTheCrosshairOpensIntoTheFullReadout() {
        world.entities.put(5, new UiEntityPose(0D, 63D, 6D, 0.6D, 1.95D));
        hud.state().target(zombie());

        List<String> first = render();
        clock.addAndGet(InsightFocus.DURATION_MILLIS);
        List<String> open = render();

        assertFalse(first.stream().anyMatch((String call) -> call.contains(" Zombie")), "starts as a bar");
        assertTrue(open.stream().anyMatch((String call) -> call.startsWith("text") && call.endsWith(" Zombie")));
        assertTrue(open.stream().anyMatch((String call) -> call.endsWith(" 15/20")));
        assertTrue(open.contains("item minecraft:iron_sword null 0,0"));
        InsightBox box = hud.lastPlate();
        assertNotNull(box);
        assertTrue(box.bottom() <= 120D - InsightLayout.CROSSHAIR, "plate stays above the crosshair");
    }

    @Test
    void aTargetAwayFromTheCrosshairStaysASlimBar() {
        world.entities.put(5, new UiEntityPose(-4D, 63D, 6D, 0.6D, 1.95D));
        hud.state().target(zombie());

        render();
        clock.addAndGet(500L);
        List<String> calls = render();

        assertFalse(calls.stream().anyMatch((String call) -> call.startsWith("text")));
        assertTrue(calls.stream().anyMatch((String call) -> call.startsWith("fill")));
        assertEquals(InsightPlate.COLLAPSED_WIDTH * 1D, hud.lastPlate().width(), 1.0E-9D);
    }

    @Test
    void nothingIsDrawnForUntrackedOrDisabledTargets() {
        hud.state().target(zombie());
        assertTrue(render().isEmpty());
        assertNull(hud.lastPlate());

        world.entities.put(5, new UiEntityPose(0D, 63D, 6D, 0.6D, 1.95D));
        hud.enabled(false);
        hud.state().target(zombie());
        assertTrue(render().isEmpty());
    }

    @Test
    void marksDrawAShortBarOverEachTrackedCreature() {
        world.entities.put(5, new UiEntityPose(0D, 63D, 6D, 0.6D, 1.95D));
        world.entities.put(9, new UiEntityPose(3D, 63D, 8D, 0.6D, 1.95D));
        hud.state().target(zombie());
        hud.state().marks(List.of(new ServerFrame.InsightMarks.Mark(9, InsightKind.PASSIVE, 0.5D), new ServerFrame.InsightMarks.Mark(10, InsightKind.HOSTILE, 1D)));

        List<String> calls = render();

        assertEquals(2, calls.stream().filter((String call) -> call.startsWith("pushPose")).count());
        assertTrue(calls.stream().anyMatch((String call) -> call.startsWith("fill 0,0,7,2 ")), "half of a 14 wide bar");
    }

    private List<String> render() {
        RecordingDraw draw = new RecordingDraw();
        hud.render(draw, 0F);
        return draw.calls();
    }

    private static ServerFrame.InsightTarget zombie() {
        return new ServerFrame.InsightTarget(5, "minecraft:zombie", "", InsightKind.HOSTILE, 15D, 20D, 0D, 2D, InsightBond.NONE, "",
                List.of(), List.of(new ServerFrame.InsightTarget.Stat("minecraft:attack_damage", 3D)),
                List.of(new ServerFrame.InsightTarget.Effect("minecraft:speed", 0, 600, false)));
    }

    private static ClientNet offline() {
        return new ClientNet(new ClientTransport() {
            @Override
            public boolean ready() {
                return false;
            }

            @Override
            public void send(byte[] frame) {
            }
        }, ClientConfig.memory());
    }

    private static final class FakeWorld implements UiWorld {
        private final Map<Integer, UiEntityPose> entities = new HashMap<>();

        @Override
        public UiCamera camera(float partialTick) {
            return new UiCamera(0D, 64.62D, 0D, 0F, 0F, 70D);
        }

        @Override
        public UiEntityPose entity(int entityId, float partialTick) {
            return entities.get(entityId);
        }
    }
}
