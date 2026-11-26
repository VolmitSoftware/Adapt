package art.arcane.adapt.content.adaptation.crafting.upgraded;

import art.arcane.adapt.api.item.ItemDurability;
import art.arcane.adapt.api.mutation.SkillMutations;
import art.arcane.adapt.api.skill.SkillRegistry;
import art.arcane.adapt.api.world.AdaptPlayer;
import art.arcane.adapt.api.world.PlayerSkillLine;
import art.arcane.adapt.api.xp.XP;
import art.arcane.adapt.content.adaptation.crafting.CraftingMasterwork;
import art.arcane.adapt.content.skill.SkillArchitect;
import art.arcane.adapt.content.skill.SkillCrafting;
import art.arcane.adapt.platform.data.DataCodecs;
import art.arcane.adapt.platform.data.DataKey;
import art.arcane.adapt.platform.event.inventory.InventoryAction;
import art.arcane.adapt.platform.event.inventory.InventoryClickEvent;
import art.arcane.adapt.platform.event.inventory.InventoryKind;
import art.arcane.adapt.platform.event.inventory.PrepareResultEvent;
import art.arcane.adapt.platform.event.inventory.SlotKind;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.menu.ClickKind;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.testkit.FakeInventory;
import art.arcane.adapt.testkit.FakeItem;
import art.arcane.adapt.testkit.FakePlayer;
import art.arcane.adapt.testkit.TestAdapt;
import art.arcane.adapt.testkit.TestPlatform;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Grand Artisan reforges gear at a smithing table with its base material and experience levels. */
class GrandArtisanReforgeTest {
  private static final Id DIAMOND = Id.minecraft("diamond");
  private static final Id PICKAXE = Id.minecraft("diamond_pickaxe");

  @TempDir
  Path data;

  private FakePlayer player;
  private TestAdapt adapt;
  private AdaptPlayer profile;
  private SkillRegistry registry;
  private FakeInventory table;

  @BeforeEach
  void start() {
    TestPlatform platform = TestPlatform.builder(data).build();
    player = platform.player("Smith", 0.5D, 61D, 0.5D);
    adapt = TestAdapt.start(platform, List.of(SkillCrafting.class, SkillArchitect.class));
    profile = adapt.join(player);
    registry = adapt.core().getAdaptServer().getSkillRegistry();
    table = new FakeInventory();
    player.openMenu(table, player.location());
    player.setLevel(30);
  }

  @AfterEach
  void stop() {
    adapt.close();
    assertThat(adapt.handlerFailures()).isEmpty();
  }

  @Test
  void plainGearGainsAMasterworkBonusForItsMaterialAndLevels() {
    learn(1);
    ItemRef gear = FakeItem.tool("diamond_pickaxe", 1561).withDamage(100);
    load(gear, new FakeItem(DIAMOND, 3));

    PrepareResultEvent prepared = prepare();
    InventoryClickEvent take = take(ClickKind.LEFT, -1);

    assertThat(prepared.result()).isNotNull();
    assertThat(prepared.result().lore()).anySatisfy((String line) -> assertThat(line).contains("[1]"));
    assertThat(take.isCancelled()).isTrue();
    assertThat(player.level()).isEqualTo(25);
    assertThat(table.item(1).isEmpty()).isTrue();
    assertThat(table.item(2).amount()).isEqualTo(2);
    ItemRef reforged = reforged();
    assertThat(reforged.lore()).anySatisfy((String line) -> assertThat(line).contains("Masterwork"));
    assertThat(ItemDurability.maxDamage(reforged)).isGreaterThan(1561);
    if (ItemDurability.perStack()) {
      assertThat(ItemDurability.maxDamage(reforged) - reforged.damage()).isEqualTo(1461);
    }
  }

