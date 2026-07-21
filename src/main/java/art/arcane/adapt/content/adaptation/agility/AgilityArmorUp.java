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

package art.arcane.adapt.content.adaptation.agility;

import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.attribute.AdaptAttributeService;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.version.IAttribute;
import art.arcane.adapt.api.version.Version;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.format.Localizer;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.adapt.util.reflect.registries.Attributes;
import art.arcane.adapt.util.reflect.registries.Particles;
import art.arcane.volmlib.util.format.Form;
import art.arcane.volmlib.util.inventorygui.Element;
import art.arcane.volmlib.util.math.M;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.event.player.PlayerToggleSprintEvent;

import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

public class AgilityArmorUp extends SimpleAdaptation<AgilityArmorUp.Config> {
  private static final int DECAY_BATCH_SIZE = 128;
  private static final String SLOT_PLATING = "plating";
  static final UUID LEGACY_MODIFIER = UUID.nameUUIDFromBytes("adapt-armor-up".getBytes());
  static final NamespacedKey LEGACY_MODIFIER_KEY = NamespacedKey.fromString("adapt:armor-up");
  private final Map<UUID, RuntimeState> states = playerState();
  private final Queue<UUID> decayQueue = new ConcurrentLinkedQueue<>();
  private final Set<UUID> queuedDecay = ConcurrentHashMap.newKeySet();

