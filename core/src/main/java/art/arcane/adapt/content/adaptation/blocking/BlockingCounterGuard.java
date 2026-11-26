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

package art.arcane.adapt.content.adaptation.blocking;

import art.arcane.adapt.api.adaptation.Adaptation;
import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.adaptation.StatLines;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.fx.Fx;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.fx.Particles;
import art.arcane.adapt.api.fx.Sounds;
import art.arcane.adapt.api.notification.ActionBarNotification;
import art.arcane.adapt.client.protocol.HudGlyph;
import art.arcane.adapt.client.server.hud.AbilityGlyphs;
import art.arcane.adapt.api.preference.CommonPreferences;
import art.arcane.adapt.api.preference.PlayerPreference;
import art.arcane.adapt.localization.AdaptLanguage;
import art.arcane.adapt.localization.catalog.BlockingMessages;
import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.LivingRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.entity.ProjectileRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.entity.EntityDamageByEntityEvent;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.volmlib.util.format.Form;
import art.arcane.volmlib.util.math.M;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public class BlockingCounterGuard extends SimpleAdaptation<BlockingCounterGuard.Config> {
  private static final long STACK_DISPLAY_MILLIS = 750L;
  private final Map<UUID, Integer> reservedStackCosts = playerState();
  private final BlockingRetaliation retaliation = new BlockingRetaliation(this);

  public BlockingCounterGuard() {
    super("blocking-counter-guard");
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("iron_bars"));
    setInterval(1000);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("shield"))
        .key("challenge_blocking_counter_500")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .build());
    registerMilestone("challenge_blocking_counter_500", "blocking.counter-guard.damage-reflected", 500, 500);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("shield"))
        .key("challenge_blocking_counter_max")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .build());
  }

  @Override
  protected void onRuntimeActivated() {
    retaliation.activate();
  }

  @Override
  public void unregister() {
    retaliation.close();
    reservedStackCosts.clear();
    super.unregister();
  }

  @Override
  public List<PlayerPreference<?>> getPlayerPreferences() {
    return List.of(CommonPreferences.ENABLED, BlockingPreferences.PLAYERS);
  }

  @Override
  public void addStats(int level, StatLines v) {
    statLore(v, getMaxStacks(level), 1);
    statLore(v, Form.pc(getReflectChance(level), 0), 2);
    statLore(v, Form.f(getReflectDamage(level)), 3);
  }

  @Handle(priority = EventPriority.HIGHEST)
  public void on(EntityDamageByEntityEvent e) {
    if (retaliation.consume(e.damager(), e.entity())) {
      return;
    }

    if (!(e.entity() instanceof PlayerRef defender)) {
      return;
    }

    if (!retaliation.accepting() || !hasActiveAdaptation(defender) || !BlockingShields.held(this, defender)) {
      return;
    }

    int level = getActiveLevel(defender);
    int maxStacks = getMaxStacks(level);
    int stacks = getStorageInt(defender, "counterStacks", 0);

    if (defender.isBlocking()) {
      int before = stacks;
      stacks = Math.min(maxStacks, stacks + 1);
      setStorage(defender, "counterStacks", stacks);
      if (stacks != before) {
        boolean nowMaxed = stacks >= maxStacks;
        boolean wasMaxed = getStorageInt(defender, "counterMaxed", 0) == 1;
        displayStacks(defender, stacks, maxStacks);
        stackGainCue(defender, stacks, maxStacks, nowMaxed);
        if (nowMaxed && !wasMaxed) {
          setStorage(defender, "counterMaxed", 1);
          fx(defender.location().add(0, 1.0D, 0), FxPriority.COMBAT)
              .sound(Sounds.BLOCK_BEACON_ACTIVATE, 0.3F, 1.8F);
        }
      }
    }

    UUID defenderId = defender.uuid();
    int availableStacks = availableStacks(
        stacks,
        reservedStackCosts.getOrDefault(defenderId, 0)
    );
    if (availableStacks <= 0 || !M.r(getReflectChance(level))) {
      return;
    }

    EntityRef source = e.damager();
    if (source instanceof ProjectileRef projectile && projectile.shooter() != null) {
      source = projectile.shooter();
    }

    if (!(source instanceof LivingRef attacker)
        || (attacker instanceof PlayerRef && !preferenceEnabled(defender, BlockingPreferences.PLAYERS))) {
      return;
    }

    int stackCost = Math.max(0, getConfig().stackCostOnReflect);
    reserveStackCost(defenderId, stackCost);
    double reflected = getReflectDamage(level) + (availableStacks * getConfig().damagePerStack);
    ReflectionOperation operation = new ReflectionOperation(
        defenderId,
        reflected,
        stackCost,
        availableStacks >= maxStacks,
        maxStacks
    );
    boolean scheduled = retaliation.strike(defender, attacker, reflected,
        () -> releaseStackCost(defenderId, stackCost),
        (Loc attackerLocation) -> completeReflectionOnDefender(defender, attackerLocation, operation));
    if (!scheduled) {
      releaseStackCost(defenderId, stackCost);
    }
  }

  private void stackGainCue(PlayerRef defender, int stacks, int maxStacks, boolean maxed) {
    Loc chest = defender.location().add(0, 1.0D, 0);
    int points = Math.min(8, Math.max(1, stacks));
    if (maxed) {
      fx(chest, FxPriority.COMBAT)
          .ring(Particles.END_ROD, 0.5D, points, 0)
          .sound(Sounds.BLOCK_AMETHYST_BLOCK_CHIME, 0.4F, 1.6F);
      return;
    }
    float pitch = (float) (0.8D + (0.6D * ((double) stacks / maxStacks)));
    fx(chest, FxPriority.COMBAT)
        .ring(Particles.CRIT_MAGIC, 0.5D, points, 0)
        .sound(Sounds.BLOCK_AMETHYST_BLOCK_CHIME, 0.4F, pitch);
  }

  private void completeReflectionOnDefender(
      PlayerRef defender,
      Loc attackerLocation,
      ReflectionOperation operation
  ) {
    releaseStackCost(operation.defenderId(), operation.stackCost());
    if (!retaliation.accepting() || !defender.isOnline() || defender.isDead()) {
      return;
    }

    int currentStacks = getStorageInt(defender, "counterStacks", 0);
    int newStacks = spentStacks(currentStacks, operation.stackCost());
    setStorage(defender, "counterStacks", newStacks);
    setStorage(defender, "counterMaxed", newStacks >= operation.maxStacks() ? 1 : 0);
    displayStacks(defender, newStacks, operation.maxStacks());
    if (operation.maxed()) {
      grantOnce(defender, "challenge_blocking_counter_max");
    }
    reflectDischarge(this, defender, attackerLocation);
    xp(defender, operation.reflectedDamage() * getConfig().xpPerReflectedDamage);
    addStat(
        defender,
        "blocking.counter-guard.damage-reflected",
        operation.reflectedDamage()
    );
  }

  private void reserveStackCost(UUID defenderId, int cost) {
    if (cost > 0) {
      reservedStackCosts.merge(defenderId, cost, Integer::sum);
    }
  }

  private void releaseStackCost(UUID defenderId, int cost) {
    if (cost <= 0) {
      return;
    }
    reservedStackCosts.computeIfPresent(defenderId, (unused, reserved) -> {
      int remaining = reserved - cost;
      return remaining > 0 ? remaining : null;
    });
  }

  static int availableStacks(int stacks, int reserved) {
    return Math.max(0, stacks - Math.max(0, reserved));
  }

  static int spentStacks(int currentStacks, int cost) {
    return Math.max(0, currentStacks - Math.max(0, cost));
  }

  static String stackFraction(int stacks, int maxStacks) {
    return Math.max(0, stacks) + "/" + Math.max(1, maxStacks);
  }

  private void displayStacks(PlayerRef defender, int stacks, int maxStacks) {
    if (AbilityGlyphs.capable(defender.uuid())) {
      if (stacks > 0) {
        AbilityGlyphs.charges(defender.uuid(), this, stacks, maxStacks);
      } else {
        AbilityGlyphs.clear(defender.uuid(), this, HudGlyph.CHARGES);
      }
      return;
    }
    String name = C.GOLD + AdaptLanguage.text(BlockingMessages.COUNTER_GUARD_NAME);
    getPlayer(defender).getActionBarNotifier().queue(ActionBarNotification.builder()
        .duration(350)
        .maxTTL(M.ms() + STACK_DISPLAY_MILLIS)
        .group("blocking-counter-guard")
        .title(name + C.GRAY + " " + stackFraction(stacks, maxStacks))
        .build());
  }

  public static void reflectDischarge(Adaptation<?> owner, PlayerRef defender, Loc attackerLocation) {
    Loc from = defender.eyeLocation();
    Loc to = attackerLocation.add(0, 1.0D, 0);
    if (from.sameWorld(to)) {
      int points = (int) Math.min(8, Math.max(3, from.distance(to) * 2));
      Fx.now(owner, from, FxPriority.COMBAT)
          .line(Particles.CRIT_MAGIC, to.x(), to.y(), to.z(), points);
    } else {
      Fx.now(owner, from, FxPriority.COMBAT).burst(Particles.CRIT_MAGIC, 3, 0.2D);
    }
    Fx.now(owner, to, FxPriority.COMBAT)
        .burst(Particles.END_ROD, 6, 0.2D)
        .chord(Sounds.ITEM_TRIDENT_RETURN, 0.7F, 1.3F, Sounds.ENTITY_ARROW_HIT, 0.5F, 0.9F);
  }

  private double getReflectChance(int level) {
    return Math.min(getConfig().maxReflectChance, getConfig().reflectChanceBase + (getLevelPercent(level) * getConfig().reflectChanceFactor));
  }

  private int getMaxStacks(int level) {
    return Math.max(1, (int) Math.round(getConfig().baseStacks + (getLevelPercent(level) * getConfig().stackFactor)));
  }

  private double getReflectDamage(int level) {
    return getConfig().baseReflectDamage + (getLevelPercent(level) * getConfig().reflectDamageFactor);
  }

  private record ReflectionOperation(
      UUID defenderId,
      double reflectedDamage,
      int stackCost,
      boolean maxed,
      int maxStacks
  ) {
  }


  @ConfigDescription("Blocking builds retaliation stacks that reflect damage back to attackers.")
  protected static class Config extends AdaptationConfig {
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Base Stacks for the Blocking Counter Guard adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double baseStacks = 2;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Stack Factor for the Blocking Counter Guard adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double stackFactor = 8;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Reflect Chance Base for the Blocking Counter Guard adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double reflectChanceBase = 0.08;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Reflect Chance Factor for the Blocking Counter Guard adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double reflectChanceFactor = 0.27;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Max Reflect Chance for the Blocking Counter Guard adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double maxReflectChance = 0.6;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Base Reflect Damage for the Blocking Counter Guard adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double baseReflectDamage = 1;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Reflect Damage Factor for the Blocking Counter Guard adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double reflectDamageFactor = 3.5;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Damage Per Stack for the Blocking Counter Guard adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double damagePerStack = 0.28;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Stack Cost On Reflect for the Blocking Counter Guard adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    int stackCostOnReflect = 1;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Xp Per Reflected Damage for the Blocking Counter Guard adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double xpPerReflectedDamage = 5.0;

    public Config() {
      baseCost = 3;
      costFactor = 0.6;
      initialCost = 2;
    }
  }
}
