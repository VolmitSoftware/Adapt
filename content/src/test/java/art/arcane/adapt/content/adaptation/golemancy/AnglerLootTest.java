package art.arcane.adapt.content.adaptation.golemancy;

import art.arcane.adapt.platform.value.Id;
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

    assertThat(table.entries()).containsExactly(new AnglerLoot.Entry(Id.minecraft("cod"), 60, 1, 1), new AnglerLoot.Entry(Id.minecraft("ink_sac"), 1, 10, 10),
        new AnglerLoot.Entry(Id.minecraft("bone"), 5, 2, 4), new AnglerLoot.Entry(Id.minecraft("string"), 3, 4, 4), new AnglerLoot.Entry(Id.minecraft("stick"), 2, 1, 64));
    assertThat(table.totalWeight()).isEqualTo(71);
    assertThat(rejected).isEmpty();
  }

  @Test
  void unknownMalformedWeightlessAndNonItemEntriesAreRejected() {
    List<String> rejected = new ArrayList<>();

    AnglerLoot.Table table = AnglerLoot.parse(Arrays.asList("NOT_A_THING:5", "COD", "COD:x", "COD:0", "COD:-3", "WATER:4", null, "SALMON:2"),
        type -> !type.equals(Id.minecraft("water")) && !type.equals(Id.minecraft("not_a_thing")), rejected::add);

    assertThat(table.entries()).containsExactly(new AnglerLoot.Entry(Id.minecraft("salmon"), 2, 1, 1));
    assertThat(rejected).containsExactly("NOT_A_THING:5", "COD", "COD:x", "COD:0", "COD:-3", "WATER:4", "null");
  }

  @Test
  void theDefaultTablesFollowVanillaFishing() {
    List<String> rejected = new ArrayList<>();

    AnglerLoot.Table fish = AnglerLoot.parse(AnglerLoot.DEFAULT_FISH, type -> true, rejected::add);
    AnglerLoot.Table junk = AnglerLoot.parse(AnglerLoot.DEFAULT_JUNK, type -> true, rejected::add);
    AnglerLoot.Table treasure = AnglerLoot.parse(AnglerLoot.DEFAULT_TREASURE, type -> true, rejected::add);

    assertThat(rejected).isEmpty();
    assertThat(fish.entries()).extracting(AnglerLoot.Entry::type).containsExactly(Id.minecraft("cod"), Id.minecraft("salmon"), Id.minecraft("tropical_fish"), Id.minecraft("pufferfish"));
    assertThat(fish.totalWeight()).isEqualTo(100);
    assertThat(junk.entries()).extracting(AnglerLoot.Entry::type).contains(Id.minecraft("lily_pad"), Id.minecraft("leather_boots"), Id.minecraft("rotten_flesh"), Id.minecraft("ink_sac"));
    assertThat(treasure.entries()).extracting(AnglerLoot.Entry::type).containsExactly(Id.minecraft("name_tag"), Id.minecraft("saddle"), Id.minecraft("bow"), Id.minecraft("fishing_rod"),
        Id.minecraft("enchanted_book"), Id.minecraft("nautilus_shell"));
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

    assertThat(roll(tables, 0, 0.0D, 0.0D)).contains(new AnglerLoot.Catch(AnglerLoot.Category.FISH, Id.minecraft("cod"), 1));
    assertThat(roll(tables, 0, 0.849D, 0.6D)).contains(new AnglerLoot.Catch(AnglerLoot.Category.FISH, Id.minecraft("salmon"), 1));
    assertThat(roll(tables, 0, 0.85D, 0.0D)).contains(new AnglerLoot.Catch(AnglerLoot.Category.JUNK, Id.minecraft("lily_pad"), 1));
    assertThat(roll(tables, 0, 0.95D, 0.0D)).contains(new AnglerLoot.Catch(AnglerLoot.Category.TREASURE, Id.minecraft("name_tag"), 1));
    assertThat(roll(tables, 0, 0.999D, 0.999D)).contains(new AnglerLoot.Catch(AnglerLoot.Category.TREASURE, Id.minecraft("nautilus_shell"), 1));
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

    assertThat(roll(noTreasure, 2, 0.999D, 0.0D)).map(AnglerLoot.Catch::type).contains(Id.minecraft("bone"));
    assertThat(roll(new AnglerLoot.Tables(table(), table(), table()), 0, 0.5D)).isEmpty();
  }

  @Test
  void treasureBooksAndDurableTreasuresAreEnchantedWhileOtherCatchesStayPlain() {
    AnglerLoot.Catch book = new AnglerLoot.Catch(AnglerLoot.Category.TREASURE, Id.minecraft("enchanted_book"), 1);
    AnglerLoot.Catch bow = new AnglerLoot.Catch(AnglerLoot.Category.TREASURE, Id.minecraft("bow"), 1);
    AnglerLoot.Catch saddle = new AnglerLoot.Catch(AnglerLoot.Category.TREASURE, Id.minecraft("saddle"), 1);
    AnglerLoot.Catch junkRod = new AnglerLoot.Catch(AnglerLoot.Category.JUNK, Id.minecraft("fishing_rod"), 1);
    AnglerLoot.Catch junkBook = new AnglerLoot.Catch(AnglerLoot.Category.JUNK, Id.minecraft("enchanted_book"), 1);

    assertThat(AnglerLoot.base(book)).isEqualTo(Id.minecraft("book"));
    assertThat(AnglerLoot.enchanted(book, 0)).isTrue();
    assertThat(AnglerLoot.base(bow)).isEqualTo(Id.minecraft("bow"));
    assertThat(AnglerLoot.enchanted(bow, 384)).isTrue();
    assertThat(AnglerLoot.enchanted(saddle, 0)).isFalse();
    assertThat(AnglerLoot.enchanted(junkRod, 64)).isFalse();
    assertThat(AnglerLoot.base(junkBook)).isEqualTo(Id.minecraft("enchanted_book"));
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
