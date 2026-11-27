package art.arcane.adapt.content.adaptation.enchanting;

import art.arcane.adapt.engine.mutation.SkillMutations;
import art.arcane.adapt.engine.skill.SkillHost;
import art.arcane.adapt.engine.skill.SkillRegistry;
import art.arcane.adapt.engine.world.AdaptPlayer;
import art.arcane.adapt.engine.world.PlayerSkillLine;
import art.arcane.adapt.engine.xp.XP;
import art.arcane.adapt.content.adaptation.enchanting.upgraded.ArcaneHand;
import art.arcane.adapt.content.skill.SkillEnchanting;
import art.arcane.adapt.content.skill.SkillStealth;
import art.arcane.adapt.platform.event.inventory.InventoryAction;
import art.arcane.adapt.platform.event.inventory.InventoryClickEvent;
import art.arcane.adapt.platform.event.inventory.InventoryKind;
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

import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Grindstone Recovery next to Arcane Hand's library, and the curses a grindstone keeps. */
class EnchantingGrindstoneRecoveryRuntimeTest {
  private static final Id ENCHANTED_BOOK = Id.minecraft("enchanted_book");

  @TempDir
  Path data;

  private TestPlatform platform;
  private FakePlayer player;
  private TestAdapt adapt;
  private AdaptPlayer profile;
  private SkillRegistry registry;
  private EnchantingGrindstoneRecovery recovery;

  @BeforeEach
  void start() {
    platform = TestPlatform.builder(data).build();
    player = platform.player("Grinder", 0.5D, 61D, 0.5D);
    adapt = TestAdapt.start(platform, List.of(SkillEnchanting.class, SkillStealth.class));
    profile = adapt.join(player);
    registry = adapt.core().getAdaptServer().getSkillRegistry();
    recovery = (EnchantingGrindstoneRecovery) registry.getAdaptation(Id.adapt("enchanting/grindstone_recovery"));
    recovery.getConfig().recoverChanceBase = 1D;
    recovery.getConfig().maxRecoverChance = 1D;
  }

  @AfterEach
  void stop() {
    adapt.close();
    assertThat(adapt.handlerFailures()).isEmpty();
  }

  @Test
  void recoveryAloneTurnsAnEnchantmentIntoABook() {
    profile.getData().getSkillLine("enchanting").setAdaptation(recovery, 3);

    grind(FakeItem.tool("diamond_sword", 1561).enchant("sharpness", 4));

    assertThat(books()).hasSize(1);
  }

  @Test
  void recoveryNeverTurnsACurseTheGrindstoneKeepsIntoABook() {
    profile.getData().getSkillLine("enchanting").setAdaptation(recovery, 3);

    grind(FakeItem.tool("diamond_sword", 1561).enchant("binding_curse", 1));

    assertThat(books()).isEmpty();
  }

  @Test
  void oneGrindPaysOneBookWhenTheLibraryAlsoApplies() throws ReflectiveOperationException {
    mutate();
    ArcaneHand hand = SkillHost.of(registry.getSkill("enchanting")).getUpgrades().stream().filter(ArcaneHand.class::isInstance)
        .map(ArcaneHand.class::cast).findFirst().orElseThrow();
    profile.getData().getSkillLine("enchanting").setAdaptation(hand, 1);
    profile.getData().getSkillLine("enchanting").setAdaptation(recovery, 3);
    certainLibrary(hand);
    player.setLevel(10);
    FakeItem plain = FakeItem.of("book");
    plain.setAmount(3);
    player.inventory().setItem(9, plain);

    grind(FakeItem.tool("diamond_sword", 1561).enchant("sharpness", 4).enchant("unbreaking", 2));

    assertThat(books()).hasSize(1);
  }

  private void grind(ItemRef enchanted) {
    FakeInventory grindstone = new FakeInventory();
    grindstone.setItem(0, enchanted);
    player.openMenu(grindstone, null);
    adapt.post(new InventoryClickEvent(player, InventoryKind.GRINDSTONE, InventoryKind.GRINDSTONE, 2, 2,
        SlotKind.RESULT, ClickKind.LEFT, InventoryAction.PICKUP_ALL, -1, FakeItem.tool(enchanted.type().path(), 250),
        FakeItem.empty()));
    grindstone.setItem(0, null);
    platform.scheduler().advance(1);
  }

  private static void certainLibrary(ArcaneHand hand) throws ReflectiveOperationException {
    Field chance = ArcaneHand.Config.class.getDeclaredField("libraryChanceBase");
    chance.setAccessible(true);
    chance.setDouble(hand.getConfig(), 1D);
  }

  private List<ItemRef> books() {
    return player.inventory().contents().stream()
        .filter((ItemRef item) -> item != null && item.type().equals(ENCHANTED_BOOK))
        .toList();
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
}
