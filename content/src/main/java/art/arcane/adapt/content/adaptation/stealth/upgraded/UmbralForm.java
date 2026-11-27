package art.arcane.adapt.content.adaptation.stealth.upgraded;

import art.arcane.adapt.api.AdaptApi;
import art.arcane.adapt.api.attribute.AdaptAttributeService;
import art.arcane.adapt.api.attribute.Attributes;
import art.arcane.adapt.api.config.ConfigDescription;
import art.arcane.adapt.api.config.ConfigDoc;
import art.arcane.adapt.api.content.PlatformNeed;
import art.arcane.adapt.api.content.StatLines;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.fx.Particles;
import art.arcane.adapt.api.mutation.LingeringSound;
import art.arcane.adapt.api.mutation.MutationCue;
import art.arcane.adapt.api.mutation.SkillUpgrade;
import art.arcane.adapt.api.text.Format;
import art.arcane.adapt.content.adaptation.stealth.StealthArmorLayer;
import art.arcane.adapt.content.adaptation.stealth.StealthCore;
import art.arcane.adapt.content.adaptation.stealth.StealthSpeed;
import art.arcane.adapt.content.adaptation.stealth.StealthVeil;
import art.arcane.adapt.platform.entity.AttributeOperation;
import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.LivingRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.entity.EntityDamageByEntityEvent;
import art.arcane.adapt.platform.event.entity.EntityDamageEvent;
import art.arcane.adapt.platform.event.player.PlayerQuitEvent;
import art.arcane.adapt.platform.event.player.PlayerToggleSneakEvent;
import art.arcane.adapt.platform.value.Id;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Shadowmeld, Ghost's Armor and Sneak Speed fused: sneaking while nothing nearby can see you makes you invisible,
 * lets you sneak at full walking speed, and builds an armor layer that the next hit breaks.
 */
public class UmbralForm extends SkillUpgrade<UmbralForm.Config> {
  public static final Id ID = Id.adapt("stealth/upgrade/umbral_form");

  private static final String SLOT_SNEAK = "sneak";

  private final Map<UUID, Umbra> umbrae = new ConcurrentHashMap<>();

