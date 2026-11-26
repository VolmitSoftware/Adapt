package art.arcane.adapt.content.adaptation.unarmed.upgraded;

import art.arcane.adapt.AdaptCore;
import art.arcane.adapt.api.adaptation.Cooldowns;
import art.arcane.adapt.api.adaptation.StatLines;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.fx.Particles;
import art.arcane.adapt.api.fx.Sounds;
import art.arcane.adapt.api.minion.MinionIdentity;
import art.arcane.adapt.api.mutation.engine.MutationPayouts;
import art.arcane.adapt.api.mutation.sound.MutationCue;
import art.arcane.adapt.api.mutation.upgrade.SkillUpgrade;
import art.arcane.adapt.client.server.hud.AbilityGlyphs;
import art.arcane.adapt.content.adaptation.unarmed.UnarmedCombo;
import art.arcane.adapt.content.adaptation.unarmed.UnarmedComboChain;
import art.arcane.adapt.content.adaptation.unarmed.UnarmedDisarm;
import art.arcane.adapt.content.adaptation.unarmed.UnarmedPressure;
import art.arcane.adapt.content.adaptation.unarmed.UnarmedPressurePoint;
import art.arcane.adapt.platform.entity.EffectInstance;
import art.arcane.adapt.platform.entity.EquipmentSlot;
import art.arcane.adapt.platform.entity.Hand;
import art.arcane.adapt.platform.entity.ItemEntityRef;
import art.arcane.adapt.platform.entity.LivingRef;
import art.arcane.adapt.platform.entity.PlayerInventoryRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.entity.EntityDamageByEntityEvent;
import art.arcane.adapt.platform.event.entity.EntityDamageEvent;
import art.arcane.adapt.platform.event.player.InteractAction;
import art.arcane.adapt.platform.event.player.PlayerInteractEvent;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.adapt.util.config.ConfigDoc;
import art.arcane.volmlib.util.format.Form;

import java.util.Map;
import java.util.UUID;

/**
 * Combo Chain and Pressure Point fused: bare-hand hits build combo stacks that taking a hit no longer lets lapse, and
 * every hit slows the target by one amplifier per stack. A sneaking hit at full stacks is the finisher.
 */
public class UnbrokenCombo extends SkillUpgrade<UnbrokenCombo.Config> {
  private static final int HOTBAR_SLOTS = 9;
  private static final int STORAGE_SLOTS = 36;
  private final UnarmedCombo combo = new UnarmedCombo(this, playerState());
  private final Cooldowns finisherCooldown = cooldowns();
  private final Map<UUID, Long> finishers = playerState();

