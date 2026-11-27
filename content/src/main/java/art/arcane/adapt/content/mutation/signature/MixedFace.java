package art.arcane.adapt.content.mutation.signature;

import art.arcane.adapt.api.AdaptApi;
import art.arcane.adapt.api.config.ConfigDescription;
import art.arcane.adapt.api.config.ConfigDoc;
import art.arcane.adapt.api.content.Adaptation;
import art.arcane.adapt.api.content.StatLines;
import art.arcane.adapt.api.item.ItemKinds;
import art.arcane.adapt.content.adaptation.architect.ArchitectBuilds;
import art.arcane.adapt.content.adaptation.excavation.ExcavationBlocks;
import art.arcane.adapt.content.adaptation.excavation.upgraded.Landshaper;
import art.arcane.adapt.content.adaptation.pickaxe.PickaxeBore;
import art.arcane.adapt.content.adaptation.pickaxe.upgraded.Earthshaker;
import art.arcane.adapt.api.world.BonusBreaks;
import art.arcane.adapt.api.world.BuildBatch;
import art.arcane.adapt.api.world.BuildBatches;
import art.arcane.adapt.api.world.BuildResult;
import art.arcane.adapt.api.mutation.MutationSlot;
import art.arcane.adapt.api.mutation.Signature;
import art.arcane.adapt.api.mutation.BlockUse;
import art.arcane.adapt.api.mutation.Mutations;
import art.arcane.adapt.api.mutation.Outcome;
import art.arcane.adapt.platform.entity.PlayerInventoryRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.block.BlockBreakEvent;
import art.arcane.adapt.platform.event.player.PlayerQuitEvent;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.BlockFace;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.world.BlockRef;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Pickaxes and Excavation, either leading, replacing Convergence: Earthshaker and Landshaper become one face that
 * takes stone and soft ground together. After a bore or a dig, what is left of the 3 by 3 face, and from level 2 the
 * faces behind it, breaks with the right tool from your hotbar, each tool paying for its own blocks: the stone first,
 * then the soft ground, a few blocks a tick.
 */
public final class MixedFace extends Signature<MixedFace.Config> {
  private static final int HOTBAR = 9;
  private static final float STEEP = 60F;
  private final Map<UUID, BlockRef> swings = new ConcurrentHashMap<>();

