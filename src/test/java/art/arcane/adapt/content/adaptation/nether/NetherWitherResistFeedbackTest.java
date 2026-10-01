package art.arcane.adapt.content.adaptation.nether;

import art.arcane.adapt.AdaptTestBase;
import art.arcane.adapt.api.adaptation.PlayerStateRegistry;
import art.arcane.adapt.api.fx.FxEmitter;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.volmlib.util.math.M;
import io.papermc.paper.registry.RegistryAccess;
import net.kyori.adventure.key.Key;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Registry;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.AfterEach;
import org.mockito.MockedStatic;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.RETURNS_SELF;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NetherWitherResistFeedbackTest extends AdaptTestBase {
    @AfterEach
    void resetPlayerState() {
        PlayerStateRegistry.reset();
    }

    @BeforeAll
    static void initializeRegistries() {
        RegistryAccess access = mock(RegistryAccess.class, RETURNS_DEEP_STUBS);
        try (MockedStatic<RegistryAccess> registries = mockStatic(RegistryAccess.class)) {
            registries.when(RegistryAccess::registryAccess).thenReturn(access);
            Registry<Sound> sounds = Registry.SOUNDS;
            doAnswer(invocation -> mock(Sound.class)).when(sounds).getOrThrow(any(Key.class));
            assertThat(Sound.PARTICLE_SOUL_ESCAPE).isNotNull();
            Registry<Particle> particles = Registry.PARTICLE_TYPE;
            doAnswer(invocation -> {
                NamespacedKey key = invocation.getArgument(0);
                for (Particle particle : Particle.values()) {
                    if (particle.getKey().equals(key)) {
                        return particle;
                    }
                }
                return null;
            }).when(particles).get(any(NamespacedKey.class));
        }
    }

    @Test
    void continuousRoseDamageRemainsProtectedWhileFeedbackRunsAtMostOncePerSecond() {
        QuietResistance adaptation = new QuietResistance();
        Player player = armoredPlayer();
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
        Player player = armoredPlayer();
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
        Player first = armoredPlayer();
        Player second = armoredPlayer();
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
        Player player = armoredPlayer();
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
        EntityDamageEvent event = damage(armoredPlayer());
        when(event.getCause()).thenReturn(EntityDamageEvent.DamageCause.FIRE);
        adaptation.onEntityDamage(event);
        verify(event, never()).setCancelled(true);
        assertThat(adaptation.feedbackCount).isZero();
        assertThat(adaptation.negatedCount).isZero();
    }

    @Test
    void retiredPlayersFeedbackStateIsClearedWithOtherPlayerState() {
        QuietResistance adaptation = new QuietResistance();
        Player player = armoredPlayer();
        try (MockedStatic<M> clock = mockStatic(M.class)) {
            clock.when(M::ms).thenReturn(1_000L);
            adaptation.onEntityDamage(damage(player));
            PlayerStateRegistry.clearPlayer(player.getUniqueId());
            adaptation.onEntityDamage(damage(player));
        }
        assertThat(adaptation.feedbackCount).isEqualTo(2);
    }

    private static Player armoredPlayer() {
        Player player = mock(Player.class);
        EntityEquipment equipment = mock(EntityEquipment.class);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        when(player.getEquipment()).thenReturn(equipment);
        when(player.getLocation()).thenAnswer(invocation -> new Location(mock(World.class), 0D, 100D, 0D));
        for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
            ItemStack armor = mock(ItemStack.class);
            when(armor.getType()).thenReturn(Material.NETHERITE_CHESTPLATE);
            when(equipment.getItem(slot)).thenReturn(armor);
        }
        return player;
    }

    private static EntityDamageEvent damage(Player player) {
        EntityDamageEvent event = mock(EntityDamageEvent.class);
        when(event.getEntity()).thenReturn(player);
        when(event.getCause()).thenReturn(EntityDamageEvent.DamageCause.WITHER);
        return event;
    }

    private static final class QuietResistance extends NetherWitherResist {
        private final FxEmitter emitter = mock(FxEmitter.class, RETURNS_SELF);
        private int feedbackCount;
        private int negatedCount;

        private QuietResistance() {
            setConfig(new Config());
        }

        @Override
        public void withAdaptedPlayer(Player player, Cancellable event, Runnable action) {
            if (!event.isCancelled()) {
                action.run();
            }
        }

        @Override
        public int getLevel(Player player) {
            return 3;
        }

        @Override
        protected void addStat(Player player, String stat, double amount) {
            negatedCount += (int) amount;
        }

        @Override
        protected FxEmitter fx(Location location, FxPriority priority) {
            feedbackCount++;
            return emitter;
        }
    }
}
