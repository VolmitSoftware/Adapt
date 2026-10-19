package art.arcane.adapt.content.adaptation.golemancy;

import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.List;
import java.util.Optional;
import java.util.function.DoubleSupplier;

import static org.assertj.core.api.Assertions.assertThat;

class AnglerLootTest {
  private static final AnglerLoot.Weights VANILLA = new AnglerLoot.Weights(new AnglerLoot.Weight(85, -1), new AnglerLoot.Weight(10, -2),
      new AnglerLoot.Weight(5, 2));

  @Test
  void entriesParseAsMaterialWeightAndOptionalAmountRange() {
    List<String> rejected = new ArrayList<>();

    AnglerLoot.Table table = AnglerLoot.parse(List.of("COD:60", "INK_SAC:1:10:10", "BONE:5:2:4", "STRING:3:4:2", "STICK:2:0:99"),
        type -> true, rejected::add);

    assertThat(table.entries()).containsExactly(new AnglerLoot.Entry(Material.COD, 60, 1, 1), new AnglerLoot.Entry(Material.INK_SAC, 1, 10, 10),
        new AnglerLoot.Entry(Material.BONE, 5, 2, 4), new AnglerLoot.Entry(Material.STRING, 3, 4, 4), new AnglerLoot.Entry(Material.STICK, 2, 1, 64));
    assertThat(table.totalWeight()).isEqualTo(71);
    assertThat(rejected).isEmpty();
  }

  @Test
  void unknownMalformedWeightlessAndNonItemEntriesAreRejected() {
    List<String> rejected = new ArrayList<>();

    AnglerLoot.Table table = AnglerLoot.parse(Arrays.asList("NOT_A_THING:5", "COD", "COD:x", "COD:0", "COD:-3", "WATER:4", null, "SALMON:2"),
        type -> type != Material.WATER, rejected::add);

    assertThat(table.entries()).containsExactly(new AnglerLoot.Entry(Material.SALMON, 2, 1, 1));
    assertThat(rejected).containsExactly("NOT_A_THING:5", "COD", "COD:x", "COD:0", "COD:-3", "WATER:4", "null");
  }

  @Test
  void theDefaultTablesFollowVanillaFishing() {
    List<String> rejected = new ArrayList<>();

    AnglerLoot.Table fish = AnglerLoot.parse(AnglerLoot.DEFAULT_FISH, type -> true, rejected::add);
    AnglerLoot.Table junk = AnglerLoot.parse(AnglerLoot.DEFAULT_JUNK, type -> true, rejected::add);
    AnglerLoot.Table treasure = AnglerLoot.parse(AnglerLoot.DEFAULT_TREASURE, type -> true, rejected::add);

    assertThat(rejected).isEmpty();
    assertThat(fish.entries()).extracting(AnglerLoot.Entry::type).containsExactly(Material.COD, Material.SALMON, Material.TROPICAL_FISH, Material.PUFFERFISH);
    assertThat(fish.totalWeight()).isEqualTo(100);
    assertThat(junk.entries()).extracting(AnglerLoot.Entry::type).contains(Material.LILY_PAD, Material.LEATHER_BOOTS, Material.ROTTEN_FLESH, Material.INK_SAC);
    assertThat(treasure.entries()).extracting(AnglerLoot.Entry::type).containsExactly(Material.NAME_TAG, Material.SADDLE, Material.BOW, Material.FISHING_ROD,
        Material.ENCHANTED_BOOK, Material.NAUTILUS_SHELL);
  }

  @Test
  void luckShiftsCategoryWeightsTowardTreasureAndNeverBelowZero() {
    assertThat(VANILLA.fish().at(0)).isEqualTo(85);
    assertThat(VANILLA.fish().at(2)).isEqualTo(83);
    assertThat(VANILLA.junk().at(2)).isEqualTo(6);
    assertThat(VANILLA.junk().at(5)).isZero();
    assertThat(VANILLA.junk().at(6)).isZero();
    assertThat(VANILLA.treasure().at(0)).isEqualTo(5);
    assertThat(VANILLA.treasure().at(2)).isEqualTo(9);
  }

  @Test
  void aRollPicksACategoryByWeightThenAnEntryByWeight() {
    AnglerLoot.Tables tables = defaults();

    assertThat(roll(tables, 0, 0.0D, 0.0D)).contains(new AnglerLoot.Catch(AnglerLoot.Category.FISH, Material.COD, 1));
    assertThat(roll(tables, 0, 0.849D, 0.6D)).contains(new AnglerLoot.Catch(AnglerLoot.Category.FISH, Material.SALMON, 1));
    assertThat(roll(tables, 0, 0.85D, 0.0D)).contains(new AnglerLoot.Catch(AnglerLoot.Category.JUNK, Material.LILY_PAD, 1));
    assertThat(roll(tables, 0, 0.95D, 0.0D)).contains(new AnglerLoot.Catch(AnglerLoot.Category.TREASURE, Material.NAME_TAG, 1));
    assertThat(roll(tables, 0, 0.999D, 0.999D)).contains(new AnglerLoot.Catch(AnglerLoot.Category.TREASURE, Material.NAUTILUS_SHELL, 1));
  }

