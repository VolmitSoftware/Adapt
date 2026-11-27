package art.arcane.adapt.content.adaptation.nether.blocks;

import art.arcane.adapt.api.AdaptApi;
import art.arcane.adapt.api.config.ConfigDescription;
import art.arcane.adapt.api.config.ConfigDoc;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.fx.Particles;
import art.arcane.adapt.api.fx.Sounds;
import art.arcane.adapt.api.mutation.BlockUse;
import art.arcane.adapt.api.mutation.MutationBlock;
import art.arcane.adapt.api.mutation.Mutations;
import art.arcane.adapt.api.mutation.Outcome;
import art.arcane.adapt.api.mutation.StrikeBlock;
import art.arcane.adapt.api.mutation.StrikeForm;
import art.arcane.adapt.content.adaptation.pickaxe.PickaxeAutosmelt;
import art.arcane.adapt.content.skill.SkillNether;
import art.arcane.adapt.platform.entity.ItemEntityRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.block.BlockDropItemEvent;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Id;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.UUID;

/**
 * Primes the player: the drops of their next few broken blocks come out smelted, raw ores as ingots (silk-touched ores
 * through Autosmelt's ore table), sand as glass, logs as charcoal and so on. A block counts only when something in its
 * drops smelted, and a player-placed block is left alone. Unused charges run out after a while.
 */
public class NetherWorkStrike extends StrikeBlock<NetherWorkStrike.Config> {
  private static final Id CHARCOAL = Id.minecraft("charcoal");
  private static final Map<Id, Id> SMELTED = Map.ofEntries(
      Map.entry(Id.minecraft("raw_iron"), Id.minecraft("iron_ingot")),
      Map.entry(Id.minecraft("raw_gold"), Id.minecraft("gold_ingot")),
      Map.entry(Id.minecraft("raw_copper"), Id.minecraft("copper_ingot")),
      Map.entry(Id.minecraft("ancient_debris"), Id.minecraft("netherite_scrap")),
      Map.entry(Id.minecraft("cobblestone"), Id.minecraft("stone")),
      Map.entry(Id.minecraft("cobbled_deepslate"), Id.minecraft("deepslate")),
      Map.entry(Id.minecraft("stone"), Id.minecraft("smooth_stone")),
      Map.entry(Id.minecraft("sand"), Id.minecraft("glass")),
      Map.entry(Id.minecraft("red_sand"), Id.minecraft("glass")),
      Map.entry(Id.minecraft("netherrack"), Id.minecraft("nether_brick")),
      Map.entry(Id.minecraft("clay_ball"), Id.minecraft("brick")),
      Map.entry(Id.minecraft("clay"), Id.minecraft("terracotta")),
      Map.entry(Id.minecraft("basalt"), Id.minecraft("smooth_basalt")),
      Map.entry(Id.minecraft("sandstone"), Id.minecraft("smooth_sandstone")),
      Map.entry(Id.minecraft("red_sandstone"), Id.minecraft("smooth_red_sandstone")),
      Map.entry(Id.minecraft("quartz_block"), Id.minecraft("smooth_quartz")),
      Map.entry(Id.minecraft("stone_bricks"), Id.minecraft("cracked_stone_bricks")),
      Map.entry(Id.minecraft("nether_bricks"), Id.minecraft("cracked_nether_bricks")),
      Map.entry(Id.minecraft("deepslate_bricks"), Id.minecraft("cracked_deepslate_bricks")),
      Map.entry(Id.minecraft("deepslate_tiles"), Id.minecraft("cracked_deepslate_tiles")),
      Map.entry(Id.minecraft("polished_blackstone_bricks"), Id.minecraft("cracked_polished_blackstone_bricks")),
      Map.entry(Id.minecraft("wet_sponge"), Id.minecraft("sponge")),
      Map.entry(Id.minecraft("cactus"), Id.minecraft("green_dye")),
      Map.entry(Id.minecraft("sea_pickle"), Id.minecraft("lime_dye")),
      Map.entry(Id.minecraft("kelp"), Id.minecraft("dried_kelp")),
      Map.entry(Id.minecraft("chorus_fruit"), Id.minecraft("popped_chorus_fruit")));

