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

package art.arcane.adapt.content.adaptation.rift;

import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.config.ConfigDescription;
import art.arcane.adapt.api.config.ConfigDoc;
import art.arcane.adapt.api.content.Adaptation;
import art.arcane.adapt.api.content.AdaptationConfig;
import art.arcane.adapt.api.content.DeathSave;
import art.arcane.adapt.api.content.SimpleAdaptation;
import art.arcane.adapt.api.content.StatLines;
import art.arcane.adapt.api.fx.Fx;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.fx.Particles;
import art.arcane.adapt.api.fx.Sounds;
import art.arcane.adapt.api.preference.CommonPreferences;
import art.arcane.adapt.api.preference.PlayerPreference;
import art.arcane.adapt.api.text.Format;
import art.arcane.adapt.api.text.Text;
import art.arcane.adapt.api.text.TextFormat;
import art.arcane.adapt.platform.advancement.AdvancementFrame;
import art.arcane.adapt.platform.advancement.AdvancementVisibility;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.entity.EntityDamageByEntityEvent;
import art.arcane.adapt.platform.event.entity.EntityDamageEvent;
import art.arcane.adapt.platform.event.player.PlayerJoinEvent;
import art.arcane.adapt.platform.event.player.PlayerQuitEvent;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.world.WorldRef;

import java.util.List;
import java.util.Set;

public class RiftVoidSkin extends SimpleAdaptation<RiftVoidSkin.Config> implements DeathSave {
  public static final Id ID = Id.adapt("rift/void_skin");
  public static final PlayerPreference<Reserve> RESERVE = PlayerPreference.of("reserve", Reserve.class, Reserve.NONE, List.of(
          new PlayerPreference.Choice<>(Reserve.NONE, Text.key("adapt.riftvoidskin.preferences.preference_reserve_none"), Id.minecraft("ender_pearl"), 1),
          new PlayerPreference.Choice<>(Reserve.ONE, Text.key("adapt.riftvoidskin.preferences.preference_reserve_one"), Id.minecraft("ender_eye"), 1),
          new PlayerPreference.Choice<>(Reserve.FOUR, Text.key("adapt.riftvoidskin.preferences.preference_reserve_four"), Id.minecraft("ender_chest"), 1))).labelled(Text.key("adapt.riftvoidskin.preferences.preference_reserve"));
  public static final PlayerPreference<Causes> CAUSES = PlayerPreference.of("causes", Causes.class, Causes.ALL, List.of(
          new PlayerPreference.Choice<>(Causes.ALL, Text.key("adapt.riftvoidskin.preferences.preference_causes_all"), Id.minecraft("totem_of_undying"), 1),
          new PlayerPreference.Choice<>(Causes.COMBAT, Text.key("adapt.riftvoidskin.preferences.preference_causes_combat"), Id.minecraft("iron_sword"), 1),
          new PlayerPreference.Choice<>(Causes.ENVIRONMENT, Text.key("adapt.riftvoidskin.preferences.preference_causes_environment"), Id.minecraft("lava_bucket"), 1))).labelled(Text.key("adapt.riftvoidskin.preferences.preference_causes"));
  public static final PlayerPreference<CommonPreferences.Toggle> SPAWN = CommonPreferences.toggle("spawn-fallback", Text.key("adapt.riftvoidskin.preferences.preference_spawn"), CommonPreferences.Toggle.ON);

  public static final Id RESISTANCE = Id.minecraft("resistance");
  public static final Id ENDER_PEARL = Id.minecraft("ender_pearl");
  public static final Set<Id> HAZARDS = Set.of(Id.minecraft("lava"), Id.minecraft("fire"), Id.minecraft("soul_fire"),
      Id.minecraft("campfire"), Id.minecraft("soul_campfire"), Id.minecraft("magma_block"), Id.minecraft("cactus"),
      Id.minecraft("powder_snow"), Id.minecraft("sweet_berry_bush"), Id.minecraft("wither_rose"));
  private final RiftVoidEscape escape = new RiftVoidEscape(this, (PlayerRef p) -> {
    addStat(p, "rift.void-skin.escapes", 1);
    addStat(p, "rift.teleports", 1);
    xp(p, getConfig().xpOnEscape, "rift:void-skin:escape");
  });