  public MixedFace(MutationSlot slot) {
    super(slot);
    setLocalizationKey(PairMessages.signatureKey(slot));
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("coarse_dirt"));
  }

  @Override
  public boolean paysOut() {
    return true;
  }

  @Override
  public void addSignatureStats(int level, StatLines v) {
    statLore(v, depth(level), 1);
    statLore(v, (depth(level) * 9) - 1, 2);
  }

  @Override
  public long cooldownMillis(int level) {
    return Math.max(0L, getConfig().cooldownMillis);
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(BlockBreakEvent e) {
    PlayerRef player = e.player();
    BlockRef origin = e.block();
    if (e.protectionProbe() || BonusBreaks.isActive(player)) {
      return;
    }
    ItemRef held = player.inventory().mainHand();
    boolean pickaxe = ItemKinds.isPickaxe(held);
    if ((!pickaxe && !ItemKinds.isShovel(held)) || getActiveLevel(player) <= 0) {
      return;
    }
    Earthshaker earthshaker = pickaxe ? ArchitectBuilds.find(Id.adapt("pickaxe/upgrade/earthshaker"), Earthshaker.class) : null;
    boolean bore = earthshaker != null && earthshaker.bores(player, origin);
    boolean dig = !pickaxe && ExcavationBlocks.isShovelable(origin.type())
        && active(Id.adapt("excavation/upgrade/landshaper"), Landshaper.class, player);
    if (!bore && !dig) {
      return;
    }
    swings.put(player.uuid(), origin);
    try {
      fire(player, null, origin.location(), Mutations.farmed(origin));
    } finally {
      swings.remove(player.uuid());
    }
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(PlayerQuitEvent e) {
    swings.remove(e.player().uuid());
  }

  @Override
  public Outcome run(BlockUse use) {
    PlayerRef player = use.player();
    BlockRef origin = swings.remove(player.uuid());
    if (origin == null) {
      return Outcome.MISSED;
    }
    Set<BlockRef> face = face(player, origin, depth(use.level()));
    if (face.isEmpty() || !AdaptApi.scheduler().runEntity(player, () -> clear(player, origin, face), Math.max(2, getConfig().delayTicks))) {
      return Outcome.MISSED;
    }
    return Outcome.INSTANT;
  }

  /** The 3 by 3 face around the mined block and, deeper along the swing, the faces behind it; the mined block left out. */
  Set<BlockRef> face(PlayerRef player, BlockRef origin, int depth) {
    Loc at = player.location();
    boolean steep = Math.abs(at.pitch()) > STEEP;
    BlockFace facing = PickaxeBore.facing(at.yaw());
    int dx = steep ? 0 : facing.dx();
    int dy = steep ? (at.pitch() > 0F ? -1 : 1) : 0;
    int dz = steep ? 0 : facing.dz();
    Set<BlockRef> face = new LinkedHashSet<>();
    for (int layer = 0; layer < depth; layer++) {
      BlockRef center = origin.relative(dx * layer, dy * layer, dz * layer);
      if (layer > 0) {
        face.add(center);
      }
      face.addAll(PickaxeBore.plane(this, player, center, 3, 3, (Id type) -> TunnelBuilder.stone(type)
          || ExcavationBlocks.isShovelable(type)));
    }
    face.remove(origin);
    return face;
  }

  /** Breaks the stone with the hotbar pickaxe, then the soft ground with the hotbar shovel, then hands the held slot back. */
  private void clear(PlayerRef player, BlockRef origin, Set<BlockRef> face) {
    if (!player.isOnline() || !player.world().uid().equals(origin.world().uid()) || BuildBatches.isRunning(player)) {
      return;
    }
    List<BlockRef> stone = new ArrayList<>(face.size());
    List<BlockRef> soft = new ArrayList<>(face.size());
    for (BlockRef block : face) {
      Id type = block.type();
      if (Mutations.farmed(block)) {
        continue;
      }
      if (TunnelBuilder.stone(type)) {
        stone.add(block);
      } else if (ExcavationBlocks.isShovelable(type)) {
        soft.add(block);
      }
    }
    PlayerInventoryRef inventory = player.inventory();
    int held = inventory.heldSlot();
    int pickaxe = slot(inventory, true);
    int shovel = slot(inventory, false);
    Runnable handBack = () -> {
      if (player.isOnline() && inventory.heldSlot() != held) {
        inventory.setHeldSlot(held);
      }
    };
    pass(player, stone, pickaxe, true, () -> pass(player, soft, shovel, false, handBack));
  }

  /** Breaks {@code blocks} as one paced batch with the tool in {@code slot}, then runs {@code then}. */
  private void pass(PlayerRef player, List<BlockRef> blocks, int slot, boolean pickaxe, Runnable then) {
    if (blocks.isEmpty() || slot < 0 || !player.isOnline()) {
      then.run();
      return;
    }
    player.inventory().setHeldSlot(slot);
    int durability = Math.max(0, getConfig().durabilityPerBlock);
    BuildBatch.Builder batch = BuildBatch.of(this, player)
        .pace(Math.max(1, getConfig().blocksPerTick))
        .onDone((BuildResult result) -> then.run());
    for (BlockRef block : blocks) {
      batch.breakWith(block, durability, pickaxe ? ItemKinds::isPickaxe : ItemKinds::isShovel);
    }
    if (!BuildBatches.start(batch.build())) {
      then.run();
    }
  }

  private int slot(PlayerInventoryRef inventory, boolean pickaxe) {
    if (pickaxe ? ItemKinds.isPickaxe(inventory.mainHand()) : ItemKinds.isShovel(inventory.mainHand())) {
      return inventory.heldSlot();
    }
    for (int slot = 0; slot < HOTBAR; slot++) {
      ItemRef item = inventory.item(slot);
      if (pickaxe ? ItemKinds.isPickaxe(item) : ItemKinds.isShovel(item)) {
        return slot;
      }
    }
    return -1;
  }

  private static <A extends Adaptation<?>> boolean active(Id id, Class<A> type, PlayerRef player) {
    A upgrade = ArchitectBuilds.find(id, type);
    return upgrade != null && upgrade.getActiveLevel(player) > 0;
  }

  int depth(int level) {
    return Math.max(1, level - getConfig().depthLevelOffset);
  }

  @ConfigDescription("Earthshaker and Landshaper clear one face of stone and soft ground together, each with its own tool.")
  public static class Config extends Signature.Config {
    @ConfigDoc(value = "Levels subtracted from the level to get the face depth; at 0 the depth equals the level.", impact = "Higher values keep the face shallower.")
    int depthLevelOffset = 0;
    @ConfigDoc(value = "Extra durability each tool loses per block it breaks for the face.", impact = "Higher values wear the tools out faster.")
    int durabilityPerBlock = 1;
    @ConfigDoc(value = "Face blocks broken per tick, before the server-wide cap.", impact = "Higher values clear the face sooner but cost more per tick.")
    int blocksPerTick = 8;
    @ConfigDoc(value = "Ticks after the swing before the rest of the face breaks, once the upgrade has bored or dug.", impact = "Raise it if the face breaks before the upgrade.")
    int delayTicks = 2;
    @ConfigDoc(value = "Cooldown after a face is cleared, in milliseconds.", impact = "Higher values clear fewer faces.")
    long cooldownMillis = 0;
  }
}
