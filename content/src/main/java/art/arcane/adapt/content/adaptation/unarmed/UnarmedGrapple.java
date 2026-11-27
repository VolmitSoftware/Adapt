/*------------------------------------------------------------------------------
 -   Adapt is a Skill/Integration plugin  for Minecraft Bukkit Servers
 -   Copyright (c) 2022 Arcane Arts (Volmit Software)
 -
 -   This program is free software: you can redistribute it and/or modify
 -   it under the terms of the GNU General Public License as published by
 -   the Free Software Foundation, either version 3 of the License, or
 -   (at your option) any later version.
 -
 -   This program is distributed in the hope that it will be useful,
 -   but WITHOUT ANY WARRANTY; without even the implied warranty of
 -   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 -   GNU General Public License for more details.
 -
 -   You should have received a copy of the GNU General Public License
 -   along with this program.  If not, see <https://www.gnu.org/licenses/>.
 -----------------------------------------------------------------------------*/

package art.arcane.adapt.content.adaptation.unarmed;

import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.config.ConfigDescription;
import art.arcane.adapt.api.config.ConfigDoc;
import art.arcane.adapt.api.content.Adaptation;
import art.arcane.adapt.api.content.AdaptationConfig;
import art.arcane.adapt.api.content.SimpleAdaptation;
import art.arcane.adapt.api.content.StatLines;
import art.arcane.adapt.api.fx.Fx;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.fx.FxTimeline;
import art.arcane.adapt.api.fx.Particles;
import art.arcane.adapt.api.fx.Sounds;
import art.arcane.adapt.api.item.ItemKinds;
import art.arcane.adapt.api.player.PlayerProfile;
import art.arcane.adapt.api.preference.CommonPreferences;
import art.arcane.adapt.api.preference.PlayerPreference;
import art.arcane.adapt.api.text.Format;
import art.arcane.adapt.api.text.Text;
import art.arcane.adapt.api.text.TextFormat;
import art.arcane.adapt.platform.advancement.AdvancementFrame;
import art.arcane.adapt.platform.advancement.AdvancementVisibility;
import art.arcane.adapt.platform.entity.LivingRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.entity.EntityDamageByEntityEvent;
import art.arcane.adapt.platform.event.player.PlayerToggleSneakEvent;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.value.Vec3;

import java.util.List;

public class UnarmedGrapple extends SimpleAdaptation<UnarmedGrapple.Config> {
  public static final Id ID = Id.adapt("unarmed/grapple");
  private final UnarmedGrab grab = new UnarmedGrab(this, playerState(), playerState(), cooldowns(),
      () -> getConfig().grabTimeoutMillis, this::getThrow, (PlayerRef player, LivingRef target) -> {
        xp(player, getConfig().xpPerHurl, "grapple");
        addStat(player, "unarmed.grapple.hurled-mobs", 1);
      });

