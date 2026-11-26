package art.arcane.adapt.content.adaptation.herbalism;

import art.arcane.adapt.api.adaptation.Adaptation;
import art.arcane.adapt.api.preference.PlayerPreference;
import art.arcane.adapt.api.protection.ProtectionProbes;
import art.arcane.adapt.content.skill.SkillHerbalism;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.world.BlockRef;
import art.arcane.adapt.platform.world.BlockStateRef;
import art.arcane.adapt.util.common.scheduling.Tasks;
import art.arcane.volmlib.util.math.M;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * Harvest & Replant's single-crop harvest: breaks a mature crop with the given tool, keeps one seed to replant it at
 * age 0, and drops or pockets the rest.
 */
public final class HerbalismHarvest {
  private static final Id KELP = Id.minecraft("kelp");
  private final Adaptation<?> owner;
  private final PlayerPreference<HerbalismPreferences.Materials> materials;
  private final Predicate<BlockRef> paysXp;
  private final Listener listener;

  /** Runs on the crop's thread after each harvest. */
  public interface Listener {
    void harvested(PlayerRef player, boolean replanted);
  }

  /** {@code paysXp} decides, before the harvest, whether the crop pays the harvest and replant skill XP. */
  public HerbalismHarvest(Adaptation<?> owner, PlayerPreference<HerbalismPreferences.Materials> materials,
                          Predicate<BlockRef> paysXp, Listener listener) {
    this.owner = owner;
    this.materials = materials;
    this.paysXp = paysXp;
    this.listener = listener;
  }

  /** A hoe ready to harvest with: a copy of it, its cooldown material, and the hand it sits in. */
  public record Hoe(ItemRef tool, Id material, boolean offHand) {
  }

  /** The off-hand hoe if it is off cooldown, else the main-hand one, else null. */
  public static Hoe readyHoe(Adaptation<?> owner, PlayerRef p) {
    ItemRef right = p.inventory().mainHand();
    ItemRef left = p.inventory().offHand();
    if (owner.isTool(left) && owner.isHoe(left) && !p.hasCooldown(left.type())) {
      return new Hoe(left.copy(), left.type(), true);
    }
    if (owner.isTool(right) && owner.isHoe(right) && !p.hasCooldown(right.type())) {
      return new Hoe(right.copy(), right.type(), false);
    }
    return null;
  }

  /** A crop at its last growth stage; a kelp head never counts, since its age only limits how tall it grows. */
  public static boolean isMature(BlockRef block) {
    if (KELP.equals(block.type())) {
      return false;
    }
    BlockStateRef state = block.state();
    return state.age() >= 0 && state.age() == state.maxAge();
  }

  /** Harvests {@code b} when it is a mature crop the player may break; true when it did. */
  public boolean harvest(PlayerRef p, BlockRef b, ItemRef harvestTool) {
    return harvest(p, b, harvestTool, null);
  }

  /**
   * {@link #harvest(PlayerRef, BlockRef, ItemRef)} with the produce handed to {@code sink} instead of dropped or
   * pocketed; the seed kept for replanting is already taken out.
   */
  public boolean harvest(PlayerRef p, BlockRef b, ItemRef harvestTool,
                         @Nullable Consumer<List<ItemRef>> sink) {
    if (b == null || (Tasks.isFoliaThreading() && !Tasks.isOwnedByCurrentRegion(p))) {
      return false;
    }
    if (!owner.preference(p, materials).allows(b.type()) || !isMature(b)
        || owner.getActiveBlockBreakLevel(p, b.location()) <= 0
        || !owner.canBlockBreak(p, b.location())
        || !ProtectionProbes.attemptBlockBreakProbe(p, b)) {
      return false;
    }
    BlockStateRef current = b.state();
    if (current.age() < 0
        || current.age() != current.maxAge()) {
      return false;
    }
    Id cropType = b.type();
    boolean paid = paysXp.test(b);

    List<ItemRef> items = new ArrayList<>(b.drops(harvestTool, p));
    boolean seedConsumed = consumeSeed(items, current.placementItem());
    if (seedConsumed && (!owner.canBlockPlace(p, b.location())
        || !ProtectionProbes.attemptBlockPlaceProbe(p, b))) {
      return false;
    }
    BlockStateRef commitData = b.state();
    if (!b.type().equals(cropType)
        || commitData.age() < 0
        || commitData.age() != commitData.maxAge()) {
      return false;
    }

    if (paid) {
      SkillHerbalism.Config skill = (SkillHerbalism.Config) owner.getSkill().getConfig();
      owner.xp(p, b.center(), skill.harvestPerAgeXP * commitData.age());
      if (seedConsumed) {
        owner.xp(p, b.center(), skill.plantCropSeedsXP);
      }
    }
    if (sink != null) {
      List<ItemRef> produce = new ArrayList<>(items.size());
      for (ItemRef i : items) {
        if (owner.isItem(i) && i.amount() > 0) {
          produce.add(i);
        }
      }
      sink.accept(produce);
    } else {
      release(p, b, items);
    }

    if (seedConsumed) {
      b.setState(commitData.with("age", "0"), true);
    } else {
      b.setType(HerbalismReplant.AIR, true);
    }
    listener.harvested(p, seedConsumed);

    HerbalismReplant.harvestFx(owner, b, M.r(1D / (double) owner.getLevel(p)));
    return true;
  }

  /** Hands {@code items} harvested at {@code b} to the player as a harvest would: pocketed with Drop to Inventory, else dropped. */
  public void release(PlayerRef p, BlockRef b, List<ItemRef> items) {
    if (items.isEmpty()) {
      return;
    }
    if (owner.getActiveSiblingBlockBreakLevel(p, "herbalism-drop-to-inventory", b.location()) > 0) {
      pocket(p, b, items);
      return;
    }
    for (ItemRef i : items) {
      if (owner.isItem(i) && i.amount() > 0) {
        p.world().dropItemNaturally(b.center().position(), i);
      }
    }
  }

  private void pocket(PlayerRef p, BlockRef b, List<ItemRef> items) {
    boolean caught = false;
    for (ItemRef i : items) {
      if (!owner.isItem(i) || i.amount() <= 0) {
        continue;
      }
      boolean allowedDrop = true;
      for (Adaptation<?> sibling : owner.getSkill().getAdaptations()) {
        if (sibling instanceof HerbalismDropToInventory pickup) {
          allowedDrop = pickup.acceptsDrop(p, i.type());
          break;
        }
      }
      if (!allowedDrop) {
        p.world().dropItemNaturally(b.center().position(), i);
        continue;
      }
      List<ItemRef> overflow = p.inventory().addItem(i);
      if (overflow == null || overflow.isEmpty()) {
        caught = true;
      } else {
        for (ItemRef rest : overflow) {
          p.world().dropItem(p.location().position(), rest);
        }
      }
    }
    if (caught) {
      HerbalismReplant.pocketFx(owner, p);
    }
  }

  private boolean consumeSeed(List<ItemRef> drops, Id seedType) {
    if (seedType == null || seedType.isAir()) {
      return false;
    }

    for (ItemRef drop : drops) {
      if (owner.isItem(drop) && drop.type().equals(seedType) && drop.amount() > 0) {
        drop.setAmount(drop.amount() - 1);
        return true;
      }
    }

    return false;
  }
}
