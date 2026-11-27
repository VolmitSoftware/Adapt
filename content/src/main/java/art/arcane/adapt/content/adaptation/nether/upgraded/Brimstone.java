package art.arcane.adapt.content.adaptation.nether.upgraded;

import art.arcane.adapt.api.config.ConfigDescription;
import art.arcane.adapt.api.config.ConfigDoc;
import art.arcane.adapt.api.content.Cooldowns;
import art.arcane.adapt.api.content.StatLines;
import art.arcane.adapt.api.mutation.MutationCue;
import art.arcane.adapt.api.mutation.Mutations;
import art.arcane.adapt.api.mutation.SkillUpgrade;
import art.arcane.adapt.api.text.Format;
import art.arcane.adapt.content.adaptation.nether.NetherBlastWard;
import art.arcane.adapt.content.adaptation.nether.NetherBlazeFeed;
import art.arcane.adapt.content.adaptation.nether.NetherBlazeLeech;
import art.arcane.adapt.content.adaptation.nether.NetherMagmaRetort;
import art.arcane.adapt.content.adaptation.nether.NetherPiglinBroker;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.block.BlockBreakEvent;
import art.arcane.adapt.platform.event.entity.EntityDamageByEntityEvent;
import art.arcane.adapt.platform.event.entity.EntityDamageEvent;
import art.arcane.adapt.platform.event.entity.EntityTargetEvent;
import art.arcane.adapt.platform.event.entity.PiglinBarterEvent;
import art.arcane.adapt.platform.event.player.InteractAction;
import art.arcane.adapt.platform.event.player.PlayerInteractEvent;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.world.BlockRef;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Magma Skin, Blaze Leech and Ghast Ward fused: melee attackers catch fire whether or not you burn, taking fire damage
 * feeds and heals you, and explosions and ghast fireballs barely hurt. Piglin Broker's barters run at its top level,
 * and piglins leave you alone without gold armor until you hit one, open what they guard or break their gold.
 */
public class Brimstone extends SkillUpgrade<Brimstone.Config> {
  public static final Id ID = Id.adapt("nether/upgrade/brimstone");

  private static final Id PIGLIN = Id.minecraft("piglin");
  private static final Set<Id> GUARDED = Set.of(Id.minecraft("chest"), Id.minecraft("trapped_chest"),
      Id.minecraft("barrel"), Id.minecraft("ender_chest"), Id.minecraft("gold_block"), Id.minecraft("raw_gold_block"),
      Id.minecraft("gilded_blackstone"), Id.minecraft("gold_ore"), Id.minecraft("deepslate_gold_ore"),
      Id.minecraft("nether_gold_ore"));
  private final Cooldowns feedCooldowns = cooldowns();
  private final Map<UUID, Long> provokedAt = playerState();

