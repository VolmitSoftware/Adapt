package art.arcane.adapt.content.adaptation.pickaxe.upgraded;

import art.arcane.adapt.api.AdaptApi;
import art.arcane.adapt.api.config.ConfigDescription;
import art.arcane.adapt.api.config.ConfigDoc;
import art.arcane.adapt.api.content.ReceiveCancelledEvents;
import art.arcane.adapt.api.content.StatLines;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.fx.Particles;
import art.arcane.adapt.api.hud.HudStatus;
import art.arcane.adapt.api.item.ItemKinds;
import art.arcane.adapt.api.mutation.MutationCue;
import art.arcane.adapt.api.mutation.Mutations;
import art.arcane.adapt.api.mutation.SkillUpgrade;
import art.arcane.adapt.api.text.Format;
import art.arcane.adapt.api.text.Text;
import art.arcane.adapt.api.text.TextFormat;
import art.arcane.adapt.content.adaptation.pickaxe.PickaxeAutosmelt;
import art.arcane.adapt.content.adaptation.pickaxe.PickaxeChisel;
import art.arcane.adapt.content.adaptation.pickaxe.PickaxeSilkSpawner;
import art.arcane.adapt.content.adaptation.pickaxe.PickaxeVein;
import art.arcane.adapt.api.world.BonusBreaks;
import art.arcane.adapt.platform.data.DataCodecs;
import art.arcane.adapt.platform.data.DataKey;
import art.arcane.adapt.platform.entity.ItemEntityRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.block.BlockBreakEvent;
import art.arcane.adapt.platform.event.block.BlockDropItemEvent;
import art.arcane.adapt.platform.event.block.BlockPlaceEvent;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.world.BlockRef;
import art.arcane.adapt.platform.world.BlockStateRef;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static art.arcane.adapt.api.world.BlockFlag.VEIN_MINED;

/**
 * Veinminer, Autosmelt and Ore Chisel fused: a mined ore takes its whole vein, smelted, plus one bonus ore. The bonus
 * needs a vein of natural ores broken without silk touch, so placed or silk-touched ores never come back richer. From
 * level 2 a spawner mined while sneaking without silk touch drops itself for pickaxe durability and no XP; from level 3
 * it names its mob and can be placed only where the placer may build. A placed spawner never drops XP.
 */
public class Motherlode extends SkillUpgrade<Motherlode.Config> {
  public static final Id ID = Id.adapt("pickaxe/upgrade/motherlode");

  private static final Id SILK_TOUCH = Id.minecraft("silk_touch");
  private static final DataKey<String> WARDED_SPAWNER = DataKey.adapt("motherlode_warded_spawner", DataCodecs.STRING);

  private final Map<UUID, PendingSpawner> pendingSpawners = playerState();

