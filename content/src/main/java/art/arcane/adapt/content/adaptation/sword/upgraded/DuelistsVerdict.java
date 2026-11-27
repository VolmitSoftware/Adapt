package art.arcane.adapt.content.adaptation.sword.upgraded;

import art.arcane.adapt.api.AdaptApi;
import art.arcane.adapt.api.config.ConfigDescription;
import art.arcane.adapt.api.config.ConfigDoc;
import art.arcane.adapt.api.content.Adaptation;
import art.arcane.adapt.api.content.Cooldowns;
import art.arcane.adapt.api.content.StatLines;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.fx.Particles;
import art.arcane.adapt.api.fx.Sounds;
import art.arcane.adapt.api.item.ItemKinds;
import art.arcane.adapt.api.mutation.MutationCue;
import art.arcane.adapt.api.mutation.Mutations;
import art.arcane.adapt.api.mutation.SkillUpgrade;
import art.arcane.adapt.api.text.Format;
import art.arcane.adapt.api.text.Text;
import art.arcane.adapt.api.text.TextFormat;
import art.arcane.adapt.content.adaptation.sword.SwordBladeLore;
import art.arcane.adapt.content.adaptation.sword.SwordDuels;
import art.arcane.adapt.content.adaptation.sword.SwordsHeirloomEdge;
import art.arcane.adapt.content.adaptation.sword.SwordsLungeStrike;
import art.arcane.adapt.content.adaptation.sword.SwordsRiposteWindow;
import art.arcane.adapt.content.golem.GolemKillCredit;
import art.arcane.adapt.platform.data.DataCodecs;
import art.arcane.adapt.platform.data.DataHolder;
import art.arcane.adapt.platform.data.DataKey;
import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.EntityTrait;
import art.arcane.adapt.platform.entity.Hand;
import art.arcane.adapt.platform.entity.LivingRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.entity.EntityDamageByEntityEvent;
import art.arcane.adapt.platform.event.entity.EntityDeathEvent;
import art.arcane.adapt.platform.event.inventory.PrepareAnvilEvent;
import art.arcane.adapt.platform.event.player.InteractAction;
import art.arcane.adapt.platform.event.player.PlayerInteractEvent;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.world.RayHit;

import java.util.Map;
import java.util.UUID;


/**
 * Awakened Blade, built on Duelist's Focus and Executioner's Edge. Against exactly one engaged foe you deal more and
 * take less on any sword. A sword named at an anvil is awakened for its namer; natural kills with it bank toward three
 * techniques on that sword only (Riposte, Lunge, Verdict) and a capped flat damage bonus. Awakening another sword puts
 * the previous one to sleep.
 */
public class DuelistsVerdict extends SkillUpgrade<DuelistsVerdict.Config> {
  public static final Id ID = Id.adapt("swords/upgrade/duelists_verdict");

  private static final DataKey<UUID> OWNER_KEY = DataKey.adapt("awakened_blade_owner", DataCodecs.UUID_STRING);
  private static final DataKey<Long> AWAKENED_AT_KEY = DataKey.adapt("awakened_blade_at", DataCodecs.LONG);
  private static final DataKey<Integer> KILLS_KEY = DataKey.adapt("awakened_blade_kills", DataCodecs.INT);
  private static final DataKey<String> LORE_KEY = DataKey.adapt("awakened_blade_lore", DataCodecs.STRING);
  private static final String CURRENT_BLADE = "awakenedBladeAt";
  private static final String LAST_AWAKENING = "awakenedBladeIssued";
  private static final double VANILLA_CRIT = 1.5D;

  private final Map<UUID, Long> riposteUntil = playerState();
  private final Cooldowns lungeCooldowns = cooldowns();