  public Brimstone() {
    super(ID);
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("blaze_powder"));
  }

  @Override
  public void addStats(int level, StatLines v) {
    statLore(v, Format.duration(getIgniteTicks(level) * 50D, 1), 1);
    statLore(v, Format.f(getFoodRestore(level), 0), 2);
    statLore(v, Format.pc(getBlastReduction(level), 0), 3);
    statLore(v, Format.pc(getConfig().barterExtraRollChance, 0), 4);
  }

  @Handle(priority = EventPriority.HIGH)
  public void on(PiglinBarterEvent e) {
    PlayerRef broker = NetherPiglinBroker.findBroker(this, e.piglin(), getConfig().brokerRange);
    if (broker == null) {
      return;
    }
    NetherPiglinBroker.markBrokered(e);
    if (Mutations.farmed(broker, e.piglin())) {
      return;
    }
    if (NetherPiglinBroker.improve(this, broker, e.outcome(), getConfig().barterExtraRollChance,
        getConfig().barterRareBonusChance, getConfig().barterAmountMultiplier)) {
      cue(broker, MutationCue.WORK);
    }
  }

  @Handle(priority = EventPriority.HIGH)
  public void on(EntityTargetEvent e) {
    if (!e.entity().type().equals(PIGLIN) || !(e.target() instanceof PlayerRef p) || provoked(p)) {
      return;
    }
    withActiveLevel(p, (int level) -> e.setCancelled(true));
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(PlayerInteractEvent e) {
    BlockRef block = e.clickedBlock();
    if (e.action() == InteractAction.RIGHT_CLICK_BLOCK && block != null && guarded(block.type())) {
      provoke(e.player());
    }
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(BlockBreakEvent e) {
    if (guarded(e.block().type())) {
      provoke(e.player());
    }
  }

  @Handle(priority = EventPriority.HIGHEST, ignoreCancelled = false)
  public void on(EntityDamageByEntityEvent e) {
    if (e.entity().type().equals(PIGLIN) && e.causingEntity() instanceof PlayerRef attacker) {
      provoke(attacker);
      return;
    }
    if (!(e.entity() instanceof PlayerRef p)) {
      return;
    }

    withActiveLevel(p, e, (int level) -> {
      if (NetherBlastWard.isGhastFireball(e.damager())) {
        NetherBlastWard.absorbFireball(this, p, e, getBlastReduction(level), getConfig().maxFireTicksAfterFireball);
        cue(p, MutationCue.BOON);
      } else if (NetherMagmaRetort.ignite(this, p, e, getIgniteTicks(level))) {
        xpFromAttacker(p, e.damager(), getConfig().xpPerIgnite);
        cue(p, MutationCue.FIGHT);
      }
    });
  }

  @Handle(priority = EventPriority.HIGH, ignoreCancelled = false)
  public void on(EntityDamageEvent e) {
    if (!(e.entity() instanceof PlayerRef p)
        || (e instanceof EntityDamageByEntityEvent byEntity && NetherBlastWard.isGhastFireball(byEntity.damager()))) {
      return;
    }

    withActiveLevel(p, e, (int level) -> {
      if (NetherBlastWard.isExplosion(e.cause())) {
        NetherBlastWard.absorbBlast(this, p, e, getBlastReduction(level));
        cue(p, MutationCue.BOON);
        return;
      }

      long cooldownMillis = getFeedCooldownMillis(level);
      if (NetherBlazeLeech.isFireDamage(e) && feedCooldowns.isReady(p.uuid(), cooldownMillis)) {
        feedCooldowns.mark(p.uuid());
        NetherBlazeFeed.feed(this, p, getFoodRestore(level), getConfig().saturationRestore, getRegenTicks(level),
            level >= getMaxLevel() ? getConfig().regenAmplifierAtMaxLevel : 0, true);
        cue(p, MutationCue.BOON);
      }
    });
  }

  private void provoke(PlayerRef p) {
    if (hasActiveAdaptation(p)) {
      provokedAt.put(p.uuid(), System.currentTimeMillis());
    }
  }

  private boolean provoked(PlayerRef p) {
    Long at = provokedAt.get(p.uuid());
    return at != null && System.currentTimeMillis() - at <= getConfig().provokedMillis;
  }

  private static boolean guarded(Id type) {
    return GUARDED.contains(type) || type.path().endsWith("shulker_box");
  }

  int getIgniteTicks(int level) {
    return Math.max(0, getConfig().igniteTicksBase + (level * getConfig().igniteTicksPerLevel));
  }

  double getFoodRestore(int level) {
    return getConfig().foodRestoreBase + (level * getConfig().foodRestorePerLevel);
  }

  int getRegenTicks(int level) {
    return Math.max(20, getConfig().regenTicksBase + (level * getConfig().regenTicksPerLevel));
  }

  long getFeedCooldownMillis(int level) {
    return (long) Math.max(getConfig().minimumFeedCooldownMillis,
        getConfig().feedCooldownMillisBase - (level * getConfig().feedCooldownMillisPerLevel));
  }

  double getBlastReduction(int level) {
    return Math.min(getConfig().maxBlastReduction, getConfig().blastReductionBase + (level * getConfig().blastReductionPerLevel));
  }

  @ConfigDescription("Melee attackers ignite, fire damage feeds you, and explosions barely hurt.")
  public static class Config extends SkillUpgrade.Config {
    @ConfigDoc(value = "Burn time set on a melee attacker before level scaling, in ticks.", impact = "Higher values burn attackers longer.")
    int igniteTicksBase = 60;
    @ConfigDoc(value = "Burn time added per level, in ticks.", impact = "Higher values reward higher levels more.")
    int igniteTicksPerLevel = 30;
    @ConfigDoc(value = "Skill XP for each attacker ignited.", impact = "Higher values reward taking hits.")
    double xpPerIgnite = 5;
    @ConfigDoc(value = "Hunger restored by fire before level scaling.", impact = "Higher values feed you more from each burn.")
    double foodRestoreBase = 1;
    @ConfigDoc(value = "Hunger restored by fire added per level.", impact = "Higher values reward higher levels more.")
    double foodRestorePerLevel = 1;
    @ConfigDoc(value = "Saturation restored by fire.", impact = "Higher values keep you full longer.")
    double saturationRestore = 1.0;
    @ConfigDoc(value = "Regeneration from fire before level scaling, in ticks.", impact = "Higher values heal longer from each burn.")
    int regenTicksBase = 40;
    @ConfigDoc(value = "Regeneration added per level, in ticks.", impact = "Higher values reward higher levels more.")
    int regenTicksPerLevel = 20;
    @ConfigDoc(value = "Regeneration amplifier at the maximum level; lower levels use amplifier 0.", impact = "Higher values heal faster at the top level.")
    int regenAmplifierAtMaxLevel = 1;
    @ConfigDoc(value = "Time between two fire feedings before level scaling, in milliseconds.", impact = "Lower values let fire feed you more often.")
    double feedCooldownMillisBase = 2400;
    @ConfigDoc(value = "Time between fire feedings removed per level, in milliseconds.", impact = "Higher values make higher levels feed more often.")
    double feedCooldownMillisPerLevel = 400;
    @ConfigDoc(value = "Shortest time between two fire feedings at any level, in milliseconds.", impact = "Lower values allow more frequent feeding.")
    double minimumFeedCooldownMillis = 500;
    @ConfigDoc(value = "Damage removed from explosions and ghast fireballs before level scaling.", impact = "Higher values make blasts hurt less.")
    double blastReductionBase = 0.5;
    @ConfigDoc(value = "Blast damage reduction added per level.", impact = "Higher values reward higher levels more.")
    double blastReductionPerLevel = 0.1;
    @ConfigDoc(value = "Highest blast damage reduction at any level.", impact = "Caps the reduction.")
    double maxBlastReduction = 0.85;
    @ConfigDoc(value = "Longest burn left after a ghast fireball hit, in ticks.", impact = "Lower values put out fireball burns faster.")
    int maxFireTicksAfterFireball = 20;
    @ConfigDoc(value = "Distance from a bartering piglin within which you broker its trade, in blocks.", impact = "Higher values broker barters from further away.")
    double brokerRange = 18;
    @ConfigDoc(value = "Chance a barter gives one of its items again.", impact = "Higher values make extra barter rolls more common.")
    double barterExtraRollChance = 0.55;
    @ConfigDoc(value = "Chance a barter adds a rare bonus item.", impact = "Higher values make rare bonus items more common.")
    double barterRareBonusChance = 0.23;
    @ConfigDoc(value = "Size of an extra barter roll compared to the item it repeats.", impact = "Higher values make extra rolls larger, up to a full stack.")
    double barterAmountMultiplier = 1.5;
    @ConfigDoc(value = "Time piglins may attack you after you hit one, open a container they guard or break their gold, in milliseconds.", impact = "Higher values keep piglins hostile longer after you provoke them.")
    long provokedMillis = 30000;
  }
}