  public RiftVoidSkin() {
    super(ID);
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("echo_shard"));
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("ender_pearl"))
        .key("challenge_rift_void_skin_50")
        .frame(AdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .child(AdaptAdvancement.builder()
            .icon(Id.minecraft("echo_shard"))
            .key("challenge_rift_void_skin_500")
            .frame(AdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.VANILLA)
            .build())
        .build());
    registerMilestone("challenge_rift_void_skin_50", "rift.void-skin.escapes", 50, 400);
    registerMilestone("challenge_rift_void_skin_500", "rift.void-skin.escapes", 500, 1500);
  }

  @Override
  public List<PlayerPreference<?>> getPlayerPreferences() {
    return List.of(CommonPreferences.ENABLED, CommonPreferences.DEATH_SAVE_PRIORITY, RESERVE, CAUSES, SPAWN);
  }


  @Override
  public int defaultOrder() {
    return 2;
  }

  @Override
  public boolean protectsFromDamage(EntityDamageEvent event) {
    return event.entity() instanceof PlayerRef player
        && isLethalDamage(player.health(), event.finalDamage())
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
    v.addLore(TextFormat.GREEN + "+ " + Text.key("adapt.rift.void_skin.lore1").resolve());
    statLore(v, TextFormat.YELLOW, "* ", Format.duration(getCooldownMillis(level), 1), 2);
    v.addLore(TextFormat.RED + "* " + Text.key("adapt.rift.void_skin.lore3").resolve());
  }


  @Handle(priority = EventPriority.HIGHEST)
  public void on(EntityDamageEvent e) {
    tryPreventDeath(e);
  }

  public void tryPreventDeath(EntityDamageEvent e) {
    if (e.isCancelled() || !(e.entity() instanceof PlayerRef p)) {
      return;
    }

    if (!isLethalDamage(p.health(), e.finalDamage())) {
      return;
    }

    Causes causes = preference(p, CAUSES);
    boolean combat = e instanceof EntityDamageByEntityEvent;
    if ((causes == Causes.COMBAT && !combat) || (causes == Causes.ENVIRONMENT && combat)) {
      return;
    }
    int level = getActiveLevel(p);
    if (level <= 0 || !escape.isAccepting()) {
      return;
    }

    int reserve = switch (preference(p, RESERVE)) { case NONE -> 0; case ONE -> 1; case FOUR -> 4; };
    escape.attempt(p, e, new RiftVoidEscape.Plan(reserve, preferenceEnabled(p, SPAWN), getConfig().searchRadius,
        getConfig().minRadius, getCooldownMillis(level), getResistanceTicks(level), getResistanceAmplifier()));
  }

  @Handle
  public void on(PlayerQuitEvent e) {
    escape.quit(e.player());
  }

  @Handle
  public void on(PlayerJoinEvent e) {
    escape.restoreStrandedReservation(e.player());
  }

  private long getCooldownMillis(int level) {
    long value = getConfig().cooldownBaseMillis - ((long) Math.max(0, level - 1) * getConfig().cooldownReductionPerLevelMillis);
    return Math.max(getConfig().minimumCooldownMillis, value);
  }

  private int getResistanceTicks(int level) {
    return Math.max(20, getConfig().resistanceTicksBase + (level * getConfig().resistanceTicksPerLevel));
  }

  private int getResistanceAmplifier() {
    return clampResistanceAmplifier(getConfig().resistanceAmplifier);
  }

  public static int clampResistanceAmplifier(int amplifier) {
    return Math.max(0, Math.min(4, amplifier));
  }

  static boolean acceptSettlement(boolean defaultConsumed, boolean defaultCostSuppressed, boolean settled) {
    return defaultConsumed || !defaultCostSuppressed || settled;
  }

  public static boolean isLethalDamage(double health, double finalDamage) {
    return Double.isFinite(health)
        && Double.isFinite(finalDamage)
        && health > 0D
        && finalDamage >= health;
  }

  static boolean cancelLethalDamage(EntityDamageEvent event, boolean escapeReserved) {
    if (!escapeReserved) {
      return false;
    }
    event.setCancelled(true);
    return true;
  }

  static Loc worldSpawnFallback(WorldRef currentWorld) {
    if (currentWorld == null) {
      return null;
    }
    Loc spawn = currentWorld.spawnLocation();
    if (spawn == null || !currentWorld.equals(spawn.world())) {
      return null;
    }
    return new Loc(spawn.world(), spawn.x(), spawn.y(), spawn.z(), spawn.yaw(), spawn.pitch());
  }

  public static void escapeFx(Adaptation<?> owner, PlayerRef p, Loc origin, Loc destination) {
    Fx.now(owner, origin, FxPriority.TRANSITION)
        .dome(Particles.ENCHANT, 1.1, 16)
        .particle(Particles.WITCH, 10, 0, 1.0, 0, 0.4, 0.02)
        .chord(Sounds.ENTITY_ENDERMAN_TELEPORT, 0.6f, 0.8f, Sounds.BLOCK_AMETHYST_BLOCK_RESONATE, 0.5f, 1.2f);
    Fx.now(owner, destination, FxPriority.TRANSITION)
        .ring(Particles.END_ROD, 0.8, 12, 0.1)
        .particle(Particles.REVERSE_PORTAL, 8, 0, 1.0, 0, 0.3, 0.04)
        .except(p)
        .chord(Sounds.ENTITY_ENDERMAN_TELEPORT, 0.6f, 1.3f, Sounds.ITEM_ARMOR_EQUIP_NETHERITE, 0.5f, 1.1f);
    Fx.sound(owner, p, destination, Sounds.ENTITY_ENDERMAN_TELEPORT, 0.6f, 1.3f);
    Fx.sound(owner, p, destination, Sounds.ITEM_ARMOR_EQUIP_NETHERITE, 0.5f, 1.1f);
  }

  @ConfigDescription("Any lethal damage blinks you to a nearby safe spot, or the current world's spawn when none exists, with brief resistance for one ender pearl.")
  protected static class Config extends AdaptationConfig {
    @ConfigDoc(value = "Base cooldown in milliseconds between escapes.", impact = "Higher values force longer waits between escapes.")
    long cooldownBaseMillis = 120000;
    @ConfigDoc(value = "Cooldown reduction in milliseconds per adaptation level beyond the first.", impact = "Higher values make leveling shorten the cooldown faster.")
    long cooldownReductionPerLevelMillis = 18000;
    @ConfigDoc(value = "Lowest possible cooldown in milliseconds regardless of level.", impact = "Higher values keep a floor under cooldown reduction.")
    long minimumCooldownMillis = 45000;
    @ConfigDoc(value = "Base resistance duration in ticks applied after an escape.", impact = "Higher values keep the resistance buff up longer.")
    int resistanceTicksBase = 60;
    @ConfigDoc(value = "Extra resistance ticks per adaptation level.", impact = "Higher values extend the resistance buff as you level.")
    int resistanceTicksPerLevel = 20;
    @ConfigDoc(value = "Resistance amplifier applied after an escape.", impact = "Higher values reduce incoming damage more during the buff.")
    int resistanceAmplifier = 2;
    @ConfigDoc(value = "Maximum horizontal search radius in blocks for a safe blink spot.", impact = "Higher values let the escape reach further, up to a hard 16-block limit.")
    double searchRadius = 9;
    @ConfigDoc(value = "Minimum horizontal blink distance in blocks.", impact = "Higher values push the escape further from where you were hit.")
    double minRadius = 4;
    @ConfigDoc(value = "Skill XP granted when an escape triggers.", impact = "Higher values grant more skill XP per escape.")
    double xpOnEscape = 40;

    public Config() {
      baseCost = 7;
      costFactor = 0.7;
      maxLevel = 4;
      initialCost = 6;
    }
  }

  public enum Reserve { NONE, ONE, FOUR }

  public enum Causes { ALL, COMBAT, ENVIRONMENT }
}
