package art.arcane.adapt.api.mutation.signature;

import art.arcane.adapt.AdaptCore;
import art.arcane.adapt.api.adaptation.BonusBlockBreaks;
import art.arcane.adapt.api.adaptation.StatLines;
import art.arcane.adapt.api.batch.BuildBatch;
import art.arcane.adapt.api.batch.BuildBatches;
import art.arcane.adapt.api.mutation.blocks.MutationOverride;
import art.arcane.adapt.api.mutation.engine.BlockUse;
import art.arcane.adapt.api.mutation.engine.MutationAdaptation;
import art.arcane.adapt.api.mutation.engine.MutationPayouts;
import art.arcane.adapt.api.mutation.engine.Outcome;
import art.arcane.adapt.content.adaptation.architect.ArchitectBuilds;
import art.arcane.adapt.content.adaptation.pickaxe.PickaxeBore;
import art.arcane.adapt.content.adaptation.pickaxe.PickaxeDeepCore;
import art.arcane.adapt.content.adaptation.pickaxe.upgraded.Earthshaker;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.block.BlockBreakEvent;
import art.arcane.adapt.platform.event.player.PlayerQuitEvent;
import art.arcane.adapt.platform.item.ItemOps;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.BlockFace;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.world.BlockRef;
import art.arcane.adapt.platform.world.BlockStateRef;
import art.arcane.adapt.util.common.scheduling.Tasks;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.adapt.util.config.ConfigDoc;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Pickaxes and Architect, either leading, replacing Convergence: while Earthshaker bores a level tunnel, the natural
 * stone around the bore is swapped for the block in your off hand, the floor first, then the walls, then the ceiling,
 * and a torch from your inventory goes down every few blocks. Every swap is a real break and a real, paid placement.
 */
public final class TunnelBuilder extends Signature<TunnelBuilder.Config> {
  private static final Id TORCH = Id.minecraft("torch");
  private static final float STEEP = 60F;
  private final Map<UUID, Bore> bores = new ConcurrentHashMap<>();
  private final Map<UUID, Loc> torches = new ConcurrentHashMap<>();

