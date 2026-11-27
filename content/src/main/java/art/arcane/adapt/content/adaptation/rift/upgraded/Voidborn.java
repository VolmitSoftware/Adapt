package art.arcane.adapt.content.adaptation.rift.upgraded;

import art.arcane.adapt.api.AdaptApi;
import art.arcane.adapt.api.attribute.Attributes;
import art.arcane.adapt.api.config.ConfigDescription;
import art.arcane.adapt.api.config.ConfigDoc;
import art.arcane.adapt.api.content.DeathSave;
import art.arcane.adapt.api.content.PlatformNeed;
import art.arcane.adapt.api.content.StatLines;
import art.arcane.adapt.api.mutation.LingeringSound;
import art.arcane.adapt.api.mutation.MutationCue;
import art.arcane.adapt.api.mutation.SkillUpgrade;
import art.arcane.adapt.api.preference.CommonPreferences;
import art.arcane.adapt.api.preference.PlayerPreference;
import art.arcane.adapt.api.text.Format;
import art.arcane.adapt.api.text.TextFormat;
import art.arcane.adapt.content.adaptation.rift.RiftEndermanCalm;
import art.arcane.adapt.content.adaptation.rift.RiftLevitation;
import art.arcane.adapt.content.adaptation.rift.RiftVoidEscape;
import art.arcane.adapt.content.adaptation.rift.RiftVoidSkin;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.entity.EntityDamageEvent;
import art.arcane.adapt.platform.event.entity.EntityTargetEvent;
import art.arcane.adapt.platform.event.player.PlayerJoinEvent;
import art.arcane.adapt.platform.event.player.PlayerQuitEvent;
import art.arcane.adapt.platform.event.player.PlayerToggleSneakEvent;
import art.arcane.adapt.platform.value.Id;

import java.util.List;

/**
 * Void Skin, Rift Visage and Anti-Levitation fused: lethal damage blinks you to a safe spot for an ender pearl, endermen
 * never turn on you, and sneaking drops you out of levitation.
 */
public class Voidborn extends SkillUpgrade<Voidborn.Config> implements DeathSave {
  public static final Id ID = Id.adapt("rift/upgrade/voidborn");

  private final RiftVoidEscape escape = new RiftVoidEscape(this, (PlayerRef p) -> {
    addStat(p, "rift.voidborn.escapes", 1);
    addStat(p, "rift.teleports", 1);
    cue(p, MutationCue.BOON);
    LingeringSound lingering = linger(p);
    AdaptApi.scheduler().runEntity(p, lingering::stop, getResistanceTicks(getActiveLevel(p)));
  });
  private final RiftEndermanCalm calm = new RiftEndermanCalm(this, cooldowns(),
      (PlayerRef p) -> addStat(p, "rift.voidborn.stares-survived", 1));
  private final RiftLevitation levitation = new RiftLevitation(this, cooldowns(), (PlayerRef p) -> {
    addStat(p, "rift.voidborn.levitation-cancelled", 1);
    cue(p, MutationCue.BOON);
  });

  public Voidborn() {
    super(ID);
    registerConfiguration(Config.class);
    needs(PlatformNeed.attribute(Attributes.FALL_DAMAGE_MULTIPLIER));
    setIcon(Id.minecraft("echo_shard"));
    setInterval(1000);
  }

  @Override
  protected List<PlayerPreference<?>> upgradePreferences() {
    return List.of(CommonPreferences.ENABLED, CommonPreferences.DEATH_SAVE_PRIORITY);
  }


  @Override
  public int defaultOrder() {
    return 2;
  }

  @Override
  public boolean protectsFromDamage(EntityDamageEvent event) {
    return event.entity() instanceof PlayerRef player
        && RiftVoidSkin.isLethalDamage(player.health(), event.finalDamage())
        && escape.isEscaping(player);
  }

  @Override
  protected void onRuntimeActivated() {
    escape.activate();
  }

  @Override
  public void unregister() {
    escape.deactivate();
    super.unregister();
  }

