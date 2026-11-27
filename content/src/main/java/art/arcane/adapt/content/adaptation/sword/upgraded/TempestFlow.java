package art.arcane.adapt.content.adaptation.sword.upgraded;

import art.arcane.adapt.api.attribute.AdaptAttributeService;
import art.arcane.adapt.api.config.ConfigDescription;
import art.arcane.adapt.api.config.ConfigDoc;
import art.arcane.adapt.api.content.Cooldowns;
import art.arcane.adapt.api.content.StatLines;
import art.arcane.adapt.api.item.ItemKinds;
import art.arcane.adapt.api.mutation.MutationCue;
import art.arcane.adapt.api.mutation.SkillUpgrade;
import art.arcane.adapt.api.player.PlayerProfile;
import art.arcane.adapt.api.preference.CommonPreferences;
import art.arcane.adapt.api.preference.PlayerPreference;
import art.arcane.adapt.api.text.Format;
import art.arcane.adapt.content.adaptation.sword.SwordCyclone;
import art.arcane.adapt.content.adaptation.sword.SwordFlow;
import art.arcane.adapt.content.adaptation.sword.SwordPreferences;
import art.arcane.adapt.platform.entity.LivingRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.entity.EntityDamageByEntityEvent;
import art.arcane.adapt.platform.event.entity.EntityDamageEvent;
import art.arcane.adapt.platform.value.Id;

import java.util.List;
import java.util.UUID;

/**
 * Blade Flow and Crimson Cyclone fused: hits build attack-speed stacks, and at full stacks a critical hit releases the
 * crimson area slash without spending the stacks. Taking damage breaks the flow, as with Blade Flow.
 */
public class TempestFlow extends SkillUpgrade<TempestFlow.Config> {
  public static final Id ID = Id.adapt("swords/upgrade/tempest_flow");

  private final SwordFlow flow = new SwordFlow(this, playerState(), playerState());
  private final SwordCyclone cyclone = new SwordCyclone(this);
  static final int WHETSTONE_LEVEL = 2;
  private final Cooldowns slashCooldowns = cooldowns();
  private final Whetstone whetstone;

