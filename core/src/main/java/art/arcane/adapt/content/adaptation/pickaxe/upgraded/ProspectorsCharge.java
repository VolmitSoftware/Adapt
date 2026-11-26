package art.arcane.adapt.content.adaptation.pickaxe.upgraded;

import art.arcane.adapt.api.batch.BuildBatch;
import art.arcane.adapt.api.batch.BuildBatches;
import art.arcane.adapt.api.batch.BuildResult;
import art.arcane.adapt.api.batch.Payments;
import art.arcane.adapt.api.mutation.engine.MutationPayouts;
import art.arcane.adapt.api.mutation.tool.MutationTool;
import art.arcane.adapt.api.mutation.tool.ToolUse;
import art.arcane.adapt.api.notification.AdaptHud;
import art.arcane.adapt.content.adaptation.pickaxe.PickaxeQuarrySense;
import art.arcane.adapt.content.adaptation.pickaxe.PickaxeVein;
import art.arcane.adapt.localization.AdaptLanguage;
import art.arcane.adapt.localization.catalog.PickaxeMessages;
import art.arcane.adapt.platform.TaskHandle;
import art.arcane.adapt.platform.entity.PlayerInventoryRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.recipe.Ingredient;
import art.arcane.adapt.platform.recipe.RecipeSpec;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.world.BlockRef;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.scheduling.Tasks;
import art.arcane.volmlib.util.localization.TextKey;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Earthshaker's charge: pressed into stone, it opens a rough sphere around that block after a short fuse. Every block
 * is a real break with the pickaxe the player holds when it goes off, run through a build batch; without one in hand
 * the charge comes back. Ores just outside the sphere glow.
 * Placed blocks, spawners, containers and bedrock are never opened, and each player has one live charge at a time.
 */
public final class ProspectorsCharge extends MutationTool<ProspectorsCharge.Data> {
  static final String ID = "tool-pickaxe-prospectors-charge";
  private static final int HOTBAR = 9;
  private static final int GLOW_SHELL = 2;
  private static final int MAX_GLOWS = 32;
  private static final double ROUGHNESS = 0.45D;

  private final Earthshaker earthshaker;
  private final Map<UUID, Blast> live = new ConcurrentHashMap<>();

  ProspectorsCharge(Earthshaker earthshaker) {
    super(ID, earthshaker, Id.minecraft("firework_star"), 1, Data.class, PickaxeMessages.PROSPECTORS_CHARGE_NAME);
    this.earthshaker = earthshaker;
  }

  /** Whether the stone each charge opens is discarded instead of dropped. */
  public static final class Data {
    boolean discardStone;
  }

  @Override
  public List<RecipeSpec> recipes() {
    return List.of(new RecipeSpec.Shapeless(Id.adapt(ID), List.of(Ingredient.of(Id.minecraft("gunpowder")),
        Ingredient.of(Id.minecraft("copper_ingot")), Ingredient.of(Id.minecraft("clay_ball"))), create(new Data())));
  }

  @Override
  protected int maxStackSize() {
    return 16;
  }

  @Override
  protected void applyLore(Data data, List<String> lines) {
    lines.add(C.GRAY + AdaptLanguage.text(data.discardStone
        ? PickaxeMessages.PROSPECTORS_CHARGE_STONE_DISCARD : PickaxeMessages.PROSPECTORS_CHARGE_STONE_DROP));
  }

