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

package art.arcane.adapt.api.item;

import art.arcane.adapt.api.AdaptApi;
import art.arcane.adapt.api.content.Adaptation;
import art.arcane.adapt.api.content.Cooldowns;
import art.arcane.adapt.api.internal.Engine;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Id;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

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
 *   <li>{@link #forItem(CustomItem.Definition)} / {@link #forGroup(Id)} drives a
 *   custom cooldown group. Only stacks carrying a matching use cooldown
 *   component gray out, so an Adapt item never blocks the vanilla item it is
 *   built from.</li>
 *   <li>{@link #forMaterial(Id)} drives the whole material. Correct only
 *   when graying that material out cannot block legitimate vanilla use.</li>
 *   <li>{@link #hidden()} gates with no overlay at all, for triggers where a
 *   visible cooldown would lie about an unrelated vanilla item.</li>
 * </ul>
 */
@ApiStatus.Experimental
public final class ItemCooldowns {
  private static final long MILLIS_PER_TICK = 50L;

  /**
   * Component duration used purely to declare a group. Adapt pushes the real
   * remaining time itself, so the declared value only has to be positive.
   */
  private static final float GROUP_MARKER_SECONDS = 0.05F;

  private final Cooldowns cooldowns;
  private final @Nullable Id group;
  private final @Nullable Id material;

  private ItemCooldowns(Cooldowns cooldowns, @Nullable Id group, @Nullable Id material) {
    this.cooldowns = cooldowns;
    this.group = group;
    this.material = material;
  }

  /**
   * Cooldown bound to the group of a custom item that has
   * {@linkplain CustomItem.Builder#ownCooldownGroup() its own}: the item's id,
   * so items of two addons never share one.
   *
   * @throws IllegalArgumentException when the item has no cooldown group
   */
  public static ItemCooldowns forItem(CustomItem.Definition<?> item) {
    if (item == null) {
      throw new IllegalArgumentException("cooldown item cannot be null");
    }

    return forGroup(item.cooldownGroup().orElseThrow(() -> new IllegalArgumentException(
        "Item " + item.id() + " has no cooldown group of its own; build it with ownCooldownGroup()")));
  }

  /**
   * Cooldown bound to an explicit custom group.
   */
  public static ItemCooldowns forGroup(Id group) {
    if (group == null) {
      throw new IllegalArgumentException("cooldown group cannot be null");
    }

    return new ItemCooldowns(Engine.bridge().items().cooldowns(null), group, null);
  }

  /**
   * Cooldown bound to an entire material. Only safe when the material has no
   * competing vanilla use.
   */
  public static ItemCooldowns forMaterial(Id material) {
    if (material == null) {
      throw new IllegalArgumentException("cooldown material cannot be null");
    }

    return new ItemCooldowns(Engine.bridge().items().cooldowns(null), null, material);
  }

  /**
   * The same gate and overlay, also shown as {@code owner}'s cooldown glyph on
   * the client HUD whenever it is marked. Call while building the adaptation,
   * before the gate is used: the returned gate starts empty.
   */
  public ItemCooldowns ownedBy(Adaptation<?> owner) {
    return new ItemCooldowns(Engine.bridge().items().cooldowns(owner), group, material);
  }

  /**
   * Gate with no overlay, for triggers where showing one would gray out an
   * unrelated vanilla item.
   */
  public static ItemCooldowns hidden() {
    return new ItemCooldowns(Engine.bridge().items().cooldowns(null), null, null);
  }

  /**
   * Converts a plugin side cooldown in milliseconds into whole client ticks,
   * rounding up so the overlay never clears before the gate opens.
   */
  public static int ticksFromMillis(long millis) {
    if (millis <= 0) {
      return 0;
    }

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
   * Stamps a use cooldown component onto a stack so the client renders the
   * sweep for this group instead of the base material. Idempotent.
   *
   * @return true when the stack was changed
   */
  public static boolean stampGroup(ItemRef stack, Id group) {
    if (stack == null || group == null) {
      return false;
    }

    return Engine.bridge().items().stampCooldownGroup(stack, group, GROUP_MARKER_SECONDS);
  }

  /**
   * The cooldown group this gate drives, or null when it drives a material or
   * nothing at all.
   */
  public @Nullable Id getGroup() {
    return group;
  }

  /**
   * The material this gate drives, or null when it drives a group or nothing.
   */
  public @Nullable Id getMaterial() {
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
  public boolean isReady(PlayerRef p, long cooldownMs) {
    return p != null && cooldowns.isReady(p.uuid(), cooldownMs);
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
  public long remaining(PlayerRef p, long cooldownMs) {
    return p == null ? 0 : cooldowns.remaining(p.uuid(), cooldownMs);
  }

  /**
   * Marks the gate and pushes the matching overlay. The overlay length is read
   * back out of the gate so the two always agree.
   */
  public void mark(PlayerRef p, long cooldownMs) {
    if (p == null) {
      return;
    }

    cooldowns.mark(p.uuid(), cooldownMs);
    push(p, ticksFromMillis(cooldowns.remaining(p.uuid(), cooldownMs)));
  }

  /**
   * Marks the gate and pushes the overlay one tick later, for triggers where
   * vanilla completes the use after this handler returns and would otherwise
   * overwrite the pushed value with the component marker.
   */
  public void markAfterVanillaUse(PlayerRef p, long cooldownMs) {
    if (p == null) {
      return;
    }

    cooldowns.mark(p.uuid(), cooldownMs);
    UUID id = p.uuid();
    AdaptApi.scheduler().runEntity(p, () -> {
      if (p.isOnline()) {
        push(p, ticksFromMillis(cooldowns.remaining(id, cooldownMs)));
      }
    }, 1L);
  }

  /**
   * Single call gate plus mark. Returns false and leaves the gate untouched
   * when the ability is still cooling down.
   */
  public boolean tryUse(PlayerRef p, long cooldownMs) {
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
  public void refresh(PlayerRef p, long cooldownMs) {
    if (p == null) {
      return;
    }

    push(p, ticksFromMillis(cooldowns.remaining(p.uuid(), cooldownMs)));
  }

  /**
   * Clears the gate and any overlay it pushed.
   */
  public void clear(PlayerRef p) {
    if (p == null) {
      return;
    }

    cooldowns.clear(p.uuid());
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
  public static void pushGroup(PlayerRef p, Id group, long remainingMillis) {
    if (p == null || group == null) {
      return;
    }

    dispatch(p, () -> p.setCooldown(group, ticksFromMillis(remainingMillis)));
  }

  /**
   * Pushes a whole material overlay for a caller that already owns its own
   * gate state.
   */
  public static void pushMaterial(PlayerRef p, Id material, long remainingMillis) {
    if (p == null || material == null) {
      return;
    }

    dispatch(p, () -> p.setCooldown(material, ticksFromMillis(remainingMillis)));
  }

  private static void dispatch(PlayerRef p, Runnable push) {
    if (AdaptApi.scheduler().owns(p)) {
      apply(push);
      return;
    }

    AdaptApi.scheduler().runEntity(p, () -> {
      if (p.isOnline()) {
        apply(push);
      }
    });
  }

  private static void apply(Runnable push) {
    try {
      push.run();
    } catch (RuntimeException | LinkageError cosmetic) {
      return;
    }
  }

  private void push(PlayerRef p, int ticks) {
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
