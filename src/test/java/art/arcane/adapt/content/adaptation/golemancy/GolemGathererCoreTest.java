package art.arcane.adapt.content.adaptation.golemancy;

import art.arcane.adapt.AdaptTestBase;
import art.arcane.adapt.api.golem.GolemCoreBehaviors;
import art.arcane.adapt.api.golem.GolemCoreType;
import art.arcane.adapt.api.golem.GolemTestItems;
import art.arcane.adapt.api.preference.CommonPreferences;
import art.arcane.adapt.api.recipe.ConfiguredRecipeParser;
import art.arcane.adapt.api.world.AdaptServer;
import art.arcane.adapt.util.common.scheduling.J;
import art.arcane.volmlib.util.math.M;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.quality.Strictness;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

class GolemGathererCoreTest extends AdaptTestBase {
  private static final UUID WORLD = UUID.fromString("00000000-0000-0000-0000-0000000000f1");
  private static final UUID OWNER = UUID.fromString("00000000-0000-0000-0000-0000000000f2");
  private static final long FIVE_MINUTES = 300_000L;

  private final AtomicLong clock = new AtomicLong(100_000L);
  private MockedStatic<ConfiguredRecipeParser> parsers;
  private MockedStatic<M> time;
  private MockedStatic<J> scheduling;
  private GolemGathererCore adaptation;
  private World world;

  @BeforeEach
  void construct() {
    when(plugin.namespace()).thenReturn("adapt");
    ConfiguredRecipeParser parser = mock(ConfiguredRecipeParser.class);
    when(parser.builder(any(), any())).thenReturn(Optional.empty());
    parsers = mockStatic(ConfiguredRecipeParser.class);
    parsers.when(() -> ConfiguredRecipeParser.bukkit(any(), any())).thenReturn(parser);
    time = mockStatic(M.class);
    time.when(M::ms).thenAnswer(invocation -> clock.get());
    scheduling = mockStatic(J.class);
    world = mock(World.class);
    when(world.getUID()).thenReturn(WORLD);
    adaptation = new GolemGathererCore();
    adaptation.onRuntimeActivated();
  }

  @AfterEach
  void close() {
    adaptation.unregister();
    scheduling.close();
    time.close();
    parsers.close();
  }

  @Test
  void magnetAndOrbControlsAppearOnceTheirLevelsUnlock() {
    assertThat(adaptation.getPlayerPreferences())
        .containsExactly(CommonPreferences.ENABLED, GolemancyPreferences.MAGNET, GolemancyPreferences.ABSORB_ORBS);
    GolemancyPreferenceAssertions.assertVisibleFrom(adaptation, GolemancyPreferences.MAGNET, 4);
    GolemancyPreferenceAssertions.assertVisibleFrom(adaptation, GolemancyPreferences.ABSORB_ORBS, 2);
  }

  @Test
  void magnetAndOrbAbsorbingDefaultOnAndFollowTheServerDefault() {
    when(plugin.getAdaptServer()).thenReturn(mock(AdaptServer.class));

    assertThat(adaptation.magnets(OWNER)).isTrue();
    assertThat(adaptation.absorbsOrbs(OWNER)).isTrue();

    GolemancyPreferenceAssertions.serverDefault(adaptation, GolemancyPreferences.MAGNET, "OFF");
    GolemancyPreferenceAssertions.serverDefault(adaptation, GolemancyPreferences.ABSORB_ORBS, "OFF");
    assertThat(adaptation.magnets(OWNER)).isFalse();
    assertThat(adaptation.absorbsOrbs(OWNER)).isFalse();
  }

  @Test
  void aPlayerDeathKeepsGatherersAwayFromItsDropsForFiveMinutes() {
    Item drop = item(at(13D, 64D, 0D));
    Item farAway = item(at(20D, 64D, 0D));

    adaptation.on(death(at(6D, 64D, 0D)));

    assertThat(work().eligible(drop, OWNER, clock.get())).isFalse();
    assertThat(work().eligible(farAway, OWNER, clock.get())).isTrue();
    assertThat(adaptation.hasTickDemand()).isTrue();
    assertThat(work().eligible(drop, OWNER, clock.get() + FIVE_MINUTES)).isTrue();

    clock.addAndGet(FIVE_MINUTES);
    adaptation.onTick();
    assertThat(adaptation.hasTickDemand()).isFalse();
  }

  @Test
  void aZeroDeathSiteDurationProtectsNothing() {
    assertThat(adaptation.reloadConfigSnapshot("deathSiteSeconds = 0\n", dataFolder.toPath().resolve("off.toml").toFile(), false)).isTrue();
    Item drop = item(at(6D, 64D, 0D));

    adaptation.on(death(at(6D, 64D, 0D)));

    assertThat(work().eligible(drop, OWNER, clock.get())).isTrue();
    assertThat(adaptation.hasTickDemand()).isFalse();
  }

  private GathererWork work() {
    return (GathererWork) GolemCoreBehaviors.of(GolemCoreType.GATHERER).orElseThrow();
  }

  private PlayerDeathEvent death(Location location) {
    Player victim = mock(Player.class, withSettings().strictness(Strictness.LENIENT));
    when(victim.getLocation()).thenReturn(location);
    when(victim.getWorld()).thenReturn(world);
    PlayerDeathEvent event = mock(PlayerDeathEvent.class, withSettings().strictness(Strictness.LENIENT));
    when(event.getEntity()).thenReturn(victim);
    when(event.getPlayer()).thenReturn(victim);
    return event;
  }

  private Item item(Location location) {
    ItemStack stack = GolemTestItems.stack(Material.DIAMOND, 1);
    Item item = mock(Item.class, withSettings().strictness(Strictness.LENIENT));
    when(item.getLocation()).thenReturn(location);
    when(item.getWorld()).thenReturn(world);
    when(item.getItemStack()).thenReturn(stack);
    when(item.isValid()).thenReturn(true);
    when(item.canPlayerPickup()).thenReturn(true);
    when(item.canMobPickup()).thenReturn(true);
    return item;
  }

  private Location at(double x, double y, double z) {
    return new Location(world, x, y, z);
  }
}