  public UnarmedGrapple() {
    super(ID);
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("lead"));
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("iron_ingot"))
        .key("challenge_unarmed_grapple_100")
        .frame(AdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .child(AdaptAdvancement.builder()
            .icon(Id.minecraft("diamond"))
            .key("challenge_unarmed_grapple_1k")
            .frame(AdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.VANILLA)
            .build())
        .build());
    registerMilestone("challenge_unarmed_grapple_100", "unarmed.grapple.hurled-mobs", 100, 400);
    registerMilestone("challenge_unarmed_grapple_1k", "unarmed.grapple.hurled-mobs", 1000, 1500);
  }

  @Override
  public void onPlayerPreferencesChanged(PlayerProfile player) {
    grab.forget(player.player());
  }

  @Override
  public List<PlayerPreference<?>> getPlayerPreferences() {
    return List.of(CommonPreferences.ENABLED, UnarmedPreferences.TARGETS, UnarmedPreferences.RELEASE);
  }

  @Override
  public void addStats(int level, StatLines v) {
    statLore(v, Format.f(getForce(level), 1), 1);
    statLore(v, TextFormat.YELLOW, "* ", Format.duration((double) getCooldownMillis(level), 1), 2);
    v.addLore(TextFormat.GRAY + Text.key("adapt.unarmed.grapple.lore3").resolve());
    statLore(v, TextFormat.RED, "- ", Format.f(getConfig().exhaustionPerThrow, 1), 4);
  }

  @Handle(priority = EventPriority.HIGHEST)
  public void on(EntityDamageByEntityEvent e) {
    Adaptation.AttackContext attack = resolveAttackContext(e);
    if (attack == null) {
      return;
    }

    PlayerRef p = attack.attacker();
    if (ItemKinds.isTool(p.inventory().mainHand()) || ItemKinds.isTool(p.inventory().offHand())) {
      return;
    }

    grab.strike(p, attack.target(), attack.level());
  }

  @Handle(priority = EventPriority.MONITOR, ignoreCancelled = false)
  public void on(PlayerToggleSneakEvent e) {
    if (!e.isSneaking()) {
      grab.unsneak(e.player());
    }
  }

  static void grabFx(Adaptation<?> owner, PlayerRef p, LivingRef victim) {
    Loc mob = victim.location().add(0, 1, 0);
    Fx.now(owner, mob, FxPriority.COMBAT)
        .particle(Particles.CRIT, 6, 0, 0, 0, 0.3D, 0.05D)
        .chord(Sounds.ITEM_ARMOR_EQUIP_LEATHER, 1.0F, 0.7F, Sounds.BLOCK_NOTE_BLOCK_BASEDRUM, 0.4F, 0.8F);
    Fx.now(owner, p.eyeLocation(), FxPriority.TRANSITION)
        .line(Particles.CRIT, mob.x(), mob.y(), mob.z(), 8);
  }

  static void hurlFx(Adaptation<?> owner, LivingRef target, Vec3 velocity) {
    Fx.now(owner, target.location().add(0, 0.8D, 0), FxPriority.COMBAT)
        .particle(Particles.CLOUD, 8, 0, 0, 0, 0.25D, 0.05D)
        .trail(Particles.CRIT, velocity.x(), velocity.y(), velocity.z(), 1.6D, 6)
        .sound(Sounds.ENTITY_PLAYER_ATTACK_KNOCKBACK, 0.9F, 1.2F);
    FxTimeline.follow(owner, target)
        .duration(6)
        .priority(FxPriority.TRAIL)
        .frame((fx, tick, progress) -> fx.particle(Particles.CRIT, 1, 0, 0.2D, 0, 0.05D, 0.0D))
        .start();
  }

  static void throwFx(Adaptation<?> owner, PlayerRef player) {
    FxTimeline.follow(owner, player)
        .duration(3)
        .priority(FxPriority.COMBAT)
        .frame((fx, tick, progress) -> fx.sound(Sounds.ENTITY_PLAYER_ATTACK_SWEEP, 0.9F, (float) (0.9D + (progress * 0.4D))))
        .start();
  }

  private UnarmedGrab.Throw getThrow(int level) {
    return new UnarmedGrab.Throw(getForce(level), getConfig().upwardBoost + (getLevelPercent(level) * getConfig().upwardBoostFactor),
        getConfig().maxHurlRange, getCooldownMillis(level), getConfig().exhaustionPerThrow);
  }

  private double getForce(int level) {
    return getConfig().forceBase + (getLevelPercent(level) * getConfig().forceFactor);
  }

  private long getCooldownMillis(int level) {
    return Math.max(1000L, (long) Math.round(getConfig().cooldownMillisBase - (getLevelPercent(level) * getConfig().cooldownMillisFactor)));
  }

  @ConfigDescription("Sneak-punch a target to grab it, then hurl it where you look.")
  protected static class Config extends AdaptationConfig {
    @ConfigDoc(value = "Base hurl force at level 1.", impact = "Higher values throw grabbed targets further.")
    double forceBase = 0.9;
    @ConfigDoc(value = "Additional hurl force granted at max level.", impact = "Higher values throw grabbed targets further as levels increase.")
    double forceFactor = 1.4;
    @ConfigDoc(value = "Base upward component added to the hurl velocity.", impact = "Higher values arc thrown mobs higher.")
    double upwardBoost = 0.2;
    @ConfigDoc(value = "Additional upward hurl component granted at max level.", impact = "Higher values arc thrown mobs higher as levels increase.")
    double upwardBoostFactor = 0.25;
    @ConfigDoc(value = "Maximum distance in blocks a grabbed target can be hurled from.", impact = "Higher values let the grab persist over larger gaps.")
    double maxHurlRange = 6;
    @ConfigDoc(value = "Milliseconds before an unused grab expires.", impact = "Higher values keep the grab primed for longer.")
    long grabTimeoutMillis = 5000;
    @ConfigDoc(value = "Base grapple cooldown in milliseconds at level 1.", impact = "Higher values make the ability usable less often.")
    double cooldownMillisBase = 9000;
    @ConfigDoc(value = "Cooldown reduction in milliseconds granted at max level.", impact = "Higher values make the ability recharge faster as levels increase.")
    double cooldownMillisFactor = 5000;
    @ConfigDoc(value = "Exhaustion added to the player per hurled mob.", impact = "Higher values drain saturation and food faster with each throw. Set to 0 to disable the exhaustion cost.")
    double exhaustionPerThrow = 2.0;
    @ConfigDoc(value = "XP granted per hurled target.", impact = "Higher values speed up unarmed skill progression from grapples.")
    double xpPerHurl = 32;

    public Config() {
      baseCost = 3;
      costFactor = 0.6;
      initialCost = 2;
    }
  }
}
