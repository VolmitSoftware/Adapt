package art.arcane.adapt.content.adaptation.enchanting.upgraded;

import art.arcane.adapt.AdaptCore;
import art.arcane.adapt.api.adaptation.Adaptation;
import art.arcane.adapt.api.mutation.MutatedSkill;
import art.arcane.adapt.api.mutation.SkillMutations;
import art.arcane.adapt.api.skill.SkillRegistry;
import art.arcane.adapt.api.world.AdaptPlayer;
import art.arcane.adapt.api.world.PlayerSkillLine;
import art.arcane.adapt.api.xp.XP;
import art.arcane.adapt.api.preference.CommonPreferences;
import art.arcane.adapt.api.preference.PlayerPreferences;
import art.arcane.adapt.content.adaptation.enchanting.EnchantingAscension;
import art.arcane.adapt.content.adaptation.enchanting.EnchantingGrindstoneRecovery;
import art.arcane.adapt.content.adaptation.enchanting.EnchantingGrindstones;
import art.arcane.adapt.content.skill.SkillEnchanting;
import art.arcane.adapt.content.skill.SkillStealth;
import art.arcane.adapt.platform.entity.Hand;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.entity.DamageCause;
import art.arcane.adapt.platform.event.inventory.EnchantOffer;
import art.arcane.adapt.platform.event.inventory.InventoryAction;
import art.arcane.adapt.platform.event.inventory.InventoryClickEvent;
import art.arcane.adapt.platform.event.inventory.InventoryCloseEvent;
import art.arcane.adapt.platform.event.inventory.InventoryKind;
import art.arcane.adapt.platform.event.inventory.PrepareAnvilEvent;
import art.arcane.adapt.platform.event.inventory.PrepareItemEnchantEvent;
import art.arcane.adapt.platform.event.inventory.PrepareResultEvent;
import art.arcane.adapt.platform.event.inventory.SlotKind;
import art.arcane.adapt.platform.event.player.InteractAction;
import art.arcane.adapt.platform.event.player.PlayerDeathEvent;
import art.arcane.adapt.platform.event.player.PlayerDropItemEvent;
import art.arcane.adapt.platform.event.player.PlayerExpChangeEvent;
import art.arcane.adapt.platform.event.player.PlayerInteractEvent;
import art.arcane.adapt.platform.event.player.PlayerRespawnEvent;
import art.arcane.adapt.platform.event.player.UseResult;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.menu.ClickKind;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.world.BlockRef;
import art.arcane.adapt.testkit.FakeInventory;
import art.arcane.adapt.testkit.FakeItem;
import art.arcane.adapt.testkit.FakeItemEntity;
import art.arcane.adapt.testkit.FakePlayer;
import art.arcane.adapt.testkit.TestAdapt;
import art.arcane.adapt.testkit.TestPlatform;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class EnchantingUpgradesRuntimeTest {
  private static final Id SHARPNESS = Id.minecraft("sharpness");
  private static final Id SMITE = Id.minecraft("smite");
  private static final Id UNBREAKING = Id.minecraft("unbreaking");
  private static final Id BINDING = Id.minecraft("binding_curse");
  private static final Id ENCHANTED_BOOK = Id.minecraft("enchanted_book");

  @TempDir
  Path data;

  private TestPlatform platform;
  private FakePlayer player;
  private TestAdapt adapt;
  private AdaptPlayer profile;
  private SkillRegistry registry;

  @BeforeEach
  void start() {
    platform = TestPlatform.builder(data).build();
    player = platform.player("Runesmith", 0.5D, 61D, 0.5D);
    adapt = TestAdapt.start(platform, List.of(SkillEnchanting.class, SkillStealth.class));
    profile = adapt.join(player);
    registry = adapt.core().getAdaptServer().getSkillRegistry();
  }

  @AfterEach
  void stop() {
    adapt.close();
    assertThat(adapt.handlerFailures()).isEmpty();
  }

  @Test
  void theMutatedSkillOffersTheThreeUpgrades() {
    mutate();
    MutatedSkill mutated = registry.getMutatedSkill("mutated-enchanting-stealth");

    assertThat(mutated.getAdaptations()).extracting(Adaptation::getName).startsWith(
        "upgraded-enchanting-arcane-hand", "upgraded-enchanting-fate-weaver", "upgraded-enchanting-soulforged",
        "upgraded-stealth-umbral-form", "upgraded-stealth-death-mark", "upgraded-stealth-phantom-decoy");
  }

  @Test
  void arcaneHandStaysInertWithoutAMutation() {
    profile.getData().getSkillLine("enchanting").setAdaptation(upgrade(ArcaneHand.class), 1);
    InventoryClickEvent click = applyBook(FakeItem.of("diamond_sword"), book(SHARPNESS, 3));

    assertThat(click.isCancelled()).isFalse();
    assertThat(click.currentItem().enchantments()).isEmpty();
  }

  @Test
  void arcaneHandAppliesABookByHand() {
    mutate();
    learn(ArcaneHand.class, 1);

    InventoryClickEvent click = applyBook(FakeItem.of("diamond_sword"), book(SHARPNESS, 3));

    assertThat(click.isCancelled()).isTrue();
    assertThat(click.currentItem().enchantmentLevel(SHARPNESS)).isEqualTo(3);
    assertThat(click.cursor().isEmpty()).isTrue();
  }

  @Test
  void arcaneHandMovesAnEnchantmentBetweenItemsWithoutAnAnvil() {
    mutate();
    ArcaneHand hand = learn(ArcaneHand.class, 1);
    player.setLevel(10);
    FakeItem source = FakeItem.tool("diamond_sword", 1561).enchant("sharpness", 4);

    InventoryClickEvent click = adapt.post(new InventoryClickEvent(player, InventoryKind.CRAFTING, InventoryKind.PLAYER, 0, 36,
        SlotKind.QUICKBAR, ClickKind.RIGHT, InventoryAction.SWAP_WITH_CURSOR, -1, FakeItem.tool("iron_sword", 250), source));

    assertThat(click.isCancelled()).isTrue();
    assertThat(click.currentItem().enchantmentLevel(SHARPNESS)).isEqualTo(4);
    assertThat(click.cursor().type()).isEqualTo(Id.minecraft("diamond_sword"));
    assertThat(click.cursor().enchantments()).doesNotContainKey(SHARPNESS);
    assertThat(player.level()).isEqualTo(10 - hand.getMoveCost(1));
  }

  @Test
  void arcaneHandRefusesToMoveAnEnchantmentThatConflictsWithTheTarget() {
    mutate();
    learn(ArcaneHand.class, 1);
    platform.items().conflicting(SHARPNESS, SMITE);
    player.setLevel(10);
    FakeItem source = FakeItem.tool("diamond_sword", 1561).enchant("smite", 4);

    InventoryClickEvent click = move(FakeItem.tool("iron_sword", 250).enchant("sharpness", 2), source);

    assertThat(click.currentItem().enchantments()).doesNotContainKey(SMITE);
    assertThat(click.cursor().enchantmentLevel(SMITE)).isEqualTo(4);
    assertThat(player.level()).isEqualTo(10);
  }

  @Test
  void arcaneHandRefusesToMoveAnEnchantmentPastThePowerLimit() {
    mutate();
    ArcaneHand hand = learn(ArcaneHand.class, 1);
    player.setLevel(10);
    FakeItem target = FakeItem.tool("iron_sword", 250).enchant("unbreaking", 3).enchant("looting", 3);
    FakeItem source = FakeItem.tool("diamond_sword", 1561).enchant("sharpness", 4);
    assertThat(3 + 3 + 4).isGreaterThan(hand.getPowerLimit(1));

    InventoryClickEvent click = move(target, source);

    assertThat(click.isCancelled()).isTrue();
    assertThat(click.currentItem().enchantments()).doesNotContainKey(SHARPNESS);
    assertThat(click.cursor().enchantmentLevel(SHARPNESS)).isEqualTo(4);
    assertThat(player.level()).isEqualTo(10);
  }

  @Test
  void arcaneHandSplitsADroppedBookWhileSneakingWithNoAnvilInSight() {
    mutate();
    ArcaneHand hand = learn(ArcaneHand.class, 1);
    player.setLevel(10);
    player.setSneaking(true);
    FakeItem book = book(SHARPNESS, 3);
    book.setStoredEnchantment(UNBREAKING, 2);
    FakeItemEntity dropped = platform.world().add(new FakeItemEntity(player.location(), book));

    adapt.post(new PlayerDropItemEvent(player, dropped));

    assertThat(dropped.item().storedEnchantments()).hasSize(1);
    assertThat(player.level()).isEqualTo(10 - hand.getSplitCost(1));
  }

  @Test
  void fateWeaverRaisesAndRevealsEveryOffer() {
    mutate();
    FateWeaver weaver = learn(FateWeaver.class, 1);
    List<EnchantOffer> offers = new ArrayList<>(List.of(
        new EnchantOffer(SHARPNESS, 1, 5), new EnchantOffer(UNBREAKING, 1, 10), new EnchantOffer(SHARPNESS, 2, 15)));

    adapt.post(new PrepareItemEnchantEvent(player, table(), FakeItem.of("diamond_sword"), 0, offers));

    int power = weaver.getShelfPower(1);
    assertThat(offers).extracting(EnchantOffer::cost).containsExactly(
        Math.min(30, 5 + power), Math.min(30, 10 + power), Math.min(30, 15 + power));
    assertThat(player.actionBars()).isNotEmpty();
    assertThat(player.actionBars().getLast()).contains("sharpness").contains("unbreaking");
    assertThat(player.actionBars().getLast().split("\\|")).hasSize(3);
  }

  @Test
  void fateWeaverRerollsTheOffersAtTheTable() {
    mutate();
    FateWeaver weaver = learn(FateWeaver.class, 1);
    player.setLevel(5);
    player.setSneaking(true);
    player.setEnchantmentSeed(42);
    player.inventory().setItem(0, new FakeItem(Id.minecraft("lapis_lazuli"), 8));

    PlayerInteractEvent click = adapt.post(new PlayerInteractEvent(player, InteractAction.RIGHT_CLICK_BLOCK, Hand.MAIN_HAND,
        FakeItem.empty(), table(), null, null, UseResult.DEFAULT, UseResult.DEFAULT));

    assertThat(click.isCancelled()).isTrue();
    assertThat(player.inventory().item(0).amount()).isEqualTo(8 - weaver.getLapisCost(1));
    assertThat(player.level()).isEqualTo(4);
    assertThat(player.hasCooldown(Id.minecraft("enchanting_table"))).isTrue();
  }

  @Test
  void soulforgedLinksGearShedsItsCursesAndKeepsItThroughDeath() {
    mutate();
    learn(Soulforged.class, 1);
    player.setLevel(30);
    player.setSneaking(true);
    player.inventory().setMainHand(FakeItem.tool("diamond_sword", 1561).enchant("sharpness", 2).enchant("binding_curse", 1));
    platform.world().set(3, 61, 3, "anvil");

    adapt.post(new PlayerInteractEvent(player, InteractAction.RIGHT_CLICK_BLOCK, Hand.MAIN_HAND, player.inventory().mainHand(),
        platform.world().blockAt(3, 61, 3), null, null, UseResult.DEFAULT, UseResult.DEFAULT));

    ItemRef linked = player.inventory().mainHand();
    assertThat(linked.enchantments()).doesNotContainKey(BINDING).containsEntry(SHARPNESS, 2);
    List<ItemRef> drops = new ArrayList<>(List.of(linked, FakeItem.of("dirt")));
    player.inventory().setMainHand(FakeItem.empty());

    PlayerDeathEvent death = adapt.post(new PlayerDeathEvent(player, null, null, null, DamageCause.VOID, drops, 100, false,
        false, 0, 0, 0));
    assertThat(death.drops()).hasSize(1);

    adapt.post(new PlayerRespawnEvent(player, player.location(), false, false));
    platform.scheduler().advance(3);

    assertThat(player.inventory().contents()).extracting(ItemRef::type).contains(Id.minecraft("diamond_sword"));
  }

  @Test
  void soulforgedDoesNotDuplicateTheLinkedItemWhenAnotherPluginKeepsTheInventory() {
    mutate();
    learn(Soulforged.class, 1);
    player.setLevel(30);
    player.setSneaking(true);
    player.inventory().setMainHand(FakeItem.tool("diamond_sword", 1561).enchant("sharpness", 2));
    platform.world().set(3, 61, 3, "anvil");
    adapt.post(new PlayerInteractEvent(player, InteractAction.RIGHT_CLICK_BLOCK, Hand.MAIN_HAND, player.inventory().mainHand(),
        platform.world().blockAt(3, 61, 3), null, null, UseResult.DEFAULT, UseResult.DEFAULT));
    adapt.bus().register(this, PlayerDeathEvent.class, EventPriority.HIGHEST,
        (PlayerDeathEvent keep) -> keep.setKeepInventory(true));
    int levels = player.level();
    List<ItemRef> drops = new ArrayList<>(List.of(player.inventory().mainHand().copy(), FakeItem.of("dirt")));

    adapt.post(new PlayerDeathEvent(player, null, null, null, DamageCause.VOID, drops, 100, false, false, 0, 0, 0));
    adapt.post(new PlayerRespawnEvent(player, player.location(), false, false));
    platform.scheduler().advance(3);

    assertThat(player.inventory().contents()).extracting(ItemRef::type)
        .filteredOn(Id.minecraft("diamond_sword")::equals).hasSize(1);
    assertThat(player.level()).isEqualTo(levels);
  }

  @Test
  void soulforgedGrowsLinkedGearAsXpIsCollected() {
    mutate();
    Soulforged soulforged = learn(Soulforged.class, 1);
    player.setSneaking(true);
    player.inventory().setMainHand(FakeItem.tool("diamond_sword", 1561).enchant("sharpness", 1));
    platform.world().set(3, 61, 3, "anvil");
    adapt.post(new PlayerInteractEvent(player, InteractAction.RIGHT_CLICK_BLOCK, Hand.MAIN_HAND, player.inventory().mainHand(),
        platform.world().blockAt(3, 61, 3), null, null, UseResult.DEFAULT, UseResult.DEFAULT));
    int needed = (int) Math.ceil(120D / soulforged.getChargeRate(1));

    adapt.post(new PlayerExpChangeEvent(player, needed));

    int maxSharpness = AdaptCore.platform().host().registries().enchantmentMaxLevel(SHARPNESS);
    assertThat(player.inventory().mainHand().enchantmentLevel(SHARPNESS)).isEqualTo(Math.min(maxSharpness, 2));
  }

  @Test
  void soulforgedDoesNothingWithoutAMutation() {
    profile.getData().getSkillLine("enchanting").setAdaptation(upgrade(Soulforged.class), 1);
    player.setSneaking(true);
    player.inventory().setMainHand(FakeItem.tool("diamond_sword", 1561).enchant("binding_curse", 1));
    platform.world().set(3, 61, 3, "anvil");

    PlayerInteractEvent click = adapt.post(new PlayerInteractEvent(player, InteractAction.RIGHT_CLICK_BLOCK, Hand.MAIN_HAND,
        player.inventory().mainHand(), platform.world().blockAt(3, 61, 3), null, null, UseResult.DEFAULT, UseResult.DEFAULT));

    assertThat(click.isCancelled()).isFalse();
    assertThat(player.inventory().mainHand().enchantments()).containsKey(BINDING);
  }

  @Test
  void soulforgedAscendsAnEnchantmentItGrewPastItsMaximum() {
    mutate();
    Soulforged soulforged = learn(Soulforged.class, 1);
    linkAndGrow(soulforged, 4);
    player.setLevel(40);

    PlayerInteractEvent click = punchTable();

    ItemRef ascended = player.inventory().mainHand();
    assertThat(click.isCancelled()).isTrue();
    assertThat(ascended.enchantmentLevel(SHARPNESS)).isEqualTo(6);
    assertThat(ascended.lore()).anySatisfy((String line) -> assertThat(line).contains("Ascended").contains("sharpness"));
    assertThat(player.level()).isEqualTo(40 - soulforged.getAscensionCost(1));
  }

  @Test
  void soulforgedRefusesToAscendGearItDidNotGrow() {
    mutate();
    learn(Soulforged.class, 3);
    player.setLevel(40);
    player.setSneaking(true);
    player.inventory().setMainHand(FakeItem.tool("diamond_sword", 1561).enchant("sharpness", 5));
    linkHeld();

    punchTable();

    assertThat(player.inventory().mainHand().enchantmentLevel(SHARPNESS)).isEqualTo(5);
    assertThat(player.level()).isEqualTo(40);
  }

  @Test
  void soulforgedRefusesASecondAscensionAtLevelOneAndWithoutTheLevels() {
    mutate();
    Soulforged soulforged = learn(Soulforged.class, 1);
    linkAndGrow(soulforged, 4);
    player.setLevel(soulforged.getAscensionCost(1) - 1);

    punchTable();

    assertThat(player.inventory().mainHand().enchantmentLevel(SHARPNESS)).isEqualTo(5);
    assertThat(player.level()).isEqualTo(soulforged.getAscensionCost(1) - 1);

    ItemRef sword = player.inventory().mainHand().copy();
    sword.setEnchantment(UNBREAKING, 5);
    EnchantingAscension.markGrown(sword, UNBREAKING);
    player.inventory().setMainHand(sword);
    player.setLevel(80);
    punchTable();
    punchTable();

    assertThat(EnchantingAscension.ascended(player.inventory().mainHand())).hasSize(1);
    assertThat(player.level()).isEqualTo(80 - soulforged.getAscensionCost(1));
  }

  @Test
  void anAscendedLevelSleepsWithAnyoneWithoutTheUpgradeAndWakesForTheHolder() {
    mutate();
    Soulforged soulforged = learn(Soulforged.class, 1);
    linkAndGrow(soulforged, 4);
    player.setLevel(40);
    punchTable();
    ItemRef ascended = player.inventory().mainHand().copy();
    FakePlayer buyer = platform.player("Buyer", 4.5D, 61D, 0.5D);
    adapt.join(buyer);
    buyer.inventory().setMainHand(ascended);

    soulforged.settleInventory(buyer);
    ItemRef traded = buyer.inventory().mainHand();
    soulforged.settleInventory(player);

    assertThat(traded.enchantmentLevel(SHARPNESS)).isEqualTo(5);
    assertThat(player.inventory().mainHand().enchantmentLevel(SHARPNESS)).isEqualTo(6);

    player.inventory().setMainHand(traded.copy());
    soulforged.settleInventory(player);
    assertThat(player.inventory().mainHand().enchantmentLevel(SHARPNESS)).isEqualTo(6);
  }

  @Test
  void aDroppedAscendedItemFallsAsleep() {
    mutate();
    Soulforged soulforged = learn(Soulforged.class, 1);
    linkAndGrow(soulforged, 4);
    player.setLevel(40);
    punchTable();
    FakeItemEntity dropped = platform.world().add(new FakeItemEntity(player.location(), player.inventory().mainHand().copy()));
    player.inventory().setMainHand(FakeItem.empty());

    adapt.post(new PlayerDropItemEvent(player, dropped));

    assertThat(dropped.item().enchantmentLevel(SHARPNESS)).isEqualTo(5);
  }

  @Test
  void anAnvilCannotCopyAnAscendedLevelAndAGrindstoneEndsIt() {
    mutate();
    Soulforged soulforged = learn(Soulforged.class, 1);
    linkAndGrow(soulforged, 4);
    player.setLevel(40);
    punchTable();
    ItemRef ascended = player.inventory().mainHand().copy();
    FakeItem copied = FakeItem.tool("iron_sword", 250).enchant("sharpness", 6);

    PrepareAnvilEvent anvil = adapt.post(new PrepareAnvilEvent(player, InventoryKind.ANVIL,
        List.of(FakeItem.tool("iron_sword", 250), ascended), copied, null, 5));
    ItemRef ground = ascended.copy();
    ground.removeEnchantment(SHARPNESS);
    PrepareResultEvent grindstone = adapt.post(new PrepareResultEvent(player, InventoryKind.GRINDSTONE,
        List.of(ascended, FakeItem.empty()), ground));
    ItemRef reenchanted = grindstone.result().copy();
    reenchanted.setEnchantment(SHARPNESS, 5);
    player.inventory().setMainHand(reenchanted);
    soulforged.settleInventory(player);

    assertThat(anvil.result().enchantmentLevel(SHARPNESS)).isEqualTo(5);
    assertThat(EnchantingAscension.hasAscension(grindstone.result())).isFalse();
    assertThat(grindstone.result().lore()).noneSatisfy((String line) -> assertThat(line).contains("Ascended"));
    assertThat(player.inventory().mainHand().enchantmentLevel(SHARPNESS)).isEqualTo(5);
  }

  @Test
  void arcaneHandPullsTheBestEnchantmentOfAGroundItemOntoABook() {
    mutate();
    ArcaneHand hand = learn(ArcaneHand.class, 1);
    hand.getConfig().libraryChanceBase = 1D;
    quietRecovery();
    player.setLevel(10);
    player.inventory().setItem(9, plainBooks(3));

    grind(FakeItem.tool("diamond_sword", 1561).enchant("sharpness", 4).enchant("unbreaking", 2).enchant("binding_curse", 1));

    assertThat(libraryBooks()).singleElement().satisfies((ItemRef book) -> assertThat(book.storedEnchantments())
        .containsExactly(Map.entry(SHARPNESS, 4)));
    assertThat(player.inventory().item(9).amount()).isEqualTo(2);
    assertThat(player.level()).isEqualTo(10 - hand.getLibraryCost(1));
    assertThat(player.hasCooldown(Id.minecraft("grindstone"))).isTrue();
  }

  @Test
  void arcaneHandNeverPullsAnEnchantmentThatCameFromAGrindstoneBookAgain() {
    mutate();
    ArcaneHand hand = learn(ArcaneHand.class, 1);
    hand.getConfig().libraryChanceBase = 1D;
    hand.getConfig().libraryCooldownTicks = 0;
    quietRecovery();
    player.setLevel(20);
    player.inventory().setItem(9, plainBooks(3));
    grind(FakeItem.tool("diamond_sword", 1561).enchant("sharpness", 4));
    ItemRef pulled = libraryBooks().getFirst();
    InventoryClickEvent apply = applyBook(FakeItem.tool("iron_sword", 250), pulled);
    clearInventoryOf(ENCHANTED_BOOK);
    int levels = player.level();

    grind(apply.currentItem());

    assertThat(libraryBooks()).isEmpty();
    assertThat(player.inventory().item(9).amount()).isEqualTo(2);
    assertThat(player.level()).isEqualTo(levels);
  }

  @Test
  void arcaneHandMakesNoGrindstoneBookWithoutAPlainBookOrTheLevels() {
    mutate();
    ArcaneHand hand = learn(ArcaneHand.class, 1);
    hand.getConfig().libraryChanceBase = 1D;
    quietRecovery();
    player.setLevel(10);

    grind(FakeItem.tool("diamond_sword", 1561).enchant("sharpness", 4));

    assertThat(libraryBooks()).isEmpty();
    assertThat(player.level()).isEqualTo(10);
    assertThat(player.actionBars()).isNotEmpty();
  }

  @Test
  void arcaneHandsGrindstoneBookStaysInertWithoutAMutation() {
    ArcaneHand hand = learn(ArcaneHand.class, 1);
    hand.getConfig().libraryChanceBase = 1D;
    player.setLevel(10);
    player.inventory().setItem(9, plainBooks(3));

    grind(FakeItem.tool("diamond_sword", 1561).enchant("sharpness", 4));

    assertThat(libraryBooks()).isEmpty();
    assertThat(player.inventory().item(9).amount()).isEqualTo(3);
  }

  @Test
  void aHolderBelowTheItemsAscensionCountKeepsItAsleep() {
    mutate();
    Soulforged soulforged = learn(Soulforged.class, 1);
    ItemRef sword = FakeItem.tool("diamond_sword", 1561).enchant("sharpness", 5).enchant("unbreaking", 5);
    EnchantingAscension.ascend(sword, SHARPNESS);
    EnchantingAscension.ascend(sword, UNBREAKING);
    player.inventory().setMainHand(sword);

    soulforged.settleInventory(player);
    assertThat(player.inventory().mainHand().enchantmentLevel(SHARPNESS)).isEqualTo(5);

    learn(Soulforged.class, 3);
    soulforged.settleInventory(player);
    assertThat(player.inventory().mainHand().enchantmentLevel(SHARPNESS)).isEqualTo(6);
    assertThat(player.inventory().mainHand().enchantmentLevel(UNBREAKING)).isEqualTo(6);
  }

  @Test
  void ascendedGearLeftInAContainerFallsAsleepWhenItCloses() {
    mutate();
    Soulforged soulforged = learn(Soulforged.class, 1);
    linkAndGrow(soulforged, 4);
    player.setLevel(40);
    punchTable();
    FakeInventory chest = new FakeInventory();
    chest.setItem(0, player.inventory().mainHand().copy());
    player.inventory().setMainHand(FakeItem.empty());
    player.openMenu(chest, null);

    adapt.post(new InventoryCloseEvent(player, InventoryKind.CHEST));

    assertThat(chest.item(0).enchantmentLevel(SHARPNESS)).isEqualTo(5);
  }

  @Test
  void aGrindstoneResultLosesItsLibraryMarks() {
    ItemRef marked = FakeItem.tool("iron_sword", 250).enchant("sharpness", 4);
    EnchantingGrindstones.carryLibraryMarks(marked, Set.of(SHARPNESS));
    ItemRef ground = marked.copy();
    ground.removeEnchantment(SHARPNESS);

    PrepareResultEvent grindstone = adapt.post(new PrepareResultEvent(player, InventoryKind.GRINDSTONE,
        List.of(marked, FakeItem.empty()), ground));

    assertThat(EnchantingGrindstones.libraryEnchants(grindstone.result())).isEmpty();
  }

  private void linkAndGrow(Soulforged soulforged, int sharpness) {
    player.setSneaking(true);
    player.inventory().setMainHand(FakeItem.tool("diamond_sword", 1561).enchant("sharpness", sharpness));
    linkHeld();
    adapt.post(new PlayerExpChangeEvent(player, (int) Math.ceil(120D / soulforged.getChargeRate(1))));
    assertThat(player.inventory().mainHand().enchantmentLevel(SHARPNESS)).isEqualTo(5);
  }

  private void linkHeld() {
    platform.world().set(3, 61, 3, "anvil");
    adapt.post(new PlayerInteractEvent(player, InteractAction.RIGHT_CLICK_BLOCK, Hand.MAIN_HAND, player.inventory().mainHand(),
        platform.world().blockAt(3, 61, 3), null, null, UseResult.DEFAULT, UseResult.DEFAULT));
  }

  private PlayerInteractEvent punchTable() {
    player.setSneaking(true);
    return adapt.post(new PlayerInteractEvent(player, InteractAction.LEFT_CLICK_BLOCK, Hand.MAIN_HAND, player.inventory().mainHand(),
        table(), null, null, UseResult.DEFAULT, UseResult.DEFAULT));
  }

  private void grind(ItemRef enchanted) {
    FakeInventory grindstone = new FakeInventory();
    grindstone.setItem(0, enchanted);
    player.openMenu(grindstone, null);
    adapt.post(new InventoryClickEvent(player, InventoryKind.GRINDSTONE, InventoryKind.GRINDSTONE, 2, 2,
        SlotKind.RESULT, ClickKind.LEFT, InventoryAction.PICKUP_ALL, -1, FakeItem.tool(enchanted.type().path(), 250), FakeItem.empty()));
    grindstone.setItem(0, null);
    platform.scheduler().advance(1);
  }

  private static FakeItem plainBooks(int amount) {
    FakeItem books = FakeItem.of("book");
    books.setAmount(amount);
    return books;
  }

  private void quietRecovery() {
    Adaptation<?> recovery = registry.getAdaptation("enchanting-grindstone-recovery");
    PlayerPreferences.set(recovery, profile, EnchantingGrindstoneRecovery.BOOKS, CommonPreferences.Toggle.OFF);
    PlayerPreferences.set(recovery, profile, EnchantingGrindstoneRecovery.BONUS_XP, CommonPreferences.Toggle.OFF);
  }

  private List<ItemRef> libraryBooks() {
    return player.inventory().contents().stream()
        .filter((ItemRef item) -> item != null && item.type().equals(ENCHANTED_BOOK))
        .toList();
  }

  private void clearInventoryOf(Id type) {
    for (int slot = 0; slot < player.inventory().size(); slot++) {
      ItemRef item = player.inventory().item(slot);
      if (item != null && item.type().equals(type)) {
        player.inventory().setItem(slot, null);
      }
    }
  }

  private InventoryClickEvent move(ItemRef target, ItemRef source) {
    return adapt.post(new InventoryClickEvent(player, InventoryKind.CRAFTING, InventoryKind.PLAYER, 0, 36,
        SlotKind.QUICKBAR, ClickKind.RIGHT, InventoryAction.SWAP_WITH_CURSOR, -1, target, source));
  }

  private InventoryClickEvent applyBook(ItemRef target, ItemRef book) {
    return adapt.post(new InventoryClickEvent(player, InventoryKind.CRAFTING, InventoryKind.PLAYER, 0, 36,
        SlotKind.QUICKBAR, ClickKind.LEFT, InventoryAction.SWAP_WITH_CURSOR, -1, target, book));
  }

  private BlockRef table() {
    platform.world().set(3, 61, 3, "enchanting_table");
    return platform.world().blockAt(3, 61, 3);
  }

  private static FakeItem book(Id enchant, int level) {
    FakeItem book = FakeItem.of(ENCHANTED_BOOK.path());
    book.setStoredEnchantment(enchant, level);
    return book;
  }

  private void mutate() {
    profile.getData().setMasterXp(1_000_000D);
    for (String skill : List.of("enchanting", "stealth")) {
      PlayerSkillLine line = profile.getData().getSkillLine(skill);
      line.setXp(XP.getXpForLevel(20) + 1D);
      line.settleWithoutRewards();
    }
    assertThat(SkillMutations.mutate(profile.getData(), registry.getSkill("enchanting"), registry.getSkill("stealth"), "Runesmith"))
        .isEqualTo(SkillMutations.Result.MUTATED);
  }

  private <A extends Adaptation<?>> A learn(Class<A> type, int level) {
    A upgrade = upgrade(type);
    profile.getData().getSkillLine("enchanting").setAdaptation(upgrade, level);
    return upgrade;
  }

  private <A extends Adaptation<?>> A upgrade(Class<A> type) {
    return registry.getSkill("enchanting").getUpgrades().stream().filter(type::isInstance).map(type::cast).findFirst()
        .orElseThrow();
  }
}
