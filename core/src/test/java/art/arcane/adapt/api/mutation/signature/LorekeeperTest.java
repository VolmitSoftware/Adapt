package art.arcane.adapt.api.mutation.signature;

import art.arcane.adapt.api.mutation.blocks.Recipe;
import art.arcane.adapt.api.xp.XpProvenance;
import art.arcane.adapt.content.skill.SkillDiscovery;
import art.arcane.adapt.content.skill.SkillEnchanting;
import art.arcane.adapt.platform.entity.InventoryRef;
import art.arcane.adapt.platform.event.player.PlayerLootGenerateEvent;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.world.BlockRef;
import art.arcane.adapt.testkit.FakeItem;
import art.arcane.adapt.testkit.FakePlayer;
import art.arcane.adapt.testkit.MutationKit;
import art.arcane.adapt.testkit.TestAdapt;
import art.arcane.adapt.testkit.TestPlatform;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mockStatic;

/** Discovery and Enchanting, either leading, replace Convergence with Lorekeeper. */
class LorekeeperTest {
  private static final Id SHARPNESS = Id.minecraft("sharpness");
  private static final Id UNBREAKING = Id.minecraft("unbreaking");
  private static final Id MENDING = Id.minecraft("mending");
  private static final Id SMITE = Id.minecraft("smite");

  @TempDir
  Path data;

  private TestPlatform platform;
  private TestAdapt adapt;
  private FakePlayer player;
  private MutationKit kit;
  private InventoryRef chest;

  @BeforeEach
  void start() {
    platform = TestPlatform.builder(data).build();
    player = platform.player("Seeker", 0.5D, 61D, 0.5D);
    adapt = TestAdapt.start(platform, List.of(SkillDiscovery.class, SkillEnchanting.class));
    kit = new MutationKit(adapt, adapt.join(player));
    platform.world().set(4, 61, 4, "chest");
    chest = platform.world().blockAt(4, 61, 4).container();
  }

  @AfterEach
  void stop() {
    adapt.close();
    assertThat(adapt.handlerFailures()).isEmpty();
  }

  @Test
  void theFirstLevelRaisesOneEnchantmentOneLevelWithEitherLead() {
    kit.mutate("discovery", "enchanting");
    kit.learn("discovery", "enchanting", Recipe.CONVERGENCE, 1);
    assertThat(kit.adaptation("discovery", "enchanting", Recipe.CONVERGENCE).getSignature()).isInstanceOf(Lorekeeper.class);
    assertThat(kit.mutated("enchanting", "discovery").getMutationAdaptation(Recipe.CONVERGENCE).getSignature())
        .isInstanceOf(Lorekeeper.class);

    open();

    assertThat(raised()).isEqualTo(1);
  }

  @Test
  void theTopLevelRaisesEveryEnchantmentOfOneBookOnly() {
    kit.mutate("enchanting", "discovery");
    kit.learn("enchanting", "discovery", Recipe.CONVERGENCE, 3);

    open();

    assertThat(raised()).isEqualTo(2);
    assertThat(chest.item(3).storedEnchantments()).containsEntry(SHARPNESS, 4).containsEntry(UNBREAKING, 3);
    assertThat(chest.item(7).storedEnchantments()).containsEntry(SMITE, 1);
  }

  @Test
  void nothingRisesWithoutTheMutationOrPastTheVanillaMaximum() {
    open();
    assertThat(raised()).isZero();

    kit.mutate("discovery", "enchanting");
    kit.learn("discovery", "enchanting", Recipe.CONVERGENCE, 3);
    platform.world().set(6, 61, 6, "chest");
    InventoryRef other = platform.world().blockAt(6, 61, 6).container();
    adapt.post(new PlayerLootGenerateEvent(player, new Loc(platform.world(), 6.5D, 61D, 6.5D)));
    other.setItem(3, book(Map.of(SHARPNESS, 5, UNBREAKING, 3, MENDING, 1)));
    platform.scheduler().advance(2);

    assertThat(other.item(3).storedEnchantments()).containsEntry(SHARPNESS, 5).containsEntry(UNBREAKING, 3)
        .containsEntry(MENDING, 1);
    assertThat(Lorekeeper.maximum(UNBREAKING)).isEqualTo(3);
    assertThat(Lorekeeper.maximum(Id.of("modpack", "frenzy"))).isEqualTo(5);
  }

  @Test
  void booksAlreadyInTheChestBeforeTheRollStayAsTheyWere() {
    kit.mutate("discovery", "enchanting");
    kit.learn("discovery", "enchanting", Recipe.CONVERGENCE, 3);
    chest.setItem(0, book(Map.of(SHARPNESS, 1)));

    open();

    assertThat(chest.item(0).storedEnchantments()).containsEntry(SHARPNESS, 1);
    assertThat(raised()).isEqualTo(2);
  }

  @Test
  void aChestRaisesBooksOnlyTheFirstTimeAPlayerOpensIt() {
    kit.mutate("discovery", "enchanting");
    kit.learn("discovery", "enchanting", Recipe.CONVERGENCE, 1);
    open();
    assertThat(raised()).isEqualTo(1);

    chest.clear(3);
    chest.clear(7);
    open();

    assertThat(raised()).isZero();
  }

  @Test
  void onlyTheHalfOfADoubleChestThatRolledIsRead() {
    assertThat(Lorekeeper.half(27, 0, 1)).containsExactly(0, 27);
    assertThat(Lorekeeper.half(54, 0, 2)).containsExactly(0, 27);
    assertThat(Lorekeeper.half(54, 1, 2)).containsExactly(27, 54);
  }

  @Test
  void aChestAPlayerPlacedRaisesNothing() {
    kit.mutate("discovery", "enchanting");
    kit.learn("discovery", "enchanting", Recipe.CONVERGENCE, 3);

    try (MockedStatic<XpProvenance> provenance = mockStatic(XpProvenance.class, CALLS_REAL_METHODS)) {
      provenance.when(() -> XpProvenance.isPlayerPlaced(any(BlockRef.class))).thenReturn(true);
      open();
    }

    assertThat(raised()).isZero();
  }

  /** The chest's loot rolls for the player: the event fires first, then the roll fills the chest. */
  private void open() {
    adapt.post(new PlayerLootGenerateEvent(player, new Loc(platform.world(), 4.5D, 61D, 4.5D)));
    chest.setItem(3, book(Map.of(SHARPNESS, 3, UNBREAKING, 2)));
    chest.setItem(7, book(Map.of(SMITE, 1)));
    platform.scheduler().advance(2);
  }

  private int raised() {
    ItemRef firstBook = chest.item(3);
    ItemRef secondBook = chest.item(7);
    Map<Id, Integer> first = firstBook == null || firstBook.isEmpty() ? Map.of() : firstBook.storedEnchantments();
    Map<Id, Integer> second = secondBook == null || secondBook.isEmpty() ? Map.of() : secondBook.storedEnchantments();
    return (first.getOrDefault(SHARPNESS, 3) - 3) + (first.getOrDefault(UNBREAKING, 2) - 2)
        + (second.getOrDefault(SMITE, 1) - 1);
  }

  private static ItemRef book(Map<Id, Integer> enchantments) {
    ItemRef book = FakeItem.of("enchanted_book");
    for (Map.Entry<Id, Integer> enchantment : enchantments.entrySet()) {
      book.setStoredEnchantment(enchantment.getKey(), enchantment.getValue());
    }
    return book;
  }
}