  @Override
  protected void use(ToolUse use) {
    if (!use.rightClick()) {
      return;
    }
    PlayerRef player = use.player();
    Data data = read(use.item());
    Data settings = data == null ? new Data() : data;
    if (use.sneaking()) {
      settings.discardStone = !settings.discardStone;
      ItemRef item = use.item();
      write(item, settings);
      player.inventory().setMainHand(item);
      AdaptHud.actionBar(player, C.GRAY + AdaptLanguage.text(settings.discardStone
          ? PickaxeMessages.PROSPECTORS_CHARGE_STONE_DISCARD : PickaxeMessages.PROSPECTORS_CHARGE_STONE_DROP));
      return;
    }

    BlockRef wall = use.block();
    if (wall == null) {
      return;
    }
    if (live.containsKey(player.uuid()) || BuildBatches.get().isRunning(player)) {
      AdaptHud.actionBar(player, C.RED + AdaptLanguage.text(PickaxeMessages.PROSPECTORS_CHARGE_BUSY));
      return;
    }
    int slot = pickaxeSlot(player.inventory());
    if (slot < 0) {
      AdaptHud.actionBar(player, C.RED + AdaptLanguage.text(PickaxeMessages.PROSPECTORS_CHARGE_NO_PICKAXE));
      return;
    }
    if (!earthshaker.blastable(wall.type(), use.level(), player.inventory().item(slot))
        || MutationPayouts.farmed(wall) || !earthshaker.canBlockBreak(player, wall.location())) {
      AdaptHud.actionBar(player, C.RED + AdaptLanguage.text(PickaxeMessages.PROSPECTORS_CHARGE_STONE_ONLY));
      return;
    }

    ItemRef spent = takeOne(player);
    if (spent == null) {
      return;
    }
    Blast blast = new Blast(wall, settings.discardStone, spent, ConcurrentHashMap.newKeySet());
    live.put(player.uuid(), blast);
    int fuse = Math.max(1, earthshaker.getConfig().chargeFuseTicks);
    earthshaker.fuseFx(wall, fuse);
    if (!Tasks.runEntity(player, () -> detonate(player, blast), fuse, () -> live.remove(player.uuid(), blast))) {
      live.remove(player.uuid(), blast);
      Payments.give(player, List.of(spent));
    }
  }

  /** Whether the drops of {@code block}, broken by {@code player}'s live blast, are stone the charge discards. */
  boolean discards(PlayerRef player, BlockRef block, Id brokenType) {
    Blast blast = live.get(player.uuid());
    return blast != null && blast.discardStone() && !PickaxeVein.isOre(brokenType)
        && blast.opening().contains(block.pos().asLong());
  }

  private void detonate(PlayerRef player, Blast blast) {
    BlockRef center = blast.center();
    int level = level(player);
    ItemRef pickaxe = player.inventory().mainHand();
    if (level <= 0 || !earthshaker.isPickaxe(pickaxe) || !player.isOnline()
        || !player.world().uid().equals(center.world().uid()) || !Tasks.isOwnedByCurrentRegion(center.location())) {
      fizzle(player, blast, pickaxeSlot(player.inventory()) < 0 ? PickaxeMessages.PROSPECTORS_CHARGE_NO_PICKAXE : null);
      return;
    }

    List<BlockRef> targets = plan(player, center, earthshaker.getChargeRadius(level), level, pickaxe);
    if (targets.isEmpty()) {
      fizzle(player, blast, PickaxeMessages.PROSPECTORS_CHARGE_STONE_ONLY);
      return;
    }
    for (BlockRef target : targets) {
      blast.opening().add(target.pos().asLong());
    }

    TaskHandle[] watch = new TaskHandle[1];
    BuildBatch.Builder batch = BuildBatch.of(earthshaker, player)
        .pace(Math.max(1, earthshaker.getConfig().chargeBlocksPerTick))
        .onDone((BuildResult result) -> {
          if (watch[0] != null) {
            watch[0].cancel();
          }
          live.remove(player.uuid(), blast);
          if (result.broken() == 0) {
            Payments.give(player, List.of(blast.spent()));
          }
          earthshaker.chargeDone(player, opened(targets), result);
        });
    int durability = Math.max(0, earthshaker.getConfig().durabilityPerBonusBlock);
    for (BlockRef target : targets) {
      batch.breakWith(target, durability, earthshaker::isPickaxe);
    }
    if (!BuildBatches.get().start(batch.build())) {
      fizzle(player, blast, PickaxeMessages.PROSPECTORS_CHARGE_BUSY);
      return;
    }
    watch[0] = Tasks.repeatEntity(player, () -> {
      if (!earthshaker.isPickaxe(player.inventory().mainHand())) {
        BuildBatches.get().cancel(player);
      }
    }, 1, 1, () -> {
    });

    earthshaker.blastFx(center);
    glowNearbyOres(player, center, earthshaker.getChargeRadius(level));
  }