  public AgilityArmorUp() {
    super("agility-armor-up");
    registerConfiguration(Config.class);
    setIcon(Material.IRON_CHESTPLATE);
    setInterval(50);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.IRON_CHESTPLATE)
        .key("challenge_agility_armor_up_30min")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.PARENT_GRANTED)
        .build());
    registerMilestone("challenge_agility_armor_up_30min", "agility.armor-up.ticks-armored", 36000, 500);
  }

  @Override
  public void addStats(int level, Element v) {
    statLore(v, Form.f(getWindupArmor(getLevelPercent(level)) * 10, 1), 1);
    v.addLore(C.YELLOW + "* " + Form.duration(getWindupTicks(getLevelPercent(level)) * 50D, 1) + " " + C.GRAY + Localizer.dLocalize("agility.armor_up.lore2"));
    v.addLore(C.YELLOW + "* " + Form.duration(getDecaySeconds(getLevelPercent(level)) * 1000D, 1) + " " + C.GRAY + Localizer.dLocalize("agility.armor_up.lore3"));
  }

  @EventHandler
  public void on(PlayerJoinEvent e) {
    purgeLegacyModifier(e.getPlayer());
  }

  @EventHandler
  public void on(PlayerQuitEvent e) {
    clearAndRemoveState(e.getPlayer());
  }

  @EventHandler
  public void on(PlayerDeathEvent e) {
    clearAndRemoveState(e.getEntity());
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void on(PlayerMoveEvent e) {
    updatePlayer(e.getPlayer(), null, null);
  }

  @EventHandler
  public void on(PlayerToggleSprintEvent e) {
    updatePlayer(e.getPlayer(), e.isSprinting(), null);
  }

  @EventHandler
  public void on(PlayerToggleSneakEvent e) {
    if (e.isSneaking()) {
      updatePlayer(e.getPlayer(), null, true);
    }
  }

  @Override
  public boolean hasTickDemand() {
    return !queuedDecay.isEmpty() || !decayQueue.isEmpty();
  }

  @Override
  public void onTick() {
    int attempts = Math.min(DECAY_BATCH_SIZE, queuedDecay.size());
    for (int i = 0; i < attempts; i++) {
      UUID id = decayQueue.poll();
      if (id == null) {
        break;
      }
      queuedDecay.remove(id);
      RuntimeState state = states.get(id);
      if (state == null || !state.decaying) {
        continue;
      }
      Player p = Bukkit.getPlayer(id);
      if (p == null || !p.isOnline()) {
        states.remove(id, state);
        continue;
      }
      withPlayerThread(p, () -> {
        updatePlayer(p, null, null);
        RuntimeState current = states.get(id);
        if (current != null && current.decaying) {
          queueDecay(id, current);
        }
      });
      RuntimeState current = states.get(id);
      if (current != null && current.decaying) {
        queueDecay(id, current);
      }
    }
  }

  private double getWindupTicks(double factor) {
    return M.lerp(getConfig().windupTicksSlowest, getConfig().windupTicksFastest, factor);
  }

  private double getWindupArmor(double factor) {
    return getConfig().windupArmorBase + (factor * getConfig().windupArmorLevelMultiplier);
  }

  private double getDecaySeconds(double factor) {
    return getConfig().decaySecondsBase + (factor * getConfig().decaySecondsMaxLevelBonus);
  }

  private void updatePlayer(Player p, Boolean sprintingOverride, Boolean sneakingOverride) {
    if (p == null || !p.isOnline()) {
      return;
    }

    UUID id = p.getUniqueId();
    RuntimeState state = states.get(id);
    boolean sprinting = sprintingOverride == null ? p.isSprinting() : sprintingOverride;
    boolean sneaking = sneakingOverride == null ? p.isSneaking() : sneakingOverride;
    if (state == null && !sprinting) {
      return;
    }
    boolean platingActive = isPlatingActive(p, sprinting, sneaking);
    if (!platingActive && state == null) {
      return;
    }

    double factor = getLevelPercent(p);
    if (platingActive) {
      if (state == null) {
        state = new RuntimeState();
        states.put(id, state);
        purgeLegacyModifier(p);
      }
      double elapsedTicks = updateElapsedTicks(state);
      buildPlating(p, state, factor, elapsedTicks);
      if (queuedDecay.remove(id)) {
        decayQueue.remove(id);
      }
      return;
    }

    double elapsedTicks = updateElapsedTicks(state);
    decayPlating(p, state, factor, elapsedTicks);
    if (state.plating <= 0D) {
      states.remove(id, state);
      if (queuedDecay.remove(id)) {
        decayQueue.remove(id);
      }
      return;
    }
    queueDecay(id, state);
  }

  private boolean isPlatingActive(Player p, boolean sprinting, boolean sneaking) {
    if (!hasActiveAdaptation(p)) {
      return false;
    }
    if (p.isSwimming() || p.isFlying() || p.isGliding() || sneaking) {
      return false;
    }
    return sprinting;
  }

  private void buildPlating(Player p, RuntimeState state, double factor, double elapsedTicks) {
    state.decaying = false;
    double ticksToMax = Math.max(1D, getWindupTicks(factor));
    state.plating = Math.min(1.0D, state.plating + (elapsedTicks / ticksToMax));
    emitPlatingFeedback(p, state, state.plating);
    double armorInc = getWindupArmor(factor) * state.plating;
    AdaptAttributeService.get().apply(p, getName(), SLOT_PLATING, Attributes.ARMOR, armorInc * 10, AttributeModifier.Operation.ADD_NUMBER);
    if (elapsedTicks > 0D) {
      addStat(p, "agility.armor-up.ticks-armored", elapsedTicks);
    }
  }

  private void decayPlating(Player p, RuntimeState state, double factor, double elapsedTicks) {
    if (state.plating <= 0D) {
      AdaptAttributeService.get().remove(p, getName(), SLOT_PLATING, Attributes.ARMOR);
      return;
    }

    if (!state.decaying) {
      state.decaying = true;
      double decayTicks = Math.max(1D, getDecaySeconds(factor) * 20D);
      state.decayPerTick = state.plating / decayTicks;
    }

    state.plating = Math.max(0D, state.plating - (state.decayPerTick * elapsedTicks));
    if (state.plating <= 0D) {
      completeDecay(p, state);
      return;
    }

    double armorInc = getWindupArmor(factor) * state.plating;
    AdaptAttributeService.get().apply(p, getName(), SLOT_PLATING, Attributes.ARMOR, armorInc * 10, AttributeModifier.Operation.ADD_NUMBER);
    emitDecayShimmer(p);
  }

  private void completeDecay(Player p, RuntimeState state) {
    state.plating = 0D;
    state.decaying = false;
    state.lastBracket = 0;
    state.fullyArmored = false;
    AdaptAttributeService.get().remove(p, getName(), SLOT_PLATING, Attributes.ARMOR);
    fx(p.getLocation(), FxPriority.TRANSITION)
        .burst(Particles.SMOKE, 3, 0.1D)
        .sound(Sound.ITEM_ARMOR_EQUIP_LEATHER, 0.3F, 0.5F);
  }

  private void emitPlatingFeedback(Player p, RuntimeState state, double progress) {
    if (progress >= 1.0D && M.r(0.2)) {
      fx(p.getLocation(), FxPriority.AMBIENT).particle(Particle.END_ROD, 1, 0, 0.1D, 0, 0.05D, 0);
    }

    int bracket = (int) Math.floor(progress / 0.25D);
    if (bracket <= state.lastBracket) {
      return;
    }

    state.lastBracket = bracket;
    if (progress >= 1.0D) {
      if (state.fullyArmored) {
        return;
      }

      state.fullyArmored = true;
      fx(p, FxPriority.GAMEPLAY)
          .dome(Particle.WAX_ON, 0.8D, 10)
          .chord(Sound.BLOCK_ANVIL_LAND, 0.25F, 1.6F, Sound.ITEM_ARMOR_EQUIP_NETHERITE, 0.4F, 1.0F);
      return;
    }

    if (bracket < 1) {
      return;
    }

    float pitch = switch (bracket) {
      case 1 -> 0.8F;
      case 2 -> 1.0F;
      default -> 1.3F;
    };
    fx(p, FxPriority.GAMEPLAY)
        .helix(Particle.END_ROD, 0.6D, 1.4D, 6, 0)
        .sound(Sound.ITEM_ARMOR_EQUIP_IRON, 0.4F, pitch);
  }

  private void emitDecayShimmer(Player p) {
    if (!M.r(0.1)) {
      return;
    }

    fx(p.getLocation(), FxPriority.AMBIENT).particle(Particle.END_ROD, 1, 0, 0.1D, 0, 0.02D, 0);
  }

  private void clearAndRemoveState(Player p) {
    if (p == null) {
      return;
    }

    states.remove(p.getUniqueId());
    if (queuedDecay.remove(p.getUniqueId())) {
      decayQueue.remove(p.getUniqueId());
    }
  }

  private void queueDecay(UUID id, RuntimeState state) {
    if (state.decaying && state.plating > 0D && queuedDecay.add(id)) {
      decayQueue.add(id);
    }
  }

  private double updateElapsedTicks(RuntimeState state) {
    long now = System.currentTimeMillis();
    double elapsed = elapsedTicks(state.lastUpdateAt, now);
    state.lastUpdateAt = now;
    return elapsed;
  }

  static double elapsedTicks(long previousUpdateAt, long now) {
    if (previousUpdateAt <= 0L) {
      return 1D;
    }
    if (now <= previousUpdateAt) {
      return 0D;
    }
    return Math.min(20D, (now - previousUpdateAt) / 50D);
  }

  private void purgeLegacyModifier(Player p) {
    if (p == null) {
      return;
    }

    withPlayerThread(p, () -> {
      IAttribute attribute = Version.get().getAttribute(p, Attributes.ARMOR);
      if (attribute == null) {
        return;
      }

      attribute.removeModifier(LEGACY_MODIFIER, LEGACY_MODIFIER_KEY);
    });
  }


  @ConfigDescription("Gain more armor the longer you sprint.")
  protected static class Config extends AdaptationConfig {
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Windup Ticks Slowest for the Agility Armor Up adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double windupTicksSlowest = 180;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Windup Ticks Fastest for the Agility Armor Up adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double windupTicksFastest = 60;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Windup Armor Base for the Agility Armor Up adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double windupArmorBase = 0.22;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Windup Armor Level Multiplier for the Agility Armor Up adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double windupArmorLevelMultiplier = 0.525;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Decay Seconds Base for the Agility Armor Up adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double decaySecondsBase = 5.0;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Decay Seconds Max Level Bonus for the Agility Armor Up adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double decaySecondsMaxLevelBonus = 5.0;

    public Config() {
      baseCost = 2;
      costFactor = 0.65;
      initialCost = 8;
    }
  }

  private static class RuntimeState {
    private long lastUpdateAt;
    private volatile double plating;
    private int lastBracket;
    private boolean fullyArmored;
    private volatile boolean decaying;
    private double decayPerTick;
  }

}
