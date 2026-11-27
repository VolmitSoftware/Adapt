package art.arcane.adapt.content.adaptation.sword.blocks;

import art.arcane.adapt.api.config.ConfigDescription;
import art.arcane.adapt.api.config.ConfigDoc;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.fx.Particles;
import art.arcane.adapt.api.fx.Sounds;
import art.arcane.adapt.api.item.ItemKinds;
import art.arcane.adapt.api.mutation.BlockUse;
import art.arcane.adapt.api.mutation.MutationBlock;
import art.arcane.adapt.api.mutation.Outcome;
import art.arcane.adapt.api.mutation.StrikeBlock;
import art.arcane.adapt.api.mutation.StrikeForm;
import art.arcane.adapt.content.adaptation.sword.SwordsMachete;
import art.arcane.adapt.content.skill.SkillSwords;
import art.arcane.adapt.api.world.BonusBreaks;
import art.arcane.adapt.platform.entity.ItemEntityRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.block.BlockDropItemEvent;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.value.Vec3;
import art.arcane.adapt.platform.world.BlockRef;
import art.arcane.adapt.platform.world.BlockStateRef;
import art.arcane.adapt.platform.world.WorldRef;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Grass, leaves and plants in an arc around the work spot, on the side the player faces, are cut and their drops go to
 * the inventory. Uses Machete's foliage list. A staged crop is cut only when fully grown; plants whose age is a growth
 * timer (cactus, sugar cane, bamboo, chorus) are cut at any age, and a column keeps its bottom block so it regrows.
 * Each cut is a normal break by the player and charges the sword. The work spot itself is left to the break that set
 * the strike off, so it never drops twice.
 */
public class SwordsWorkStrike extends StrikeBlock<SwordsWorkStrike.Config> {
  private static final Set<Id> STAGED = Set.of(Id.minecraft("wheat"), Id.minecraft("beetroots"),
      Id.minecraft("carrots"), Id.minecraft("potatoes"), Id.minecraft("nether_wart"), Id.minecraft("cocoa"));
  private static final Set<Id> COLUMNS = Set.of(Id.minecraft("cactus"), Id.minecraft("sugar_cane"),
      Id.minecraft("bamboo"));
  private final Set<UUID> collecting = ConcurrentHashMap.newKeySet();

  public SwordsWorkStrike() {
    super(SkillSwords.ID, StrikeForm.WORK);
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("short_grass"));
  }

  @Override
  public boolean paysOut() {
    return true;
  }

  @Override
  public Outcome strike(BlockUse use) {
    PlayerRef player = use.player();
    Loc origin = use.center();
    Vec3 facing = player.location().direction().withY(0D);
    if (facing.lengthSquared() < 1.0E-6D) {
      facing = Vec3.ZERO.withZ(1D);
    }
    facing = facing.normalize();
    double radius = use.scaled(getConfig().radiusAtFirstLevel, getConfig().radiusAtTopLevel);
    int reach = (int) Math.ceil(radius);
    WorldRef world = origin.world();
    int cut = 0;
    collecting.add(player.uuid());
    try {
      for (int y = 2; y >= -1 && cut < getConfig().maxBlocks; y--) {
        for (int x = -reach; x <= reach && cut < getConfig().maxBlocks; x++) {
          for (int z = -reach; z <= reach && cut < getConfig().maxBlocks; z++) {
            if ((x == 0 && y == 0 && z == 0) || (x * x) + (z * z) > radius * radius
                || (x * facing.x()) + (z * facing.z()) < 0D) {
              continue;
            }
            if (!BonusBreaks.canCharge(player.inventory().mainHand(), getConfig().durabilityPerBlock)) {
              return finish(player, facing, cut);
            }
            BlockRef block = world.blockAt(origin.blockX() + x, origin.blockY() + y, origin.blockZ() + z);
            if (cuts(block) && canBlockBreak(player, block.location())
                && BonusBreaks.breakBlock(player, block, getConfig().durabilityPerBlock)) {
              cut++;
            }
          }
        }
      }
    } finally {
      collecting.remove(player.uuid());
    }
    return finish(player, facing, cut);
  }

  /** Drops of the blocks the strike cuts go to the inventory; what does not fit falls at the player. */
  @Handle(priority = EventPriority.HIGHEST)
  public void on(BlockDropItemEvent e) {
    PlayerRef player = e.player();
    if (!collecting.contains(player.uuid())) {
      return;
    }
    for (ItemEntityRef item : List.copyOf(e.items())) {
      ItemRef drop = item.item().copy();
      if (!ItemKinds.isItem(drop) || drop.amount() <= 0 || !e.items().remove(item)) {
        continue;
      }
      List<ItemRef> overflow = player.inventory().addItem(drop);
      if (overflow == null) {
        continue;
      }
      for (ItemRef rest : overflow) {
        player.world().dropItem(player.location().position(), rest);
      }
    }
  }

  private Outcome finish(PlayerRef player, Vec3 facing, int cut) {
    if (cut <= 0) {
      return Outcome.MISSED;
    }
    addStat(player, "swords.mutated.foliage-cut", cut);
    fx(player.eyeLocation().add(facing.multiply(1.6D)), FxPriority.COMBAT)
        .particle(Particles.CRIT, Math.min(8, cut + 4), 0, 0, 0, 0.25D, 0.05D)
        .chord(Sounds.ENTITY_PLAYER_ATTACK_SWEEP, 1F, 0.9F, Sounds.ITEM_AXE_STRIP, 0.35F, 1.4F);
    return Outcome.INSTANT;
  }

  private static boolean cuts(BlockRef block) {
    Id type = block.type();
    if (STAGED.contains(type)) {
      BlockStateRef state = block.state();
      return state.age() >= 0 && state.age() == state.maxAge();
    }
    if (COLUMNS.contains(type)) {
      return block.relative(0, -1, 0).type().equals(type);
    }
    return SwordsMachete.isFoliage(type);
  }

  @ConfigDescription("Grass, leaves and crops in a 3-block arc are cut and collected.")
  public static class Config extends MutationBlock.Config {
    @ConfigDoc(value = "Reach of the arc in blocks at level 1.", impact = "Higher values cut farther.")
    double radiusAtFirstLevel = 3D;
    @ConfigDoc(value = "Reach of the arc in blocks at the top level.", impact = "Higher values cut farther.")
    double radiusAtTopLevel = 4D;
    @ConfigDoc(value = "Most blocks one strike cuts.", impact = "Lower values limit the work one strike does.")
    int maxBlocks = 32;
    @ConfigDoc(value = "Sword durability each cut block costs.", impact = "Higher values wear the sword faster.")
    int durabilityPerBlock = 1;
  }
}
