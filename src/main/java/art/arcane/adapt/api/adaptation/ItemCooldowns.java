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

package art.arcane.adapt.api.adaptation;

import art.arcane.adapt.util.common.scheduling.J;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.components.UseCooldownComponent;

import java.util.Locale;
import java.util.UUID;

/**
 * Binds a plugin side {@link Cooldowns} gate to the vanilla item cooldown
 * overlay so the sweep a player sees and the check the server enforces always
 * read from one piece of state.
 *
 * <p>The gate is the authority. The overlay is derived from
 * {@link Cooldowns#remaining(UUID, long)} immediately after the gate is marked,
 * so the two cannot drift.
 *
 * <p>Three surfaces are supported:
 * <ul>
 *   <li>{@link #forItem(Class)} / {@link #forGroup(NamespacedKey)} drives a
 *   custom cooldown group. Only stacks carrying a matching use cooldown
 *   component gray out, so an Adapt item never blocks the vanilla item it is
 *   built from.</li>
 *   <li>{@link #forMaterial(Material)} drives the whole material. Correct only
 *   when graying that material out cannot block legitimate vanilla use.</li>
 *   <li>{@link #hidden()} gates with no overlay at all, for triggers where a
 *   visible cooldown would lie about an unrelated vanilla item.</li>
 * </ul>
 */
public final class ItemCooldowns {
  /**
   * Namespace every Adapt cooldown group lives under.
   */
  public static final String NAMESPACE = "adapt";

  private static final long MILLIS_PER_TICK = 50L;

  /**
   * Component duration used purely to declare a group. Adapt pushes the real
   * remaining time itself, so the declared value only has to be positive.
   */
  private static final float GROUP_MARKER_SECONDS = 0.05F;

  private final Cooldowns cooldowns;
  private final NamespacedKey group;
  private final Material material;

  private ItemCooldowns(Cooldowns cooldowns, NamespacedKey group, Material material) {
    this.cooldowns = cooldowns;
    this.group = group;
    this.material = material;
  }

  /**
   * Cooldown bound to the custom group derived from an Adapt item class.
   */
  public static ItemCooldowns forItem(Class<?> itemType) {
    return forGroup(groupKeyFor(itemType));
  }

  /**
   * Cooldown bound to an explicit custom group.
   */
  public static ItemCooldowns forGroup(NamespacedKey group) {
    if (group == null) {
      throw new IllegalArgumentException("cooldown group cannot be null");
    }

    return new ItemCooldowns(PlayerStateRegistry.newCooldowns(), group, null);
  }

  /**
   * Cooldown bound to an entire material. Only safe when the material has no
   * competing vanilla use.
   */
  public static ItemCooldowns forMaterial(Material material) {
    if (material == null) {
      throw new IllegalArgumentException("cooldown material cannot be null");
    }

    return new ItemCooldowns(PlayerStateRegistry.newCooldowns(), null, material);
  }

  /**
   * Gate with no overlay, for triggers where showing one would gray out an
   * unrelated vanilla item.
   */
  public static ItemCooldowns hidden() {
    return new ItemCooldowns(PlayerStateRegistry.newCooldowns(), null, null);
  }

  /**
   * Converts a plugin side cooldown in milliseconds into whole client ticks,
   * rounding up so the overlay never clears before the gate opens.
   */
  public static int ticksFromMillis(long millis) {
    if (millis <= 0) {
      return 0;
    }

    // Divide before rounding up; adding first overflows on absurd cooldowns.
    long ticks = millis / MILLIS_PER_TICK;
    if (millis % MILLIS_PER_TICK != 0) {
      ticks++;
    }

    return (int) Math.min(ticks, Integer.MAX_VALUE);
  }

  /**
   * Inverse of {@link #ticksFromMillis(long)}.
   */
  public static long millisFromTicks(int ticks) {
    if (ticks <= 0) {
      return 0;
    }

    return ticks * MILLIS_PER_TICK;
  }