  @Override
  public void addStats(int level, StatLines v) {
    statLore(v, TextFormat.YELLOW, "* ", Format.duration(getCooldownMillis(level), 1), 1);
    statLore(v, Format.duration(getResistanceTicks(level) * 50D, 1), 2);
    statLore(v, TextFormat.YELLOW, "* ", Format.duration(getConfig().levitationCooldownSeconds * 1000D, 1), 3);
  }

  @Handle(priority = EventPriority.HIGHEST)
  public void on(EntityDamageEvent e) {
    tryPreventDeath(e);
  }

  public void tryPreventDeath(EntityDamageEvent e) {
    if (e.isCancelled() || !(e.entity() instanceof PlayerRef p)
        || !RiftVoidSkin.isLethalDamage(p.health(), e.finalDamage())) {
      return;
    }

    int level = getActiveLevel(p);
    if (level <= 0 || !escape.isAccepting()) {
      return;
    }

    escape.attempt(p, e, new RiftVoidEscape.Plan(0, true, getConfig().searchRadius, getConfig().minRadius,
        getCooldownMillis(level), getResistanceTicks(level),
        RiftVoidSkin.clampResistanceAmplifier(getConfig().resistanceAmplifier)));
  }

  @Handle
  public void on(EntityTargetEvent e) {
    PlayerRef player = RiftEndermanCalm.endermanTarget(e);
    if (player != null && hasActiveAdaptation(player)) {
      calm.calm(e, player);
    }
  }

  @Handle(priority = EventPriority.HIGHEST)
  public void on(PlayerToggleSneakEvent e) {
    if (e.isSneaking()) {
      levitation.cancel(e.player(), getConfig().levitationCooldownSeconds);
    }
  }

  @Handle
  public void on(PlayerQuitEvent e) {
    escape.quit(e.player());
  }

  @Handle
  public void on(PlayerJoinEvent e) {
    escape.restoreStrandedReservation(e.player());
  }

  @Override
  public void onTick() {
    calm.prune();
  }

  long getCooldownMillis(int level) {
    return Math.max(getConfig().minimumCooldownMillis,
        getConfig().cooldownMillisBase - (level * getConfig().cooldownMillisPerLevel));
  }

  int getResistanceTicks(int level) {
    return getConfig().resistanceTicksBase + (level * getConfig().resistanceTicksPerLevel);
  }

  @ConfigDescription("Lethal damage blinks you to safety for an ender pearl, endermen ignore you, and sneaking ends levitation.")
  public static class Config extends SkillUpgrade.Config {
    @ConfigDoc(value = "Escape cooldown before level scaling, in milliseconds.", impact = "Higher values make escapes rarer.")
    long cooldownMillisBase = 100000;
    @ConfigDoc(value = "Escape cooldown removed per level, in milliseconds.", impact = "Higher values make higher levels escape more often.")
    long cooldownMillisPerLevel = 15000;
    @ConfigDoc(value = "Shortest escape cooldown at any level, in milliseconds.", impact = "Lower values allow more frequent escapes.")
    long minimumCooldownMillis = 30000;
    @ConfigDoc(value = "Farthest an escape searches for a safe spot, in blocks.", impact = "Higher values escape farther; capped at 16.")
    double searchRadius = 12;
    @ConfigDoc(value = "Nearest an escape lands, in blocks.", impact = "Higher values put more distance between you and the danger.")
    double minRadius = 4;
    @ConfigDoc(value = "Resistance after an escape before level scaling, in ticks.", impact = "Higher values protect you longer after escaping.")
    int resistanceTicksBase = 60;
    @ConfigDoc(value = "Resistance after an escape added per level, in ticks.", impact = "Higher values reward higher levels more.")
    int resistanceTicksPerLevel = 30;
    @ConfigDoc(value = "Resistance amplifier after an escape.", impact = "Higher values cut more damage while it lasts.")
    int resistanceAmplifier = 3;
    @ConfigDoc(value = "Time between two levitation releases, in seconds; your fall is guarded for as long.", impact = "Higher values guard the fall longer but allow fewer releases.")
    double levitationCooldownSeconds = 3;
  }
}
