package art.arcane.adapt.api.mutation.signature;

import art.arcane.adapt.AdaptCore;
import art.arcane.adapt.api.adaptation.BonusBlockBreaks;
import art.arcane.adapt.api.adaptation.StatLines;
import art.arcane.adapt.api.batch.BuildBatch;
import art.arcane.adapt.api.batch.BuildBatches;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.fx.Particles;
import art.arcane.adapt.api.mutation.blocks.MutationOverride;
import art.arcane.adapt.api.mutation.engine.BlockUse;
import art.arcane.adapt.api.mutation.engine.MutationAdaptation;
import art.arcane.adapt.api.mutation.engine.MutationPayouts;
import art.arcane.adapt.api.mutation.engine.Outcome;
import art.arcane.adapt.api.xp.XpProvenance;
import art.arcane.adapt.content.adaptation.axe.AxeFelling;
import art.arcane.adapt.content.adaptation.axe.blocks.AxesWorkStrike;
import art.arcane.adapt.content.adaptation.axe.upgraded.Timberfall;
import art.arcane.adapt.content.adaptation.herbalism.HerbalismGrowthPulses;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.block.BlockBreakEvent;
import art.arcane.adapt.platform.event.player.PlayerQuitEvent;
import art.arcane.adapt.platform.item.ItemOps;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.world.BlockRef;
import art.arcane.adapt.platform.world.BlockStateRef;
import art.arcane.adapt.platform.world.WorldRef;
import art.arcane.adapt.util.common.scheduling.Tasks;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.adapt.util.config.ConfigDoc;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Axes and Herbalism, either leading, replacing Convergence: every natural tree a Timberfall swing fells replants
 * itself from a sapling you carry, and saplings around it grow one stage, recorded as forced growth.
 */
public final class ForestKeeper extends Signature<ForestKeeper.Config> {
  private static final String STAGE = "stage";
  private static final Id PROPAGULE = Id.minecraft("mangrove_propagule");
  private static final int MAX_SEARCHED = 2048;
  private final Map<UUID, Id> felled = new ConcurrentHashMap<>();
  private final Set<UUID> tending = ConcurrentHashMap.newKeySet();