  public DuelistsVerdict() {
    super(ID);
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("diamond_sword"));
  }

  @Override
  public void addStats(int level, StatLines v) {
    statLore(v, Format.pc(getBonusDamage(level), 0), 1);
    statLore(v, Format.pc(getDamageReduction(level), 0), 2);
    statLore(v, Format.pc(getFinishingBonus(level), 0), 3);
    statLore(v, "+" + Format.f(getBladeBonusCap(level), 1), 4);
    statLore(v, getConfig().riposteKills + " / " + getConfig().lungeKills + " / " + getConfig().verdictKills, 5);
  }

  @Handle(priority = EventPriority.HIGHEST, ignoreCancelled = false)
  public void on(PrepareAnvilEvent e) {
    PlayerRef p = e.player();
    String rename = e.renameText();
    ItemRef result = e.result();
    if (p == null || rename == null || rename.isBlank() || result == null || !ItemKinds.isSword(result) || getActiveLevel(p) <= 0) {
      return;
    }

    DataHolder data = result.data();
    if (!p.uuid().equals(data.get(OWNER_KEY))) {
      data.set(OWNER_KEY, p.uuid());
      data.set(KILLS_KEY, 0);
    }
    long awakenedAt = Math.max(System.currentTimeMillis(), getStorageLong(p, LAST_AWAKENING, 0L) + 1L);
    setStorage(p, LAST_AWAKENING, awakenedAt);
    data.set(AWAKENED_AT_KEY, awakenedAt);
    applyBladeLore(result);
    e.setResult(result);
  }

  @Handle(priority = EventPriority.MONITOR, ignoreCancelled = false)
  public void on(EntityDeathEvent e) {
    PlayerRef p = e.killer();
    LivingRef victim = e.entity();
    if (p == null || victim instanceof PlayerRef || e.directDamager() != p || GolemKillCredit.isGolemKill(victim) || Mutations.farmed(p, victim)) {
      return;
    }

    AdaptApi.scheduler().runEntity(p, () -> bankKill(p));
  }

  @Handle(priority = EventPriority.HIGHEST)
  public void on(EntityDamageByEntityEvent e) {
    MeleeContext combat = resolveMeleeContext(e, ItemKinds::isSword);
    if (combat != null) {
      strike(e, combat);
    }

    if (e.entity() instanceof PlayerRef victim) {
      defend(e, victim);
    }
  }

  @Handle(priority = EventPriority.HIGH)
  public void on(PlayerInteractEvent e) {
    if (e.action() != InteractAction.LEFT_CLICK_AIR || e.hand() != Hand.MAIN_HAND) {
      return;
    }

    PlayerRef p = e.player();
    if (!p.isSprinting()) {
      return;
    }
    int level = getActiveLevel(p);
    ItemRef hand = p.inventory().mainHand();
    if (level <= 0 || tier(p, hand) < 2 || !lungeCooldowns.isReady(p.uuid(), (long) getConfig().lungeCooldownMillis)) {
      return;
    }

    Loc eye = p.eyeLocation();
    double attackReach = getConfig().attackReach;
    UUID self = p.uuid();
    RayHit hit = p.world().rayTraceEntityHit(eye.position(), eye.direction(), attackReach + getConfig().lungeBlocks, 0.3D,
        (EntityRef entity) -> entity instanceof LivingRef && !entity.uuid().equals(self) && !entity.has(EntityTrait.ARMOR_STAND));
    if (hit == null || !(hit.entity() instanceof LivingRef target)) {
      return;
    }
    double distance = eye.position().distance(hit.position());
    boolean allowed = target instanceof PlayerRef ? canPVP(p, target.location()) : canPVE(p, target);
    if (distance <= attackReach || !allowed || isProtectedFriendly(p, target)) {
      return;
    }

    lungeCooldowns.mark(p.uuid(), (long) getConfig().lungeCooldownMillis);
    double surge = Math.min(getConfig().lungeMaxSurge, (distance - getConfig().lungeStopShort) * getConfig().lungeSurgePerBlock);
    SwordsLungeStrike.surge(p, surge, getConfig().lungeVerticalBoost);
    fx(p.location().add(0, 1, 0), FxPriority.COMBAT)
        .particle(Particles.SWEEP_ATTACK, 1, 0, 0, 0, 0, 0)
        .sound(Sounds.ITEM_TRIDENT_RIPTIDE_1, 0.5F, 1.4F);
    addStat(p, "swords.duelists-verdict.lunges", 1);
    cue(p, MutationCue.FIGHT);
  }

  private void strike(EntityDamageByEntityEvent e, MeleeContext combat) {
    PlayerRef attacker = combat.attacker();
    LivingRef target = combat.target();
    int level = combat.level();
    double multiplier = 1D;
    if (SwordDuels.countEngaged(attacker, getConfig().engageRadius) == 1) {
      multiplier += getBonusDamage(level);
      if (finishing(target)) {
        multiplier += getFinishingBonus(level);
      }
      xpOn(attacker, target, getConfig().xpPerFocusedHit);
      addStat(attacker, "swords.duelists-verdict.focused-hits", 1);
    }

    ItemRef hand = combat.mainHand();
    int tier = tier(attacker, hand);
    double flat = 0D;
    if (tier >= 0) {
      flat = Math.max(0D, getBladeBonus(level, kills(hand)) - SwordsHeirloomEdge.bankedBonus(hand));
      long now = System.currentTimeMillis();
      if (tier >= 1 && riposteUntil.getOrDefault(attacker.uuid(), 0L) >= now) {
        riposteUntil.remove(attacker.uuid());
        if (!e.isCritical()) {
          multiplier *= VANILLA_CRIT;
        }
        addStat(attacker, "swords.duelists-verdict.ripostes", 1);
      }
    }

    double damage = (e.damage() * multiplier) + flat;
    if (tier >= 3 && verdictApplies(target)) {
      damage = Math.max(damage, target.health() + target.absorption() + 1D);
      e.setIgnoreReductions(true);
      addStat(attacker, "swords.duelists-verdict.verdicts", 1);
      fx(target.location().add(0, 1, 0), FxPriority.COMBAT)
          .particle(Particles.CRIT, 12, 0, 0, 0, 0.3D, 0.1D)
          .sound(Sounds.BLOCK_ANVIL_LAND, 0.3F, 1.6F);
    }
    if (damage != e.damage()) {
      e.setDamage(damage);
      cue(attacker, MutationCue.FIGHT);
    }
  }

  private void defend(EntityDamageByEntityEvent e, PlayerRef victim) {
    int level = getActiveLevel(victim);
    ItemRef hand = victim.inventory().mainHand();
    if (level <= 0 || !ItemKinds.isSword(hand)) {
      return;
    }

    if (SwordsRiposteWindow.shieldBlocking(victim) && tier(victim, hand) >= 1 && !keptRiposteArms(victim)) {
      riposteUntil.put(victim.uuid(), System.currentTimeMillis() + (long) getConfig().riposteWindowMillis);
      fx(victim.location().add(0, 1, 0), FxPriority.COMBAT).sound(Sounds.ITEM_SHIELD_BLOCK, 0.6F, 1.5F);
    }
    if (SwordDuels.countEngaged(victim, getConfig().engageRadius) == 1) {
      e.setDamage(Math.max(0D, e.damage() * (1D - getDamageReduction(level))));
    }
  }

  private boolean keptRiposteArms(PlayerRef victim) {
    for (Adaptation<?> adaptation : getSkill().getAdaptations()) {
      if (adaptation instanceof SwordsRiposteWindow riposte && riposte.arms(victim)) {
        return true;
      }
    }
    return false;
  }

  private void bankKill(PlayerRef p) {
    if (!p.isOnline() || getActiveLevel(p) <= 0) {
      return;
    }

    ItemRef hand = p.inventory().mainHand();
    int before = tier(p, hand);
    if (before < 0) {
      return;
    }

    hand.data().set(KILLS_KEY, kills(hand) + 1);
    applyBladeLore(hand);
    p.inventory().setMainHand(hand);
    if (tier(p, hand) > before) {
      addStat(p, "swords.duelists-verdict.techniques", 1);
      cue(p, MutationCue.BOON);
    }
  }

  /**
   * The awakened tier of {@code item} for {@code p} (0 to 3), or -1 when it is not their awake blade. The newest blade a
   * player awakened is awake; touching a newer one puts the older to sleep.
   */
  int tier(PlayerRef p, ItemRef item) {
    if (item == null || !ItemKinds.isSword(item)) {
      return -1;
    }
    DataHolder data = item.data();
    Long awakenedAt = data.get(AWAKENED_AT_KEY);
    if (awakenedAt == null || !p.uuid().equals(data.get(OWNER_KEY))) {
      return -1;
    }
    long current = getStorageLong(p, CURRENT_BLADE, 0L);
    if (awakenedAt > current) {
      setStorage(p, CURRENT_BLADE, awakenedAt);
    } else if (awakenedAt < current) {
      return -1;
    }

    int kills = kills(item);
    return kills >= getConfig().verdictKills ? 3 : kills >= getConfig().lungeKills ? 2 : kills >= getConfig().riposteKills ? 1 : 0;
  }

  private static int kills(ItemRef item) {
    return item.data().getOrDefault(KILLS_KEY, 0);
  }

  private void applyBladeLore(ItemRef item) {
    SwordBladeLore.replace(item, LORE_KEY, null,
        TextFormat.GOLD + Text.key("adapt.upgrade.swords.duelists_verdict.blade").withMarkup("kills", String.valueOf(kills(item))).resolve());
  }

  private boolean verdictApplies(LivingRef target) {
    return !(target instanceof PlayerRef) && !target.has(EntityTrait.BOSS)
        && target.health() / Math.max(1.0E-6D, SwordDuels.maxHealth(target)) < getConfig().verdictThreshold;
  }

  private boolean finishing(LivingRef target) {
    double maxHealth = SwordDuels.maxHealth(target);
    return maxHealth > 0 && target.health() / maxHealth <= getConfig().finishingThreshold;
  }

  double getBonusDamage(int level) {
    return getConfig().bonusDamageBase + (level * getConfig().bonusDamagePerLevel);
  }

  double getDamageReduction(int level) {
    return Math.min(getConfig().maxReduction, getConfig().reductionBase + (level * getConfig().reductionPerLevel));
  }

  double getFinishingBonus(int level) {
    return getConfig().finishingBonusBase + (level * getConfig().finishingBonusPerLevel);
  }

  double getBladeBonusCap(int level) {
    return level * getConfig().bladeBonusCapPerLevel;
  }

  double getBladeBonus(int level, int kills) {
    return Math.min(getBladeBonusCap(level), kills * getConfig().bladeBonusPerKill);
  }

  @ConfigDescription("Duel bonus on any sword; a named sword wakes Riposte, Lunge and Verdict through natural kills.")
  public static class Config extends SkillUpgrade.Config {
    @ConfigDoc(value = "Radius in blocks used to count engaged foes.", impact = "Higher values count foes from farther away when deciding if you are in a duel.")
    double engageRadius = 7;
    @ConfigDoc(value = "Duel bonus damage before level scaling.", impact = "Higher values raise damage in every duel.")
    double bonusDamageBase = 0.15;
    @ConfigDoc(value = "Duel bonus damage added per level.", impact = "Higher values reward higher levels more.")
    double bonusDamagePerLevel = 0.10;
    @ConfigDoc(value = "Damage reduction in a duel before level scaling.", impact = "Higher values make you harder to kill one on one.")
    double reductionBase = 0.10;
    @ConfigDoc(value = "Damage reduction added per level.", impact = "Higher values reward higher levels more.")
    double reductionPerLevel = 0.07;
    @ConfigDoc(value = "Highest damage reduction at any level.", impact = "Caps the reduction.")
    double maxReduction = 0.40;
    @ConfigDoc(value = "Health fraction at or under which the finishing bonus applies.", impact = "Higher values let the bonus start earlier in the fight.")
    double finishingThreshold = 0.30;
    @ConfigDoc(value = "Finishing bonus before level scaling.", impact = "Higher values make finishing blows hit harder.")
    double finishingBonusBase = 0.20;
    @ConfigDoc(value = "Finishing bonus added per level.", impact = "Higher values reward higher levels more.")
    double finishingBonusPerLevel = 0.15;
    @ConfigDoc(value = "Skill XP for each hit landed in a duel.", impact = "Higher values reward dueling.")
    double xpPerFocusedHit = 4;
    @ConfigDoc(value = "Natural kills with the awakened sword that wake Riposte.", impact = "Lower values wake the first technique sooner.")
    int riposteKills = 100;
    @ConfigDoc(value = "Natural kills with the awakened sword that wake Lunge.", impact = "Lower values wake the second technique sooner.")
    int lungeKills = 300;
    @ConfigDoc(value = "Natural kills with the awakened sword that wake Verdict.", impact = "Lower values wake the third technique sooner.")
    int verdictKills = 800;
    @ConfigDoc(value = "Flat damage the awakened sword banks per natural kill.", impact = "Higher values reach the cap with fewer kills.")
    double bladeBonusPerKill = 0.01;
    @ConfigDoc(value = "Most flat damage the awakened sword can bank, per level.", impact = "Higher values raise the cap at every level.")
    double bladeBonusCapPerLevel = 1.0;
    @ConfigDoc(value = "How long a shield block keeps the guaranteed critical ready, in milliseconds.", impact = "Higher values give more time to answer a block.")
    double riposteWindowMillis = 1500;
    @ConfigDoc(value = "Your normal attack reach in blocks; Lunge only fires on targets beyond it.", impact = "Higher values stop the lunge from firing on targets you could already hit.")
    double attackReach = 3.0;
    @ConfigDoc(value = "Extra blocks Lunge closes past your reach.", impact = "Higher values let you lunge at farther targets.")
    double lungeBlocks = 4.0;
    @ConfigDoc(value = "Forward push per block of distance to the target.", impact = "Higher values carry you farther per block.")
    double lungeSurgePerBlock = 0.3;
    @ConfigDoc(value = "Blocks short of the target the lunge aims to stop.", impact = "Higher values land you farther from the target.")
    double lungeStopShort = 1.5;
    @ConfigDoc(value = "Hard cap on the lunge push.", impact = "Lower values keep the lunge shorter.")
    double lungeMaxSurge = 1.6;
    @ConfigDoc(value = "Upward hop added to the lunge.", impact = "Higher values lift you more so the lunge clears ground friction.")
    double lungeVerticalBoost = 0.2;
    @ConfigDoc(value = "Time between two lunges, in milliseconds.", impact = "Lower values allow more frequent lunges.")
    double lungeCooldownMillis = 3000;
    @ConfigDoc(value = "Health fraction under which a hit from the awakened sword ends a non-boss mob.", impact = "Higher values let Verdict end fights earlier.")
    double verdictThreshold = 0.30;
  }
}
