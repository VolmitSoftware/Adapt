package art.arcane.adapt.content.adaptation.axe.blocks;

import art.arcane.adapt.AdaptCore;
import art.arcane.adapt.api.adaptation.BonusBlockBreaks;
import art.arcane.adapt.api.batch.BuildBatch;
import art.arcane.adapt.api.batch.BuildBatches;
import art.arcane.adapt.api.batch.BuildResult;
import art.arcane.adapt.api.batch.Payment;
import art.arcane.adapt.api.batch.Payments;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.fx.Particles;
import art.arcane.adapt.api.mutation.engine.BlockUse;
import art.arcane.adapt.api.mutation.engine.MutationBlock;
import art.arcane.adapt.api.mutation.engine.MutationPayouts;
import art.arcane.adapt.api.mutation.engine.Outcome;
import art.arcane.adapt.api.mutation.engine.StrikeBlock;
import art.arcane.adapt.api.preference.CommonPreferences;
import art.arcane.adapt.api.preference.PlayerPreference;
import art.arcane.adapt.content.adaptation.axe.AxeFelling;
import art.arcane.adapt.content.adaptation.axe.AxePreferences;
import art.arcane.adapt.platform.entity.ItemEntityRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.block.BlockBreakEvent;
import art.arcane.adapt.platform.event.block.BlockDropItemEvent;
import art.arcane.adapt.platform.item.ItemOps;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.BlockPos;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.world.BlockRef;
import art.arcane.adapt.util.common.scheduling.Tasks;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.adapt.util.config.ConfigDoc;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static art.arcane.adapt.util.data.BlockFlag.VEIN_MINED;

/**
 * Forester: primes the player, and the next natural log they break fells the connected logs of its kind. The felled
 * logs drop stripped, or as planks at the vanilla 4 per log when the player prefers planks, and the tree replants itself
 * with a sapling taken from its own leaves' drops. Player-placed logs neither use the prime nor fall. Every break is a normal break.
 */
public class AxesWorkStrike extends StrikeBlock<AxesWorkStrike.Config> {
  private static final int PLANKS_PER_LOG = 4;
  private static final int CANOPY_SEARCH = 2;
  private static final Set<Id> SAPLING_SOIL = Set.of(
      Id.minecraft("grass_block"), Id.minecraft("dirt"), Id.minecraft("coarse_dirt"), Id.minecraft("podzol"),
      Id.minecraft("rooted_dirt"), Id.minecraft("mycelium"), Id.minecraft("moss_block"), Id.minecraft("pale_moss_block"),
      Id.minecraft("mud"), Id.minecraft("muddy_mangrove_roots"), Id.minecraft("farmland"));
  private static final Set<Id> CLAY_PLANTS = Set.of(
      Id.minecraft("mangrove_propagule"), Id.minecraft("azalea"), Id.minecraft("flowering_azalea"));
  private static final Id CLAY = Id.minecraft("clay");
  private final Map<UUID, Primed> primed = playerState();
  private final Map<UUID, Fell> fells = playerState();

