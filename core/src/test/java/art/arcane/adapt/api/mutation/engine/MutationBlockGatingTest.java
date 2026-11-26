package art.arcane.adapt.api.mutation.engine;

import art.arcane.adapt.api.ComponentRegistrar;
import art.arcane.adapt.api.adaptation.RunsWithoutLearnedAdaptation;
import art.arcane.adapt.api.mutation.MutatedSkill;
import art.arcane.adapt.api.mutation.SkillMutation;
import art.arcane.adapt.api.mutation.blocks.MutatedAdaptation;
import art.arcane.adapt.api.mutation.blocks.MutationRecipes;
import art.arcane.adapt.api.mutation.blocks.SkillBlockCatalog;
import art.arcane.adapt.api.mutation.blocks.SkillBlocks;
import art.arcane.adapt.api.skill.Skill;
import art.arcane.adapt.api.skill.SkillRegistry;
import art.arcane.adapt.api.world.AdaptPlayer;
import art.arcane.adapt.content.CoreContent;
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
    adapt = TestAdapt.start(platform, CoreContent.skillTypes());
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
            .as("%s#%s", block.getName(), method.method().getName()).isFalse();
      }
    }
  }

  @TestFactory
  Stream<DynamicTest> everyGatedBlockHandlerRunsOnlyForHolders() {
    List<DynamicTest> tests = new ArrayList<>();
    for (MutationBlock<?> block : blocks()) {
      for (HandlerMethod method : AnnotatedHandlers.of(block.getClass())) {
        if (ComponentRegistrar.gatesOnLearner(block, method)) {
          tests.add(DynamicTest.dynamicTest(block.getName() + "#" + method.eventType().getSimpleName(),
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

    kit.learn(holding.lead(), holding.partner(), holding.recipe().recipe(), 1);
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
    String skill = block.getSkillId();
    for (SkillBlocks other : SkillBlockCatalog.all()) {
      if (other.skillId().equals(skill)) {
        continue;
      }
      SkillBlocks own = SkillBlockCatalog.require(skill);
      for (SkillBlocks[] pair : List.of(new SkillBlocks[] {own, other}, new SkillBlocks[] {other, own})) {
        MutatedSkill mutated = registry.getMutatedSkill(new SkillMutation(pair[0].skillId(), pair[1].skillId()).id());
        for (MutatedAdaptation recipe : MutationRecipes.compose(pair[0], pair[1])) {
          if (recipe.blocks().contains(block.getRef()) && mutated != null
              && mutated.getMutationAdaptation(recipe.recipe()) != null) {
            return new Holding(pair[0].skillId(), pair[1].skillId(), recipe);
          }
        }
      }
    }
    throw new AssertionError("No mutated adaptation uses " + block.getName());
  }

  private record Holding(String lead, String partner, MutatedAdaptation recipe) {
  }

  private static List<MutationBlock<?>> blocks() {
    List<MutationBlock<?>> blocks = new ArrayList<>();
    for (Skill<?> skill : registry.getSkills()) {
      blocks.addAll(skill.getBlocks());
    }
    return blocks;
  }
}
