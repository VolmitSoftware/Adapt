package art.arcane.adapt.api.mutation.signature;

import art.arcane.adapt.api.adaptation.Adaptation;
import art.arcane.adapt.api.mutation.blocks.Recipe;
import art.arcane.adapt.api.world.AdaptPlayer;
import art.arcane.adapt.api.mutation.tool.MutationTool;
import art.arcane.adapt.content.adaptation.herbalism.upgraded.HarvestMoon;
import art.arcane.adapt.content.adaptation.rift.upgraded.PocketRealm;
import art.arcane.adapt.content.skill.SkillHerbalism;
import art.arcane.adapt.content.skill.SkillRift;
import art.arcane.adapt.platform.entity.Hand;
import art.arcane.adapt.platform.entity.InventoryRef;
import art.arcane.adapt.platform.entity.ItemEntityRef;
import art.arcane.adapt.platform.event.player.InteractAction;
import art.arcane.adapt.platform.event.player.PlayerInteractEvent;
import art.arcane.adapt.platform.event.player.UseResult;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.BlockFace;
import art.arcane.adapt.platform.value.BlockPos;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.world.BlockRef;
import art.arcane.adapt.testkit.FakeBlockState;
import art.arcane.adapt.testkit.FakeInventory;
import art.arcane.adapt.testkit.FakeItem;
import art.arcane.adapt.testkit.FakePlayer;
import art.arcane.adapt.testkit.MutationKit;
import art.arcane.adapt.testkit.TestAdapt;
import art.arcane.adapt.testkit.TestPlatform;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Herbalism leading Rift replaces Convergence with Rift Harvest. */
class RiftHarvestTest {
  private static final Id WHEAT = Id.minecraft("wheat");
  private static final Id CARROT = Id.minecraft("carrot");
  private static final Id PEARL = Id.minecraft("ender_pearl");

  @TempDir
  Path data;

  private TestPlatform platform;
  private TestAdapt adapt;
  private FakePlayer player;
  private AdaptPlayer profile;
  private MutationKit kit;

  @BeforeEach
  void start() {
    platform = TestPlatform.builder(data).build();
    player = platform.player("Warden", 0.5D, 61D, 0.5D);
    adapt = TestAdapt.start(platform, List.of(SkillHerbalism.class, SkillRift.class));
    profile = adapt.join(player);
    kit = new MutationKit(adapt, profile);
    player.holding(FakeItem.tool("iron_hoe", 250));
    for (int x = 0; x < 5; x++) {
      platform.world().set(new BlockPos(x, 61, 3), FakeBlockState.of("wheat").with("age", "7"));
    }
  }

  @AfterEach
  void stop() {
    adapt.close();
    assertThat(adapt.handlerFailures()).isEmpty();
  }

  @Test
  void aHarvestMoonSweepSendsItsProduceToTheEnderChest() {
    kit.mutate("herbalism", "rift");
    harvestMoon();
    kit.learn("herbalism", "rift", Recipe.CONVERGENCE, 1);

    sweep();

    assertThat(kit.adaptation("herbalism", "rift", Recipe.CONVERGENCE).getSignature()).isInstanceOf(RiftHarvest.class);
    assertThat(player.enderChest().contains(WHEAT, 5)).isTrue();
    assertThat(player.inventory().contains(WHEAT, 1)).isFalse();
  }

  @Test
  void riftLeadingHerbalismAndAPlayerWithoutTheAdaptationKeepTheProduce() {
    kit.mutate("rift", "herbalism");
    harvestMoon();
    kit.learn("rift", "herbalism", Recipe.CONVERGENCE, 3);

    sweep();

    assertThat(player.inventory().contains(WHEAT, 5)).isTrue();
    assertThat(player.enderChest().contains(WHEAT, 1)).isFalse();
  }

  @Test
  void eachSweepSendsAtMostTheLevelsStacks() {
    kit.mutate("herbalism", "rift");
    harvestMoon();
    RiftHarvest harvest = (RiftHarvest) kit.learn("herbalism", "rift", Recipe.CONVERGENCE, 1).getSignature();
    harvest.getConfig().stacksAtFirstLevel = 1;
    for (int x = -4; x <= 4; x++) {
      for (int z = 0; z < 8; z++) {
        platform.world().set(new BlockPos(x, 61, z), FakeBlockState.of("wheat").with("age", "7"));
      }
    }

    sweep();

    assertThat(harvest.maxStacks(3)).isGreaterThan(harvest.maxStacks(2)).isGreaterThan(harvest.maxStacks(1));
    assertThat(player.enderChest().contains(WHEAT, 64)).isTrue();
    assertThat(player.enderChest().contains(WHEAT, 65)).isFalse();
    assertThat(count(WHEAT)).isEqualTo(8);
  }

  @Test
  void produceThatHarvestMoonDidNotYieldStaysWhereItIs() {
    kit.mutate("herbalism", "rift");
    kit.learn("herbalism", "rift", Recipe.CONVERGENCE, 3);
    BlockRef crop = platform.world().blockAt(0, 61, 3);

    adapt.post(new PlayerInteractEvent(player, InteractAction.RIGHT_CLICK_BLOCK, Hand.MAIN_HAND,
        player.inventory().mainHand(), crop, BlockFace.UP, crop.center().position(), UseResult.DEFAULT,
        UseResult.DEFAULT));
    ItemEntityRef dropped = platform.world().dropItem(crop.center().position(), new FakeItem(WHEAT, 3));
    platform.scheduler().advance(2);

    assertThat(dropped.isValid()).isTrue();
    assertThat(player.enderChest().contains(WHEAT, 1)).isFalse();
  }

