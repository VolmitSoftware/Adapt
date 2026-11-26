package art.arcane.adapt.content.adaptation.discovery.upgraded;

import art.arcane.adapt.api.adaptation.Cooldowns;
import art.arcane.adapt.api.adaptation.StatLines;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.fx.Particles;
import art.arcane.adapt.api.mutation.sound.MutationCue;
import art.arcane.adapt.api.mutation.upgrade.SkillUpgrade;
import art.arcane.adapt.api.mutation.upgrade.UpgradeXp;
import art.arcane.adapt.api.world.AdaptPlayer;
import art.arcane.adapt.api.world.PlayerData;
import art.arcane.adapt.content.adaptation.discovery.DiscoveryFieldNotes;
import art.arcane.adapt.content.adaptation.discovery.DiscoveryRelicAppraiser;
import art.arcane.adapt.content.adaptation.discovery.DiscoveryTrailblazer;
import art.arcane.adapt.content.adaptation.discovery.FirstVisits;
import art.arcane.adapt.content.adaptation.discovery.RelicAppraisal;
import art.arcane.adapt.content.adaptation.discovery.SpeciesNotes;
import art.arcane.adapt.platform.entity.LivingRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.block.BlockDropItemEvent;
import art.arcane.adapt.platform.event.block.BlockPlaceEvent;
import art.arcane.adapt.platform.event.entity.EntityDamageByEntityEvent;
import art.arcane.adapt.platform.event.entity.EntityDeathEvent;
import art.arcane.adapt.platform.event.player.InteractAction;
import art.arcane.adapt.platform.event.player.PlayerInteractEvent;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.util.common.scheduling.Tasks;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.adapt.util.config.ConfigDoc;
import art.arcane.volmlib.util.format.Form;

import java.util.UUID;

/**
 * Trailblazer, Field Notes and Relic Appraiser fused: first visits, first kills of a species and appraised relics pay
 * more, and each banks a permanent payout bonus; kills also bank damage against their species.
 */
public class ExplorersLuck extends SkillUpgrade<ExplorersLuck.Config> {
  static final String BANK = "discovery.explorers-luck.bank";
  private static final long SCAN_INTERVAL_MILLIS = 1200L;
  private static final long APPRAISE_COOLDOWN_MILLIS = 400L;
  private final Cooldowns scanThrottle = cooldowns();
  private final Cooldowns appraiseCooldowns = cooldowns();