  /**
   * Derives the stable cooldown group id for an Adapt item class, so the key a
   * stack is stamped with and the key the gate pushes can never disagree.
   */
  public static String groupIdFor(Class<?> itemType) {
    if (itemType == null) {
      throw new IllegalArgumentException("cooldown item type cannot be null");
    }

    return "item_" + toSnakeCase(itemType.getSimpleName());
  }

  /**
   * Derives the cooldown group key for an Adapt item class.
   */
  public static NamespacedKey groupKeyFor(Class<?> itemType) {
    return groupKey(groupIdFor(itemType));
  }

  /**
   * Builds an Adapt namespaced cooldown group key from a raw id.
   */
  public static NamespacedKey groupKey(String id) {
    if (id == null || id.isEmpty()) {
      throw new IllegalArgumentException("cooldown group id cannot be empty");
    }

    return new NamespacedKey(NAMESPACE, toSnakeCase(id));
  }

  static String toSnakeCase(String name) {
    StringBuilder out = new StringBuilder(name.length() + 8);

    for (int i = 0; i < name.length(); i++) {
      char c = name.charAt(i);

      if (Character.isUpperCase(c)) {
        if (i > 0 && out.length() > 0 && out.charAt(out.length() - 1) != '_') {
          out.append('_');
        }

        out.append(Character.toLowerCase(c));
        continue;
      }

      if (Character.isLetterOrDigit(c)) {
        out.append(Character.toLowerCase(c));
        continue;
      }

      if (out.length() > 0 && out.charAt(out.length() - 1) != '_') {
        out.append('_');
      }
    }

    while (out.length() > 0 && out.charAt(out.length() - 1) == '_') {
      out.setLength(out.length() - 1);
    }

    return out.toString().toLowerCase(Locale.ROOT);
  }

  /**
   * Stamps a use cooldown component onto a stack so the client renders the
   * sweep for this group instead of the base material. Idempotent.
   *
   * @return true when the stack was changed
   */
  public static boolean stampGroup(ItemStack stack, NamespacedKey group) {
    if (stack == null || group == null) {
      return false;
    }

    ItemMeta meta = stack.getItemMeta();
    if (meta == null) {
      return false;
    }

    if (stampGroup(meta, group)) {
      stack.setItemMeta(meta);
      return true;
    }

    return false;
  }

  /**
   * Stamps a use cooldown component onto meta that is about to be applied.
   *
   * @return true when the meta was changed
   */
  public static boolean stampGroup(ItemMeta meta, NamespacedKey group) {
    if (meta == null || group == null) {
      return false;
    }

    if (meta.hasUseCooldown() && group.equals(meta.getUseCooldown().getCooldownGroup())) {
      return false;
    }

    UseCooldownComponent component = meta.getUseCooldown();
    component.setCooldownSeconds(GROUP_MARKER_SECONDS);
    component.setCooldownGroup(group);
    meta.setUseCooldown(component);
    return true;
  }

  /**
   * The cooldown group this gate drives, or null when it drives a material or
   * nothing at all.
   */
  public NamespacedKey getGroup() {
    return group;
  }

  /**
   * The material this gate drives, or null when it drives a group or nothing.
   */
  public Material getMaterial() {
    return material;
  }

  /**
   * True when this gate pushes a client visible overlay.
   */
  public boolean isVisible() {
    return group != null || material != null;
  }

  /**
   * Gate check. Call this before doing any ability work.
   */
  public boolean isReady(UUID id, long cooldownMs) {
    return cooldowns.isReady(id, cooldownMs);
  }

  /**
   * Gate check for a player.
   */
  public boolean isReady(Player p, long cooldownMs) {
    return p != null && cooldowns.isReady(p.getUniqueId(), cooldownMs);
  }

