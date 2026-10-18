package art.arcane.adapt.content.adaptation.golemancy;

import art.arcane.adapt.api.fx.FxTimeline;
import art.arcane.adapt.api.golem.GolemBody;
import art.arcane.adapt.api.golem.GolemCoreType;
import art.arcane.adapt.api.golem.GolemRitualMarker;
import art.arcane.adapt.api.golem.item.GolemItems;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.entity.Snowman;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BindingRitualTest {
  private static final long STARTED_AT = 10_000L;

  private final World world = mock(World.class);
  private final World otherWorld = mock(World.class);
  private final Player owner = mock(Player.class);
  private final PlayerInventory inventory = mock(PlayerInventory.class);
  private final Snowman golem = mock(Snowman.class);
  private final PersistentDataContainer golemData = mock(PersistentDataContainer.class);
  private final ItemStack blankCore = mock(ItemStack.class);
  private final ItemStack guardianCore = mock(ItemStack.class);
  private MockedStatic<GolemItems> items;

  @BeforeEach
  void setUp() {
    when(owner.getInventory()).thenReturn(inventory);
    when(inventory.getHeldItemSlot()).thenReturn(3);
    when(inventory.getItemInMainHand()).thenReturn(blankCore);
    when(golem.isAware()).thenReturn(true);
    when(golem.getPersistentDataContainer()).thenReturn(golemData);
    when(golem.getLocation()).thenReturn(new Location(world, 0D, 64D, 0D));
    when(owner.getLocation()).thenReturn(new Location(world, 0D, 64D, 4D));
    items = mockStatic(GolemItems.class);
    items.when(() -> GolemItems.coreOf(blankCore)).thenReturn(Optional.of(GolemCoreType.BLANK));
    items.when(() -> GolemItems.coreOf(guardianCore)).thenReturn(Optional.of(GolemCoreType.GUARDIAN));
  }

  @AfterEach
  void tearDown() {
    items.close();
  }

  @Test
  void progressStepsFromZeroToOneAcrossTheRitual() {
    BindingRitual ritual = ritual(4);

    assertThat(ritual.progress()).isZero();
    ritual.advance();
    ritual.advance();
    assertThat(ritual.progress()).isEqualTo(0.5D);
    assertThat(ritual.complete()).isFalse();
    ritual.advance();
    ritual.advance();
    ritual.advance();
    assertThat(ritual.progress()).isEqualTo(1D);
    assertThat(ritual.complete()).isTrue();
  }

  @Test
  void finishingRunsOnceAndStopsTheEffects() {
    BindingRitual ritual = ritual(4);
    FxTimeline timeline = mock(FxTimeline.class);
    ritual.attach(timeline);

    assertThat(ritual.finish()).isTrue();
    assertThat(ritual.finish()).isFalse();
    assertThat(ritual.isFinished()).isTrue();
    verify(timeline, times(1)).cancel();
  }

  @Test
  void aRitualTurnsStaleFiveSecondsAfterItsLastPulseHoweverLongItRuns() {
    BindingRitual ritual = ritual(40);

    assertThat(ritual.stale(STARTED_AT + 5_000L)).isFalse();
    assertThat(ritual.stale(STARTED_AT + 5_001L)).isTrue();

    ritual.pulsed(STARTED_AT + 60_000L);

    assertThat(ritual.stale(STARTED_AT + 65_000L)).isFalse();
    assertThat(ritual.stale(STARTED_AT + 65_001L)).isTrue();
  }

  @Test
  void theGolemsCurrentAwarenessIsRememberedWithoutAMarker() {
    assertThat(ritual(4).wasAware()).isTrue();
  }

  @Test
  void anExistingRitualMarkerWinsOverTheFrozenGolemsAwareness() {
    when(golem.isAware()).thenReturn(false);
    when(golemData.get(GolemRitualMarker.KEY, PersistentDataType.BYTE)).thenReturn((byte) 1);

    assertThat(ritual(4).wasAware()).isTrue();
  }

  @Test
  void theCoreStaysHeldUntilTheSlotOrTheCoreTypeChanges() {
    BindingRitual ritual = ritual(4);
    assertThat(ritual.stillHeld()).isTrue();

    when(inventory.getHeldItemSlot()).thenReturn(4);
    assertThat(ritual.stillHeld()).isFalse();

    when(inventory.getHeldItemSlot()).thenReturn(3);
    when(inventory.getItemInMainHand()).thenReturn(guardianCore);
    assertThat(ritual.stillHeld()).isFalse();

    when(inventory.getItemInMainHand()).thenReturn(mock(ItemStack.class));
    assertThat(ritual.stillHeld()).isFalse();
  }

  @Test
  void theOwnerMustStayWithinFourBlocksInTheSameWorld() {
    BindingRitual ritual = ritual(4);
    assertThat(ritual.withinReach(4D)).isTrue();

    when(owner.getLocation()).thenReturn(new Location(world, 0D, 64D, 4.01D));
    assertThat(ritual.withinReach(4D)).isFalse();

    when(owner.getLocation()).thenReturn(new Location(otherWorld, 0D, 64D, 1D));
    assertThat(ritual.withinReach(4D)).isFalse();
  }

  private BindingRitual ritual(int ticks) {
    return new BindingRitual(new BindingTarget(owner, golem, GolemBody.FROST, GolemCoreType.BLANK), ticks, STARTED_AT);
  }
}
