package art.arcane.adapt.content.adaptation.excavation;

import art.arcane.adapt.AdaptCore;
import art.arcane.adapt.api.adaptation.Adaptation;
import art.arcane.adapt.platform.item.ItemOps;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;

import java.util.List;
import java.util.Set;

/** What digging can turn up: archaeology treasure in loose ground, bone loot in earth, and bonus muddy blocks. */
public final class ExcavationFinds {
  public static final List<String> TREASURE_TABLE = List.of(
      "BONE:30:1:2",
      "FLINT:30:1:2",
      "CLAY_BALL:15:1:3",
      "ANGLER_POTTERY_SHERD:6:1:1",
      "ARMS_UP_POTTERY_SHERD:6:1:1",
      "SKULL_POTTERY_SHERD:4:1:1",
      "EMERALD:3:1:1"
  );
  public static final List<String> BONE_TABLE = List.of(
      "BONE:40:1:2",
      "BONE_MEAL:25:2:4",
      "ROTTEN_FLESH:20:1:2",
      "BONE_BLOCK:6:1:1",
      "SKELETON_SKULL:2:1:1"
  );
  private static final Set<String> TREASURE_BLOCKS = Set.of("sand", "red_sand", "gravel", "mud", "clay");
  private static final Set<String> GRAVE_SOIL = Set.of("dirt", "grass_block", "coarse_dirt", "rooted_dirt", "podzol",
      "mycelium", "dirt_path");
  private static final Id BONE_BLOCK = Id.minecraft("bone_block");
  private static final Id SKELETON_SKULL = Id.minecraft("skeleton_skull");

  private ExcavationFinds() {
  }

  public static boolean isTreasureBlock(Id type) {
    return type.isMinecraft() && TREASURE_BLOCKS.contains(type.path());
  }

  public static boolean isGraveSoil(Id type) {
    return type.isMinecraft() && GRAVE_SOIL.contains(type.path());
  }

  public static boolean isMudlarkBlock(Id type) {
    if (!type.isMinecraft()) {
      return false;
    }
    return switch (type.path()) {
      case "clay", "mud", "muddy_mangrove_roots", "soul_sand", "soul_soil" -> true;
      default -> false;
    };
  }

  public static Id mudlarkBonus(Id type) {
    return switch (type.path()) {
      case "clay" -> Id.minecraft("clay_ball");
      case "mud", "muddy_mangrove_roots" -> Id.minecraft("mud");
      case "soul_sand" -> Id.minecraft("soul_sand");
      default -> Id.minecraft("soul_soil");
    };
  }

  /** Drops a treasure roll at {@code drop} with a sparkle, brighter for rare entries. */
  public static void unearthTreasure(Adaptation<?> owner, Loc drop, ExcavationLoot.Roll roll) {
    ItemOps items = AdaptCore.platform().host().items();
    drop.world().dropItemNaturally(drop.position(), items.create(roll.material(), roll.amount()));
    ExcavationTreasureHunter.unearthFx(owner, drop, items.create(roll.material()), roll.weight() <= 6.0D);
  }

  /** Drops a bone loot roll at {@code center} in a puff of ash. */
  public static void unearthBones(Adaptation<?> owner, Loc center, ExcavationLoot.Roll roll) {
    ItemOps items = AdaptCore.platform().host().items();
    center.world().dropItemNaturally(center.position(), items.create(roll.material(), roll.amount()));
    boolean bony = roll.material().equals(BONE_BLOCK) || roll.material().equals(SKELETON_SKULL);
    ExcavationGraveDigger.unearthFx(owner, center, bony);
  }

  /** Drops one {@code bonus} item at {@code drop} with a splash. */
  public static void dropMudlarkBonus(Adaptation<?> owner, Loc drop, Id bonus) {
    ItemOps items = AdaptCore.platform().host().items();
    drop.world().dropItemNaturally(drop.position(), items.create(bonus, 1));
    ExcavationMudlark.bonusFx(owner, drop, items.create(bonus));
  }
}