  private List<BlockRef> plan(PlayerRef player, BlockRef center, int radius, int level, ItemRef pickaxe) {
    ThreadLocalRandom random = ThreadLocalRandom.current();
    List<BlockRef> targets = new ArrayList<>();
    for (int dx = -radius; dx <= radius; dx++) {
      for (int dy = -radius; dy <= radius; dy++) {
        for (int dz = -radius; dz <= radius; dz++) {
          double edge = Math.max(0.5D, radius + random.nextDouble(-ROUGHNESS, ROUGHNESS));
          if ((dx * dx) + (dy * dy) + (dz * dz) > edge * edge) {
            continue;
          }
          BlockRef block = center.relative(dx, dy, dz);
          if (Tasks.isOwnedByCurrentRegion(block.location()) && earthshaker.blastable(block.type(), level, pickaxe)
              && !MutationPayouts.farmed(block) && earthshaker.canBlockBreak(player, block.location())) {
            targets.add(block);
          }
        }
      }
    }
    targets.sort(Comparator.comparingInt((BlockRef block) -> distanceSquared(center, block)));
    return targets;
  }

  private void glowNearbyOres(PlayerRef player, BlockRef center, int radius) {
    int outer = radius + GLOW_SHELL;
    int shown = 0;
    int glowTicks = Math.max(20, earthshaker.getConfig().chargeGlowTicks);
    for (int dx = -outer; dx <= outer && shown < MAX_GLOWS; dx++) {
      for (int dy = -outer; dy <= outer && shown < MAX_GLOWS; dy++) {
        for (int dz = -outer; dz <= outer && shown < MAX_GLOWS; dz++) {
          int distance = (dx * dx) + (dy * dy) + (dz * dz);
          if (distance <= radius * radius || distance > outer * outer) {
            continue;
          }
          BlockRef block = center.relative(dx, dy, dz);
          Id type = block.type();
          if (Tasks.isOwnedByCurrentRegion(block.location()) && PickaxeVein.isOre(type)
              && PickaxeQuarrySense.showOre(earthshaker, player, block.location(), type,
              PickaxeQuarrySense.oreColor(type), glowTicks)) {
            shown++;
          }
        }
      }
    }
  }

  private void fizzle(PlayerRef player, Blast blast, @Nullable TextKey reason) {
    live.remove(player.uuid(), blast);
    Payments.give(player, List.of(blast.spent()));
    if (reason != null) {
      AdaptHud.actionBar(player, C.RED + AdaptLanguage.text(reason));
    }
  }

  private @Nullable ItemRef takeOne(PlayerRef player) {
    ItemRef hand = player.inventory().mainHand();
    if (!is(hand) || hand.amount() <= 0) {
      return null;
    }
    ItemRef spent = hand.copyWithAmount(1);
    hand.setAmount(hand.amount() - 1);
    player.inventory().setMainHand(hand);
    return spent;
  }

  private int pickaxeSlot(PlayerInventoryRef inventory) {
    if (earthshaker.isPickaxe(inventory.mainHand())) {
      return inventory.heldSlot();
    }
    for (int slot = 0; slot < HOTBAR; slot++) {
      if (earthshaker.isPickaxe(inventory.item(slot))) {
        return slot;
      }
    }
    return -1;
  }

  private static List<BlockRef> opened(List<BlockRef> targets) {
    List<BlockRef> opened = new ArrayList<>(targets.size());
    for (BlockRef target : targets) {
      if (target.isAir()) {
        opened.add(target);
      }
    }
    return opened;
  }

  private static int distanceSquared(BlockRef a, BlockRef b) {
    int dx = a.x() - b.x();
    int dy = a.y() - b.y();
    int dz = a.z() - b.z();
    return (dx * dx) + (dy * dy) + (dz * dz);
  }

  private record Blast(BlockRef center, boolean discardStone, ItemRef spent, Set<Long> opening) {
  }
}
