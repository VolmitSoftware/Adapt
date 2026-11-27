package art.arcane.adapt.content.adaptation.pickaxe.upgraded;

import art.arcane.adapt.engine.mutation.SkillMutations;
import art.arcane.adapt.engine.skill.SkillHost;
import art.arcane.adapt.engine.skill.SkillRegistry;
import art.arcane.adapt.engine.world.AdaptPlayer;
import art.arcane.adapt.engine.world.PlayerSkillLine;
import art.arcane.adapt.engine.xp.XP;
import art.arcane.adapt.engine.xp.XpProvenance;
import art.arcane.adapt.content.skill.SkillPickaxes;
import art.arcane.adapt.content.skill.SkillStealth;
import art.arcane.adapt.platform.entity.Hand;
import art.arcane.adapt.platform.event.player.InteractAction;
import art.arcane.adapt.platform.event.player.PlayerInteractEvent;
import art.arcane.adapt.platform.event.player.UseResult;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.BlockFace;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.world.BlockRef;
import art.arcane.adapt.testkit.FakeItem;
import art.arcane.adapt.testkit.FakeItemEntity;
import art.arcane.adapt.testkit.FakePlayer;
import art.arcane.adapt.testkit.FakeWorld;
import art.arcane.adapt.testkit.TestAdapt;
import art.arcane.adapt.testkit.TestPlatform;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mockStatic;

/** Prospector's Charge opens a sphere of natural stone with the hotbar pickaxe, pays honestly and never dupes. */
class ProspectorsChargeTest {
  private static final Id STONE = Id.minecraft("stone");
  private static final int CX = 0;
  private static final int CY = 50;
  private static final int CZ = 8;
  private static final int FUSE = 60;

  @TempDir
  Path data;

  private TestPlatform platform;
  private FakeWorld world;
  private FakePlayer player;
  private TestAdapt adapt;
  private AdaptPlayer profile;
  private SkillRegistry registry;
  private FakeItem pickaxe;

  @BeforeEach
  void start() {
    platform = TestPlatform.builder(data).build();
    world = platform.world();
    player = platform.player("Delver", 0.5D, 61D, 0.5D);
    adapt = TestAdapt.start(platform, List.of(SkillPickaxes.class, SkillStealth.class));
    profile = adapt.join(player);
    registry = adapt.core().getAdaptServer().getSkillRegistry();
    pickaxe = FakeItem.tool("diamond_pickaxe", 1561);
    player.inventory().setItem(3, pickaxe);
    for (int x = -5; x <= 5; x++) {
      for (int y = -5; y <= 5; y++) {
        for (int z = -5; z <= 5; z++) {
          world.set(CX + x, CY + y, CZ + z, "stone");
        }
      }
    }
  }

  @AfterEach
  void stop() {
    adapt.close();
    assertThat(adapt.handlerFailures()).isEmpty();
  }

  @Test
  void aChargeOpensASphereWithThePickaxeInHand() {
    Earthshaker earthshaker = mutateAndLearn(1);
    world.set(CX + 1, CY, CZ, "iron_ore");
    holdCharges(earthshaker, 4);

    press(center());
    assertThat(player.inventory().mainHand().amount()).isEqualTo(3);
    player.inventory().setHeldSlot(3);
    platform.scheduler().advance(FUSE - 1);
    assertThat(center().type()).isEqualTo(STONE);
    platform.scheduler().advance(20);

    assertThat(center().isAir()).isTrue();
    assertThat(block(1, 1, 0).isAir()).isTrue();
    assertThat(block(0, -1, 1).isAir()).isTrue();
    assertThat(block(3, 0, 0).type()).isEqualTo(STONE);
    assertThat(player.inventory().heldSlot()).isEqualTo(3);
    int opened = (int) profile.getData().getStat("pickaxe.earthshaker.blocks-blasted");
    assertThat(opened).isGreaterThanOrEqualTo(19);
    assertThat(pickaxe.damage()).isEqualTo(opened * earthshaker.getConfig().durabilityPerBonusBlock);
    assertThat(collected()).contains(Id.minecraft("iron_ore"), STONE);
  }

