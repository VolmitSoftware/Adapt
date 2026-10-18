package art.arcane.adapt.content.adaptation.golemancy;

import art.arcane.adapt.AdaptTestBase;
import art.arcane.adapt.api.golem.GolemRegistry;
import art.arcane.adapt.api.golem.GolemancyRuntime;
import art.arcane.adapt.api.golem.overseer.GolemSignal;
import art.arcane.adapt.api.golem.overseer.Overseer;
import art.arcane.adapt.api.golem.overseer.OverseerSignal;
import art.arcane.volmlib.util.inventorygui.Element;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.plugin.PluginManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Answers.RETURNS_SELF;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GolemOverseerExecutionTest extends AdaptTestBase {
  private static final UUID OWNER = UUID.fromString("00000000-0000-0000-0000-000000000901");

  private MockedStatic<GolemancyRuntime> runtime;
  private GolemRegistry registry;
  private GolemOverseer adaptation;

  @BeforeEach
  void construct() {
    registry = mock(GolemRegistry.class);
    runtime = mockStatic(GolemancyRuntime.class);
    runtime.when(GolemancyRuntime::registry).thenReturn(Optional.of(registry));
    adaptation = new GolemOverseer();
  }

  @AfterEach
  void close() {
    adaptation.unregister();
    runtime.close();
  }

  @Test
  void theDefaultsFollowTheBrief() {
    GolemOverseer.Config config = new GolemOverseer.Config();

    assertThat(config.radiusByLevel).containsExactly(16, 24, 32, 48, 64);
    assertThat(config.signalCooldownMillis).isEqualTo(1_000L);
    assertThat(config.sicLevel).isEqualTo(2);
    assertThat(config.sicRange).isEqualTo(24D);
    assertThat(config.sicGlowSeconds).isEqualTo(5);
    assertThat(config.sicHoldSeconds).isEqualTo(20);
    assertThat(config.channelFilterLevel).isEqualTo(3);
    assertThat(config.rallyLevel).isEqualTo(4);
    assertThat(config.rallyCooldownSeconds).isEqualTo(60);
    assertThat(config.formationLevel).isEqualTo(5);
    assertThat(config.formationRingRadius).isEqualTo(2.5D);
    assertThat(config.formationArmorPerGolem).isEqualTo(1D);
    assertThat(config.formationArmorMax).isEqualTo(6D);
    assertThat(config.formationArmorRange).isEqualTo(8D);
    assertThat(config.requires).containsExactly("golem-animus-binding:1");
  }

  @Test
  void configurationLoadsAndEveryLevelHasAPositiveCostAndProgress() {
    assertThat(adaptation.reloadConfigFromDisk(false)).isTrue();
    assertThat(adaptation.getName()).isEqualTo("golem-overseer");
    assertThat(adaptation.getMaxLevel()).isEqualTo(5);
    double previous = 0D;
    for (int level = 1; level <= adaptation.getMaxLevel(); level++) {
      assertThat(adaptation.getCostFor(level)).as("cost at level %s", level).isPositive();
      assertThat(adaptation.getLevelPercent(level)).isBetween(previous, 1D);
      previous = adaptation.getLevelPercent(level);
    }
    assertThat(adaptation.isRuntimeRegistered()).isFalse();
  }

  @Test
  void everyLevelShowsItsSignalRadiusAndTheUnlocksItHasReached() {
    assertThat(lore(1)).containsExactly("+ 16 Block Signal Radius");
    assertThat(lore(2)).containsExactly("+ 24 Block Signal Radius", "+ Sic: Your Fighting Golems Attack What You Look At");
    assertThat(lore(3)).containsExactly("+ 32 Block Signal Radius", "+ Sic: Your Fighting Golems Attack What You Look At",
        "+ Crook Channel Filter, Set in the Golem Roster");
    assertThat(lore(4)).containsExactly("+ 48 Block Signal Radius", "+ Sic: Your Fighting Golems Attack What You Look At",
        "+ Crook Channel Filter, Set in the Golem Roster", "+ Rally: Follow Calls Distant Golems to You");
    assertThat(lore(5)).containsExactly("+ 64 Block Signal Radius", "+ Sic: Your Fighting Golems Attack What You Look At",
        "+ Crook Channel Filter, Set in the Golem Roster", "+ Rally: Follow Calls Distant Golems to You",
        "+ 6 Max Armor from Golems Following You in Formation");
  }

  @Test
  void aDisabledReloadTurnsTheAdaptationOff() {
    assertThat(adaptation.reloadConfigSnapshot("enabled = false\nmaxLevel = 1\n", dataFolder.toPath().resolve("disabled.toml").toFile(), false)).isTrue();
    try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
      bukkit.when(Bukkit::getPluginManager).thenReturn(mock(PluginManager.class));
      assertThat(adaptation.isEnabled()).isFalse();
    }
    assertThat(adaptation.getMaxLevel()).isEqualTo(1);
  }

  @Test
  void loadingClampsRangesAndAmounts() {
    assertThat(adaptation.reloadConfigSnapshot("sicRange = 500.0\nformationRingRadius = 0.0\nformationArmorMax = 90.0\nformationArmorPerGolem = -1.0\n"
        + "formationArmorRange = -4.0\nsicHoldSeconds = 0\nsicGlowSeconds = -3\nrallyCooldownSeconds = -1\nsignalCooldownMillis = -5\n",
        dataFolder.toPath().resolve("clamp.toml").toFile(), false)).isTrue();

    GolemOverseer.Config config = adaptation.getConfig();
    assertThat(config.sicRange).isEqualTo(64D);
    assertThat(config.formationRingRadius).isEqualTo(1D);
    assertThat(config.formationArmorMax).isEqualTo(30D);
    assertThat(config.formationArmorPerGolem).isZero();
    assertThat(config.formationArmorRange).isZero();
    assertThat(config.sicHoldSeconds).isEqualTo(1);
    assertThat(config.sicGlowSeconds).isZero();
    assertThat(config.rallyCooldownSeconds).isZero();
    assertThat(config.signalCooldownMillis).isZero();
  }

  @Test
  void activatingTheRuntimeInstallsTheCrookCommanderAndTheGolemReceiverUntilUnregistered() {
    assertThat(Overseer.commander()).isEmpty();

    adaptation.onRuntimeActivated();

    assertThat(Overseer.commander()).isPresent();
    Overseer.dispatch(signal());
    verify(registry).loadedOf(OWNER);

    adaptation.unregister();

    assertThat(Overseer.commander()).isEmpty();
    GolemRegistry later = mock(GolemRegistry.class);
    runtime.when(GolemancyRuntime::registry).thenReturn(Optional.of(later));
    Overseer.dispatch(signal());
    verify(later, never()).loadedOf(OWNER);
  }

  private OverseerSignal signal() {
    return new OverseerSignal(OWNER, GolemSignal.FOLLOW, new Location(mock(World.class), 0D, 64D, 0D), 16D, null, null, 0L, null, entity -> {
    });
  }

  private List<String> lore(int level) {
    List<String> lines = new ArrayList<>();
    Element stats = mock(Element.class, RETURNS_SELF);
    when(stats.addLore(anyString())).thenAnswer(invocation -> {
      lines.add(invocation.<String>getArgument(0).replaceAll("§[0-9a-fk-orx]", ""));
      return stats;
    });
    adaptation.addStats(level, stats);
    return lines;
  }
}
