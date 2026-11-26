package art.arcane.adapt.content.adaptation.unarmed.upgraded;

import art.arcane.adapt.api.adaptation.Cooldowns;
import art.arcane.adapt.api.adaptation.StatLines;
import art.arcane.adapt.api.batch.BuildBatch;
import art.arcane.adapt.api.batch.BuildBatches;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.fx.Particles;
import art.arcane.adapt.api.fx.Sounds;
import art.arcane.adapt.api.mutation.engine.MutationPayouts;
import art.arcane.adapt.api.mutation.sound.MutationCue;
import art.arcane.adapt.api.mutation.upgrade.SkillUpgrade;
import art.arcane.adapt.api.preference.CommonPreferences;
import art.arcane.adapt.api.preference.PlayerPreference;
import art.arcane.adapt.client.server.hud.AbilityGlyphs;
import art.arcane.adapt.content.adaptation.blocking.BlockingShields;
import art.arcane.adapt.content.adaptation.unarmed.UnarmedBatteringCharge;
import art.arcane.adapt.content.adaptation.unarmed.UnarmedFists;
import art.arcane.adapt.content.adaptation.unarmed.UnarmedGlassCannon;
import art.arcane.adapt.content.adaptation.unarmed.UnarmedPreferences;
import art.arcane.adapt.content.adaptation.unarmed.UnarmedSuckerPunch;
import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.block.BlockDamageEvent;
import art.arcane.adapt.platform.event.entity.EntityDamageByEntityEvent;
import art.arcane.adapt.platform.value.BlockPos;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.value.Vec3;
import art.arcane.adapt.platform.world.BlockRef;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.adapt.util.config.ConfigDoc;
import art.arcane.volmlib.util.format.Form;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Unarmed Power, Iron Fists and Glass Cannon fused at full strength: a punch takes all three originals' maxed bonuses,
 * in their order, then a per-level share on top. A sprint punch shoves the target through the soft blocks behind it and
 * knocks a raised shield down.
 */
public class TitanFists extends SkillUpgrade<TitanFists.Config> {
  private final Cooldowns sprintCooldown = cooldowns();