  /**
   * Remaining gate time in milliseconds.
   */
  public long remaining(UUID id, long cooldownMs) {
    return cooldowns.remaining(id, cooldownMs);
  }

  /**
   * Remaining gate time in milliseconds for a player.
   */
  public long remaining(Player p, long cooldownMs) {
    return p == null ? 0 : cooldowns.remaining(p.getUniqueId(), cooldownMs);
  }

  /**
   * Marks the gate and pushes the matching overlay. The overlay length is read
   * back out of the gate so the two always agree.
   */
  public void mark(Player p, long cooldownMs) {
    if (p == null) {
      return;
    }

    cooldowns.mark(p.getUniqueId());
    push(p, ticksFromMillis(cooldowns.remaining(p.getUniqueId(), cooldownMs)));
  }

  /**
   * Marks the gate and pushes the overlay one tick later, for triggers where
   * vanilla completes the use after this handler returns and would otherwise
   * overwrite the pushed value with the component marker.
   */
  public void markAfterVanillaUse(Player p, long cooldownMs) {
    if (p == null) {
      return;
    }

    cooldowns.mark(p.getUniqueId());
    UUID id = p.getUniqueId();
    J.runEntity(p, () -> {
      if (p.isOnline()) {
        push(p, ticksFromMillis(cooldowns.remaining(id, cooldownMs)));
      }
    }, 1);
  }

  /**
   * Single call gate plus mark. Returns false and leaves the gate untouched
   * when the ability is still cooling down.
   */
  public boolean tryUse(Player p, long cooldownMs) {
    if (!isReady(p, cooldownMs)) {
      return false;
    }

    mark(p, cooldownMs);
    return true;
  }

  /**
   * Re-pushes the overlay from current gate state without touching it. Use on
   * discrete events such as the player taking the item back in hand, never on
   * a repeating schedule.
   */
  public void refresh(Player p, long cooldownMs) {
    if (p == null) {
      return;
    }

    push(p, ticksFromMillis(cooldowns.remaining(p.getUniqueId(), cooldownMs)));
  }

  /**
   * Clears the gate and any overlay it pushed.
   */
  public void clear(Player p) {
    if (p == null) {
      return;
    }

    cooldowns.clear(p.getUniqueId());
    push(p, 0);
  }

  /**
   * Clears the gate without touching the client, for offline or bulk resets.
   */
  public void clear(UUID id) {
    if (id != null) {
      cooldowns.clear(id);
    }
  }

  /**
   * Pushes a cooldown group overlay for a caller that already owns its own
   * gate state. Pass the remaining time from that same state so the sweep and
   * the gate cannot disagree.
   */
  public static void pushGroup(Player p, NamespacedKey group, long remainingMillis) {
    if (p == null || group == null) {
      return;
    }

    dispatch(p, () -> p.setCooldown(group, ticksFromMillis(remainingMillis)));
  }

  /**
   * Pushes a whole material overlay for a caller that already owns its own
   * gate state.
   */
  public static void pushMaterial(Player p, Material material, long remainingMillis) {
    if (p == null || material == null) {
      return;
    }

    dispatch(p, () -> p.setCooldown(material, ticksFromMillis(remainingMillis)));
  }

  private static void dispatch(Player p, Runnable push) {
    if (J.isOwnedByCurrentRegion(p)) {
      apply(push);
      return;
    }

    J.runEntity(p, () -> {
      if (p.isOnline()) {
        apply(push);
      }
    });
  }

  private static void apply(Runnable push) {
    try {
      push.run();
    } catch (Throwable ignored) {
      // A cooldown overlay is cosmetic; the gate is the enforcement.
    }
  }

  private void push(Player p, int ticks) {
    if (!isVisible()) {
      return;
    }

    long remainingMillis = millisFromTicks(ticks);
    if (group != null) {
      pushGroup(p, group, remainingMillis);
      return;
    }

    pushMaterial(p, material, remainingMillis);
  }
}
