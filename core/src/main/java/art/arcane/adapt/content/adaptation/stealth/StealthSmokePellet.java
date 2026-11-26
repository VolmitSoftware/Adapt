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

package art.arcane.adapt.content.adaptation.stealth;

import art.arcane.adapt.AdaptCore;
import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.adaptation.Cooldowns;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.adaptation.StatLines;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.fx.Particles;
import art.arcane.adapt.api.fx.Sounds;
import art.arcane.adapt.api.preference.CommonPreferences;
import art.arcane.adapt.api.preference.PlayerPreference;
import art.arcane.adapt.api.world.AdaptPlayer;
import art.arcane.adapt.client.server.hud.AbilityGlyphs;
import art.arcane.adapt.localization.catalog.StealthMessages;
import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.EquipmentSlot;
import art.arcane.adapt.platform.entity.LivingRef;
import art.arcane.adapt.platform.entity.PlayerInventoryRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.entity.EntityTargetEvent;
import art.arcane.adapt.platform.event.player.PlayerQuitEvent;
import art.arcane.adapt.platform.event.player.PlayerToggleSneakEvent;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.value.Vec3;
import art.arcane.adapt.platform.world.RayHit;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.volmlib.util.format.Form;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public class StealthSmokePellet extends SimpleAdaptation<StealthSmokePellet.Config> {
  public static final PlayerPreference<Hand> HAND = new PlayerPreference<>(Hand.class,
      new PlayerPreference.Definition<>("hand", StealthMessages.STEALTHSMOKEPELLET_PREFERENCE_HAND, Hand.EITHER, List.of(
          new PlayerPreference.Choice<>(Hand.EITHER, StealthMessages.STEALTHSMOKEPELLET_PREFERENCE_HAND_EITHER, Id.minecraft("gunpowder"), 1),
          new PlayerPreference.Choice<>(Hand.MAIN, StealthMessages.STEALTHSMOKEPELLET_PREFERENCE_HAND_MAIN, Id.minecraft("iron_sword"), 1),
          new PlayerPreference.Choice<>(Hand.OFF, StealthMessages.STEALTHSMOKEPELLET_PREFERENCE_HAND_OFF, Id.minecraft("shield"), 1))));
  public static final PlayerPreference<Gesture> GESTURE = new PlayerPreference<>(Gesture.class,
      new PlayerPreference.Definition<>("gesture", StealthMessages.STEALTHSMOKEPELLET_PREFERENCE_GESTURE, Gesture.SINGLE, List.of(
          new PlayerPreference.Choice<>(Gesture.SINGLE, StealthMessages.STEALTHSMOKEPELLET_PREFERENCE_GESTURE_SINGLE, Id.minecraft("feather"), 1),
          new PlayerPreference.Choice<>(Gesture.DOUBLE, StealthMessages.STEALTHSMOKEPELLET_PREFERENCE_GESTURE_DOUBLE, Id.minecraft("rabbit_foot"), 1))));
  public static final PlayerPreference<Reserve> RESERVE = new PlayerPreference<>(Reserve.class,
      new PlayerPreference.Definition<>("reserve", StealthMessages.STEALTHSMOKEPELLET_PREFERENCE_RESERVE, Reserve.NONE, List.of(
          new PlayerPreference.Choice<>(Reserve.NONE, StealthMessages.STEALTHSMOKEPELLET_PREFERENCE_RESERVE_NONE, Id.minecraft("bowl"), 1),
          new PlayerPreference.Choice<>(Reserve.ONE, StealthMessages.STEALTHSMOKEPELLET_PREFERENCE_RESERVE_ONE, Id.minecraft("gunpowder"), 1),
          new PlayerPreference.Choice<>(Reserve.FOUR, StealthMessages.STEALTHSMOKEPELLET_PREFERENCE_RESERVE_FOUR, Id.minecraft("tnt"), 1))));

  private final Map<UUID, Long> lastPress = playerState();
  private static final Id GUNPOWDER = Id.minecraft("gunpowder");
  private static final Id AIR = Id.minecraft("air");

  private final Cooldowns cooldowns = cooldowns();
  private final StealthSmoke smoke = new StealthSmoke(this::cloudFx);

  public StealthSmokePellet() {
    super("stealth-smoke-pellet");
    registerConfiguration(Config.class);
    setIcon(GUNPOWDER);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("gunpowder"))
        .key("challenge_stealth_smoke_100")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .child(AdaptAdvancement.builder()
            .icon(Id.minecraft("tnt"))
            .key("challenge_stealth_smoke_1k")
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.VANILLA)
            .build())
        .build());
    registerMilestone("challenge_stealth_smoke_100", "stealth.smoke-pellet.thrown", 100, 400);
    registerMilestone("challenge_stealth_smoke_1k", "stealth.smoke-pellet.thrown", 1000, 1500);
  }

  static double computeRadius(double base, double factor, double maxRadius, double percent) {
    return Math.min(maxRadius, base + (percent * factor));
  }

  static int computePulses(double base, double factor, double percent) {
    return Math.max(1, (int) Math.round(base + (percent * factor)));
  }

  static double computeRaycastRange(double configured) {
    return Double.isFinite(configured) ? Math.max(2D, Math.min(64D, configured)) : 24D;
  }

  static EquipmentSlot gunpowderHand(boolean enteringSneak, Id mainHand, Id offHand) {
    if (!enteringSneak) {
      return null;
    }
    if (GUNPOWDER.equals(mainHand)) {
      return EquipmentSlot.MAIN_HAND;
    }
    return GUNPOWDER.equals(offHand) ? EquipmentSlot.OFF_HAND : null;
  }

  static boolean isBlindable(EntityRef entity) {
    return entity instanceof LivingRef;
  }

  @Override
  public List<PlayerPreference<?>> getPlayerPreferences() {
    return List.of(CommonPreferences.ENABLED, HAND, GESTURE, RESERVE);
  }

  @Override
  public void onPlayerPreferencesChanged(AdaptPlayer player) {
    lastPress.remove(player.getPlayer().uuid());
  }

  @Override
  public void addStats(int level, StatLines v) {
    statLore(v, Form.f(getRadius(level), 1), 1);
    statLore(v, Form.duration(getPulses(level) * StealthSmoke.PULSE_INTERVAL_TICKS * 50D, 1), 2);
  }

  @Handle(priority = EventPriority.NORMAL)
  public void on(PlayerToggleSneakEvent e) {
    PlayerRef p = e.player();
    PlayerInventoryRef inventory = p.inventory();
    EquipmentSlot hand = gunpowderHand(
        e.isSneaking(),
        preference(p, HAND) == Hand.OFF ? AIR : inventory.mainHand().type(),
        preference(p, HAND) == Hand.MAIN ? AIR : inventory.offHand().type()
    );
    if (hand == null) {
      return;
    }

    int level = getActiveLevel(p);
    if (level <= 0) {
      return;
    }

    if (!cooldowns.isReady(p.uuid(), getConfig().cooldownMillis)) {
      return;
    }

    if (preference(p, GESTURE) == Gesture.DOUBLE) {
      long now = System.currentTimeMillis();
      Long last = lastPress.put(p.uuid(), now);
      if (last == null || now - last > 350L) {
        return;
      }
      lastPress.remove(p.uuid());
    }
    if (!consumeGunpowder(p, inventory, hand)) {
      return;
    }
    cooldowns.mark(p.uuid(), getConfig().cooldownMillis);
    throwRay(p, level);
  }

  @Handle(priority = EventPriority.HIGHEST)
  public void on(EntityTargetEvent e) {
    smoke.onTarget(e);
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(PlayerQuitEvent e) {
    smoke.forget(e.player());
  }

  boolean isConcealed(PlayerRef player) {
    return smoke.isConcealed(player);
  }

  private boolean consumeGunpowder(PlayerRef p, PlayerInventoryRef inventory, EquipmentSlot hand) {
    boolean offHand = hand == EquipmentSlot.OFF_HAND;
    ItemRef held = offHand ? inventory.offHand() : inventory.mainHand();
    int reserve = switch (preference(p, RESERVE)) {
      case NONE -> 0;
      case ONE -> 1;
      case FOUR -> 4;
    };
    if (held.amount() <= reserve) {
      return false;
    }
    return payItemCost(p, "pellet", AdaptCore.platform().host().items().create(GUNPOWDER), 1, () -> {
      held.setAmount(held.amount() - 1);
      if (offHand) {
        inventory.setOffHand(held);
      } else {
        inventory.setMainHand(held);
      }
      return true;
    });
  }

  private void throwRay(PlayerRef p, int level) {
    Loc eye = p.eyeLocation();
    Vec3 direction = eye.direction().normalize();
    double raycastRange = computeRaycastRange(getConfig().raycastRange);
    RayHit hit = p.world().rayTrace(
        eye.position(),
        direction,
        raycastRange,
        false,
        0.35D,
        entity -> entity instanceof LivingRef && !entity.uuid().equals(p.uuid())
    );
    Vec3 hitPosition = hit == null
        ? eye.position().add(direction.multiply(raycastRange))
        : hit.position();
    if (hit != null && hit.block() != null) {
      hitPosition = hitPosition.subtract(direction.multiply(0.2D));
    }
    Loc impact = Loc.of(p.world(), hitPosition);
    double radius = getRadius(level);
    int pulses = getPulses(level);
    int trailPoints = Math.max(4, Math.min(32, (int) Math.ceil(eye.distance(impact) * 2D)));

    addStat(p, "stealth.smoke-pellet.thrown", 1);
    xp(p, getConfig().xpOnThrow);
    fx(eye, FxPriority.TRANSITION)
        .line(Particles.SMOKE, impact.x(), impact.y(), impact.z(), trailPoints)
        .burst(Particles.SMOKE, 4, 0.15D)
        .sound(Sounds.ENTITY_WITCH_THROW, 0.4F, 1.4F);
    smoke.release(impact, radius, pulses);
    AbilityGlyphs.duration(p.uuid(), this, System.currentTimeMillis(), pulses * StealthSmoke.PULSE_INTERVAL_TICKS * 50L);
  }

  private void cloudFx(Loc center, double radius, int pulsesLeft) {
    fx(center, FxPriority.GAMEPLAY)
        .particle(Particles.SMOKE, (int) Math.max(8, radius * 6), 0, 0, 0, radius * 0.55D, 0.01D)
        .particle(Particles.CAMPFIRE_COSY_SMOKE, (int) Math.max(2, radius), 0, 0, 0, radius * 0.5D, 0.005D);
    if (pulsesLeft % 2 == 0) {
      fx(center, FxPriority.AMBIENT).sound(Sounds.BLOCK_FIRE_EXTINGUISH, 0.3F, 0.7F);
    }
  }

  @Override
  public void unregister() {
    smoke.close();
    super.unregister();
  }

  private double getRadius(int level) {
    return computeRadius(getConfig().radiusBase, getConfig().radiusFactor, getConfig().radiusMax, getLevelPercent(level));
  }

  private int getPulses(int level) {
    return computePulses(getConfig().pulsesBase, getConfig().pulsesFactor, getLevelPercent(level));
  }

  @ConfigDescription("Sneak while holding gunpowder in either hand to cast a smoke cloud that conceals players, blinds living entities, and clears mob aggression.")
  protected static class Config extends AdaptationConfig {
    @art.arcane.adapt.util.config.ConfigDoc(value = "Base cloud radius of the smoke pellet.", impact = "Higher values blind living entities across a wider area.")
    double radiusBase = 2.5;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Extra cloud radius gained across levels.", impact = "Higher values expand the cloud more per level.")
    double radiusFactor = 2.5;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Maximum cloud radius after scaling.", impact = "Caps how large the smoke cloud can get.")
    double radiusMax = 6.0;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Base number of blinding pulses in the cloud.", impact = "Higher values make the cloud last longer.")
    double pulsesBase = 8;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Extra pulses gained across levels.", impact = "Higher values extend cloud duration more per level.")
    double pulsesFactor = 10;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Maximum smoke ray distance in blocks.", impact = "Higher values place the smoke cloud farther along the player's aim.")
    double raycastRange = 24.0;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Cooldown between pellet throws, in milliseconds.", impact = "Higher values mean longer waits between throws.")
    long cooldownMillis = 1500;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Experience granted per pellet thrown.", impact = "Higher values level the adaptation faster.")
    double xpOnThrow = 10;

    public Config() {
      baseCost = 2;
      costFactor = 0.5;
      maxLevel = 3;
      initialCost = 1;
    }
  }

  public enum Hand { EITHER, MAIN, OFF }

  public enum Gesture { SINGLE, DOUBLE }

  public enum Reserve { NONE, ONE, FOUR }
}