  public TitanFists() {
    super("upgraded-unarmed-titan-fists");
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("iron_ingot"));
  }

  @Override
  protected List<PlayerPreference<?>> upgradePreferences() {
    return List.of(CommonPreferences.ENABLED, UnarmedPreferences.COMBAT, UnarmedPreferences.BREAKING);
  }

  @Override
  public void addStats(int level, StatLines v) {
    statLore(v, Form.f(damage(1D, 0D, level), 1), 1);
    statLore(v, Form.f(getConfig().powerMultiplier * getConfig().nakedFactor * boost(level), 2), 2);
    statLore(v, Form.f((getConfig().flatBonus + getConfig().ironBonus) * boost(level), 2), 3);
    statLore(v, C.YELLOW, "* ", Form.duration((double) getSprintCooldownMillis(level), 1), 4);
    statLore(v, getConfig().sprintBlocksBroken, 5);
  }

  @Handle(priority = EventPriority.NORMAL)
  public void on(EntityDamageByEntityEvent e) {
    AttackContext attack = resolveAttackContext(e);
    if (attack == null) {
      return;
    }

    PlayerRef p = attack.attacker();
    if (isTool(p.inventory().mainHand()) || isTool(p.inventory().offHand())
        || !preferenceEnabled(p, UnarmedPreferences.COMBAT)) {
      return;
    }

    int level = attack.level();
    double powered = e.damage() * getConfig().powerMultiplier;
    double glass = UnarmedGlassCannon.hit(this, e.entity(), powered, getArmorValue(p), getConfig().nakedFactor,
        getConfig().flatBonus);
    e.setDamage((glass + getConfig().ironBonus) * boost(level));
    xpOn(p, e.entity(), getConfig().xpPerHit);
    addStat(p, "unarmed.titan-fists.hits", 1);
    if (!sprintPunch(p, e.entity(), level)) {
      cue(p, MutationCue.FIGHT);
    }
  }

  @Handle(priority = EventPriority.HIGHEST)
  public void on(BlockDamageEvent e) {
    PlayerRef p = e.player();
    if (!preferenceEnabled(p, UnarmedPreferences.BREAKING) || isItem(p.inventory().mainHand())) {
      return;
    }

    BlockRef block = e.block();
    if (!UnarmedFists.isSoftBlock(block, getConfig().softBlockMaxHardness)
        || resolveInteractContext(p, block.location()) == null) {
      return;
    }

    e.setInstaBreak(true);
    xpOn(p, block, getConfig().xpPerSoftBlock);
    cue(p, MutationCue.WORK);
  }

  private boolean sprintPunch(PlayerRef p, EntityRef target, int level) {
    if (!UnarmedSuckerPunch.isSuckerPunch(p) || p.vehicle() != null) {
      return false;
    }
    long cooldownMillis = getSprintCooldownMillis(level);
    if (!sprintCooldown.isReady(p.uuid(), cooldownMillis)) {
      return false;
    }
    int hunger = getConfig().sprintHunger;
    if (!payHungerCost(p, "sprint-punch", hunger, () -> {
      if (p.foodLevel() < hunger) {
        return false;
      }
      p.setFoodLevel(p.foodLevel() - hunger);
      return true;
    })) {
      return false;
    }

    sprintCooldown.mark(p.uuid(), cooldownMillis);
    AbilityGlyphs.cooldown(p, this, cooldownMillis);
    breakThrough(p, target);
    UnarmedBatteringCharge.shove(p, target, getConfig().sprintKnockback);
    if (target instanceof PlayerRef blocker && blocker.isBlocking()) {
      blocker.setCooldown(BlockingShields.SHIELD,
          Math.max(blocker.cooldownTicks(BlockingShields.SHIELD), getConfig().shieldLockTicks));
      fx(blocker, FxPriority.COMBAT).sound(Sounds.ITEM_SHIELD_BREAK, 0.9F, 0.8F);
    }
    fx(target.location().add(0D, 0.8D, 0D), FxPriority.COMBAT)
        .particle(Particles.EXPLOSION, 1, 0D, 0D, 0D, 0.05D, 0.0D)
        .particle(Particles.CLOUD, 10, 0D, 0D, 0D, 0.25D, 0.05D)
        .sound(Sounds.ENTITY_IRON_GOLEM_ATTACK, 0.7F, 0.7F);
    addStat(p, "unarmed.titan-fists.sprint-punches", 1);
    cue(p, MutationCue.BOON);
    return true;
  }

  /** Breaks the soft blocks behind the target along the shove, feet and head height, so it is carried through them. */
  private void breakThrough(PlayerRef p, EntityRef target) {
    int most = getConfig().sprintBlocksBroken;
    Vec3 facing = p.location().direction();
    Loc at = target.location();
    Vec3 push = new Vec3(facing.x(), 0D, facing.z());
    if (most <= 0 || push.lengthSquared() < 1.0E-6D) {
      return;
    }
    Vec3 step = push.normalize();
    List<BlockRef> blocks = new ArrayList<>();
    Set<BlockPos> seen = new HashSet<>();
    for (int distance = 1; distance <= getConfig().sprintReach && blocks.size() < most; distance++) {
      for (int height = 0; height <= 1 && blocks.size() < most; height++) {
        BlockPos pos = at.add(step.x() * distance, height, step.z() * distance).blockPos();
        BlockRef block = at.world().blockAt(pos.x(), pos.y(), pos.z());
        if (seen.add(pos) && !block.isAir() && UnarmedFists.isSoftBlock(block, getConfig().softBlockMaxHardness)
            && !MutationPayouts.farmed(block)) {
          blocks.add(block);
        }
      }
    }
    if (blocks.isEmpty()) {
      return;
    }
    BuildBatch.Builder batch = BuildBatch.of(this, p).limit(most).pace(most);
    for (BlockRef block : blocks) {
      batch.breakBlock(block, 0);
    }
    BuildBatches.get().start(batch.build());
  }

  double damage(double hit, double armor, int level) {
    double powered = hit * getConfig().powerMultiplier;
    double glass = UnarmedGlassCannon.damage(powered, armor, getConfig().nakedFactor, getConfig().flatBonus);
    return (glass + getConfig().ironBonus) * boost(level);
  }

  double boost(int level) {
    return 1D + (Math.max(0, level) * getConfig().bonusPerLevel);
  }

  long getSprintCooldownMillis(int level) {
    return (long) Math.max(1000D, getConfig().sprintCooldownMillisBase - (level * getConfig().sprintCooldownMillisPerLevel));
  }

  @ConfigDescription("Fists hit like the maxed Unarmed originals and more, and a sprint punch breaks through soft blocks.")
  public static class Config extends SkillUpgrade.Config {
    @ConfigDoc(value = "Attack damage multiplier of Unarmed Power at its top level.", impact = "Higher values make every punch hit harder.")
    double powerMultiplier = 3.57;
    @ConfigDoc(value = "Glass Cannon's no-armor damage multiplier at its top level.", impact = "Higher values reward fighting without armor.")
    double nakedFactor = 5.05;
    @ConfigDoc(value = "Glass Cannon's flat damage at its top level.", impact = "Higher values raise every punch.")
    double flatBonus = 1.75;
    @ConfigDoc(value = "Iron Fists' flat damage at its top level.", impact = "Higher values raise every punch.")
    double ironBonus = 3.0;
    @ConfigDoc(value = "Share of damage added on top of the maxed originals per level.", impact = "Higher values reward higher levels more.")
    double bonusPerLevel = 0.1;
    @ConfigDoc(value = "Hardest block that still breaks instantly by hand.", impact = "Higher values let tougher blocks break instantly.")
    double softBlockMaxHardness = 0.8;
    @ConfigDoc(value = "Sprint punch cooldown before level scaling, in milliseconds.", impact = "Higher values make sprint punches rarer.")
    double sprintCooldownMillisBase = 7000;
    @ConfigDoc(value = "Sprint punch cooldown removed per level, in milliseconds.", impact = "Higher values make higher levels punch through more often.")
    double sprintCooldownMillisPerLevel = 1000;
    @ConfigDoc(value = "Hunger points a sprint punch costs.", impact = "Higher values drain food faster; 0 removes the cost.")
    int sprintHunger = 1;
    @ConfigDoc(value = "Push of a sprint punch.", impact = "Higher values throw the target farther.")
    double sprintKnockback = 1.4;
    @ConfigDoc(value = "Most soft blocks a sprint punch breaks behind the target.", impact = "Higher values open bigger holes.")
    int sprintBlocksBroken = 3;
    @ConfigDoc(value = "How far behind the target a sprint punch looks for soft blocks, in blocks.", impact = "Higher values break blocks farther back.")
    int sprintReach = 3;
    @ConfigDoc(value = "How long a raised shield is knocked down by a sprint punch, in ticks.", impact = "Higher values keep the target from blocking longer.")
    int shieldLockTicks = 100;
    @ConfigDoc(value = "Skill XP for each punch.", impact = "Higher values reward fist fighting.")
    double xpPerHit = 3;
    @ConfigDoc(value = "Skill XP for each soft block broken by hand.", impact = "Higher values reward breaking blocks by hand.")
    double xpPerSoftBlock = 0.5;
  }
}
