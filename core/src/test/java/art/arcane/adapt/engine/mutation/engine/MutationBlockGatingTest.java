package art.arcane.adapt.engine.mutation.engine;

import art.arcane.adapt.api.content.Skill;
import art.arcane.adapt.engine.skill.SkillHost;
import art.arcane.adapt.platform.value.Id;

import art.arcane.adapt.api.content.RunsWithoutLearnedAdaptation;
import art.arcane.adapt.content.BuiltinAddon;
import art.arcane.adapt.engine.ComponentRegistrar;
import art.arcane.adapt.engine.mutation.MutatedSkill;
import art.arcane.adapt.api.mutation.MutationBlock;
import art.arcane.adapt.api.mutation.Recipe;
import art.arcane.adapt.api.mutation.SkillPair;
import art.arcane.adapt.engine.skill.SkillRegistry;
import art.arcane.adapt.engine.world.AdaptPlayer;
import art.arcane.adapt.platform.event.AdaptEvent;
import art.arcane.adapt.platform.event.AnnotatedHandlers;
import art.arcane.adapt.platform.event.EventListener;
import art.arcane.adapt.platform.event.HandlerMethod;
import art.arcane.adapt.platform.event.player.PlayerJumpEvent;
import art.arcane.adapt.platform.event.player.PlayerMoveEvent;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.testkit.FakeGameContent;
import art.arcane.adapt.testkit.FakePlayer;
import art.arcane.adapt.testkit.MutationKit;
import art.arcane.adapt.testkit.TestAdapt;
import art.arcane.adapt.testkit.TestPlatform;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Every registered block's learner-gated handler runs for a player holding a learned mutated adaptation that uses the
 * block, and for no one else; no block opts out of the gate.
 */
class MutationBlockGatingTest {
  @TempDir
  static Path data;

  private static TestPlatform platform;
  private static TestAdapt adapt;
  private static SkillRegistry registry;
  private static int players;

  @BeforeAll
  static void start() {
    platform = TestPlatform.builder(data).build();
    platform.host().runGame(FakeGameContent.v26_3());
    adapt = TestAdapt.start(platform, new BuiltinAddon());
    registry = adapt.core().getAdaptServer().getSkillRegistry();
  }

  @AfterAll
  static void stop() {
    adapt.close();
  }

  @Test
  void noBlockHandlerOptsOutOfTheLearnerGate() {
    for (MutationBlock<?> block : blocks()) {
      for (HandlerMethod method : AnnotatedHandlers.of(block.getClass())) {
        assertThat(method.isAnnotationPresent(RunsWithoutLearnedAdaptation.class))
            .as("%s#%s", block.getId().toString(), method.method().getName()).isFalse();
      }
    }
  }

  @TestFactory
  Stream<DynamicTest> everyGatedBlockHandlerRunsOnlyForHolders() {
    List<DynamicTest> tests = new ArrayList<>();
    for (MutationBlock<?> block : blocks()) {
      for (HandlerMethod method : AnnotatedHandlers.of(block.getClass())) {
        if (ComponentRegistrar.gatesOnLearner(block, method)) {
          tests.add(DynamicTest.dynamicTest(block.getId().toString() + "#" + method.eventType().getSimpleName(),
              () -> runsOnlyForHolders(block, method)));
        }
      }
    }
    assertThat(tests).as("blocks with move or jump handlers").isNotEmpty();
    return tests.stream();
  }

  private static void runsOnlyForHolders(MutationBlock<?> block, HandlerMethod method) throws Exception {
    Holding holding = holding(block);
    FakePlayer holder = platform.player("Holder" + players++, 0.5D, 61D, 0.5D);
    FakePlayer other = platform.player("Other" + players++, 4.5D, 61D, 0.5D);
    AdaptPlayer profile = adapt.join(holder);
    adapt.join(other);
    MutationKit kit = new MutationKit(adapt, profile);
    AtomicInteger runs = new AtomicInteger();
    EventListener<AdaptEvent> gated = ComponentRegistrar.gate(block, method, (AdaptEvent event) -> runs.incrementAndGet());

    kit.mutate(holding.lead(), holding.partner());
    gated.handle(event(method, holder));
    assertThat(runs).as("mutated but not learned").hasValue(0);

    kit.learn(holding.lead(), holding.partner(), holding.recipe(), 1);
    gated.handle(event(method, holder));
    gated.handle(event(method, other));
    assertThat(runs).as("holder runs, other does not").hasValue(1);
  }

  private static AdaptEvent event(HandlerMethod method, FakePlayer player) {
    Loc from = player.location();
    Loc to = new Loc(from.world(), from.x() + 1D, from.y(), from.z(), 0F, 0F);
    if (method.eventType() == PlayerMoveEvent.class) {
      return new PlayerMoveEvent(player, from, to);
    }
    if (method.eventType() == PlayerJumpEvent.class) {
      return new PlayerJumpEvent(player, from, to);
    }
    throw new AssertionError("The learner gate covers " + method.eventType().getSimpleName()
        + "; give this test an event for it");
  }

  /** A pair and recipe whose mutated adaptation uses the block on this server. */
  private static Holding holding(MutationBlock<?> block) {
    Id skill = block.getSkillId();
    for (Skill<?> other : registry.getSkills()) {
      if (other.getId().equals(skill)) {
        continue;
      }
      for (SkillPair pair : List.of(new SkillPair(skill, other.getId()), new SkillPair(other.getId(), skill))) {
        MutatedSkill mutated = registry.getMutatedSkill(pair);
        if (mutated == null) {
          continue;
        }
        for (MutationAdaptation adaptation : mutated.getMutationAdaptations()) {
          if (adaptation.uses(block)) {
            return new Holding(pair.lead().path(), pair.partner().path(), adaptation.getRecipe());
          }
        }
      }
    }
    throw new AssertionError("No mutated adaptation uses " + block.getId().toString());
  }

  private record Holding(String lead, String partner, Recipe recipe) {
  }

  private static List<MutationBlock<?>> blocks() {
    List<MutationBlock<?>> blocks = new ArrayList<>();
    for (Skill<?> skill : registry.getSkills()) {
      blocks.addAll(SkillHost.of(skill).getBlocks());
    }
    return blocks;
  }
}
