package art.arcane.adapt.content.adaptation.ranged;

import art.arcane.adapt.AdaptTestBase;
import art.arcane.adapt.api.fx.FxEmitter;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.preference.PlayerPreference;
import art.arcane.adapt.api.preference.CommonPreferences;
import art.arcane.adapt.util.common.scheduling.J;
import io.papermc.paper.registry.RegistryAccess;
import net.kyori.adventure.key.Key;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Registry;
import org.bukkit.Sound;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Snowball;
import org.bukkit.entity.Trident;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.metadata.MetadataValue;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedStatic;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.RETURNS_SELF;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

class RangedPlayerPreferencesTest extends AdaptTestBase {
  @BeforeAll
  static void initializeSoundRegistry() {
    RegistryAccess access = mock(RegistryAccess.class, RETURNS_DEEP_STUBS);
    try (MockedStatic<RegistryAccess> registry = mockStatic(RegistryAccess.class)) {
      registry.when(RegistryAccess::registryAccess).thenReturn(access);
      Registry<Sound> sounds = Registry.SOUNDS;
      doAnswer(call -> {
        Key key = call.getArgument(0);
        Sound sound = mock(Sound.class);
        when(sound.getKey()).thenReturn(new NamespacedKey(key.namespace(), key.value()));
        return sound;
      }).when(sounds).getOrThrow(any(Key.class));
      assertThat(Sound.UI_BUTTON_CLICK).isNotNull();
      Registry<Particle> particles = Registry.PARTICLE_TYPE;
      doAnswer(call -> {
        NamespacedKey key = call.getArgument(0);
        for (Particle particle : Particle.values()) {
          if (particle.getKey().equals(key)) {
            return particle;
          }
        }
        return null;
      }).when(particles).get(any(NamespacedKey.class));
    }
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void fetchUsesSneakingAtLaunchInsteadOfImpact(boolean sneakingAtLaunch) {
    RangedFetchShot adaptation = new RangedFetchShot() {
      private final Config config = new Config();

      @Override
      public Config getConfig() {
        return config;
      }

      @Override
      public int getActiveLevel(Player player) {
        return 1;
      }

      @Override
      public int getMaxLevel() {
        return 5;
      }

      @Override
      public boolean canAccessChest(Player player, Location location) {
        return true;
      }

      @Override
      public boolean preferenceEnabled(Player player, PlayerPreference<CommonPreferences.Toggle> preference) {
        return true;
      }
    };
    Player player = mock(Player.class);
    Arrow projectile = mock(Arrow.class);
    PersistentDataContainer data = mock(PersistentDataContainer.class);
    NamespacedKey key = NamespacedKey.fromString("adapt:fetch-launch-sneak");
    AtomicReference<Byte> snapshot = new AtomicReference<>();
    when(player.isOnline()).thenReturn(true);
    when(player.isSneaking()).thenReturn(sneakingAtLaunch);
    when(projectile.getShooter()).thenReturn(player);
    when(projectile.getLocation()).thenReturn(new Location(null, 1, 64, 1));
    when(projectile.getPersistentDataContainer()).thenReturn(data);
    doAnswer(call -> {
      snapshot.set(call.getArgument(2));
      return null;
    }).when(data).set(eq(key), eq(PersistentDataType.BYTE), any(Byte.class));
    when(data.getOrDefault(key, PersistentDataType.BYTE, (byte) 0))
        .thenAnswer(call -> snapshot.get() == null ? (byte) 0 : snapshot.get());
    try (MockedStatic<J> scheduler = mockStatic(J.class)) {
      scheduler.when(() -> J.isOwnedByCurrentRegion(player)).thenReturn(true);
      scheduler.when(() -> J.runEntity(eq(player), any(Runnable.class))).thenAnswer(call -> {
        call.<Runnable>getArgument(1).run();
        return true;
      });
      adaptation.on(new ProjectileLaunchEvent(projectile));
      assertThat(snapshot.get()).isEqualTo(sneakingAtLaunch ? (byte) 1 : (byte) 0);
      when(player.isSneaking()).thenReturn(!sneakingAtLaunch);
      adaptation.on(new ProjectileHitEvent(projectile));
      scheduler.verify(() -> J.runAt(any(Location.class), any(Runnable.class)),
          sneakingAtLaunch ? times(1) : never());
    }
  }

  @Test
  void fetchLaunchDoesNotReadSneakingAcrossPlayerRegions() {
    RangedFetchShot adaptation = new RangedFetchShot();
    Player player = mock(Player.class);
    Arrow projectile = mock(Arrow.class);
    when(projectile.getShooter()).thenReturn(player);
    try (MockedStatic<J> scheduler = mockStatic(J.class)) {
      adaptation.on(new ProjectileLaunchEvent(projectile));
    }
    verify(player, never()).isSneaking();
    verify(projectile, never()).getPersistentDataContainer();
  }

  @Test
  void shotCategoriesKeepTridentsAndCrossbowsDistinct() {
    Arrow arrow = mock(Arrow.class);
    Trident trident = mock(Trident.class);
    assertThat(RangedPreferences.ShotScope.BOW.accepts(arrow)).isTrue();
    when(arrow.isShotFromCrossbow()).thenReturn(true);
    assertThat(RangedPreferences.ShotScope.BOW.accepts(arrow)).isFalse();
    assertThat(RangedPreferences.ShotScope.CROSSBOW.accepts(arrow)).isTrue();
    assertThat(RangedPreferences.ShotScope.ARROWS.accepts(trident)).isFalse();
    assertThat(RangedPreferences.ShotScope.TRIDENTS.accepts(trident)).isTrue();
    assertThat(RangedPreferences.ShotScope.THROWN.accepts(mock(Snowball.class))).isTrue();
  }

  @Test
  void personalProjectileFilterChangesOnlyThatPlayersLaunch() {
    TestHeavyDraw adaptation = new TestHeavyDraw();
    Player bowOnly = mock(Player.class);
    Player crossbowOnly = mock(Player.class);
    adaptation.shots.put(bowOnly, RangedPreferences.ShotScope.BOW);
    adaptation.shots.put(crossbowOnly, RangedPreferences.ShotScope.CROSSBOW);
    Arrow rejected = arrow(bowOnly);
    Arrow accepted = arrow(crossbowOnly);
    when(rejected.isShotFromCrossbow()).thenReturn(true);
    when(accepted.isShotFromCrossbow()).thenReturn(true);

    adaptation.on(new ProjectileLaunchEvent(rejected));
    adaptation.on(new ProjectileLaunchEvent(accepted));

    verify(rejected, never()).setVelocity(any(Vector.class));
    verify(rejected, never()).setMetadata(eq(RangedHeavyDraw.HEAVY_LEVEL_META), any(MetadataValue.class));
    assertThat(accepted.getVelocity().length()).isLessThan(3D);
    assertThat(RangedHeavyDraw.readHeavyLevel(accepted)).isEqualTo(3);
  }

  @Test
  void committedHeavyShotKeepsPenaltyAndDamageAfterDisabling() {
    TestHeavyDraw adaptation = new TestHeavyDraw();
    Player owner = mock(Player.class);
    Arrow arrow = arrow(owner);
    adaptation.on(new ProjectileLaunchEvent(arrow));
    double launchSpeed = arrow.getVelocity().length();
    adaptation.enabled = false;
    adaptation.shots.put(owner, RangedPreferences.ShotScope.TRIDENTS);
    EntityDamageByEntityEvent hit = mock(EntityDamageByEntityEvent.class);
    when(hit.getDamager()).thenReturn(arrow);
    when(hit.getEntity()).thenReturn(mock(Entity.class));
    when(hit.getDamage()).thenReturn(4D);
    List<Double> damage = new ArrayList<>();
    doAnswer(call -> { damage.add(call.getArgument(0)); return null; }).when(hit).setDamage(anyDouble());
    try (MockedStatic<J> scheduler = mockStatic(J.class)) {
      adaptation.on(hit);
    }
    assertThat(launchSpeed).isLessThan(3D);
    assertThat(arrow.getVelocity().length()).isEqualTo(launchSpeed);
    assertThat(damage).singleElement().satisfies(value -> assertThat(value).isGreaterThan(4D));
  }

  private Arrow arrow(Player owner) {
    Arrow arrow = mock(Arrow.class);
    List<MetadataValue> metadata = new ArrayList<>();
    Vector[] velocity = {new Vector(3, 0, 0)};
    when(arrow.getShooter()).thenReturn(owner);
    when(arrow.getMetadata(RangedHeavyDraw.HEAVY_LEVEL_META)).thenReturn(metadata);
    when(arrow.getVelocity()).thenAnswer(call -> velocity[0]);
    doAnswer(call -> { velocity[0] = call.getArgument(0); return null; }).when(arrow).setVelocity(any(Vector.class));
    doAnswer(call -> { metadata.add(call.getArgument(1)); return null; }).when(arrow)
        .setMetadata(eq(RangedHeavyDraw.HEAVY_LEVEL_META), any(MetadataValue.class));
    return arrow;
  }

  private static final class TestHeavyDraw extends RangedHeavyDraw {
    private final Config config = new Config();
    private final Map<Player, RangedPreferences.ShotScope> shots = new IdentityHashMap<>();
    private final FxEmitter emitter = mock(FxEmitter.class, RETURNS_SELF);
    private boolean enabled = true;

    @Override public Config getConfig() { return config; }
    @Override public int getMaxLevel() { return 4; }
    @Override public int getActiveLevel(Player player) { return enabled ? 3 : 0; }
    @Override public boolean isProtectedFriendly(Player actor, Entity target) { return false; }
    @Override public <E extends Enum<E>> E preference(Player player, PlayerPreference<E> preference) {
      return preference.parse(shots.getOrDefault(player, RangedPreferences.ShotScope.ALL).name());
    }
    @Override protected FxEmitter fx(Location location, FxPriority priority) { return emitter; }
    @Override protected FxEmitter fx(Entity entity, FxPriority priority) { return emitter; }
  }
}
