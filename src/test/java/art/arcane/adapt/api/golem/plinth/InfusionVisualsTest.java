package art.arcane.adapt.api.golem.plinth;

import art.arcane.adapt.api.fx.FxEmitter;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.fx.FxTimeline;
import art.arcane.adapt.api.golem.GolemRuneType;
import io.papermc.paper.registry.RegistryAccess;
import net.kyori.adventure.key.Key;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Registry;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.invocation.Invocation;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.RETURNS_SELF;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.mockingDetails;

class InfusionVisualsTest {
  private static final UUID WORLD = UUID.fromString("00000000-0000-0000-0000-0000000000c1");
  private static final PlinthSpot CENTER = new PlinthSpot(WORLD, 8, 64, 8);
  private static final int[] DURATIONS = {100, 160, 240, 300};
  private static final String SWIFTNESS = "golemancy:rune/swiftness/1";
  private static final String HEART = "golemancy:masterwork_heart";
  private static final double EPSILON = 1.0E-9D;

  private final PlinthHost host = mock(PlinthHost.class);
  private final List<FxEmitter> emitters = new ArrayList<>();
  private final List<FxTimeline> timelines = new ArrayList<>();
  private final List<FxPriority> priorities = new ArrayList<>();
  private final InfusionVisuals visuals = new InfusionVisuals(() -> host);
  private Location anchor;

  @BeforeAll
  static void initializeSounds() {
    RegistryAccess access = mock(RegistryAccess.class, RETURNS_DEEP_STUBS);
    try (MockedStatic<RegistryAccess> registryAccess = mockStatic(RegistryAccess.class)) {
      registryAccess.when(RegistryAccess::registryAccess).thenReturn(access);
      Registry<Sound> sounds = Registry.SOUNDS;
      doAnswer(call -> mock(Sound.class)).when(sounds).getOrThrow(any(Key.class));
      assertThat(Sound.BLOCK_AMETHYST_BLOCK_CHIME).isNotNull();
    }
  }

  @BeforeEach
  void setUp() {
    World world = mock(World.class);
    anchor = CENTER.center(world);
    lenient().when(host.fx(any(Location.class), any(FxPriority.class))).thenAnswer(call -> {
      FxEmitter emitter = mock(FxEmitter.class, RETURNS_SELF);
      lenient().when(emitter.viewerCount()).thenReturn(1);
      emitters.add(emitter);
      priorities.add(call.getArgument(1));
      return emitter;
    });
    lenient().when(host.timeline(any(Location.class))).thenAnswer(call -> {
      FxTimeline timeline = mock(FxTimeline.class, RETURNS_SELF);
      timelines.add(timeline);
      return timeline;
    });
  }

  @Test
  void everySwirlFrameStaysUnderThePerStreamAndPerTickCaps() {
    for (int duration : DURATIONS) {
      InfusionRitual ritual = ritual(SWIFTNESS, duration);
      for (int tick = 1; tick <= duration; tick++) {
        ritual.advance();
        emitters.clear();
        visuals.swirl(anchor, ritual);
        Frame frame = frame(emitters.getLast());
        assertThat(emitters).as("tick %d of %d", tick, duration).hasSize(1);
        assertThat(frame.particles()).as("tick %d of %d", tick, duration).isLessThanOrEqualTo(InfusionVortex.FRAME_CAP);
        assertThat(frame.streamParticles()).as("tick %d of %d", tick, duration).isLessThanOrEqualTo(InfusionVortex.ARMS * InfusionVortex.STREAM_CAP);
        for (Orbit orbit : frame.orbits()) {
          assertThat(orbit.arms()).isEqualTo(InfusionVortex.ARMS);
        }
      }
    }
  }

  @Test
  void theStreamsAreFourArmsThatSpiralInwardAndMeetAtTheCenterItem() {
    InfusionRitual ritual = ritual(SWIFTNESS, 100);
    double previousRadius = Double.MAX_VALUE;
    double previousAngle = -1D;
    for (int tick = 1; tick <= 100; tick++) {
      ritual.advance();
      emitters.clear();
      visuals.swirl(anchor, ritual);
      List<Orbit> dust = frame(emitters.getLast()).orbits(Particle.DUST);
      Orbit head = dust.getFirst();
      double progress = InfusionVortex.progress(tick, 100);
      assertThat(head.radius()).as("tick %d", tick).isCloseTo(InfusionVortex.radius(progress), within(EPSILON)).isLessThanOrEqualTo(previousRadius);
      assertThat(head.height()).as("tick %d", tick).isCloseTo(InfusionVortex.height(progress), within(EPSILON));
      assertThat(head.angle()).as("tick %d", tick).isGreaterThan(previousAngle);
      for (int sample = 1; sample < dust.size(); sample++) {
        assertThat(dust.get(sample).angle()).as("trail sample %d at tick %d", sample, tick).isLessThan(dust.get(sample - 1).angle());
      }
      previousRadius = head.radius();
      previousAngle = head.angle();
    }
    assertThat(previousRadius).isCloseTo(0D, within(EPSILON));
    assertThat(previousAngle).isGreaterThan(Math.PI * 4D);
  }

