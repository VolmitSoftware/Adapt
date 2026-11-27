package art.arcane.adapt.content.adaptation.stealth.upgraded;

import art.arcane.adapt.api.config.ConfigDescription;
import art.arcane.adapt.api.config.ConfigDoc;
import art.arcane.adapt.api.content.Cooldowns;
import art.arcane.adapt.api.content.StatLines;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.fx.Particles;
import art.arcane.adapt.api.mutation.MutationCue;
import art.arcane.adapt.api.mutation.Mutations;
import art.arcane.adapt.api.mutation.SkillUpgrade;
import art.arcane.adapt.api.text.Format;
import art.arcane.adapt.content.adaptation.stealth.StealthCore;
import art.arcane.adapt.content.adaptation.stealth.StealthExecution;
import art.arcane.adapt.content.adaptation.stealth.StealthPockets;
import art.arcane.adapt.platform.entity.LivingRef;
import art.arcane.adapt.platform.entity.MobRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.entity.EntityDamageByEntityEvent;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.value.Rgb;

import java.util.Map;
import java.util.UUID;


/**
 * Assassinate and Cutpurse fused: a strike while the target cannot see you kills a mob under the health cap outright;
 * a mob it cannot kill is robbed instead.
 */
public class DeathMark extends SkillUpgrade<DeathMark.Config> {
  public static final Id ID = Id.adapt("stealth/upgrade/death_mark");

  private static final Rgb MARK_BLACK = Rgb.of(0x14081C);

  private final Cooldowns cooldowns = cooldowns();
  private final Map<UUID, Opening> openings = playerState();
  private final StealthPockets pockets = new StealthPockets((PlayerRef thief, Loc from) -> {
    addStat(thief, "stealth.death-mark.pockets-picked", 1);
    cue(thief, MutationCue.FIGHT);
    fx(from.add(0, 1.0D, 0), FxPriority.TRAIL).burst(Particles.WAX_OFF, 8, 0.3D);
  });

  public DeathMark() {
    super(ID);
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("black_dye"));
  }

  @Override
  public void addStats(int level, StatLines v) {
    statLore(v, Format.f(getHealthCap(level), 0), 1);
    statLore(v, Format.duration(getCooldownMillis(level), 1), 2);
    statLore(v, getLootStacks(level), 3);
  }

  @Handle(priority = EventPriority.HIGHEST)
  public void on(EntityDamageByEntityEvent e) {
    MeleeContext combat = resolveMeleeContext(e);
    if (combat == null) {
      return;
    }

    PlayerRef attacker = combat.attacker();
    LivingRef target = combat.target();
    StealthCore stealth = StealthCore.of(getSkill());
    boolean opened = opened(attacker, target);
    if (stealth == null || !StealthExecution.isExecutable(attacker, target)
        || !(opened || stealth.unseenBy(attacker, target))) {
      return;
    }

    int level = combat.level();
    double maxHealth = StealthExecution.maxHealth(target);
    long cooldown = getCooldownMillis(level);
    if (maxHealth > 0D && maxHealth <= getHealthCap(level) && cooldowns.isReady(attacker.uuid(), cooldown)) {
      cooldowns.mark(attacker.uuid(), cooldown);
      StealthExecution.applyExecutionDamage(e, target.health());
      fx(target.location().add(0, 1.0D, 0), FxPriority.COMBAT)
          .burst(Particles.SCULK_SOUL, 12, 0.4D)
          .dustBurst(MARK_BLACK, 10, 0.4D, 1.2F);
      xpOn(attacker, target, getConfig().xpOnExecution);
      addStat(attacker, "stealth.death-mark.executions", 1);
      cue(attacker, MutationCue.FIGHT);
      return;
    }

    if (target instanceof MobRef mob && !Mutations.farmed(attacker, target)
        && pockets.steal(attacker, mob, (float) getConfig().lootQuality, getLootStacks(level))) {
      xpOn(attacker, mob, getConfig().xpOnSteal);
    }
  }

  /** Makes the player's first hit on {@code target} within {@code windowMillis} an opening strike, seen or not. */
  public void open(PlayerRef player, LivingRef target, long windowMillis) {
    openings.put(player.uuid(), new Opening(target.uuid(), System.currentTimeMillis() + windowMillis));
  }

  private boolean opened(PlayerRef attacker, LivingRef target) {
    Opening opening = openings.get(attacker.uuid());
    if (opening == null) {
      return false;
    }
    if (System.currentTimeMillis() > opening.until()) {
      openings.remove(attacker.uuid(), opening);
      return false;
    }
    return opening.target().equals(target.uuid()) && openings.remove(attacker.uuid(), opening);
  }

  double getHealthCap(int level) {
    return getConfig().healthCapBase + (level * getConfig().healthCapPerLevel);
  }

  long getCooldownMillis(int level) {
    return (long) Math.max(getConfig().minimumCooldownMillis,
        getConfig().cooldownMillisBase - (level * getConfig().cooldownMillisPerLevel));
  }

  int getLootStacks(int level) {
    return Math.max(1, getConfig().lootStacksBase + level);
  }

  private record Opening(UUID target, long until) {
  }

  @ConfigDescription("An unseen strike kills mobs under a health cap outright and robs the ones it cannot kill.")
  public static class Config extends SkillUpgrade.Config {
    @ConfigDoc(value = "Highest max health a mob can have to die outright, before level scaling.", impact = "Higher values let you kill tougher mobs in one strike.")
    double healthCapBase = 30;
    @ConfigDoc(value = "Health cap added per level.", impact = "Higher values reward higher levels more.")
    double healthCapPerLevel = 15;
    @ConfigDoc(value = "Time between outright kills before level scaling, in milliseconds.", impact = "Higher values make outright kills rarer.")
    double cooldownMillisBase = 20000;
    @ConfigDoc(value = "Cooldown removed per level, in milliseconds.", impact = "Higher values let higher levels kill more often.")
    double cooldownMillisPerLevel = 4000;
    @ConfigDoc(value = "Shortest time between outright kills, in milliseconds.", impact = "Lower values allow more frequent kills.")
    double minimumCooldownMillis = 6000;
    @ConfigDoc(value = "Loot stacks taken from a robbed mob, before adding the level.", impact = "Higher values steal more at once.")
    int lootStacksBase = 0;
    @ConfigDoc(value = "Loot luck used when rolling stolen loot.", impact = "Higher values bias steals toward rarer drops.")
    double lootQuality = 2;
    @ConfigDoc(value = "Skill XP for each outright kill.", impact = "Higher values reward executions.")
    double xpOnExecution = 45;
    @ConfigDoc(value = "Skill XP for each robbed mob.", impact = "Higher values reward theft.")
    double xpOnSteal = 15;
  }
}
