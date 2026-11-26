package art.arcane.adapt.api.mutation.signature;

import art.arcane.adapt.api.adaptation.Adaptation;
import art.arcane.adapt.api.mutation.blocks.Recipe;
import art.arcane.adapt.api.mutation.engine.MutationAdaptation;
import art.arcane.adapt.api.skill.Skill;
import art.arcane.adapt.api.skill.SkillRegistry;
import art.arcane.adapt.api.world.AdaptPlayer;
import art.arcane.adapt.platform.event.block.BlockBreakEvent;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.world.BlockRef;
import art.arcane.adapt.testkit.FakeItemEntity;
import art.arcane.adapt.testkit.FakePlayer;
import art.arcane.adapt.testkit.FakeWorld;
import art.arcane.adapt.testkit.MutationKit;
import art.arcane.adapt.testkit.TestAdapt;
import art.arcane.adapt.testkit.TestPlatform;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** One player, the pair's two skills, and the small steps every signature test repeats. */
final class SignatureBench {
  static final int Y = 61;

  final TestPlatform platform;
  final FakeWorld world;
  final FakePlayer player;
  final TestAdapt adapt;
  final AdaptPlayer profile;
  final MutationKit kit;
  final SkillRegistry registry;

  SignatureBench(Path data, List<Class<? extends Skill<?>>> skills) {
    platform = TestPlatform.builder(data).build();
    world = platform.world();
    player = platform.player("Builder", 0.5D, Y, 0.5D);
    adapt = TestAdapt.start(platform, skills);
    profile = adapt.join(player);
    kit = new MutationKit(adapt, profile);
    registry = adapt.core().getAdaptServer().getSkillRegistry();
  }

  void close() {
    adapt.close();
    assertThat(adapt.handlerFailures()).isEmpty();
  }

  /** Mutates the pair and learns its Convergence at {@code level}; returns the adaptation it replaced. */
  MutationAdaptation signature(String lead, String partner, int level) {
    kit.mutate(lead, partner);
    return kit.learn(lead, partner, Recipe.CONVERGENCE, level);
  }

  <A extends Adaptation<?>> A upgrade(String skill, Class<A> type, int level) {
    A upgrade = registry.getSkill(skill).getUpgrades().stream().filter(type::isInstance).map(type::cast).findFirst()
        .orElseThrow();
    profile.getData().getSkillLine(skill).setAdaptation(upgrade, level);
    return upgrade;
  }

  <S extends Signature<?>> S handler(String lead, String partner, Class<S> type) {
    MutationAdaptation adaptation = kit.adaptation(lead, partner, Recipe.CONVERGENCE);
    assertThat(adaptation.getSignature()).isInstanceOf(type);
    return type.cast(adaptation.getSignature());
  }

  BlockBreakEvent breakBlock(BlockRef block) {
    return adapt.post(new BlockBreakEvent(player, block, true, 0));
  }

  void advance(int ticks) {
    platform.scheduler().advance(ticks);
  }

  /** Items of {@code type} in the inventory, the off hand and on the ground. */
  int count(Id type) {
    int count = 0;
    List<ItemRef> contents = player.inventory().contents();
    for (ItemRef item : contents) {
      if (item != null && !item.isEmpty() && item.type().equals(type)) {
        count += item.amount();
      }
    }
    ItemRef off = player.inventory().offHand();
    if (off != null && !off.isEmpty() && off.type().equals(type) && !contents.contains(off)) {
      count += off.amount();
    }
    for (FakeItemEntity item : world.items()) {
      if (item.isValid() && item.item().type().equals(type)) {
        count += item.item().amount();
      }
    }
    return count;
  }

  int placed(Id type, List<BlockRef> blocks) {
    int placed = 0;
    for (BlockRef block : blocks) {
      placed += block.type().equals(type) ? 1 : 0;
    }
    return placed;
  }
}