  public ExplorersLuck() {
    super("upgraded-discovery-explorers-luck");
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("filled_map"));
    setInterval(600);
  }

  @Override
  public void addStats(int level, StatLines v) {
    statLore(v, Form.pc(getPayout(level), 0), 1);
    statLore(v, Form.pc(getConfig().bankPerDiscovery, 1), 2);
    statLore(v, Form.f(getSpeciesCap(level), 1), 3);
  }

  @Override
  protected boolean usesLearnerBoundTicking() {
    return true;
  }

  @Override
  public void onTick() {
    for (AdaptPlayer adaptPlayer : learnedCandidates(System.currentTimeMillis())) {
      PlayerRef player = adaptPlayer.getPlayer();
      if (player == null || !player.isOnline() || !scanThrottle.isReady(player.uuid(), SCAN_INTERVAL_MILLIS)) {
        continue;
      }
      scanThrottle.mark(player.uuid());
      Tasks.runEntity(player, () -> visitOwned(player));
    }
  }

  void visitOwned(PlayerRef player) {
    int level = getActiveLevel(player);
    if (level <= 0) {
      return;
    }
    PlayerData data = getPlayer(player).getData();
    FirstVisits.Visit visit = FirstVisits.record(data, player);
    if (visit == FirstVisits.Visit.NONE) {
      return;
    }
    double reward = DiscoveryTrailblazer.firstVisitXp(getLevelPercent(level), getConfig().visitXpBase, getConfig().visitXpFactor);
    if (visit == FirstVisits.Visit.STRUCTURE) {
      reward *= getConfig().structureXpMultiplier;
    }
    pay(player, data, level, reward);
  }

  @Handle(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void on(EntityDeathEvent e) {
    if (e.platformCancelled()) {
      return;
    }
    LivingRef victim = e.entity();
    PlayerRef killer = SpeciesNotes.notedKiller(victim);
    if (killer == null) {
      return;
    }
    if (!UpgradeXp.naturalMob(killer, victim)) {
      return;
    }
    Id species = victim.type();
    Tasks.runEntity(killer, () -> killOwned(killer, species));
  }

  private void killOwned(PlayerRef killer, Id species) {
    int level = getActiveLevel(killer);
    if (level <= 0) {
      return;
    }
    PlayerData data = getPlayer(killer).getData();
    if (SpeciesNotes.record(data, species, getConfig().bonusPerKill, getSpeciesCap(level))) {
      pay(killer, data, level, DiscoveryFieldNotes.firstKillXp(getLevelPercent(level), getConfig().killXpBase, getConfig().killXpFactor));
    }
  }

  @Handle(priority = EventPriority.HIGH, ignoreCancelled = true)
  public void on(EntityDamageByEntityEvent e) {
    if (!(e.entity() instanceof LivingRef victim) || victim instanceof PlayerRef) {
      return;
    }
    PlayerRef attacker = SpeciesNotes.attacker(e.damager());
    if (attacker == null || attacker.uuid().equals(victim.uuid())) {
      return;
    }
    int level = getActiveLevel(attacker);
    if (level <= 0) {
      return;
    }
    double bonus = SpeciesNotes.bankedBonus(getPlayer(attacker).getData(), victim.type(), getSpeciesCap(level));
    if (bonus > 0D) {
      e.setDamage(e.damage() + bonus);
    }
  }

  @Handle(priority = EventPriority.HIGHEST)
  public void on(PlayerInteractEvent e) {
    if (!RelicAppraisal.isAppraisalClick(e)) {
      return;
    }
    PlayerRef player = e.player();
    int level = getActiveLevel(player);
    if (level <= 0) {
      return;
    }
    ItemRef hand = player.inventory().mainHand();
    UUID id = player.uuid();
    if (hand.isEmpty() || !DiscoveryRelicAppraiser.isAppraisable(hand) || RelicAppraisal.isAppraised(hand)
        || !appraiseCooldowns.isReady(id, APPRAISE_COOLDOWN_MILLIS)) {
      return;
    }
    appraiseCooldowns.mark(id);
    if (e.action() == InteractAction.RIGHT_CLICK_BLOCK) {
      e.setCancelled(true);
    }

    double reward = DiscoveryRelicAppraiser.appraiseXp(getLevelPercent(level), getConfig().relicXpBase, getConfig().relicXpFactor)
        * DiscoveryRelicAppraiser.rarityWeight(hand.type(), getConfig().discRarityWeight, getConfig().headRarityWeight,
        getConfig().trimRarityWeight, getConfig().sherdRarityWeight);
    RelicAppraisal.stamp(player, hand);
    RelicAppraisal.grantRandomSkillXp(player, getSkill(), getConfig().randomSkillXpMin, getConfig().randomSkillXpMax);
    pay(player, getPlayer(player).getData(), level, reward);
  }

  @Handle(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void on(BlockPlaceEvent e) {
    RelicAppraisal.rememberPlaced(e);
  }

  @Handle(priority = EventPriority.LOWEST, ignoreCancelled = true)
  public void on(BlockDropItemEvent e) {
    RelicAppraisal.restorePlaced(e);
  }

  private void pay(PlayerRef player, PlayerData data, int level, double reward) {
    double bank = data.getStat(BANK);
    xpOnDiscovery(player, reward * getPayout(level) * (1D + bank));
    double banked = Math.min(getConfig().maxBank, bank + getConfig().bankPerDiscovery);
    if (banked > bank) {
      data.addStat(BANK, banked - bank);
    }
    addStat(player, "discovery.explorers-luck.discoveries", 1);
    cue(player, banked >= getConfig().maxBank && bank < getConfig().maxBank ? MutationCue.BOON : MutationCue.WORK);
    fx(player.eyeLocation(), FxPriority.TRANSITION).particle(Particles.WAX_ON, 6, 0, 0.3, 0, 0.3, 0.02);
  }

  double getPayout(int level) {
    return getConfig().payoutBase + (level * getConfig().payoutPerLevel);
  }

  double getSpeciesCap(int level) {
    return DiscoveryFieldNotes.perSpeciesCap(getLevelPercent(level), getConfig().speciesCapBase, getConfig().speciesCapFactor);
  }

  @ConfigDescription("First visits, first kills and relics pay more and bank permanent bonuses.")
  public static class Config extends SkillUpgrade.Config {
    @ConfigDoc(value = "Payout multiplier on every discovery before level scaling.", impact = "Higher values make every discovery pay more.")
    double payoutBase = 1.25;
    @ConfigDoc(value = "Payout multiplier added per level.", impact = "Higher values reward higher levels more.")
    double payoutPerLevel = 0.25;
    @ConfigDoc(value = "Permanent payout bonus banked by each discovery.", impact = "Higher values reach the bank cap in fewer discoveries.")
    double bankPerDiscovery = 0.01;
    @ConfigDoc(value = "Largest permanent payout bonus the bank holds.", impact = "Higher values let long explorations pay far more.")
    double maxBank = 0.5;
    @ConfigDoc(value = "First-visit XP at level 1.", impact = "Higher values make new biomes and structures pay more.")
    double visitXpBase = 40;
    @ConfigDoc(value = "First-visit XP gained across levels.", impact = "Higher values reward higher levels more.")
    double visitXpFactor = 160;
    @ConfigDoc(value = "Multiplier on first visits to a structure type instead of a biome.", impact = "Higher values make structures pay more than biomes.")
    double structureXpMultiplier = 2.5;
    @ConfigDoc(value = "First-kill XP at level 1.", impact = "Higher values make each new species pay more.")
    double killXpBase = 120;
    @ConfigDoc(value = "First-kill XP gained across levels.", impact = "Higher values reward higher levels more.")
    double killXpFactor = 240;
    @ConfigDoc(value = "Damage banked against a species per kill until its cap.", impact = "Higher values reach the cap in fewer kills.")
    double bonusPerKill = 0.25;
    @ConfigDoc(value = "Damage cap per species at level 1.", impact = "Higher values raise the banked damage ceiling.")
    double speciesCapBase = 1.0;
    @ConfigDoc(value = "Damage cap per species gained across levels.", impact = "Higher values reward higher levels more.")
    double speciesCapFactor = 3.0;
    @ConfigDoc(value = "Relic appraisal XP at level 1.", impact = "Higher values make relics pay more.")
    double relicXpBase = 60;
    @ConfigDoc(value = "Relic appraisal XP gained across levels.", impact = "Higher values reward higher levels more.")
    double relicXpFactor = 180;
    @ConfigDoc(value = "Least XP one appraisal gives a random other skill; clamped to 0-10,000.", impact = "Set both bounds to zero to turn the extra payout off.")
    double randomSkillXpMin = 30;
    @ConfigDoc(value = "Most XP one appraisal gives a random other skill; clamped to 0-10,000.", impact = "Higher values make the extra payout larger.")
    double randomSkillXpMax = 90;
    @ConfigDoc(value = "Rarity multiplier for music discs.", impact = "Higher values pay more for discs.")
    double discRarityWeight = 1.5;
    @ConfigDoc(value = "Rarity multiplier for heads and skulls.", impact = "Higher values pay more for mob heads.")
    double headRarityWeight = 1.4;
    @ConfigDoc(value = "Rarity multiplier for armor trim templates.", impact = "Higher values pay more for trims.")
    double trimRarityWeight = 1.25;
    @ConfigDoc(value = "Rarity multiplier for pottery sherds.", impact = "Higher values pay more for sherds.")
    double sherdRarityWeight = 1.0;
  }
}