  public TempestFlow() {
    super(ID);
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("netherite_sword"));
    whetstone = registerTool(new Whetstone(this, playerState()));
  }

  @Override
  protected List<PlayerPreference<?>> upgradePreferences() {
    return List.of(CommonPreferences.ENABLED, SwordPreferences.IGNORE_PASSIVE, SwordPreferences.VISUALS);
  }

  @Override
  public void onPlayerPreferencesChanged(PlayerProfile player) {
    if (getActiveLevel(player.player()) <= 0) {
      AdaptAttributeService.get().removeAll(player.player(), getId().toString());
    }
  }

  @Override
  public void addStats(int level, StatLines v) {
    statLore(v, getStackCap(level), 1);
    statLore(v, Format.f(getSlashDamage(level), 1), 2);
    statLore(v, Format.f(getSlashRadius(level), 1), 3);
    if (level >= WHETSTONE_LEVEL) {
      statLore(v, Format.f(serratedBleed(level), 1), 4);
      statLore(v, Format.duration(weightedTicks(level) * 50D, 1), 5);
      statLore(v, Format.pc(keenBonus(level), 0), 6);
    }
  }

  @Handle(priority = EventPriority.HIGHEST)
  public void on(EntityDamageByEntityEvent e) {
    MeleeContext combat = resolveMeleeContext(e, ItemKinds::isSword);
    if (combat == null) {
      return;
    }

    whetstone.strike(e, combat);
    PlayerRef player = combat.attacker();
    UUID id = player.uuid();
    int level = combat.level();
    int cap = getStackCap(level);
    long now = System.currentTimeMillis();
    long slashCooldown = (long) getConfig().slashCooldownMillis;
    if (e.isCritical() && flow.stacks(id, now) >= cap && slashCooldowns.isReady(id, slashCooldown)) {
      slashCooldowns.mark(id, slashCooldown);
      release(e, combat);
    }

    SwordFlow.Step step = flow.build(player, cap, (long) getConfig().windowMillis, now);
    xpOn(player, combat.target(), getConfig().xpPerStack);
    if (step.stacks() >= cap && step.previous() < cap) {
      cue(player, MutationCue.BOON);
    }
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(EntityDamageEvent e) {
    if (e.entity() instanceof PlayerRef player && e.finalDamage() > 0 && flow.holds(player.uuid())) {
      flow.clear(player);
    }
  }

  private void release(EntityDamageByEntityEvent e, MeleeContext combat) {
    PlayerRef player = combat.attacker();
    int level = combat.level();
    double damage = getSlashDamage(level);
    int candidateLimit = SwordCyclone.candidateLimit(getConfig().maxCandidatesPerActivation);
    int targetFxLimit = preferenceEnabled(player, SwordPreferences.VISUALS)
        ? SwordCyclone.targetFxLimit(getConfig().maxTargetFxPerActivation) : 0;
    e.setDamage(e.damage() + damage);
    cyclone.release(new SwordCyclone.Strike(player, combat.target(), level, getSlashRadius(level), damage,
        candidateLimit, SwordCyclone.affectedLimit(getConfig().maxAffectedPerActivation, candidateLimit), targetFxLimit,
        getConfig().ignorePassiveMobs || preferenceEnabled(player, SwordPreferences.IGNORE_PASSIVE),
        getConfig().ignorePassiveMobs, getConfig().bleedDamagePerProc, getConfig().bleedTicks, true),
        (PlayerRef owner, List<LivingRef> hits) -> {
          xpOnMobs(owner, hits, getConfig().xpPerTargetHit);
          addStat(owner, "swords.tempest-flow.mobs-hit", hits.size());
        });
    cue(player, MutationCue.FIGHT);
  }

  int getStackCap(int level) {
    return Math.max(1, getConfig().stackCapBase + level);
  }

  double getSlashDamage(int level) {
    return getConfig().slashDamageBase + (level * getConfig().slashDamagePerLevel);
  }

  double getSlashRadius(int level) {
    return getConfig().slashRadiusBase + (level * getConfig().slashRadiusPerLevel);
  }

  double serratedBleed(int level) {
    return getConfig().serratedBleedBase + (level * getConfig().serratedBleedPerLevel);
  }

  int weightedTicks(int level) {
    return (int) Math.round(getConfig().weightedTicksBase + (level * getConfig().weightedTicksPerLevel));
  }

  double keenBonus(int level) {
    return getConfig().keenBonusBase + (level * getConfig().keenBonusPerLevel);
  }

  @ConfigDescription("Sword hits build attack speed; a critical hit at full stacks releases a crimson area slash.")
  public static class Config extends SkillUpgrade.ToolConfig {
    @ConfigDoc(value = "Stacks needed for the release, before adding the level.", impact = "Higher values need longer chains before the slash.")
    int stackCapBase = 2;
    @ConfigDoc(value = "How long a stack survives without a new hit, in milliseconds.", impact = "Higher values keep the flow alive longer between strikes.")
    double windowMillis = 4500;
    @ConfigDoc(value = "Slash damage before level scaling.", impact = "Higher values make the area slash hit harder.")
    double slashDamageBase = 2.0;
    @ConfigDoc(value = "Slash damage added per level.", impact = "Higher values reward higher levels more.")
    double slashDamagePerLevel = 1.5;
    @ConfigDoc(value = "Slash radius before level scaling, in blocks.", impact = "Higher values reach more targets.")
    double slashRadiusBase = 3.0;
    @ConfigDoc(value = "Slash radius added per level, in blocks.", impact = "Higher values widen the slash at higher levels.")
    double slashRadiusPerLevel = 0.5;
    @ConfigDoc(value = "Time between two releases, in milliseconds.", impact = "Lower values allow more frequent slashes.")
    double slashCooldownMillis = 6000;
    @ConfigDoc(value = "Bleed damage per proc on every target the slash hits.", impact = "Higher values make the bleed hit harder.")
    double bleedDamagePerProc = 0.6;
    @ConfigDoc(value = "Bleed duration on every target the slash hits, in ticks.", impact = "Higher values make the bleed last longer.")
    int bleedTicks = 100;
    @ConfigDoc(value = "Exclude passive and neutral mobs from the slash and its bleeding.", impact = "When enabled, protected mobs do not use up target limits.")
    boolean ignorePassiveMobs = false;
    @ConfigDoc(value = "Most nearby living targets one slash inspects.", impact = "Lower values reduce work in crowds; clamped to 32.")
    int maxCandidatesPerActivation = 16;
    @ConfigDoc(value = "Most targets one slash hits, including the struck target.", impact = "Lower values cap damage in crowds; clamped to 16.")
    int maxAffectedPerActivation = 12;
    @ConfigDoc(value = "Most hit targets that get their own spark effect.", impact = "Lower values reduce particles; clamped to 12.")
    int maxTargetFxPerActivation = 9;
    @ConfigDoc(value = "Skill XP for each stack built.", impact = "Higher values reward long chains.")
    double xpPerStack = 3;
    @ConfigDoc(value = "Skill XP for each target the slash hits.", impact = "Higher values reward slashing groups.")
    double xpPerTargetHit = 10;
    @ConfigDoc(value = "Sword durability one whetstone grind costs.", impact = "Higher values wear the sword faster per grind.")
    int whetstoneDurabilityCost = 10;
    @ConfigDoc(value = "Combat time one grind lasts, in milliseconds.", impact = "Higher values make each whetstone last longer.")
    double whetstoneCombatMillis = 600_000;
    @ConfigDoc(value = "Most combat time one hit spends from a grind, in milliseconds.", impact = "Higher values use up grinds faster between spaced hits.")
    double whetstoneMillisPerHit = 5_000;
    @ConfigDoc(value = "Serrated bleed damage per pulse before level scaling; scales with Witherblade.", impact = "Higher values make Serrated bleed harder.")
    double serratedBleedBase = 0.3;
    @ConfigDoc(value = "Serrated bleed damage per pulse added per Witherblade level.", impact = "Higher values reward Witherblade more.")
    double serratedBleedPerLevel = 0.2;
    @ConfigDoc(value = "How long one Serrated bleed lasts, in milliseconds.", impact = "Higher values make each bleed last longer.")
    double serratedBleedMillis = 3_000;
    @ConfigDoc(value = "Weighted stagger and shield lockout before level scaling, in ticks; scales with Tempest Flow.", impact = "Higher values keep targets off balance longer.")
    double weightedTicksBase = 20;
    @ConfigDoc(value = "Weighted stagger and shield lockout added per Tempest Flow level, in ticks.", impact = "Higher values reward Tempest Flow more.")
    double weightedTicksPerLevel = 10;
    @ConfigDoc(value = "Slowness amplifier of the Weighted stagger; 0 is Slowness I.", impact = "Higher values slow staggered targets more.")
    int weightedSlownessAmplifier = 0;
    @ConfigDoc(value = "Time before the same target can be staggered again, in milliseconds.", impact = "Lower values let Weighted stagger more often.")
    double weightedCooldownMillis = 3_000;
    @ConfigDoc(value = "Keen critical damage bonus before level scaling; scales with Duelist's Verdict.", impact = "Higher values make Keen crits hit harder.")
    double keenBonusBase = 0.10;
    @ConfigDoc(value = "Keen critical damage bonus added per Duelist's Verdict level.", impact = "Higher values reward Duelist's Verdict more.")
    double keenBonusPerLevel = 0.10;
  }
}