  @Test
  void theSamePieceAlwaysOffersTheSameRollsSoReloadingTheTableNeverRerollsForFree() {
    learn(3);
    load(FakeItem.tool("diamond_pickaxe", 1561), new FakeItem(DIAMOND, 1));

    List<String> first = prepare().result().lore();
    List<String> second = prepare().result().lore();

    assertThat(second).isEqualTo(first);
    assertThat(first.stream().filter((String line) -> line.contains("[")).count()).isEqualTo(3);
  }

  @Test
  void aNumberKeyPicksAnotherOfferedRollAndTheCostFallsWithLevel() {
    learn(3);
    load(FakeItem.tool("diamond_pickaxe", 1561), new FakeItem(DIAMOND, 1));

    prepare();
    take(ClickKind.NUMBER_KEY, 2);

    assertThat(player.level()).isEqualTo(27);
    assertThat(table.item(2).isEmpty()).isTrue();
    assertThat(reforged()).isNotNull();
  }

  @Test
  void plainGearReforgesOnlyOnce() {
    learn(3);
    load(FakeItem.tool("diamond_pickaxe", 1561), new FakeItem(DIAMOND, 2));
    prepare();
    take(ClickKind.LEFT, -1);
    ItemRef once = reforged();
    player.inventory().clear(player.inventory().first(PICKAXE));

    load(once, table.item(2));

    assertThat(prepare().result()).isNull();
    assertThat(take(ClickKind.LEFT, -1).isCancelled()).isFalse();
    assertThat(player.level()).isEqualTo(27);
  }

  @Test
  void theDurabilityLockKeepsTheBonus() {
    learn(3);
    ItemRef gear = FakeItem.tool("diamond_pickaxe", 1561);
    gear.data().set(DataKey.adapt("masterwork_roll", DataCodecs.BYTE), (byte) 1);
    load(gear, new FakeItem(DIAMOND, 2));
    prepare();
    take(ClickKind.LEFT, -1);
    ItemRef once = reforged();
    int bonus = ItemDurability.maxDamage(once);
    player.inventory().clear(player.inventory().first(PICKAXE));
    profile.getData().getPreferences().set("upgraded-crafting-grand-artisan", GrandArtisan.LOCK.id(),
        CraftingMasterwork.ReforgeLock.DURABILITY.name());

    load(once, table.item(2));
    prepare();
    take(ClickKind.LEFT, -1);

    assertThat(ItemDurability.maxDamage(reforged())).isEqualTo(bonus);
  }

  @Test
  void withoutEnoughLevelsNothingIsTaken() {
    learn(1);
    player.setLevel(4);
    ItemRef gear = FakeItem.tool("diamond_pickaxe", 1561);
    load(gear, new FakeItem(DIAMOND, 1));

    prepare();
    InventoryClickEvent take = take(ClickKind.LEFT, -1);

    assertThat(take.isCancelled()).isTrue();
    assertThat(table.item(1).isSimilar(gear)).isTrue();
    assertThat(table.item(2).amount()).isEqualTo(1);
    assertThat(player.level()).isEqualTo(4);
    assertThat(player.inventory().first(PICKAXE)).isNegative();
  }

  @Test
  void swappingThePieceAfterThePreviewGivesNothing() {
    learn(1);
    load(FakeItem.tool("diamond_pickaxe", 1561), new FakeItem(DIAMOND, 1));
    prepare();

    table.setItem(1, FakeItem.tool("diamond_pickaxe", 1561).withDamage(1500));
    InventoryClickEvent take = take(ClickKind.LEFT, -1);

    assertThat(take.isCancelled()).isTrue();
    assertThat(player.level()).isEqualTo(30);
    assertThat(player.inventory().first(PICKAXE)).isNegative();
  }

  @Test
  void theWrongMaterialOrAnyTemplateShowsNoReforge() {
    learn(1);
    load(FakeItem.tool("diamond_pickaxe", 1561), new FakeItem(Id.minecraft("iron_ingot"), 1));
    assertThat(prepare().result()).isNull();

    load(FakeItem.tool("diamond_pickaxe", 1561), new FakeItem(DIAMOND, 1));
    table.setItem(0, FakeItem.of("netherite_upgrade_smithing_template"));
    assertThat(prepare().result()).isNull();
    assertThat(take(ClickKind.LEFT, -1).isCancelled()).isFalse();
  }