  public UmbralForm() {
    super(ID);
    needs(PlatformNeed.attribute(Attributes.SNEAKING_SPEED));
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("black_stained_glass"));
    setInterval(250);
  }

  @Override
  public void addStats(int level, StatLines v) {
    statLore(v, Format.f(getMaxArmor(level), 0), 1);
    statLore(v, Format.f(getArmorPerPulse(level), 1), 2);
    statLore(v, Format.f(getConfig().watchRadius, 0), 3);
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(PlayerToggleSneakEvent e) {
    PlayerRef player = e.player();
    if (e.isSneaking() && getActiveLevel(player) > 0) {
      umbrae.computeIfAbsent(player.uuid(), (UUID id) -> new Umbra(player));
      return;
    }

    Umbra umbra = umbrae.get(player.uuid());
    if (umbra != null) {
      emerge(umbra);
    }
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(EntityDamageByEntityEvent e) {
    if (e.damager() instanceof PlayerRef player) {
      Umbra umbra = umbrae.get(player.uuid());
      if (umbra != null) {
        AdaptApi.scheduler().runEntity(player, () -> emerge(umbra));
      }
    }
  }

  @Handle(priority = EventPriority.HIGHEST)
  public void on(EntityDamageEvent e) {
    if (!(e.entity() instanceof PlayerRef player) || e.damage() <= 0D || e.finalDamage() <= 0D) {
      return;
    }

    Umbra umbra = umbrae.get(player.uuid());
    if (umbra == null) {
      return;
    }

    emerge(umbra);
    double stored = StealthArmorLayer.clampArmor(umbra.armor);
    if (StealthArmorLayer.bypassesArmor(e) || !StealthArmorLayer.isConsumableCharge(stored)) {
      return;
    }

    StealthArmorLayer.clear(this, player);
    fx(player.location().add(0, 1.0D, 0), FxPriority.COMBAT)
        .ring(Particles.SQUID_INK, Math.min(1.6D, 0.8D + (stored * 0.05D)), 12, 0)
        .burst(Particles.SMOKE, 6, 0.3D);
    umbra.armor = 0D;
    umbra.full = false;
    addStat(player, "stealth.umbral-form.layers-broken", 1);
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(PlayerQuitEvent e) {
    Umbra umbra = umbrae.remove(e.player().uuid());
    if (umbra != null) {
      emerge(umbra);
      StealthArmorLayer.clear(this, umbra.player);
    }
  }

  @Override
  public void onTick() {
    for (Umbra umbra : umbrae.values()) {
      if (!AdaptApi.scheduler().runEntity(umbra.player, () -> pulse(umbra))) {
        umbrae.remove(umbra.player.uuid(), umbra);
      }
    }
  }

  @Override
  public void unregister() {
    for (Umbra umbra : umbrae.values()) {
      AdaptApi.scheduler().runEntity(umbra.player, () -> {
        emerge(umbra);
        StealthArmorLayer.clear(this, umbra.player);
      });
    }
    umbrae.clear();
    super.unregister();
  }

  private void pulse(Umbra umbra) {
    PlayerRef player = umbra.player;
    int level = getActiveLevel(player);
    if (!player.isOnline() || level <= 0 || !player.isSneaking() || !unseen(player)) {
      emerge(umbra);
      return;
    }

    StealthVeil.veil(player);
    if (!umbra.veiled) {
      umbra.veiled = true;
      AdaptAttributeService.get().apply(player, getId().toString(), SLOT_SNEAK, Attributes.SNEAKING_SPEED,
          StealthSpeed.MAX_SNEAK_SCALAR, AttributeOperation.ADD_MULTIPLIED_TOTAL);
      umbra.lingering = linger(player);
      fx(player.location().add(0, 1.0D, 0), FxPriority.TRANSITION)
          .burst(Particles.SMOKE, 10, 0.3D)
          .ring(Particles.SQUID_INK, 0.6D, 8, 0.05D);
    }

    double maximum = getMaxArmor(level);
    umbra.armor = StealthArmorLayer.charge(this, player, umbra.armor, maximum, getArmorPerPulse(level));
    boolean full = umbra.armor >= maximum - 1.0E-4D;
    if (full && !umbra.full) {
      cue(player, MutationCue.BOON);
    }
    umbra.full = full;
  }

  private boolean unseen(PlayerRef player) {
    StealthCore stealth = StealthCore.of(getSkill());
    if (stealth == null) {
      return false;
    }
    double radius = getConfig().watchRadius;
    for (EntityRef entity : StealthCore.nearbyEntities(player.location(), radius,
        (EntityRef candidate) -> candidate instanceof LivingRef && !candidate.uuid().equals(player.uuid()))) {
      if (entity instanceof LivingRef observer && !stealth.unseenBy(player, observer)) {
        return false;
      }
    }
    return true;
  }

  private void emerge(Umbra umbra) {
    if (!umbra.veiled) {
      return;
    }
    umbra.veiled = false;
    PlayerRef player = umbra.player;
    AdaptAttributeService.get().remove(player, getId().toString(), SLOT_SNEAK, Attributes.SNEAKING_SPEED);
    if (umbra.lingering != null) {
      umbra.lingering.stop();
      umbra.lingering = null;
    }
    if (player.isOnline()) {
      StealthVeil.unveil(player);
    }
  }

  double getMaxArmor(int level) {
    return getConfig().maxArmorBase + (level * getConfig().maxArmorPerLevel);
  }

  double getArmorPerPulse(int level) {
    return getConfig().armorPerPulseBase + (level * getConfig().armorPerPulsePerLevel);
  }

  private static final class Umbra {
    private final PlayerRef player;
    private volatile boolean veiled;
    private volatile boolean full;
    private volatile double armor;
    private volatile LingeringSound lingering;

    private Umbra(PlayerRef player) {
      this.player = player;
    }
  }

  @ConfigDescription("Unseen sneaking turns you invisible at full walking speed and builds armor the next hit breaks.")
  public static class Config extends SkillUpgrade.Config {
    @ConfigDoc(value = "Radius checked for anything that could see you, in blocks.", impact = "Higher values make it harder to stay unseen in busy areas.")
    double watchRadius = 16;
    @ConfigDoc(value = "Largest armor layer before level scaling, in armor points.", impact = "Higher values absorb more of the next hit.")
    double maxArmorBase = 4;
    @ConfigDoc(value = "Armor layer added to the cap per level, in armor points.", impact = "Higher values reward higher levels more.")
    double maxArmorPerLevel = 4;
    @ConfigDoc(value = "Armor built per unseen pulse before level scaling.", impact = "Higher values fill the layer faster.")
    double armorPerPulseBase = 0.5;
    @ConfigDoc(value = "Armor built per pulse added per level.", impact = "Higher values fill the layer faster at higher levels.")
    double armorPerPulsePerLevel = 0.5;
  }
}
