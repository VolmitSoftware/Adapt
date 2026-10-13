package art.arcane.adapt.content.adaptation.discovery;

import art.arcane.adapt.AdaptTestBase;
import art.arcane.adapt.api.adaptation.Cooldowns;
import art.arcane.adapt.api.fx.FxEmitter;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.fx.ViewerDisplayDirector;
import art.arcane.adapt.api.notification.AdaptHud;
import art.arcane.adapt.api.preference.CommonPreferences;
import art.arcane.adapt.api.world.AdaptPlayer;
import art.arcane.adapt.api.world.PlayerData;
import art.arcane.adapt.localization.AdaptLanguage;
import art.arcane.volmlib.util.math.M;
import io.papermc.paper.registry.RegistryAccess;
import net.kyori.adventure.key.Key;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.util.BoundingBox;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import org.mockito.MockedStatic;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyFloat;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.RETURNS_SELF;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DiscoverySixthSenseCueTest extends AdaptTestBase {
  @BeforeAll
  static void initializeSounds() {
    RegistryAccess access = mock(RegistryAccess.class, RETURNS_DEEP_STUBS);
    try (MockedStatic<RegistryAccess> registries = mockStatic(RegistryAccess.class)) {
      registries.when(RegistryAccess::registryAccess).thenReturn(access);
      Registry<Sound> sounds = Registry.SOUNDS;
      doAnswer(call -> mock(Sound.class)).when(sounds).getOrThrow(any(Key.class));
      assertNotNull(Sound.BLOCK_AMETHYST_BLOCK_CHIME);
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

  @Test
  void firstCueIsImmediateAndNearerHudUpdatesWaitForTheNextCueInterval() throws ReflectiveOperationException {
    AtomicLong now = new AtomicLong(1000L);
    Player player = mock(Player.class);
    World world = mock(World.class);
    UUID playerId = UUID.randomUUID();
    UUID worldId = UUID.randomUUID();
    UUID sweepId = UUID.randomUUID();
    when(player.getUniqueId()).thenReturn(playerId);
    when(world.getUID()).thenReturn(worldId);
    when(player.getLocation()).thenAnswer(call -> new Location(world, 0D, 100D, 0D));
    when(player.getEyeLocation()).thenAnswer(call -> new Location(world, 0D, 101.6D, 0D));
    FxEmitter effects = mock(FxEmitter.class, RETURNS_SELF);
    TestSense sense = spy(new TestSense(effects));
    sense.setConfig(new DiscoverySixthSense.Config());
    doReturn(5).when(sense).getActiveLevel(player);
    doReturn(true).when(sense).preferenceEnabled(player, DiscoverySixthSense.HUD);
    doReturn(false).when(sense).preferenceEnabled(player, DiscoverySixthSense.FULL);
    doReturn(CommonPreferences.Scale.FULL).when(sense).preference(player, DiscoverySixthSense.FREQUENCY);
    AdaptPlayer owner = mock(AdaptPlayer.class);
    PlayerData data = mock(PlayerData.class);
    doReturn(owner).when(sense).getPlayer(player);
    when(owner.getData()).thenReturn(data);
    GeneratedStructureSearch search = new GeneratedStructureSearch();
    GeneratedStructureSearch.Sweep sweep = search.begin(new GeneratedStructureSearch.Request(
        sweepId, world, worldId, 0D, 0D, 500D, List.of(), candidate -> { }));
    searches(sense).put(playerId, sweep);
    Method apply = DiscoverySixthSense.class.getDeclaredMethod("acceptCandidateOwned", Player.class,
        GeneratedStructureSearch.Candidate.class);
    apply.setAccessible(true);
    try (MockedStatic<M> clock = mockStatic(M.class, CALLS_REAL_METHODS);
         MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class);
         MockedStatic<AdaptLanguage> language = mockStatic(AdaptLanguage.class);
         MockedStatic<AdaptHud> hud = mockStatic(AdaptHud.class);
         MockedStatic<ViewerDisplayDirector> displays = mockStatic(ViewerDisplayDirector.class)) {
      clock.when(M::ms).thenAnswer(call -> now.get());
      bukkit.when(() -> Bukkit.createBlockData(Material.LIGHT_BLUE_STAINED_GLASS)).thenReturn(mock(BlockData.class));
      pulseThrottle(sense).mark(playerId);
      apply.invoke(sense, player, candidate(sweepId, worldId, 200D));
      verify(effects).sound(any(Sound.class), anyFloat(), anyFloat());
      verify(data).addStat("discovery.sixth-sense.senses", 1D);
      now.set(1200L);
      apply.invoke(sense, player, candidate(sweepId, worldId, 100D));
      hud.verify(() -> AdaptHud.ambientStatus(player, "discovery-sixth-sense", "E 100"));
      verify(effects, times(1)).sound(any(Sound.class), anyFloat(), anyFloat());
      verify(data, times(1)).addStat("discovery.sixth-sense.senses", 1D);
      now.set(5000L);
      apply.invoke(sense, player, candidate(sweepId, worldId, 50D));
      hud.verify(() -> AdaptHud.ambientStatus(player, "discovery-sixth-sense", "E 50"));
      verify(effects, times(2)).sound(any(Sound.class), anyFloat(), anyFloat());
      verify(data, times(2)).addStat("discovery.sixth-sense.senses", 1D);
    }
  }

  private GeneratedStructureSearch.Candidate candidate(UUID sweepId, UUID worldId, double x) {
    return new GeneratedStructureSearch.Candidate(sweepId, worldId, x, 0D, "Synthetic Structure", "",
        new BoundingBox(x - 1D, 0D, -1D, x + 1D, 70D, 1D));
  }

  @SuppressWarnings("unchecked")
  private Map<UUID, GeneratedStructureSearch.Sweep> searches(DiscoverySixthSense sense) throws ReflectiveOperationException {
    Field field = DiscoverySixthSense.class.getDeclaredField("searches");
    field.setAccessible(true);
    return (Map<UUID, GeneratedStructureSearch.Sweep>) field.get(sense);
  }

  private Cooldowns pulseThrottle(DiscoverySixthSense sense) throws ReflectiveOperationException {
    Field field = DiscoverySixthSense.class.getDeclaredField("pulseThrottle");
    field.setAccessible(true);
    return (Cooldowns) field.get(sense);
  }

  private static final class TestSense extends DiscoverySixthSense {
    private final FxEmitter effects;

    private TestSense(FxEmitter effects) { this.effects = effects; }

    @Override
    protected FxEmitter fx(Location location, FxPriority priority) { return effects; }

    @Override
    protected FxEmitter fx(Entity entity, FxPriority priority) { return effects; }
  }
}
