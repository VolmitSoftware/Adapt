package art.arcane.adapt.content.adaptation.crafting.upgraded;

import art.arcane.adapt.api.content.Adaptation;
import art.arcane.adapt.engine.mutation.SkillMutations;
import art.arcane.adapt.engine.skill.SkillHost;
import art.arcane.adapt.engine.skill.SkillRegistry;
import art.arcane.adapt.engine.world.AdaptPlayer;
import art.arcane.adapt.engine.world.PlayerSkillLine;
import art.arcane.adapt.engine.xp.XP;
import art.arcane.adapt.content.skill.SkillArchitect;
import art.arcane.adapt.content.skill.SkillCrafting;
import art.arcane.adapt.platform.entity.CraftingStation;
import art.arcane.adapt.platform.entity.Hand;
import art.arcane.adapt.platform.event.inventory.CraftItemEvent;
import art.arcane.adapt.platform.event.inventory.InventoryAction;
import art.arcane.adapt.platform.event.inventory.InventoryKind;
import art.arcane.adapt.platform.event.player.InteractAction;
import art.arcane.adapt.platform.event.player.PlayerInteractEvent;
import art.arcane.adapt.platform.event.player.PlayerSwapHandItemsEvent;
import art.arcane.adapt.platform.event.player.UseResult;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.menu.ClickKind;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.testkit.FakeItem;
import art.arcane.adapt.testkit.FakePlayer;
import art.arcane.adapt.testkit.TestAdapt;
import art.arcane.adapt.testkit.TestPlatform;
import art.arcane.adapt.testkit.XpPaid;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CraftingUpgradesRuntimeTest {
  private static final Id IRON_INGOT = Id.minecraft("iron_ingot");
  private static final Id IRON_BLOCK = Id.minecraft("iron_block");
  private static final Id PICKAXE = Id.minecraft("diamond_pickaxe");

  @TempDir
  Path data;

  private FakePlayer player;
  private TestAdapt adapt;
  private AdaptPlayer profile;
  private SkillRegistry registry;

  @BeforeEach
  void start() {
    TestPlatform platform = TestPlatform.builder(data).build();
    player = platform.player("Artisan", 0.5D, 61D, 0.5D);
    adapt = TestAdapt.start(platform, List.of(SkillCrafting.class, SkillArchitect.class));
    profile = adapt.join(player);
    registry = adapt.core().getAdaptServer().getSkillRegistry();
  }

  @AfterEach
  void stop() {
    adapt.close();
    assertThat(adapt.handlerFailures()).isEmpty();
  }

  @Test
  void grandArtisanForgesEveryCraftedToolAtItsTopLevel() {
    mutate();
    learn(GrandArtisan.class, 3);
    ItemRef plain = FakeItem.tool("diamond_pickaxe", 1561);

    CraftItemEvent craft = craft(plain);

    assertThat(craft.currentItem()).isNotSameAs(plain);
    assertThat(craft.currentItem().lore()).isNotEmpty();
  }

  @Test
  void grandArtisanDoesNothingForAPlayerWhoHasNotMutated() {
    learn(GrandArtisan.class, 3);
    ItemRef plain = FakeItem.tool("diamond_pickaxe", 1561);

    assertThat(craft(plain).currentItem()).isSameAs(plain);
  }

  @Test
  void unmakerCompactsFullStacksAwayFromACraftingTable() {
    mutate();
    learn(Unmaker.class, 1);
    player.setSneaking(true);
    player.inventory().setItem(3, new FakeItem(IRON_INGOT, 64));

    PlayerSwapHandItemsEvent swap = adapt.post(new PlayerSwapHandItemsEvent(player, FakeItem.empty(), FakeItem.empty()));

    assertThat(swap.isCancelled()).isTrue();
    assertThat(player.inventory().contains(IRON_BLOCK, 7)).isTrue();
    assertThat(player.inventory().contains(IRON_INGOT, 2)).isFalse();
  }

  @Test
  void unmakerLeavesTheSwapAloneWhenNothingCompactsAndPaysNoXp() {
    mutate();
    Unmaker unmaker = learn(Unmaker.class, 3);
    player.setSneaking(true);

    PlayerSwapHandItemsEvent nothing = adapt.post(new PlayerSwapHandItemsEvent(player, FakeItem.empty(), FakeItem.empty()));
    player.inventory().setItem(3, new FakeItem(IRON_INGOT, 64));
    PlayerSwapHandItemsEvent compacted = adapt.post(new PlayerSwapHandItemsEvent(player, FakeItem.empty(), FakeItem.empty()));

    assertThat(compacted.isCancelled()).isTrue();
    assertThat(nothing.isCancelled()).isFalse();
    assertThat(XpPaid.by(profile, unmaker)).isZero();
  }

  @Test
  void unmakerDoesNothingForAPlayerWhoHasNotMutated() {
    learn(Unmaker.class, 3);
    player.setSneaking(true);
    player.inventory().setItem(3, new FakeItem(IRON_INGOT, 64));

    adapt.post(new PlayerSwapHandItemsEvent(player, FakeItem.empty(), FakeItem.empty()));

    assertThat(player.inventory().contains(IRON_BLOCK, 1)).isFalse();
  }

  @Test
  void fieldWorkshopOpensAHeldWorkstationThroughPortableTables() {
    mutate();
    learn(FieldWorkshop.class, 1);
    player.holding(new FakeItem(Id.minecraft("stonecutter"), 1));

    adapt.post(new PlayerInteractEvent(player, InteractAction.RIGHT_CLICK_AIR, Hand.MAIN_HAND, player.inventory().mainHand(),
        null, null, null, UseResult.DEFAULT, UseResult.DEFAULT));

    assertThat(player.openStation()).isEqualTo(CraftingStation.STONECUTTER);
  }

  private CraftItemEvent craft(ItemRef result) {
    return adapt.post(new CraftItemEvent(player, InventoryKind.WORKBENCH, 0, 0, ClickKind.LEFT, InventoryAction.PICKUP_ALL, -1,
        result, FakeItem.empty(), Id.minecraft("diamond_pickaxe"), CraftItemEvent.RecipeKind.SHAPED, List.of()));
  }

  private void mutate() {
    profile.getData().setMasterXp(1_000_000D);
    for (String skill : List.of("crafting", "architect")) {
      PlayerSkillLine line = profile.getData().getSkillLine(skill);
      line.setXp(XP.getXpForLevel(20) + 1D);
      line.settleWithoutRewards();
    }
    assertThat(SkillMutations.mutate(profile.getData(), registry.getSkill("crafting"), registry.getSkill("architect"), "Artisan"))
        .isEqualTo(SkillMutations.Result.MUTATED);
  }

  private <A extends Adaptation<?>> A learn(Class<A> type, int level) {
    A upgrade = SkillHost.of(registry.getSkill("crafting")).getUpgrades().stream().filter(type::isInstance).map(type::cast).findFirst()
        .orElseThrow();
    profile.getData().getSkillLine("crafting").setAdaptation(upgrade, level);
    return upgrade;
  }
}