  @Test
  void luckTurnsTheSameRollIntoTreasure() {
    AnglerLoot.Tables tables = defaults();

    assertThat(roll(tables, 0, 0.92D, 0.0D)).map(AnglerLoot.Catch::category).contains(AnglerLoot.Category.JUNK);
    assertThat(roll(tables, 2, 0.92D, 0.0D)).map(AnglerLoot.Catch::category).contains(AnglerLoot.Category.TREASURE);
    assertThat(roll(tables, 0, 0.848D, 0.0D)).map(AnglerLoot.Catch::category).contains(AnglerLoot.Category.FISH);
    assertThat(roll(tables, 2, 0.848D, 0.0D)).map(AnglerLoot.Catch::category).contains(AnglerLoot.Category.JUNK);
  }

  @Test
  void amountsRollWithinTheEntryRange() {
    AnglerLoot.Tables tables = new AnglerLoot.Tables(table("BONE:5:2:4"), table(), table());

    assertThat(roll(tables, 0, 0.0D, 0.0D, 0.0D)).map(AnglerLoot.Catch::amount).contains(2);
    assertThat(roll(tables, 0, 0.0D, 0.0D, 0.5D)).map(AnglerLoot.Catch::amount).contains(3);
    assertThat(roll(tables, 0, 0.0D, 0.0D, 0.999D)).map(AnglerLoot.Catch::amount).contains(4);
  }

  @Test
  void emptyTablesNeverRollAndAllEmptyRollsNothing() {
    AnglerLoot.Tables noTreasure = new AnglerLoot.Tables(table("COD:1"), table("BONE:1"), table());

    assertThat(roll(noTreasure, 2, 0.999D, 0.0D)).map(AnglerLoot.Catch::type).contains(Material.BONE);
    assertThat(roll(new AnglerLoot.Tables(table(), table(), table()), 0, 0.5D)).isEmpty();
  }

  @Test
  void treasureBooksAndDurableTreasuresAreEnchantedWhileOtherCatchesStayPlain() {
    AnglerLoot.Catch book = new AnglerLoot.Catch(AnglerLoot.Category.TREASURE, Material.ENCHANTED_BOOK, 1);
    AnglerLoot.Catch bow = new AnglerLoot.Catch(AnglerLoot.Category.TREASURE, Material.BOW, 1);
    AnglerLoot.Catch saddle = new AnglerLoot.Catch(AnglerLoot.Category.TREASURE, Material.SADDLE, 1);
    AnglerLoot.Catch junkRod = new AnglerLoot.Catch(AnglerLoot.Category.JUNK, Material.FISHING_ROD, 1);
    AnglerLoot.Catch junkBook = new AnglerLoot.Catch(AnglerLoot.Category.JUNK, Material.ENCHANTED_BOOK, 1);

    assertThat(AnglerLoot.base(book)).isEqualTo(Material.BOOK);
    assertThat(AnglerLoot.enchanted(book, 0)).isTrue();
    assertThat(AnglerLoot.base(bow)).isEqualTo(Material.BOW);
    assertThat(AnglerLoot.enchanted(bow, 384)).isTrue();
    assertThat(AnglerLoot.enchanted(saddle, 0)).isFalse();
    assertThat(AnglerLoot.enchanted(junkRod, 64)).isFalse();
    assertThat(AnglerLoot.base(junkBook)).isEqualTo(Material.ENCHANTED_BOOK);
    assertThat(AnglerLoot.enchanted(junkBook, 0)).isFalse();
  }

  private static AnglerLoot.Tables defaults() {
    return new AnglerLoot.Tables(AnglerLoot.parse(AnglerLoot.DEFAULT_FISH, type -> true, name -> {
    }), AnglerLoot.parse(AnglerLoot.DEFAULT_JUNK, type -> true, name -> {
    }), AnglerLoot.parse(AnglerLoot.DEFAULT_TREASURE, type -> true, name -> {
    }));
  }

  private static AnglerLoot.Table table(String... entries) {
    return AnglerLoot.parse(List.of(entries), type -> true, name -> {
    });
  }

  private static Optional<AnglerLoot.Catch> roll(AnglerLoot.Tables tables, int luck, double... draws) {
    Deque<Double> queue = new ArrayDeque<>();
    for (double draw : draws) {
      queue.add(draw);
    }
    DoubleSupplier random = () -> queue.isEmpty() ? 0.0D : queue.poll();
    return AnglerLoot.roll(tables, VANILLA, luck, random);
  }
}