  public ForestKeeper(MutationOverride row) {
    super(row);
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("moss_block"));
  }

  @Override
  public boolean paysOut() {
    return true;
  }

  @Override
  public void addSignatureStats(int level, StatLines v) {
    statLore(v, saplings(level), 1);
    statLore(v, radius(level), 2);
  }

  @Override
  public long cooldownMillis(MutationAdaptation adaptation, int level) {
    return Math.max(0L, getConfig().cooldownMillis);
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(BlockBreakEvent e) {
    PlayerRef player = e.player();
    BlockRef origin = e.block();
    if (e.protectionProbe() || BonusBlockBreaks.isActive(player) || tending.contains(player.uuid())
        || !Timberfall.fells(origin) || !AxeFelling.isWood(origin.type()) || !isAxe(player.inventory().mainHand())
        || getActiveLevel(player) <= 0) {
      return;
    }
    felled.put(player.uuid(), origin.type());
    try {
      fire(player, null, origin.location(), MutationPayouts.farmed(origin));
    } finally {
      felled.remove(player.uuid());
    }
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(PlayerQuitEvent e) {
    felled.remove(e.player().uuid());
    tending.remove(e.player().uuid());
  }

  @Override
  protected Outcome run(BlockUse use) {
    PlayerRef player = use.player();
    Id log = felled.remove(player.uuid());
    Loc at = use.center();
    if (log == null || at == null) {
      return Outcome.MISSED;
    }
    int level = use.level();
    BlockRef origin = at.world().blockAt(at.blockX(), at.blockY(), at.blockZ());
    tending.add(player.uuid());
    if (!Tasks.runEntity(player, () -> tend(player, origin, saplingOf(log), level), Math.max(2, getConfig().delayTicks),
        () -> tending.remove(player.uuid()))) {
      tending.remove(player.uuid());
      return Outcome.MISSED;
    }
    return Outcome.INSTANT;
  }

  private void tend(PlayerRef player, BlockRef origin, @Nullable Id sapling, int level) {
    tending.remove(player.uuid());
    if (!player.isOnline() || !player.world().uid().equals(origin.world().uid())) {
      return;
    }
    replant(player, origin, sapling);
    grow(player, origin, level);
  }

  private void replant(PlayerRef player, BlockRef spot, @Nullable Id sapling) {
    ItemOps items = items();
    if (sapling == null || !items.isItem(sapling) || !items.isBlock(sapling) || !spot.isAir()
        || !AxesWorkStrike.growsOn(sapling, spot.relative(0, -1, 0).type()) || BuildBatches.get().isRunning(player)) {
      return;
    }
    BlockStateRef state = items.blockState(sapling);
    if (state == null || state.isAir()) {
      return;
    }
    BuildBatches.get().start(BuildBatch.of(this, player).limit(1).pace(1)
        .place(spot, state, items.create(sapling, 1)).build());
  }

  /** Ages the young saplings nearest the stump by one stage, at most the level's count, reading a bounded area. */
  private void grow(PlayerRef player, BlockRef origin, int level) {
    int reach = radius(level);
    int height = Math.max(0, getConfig().heightRange);
    int left = saplings(level);
    int searched = 0;
    WorldRef world = origin.world();
    for (int ring = 0; ring <= reach && left > 0 && searched < MAX_SEARCHED; ring++) {
      for (int x = -ring; x <= ring && left > 0 && searched < MAX_SEARCHED; x++) {
        for (int z = -ring; z <= ring && left > 0 && searched < MAX_SEARCHED; z++) {
          if (Math.max(Math.abs(x), Math.abs(z)) != ring) {
            continue;
          }
          int bx = origin.x() + x;
          int bz = origin.z() + z;
          if (!world.isChunkLoaded(bx >> 4, bz >> 4)) {
            continue;
          }
          for (int y = -height; y <= height && left > 0; y++) {
            searched++;
            BlockRef block = world.blockAt(bx, origin.y() + y, bz);
            if (age(player, block)) {
              left--;
            }
          }
        }
      }
    }
  }

  private boolean age(PlayerRef player, BlockRef block) {
    Id type = block.type();
    if (!type.path().endsWith("_sapling") && !type.equals(PROPAGULE)) {
      return false;
    }
    BlockStateRef state = block.state();
    if (!"0".equals(state.property(STAGE)) || !Tasks.isOwnedByCurrentRegion(block.location())
        || !HerbalismGrowthPulses.mayGrow(this, player, block)) {
      return false;
    }
    block.setState(state.with(STAGE, "1"), true);
    XpProvenance.recordBonemeal(block);
    fx(block.center(), FxPriority.GAMEPLAY).particle(Particles.HAPPY_VILLAGER, 4, 0.3D, 0.3D, 0.3D, 0D, 0D);
    return true;
  }

  /** The sapling a log or wood of this kind grows from; null for stems and anything without one. */
  static @Nullable Id saplingOf(Id log) {
    String path = log.path();
    if (!log.isMinecraft() || !(path.endsWith("_log") || path.endsWith("_wood"))) {
      return null;
    }
    String wood = path.startsWith("stripped_") ? path.substring("stripped_".length()) : path;
    String kind = wood.substring(0, wood.lastIndexOf('_'));
    return kind.equals("mangrove") ? PROPAGULE : Id.minecraft(kind + "_sapling");
  }

  int saplings(int level) {
    return (int) Math.round(SignatureKit.scaled(level, getMaxLevel(), getConfig().saplingsAtFirstLevel,
        getConfig().saplingsAtTopLevel));
  }

  int radius(int level) {
    return (int) Math.round(SignatureKit.scaled(level, getMaxLevel(), getConfig().radiusAtFirstLevel,
        getConfig().radiusAtTopLevel));
  }

  private static ItemOps items() {
    return AdaptCore.platform().host().items();
  }

  @ConfigDescription("Every tree Timberfall fells replants itself and grows the saplings around it one stage.")
  public static class Config extends Signature.Config {
    @ConfigDoc(value = "Saplings grown one stage per felled tree at level 1.", impact = "Higher values grow more saplings each fell.")
    int saplingsAtFirstLevel = 4;
    @ConfigDoc(value = "Saplings grown one stage per felled tree at the top level.", impact = "Higher values grow more saplings each fell.")
    int saplingsAtTopLevel = 24;
    @ConfigDoc(value = "Blocks around the stump searched for saplings at level 1.", impact = "Higher values reach farther saplings.")
    int radiusAtFirstLevel = 6;
    @ConfigDoc(value = "Blocks around the stump searched for saplings at the top level.", impact = "Higher values reach farther saplings.")
    int radiusAtTopLevel = 12;
    @ConfigDoc(value = "Blocks above and below the stump searched for saplings.", impact = "Higher values reach saplings on slopes, at more cost per fell.")
    int heightRange = 2;
    @ConfigDoc(value = "Ticks after the first log before the stump is replanted, once the tree has fallen.", impact = "Raise it if saplings from the leaves arrive late.")
    int delayTicks = 3;
    @ConfigDoc(value = "Cooldown after a tree is tended, in milliseconds.", impact = "Higher values leave more trees untended.")
    long cooldownMillis = 0;
  }
}
