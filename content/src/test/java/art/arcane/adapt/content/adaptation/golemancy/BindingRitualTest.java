package art.arcane.adapt.content.adaptation.golemancy;

import art.arcane.adapt.api.fx.FxTimeline;
import art.arcane.adapt.content.golem.GolemBody;
import art.arcane.adapt.content.golem.GolemCoreType;
import art.arcane.adapt.content.golem.GolemRitualMarker;
import art.arcane.adapt.content.golem.item.GolemItems;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.world.WorldRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.entity.MobRef;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.entity.PlayerInventoryRef;
import art.arcane.adapt.platform.data.DataHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BindingRitualTest {
  private static final long STARTED_AT = 10_000L;

  private final WorldRef world = mock(WorldRef.class);
  private final WorldRef otherWorld = mock(WorldRef.class);
  private final PlayerRef owner = mock(PlayerRef.class);
  private final PlayerInventoryRef inventory = mock(PlayerInventoryRef.class);
  private final MobRef golem = mock(MobRef.class);
  private final DataHolder golemData = mock(DataHolder.class);
  private final ItemRef blankCore = mock(ItemRef.class);
  private final ItemRef guardianCore = mock(ItemRef.class);
  private final ItemRef escrowed = mock(ItemRef.class);
  private MockedStatic<GolemItems> items;

  @BeforeEach
  void setUp() {
    lenient().when(owner.inventory()).thenReturn(inventory);
    lenient().when(inventory.mainHand()).thenReturn(blankCore);
    when(golem.isAware()).thenReturn(true);
    when(golem.data()).thenReturn(golemData);
    when(golem.location()).thenReturn(new Loc(world, 0D, 64D, 0D));
    when(owner.location()).thenReturn(new Loc(world, 0D, 64D, 4D));
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
    when(golemData.get(GolemRitualMarker.KEY)).thenReturn((byte) 1);

    assertThat(ritual(4).wasAware()).isTrue();
  }

  @Test
  void theEscrowedCoreIsHandedOutExactlyOnce() {
    BindingRitual ritual = ritual(4);

    assertThat(ritual.takeCore()).isSameAs(escrowed);
    assertThat(ritual.takeCore()).isNull();
  }

  @Test
  void theRitualNeverLooksAtTheOwnersHandOnceTheCoreIsTaken() {
    ritual(4);

    verify(owner, never()).inventory();
  }

  @Test
  void theAnchorIsACopyOfWhereTheGolemStood() {
    BindingRitual ritual = ritual(4);
    Loc anchor = ritual.anchor();
    anchor.add(5D, 0D, 0D);

    assertThat(ritual.anchor().x()).isZero();
  }

  @Test
  void theOwnerMustStayWithinFourBlocksInTheSameWorld() {
    BindingRitual ritual = ritual(4);
    assertThat(ritual.withinReach(4D)).isTrue();

    when(owner.location()).thenReturn(new Loc(world, 0D, 64D, 4.01D));
    assertThat(ritual.withinReach(4D)).isFalse();

    when(owner.location()).thenReturn(new Loc(otherWorld, 0D, 64D, 1D));
    assertThat(ritual.withinReach(4D)).isFalse();
  }

  @Test
  void theGolemIsHeldOnceItDriftsMoreThanATenthOfABlockFromWhereTheRitualStarted() {
    BindingRitual ritual = ritual(4);

    assertThat(ritual.drifted(new Loc(world, 0D, 64D, 0D))).isFalse();
    assertThat(ritual.drifted(new Loc(world, 0.07D, 64D, 0.07D))).isFalse();
    assertThat(ritual.drifted(new Loc(world, 0.11D, 64D, 0D))).isTrue();
    assertThat(ritual.drifted(new Loc(world, 0D, 64.11D, 0D))).isTrue();
    assertThat(ritual.drifted(new Loc(world, 2.3D, 64D, 2.3D))).isTrue();
    assertThat(ritual.drifted(new Loc(otherWorld, 2.3D, 64D, 2.3D))).isFalse();
  }

  @Test
  void theHoldReturnsTheGolemToItsStartingSpotFacingItsCurrentDirection() {
    when(golem.location()).thenReturn(new Loc(world, 1.5D, 64D, -2.5D, 10F, 0F));
    BindingRitual ritual = ritual(4);

    Loc held = ritual.anchorFacing(new Loc(world, 3.2D, 64D, -1.1D, 135F, 20F));

    assertThat(held.world()).isSameAs(world);
    assertThat(held.x()).isEqualTo(1.5D);
    assertThat(held.y()).isEqualTo(64D);
    assertThat(held.z()).isEqualTo(-2.5D);
    assertThat(held.yaw()).isEqualTo(135F);
    assertThat(held.pitch()).isEqualTo(20F);
  }

  private BindingRitual ritual(int ticks) {
    return new BindingRitual(new BindingTarget(owner, golem, GolemBody.FROST, GolemCoreType.BLANK), ticks, STARTED_AT, escrowed);
  }
}
