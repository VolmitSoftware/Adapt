package art.arcane.adapt.content.adaptation.excavation;

import art.arcane.adapt.api.adaptation.Adaptation;
import art.arcane.adapt.api.skill.Skill;
import art.arcane.adapt.api.skill.SkillRegistry;
import art.arcane.adapt.api.world.AdaptPlayer;
import art.arcane.adapt.content.skill.SkillExcavation;
import art.arcane.adapt.platform.entity.ItemEntityRef;
import art.arcane.adapt.platform.event.block.BlockBreakEvent;
import art.arcane.adapt.platform.event.block.BlockDropItemEvent;
import art.arcane.adapt.platform.event.entity.DamageCause;
import art.arcane.adapt.platform.event.entity.EntityDamageEvent;
import art.arcane.adapt.platform.event.entity.FinalDamage;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.world.BlockRef;
import art.arcane.adapt.testkit.FakeBlockState;
import art.arcane.adapt.testkit.FakeItem;
import art.arcane.adapt.testkit.FakePlayer;
import art.arcane.adapt.testkit.FakeWorld;
import art.arcane.adapt.testkit.TestAdapt;
import art.arcane.adapt.testkit.TestPlatform;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ExcavationRuntimeTest {
  @TempDir
  Path data;

  private FakeWorld world;
  private FakePlayer player;
  private TestAdapt adapt;
  private AdaptPlayer profile;

  @BeforeEach
  void start() {
    TestPlatform platform = TestPlatform.builder(data).build();
    world = platform.world();
    player = platform.player("Digger", 0.5D, 61D, 0.5D);
    player.holding(FakeItem.tool("iron_shovel", 250));
    adapt = TestAdapt.start(platform, List.of(SkillExcavation.class));
    profile = adapt.join(player);
  }

  @AfterEach
  void stop() {
    adapt.close();
    assertThat(adapt.handlerFailures()).isEmpty();
  }

  private void learn(Class<? extends Adaptation<?>> type, int level) {
    Skill<?> skill = SkillRegistry.skills.get("excavation");
    for (Adaptation<?> adaptation : skill.getAdaptations()) {
      if (type.isInstance(adaptation)) {
        profile.getData().getSkillLine("excavation").setAdaptation(adaptation, level);
        return;
      }
    }
    throw new AssertionError("No adaptation " + type.getSimpleName());
  }

  private double stat(String key) {
    return profile.getData().getStat(key);
  }

  @Test
  void skillRegistersEveryAdaptation() {
    assertThat(SkillRegistry.skills.get("excavation").getAdaptations()).hasSize(13);
  }

  @Test
  void skillCountsShovelBreaksAndLooseGround() {
    BlockRef gravel = world.set(0, 60, 0, "gravel").blockAt(0, 60, 0);

    adapt.post(new BlockBreakEvent(player, gravel, true, 0));

    assertThat(stat("excavation.blocks.broken")).isEqualTo(1D);
    assertThat(stat("excavation.gravel")).isEqualTo(1D);
  }

  @Test
  void skillIgnoresBreaksWithoutAShovel() {
    player.holding(FakeItem.of("stick"));

    adapt.post(new BlockBreakEvent(player, world.set(0, 60, 0, "dirt").blockAt(0, 60, 0), true, 0));

    assertThat(stat("excavation.blocks.broken")).isZero();
  }

  @Test
  void dropToInventoryCatchesShovelDrops() {
    learn(ExcavationDropToInventory.class, 1);
    BlockRef dirt = world.blockAt(0, 60, 0);
    List<ItemEntityRef> entities = new ArrayList<>();
    entities.add(world.createItemEntity(dirt.center().position(), FakeItem.of("dirt")));

    BlockDropItemEvent event = adapt.post(new BlockDropItemEvent(player, dirt, FakeBlockState.of("dirt"), entities));

    assertThat(event.items()).isEmpty();
    assertThat(stat("excavation.drop-to-inv.items-caught")).isEqualTo(1D);
    boolean caught = false;
    for (ItemRef item : player.inventory().contents()) {
      caught |= item.type().equals(Id.minecraft("dirt"));
    }
    assertThat(caught).isTrue();
  }

  @Test
  void softFallReducesFallDamageOnSoftGround() {
    learn(ExcavationSoftFall.class, 1);
    world.set(0, 60, 0, "sand");

    EntityDamageEvent event = adapt.post(new EntityDamageEvent(player, DamageCause.FALL, Id.minecraft("fall"), 10D,
        FinalDamage.IDENTITY));

    assertThat(event.damage()).isLessThan(10D);
    assertThat(stat("excavation.soft-fall.damage-prevented")).isPositive();
  }
}