  @Test
  void theChargeIsInertWithoutTheMutation() {
    Earthshaker earthshaker = upgrade();
    profile.getData().getSkillLine("pickaxe").setAdaptation(earthshaker, 3);
    holdCharges(earthshaker, 2);

    press(center());
    platform.scheduler().advance(FUSE + 20);

    assertThat(center().type()).isEqualTo(STONE);
    assertThat(player.inventory().mainHand().amount()).isEqualTo(2);
  }

  @Test
  void placedBlocksAreNeverOpenedSoPlacedOresCannotBeFarmed() {
    Earthshaker earthshaker = mutateAndLearn(1);
    world.set(CX + 1, CY, CZ, "diamond_ore");
    holdCharges(earthshaker, 1);

    try (MockedStatic<XpProvenance> provenance = mockStatic(XpProvenance.class, CALLS_REAL_METHODS)) {
      provenance.when(() -> XpProvenance.isPlayerPlaced(argThat((BlockRef block) -> block.x() == CX + 1)))
          .thenReturn(true);
      press(center());
      player.inventory().setHeldSlot(3);
      platform.scheduler().advance(FUSE + 20);
    }

    assertThat(center().isAir()).isTrue();
    assertThat(block(1, 0, 0).type()).isEqualTo(Id.minecraft("diamond_ore"));
    assertThat(block(1, 1, 0).type()).isEqualTo(STONE);
    assertThat(collected()).doesNotContain(Id.minecraft("diamond_ore"), Id.minecraft("diamond"));
  }

  @Test
  void aPlacedWallTakesNoCharge() {
    Earthshaker earthshaker = mutateAndLearn(1);
    holdCharges(earthshaker, 2);

    try (MockedStatic<XpProvenance> provenance = mockStatic(XpProvenance.class, CALLS_REAL_METHODS)) {
      provenance.when(() -> XpProvenance.isPlayerPlaced(argThat((BlockRef block) -> block.z() == CZ)))
          .thenReturn(true);
      press(center());
    }
    platform.scheduler().advance(FUSE + 20);

    assertThat(player.inventory().mainHand().amount()).isEqualTo(2);
    assertThat(center().type()).isEqualTo(STONE);
  }

  @Test
  void onlyOneChargeLivesAtATime() {
    Earthshaker earthshaker = mutateAndLearn(1);
    holdCharges(earthshaker, 3);

    press(center());
    press(block(0, 3, 0));

    assertThat(player.inventory().mainHand().amount()).isEqualTo(2);
    assertThat(player.actionBars()).isNotEmpty();
  }

  @Test
  void aDiscardingChargeKeepsTheOresAndDropsNoStone() {
    Earthshaker earthshaker = mutateAndLearn(1);
    world.set(CX - 1, CY, CZ, "coal_ore");
    holdCharges(earthshaker, 1);

    player.setSneaking(true);
    press(center());
    player.setSneaking(false);
    assertThat(earthshaker.getTools().getFirst().is(player.inventory().mainHand())).isTrue();
    press(center());
    player.inventory().setHeldSlot(3);
    platform.scheduler().advance(FUSE + 20);

    assertThat(center().isAir()).isTrue();
    assertThat(collected()).contains(Id.minecraft("coal_ore")).doesNotContain(STONE);
  }

  @Test
  void aChargeThatCannotGoOffComesBackOnce() {
    Earthshaker earthshaker = mutateAndLearn(1);
    holdCharges(earthshaker, 1);

    press(center());
    platform.protection().deny(true);
    platform.scheduler().advance(FUSE + 20);

    assertThat(center().type()).isEqualTo(STONE);
    assertThat(countCharges(earthshaker)).isEqualTo(1);
  }