  public TunnelBuilder(MutationOverride row) {
    super(row);
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("torch"));
  }

  @Override
  public void addSignatureStats(int level, StatLines v) {
    statLore(v, cells(level), 1);
    statLore(v, Math.max(1, getConfig().torchSpacing), 2);
  }

  @Override
  public long cooldownMillis(MutationAdaptation adaptation, int level) {
    return Math.max(0L, getConfig().cooldownMillis);
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(BlockBreakEvent e) {
    PlayerRef player = e.player();
    BlockRef origin = e.block();
    Loc at = player.location();
    if (e.protectionProbe() || BonusBlockBreaks.isActive(player) || !isPickaxe(player.inventory().mainHand())
        || Math.abs(at.pitch()) > STEEP || getActiveLevel(player) <= 0) {
      return;
    }
    Earthshaker earthshaker = ArchitectBuilds.find("upgraded-pickaxe-earthshaker", Earthshaker.class);
    if (earthshaker == null || !earthshaker.bores(player, origin)) {
      return;
    }
    bores.put(player.uuid(), new Bore(origin, PickaxeBore.facing(at.yaw())));
    try {
      fire(player, null, origin.location(), false);
    } finally {
      bores.remove(player.uuid());
    }
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(PlayerQuitEvent e) {
    bores.remove(e.player().uuid());
    torches.remove(e.player().uuid());
  }

  @Override
  protected Outcome run(BlockUse use) {
    PlayerRef player = use.player();
    Bore bore = bores.remove(player.uuid());
    if (bore == null) {
      return Outcome.MISSED;
    }
    BlockStateRef lining = ArchitectBuilds.placedState(player.inventory().offHand());
    boolean torch = player.inventory().contains(TORCH, 1);
    if (lining == null && !torch) {
      return Outcome.MISSED;
    }
    int level = use.level();
    if (!Tasks.runEntity(player, () -> line(player, bore, lining, level), Math.max(2, getConfig().delayTicks))) {
      return Outcome.MISSED;
    }
    return Outcome.INSTANT;
  }

  private void line(PlayerRef player, Bore bore, @Nullable BlockStateRef lining, int level) {
    if (!player.isOnline() || !player.world().uid().equals(bore.origin().world().uid())
        || !isPickaxe(player.inventory().mainHand()) || BuildBatches.get().isRunning(player)) {
      return;
    }
    BuildBatch.Builder batch = BuildBatch.of(this, player).pace(Math.max(1, getConfig().blocksPerTick));
    int durability = Math.max(0, getConfig().durabilityPerSwap);
    if (lining != null) {
      ItemRef unit = player.inventory().offHand().copyWithAmount(1);
      for (BlockRef cell : shell(bore, level)) {
        if (stone(cell.type()) && !cell.type().equals(lining.type()) && !MutationPayouts.farmed(cell) && canBlockBreak(player, cell.location())) {
          batch.breakWith(cell, durability, this::isPickaxe);
          batch.place(cell, lining, unit);
        } else if (cell.isAir() || cell.isReplaceable()) {
          batch.place(cell, lining, unit);
        }
      }
    }
    BlockRef torchSpot = at(bore, -1, -1);
    BlockStateRef torch = items().blockState(TORCH);
    Loc last = torches.get(player.uuid());
    boolean due = last == null || !last.sameWorld(torchSpot.location())
        || horizontalDistanceSquared(last, torchSpot.location()) >= (double) getConfig().torchSpacing * getConfig().torchSpacing;
    if (due && torch != null && torchSpot.isAir() && player.inventory().contains(TORCH, 1)) {
      batch.place(torchSpot, torch, items().create(TORCH, 1));
      torches.put(player.uuid(), torchSpot.location());
    }
    BuildBatch built = batch.build();
    if (!built.steps().isEmpty()) {
      BuildBatches.get().start(built);
    }
  }

  /** The ring around the bore that gets lined at {@code level}: the floor, then the walls, then the ceiling. */
  List<BlockRef> shell(Bore bore, int level) {
    List<BlockRef> cells = new ArrayList<>(12);
    for (int w = -1; w <= 1; w++) {
      cells.add(at(bore, w, -2));
    }
    if (level >= getConfig().wallsLevel) {
      for (int h = -1; h <= 1; h++) {
        cells.add(at(bore, -2, h));
        cells.add(at(bore, 2, h));
      }
    }
    if (level >= getConfig().ceilingLevel) {
      for (int w = -1; w <= 1; w++) {
        cells.add(at(bore, w, 2));
      }
    }
    return cells;
  }

  int cells(int level) {
    return 3 + (level >= getConfig().wallsLevel ? 6 : 0) + (level >= getConfig().ceilingLevel ? 3 : 0);
  }

  private static BlockRef at(Bore bore, int across, int up) {
    boolean widthOnX = bore.facing().dz() != 0;
    return bore.origin().relative(widthOnX ? across : 0, up, widthOnX ? 0 : across);
  }

  static boolean stone(Id type) {
    return PickaxeBore.STONE.contains(type) || PickaxeDeepCore.DEEPSLATE_BLOCKS.contains(type);
  }

  private static double horizontalDistanceSquared(Loc a, Loc b) {
    double x = a.x() - b.x();
    double z = a.z() - b.z();
    return (x * x) + (z * z);
  }

  private static ItemOps items() {
    return AdaptCore.platform().host().items();
  }

  record Bore(BlockRef origin, BlockFace facing) {
  }

  @ConfigDescription("Earthshaker's tunnels are lined with your off-hand block and lit with torches as you bore.")
  public static class Config extends Signature.Config {
    @ConfigDoc(value = "Level from which the walls are lined as well as the floor.", impact = "Lower values line walls sooner.")
    int wallsLevel = 2;
    @ConfigDoc(value = "Level from which the ceiling is lined too.", impact = "Lower values seal the ceiling sooner.")
    int ceilingLevel = 3;
    @ConfigDoc(value = "Blocks between two torches along a tunnel.", impact = "Lower values light the tunnel more densely.")
    int torchSpacing = 8;
    @ConfigDoc(value = "Extra pickaxe durability lost per stone block swapped for lining.", impact = "Higher values wear the pickaxe out faster.")
    int durabilityPerSwap = 1;
    @ConfigDoc(value = "Blocks lined per tick, before the server-wide cap.", impact = "Higher values finish each ring sooner.")
    int blocksPerTick = 16;
    @ConfigDoc(value = "Ticks after the swing before the ring is lined, once Earthshaker has bored.", impact = "Raise it if lining starts before the bore.")
    int delayTicks = 2;
    @ConfigDoc(value = "Cooldown after a ring is lined, in milliseconds.", impact = "Higher values line fewer rings.")
    long cooldownMillis = 0;
  }
}