  @Test
  void aPlayerWhoHasNotMutatedGetsNoReforge() {
    profile.getData().getSkillLine("crafting").setAdaptation(registry.getAdaptation("upgraded-crafting-grand-artisan"), 3);
    load(FakeItem.tool("diamond_pickaxe", 1561), new FakeItem(DIAMOND, 1));

    assertThat(prepare().result()).isNull();
  }

  @Test
  void eachGearPieceTakesOnlyItsOwnBaseMaterial() {
    assertThat(GrandArtisan.reforgeMaterial(Id.minecraft("wooden_sword"), Id.minecraft("birch_planks"))).isTrue();
    assertThat(GrandArtisan.reforgeMaterial(Id.minecraft("chainmail_boots"), Id.minecraft("iron_ingot"))).isTrue();
    assertThat(GrandArtisan.reforgeMaterial(Id.minecraft("netherite_axe"), DIAMOND)).isFalse();
    assertThat(GrandArtisan.reforgeMaterial(Id.minecraft("turtle_helmet"), Id.minecraft("turtle_scute"))).isTrue();
  }

  @Test
  void aBonusMovesOutWholeAndTheStrippedPieceNeverReforgesANewOne() {
    learn(3);
    CraftingMasterwork masterwork = registry.getSkill("crafting").getAdaptations().stream()
        .filter(CraftingMasterwork.class::isInstance).map(CraftingMasterwork.class::cast).findFirst().orElseThrow();
    ItemRef gear = masterwork.apply(FakeItem.tool("diamond_pickaxe", 1561).withDamage(100),
        new CraftingMasterwork.Bonus(0.2D, false));

    ItemRef stripped = CraftingMasterwork.strip(gear);
    load(stripped, new FakeItem(DIAMOND, 1));

    assertThat(CraftingMasterwork.bonus(gear).durabilityShare()).isGreaterThan(0.19D);
    assertThat(CraftingMasterwork.bonus(stripped)).isNull();
    assertThat(CraftingMasterwork.strip(stripped)).isNull();
    assertThat(ItemDurability.maxDamage(stripped)).isEqualTo(1561);
    assertThat(masterwork.apply(gear, new CraftingMasterwork.Bonus(0.1D, false))).isNull();
    assertThat(prepare().result()).isNull();
  }

  private void load(ItemRef gear, ItemRef material) {
    table.setItem(0, FakeItem.empty());
    table.setItem(1, gear);
    table.setItem(2, material);
  }

  private PrepareResultEvent prepare() {
    return adapt.post(new PrepareResultEvent(player, InventoryKind.SMITHING,
        List.of(table.item(0), table.item(1), table.item(2)), null));
  }

  private InventoryClickEvent take(ClickKind click, int hotbar) {
    return adapt.post(new InventoryClickEvent(player, InventoryKind.SMITHING, InventoryKind.SMITHING, 3, 3, SlotKind.RESULT,
        click, InventoryAction.PICKUP_ALL, hotbar, table.item(3), FakeItem.empty()));
  }

  private ItemRef reforged() {
    int slot = player.inventory().first(PICKAXE);
    return slot < 0 ? null : player.inventory().item(slot);
  }

  private void learn(int level) {
    profile.getData().setMasterXp(1_000_000D);
    for (String skill : List.of("crafting", "architect")) {
      PlayerSkillLine line = profile.getData().getSkillLine(skill);
      line.setXp(XP.getXpForLevel(20) + 1D);
      line.settleWithoutRewards();
    }
    assertThat(SkillMutations.mutate(profile.getData(), registry.getSkill("crafting"), registry.getSkill("architect"), "Artisan"))
        .isEqualTo(SkillMutations.Result.MUTATED);
    profile.getData().getSkillLine("crafting").setAdaptation(registry.getAdaptation("upgraded-crafting-grand-artisan"), level);
  }
}