  public UnbrokenCombo() {
    super("upgraded-unarmed-unbroken-combo");
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("chainmail_chestplate"));
  }

  @Override
  public void addStats(int level, StatLines v) {
    statLore(v, getMaxStacks(level), 1);
    statLore(v, Form.f(getDamagePerStack(level)), 2);
    statLore(v, getMaxSlownessAmplifier(level) + 1, 3);
    statLore(v, Form.f(getFinisherDamagePerStack(level)), 4);
    statLore(v, getFinisherWeaknessAmplifier(level) + 1, 5);
    statLore(v, C.YELLOW, "* ", Form.duration((double) getConfig().finisherCooldownMillis, 1), 6);
  }

  @Handle(priority = EventPriority.HIGHEST)
  public void on(EntityDamageByEntityEvent e) {
    AttackContext attack = resolveAttackContext(e);
    if (attack == null) {
      return;
    }

    PlayerRef p = attack.attacker();
    if (isMelee(p.inventory().mainHand())) {
      combo.drop(p);
      return;
    }

    long now = System.currentTimeMillis();
    int level = attack.level();
    int maxStacks = getMaxStacks(level);
    long windowMillis = getComboWindowMillis(level);
    int previous = combo.stacks(p, windowMillis, now);
    if (p.isSneaking() && previous >= maxStacks && attack.target() instanceof LivingRef finished
        && finisherCooldown.isReady(p.uuid(), getConfig().finisherCooldownMillis)) {
      finish(p, finished, e, previous, level);
      return;
    }
    int stacks = combo.build(p, maxStacks, windowMillis, now);
    double bonus = stacks * getDamagePerStack(level);
    e.setDamage(e.damage() + bonus);
    UnarmedComboChain.feedback(this, e.entity().location(), stacks, maxStacks);
    if (attack.target() instanceof LivingRef victim) {
      int slowAmplifier = UnarmedPressure.stack(victim, UnarmedPressure.SLOWNESS,
          Math.min(getMaxSlownessAmplifier(level), stacks - 1), getConfig().slownessDurationTicks);
      UnarmedPressurePoint.strikeFx(this, victim, slowAmplifier, false);
    }
    xpOn(p, attack.target(), bonus * getConfig().xpPerBonusDamage);
    addStat(p, "unarmed.unbroken-combo.combo-hits", 1);
    cue(p, stacks >= maxStacks && previous < maxStacks ? MutationCue.BOON : MutationCue.FIGHT);
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(EntityDamageEvent e) {
    if (!(e.entity() instanceof PlayerRef p) || e.finalDamage() <= 0) {
      return;
    }

    int level = getActiveLevel(p);
    if (level > 0) {
      combo.hold(p, getMaxStacks(level), getComboWindowMillis(level), System.currentTimeMillis());
    }
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(PlayerInteractEvent e) {
    InteractAction action = e.action();
    if (action != InteractAction.LEFT_CLICK_AIR && action != InteractAction.LEFT_CLICK_BLOCK) {
      return;
    }

    PlayerRef p = e.player();
    if (!hasActiveAdaptation(p) || isMelee(p.inventory().mainHand())) {
      return;
    }

    combo.missed(p, getConfig().missResetGraceMillis, System.currentTimeMillis());
  }

  /** Spends the full combo in one blow: bonus damage per stack, Weakness, and the target's held item taken away. */
  private void finish(PlayerRef p, LivingRef victim, EntityDamageByEntityEvent e, int stacks, int level) {
    combo.spend(p);
    finishers.put(p.uuid(), e.dispatchId());
    finisherCooldown.mark(p.uuid(), getConfig().finisherCooldownMillis);
    AbilityGlyphs.cooldown(p, this, getConfig().finisherCooldownMillis);
    double bonus = stacks * getFinisherDamagePerStack(level);
    e.setDamage(e.damage() + bonus);
    victim.addEffect(new EffectInstance(UnarmedPressure.WEAKNESS, getConfig().finisherWeaknessTicks,
        getFinisherWeaknessAmplifier(level), false, true, true));
    disarm(p, victim);
    fx(victim.location().add(0D, 1D, 0D), FxPriority.COMBAT)
        .particle(Particles.CRIT, 16, 0D, 0D, 0D, 0.4D, 0.1D)
        .particle(Particles.SWEEP_ATTACK, 1, 0D, 0D, 0D, 0D, 0D)
        .chord(Sounds.ENTITY_PLAYER_ATTACK_STRONG, 1.0F, 0.7F, Sounds.ENTITY_PLAYER_ATTACK_CRIT, 0.8F, 1.2F);
    xpOn(p, victim, bonus * getConfig().xpPerBonusDamage);
    addStat(p, "unarmed.unbroken-combo.finishers", 1);
    cue(p, MutationCue.BOON);
  }

  /**
   * Mobs drop what they hold, unless they were farmed, when it is only taken. Players never lose it: the item moves to
   * a free slot of their own inventory, or swaps with the next hotbar slot.
   */
  private void disarm(PlayerRef p, LivingRef victim) {
    EquipmentSlot hand = UnarmedDisarm.heldSlot(victim);
    if (hand == null || MinionIdentity.isMinion(victim)) {
      return;
    }
    ItemRef empty = AdaptCore.platform().host().items().empty();
    ItemRef held = victim.equipment(hand).copy();
    if (victim instanceof PlayerRef holder) {
      stow(holder, hand, held, empty);
      return;
    }
    victim.setEquipment(hand, empty);
    if (!MutationPayouts.farmed(p, victim)) {
      ItemEntityRef dropped = victim.world().dropItemNaturally(victim.location().add(0D, 0.4D, 0D).position(), held);
      dropped.setPickupDelay(getConfig().disarmPickupDelayTicks);
    }
  }

  private static void stow(PlayerRef holder, EquipmentSlot hand, ItemRef held, ItemRef empty) {
    PlayerInventoryRef inventory = holder.inventory();
    int heldSlot = inventory.heldSlot();
    for (int slot = 0; slot < STORAGE_SLOTS; slot++) {
      ItemRef there = inventory.item(slot);
      if (slot != heldSlot && (there == null || there.type().isAir())) {
        inventory.setItem(slot, held);
        inventory.setHand(handOf(hand), empty);
        return;
      }
    }
    if (hand == EquipmentSlot.MAIN_HAND) {
      int next = (heldSlot + 1) % HOTBAR_SLOTS;
      ItemRef swapped = inventory.item(next);
      inventory.setItem(next, held);
      inventory.setMainHand(swapped);
    }
  }

  private static Hand handOf(EquipmentSlot slot) {
    return slot == EquipmentSlot.OFF_HAND ? Hand.OFF_HAND : Hand.MAIN_HAND;
  }

  /** Whether {@code hit} was {@code p}'s finisher, so no other sneak-punch effect takes it. */
  public boolean finished(PlayerRef p, EntityDamageByEntityEvent hit) {
    Long finisher = finishers.get(p.uuid());
    return finisher != null && finisher == hit.dispatchId();
  }

  double getFinisherDamagePerStack(int level) {
    return getConfig().finisherDamagePerStackBase + (level * getConfig().finisherDamagePerStackPerLevel);
  }

  int getFinisherWeaknessAmplifier(int level) {
    return Math.max(0, Math.min(getConfig().finisherWeaknessMaxAmplifier, level - 1));
  }

  int getMaxStacks(int level) {
    return Math.max(1, getConfig().maxStacksBase + (level * getConfig().maxStacksPerLevel));
  }

  double getDamagePerStack(int level) {
    return getConfig().damagePerStackBase + (level * getConfig().damagePerStackPerLevel);
  }

  long getComboWindowMillis(int level) {
    return Math.max(250L, (long) (getConfig().comboWindowMillisBase + (level * getConfig().comboWindowMillisPerLevel)));
  }

  int getMaxSlownessAmplifier(int level) {
    return Math.max(0, getConfig().maxSlownessAmplifierBase + level);
  }

  @ConfigDescription("Combo stacks survive being hit, each stack slows the target, and a sneaking hit at full stacks spends them all.")
  public static class Config extends SkillUpgrade.Config {
    @ConfigDoc(value = "Most combo stacks before level scaling.", impact = "Higher values allow longer combos.")
    int maxStacksBase = 4;
    @ConfigDoc(value = "Most combo stacks added per level.", impact = "Higher values reward higher levels with longer combos.")
    int maxStacksPerLevel = 2;
    @ConfigDoc(value = "Bonus damage per stack before level scaling.", impact = "Higher values make every stack hit harder.")
    double damagePerStackBase = 0.4;
    @ConfigDoc(value = "Bonus damage per stack added per level.", impact = "Higher values reward higher levels more.")
    double damagePerStackPerLevel = 0.15;
    @ConfigDoc(value = "Time a combo survives without a hit before level scaling, in milliseconds. Being hit restarts it.", impact = "Higher values keep combos alive between punches.")
    double comboWindowMillisBase = 2200;
    @ConfigDoc(value = "Combo window added per level, in milliseconds.", impact = "Higher values keep combos alive longer at higher levels.")
    double comboWindowMillisPerLevel = 400;
    @ConfigDoc(value = "Time after the last hit before a missed swing drops the combo, in milliseconds.", impact = "Higher values forgive more missed swings.")
    long missResetGraceMillis = 280;
    @ConfigDoc(value = "Highest slowness amplifier before adding the level.", impact = "Higher values let long combos slow targets more.")
    int maxSlownessAmplifierBase = 0;
    @ConfigDoc(value = "Slowness duration per hit, in ticks.", impact = "Higher values keep targets slowed for longer.")
    int slownessDurationTicks = 60;
    @ConfigDoc(value = "Skill XP per point of bonus damage.", impact = "Higher values reward long combos.")
    double xpPerBonusDamage = 4;
    @ConfigDoc(value = "Finisher damage per stack spent before level scaling.", impact = "Higher values make a full combo end harder.")
    double finisherDamagePerStackBase = 0.5;
    @ConfigDoc(value = "Finisher damage per stack spent added per level.", impact = "Higher values reward higher levels more.")
    double finisherDamagePerStackPerLevel = 0.3;
    @ConfigDoc(value = "Highest Weakness amplifier a finisher applies; 0 is Weakness I.", impact = "Higher values weaken finished targets more.")
    int finisherWeaknessMaxAmplifier = 1;
    @ConfigDoc(value = "Weakness duration after a finisher, in ticks.", impact = "Higher values keep finished targets weak longer.")
    int finisherWeaknessTicks = 100;
    @ConfigDoc(value = "Time between finishers, in milliseconds.", impact = "Higher values make finishers rarer.")
    long finisherCooldownMillis = 8000;
    @ConfigDoc(value = "Ticks before a mob can pick its dropped item up again.", impact = "Higher values keep disarmed mobs empty-handed longer.")
    int disarmPickupDelayTicks = 60;
  }
}