  private final Map<UUID, Charges> charges = playerState();

  public NetherWorkStrike() {
    super(SkillNether.ID, StrikeForm.WORK);
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("furnace"));
  }

  @Override
  public boolean paysOut() {
    return true;
  }

  @Override
  public Outcome strike(BlockUse use) {
    int blocks = (int) Math.round(use.scaled(getConfig().blocksAtFirstLevel, getConfig().blocksAtTopLevel));
    if (blocks <= 0) {
      return Outcome.MISSED;
    }
    PlayerRef player = use.player();
    Charges held = charges.get(player.uuid());
    int kept = held == null || held.expired() ? 0 : held.blocks();
    long expiresAt = System.currentTimeMillis() + getConfig().primeMillis;
    charges.put(player.uuid(), new Charges(Math.max(kept, blocks), expiresAt));
    fx(player.location().add(0D, 1D, 0D), FxPriority.TRANSITION)
        .particle(Particles.FLAME, 8, 0D, 0D, 0D, 0.3D, 0.02D)
        .sound(Sounds.ITEM_FIRECHARGE_USE, 0.4F, 1.3F);
    return Outcome.INSTANT;
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(BlockDropItemEvent e) {
    PlayerRef player = e.player();
    Charges left = charges.get(player.uuid());
    if (left == null) {
      return;
    }
    if (left.expired()) {
      charges.remove(player.uuid());
      return;
    }
    if (e.items().isEmpty() || !hasActiveAdaptation(player) || Mutations.farmed(e.block())) {
      return;
    }
    boolean smelted = false;
    for (ItemEntityRef drop : e.items()) {
      ItemRef stack = drop.item();
      Id result = stack == null || stack.isEmpty() ? null : smelted(stack.type());
      if (result != null) {
        drop.setItem(AdaptApi.platform().host().items().create(result, stack.amount()));
        smelted = true;
      }
    }
    if (!smelted) {
      return;
    }
    if (left.blocks() <= 1) {
      charges.remove(player.uuid());
    } else {
      charges.put(player.uuid(), new Charges(left.blocks() - 1, left.expiresAt()));
    }
    addStat(player, "nether.mutated.blocks-smelted", 1);
    fx(e.block().location().add(0.5D, 0.5D, 0.5D), FxPriority.TRANSITION)
        .particle(Particles.FLAME, 6, 0D, 0.1D, 0D, 0.16D, 0.02D)
        .sound(Sounds.BLOCK_FURNACE_FIRE_CRACKLE, 0.6F, 1.1F);
  }

  static @Nullable Id smelted(Id item) {
    Id result = SMELTED.get(item);
    if (result != null) {
      return result;
    }
    if (item.isMinecraft() && (item.path().endsWith("_log") || item.path().endsWith("_wood"))) {
      return CHARCOAL;
    }
    return PickaxeAutosmelt.getIngotFor(item);
  }

  private record Charges(int blocks, long expiresAt) {
    boolean expired() {
      return System.currentTimeMillis() > expiresAt;
    }
  }

  @ConfigDescription("The next 3 blocks you break are smelted.")
  public static class Config extends MutationBlock.Config {
    @ConfigDoc(value = "Blocks smelted per use at level 1.", impact = "Higher values smelt more blocks.")
    double blocksAtFirstLevel = 3D;
    @ConfigDoc(value = "Blocks smelted per use at the top level.", impact = "Higher values smelt more blocks.")
    double blocksAtTopLevel = 5D;
    @ConfigDoc(value = "How long unused smelting charges last, in milliseconds.", impact = "Higher values keep the charges longer.")
    long primeMillis = 30_000L;
  }
}