  public Motherlode() {
    super(ID);
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("raw_gold_block"));
  }

  @Override
  public void addStats(int level, StatLines v) {
    statLore(v, getVeinRadius(level), 1);
    statLore(v, getConfig().maxBlocks, 2);
    statLore(v, getConfig().bonusOres, 3);
    if (level >= getConfig().spawnerLevel) {
      statLore(v, TextFormat.RED, "- ", getConfig().spawnerDurability, 4);
    }
  }

  @Handle(priority = EventPriority.HIGH)
  public void on(BlockBreakEvent e) {
    BlockRef origin = e.block();
    Id ore = origin.type();
    PlayerRef player = e.player();
    if (PickaxeSilkSpawner.SPAWNER.equals(ore)) {
      guardSpawner(e, player);
      return;
    }
    if (VEIN_MINED.get(origin) || !PickaxeVein.isVeinminable(ore) || BonusBreaks.isActive(player)) {
      return;
    }

    if (!ItemKinds.isPickaxe(player.inventory().mainHand())) {
      return;
    }
    int level = getActiveLevel(player);
    if (level <= 0) {
      return;
    }

    Id family = PickaxeVein.veinFamily(ore);
    List<BlockRef> siblings = PickaxeVein.siblings(this, player, origin, getVeinRadius(level), getConfig().maxBlocks);
    List<BlockRef> naturalSiblings = naturalSiblings(origin, siblings, player.inventory().mainHand());
    AdaptApi.scheduler().runEntity(player, () -> mineVein(player, origin, ore, family, siblings, naturalSiblings), 1);
  }

  @Handle(priority = EventPriority.HIGHEST)
  public void on(BlockDropItemEvent e) {
    PlayerRef player = e.player();
    Id ore = e.brokenState().type();
    ItemRef tool = player.inventory().mainHand();
    if (PickaxeAutosmelt.getIngotFor(ore) == null || e.items().isEmpty() || !ItemKinds.isPickaxe(tool)
        || !e.brokenState().isPreferredTool(tool) || tool.enchantments().containsKey(SILK_TOUCH)) {
      return;
    }

    BlockActionContext context = resolveBlockBreakContext(player, e.block().location());
    int extraLevel = context == null || Mutations.farmed(e.block()) ? 0 : context.level();
    if (context != null && PickaxeAutosmelt.replaceRawDrops(e, ore, this, extraLevel)) {
      addStat(player, "pickaxe.motherlode.ores-smelted", 1);
    }
  }

  @Handle(priority = EventPriority.HIGH)
  public void onSpawnerDrop(BlockDropItemEvent e) {
    PlayerRef player = e.player();
    Id mob = e.brokenSpawnerType();
    if (mob == null || !PickaxeSilkSpawner.SPAWNER.equals(e.brokenState().type())) {
      return;
    }
    int level = spawnerLevel(player, e.block(), e.brokenState());
    if (level <= 0 || !BonusBreaks.canCharge(player.inventory().mainHand(), getConfig().spawnerDurability)) {
      return;
    }

    ItemRef spawner = PickaxeSilkSpawner.createSpawnerItem(mob);
    if (level >= getConfig().spawnerDisplayLevel) {
      spawner.data().set(WARDED_SPAWNER, mob.toString());
      spawner.setLore(List.of(TextFormat.GRAY + Text.key("adapt.upgrade.pickaxe.motherlode.spawner_mob").withMarkup("mob", Format.capitalizeWords(mob.path().replace('_', ' '))).resolve()));
    }
    pendingSpawners.put(player.uuid(), new PendingSpawner(e, PickaxeSilkSpawner.dropSpawner(e, player, spawner)));
  }

  @Handle(priority = EventPriority.HIGHEST)
  public void onSpawnerKept(BlockDropItemEvent e) {
    PlayerRef player = e.player();
    PendingSpawner pending = pendingSpawners.remove(player.uuid());
    if (pending == null || pending.event() != e || !e.items().contains(pending.item())) {
      return;
    }
    int cost = getConfig().spawnerDurability;
    payDurabilityCost(player, "spawner", cost, () -> {
      ItemRef pickaxe = player.inventory().mainHand();
      pickaxe.setDamage(pickaxe.damage() + cost);
      player.inventory().setMainHand(pickaxe);
      return true;
    });
    addStat(player, "pickaxe.motherlode.spawners-kept", 1);
    cue(player, MutationCue.WORK);
    fx(e.block().center(), FxPriority.GAMEPLAY).ring(Particles.SOUL, 1.2D, 14, 0.4D);
  }

  @ReceiveCancelledEvents
  @Handle(priority = EventPriority.MONITOR, ignoreCancelled = false)
  public void onSpawnerSettled(BlockDropItemEvent e) {
    PendingSpawner pending = pendingSpawners.get(e.player().uuid());
    if (pending == null || pending.event() != e) {
      return;
    }
    pendingSpawners.remove(e.player().uuid(), pending);
    if (e.isCancelled() && pending.item().isValid()) {
      pending.item().remove();
    }
  }

  @Handle(priority = EventPriority.HIGH)
  public void on(BlockPlaceEvent e) {
    if (e.protectionProbe()) {
      return;
    }
    ItemRef item = e.itemInHand();
    if (item != null && !item.isEmpty() && item.data().get(WARDED_SPAWNER) != null
        && !canBlockPlace(e.player(), e.block().location())) {
      e.setCancelled(true);
    }
  }

  /**
   * A kept spawner and a placed one drop no XP. A pickaxe that cannot pay stops a keep, so the spawner is never lost;
   * without sneaking it breaks as usual.
   */
  private void guardSpawner(BlockBreakEvent e, PlayerRef player) {
    if (Mutations.farmed(e.block())) {
      e.setExperience(0);
    }
    if (spawnerLevel(player, e.block(), e.block().state()) <= 0) {
      return;
    }
    if (!BonusBreaks.canCharge(player.inventory().mainHand(), getConfig().spawnerDurability)) {
      e.setCancelled(true);
      HudStatus.show(player, TextFormat.RED + Text.key("adapt.upgrade.pickaxe.motherlode.spawner_worn").withMarkup("durability", String.valueOf(getConfig().spawnerDurability)).resolve());
      return;
    }
    e.setExperience(0);
  }

  /**
   * The Motherlode level that keeps this spawner, or 0 when it does not: the player must sneak, as with Silk Spawner,
   * and silk touch is left to Silk Spawner.
   */
  private int spawnerLevel(PlayerRef player, BlockRef block, BlockStateRef state) {
    ItemRef tool = player.inventory().mainHand();
    if (!player.isSneaking() || BonusBreaks.isActive(player) || !ItemKinds.isPickaxe(tool) || !state.isPreferredTool(tool)
        || tool.enchantments().containsKey(SILK_TOUCH)) {
      return 0;
    }
    BlockActionContext context = resolveBlockBreakContext(player, block.location());
    return context == null || context.level() < getConfig().spawnerLevel ? 0 : context.level();
  }

  private void mineVein(PlayerRef player, BlockRef origin, Id ore, Id family, List<BlockRef> siblings,
                        List<BlockRef> natural) {
    List<BlockRef> mined = new ArrayList<>(siblings.size());
    int broken = PickaxeVein.breakSiblings(this, player, origin, family, siblings, (Id type) -> true,
        (BlockRef block, BlockStateRef state, int count) -> {
          mined.add(block);
          fx(block.center(), FxPriority.TRAIL).particle(Particles.blockCrack(state), 4, 0, 0, 0, 0.3, 0.0);
        });
    if (broken <= 0) {
      return;
    }

    ItemRef bonus = naturalVeinBroken(family, natural) >= getConfig().bonusVeinSize ? bonusOre(ore) : null;
    if (bonus != null) {
      origin.world().dropItemNaturally(origin.center().position(), bonus);
    }
    xpOnBlocks(player, mined, getConfig().xpPerOre);
    addStat(player, "pickaxe.motherlode.ores-mined", broken);
    cue(player, MutationCue.WORK);
    fx(origin.center(), FxPriority.GAMEPLAY)
        .ring(Particles.WAX_ON, Math.min(3.0D, 0.8D + (broken * 0.05D)), Math.min(24, 8 + broken), 0.2D);
  }

  /** The siblings that may count toward the bonus, or null when the origin was placed or the tool has silk touch. */
  private static List<BlockRef> naturalSiblings(BlockRef origin, List<BlockRef> siblings, ItemRef tool) {
    if (Mutations.farmed(origin) || tool.enchantments().containsKey(SILK_TOUCH)) {
      return null;
    }
    List<BlockRef> natural = new ArrayList<>();
    for (BlockRef sibling : siblings) {
      if (!Mutations.farmed(sibling)) {
        natural.add(sibling);
      }
    }
    return natural;
  }

  private static int naturalVeinBroken(Id family, List<BlockRef> natural) {
    if (natural == null) {
      return 0;
    }
    int broken = 1;
    for (BlockRef block : natural) {
      if (!PickaxeVein.veinFamily(block.type()).equals(family)) {
        broken++;
      }
    }
    return broken;
  }

  private ItemRef bonusOre(Id ore) {
    int amount = Math.max(0, getConfig().bonusOres);
    Id ingot = PickaxeAutosmelt.getIngotFor(ore);
    if (amount <= 0) {
      return null;
    }
    if (ingot != null) {
      return AdaptApi.platform().host().items().create(ingot, amount);
    }
    ItemRef chiseled = PickaxeChisel.getDropFor(ore);
    if (chiseled != null) {
      chiseled.setAmount(amount);
    }
    return chiseled;
  }

  int getVeinRadius(int level) {
    return getConfig().veinRadiusBase + level;
  }

  private record PendingSpawner(BlockDropItemEvent event, ItemEntityRef item) {
  }

  @ConfigDescription("Mining an ore breaks its whole vein, smelts the drops and adds a bonus ore; mined spawners drop from level 2.")
  public static class Config extends SkillUpgrade.Config {
    @ConfigDoc(value = "Vein radius in blocks before adding the level.", impact = "Higher values reach farther along a vein.")
    int veinRadiusBase = 3;
    @ConfigDoc(value = "Most blocks one vein can break.", impact = "Higher values allow larger veins but cost more block updates.")
    int maxBlocks = 64;
    @ConfigDoc(value = "Extra ores dropped for each vein.", impact = "Higher values make every vein richer.")
    int bonusOres = 1;
    @ConfigDoc(value = "Natural ores a vein needs, the mined one included, before it drops the bonus.", impact = "Higher values keep the bonus for larger veins.")
    int bonusVeinSize = 3;
    @ConfigDoc(value = "Skill XP for each ore in the vein.", impact = "Higher values reward large veins.")
    double xpPerOre = 2;
    @ConfigDoc(value = "Level from which a spawner mined while sneaking without silk touch drops itself.", impact = "Higher values keep spawner pickup for higher levels.")
    int spawnerLevel = 2;
    @ConfigDoc(value = "Pickaxe durability each kept spawner costs.", impact = "Higher values make spawner pickup wear the pickaxe faster; a pickaxe that cannot pay stops a sneaking break.")
    int spawnerDurability = 200;
    @ConfigDoc(value = "Level from which a kept spawner names its mob and can be placed only where the placer may build.", impact = "Higher values keep the label and placement rule for higher levels.")
    int spawnerDisplayLevel = 3;
  }
}
