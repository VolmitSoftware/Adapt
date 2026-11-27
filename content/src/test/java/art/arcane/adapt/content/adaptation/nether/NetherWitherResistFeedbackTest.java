package art.arcane.adapt.content.adaptation.nether;

import art.arcane.adapt.AdaptTestBase;
import art.arcane.adapt.api.fx.FxEmitter;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.engine.adaptation.AdaptationHost;
import art.arcane.adapt.engine.adaptation.PlayerStateRegistry;
import art.arcane.adapt.engine.component.ComponentHost;
import art.arcane.adapt.platform.entity.EquipmentSlot;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.Cancellable;
import art.arcane.adapt.platform.event.entity.DamageCause;
import art.arcane.adapt.platform.event.entity.EntityDamageEvent;
import art.arcane.adapt.platform.event.entity.FinalDamage;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.world.WorldRef;
import art.arcane.adapt.testkit.FakeItem;
import art.arcane.adapt.testkit.MockedEngine;
import art.arcane.adapt.testkit.RuntimeSpy;
import art.arcane.adapt.testkit.TestContent;
import art.arcane.volmlib.util.math.M;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.RETURNS_SELF;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NetherWitherResistFeedbackTest extends AdaptTestBase {
    private MockedEngine engine;

    @BeforeEach
    void startEngine() {
        engine = new MockedEngine(core);
    }

    @AfterEach
    void resetPlayerState() {
        PlayerStateRegistry.reset();
        if (engine != null) {
            engine.close();
        }
    }

    @Test
    void continuousRoseDamageRemainsProtectedWhileFeedbackRunsAtMostOncePerSecond() {
        QuietResistance adaptation = new QuietResistance();
        PlayerRef player = armoredPlayer();
        AtomicLong now = new AtomicLong(1_000L);

        try (MockedStatic<M> clock = mockStatic(M.class)) {
            clock.when(M::ms).thenAnswer(invocation -> now.get());
            for (int tick = 0; tick < 120; tick++) {
                EntityDamageEvent event = damage(player);
                adaptation.onEntityDamage(event);
                verify(event).setCancelled(true);
                verify(event, never()).setDamage(anyDouble());
                now.addAndGet(50L);
            }
        }

        assertThat(adaptation.feedbackCount).isEqualTo(6);
        assertThat(adaptation.negatedCount).isEqualTo(120);
        verify(player, never()).setHealth(anyDouble());
    }

    @Test
    void feedbackResumesAtTheOneSecondBoundary() {
        QuietResistance adaptation = new QuietResistance();
        PlayerRef player = armoredPlayer();
        AtomicLong now = new AtomicLong(1_000L);

        try (MockedStatic<M> clock = mockStatic(M.class)) {
            clock.when(M::ms).thenAnswer(invocation -> now.get());
            adaptation.onEntityDamage(damage(player));
            now.set(1_999L);
            adaptation.onEntityDamage(damage(player));
            assertThat(adaptation.feedbackCount).isEqualTo(1);

            now.set(2_000L);
            adaptation.onEntityDamage(damage(player));
            assertThat(adaptation.feedbackCount).isEqualTo(2);
        }

        assertThat(adaptation.negatedCount).isEqualTo(3);
    }

    @Test
    void onePlayersFeedbackDoesNotSuppressAnotherPlayersCue() {
        QuietResistance adaptation = new QuietResistance();
        PlayerRef first = armoredPlayer();
        PlayerRef second = armoredPlayer();

        try (MockedStatic<M> clock = mockStatic(M.class)) {
            clock.when(M::ms).thenReturn(1_000L);
            adaptation.onEntityDamage(damage(first));
            adaptation.onEntityDamage(damage(second));
            adaptation.onEntityDamage(damage(first));
            adaptation.onEntityDamage(damage(second));
        }

        assertThat(adaptation.feedbackCount).isEqualTo(2);
        assertThat(adaptation.negatedCount).isEqualTo(4);
    }

    @Test
    void failedResistanceDoesNotConsumeTheFeedbackWindow() {
        QuietResistance adaptation = new QuietResistance();
        PlayerRef player = armoredPlayer();
        NetherWitherResist.Config noChance = new NetherWitherResist.Config();
        noChance.setBasePieceChance(0D);
        noChance.setChanceAddition(0D);
        adaptation.setConfig(noChance);
        EntityDamageEvent missed = damage(player);

        try (MockedStatic<M> clock = mockStatic(M.class)) {
            clock.when(M::ms).thenReturn(1_000L);
            adaptation.onEntityDamage(missed);
            adaptation.setConfig(new NetherWitherResist.Config());
            EntityDamageEvent protectedHit = damage(player);
            adaptation.onEntityDamage(protectedHit);
            verify(protectedHit).setCancelled(true);
        }

        verify(missed, never()).setCancelled(true);
        assertThat(adaptation.feedbackCount).isEqualTo(1);
        assertThat(adaptation.negatedCount).isEqualTo(1);
    }

    @Test
    void otherDamageDoesNotActivateResistanceFeedback() {
        QuietResistance adaptation = new QuietResistance();
        EntityDamageEvent event = damage(armoredPlayer(), DamageCause.FIRE);

        adaptation.onEntityDamage(event);

        verify(event, never()).setCancelled(true);
        assertThat(adaptation.feedbackCount).isZero();
        assertThat(adaptation.negatedCount).isZero();
    }

    @Test
    void retiredPlayersFeedbackStateIsClearedWithOtherPlayerState() {
        QuietResistance adaptation = new QuietResistance();
        PlayerRef player = armoredPlayer();

        try (MockedStatic<M> clock = mockStatic(M.class)) {
            clock.when(M::ms).thenReturn(1_000L);
            adaptation.onEntityDamage(damage(player));
            PlayerStateRegistry.clearPlayer(player.uuid());
            adaptation.onEntityDamage(damage(player));
        }

        assertThat(adaptation.feedbackCount).isEqualTo(2);
    }

    private static PlayerRef armoredPlayer() {
        PlayerRef player = mock(PlayerRef.class);
        WorldRef world = mock(WorldRef.class);
        when(player.uuid()).thenReturn(UUID.randomUUID());
        when(player.location()).thenAnswer(invocation -> new Loc(world, 0D, 100D, 0D));
        for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
            when(player.equipment(slot)).thenReturn(FakeItem.of("netherite_chestplate"));
        }
        return player;
    }

    private static EntityDamageEvent damage(PlayerRef player) {
        return damage(player, DamageCause.WITHER);
    }

    private static EntityDamageEvent damage(PlayerRef player, DamageCause cause) {
        return spy(new EntityDamageEvent(player, cause, Id.minecraft("wither"), 1D, FinalDamage.IDENTITY));
    }

    /** A real Wither Resistance whose engine side records stats and effects instead of reaching a player. */
    private static final class QuietResistance {
        private final NetherWitherResist adaptation = TestContent.adaptation(Id.adapt("nether"), NetherWitherResist::new);
        private final FxEmitter emitter = mock(FxEmitter.class, RETURNS_SELF);
        private final AdaptationHost runtime;
        private int feedbackCount;
        private int negatedCount;

        private QuietResistance() {
            ComponentHost.of(adaptation).useFixedConfig(new NetherWitherResist.Config());
            runtime = RuntimeSpy.install(adaptation, adaptation);
            doAnswer(invocation -> {
                Cancellable event = invocation.getArgument(1);
                if (event == null || !event.isCancelled()) {
                    ((Runnable) invocation.getArgument(2)).run();
                }
                return null;
            }).when(runtime).withAdaptedPlayer(any(PlayerRef.class), any(), any(Runnable.class));
            doReturn(3).when(runtime).level(any(PlayerRef.class));
            doAnswer(invocation -> {
                negatedCount += (int) (double) invocation.<Double>getArgument(2);
                return null;
            }).when(runtime).addStat(any(PlayerRef.class), anyString(), anyDouble());
            doAnswer(invocation -> {
                feedbackCount++;
                return emitter;
            }).when(runtime).fx(any(Loc.class), any(FxPriority.class));
        }

        private void onEntityDamage(EntityDamageEvent event) {
            adaptation.onEntityDamage(event);
        }

        private void setConfig(NetherWitherResist.Config config) {
            runtime.useFixedConfig(config);
        }
    }
}