  public AxesWorkStrike() {
    super("block-axes-strike-work");
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("oak_sapling"));
  }

  @Override
  public List<PlayerPreference<?>> getPlayerPreferences() {
    return List.of(CommonPreferences.ENABLED, AxePreferences.FELLED_LOGS);
  }

  @Override
  public boolean paysOut() {
    return true;
  }

  @Override
  public Outcome strike(BlockUse use) {
    int logs = (int) Math.round(use.scaled(getConfig().logsAtFirstLevel, getConfig().logsAtTopLevel));
    boolean planks = use.adaptation().preference(use.player(), AxePreferences.FELLED_LOGS) == AxePreferences.FelledLogs.PLANKS;
    primed.put(use.player().uuid(), new Primed(logs, planks, System.currentTimeMillis() + getConfig().primedMillis));
    return Outcome.INSTANT;
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(BlockBreakEvent e) {
    if (e.protectionProbe()) {
      return;
    }
    PlayerRef player = e.player();
    BlockRef origin = e.block();
    Primed prime = primed.get(player.uuid());
    if (prime == null || !AxeFelling.isWood(origin.type()) || VEIN_MINED.get(origin)
        || BonusBlockBreaks.isActive(player)) {
      return;
    }
    if (System.currentTimeMillis() > prime.expiresAt() || !hasActiveAdaptation(player)) {
      primed.remove(player.uuid());
      return;
    }
    if (MutationPayouts.farmed(origin)) {
      return;
    }
    primed.remove(player.uuid());
    Fell fell = new Fell(origin, prime.planks());
    fell.logs.add(key(origin));
    fells.put(player.uuid(), fell);

    Set<BlockRef> connected = AxeFelling.floodFoliage(this, player, origin, getConfig().radius, prime.logs() + 1);
    BlockRef top = origin;
    int felled = 0;
    for (BlockRef log : connected) {
      if (log.equals(origin) || MutationPayouts.farmed(log)) {
        continue;
      }
      if (!BonusBlockBreaks.canCharge(player.inventory().mainHand(), getConfig().durabilityPerLog)) {
        break;
      }
      fell.logs.add(key(log));
      VEIN_MINED.add(log);
      try {
        if (BonusBlockBreaks.breakBlock(player, log, getConfig().durabilityPerLog)) {
          felled++;
          top = log.y() > top.y() ? log : top;
        }
      } finally {
        VEIN_MINED.remove(log);
      }
    }
    searchLeavesForSapling(player, top, fell);
    if (!Tasks.runEntity(player, () -> replant(player, fell), 1)) {
      replant(player, fell);
    }
    if (felled > 0) {
      addStat(player, "axes.mutated.logs-felled", felled);
      fx(origin.center(), FxPriority.GAMEPLAY)
          .particle(Particles.CRIT, Math.min(10, felled + 4), 0D, 0D, 0D, 0.4D, 0.05D);
    }
  }

  @Handle(priority = EventPriority.HIGH)
  public void on(BlockDropItemEvent e) {
    Fell fell = fells.get(e.player().uuid());
    if (fell == null) {
      return;
    }
    BlockPos at = key(e.block());
    if (fell.logs.contains(at)) {
      convert(e.items(), fell.planks);
    } else if (fell.leaves.contains(at) && fell.sapling == null) {
      fell.sapling = takeSapling(e.items());
    }
  }

  private void searchLeavesForSapling(PlayerRef player, BlockRef top, Fell fell) {
    BlockRef canopy = canopyLeaf(top);
    if (canopy == null) {
      return;
    }
    for (BlockRef leaf : AxeFelling.floodFoliage(this, player, canopy, getConfig().leafRadius, getConfig().leavesSearched)) {
      if (fell.sapling != null) {
        return;
      }
      if (MutationPayouts.farmed(leaf)) {
        continue;
      }
      fell.leaves.add(key(leaf));
      VEIN_MINED.add(leaf);
      try {
        BonusBlockBreaks.breakBlock(player, leaf, 0);
      } finally {
        VEIN_MINED.remove(leaf);
      }
    }
  }

  private void replant(PlayerRef player, Fell fell) {
    fells.remove(player.uuid(), fell);
    ItemRef sapling = fell.sapling;
    if (sapling == null) {
      return;
    }
    BlockRef spot = fell.origin.world().blockAt(fell.origin.x(), fell.origin.y(), fell.origin.z());
    if (!player.isOnline()) {
      spot.world().dropItemNaturally(spot.center().position(), sapling);
      return;
    }
    if (!spot.isAir() || !growsOn(sapling.type(), spot.relative(0, -1, 0).type()) || !items().isBlock(sapling.type())) {
      Payments.give(player, List.of(sapling));
      return;
    }
    HeldSapling payment = new HeldSapling(player, sapling);
    BuildBatch batch = BuildBatch.of(this, player)
        .limit(1)
        .pace(1)
        .payFrom(payment)
        .place(spot, items().blockState(sapling.type()), sapling)
        .onDone((BuildResult result) -> payment.release())
        .build();
    if (!BuildBatches.get().start(batch)) {
      payment.release();
    }
  }

  private static void convert(List<ItemEntityRef> drops, boolean planks) {
    for (ItemEntityRef drop : drops) {
      ItemRef stack = drop.item();
      Id converted = planks ? planksOf(stack.type()) : strippedOf(stack.type());
      if (converted == null || !items().isItem(converted)) {
        continue;
      }
      drop.setItem(items().create(converted, stack.amount() * (planks ? PLANKS_PER_LOG : 1)));
    }
  }

  private static @Nullable ItemRef takeSapling(List<ItemEntityRef> drops) {
    for (ItemEntityRef drop : new ArrayList<>(drops)) {
      ItemRef stack = drop.item();
      if (!isSapling(stack.type()) || stack.amount() <= 0) {
        continue;
      }
      ItemRef taken = stack.copyWithAmount(1);
      if (stack.amount() > 1) {
        drop.setItem(stack.copyWithAmount(stack.amount() - 1));
      } else if (!drops.remove(drop)) {
        continue;
      }
      return taken;
    }
    return null;
  }

  private static @Nullable BlockRef canopyLeaf(BlockRef top) {
    for (int y = 1; y >= -CANOPY_SEARCH; y--) {
      for (int x = -CANOPY_SEARCH; x <= CANOPY_SEARCH; x++) {
        for (int z = -CANOPY_SEARCH; z <= CANOPY_SEARCH; z++) {
          BlockRef candidate = top.relative(x, y, z);
          if (candidate.type().path().endsWith("_leaves")) {
            return candidate;
          }
        }
      }
    }
    return null;
  }

  /** The stripped form of a natural log or wood block; null when it has none or is stripped already. */
  public static @Nullable Id strippedOf(Id log) {
    String path = log.path();
    if (!log.isMinecraft() || path.startsWith("stripped_") || !(path.endsWith("_log") || path.endsWith("_wood"))) {
      return null;
    }
    return Id.minecraft("stripped_" + path);
  }

  /** The planks a log or wood block crafts into; null when it crafts into none. */
  public static @Nullable Id planksOf(Id log) {
    String path = log.path();
    if (!log.isMinecraft() || !(path.endsWith("_log") || path.endsWith("_wood"))) {
      return null;
    }
    String wood = path.startsWith("stripped_") ? path.substring("stripped_".length()) : path;
    return Id.minecraft(wood.substring(0, wood.lastIndexOf('_')) + "_planks");
  }

  static boolean isSapling(Id item) {
    return item.path().endsWith("_sapling") || CLAY_PLANTS.contains(item);
  }

  /** Whether {@code sapling} may be planted on {@code soil}. */
  public static boolean growsOn(Id sapling, Id soil) {
    return SAPLING_SOIL.contains(soil) || (soil.equals(CLAY) && CLAY_PLANTS.contains(sapling));
  }

  private static BlockPos key(BlockRef block) {
    return new BlockPos(block.x(), block.y(), block.z());
  }

  private static ItemOps items() {
    return AdaptCore.platform().host().items();
  }

  private record Primed(int logs, boolean planks, long expiresAt) {
  }

  private static final class Fell {
    private final BlockRef origin;
    private final boolean planks;
    private final Set<BlockPos> logs = new HashSet<>();
    private final Set<BlockPos> leaves = new HashSet<>();
    private @Nullable ItemRef sapling;

    private Fell(BlockRef origin, boolean planks) {
      this.origin = origin;
      this.planks = planks;
    }
  }

  /** Pays the placement with the sapling the leaves dropped, and hands it to the player if it was not placed. */
  private static final class HeldSapling implements Payment {
    private final PlayerRef player;
    private @Nullable ItemRef held;

    private HeldSapling(PlayerRef player, ItemRef sapling) {
      this.player = player;
      this.held = sapling;
    }

    @Override
    public @Nullable ItemRef take(ItemRef unit) {
      ItemRef sapling = held;
      if (sapling == null || !sapling.isSimilar(unit)) {
        return null;
      }
      held = null;
      return sapling;
    }

    @Override
    public void giveBack(ItemRef taken) {
      Payments.give(player, List.of(taken));
    }

    private void release() {
      ItemRef sapling = held;
      held = null;
      if (sapling != null) {
        Payments.give(player, List.of(sapling));
      }
    }
  }

  @ConfigDescription("The next tree you fell replants itself and drops stripped logs, or planks if you prefer them.")
  public static class Config extends MutationBlock.Config {
    @ConfigDoc(value = "Connected logs felled at level 1.", impact = "Higher values fell more of the tree.")
    double logsAtFirstLevel = 6D;
    @ConfigDoc(value = "Connected logs felled at the top level.", impact = "Higher values fell more of the tree.")
    double logsAtTopLevel = 10D;
    @ConfigDoc(value = "Farthest a felled log may be from the broken one, in blocks.", impact = "Higher values reach farther up tall trunks.")
    int radius = 8;
    @ConfigDoc(value = "How long the prime waits for a log, in milliseconds.", impact = "Higher values give more time to reach a tree.")
    long primedMillis = 30_000L;
    @ConfigDoc(value = "Axe durability each felled log costs.", impact = "Higher values wear the axe faster.")
    int durabilityPerLog = 1;
    @ConfigDoc(value = "Most of the tree's leaves broken while looking for a sapling.", impact = "Higher values make a replant more likely and clear more canopy.")
    int leavesSearched = 32;
    @ConfigDoc(value = "Farthest a searched leaf may be from the canopy, in blocks.", impact = "Higher values search wider canopies.")
    int leafRadius = 6;
  }
}
