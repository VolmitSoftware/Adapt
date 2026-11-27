package art.arcane.adapt.content.adaptation.enchanting;

import art.arcane.adapt.AdaptCore;

import art.arcane.adapt.api.content.Adaptation;
import art.arcane.adapt.api.content.Skill;
import art.arcane.adapt.engine.skill.SkillRegistry;
import art.arcane.adapt.engine.world.AdaptPlayer;
import art.arcane.adapt.content.skill.SkillEnchanting;
import art.arcane.adapt.platform.data.DataCodecs;
import art.arcane.adapt.platform.data.DataKey;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.entity.DamageCause;
import art.arcane.adapt.platform.event.inventory.EnchantItemEvent;
import art.arcane.adapt.platform.event.inventory.InventoryAction;
import art.arcane.adapt.platform.event.inventory.InventoryClickEvent;
import art.arcane.adapt.platform.event.inventory.InventoryKind;
import art.arcane.adapt.platform.event.inventory.SlotKind;
import art.arcane.adapt.platform.event.player.PlayerDeathEvent;
import art.arcane.adapt.platform.event.player.PlayerRespawnEvent;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EnchantingRuntimeTest {
  private static final Id SHARPNESS = Id.minecraft("sharpness");
  private static final Id BINDING = Id.minecraft("binding_curse");

  @TempDir
  Path data;

  private TestPlatform platform;
  private FakePlayer player;
  private TestAdapt adapt;
  private AdaptPlayer profile;

  @BeforeEach
  void start() {
    platform = TestPlatform.builder(data).build();
    player = platform.player("Enchanter", 0.5D, 61D, 0.5D);
    adapt = TestAdapt.start(platform, List.of(SkillEnchanting.class));
    profile = adapt.join(player);
  }

  @AfterEach
  void stop() {
    adapt.close();
    assertTrue(adapt.handlerFailures().isEmpty(), () -> String.valueOf(adapt.handlerFailures()));
  }

  @Test
  void committedEnchantRecordsSkillStats() {
    platform.world().set(3, 61, 3, "enchanting_table");
    adapt.post(new EnchantItemEvent(player, platform.world().blockAt(3, 61, 3), FakeItem.of("diamond_sword"), 2,
        Map.of(SHARPNESS, 3, Id.minecraft("looting"), 2), 30));

    assertEquals(1D, profile.getData().getStat("enchanted.items"));
    assertEquals(5D, profile.getData().getStat("enchanted.power"));
    assertEquals(1D, profile.getData().getStat("enchanting.high.level"));
    assertEquals(30D, profile.getData().getStat("enchanting.total.levels"));
  }

  @Test
  void quickEnchantMovesBookEnchantsOntoTheClickedItem() {
    learn(EnchantingQuickEnchant.class, 7);
    FakeItem book = FakeItem.of("enchanted_book");
    book.setStoredEnchantment(SHARPNESS, 3);
    InventoryClickEvent click = new InventoryClickEvent(player, InventoryKind.CRAFTING, InventoryKind.PLAYER, 0, 36,
        SlotKind.QUICKBAR, ClickKind.LEFT, InventoryAction.SWAP_WITH_CURSOR, -1, FakeItem.of("diamond_sword"), book);

    adapt.post(click);

    assertTrue(click.isCancelled());
    assertEquals(3, click.currentItem().enchantmentLevel(SHARPNESS));
    assertTrue(click.cursor().isEmpty());
    assertEquals(1D, profile.getData().getStat("enchanting.quick-enchant.books-applied"));
  }

  @Test
  void curseCleansingTakesTheCursedInputAndReturnsACleanCopy() {
    learn(EnchantingCurseCleansing.class, 1);
    player.setSneaking(true);
    FakeInventory grindstone = new FakeInventory();
    FakeItem cursed = FakeItem.tool("diamond_sword", 1561).enchant("sharpness", 5).enchant("binding_curse", 1);
    grindstone.setItem(0, cursed);
    player.openMenu(grindstone, null);
    InventoryClickEvent click = new InventoryClickEvent(player, InventoryKind.GRINDSTONE, InventoryKind.GRINDSTONE, 2, 2,
        SlotKind.RESULT, ClickKind.LEFT, InventoryAction.PICKUP_ALL, -1, FakeItem.tool("diamond_sword", 1561), FakeItem.empty());

    adapt.post(click);

    assertTrue(click.isCancelled());
    assertTrue(grindstone.item(0) == null || grindstone.item(0).isEmpty());
    ItemRef returned = player.inventory().item(0);
    assertEquals(5, returned.enchantmentLevel(SHARPNESS));
    assertFalse(returned.enchantments().containsKey(BINDING));
    assertEquals(1D, profile.getData().getStat("enchanting.curse-cleansing.curses-removed"));
  }

  @Test
  void soulLinkKeepsTheMarkedItemThroughDeath() {
    learn(EnchantingSoulLink.class, 5);
    player.setLevel(30);
    FakeItem marked = FakeItem.tool("diamond_sword", 1561).enchant("sharpness", 5);
    marked.data().set(DataKey.adapt("soul-link-token", DataCodecs.STRING), "token-1");
    EnchantingSoulLink soulLink = (EnchantingSoulLink) adaptation(EnchantingSoulLink.class);
    soulLink.setStorage(player, "soul-link-token", "token-1");
    List<ItemRef> drops = new ArrayList<>(List.of(marked, FakeItem.of("dirt")));

    PlayerDeathEvent death = adapt.post(new PlayerDeathEvent(player, null, null, null, DamageCause.VOID, drops, 100, false,
        false, 0, 0, 0));

    assertEquals(1, death.drops().size());
    assertEquals(1D, profile.getData().getStat("enchanting.soul-link.items-saved"));

    adapt.post(new PlayerRespawnEvent(player, player.location(), false, false));
    platform.scheduler().advance(3);

    assertEquals(Id.minecraft("diamond_sword"), player.inventory().item(0).type());
    assertNull(soulLink.getStorageString(player, "soul-link-pending", null));
  }

  @Test
  void soulLinkStoresNothingWhenAnotherPluginKeepsTheInventoryLater() {
    learn(EnchantingSoulLink.class, 5);
    player.setLevel(30);
    FakeItem marked = FakeItem.tool("diamond_sword", 1561).enchant("sharpness", 5);
    marked.data().set(DataKey.adapt("soul-link-token", DataCodecs.STRING), "token-1");
    EnchantingSoulLink soulLink = (EnchantingSoulLink) adaptation(EnchantingSoulLink.class);
    soulLink.setStorage(player, "soul-link-token", "token-1");
    player.inventory().setItem(0, marked);
    adapt.bus().register(this, PlayerDeathEvent.class, EventPriority.HIGHEST,
        (PlayerDeathEvent keep) -> keep.setKeepInventory(true));
    List<ItemRef> drops = new ArrayList<>(List.of(marked.copy(), FakeItem.of("dirt")));

    adapt.post(new PlayerDeathEvent(player, null, null, null, DamageCause.VOID, drops, 100, false, false, 0, 0, 0));
    adapt.post(new PlayerRespawnEvent(player, player.location(), false, false));
    platform.scheduler().advance(3);

    assertNull(soulLink.getStorageString(player, "soul-link-pending", null));
    assertEquals(1L, player.inventory().contents().stream()
        .filter((ItemRef item) -> item.type().equals(Id.minecraft("diamond_sword"))).count());
    assertEquals(30, player.level());
  }

  private Adaptation<?> adaptation(Class<? extends Adaptation<?>> type) {
    Skill<?> skill = AdaptCore.get().getAdaptServer().getSkillRegistry().getSkill("enchanting");
    for (Adaptation<?> adaptation : skill.getAdaptations()) {
      if (type.isInstance(adaptation)) {
        return adaptation;
      }
    }
    throw new AssertionError("No adaptation " + type.getSimpleName());
  }

  private void learn(Class<? extends Adaptation<?>> type, int level) {
    profile.getData().getSkillLine("enchanting").setAdaptation(adaptation(type), level);
  }
}