  @Test
  void puttingThePickaxeAwayStopsTheBlast() {
    Earthshaker earthshaker = mutateAndLearn(3);
    earthshaker.getConfig().chargeBlocksPerTick = 1;
    holdCharges(earthshaker, 1);

    press(center());
    player.inventory().setHeldSlot(3);
    platform.scheduler().advance(FUSE + 2);
    player.inventory().setHeldSlot(0);
    int damage = pickaxe.damage();
    platform.scheduler().advance(40);

    assertThat(pickaxe.damage()).isEqualTo(damage);
    assertThat(block(0, 4, 0).type()).isEqualTo(STONE);
  }

  @Test
  void aWornPickaxeStopsTheBlastBeforeItBreaks() {
    Earthshaker earthshaker = mutateAndLearn(1);
    pickaxe.setDamage(1561 - 3);
    holdCharges(earthshaker, 1);

    press(center());
    player.inventory().setHeldSlot(3);
    platform.scheduler().advance(FUSE + 20);

    assertThat(pickaxe.damage()).isLessThan(1561);
    assertThat(profile.getData().getStat("pickaxe.earthshaker.blocks-blasted")).isLessThanOrEqualTo(2D);
  }

  @Test
  void aChargeGoingOffWithoutAPickaxeInHandComesBackAndLeavesTheSlotAlone() {
    Earthshaker earthshaker = mutateAndLearn(1);
    holdCharges(earthshaker, 2);

    press(center());
    platform.scheduler().advance(FUSE + 20);

    assertThat(center().type()).isEqualTo(STONE);
    assertThat(player.inventory().heldSlot()).isZero();
    assertThat(countCharges(earthshaker)).isEqualTo(2);
    assertThat(pickaxe.damage()).isZero();
  }

  /** What the blasted blocks dropped: on the ground, or in the inventory where the kept Drop-To-Inventory put it. */
  private List<Id> collected() {
    List<Id> types = new ArrayList<>();
    for (FakeItemEntity item : world.items()) {
      types.add(item.item().type());
    }
    for (ItemRef item : player.inventory().contents()) {
      if (!item.isEmpty()) {
        types.add(item.type());
      }
    }
    return types;
  }

  private void press(BlockRef wall) {
    ItemRef hand = player.inventory().mainHand();
    adapt.post(new PlayerInteractEvent(player, InteractAction.RIGHT_CLICK_BLOCK, Hand.MAIN_HAND, hand, wall,
        BlockFace.UP, null, UseResult.DEFAULT, UseResult.DEFAULT));
  }

  private void holdCharges(Earthshaker earthshaker, int amount) {
    ItemRef charges = ((ProspectorsCharge) earthshaker.getTools().getFirst()).create(new ProspectorsCharge.Data());
    charges.setAmount(amount);
    player.inventory().setItem(0, charges);
    player.inventory().setHeldSlot(0);
  }

  private int countCharges(Earthshaker earthshaker) {
    int total = 0;
    for (ItemRef item : player.inventory().contents()) {
      if (earthshaker.getTools().getFirst().is(item)) {
        total += item.amount();
      }
    }
    return total;
  }

  private BlockRef center() {
    return block(0, 0, 0);
  }

  private BlockRef block(int dx, int dy, int dz) {
    return world.blockAt(CX + dx, CY + dy, CZ + dz);
  }

  private Earthshaker mutateAndLearn(int level) {
    profile.getData().setMasterXp(1_000_000D);
    for (String skill : List.of("pickaxe", "stealth")) {
      PlayerSkillLine line = profile.getData().getSkillLine(skill);
      line.setXp(XP.getXpForLevel(20) + 1D);
      line.settleWithoutRewards();
    }
    assertThat(SkillMutations.mutate(profile.getData(), registry.getSkill("pickaxe"), registry.getSkill("stealth"),
        "Delver")).isEqualTo(SkillMutations.Result.MUTATED);
    Earthshaker earthshaker = upgrade();
    profile.getData().getSkillLine("pickaxe").setAdaptation(earthshaker, level);
    return earthshaker;
  }

  private Earthshaker upgrade() {
    return SkillHost.of(registry.getSkill("pickaxe")).getUpgrades().stream().filter(Earthshaker.class::isInstance)
        .map(Earthshaker.class::cast).findFirst().orElseThrow();
  }
}