  @Test
  void streamsStartAtThePedestalsAndRiseAboveThem() {
    InfusionRitual ritual = ritual(SWIFTNESS, 100);
    ritual.advance();
    visuals.swirl(anchor, ritual);
    Orbit head = frame(emitters.getLast()).orbits(Particle.DUST).getFirst();
    assertThat(head.radius()).isEqualTo(InfusionVortex.PEDESTAL_RADIUS);
    assertThat(head.angle()).isLessThan(0.1D);
    assertThat(head.height()).isGreaterThanOrEqualTo(InfusionVortex.ITEM_HEIGHT);
  }

  @Test
  void streamsWearTheTintOfTheRecipe() {
    InfusionRitual tide = ritual("golemancy:rune/tide/2", 160);
    Set<Particle> accents = new HashSet<>();
    for (int tick = 1; tick <= 160; tick++) {
      tide.advance();
      emitters.clear();
      visuals.swirl(anchor, tide);
      Frame frame = frame(emitters.getLast());
      List<Orbit> dust = frame.orbits(Particle.DUST);
      for (Orbit orbit : dust) {
        assertThat(((Particle.DustOptions) orbit.data()).getColor()).isEqualTo(InfusionTint.of(GolemRuneType.TIDE).color());
      }
      assertThat(dust.getFirst().data()).isSameAs(InfusionTint.of(GolemRuneType.TIDE).dust(0));
      for (Orbit orbit : dust.subList(1, dust.size())) {
        assertThat(orbit.data()).isSameAs(InfusionTint.of(GolemRuneType.TIDE).trail(0));
      }
      for (Orbit orbit : frame.orbits()) {
        if (orbit.particle() != Particle.DUST) {
          accents.add(orbit.particle());
        }
      }
    }
    assertThat(accents).containsExactly(Particle.BUBBLE_POP);

    InfusionRitual heart = ritual(HEART, 300);
    Set<Object> colors = new HashSet<>();
    for (int tick = 1; tick <= 120; tick++) {
      heart.advance();
      emitters.clear();
      visuals.swirl(anchor, heart);
      for (Orbit orbit : frame(emitters.getLast()).orbits(Particle.DUST)) {
        colors.add(((Particle.DustOptions) orbit.data()).getColor());
      }
    }
    assertThat(colors).hasSize(InfusionVortex.ARMS);
  }

  @Test
  void aSoundSwellRisesInPitchThroughTheSwirl() {
    InfusionRitual ritual = ritual(SWIFTNESS, 240);
    float previous = 0F;
    int swells = 0;
    for (int tick = 1; tick <= 240; tick++) {
      ritual.advance();
      emitters.clear();
      visuals.swirl(anchor, ritual);
      if (InfusionVortex.phase(ritual.progress()) != InfusionVortex.Phase.SWIRL) {
        continue;
      }
      for (Invocation call : calls(emitters.getLast(), "sound")) {
        float pitch = call.getArgument(2);
        assertThat(pitch).as("tick %d", tick).isGreaterThanOrEqualTo(previous);
        previous = pitch;
        swells++;
      }
    }
    assertThat(swells).isGreaterThan(10);
    assertThat(previous).isGreaterThan(1.5F);
  }

  @Test
  void theCollisionFlashesBurstsAndRingsOutAcrossThePlinth() {
    visuals.collide(anchor, InfusionTint.of(GolemRuneType.EMBER));

    Frame burst = frame(emitters.getFirst());
    assertThat(burst.particles()).isBetween(40, InfusionVortex.BURST_CAP);
    assertThat(calls(emitters.getFirst(), "particle")).anyMatch(call -> call.getArgument(0) == Particle.FLASH);
    assertThat(calls(emitters.getFirst(), "chord")).hasSize(1);
    assertThat(priorities).containsOnly(FxPriority.TRANSITION);

    FxTimeline ring = timelines.getFirst();
    int duration = argument(ring, "duration");
    assertThat(calls(ring, "start")).hasSize(1);
    double previous = 0D;
    for (int tick = 0; tick < duration; tick++) {
      emitters.clear();
      FxEmitter fx = host.fx(anchor, FxPriority.TRANSITION);
      timelineFrame(ring).render(fx, tick, duration <= 1 ? 1D : (double) tick / (duration - 1));
      List<Invocation> rings = calls(fx, "ring");
      assertThat(rings).as("frame %d", tick).hasSize(1);
      double radius = rings.getFirst().getArgument(1);
      int points = rings.getFirst().getArgument(2);
      assertThat(radius).as("frame %d", tick).isGreaterThan(previous);
      assertThat(points).isEqualTo(InfusionVortex.RING_POINTS);
      assertThat(frame(fx).particles()).isLessThanOrEqualTo(InfusionVortex.RING_POINTS);
      previous = radius;
    }
    assertThat(previous).isGreaterThan(InfusionVortex.PEDESTAL_RADIUS);
  }