  @Test
  void produceYouAlreadyCarriedStaysWithYou() {
    kit.mutate("herbalism", "rift");
    harvestMoon();
    kit.learn("herbalism", "rift", Recipe.CONVERGENCE, 3);
    player.inventory().addItem(new FakeItem(CARROT, 32));

    sweep();

    assertThat(count(CARROT)).isEqualTo(32);
    assertThat(player.enderChest().contains(CARROT, 1)).isFalse();
  }

  @Test
  void aChosenAnchorReceivesTheProduceForItsPearlAndUnchoosingFallsBackToTheEnderChest() throws InterruptedException {
    kit.mutate("herbalism", "rift");
    harvestMoon();
    kit.learn("herbalism", "rift", Recipe.CONVERGENCE, 1);
    PocketRealm realm = upgrade("rift", PocketRealm.class);
    profile.getData().getSkillLine("rift").setAdaptation(realm, 1);
    InventoryRef chest = anchorWithChest(realm, 20);
    player.inventory().setItem(10, new FakeItem(PEARL, 2));
    player.holding(FakeItem.tool("iron_hoe", 250));

    choose(20);
    sweep();
    long deadline = System.currentTimeMillis() + 2000L;
    while (!chest.contains(WHEAT, 5) && System.currentTimeMillis() < deadline) {
      platform.scheduler().advance(1);
      Thread.sleep(10L);
    }

    assertThat(chest.contains(WHEAT, 5)).isTrue();
    assertThat(player.enderChest().contains(WHEAT, 1)).isFalse();
    assertThat(count(PEARL)).isEqualTo(1);

    choose(20);
    for (int x = 0; x < 5; x++) {
      platform.world().set(new BlockPos(x, 61, 3), FakeBlockState.of("wheat").with("age", "7"));
    }
    player.holding(FakeItem.tool("diamond_hoe", 1561));
    sweep();
    assertThat(player.enderChest().contains(WHEAT, 5)).isTrue();
    assertThat(count(PEARL)).isEqualTo(1);
  }

  @Test
  void withoutAPearlTheProduceFallsBackToTheEnderChest() {
    kit.mutate("herbalism", "rift");
    harvestMoon();
    kit.learn("herbalism", "rift", Recipe.CONVERGENCE, 1);
    PocketRealm realm = upgrade("rift", PocketRealm.class);
    profile.getData().getSkillLine("rift").setAdaptation(realm, 1);
    InventoryRef chest = anchorWithChest(realm, 20);
    player.holding(FakeItem.tool("iron_hoe", 250));

    choose(20);
    sweep();

    assertThat(player.enderChest().contains(WHEAT, 5)).isTrue();
    assertThat(chest.contains(WHEAT, 1)).isFalse();
  }

  private InventoryRef anchorWithChest(PocketRealm realm, int x) {
    platform.world().set(x, 60, 0, "stone");
    ItemRef anchor = ((MutationTool<MutationTool.NoData>) realm.getTools().getFirst()).create(new MutationTool.NoData());
    ItemRef hoe = player.inventory().mainHand();
    player.holding(anchor);
    BlockRef ground = platform.world().blockAt(x, 60, 0);
    adapt.post(new PlayerInteractEvent(player, InteractAction.RIGHT_CLICK_BLOCK, Hand.MAIN_HAND, anchor, ground,
        BlockFace.UP, null, UseResult.DEFAULT, UseResult.DEFAULT));
    platform.scheduler().advance(2);
    player.holding(hoe);
    assertThat(platform.world().blockAt(x, 61, 0).type()).isEqualTo(Id.minecraft("lodestone"));
    platform.world().set(x, 61, 1, "chest");
    FakeInventory inventory = new FakeInventory();
    platform.world().blockInventory(new BlockPos(x, 61, 1), inventory);
    return inventory;
  }

  private void choose(int x) {
    player.setSneaking(true);
    BlockRef anchor = platform.world().blockAt(x, 61, 0);
    adapt.post(new PlayerInteractEvent(player, InteractAction.RIGHT_CLICK_BLOCK, Hand.MAIN_HAND,
        player.inventory().mainHand(), anchor, BlockFace.UP, anchor.center().position(), UseResult.DEFAULT,
        UseResult.DEFAULT));
    player.setSneaking(false);
  }

  private <A extends Adaptation<?>> A upgrade(String skill, Class<A> type) {
    return kit.registry().getSkill(skill).getUpgrades().stream().filter(type::isInstance).map(type::cast)
        .findFirst().orElseThrow();
  }

  private void harvestMoon() {
    for (Adaptation<?> upgrade : kit.registry().getSkill("herbalism").getUpgrades()) {
      if (upgrade instanceof HarvestMoon moon) {
        profile.getData().getSkillLine("herbalism").setAdaptation(moon, 1);
      }
    }
  }

  private void sweep() {
    BlockRef crop = platform.world().blockAt(0, 61, 3);
    adapt.post(new PlayerInteractEvent(player, InteractAction.RIGHT_CLICK_BLOCK, Hand.MAIN_HAND,
        player.inventory().mainHand(), crop, BlockFace.UP, crop.center().position(), UseResult.DEFAULT,
        UseResult.DEFAULT));
    platform.scheduler().advance(2);
  }

  private int count(Id type) {
    int total = 0;
    for (ItemRef stack : player.inventory().contents()) {
      if (stack != null && stack.type().equals(type)) {
        total += stack.amount();
      }
    }
    return total;
  }
}
