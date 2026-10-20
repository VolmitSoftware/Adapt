package art.arcane.adapt.content.adaptation.golemancy;

import art.arcane.adapt.AdaptTestBase;
import art.arcane.adapt.api.golem.ChannelMask;
import art.arcane.adapt.api.golem.GolemBody;
import art.arcane.adapt.api.golem.GolemBrain;
import art.arcane.adapt.api.golem.GolemCoreType;
import art.arcane.adapt.api.golem.GolemHandle;
import art.arcane.adapt.api.golem.GolemMode;
import art.arcane.adapt.api.golem.GolemRegistry;
import art.arcane.adapt.api.golem.GolemState;
import art.arcane.adapt.api.golem.GolemTeleport;
import art.arcane.adapt.api.golem.GolemancyRuntime;
import art.arcane.adapt.api.golem.overseer.GolemSignal;
import art.arcane.adapt.api.golem.overseer.OverseerSignal;
import art.arcane.adapt.api.golem.reveal.RevealService;
import art.arcane.adapt.util.common.scheduling.J;
import org.bukkit.DyeColor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.IronGolem;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Zombie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class OverseerGolemsTest extends AdaptTestBase {
  private static final UUID OWNER = UUID.fromString("00000000-0000-0000-0000-000000000801");

  private final World world = mock(World.class);
  private final World elsewhere = mock(World.class);
  private final List<GolemHandle> loaded = new ArrayList<>();
  private final List<Entity> pulsed = new ArrayList<>();
  private final List<Entity> scheduled = new ArrayList<>();
  private final Map<Mob, GolemBrain> brains = new HashMap<>();
  private final RevealService reveal = mock(RevealService.class);
  private final List<Note> notes = new ArrayList<>();
  private final List<Queued> queued = new ArrayList<>();
  private final OverseerGolems golems = new OverseerGolems(() -> 10D, (owner, count) -> notes.add(new Note(owner, count)));
  private boolean deferring;
  private boolean refusing;
  private MockedStatic<GolemancyRuntime> runtime;
  private MockedStatic<J> scheduling;
  private MockedStatic<GolemTeleport> teleports;

  @BeforeEach
  void setUp() {
    GolemRegistry registry = mock(GolemRegistry.class);
    lenient().when(registry.loadedOf(OWNER)).thenReturn(loaded);
    runtime = mockStatic(GolemancyRuntime.class);
    runtime.when(GolemancyRuntime::registry).thenReturn(Optional.of(registry));
    runtime.when(GolemancyRuntime::reveal).thenReturn(Optional.of(reveal));
    runtime.when(() -> GolemancyRuntime.brain(any(Entity.class))).thenAnswer(call -> Optional.ofNullable(brains.get(call.<Entity>getArgument(0))));
    scheduling = mockStatic(J.class);
    scheduling.when(() -> J.runEntity(any(Entity.class), any(Runnable.class))).thenAnswer(call -> {
      scheduled.add(call.getArgument(0));
      call.<Runnable>getArgument(1).run();
      return true;
    });
    scheduling.when(() -> J.runEntity(any(Entity.class), any(Runnable.class), any(Runnable.class))).thenAnswer(call -> {
      if (refusing) {
        return false;
      }
      scheduled.add(call.getArgument(0));
      if (deferring) {
        queued.add(new Queued(call.getArgument(1), call.getArgument(2)));
      } else {
        call.<Runnable>getArgument(1).run();
      }
      return true;
    });
    scheduling.when(() -> J.isOwnedByCurrentRegion(any(Entity.class))).thenReturn(true);
    teleports = mockStatic(GolemTeleport.class);
    teleports.when(() -> GolemTeleport.teleport(any(GolemHandle.class), any(Location.class))).thenReturn(CompletableFuture.completedFuture(true));
  }

  @AfterEach
  void tearDown() {
    teleports.close();
    scheduling.close();
    runtime.close();
  }

  @Test
  void followSetsTheModeOfGolemsInRangeAndPulsesThem() {
    GolemHandle near = golem(GolemCoreType.GATHERER, at(10D), ChannelMask.NONE);

    golems.receive(signal(GolemSignal.FOLLOW, null, null));

    verify(near).setMode(GolemMode.FOLLOW);
    assertThat(pulsed).containsExactly(near.entity());
  }

  @Test
  void aMovementSignalEndsAnActiveSicAndRefreshesTheOwnersLabel() {
    GolemHandle guardian = golem(GolemCoreType.GUARDIAN, at(6D), ChannelMask.NONE);

    golems.receive(signal(GolemSignal.HOLD, null, null));

    verify(brains.get(guardian.entity())).clearCommand();
    verify(reveal).refresh(guardian);
  }

  @Test
  void frostGolemsIgnoreASicTargetBeyondTheirSentryRange() {
    LivingEntity near = mock(Zombie.class);
    lenient().when(near.getWorld()).thenReturn(world);
    lenient().when(near.getLocation()).thenAnswer(call -> new Location(world, 14D, 64D, 0D));
    LivingEntity far = mock(Zombie.class);
    lenient().when(far.getWorld()).thenReturn(world);
    lenient().when(far.getLocation()).thenAnswer(call -> new Location(world, 16D, 64D, 0D));
    GolemHandle frost = golem(GolemCoreType.GUARDIAN, at(5D), ChannelMask.NONE);
    lenient().when(frost.body()).thenReturn(GolemBody.FROST);

    golems.receive(signal(GolemSignal.SIC, far, null));

    verify(brains.get(frost.entity()), never()).command(any(Entity.class), anyLong());
    assertThat(pulsed).isEmpty();

    golems.receive(signal(GolemSignal.SIC, near, null));

    verify(brains.get(frost.entity())).command(near, 25_000L);
    assertThat(pulsed).containsExactly(frost.entity());
  }

  @Test
  void holdKeepsGolemsWhereTheyStand() {
    GolemHandle near = golem(GolemCoreType.BLANK, at(4D), ChannelMask.NONE);

    golems.receive(signal(GolemSignal.HOLD, null, null));

    verify(near).setMode(GolemMode.STAY);
  }

  @Test
  void golemsThatCannotTakeTheModeAreSkippedWithoutAPulse() {
    GolemHandle companion = golem(GolemCoreType.BLANK, at(4D), ChannelMask.NONE);

    golems.receive(signal(GolemSignal.WORK, null, null));

    verify(companion, never()).setMode(any(GolemMode.class));
    assertThat(pulsed).isEmpty();
  }

  @Test
  void golemsBeyondTheRadiusOrInAnotherWorldIgnoreTheSignal() {
    GolemHandle far = golem(GolemCoreType.BLANK, at(17D), ChannelMask.NONE);
    GolemHandle away = golem(GolemCoreType.BLANK, new Location(elsewhere, 1D, 64D, 0D), ChannelMask.NONE);

    golems.receive(signal(GolemSignal.FOLLOW, null, null));

    verify(far, never()).setMode(any(GolemMode.class));
    verify(away, never()).setMode(any(GolemMode.class));
    teleports.verify(() -> GolemTeleport.teleport(any(GolemHandle.class), any(Location.class)), never());
    assertThat(pulsed).isEmpty();
  }

  @Test
  void theCrookChannelLimitsWhichGolemsAnswer() {
    GolemHandle red = golem(GolemCoreType.BLANK, at(4D), ChannelMask.of(DyeColor.RED));
    GolemHandle blue = golem(GolemCoreType.BLANK, at(4D), ChannelMask.of(DyeColor.BLUE));

    golems.receive(new OverseerSignal(OWNER, GolemSignal.HOLD, origin(), 16D, DyeColor.RED, null, 0L, null, pulsed::add));

    verify(red).setMode(GolemMode.STAY);
    verify(blue, never()).setMode(any(GolemMode.class));
  }

  @Test
  void sleepingGolemsAreNeverScheduled() {
    GolemHandle asleep = golem(GolemCoreType.BLANK, at(4D), ChannelMask.NONE);
    lenient().when(asleep.isDormant()).thenReturn(true);

    golems.receive(signal(GolemSignal.FOLLOW, null, null));

    assertThat(scheduled).isEmpty();
    verify(asleep, never()).setMode(any(GolemMode.class));
  }

  @Test
  void golemsRetiredBeforeTheirTurnAreSkipped() {
    GolemHandle retired = golem(GolemCoreType.BLANK, at(4D), ChannelMask.NONE);
    lenient().when(retired.isRetired()).thenReturn(true);

    golems.receive(signal(GolemSignal.FOLLOW, null, null));

    verify(retired, never()).setMode(any(GolemMode.class));
  }

  @Test
  void aGrantedRallyTeleportsFarGolemsToTheOwnerInFollowMode() {
    GolemHandle far = golem(GolemCoreType.GATHERER, at(200D), ChannelMask.NONE);

    golems.receive(signal(GolemSignal.FOLLOW, null, () -> true));

    verify(far).setMode(GolemMode.FOLLOW);
    teleports.verify(() -> GolemTeleport.teleport(eq(far), eq(origin())));
    assertThat(pulsed).containsExactly(far.entity());
  }

  @Test
  void aRallyStillCoolingDownLeavesFarGolemsWhereTheyAre() {
    GolemHandle near = golem(GolemCoreType.GATHERER, at(8D), ChannelMask.NONE);
    GolemHandle far = golem(GolemCoreType.GATHERER, at(200D), ChannelMask.NONE);

    golems.receive(signal(GolemSignal.FOLLOW, null, () -> false));

    verify(near).setMode(GolemMode.FOLLOW);
    verify(far, never()).setMode(any(GolemMode.class));
    teleports.verify(() -> GolemTeleport.teleport(any(GolemHandle.class), any(Location.class)), never());
  }

  @Test
  void sicCommandsFightingGolemsInRangeAndSkipsTheRest() {
    LivingEntity target = mock(Zombie.class);
    GolemHandle guardian = golem(GolemCoreType.GUARDIAN, at(6D), ChannelMask.NONE);
    GolemHandle breaker = golem(GolemCoreType.BREAKER, at(6D), ChannelMask.NONE);
    GolemHandle distant = golem(GolemCoreType.GUARDIAN, at(30D), ChannelMask.NONE);

    golems.receive(signal(GolemSignal.SIC, target, null));

    verify(brains.get(guardian.entity())).command(target, 25_000L);
    verify(brains.get(breaker.entity()), never()).command(any(Entity.class), anyLong());
    verify(brains.get(distant.entity()), never()).command(any(Entity.class), anyLong());
    verify(guardian, never()).setMode(any(GolemMode.class));
    assertThat(pulsed).containsExactly(guardian.entity());
  }

  @Test
  void sicIsIgnoredByGolemsWithCombatOffAndTheOwnerGetsOneNoteNamingTheCount() {
    LivingEntity target = mock(Zombie.class);
    GolemHandle quietOne = golem(GolemCoreType.BLANK, at(6D), ChannelMask.NONE);
    GolemHandle quietTwo = golem(GolemCoreType.BLANK, at(7D), ChannelMask.NONE);
    GolemHandle fighter = golem(GolemCoreType.BLANK, at(8D), ChannelMask.NONE);
    combatOff(quietOne);
    combatOff(quietTwo);

    golems.receive(signal(GolemSignal.SIC, target, null));

    verify(brains.get(quietOne.entity()), never()).command(any(Entity.class), anyLong());
    verify(brains.get(quietTwo.entity()), never()).command(any(Entity.class), anyLong());
    verify(brains.get(fighter.entity())).command(target, 25_000L);
    assertThat(pulsed).containsExactly(fighter.entity());
    assertThat(notes).containsExactly(new Note(OWNER, 2));
  }

  @Test
  void aSingleIgnoringGolemStillGetsOneNote() {
    GolemHandle quiet = golem(GolemCoreType.BLANK, at(6D), ChannelMask.NONE);
    combatOff(quiet);

    golems.receive(signal(GolemSignal.SIC, mock(Zombie.class), null));

    assertThat(notes).containsExactly(new Note(OWNER, 1));
    assertThat(pulsed).isEmpty();
  }

  @Test
  void sicLeavesNoNoteWhenEveryGolemObeysIt() {
    golem(GolemCoreType.BLANK, at(6D), ChannelMask.NONE);
    golem(GolemCoreType.GUARDIAN, at(7D), ChannelMask.NONE);

    golems.receive(signal(GolemSignal.SIC, mock(Zombie.class), null));

    assertThat(notes).isEmpty();
    assertThat(pulsed).hasSize(2);
  }

  @Test
  void golemsOutOfRangeOrWithoutAFightingCoreAreNotCountedAsIgnoringSic() {
    GolemHandle distant = golem(GolemCoreType.BLANK, at(30D), ChannelMask.NONE);
    GolemHandle breaker = golem(GolemCoreType.BREAKER, at(6D), ChannelMask.NONE);
    combatOff(distant);
    combatOff(breaker);

    golems.receive(signal(GolemSignal.SIC, mock(Zombie.class), null));

    assertThat(notes).isEmpty();
  }

  @Test
  void onlyGolemsOnTheCrookChannelCountAsIgnoringSic() {
    GolemHandle red = golem(GolemCoreType.BLANK, at(6D), ChannelMask.of(DyeColor.RED));
    GolemHandle blue = golem(GolemCoreType.BLANK, at(6D), ChannelMask.of(DyeColor.BLUE));
    combatOff(red);
    combatOff(blue);

    golems.receive(new OverseerSignal(OWNER, GolemSignal.SIC, origin(), 16D, DyeColor.RED, mock(Zombie.class), 25_000L, null, pulsed::add));

    assertThat(notes).containsExactly(new Note(OWNER, 1));
  }

  @Test
  void aGuardianStillAnswersSicWhenItsStoredCombatIsOff() {
    LivingEntity target = mock(Zombie.class);
    GolemHandle guardian = golem(GolemCoreType.GUARDIAN, at(6D), ChannelMask.NONE);
    combatOff(guardian);

    golems.receive(signal(GolemSignal.SIC, target, null));

    verify(brains.get(guardian.entity())).command(target, 25_000L);
    assertThat(notes).isEmpty();
  }

  @Test
  void movementSignalsStillReachGolemsWithCombatOffAndNoteNothing() {
    GolemHandle quiet = golem(GolemCoreType.BLANK, at(4D), ChannelMask.NONE);
    combatOff(quiet);

    golems.receive(signal(GolemSignal.HOLD, null, null));

    verify(quiet).setMode(GolemMode.STAY);
    assertThat(notes).isEmpty();
  }

  @Test
  void theNoteWaitsForEveryGolemOnItsOwnSchedulerToAnswer() {
    GolemHandle quietOne = golem(GolemCoreType.BLANK, at(6D), ChannelMask.NONE);
    GolemHandle quietTwo = golem(GolemCoreType.BLANK, at(7D), ChannelMask.NONE);
    golem(GolemCoreType.BLANK, at(8D), ChannelMask.NONE);
    combatOff(quietOne);
    combatOff(quietTwo);
    deferring = true;

    golems.receive(signal(GolemSignal.SIC, mock(Zombie.class), null));

    assertThat(queued).hasSize(3);
    assertThat(notes).isEmpty();
    queued.get(0).task().run();
    queued.get(2).task().run();
    assertThat(notes).isEmpty();
    queued.get(1).task().run();
    assertThat(notes).containsExactly(new Note(OWNER, 2));
  }

  @Test
  void aGolemRetiredBeforeItsTurnNeverBlocksTheNote() {
    GolemHandle quiet = golem(GolemCoreType.BLANK, at(6D), ChannelMask.NONE);
    golem(GolemCoreType.BLANK, at(7D), ChannelMask.NONE);
    combatOff(quiet);
    deferring = true;

    golems.receive(signal(GolemSignal.SIC, mock(Zombie.class), null));
    queued.get(1).retired().run();
    assertThat(notes).isEmpty();
    queued.get(0).task().run();

    assertThat(notes).containsExactly(new Note(OWNER, 1));
  }

  @Test
  void aGolemThatCannotBeScheduledNeverBlocksTheNote() {
    GolemHandle quiet = golem(GolemCoreType.BLANK, at(6D), ChannelMask.NONE);
    combatOff(quiet);
    GolemHandle refused = golem(GolemCoreType.BLANK, at(7D), ChannelMask.NONE);
    deferring = true;
    scheduling.when(() -> J.runEntity(eq(refused.entity()), any(Runnable.class), any(Runnable.class))).thenReturn(false);

    golems.receive(signal(GolemSignal.SIC, mock(Zombie.class), null));
    assertThat(notes).isEmpty();
    queued.getFirst().task().run();

    assertThat(notes).containsExactly(new Note(OWNER, 1));
  }

  @Test
  void aFailingAnswerStillLetsTheNoteGo() {
    GolemHandle quiet = golem(GolemCoreType.BLANK, at(6D), ChannelMask.NONE);
    combatOff(quiet);
    GolemHandle failing = golem(GolemCoreType.BLANK, at(7D), ChannelMask.NONE);
    GolemBrain failingBrain = brains.get(failing.entity());
    doThrow(new IllegalStateException("answer failed")).when(failingBrain).command(any(Entity.class), anyLong());
    deferring = true;

    golems.receive(signal(GolemSignal.SIC, mock(Zombie.class), null));
    queued.get(0).task().run();
    assertThat(notes).isEmpty();

    assertThatThrownBy(() -> queued.get(1).task().run()).isInstanceOf(IllegalStateException.class).hasMessage("answer failed");
    assertThat(notes).containsExactly(new Note(OWNER, 1));
  }

  private static void combatOff(GolemHandle handle) {
    lenient().when(handle.state().combatOff()).thenReturn(true);
  }

  private OverseerSignal signal(GolemSignal kind, LivingEntity target, OverseerSignal.Rally rally) {
    return new OverseerSignal(OWNER, kind, origin(), 16D, null, target, 25_000L, rally, pulsed::add);
  }

  private Location origin() {
    return new Location(world, 0D, 64D, 0D);
  }

  private Location at(double x) {
    return new Location(world, x, 64D, 0D);
  }

  private GolemHandle golem(GolemCoreType core, Location location, int channels) {
    Mob body = mock(IronGolem.class);
    lenient().when(body.isValid()).thenReturn(true);
    lenient().when(body.getLocation()).thenAnswer(call -> location.clone());
    lenient().when(body.getWorld()).thenReturn(location.getWorld());
    GolemState state = mock(GolemState.class);
    lenient().when(state.cores()).thenReturn(List.of(core));
    lenient().when(state.channels()).thenReturn(channels);
    GolemHandle handle = mock(GolemHandle.class);
    lenient().when(handle.id()).thenReturn(UUID.randomUUID());
    lenient().when(handle.owner()).thenReturn(OWNER);
    lenient().when(handle.entity()).thenReturn(body);
    lenient().when(handle.body()).thenReturn(GolemBody.IRON);
    lenient().when(handle.core()).thenReturn(core);
    lenient().when(handle.state()).thenReturn(state);
    brains.put(body, mock(GolemBrain.class));
    loaded.add(handle);
    return handle;
  }

  private record Note(UUID owner, int count) {
  }

  private record Queued(Runnable task, Runnable retired) {
  }
}