  @Test
  void anAbortScattersTheStreamsOutwardWithALowSoundAndStops() {
    InfusionRitual ritual = ritual(SWIFTNESS, 100);
    for (int tick = 0; tick < 50; tick++) {
      ritual.advance();
    }
    double radius = InfusionVortex.radius(ritual.progress());

    visuals.fizzle(anchor, ritual);

    assertThat(timelines).hasSize(1);
    FxTimeline fizzle = timelines.getFirst();
    int duration = argument(fizzle, "duration");
    assertThat(duration).isEqualTo(InfusionVortex.FIZZLE_TICKS);
    assertThat(calls(fizzle, "start")).hasSize(1);
    double previous = radius;
    boolean lowSound = false;
    for (int tick = 0; tick < duration; tick++) {
      FxEmitter fx = host.fx(anchor, FxPriority.TRANSITION);
      timelineFrame(fizzle).render(fx, tick, duration <= 1 ? 1D : (double) tick / (duration - 1));
      Frame frame = frame(fx);
      assertThat(frame.particles()).as("frame %d is no burst", tick).isLessThanOrEqualTo(InfusionVortex.ARMS * InfusionVortex.STREAM_CAP);
      assertThat(calls(fx, "particle")).as("frame %d", tick).noneMatch(call -> call.getArgument(0) == Particle.FLASH);
      double nearest = frame.orbits(Particle.DUST).stream().mapToDouble(Orbit::radius).min().orElseThrow();
      if (tick == 0) {
        assertThat(nearest).isGreaterThanOrEqualTo(previous);
      } else {
        assertThat(nearest).as("frame %d", tick).isGreaterThan(previous);
      }
      previous = nearest;
      for (Invocation call : calls(fx, "chord")) {
        float first = call.getArgument(2);
        float second = call.getArgument(5);
        lowSound |= first < 1F && second < 1F;
      }
    }
    assertThat(lowSound).isTrue();
  }

  @Test
  void aFrameWithoutViewersEmitsNothing() {
    lenient().when(host.fx(any(Location.class), any(FxPriority.class))).thenAnswer(call -> {
      FxEmitter emitter = mock(FxEmitter.class, RETURNS_SELF);
      emitters.add(emitter);
      return emitter;
    });
    InfusionRitual ritual = ritual(SWIFTNESS, 100);
    ritual.advance();

    visuals.swirl(anchor, ritual);

    assertThat(frame(emitters.getLast()).particles()).isZero();
  }

  private InfusionRitual ritual(String result, int duration) {
    return new InfusionRitual(mock(Player.class), CENTER, InfusionTintTest.infusion(result, duration), new EnumMap<>(PlinthSlot.class), 0L);
  }

  private static FxTimeline.Frame timelineFrame(FxTimeline timeline) {
    return argument(timeline, "frame");
  }

  private static <T> T argument(Object mock, String method) {
    List<Invocation> found = calls(mock, method);
    assertThat(found).as(method).hasSize(1);
    return found.getFirst().getArgument(0);
  }

  private static List<Invocation> calls(Object mock, String method) {
    List<Invocation> found = new ArrayList<>();
    for (Invocation invocation : mockingDetails(mock).getInvocations()) {
      if (invocation.getMethod().getName().equals(method)) {
        found.add(invocation);
      }
    }
    return found;
  }

  private static Frame frame(FxEmitter emitter) {
    int particles = 0;
    int streamParticles = 0;
    List<Orbit> orbits = new ArrayList<>();
    for (Invocation call : mockingDetails(emitter).getInvocations()) {
      String name = call.getMethod().getName();
      switch (name) {
        case "orbit" -> {
          Orbit orbit = new Orbit(call.getArgument(0), call.getArgument(1), call.getArgument(2), call.getArgument(3), call.getArgument(4),
              call.getArgument(5));
          orbits.add(orbit);
          particles += orbit.arms();
          streamParticles += orbit.arms();
        }
        case "particle", "burst" -> particles += (int) call.getArgument(1);
        case "ring" -> particles += (int) call.getArgument(2);
        case "sound", "chord", "viewerCount", "color" -> {
        }
        default -> throw new AssertionError("Unexpected fx call " + name);
      }
    }
    return new Frame(particles, streamParticles, orbits);
  }

  private record Orbit(Particle particle, int arms, double radius, double angle, double height, Object data) {
  }

  private record Frame(int particles, int streamParticles, List<Orbit> orbits) {
    private List<Orbit> orbits(Particle particle) {
      List<Orbit> matching = new ArrayList<>();
      for (Orbit orbit : orbits) {
        if (orbit.particle() == particle) {
          matching.add(orbit);
        }
      }
      return matching;
    }
  }
}
