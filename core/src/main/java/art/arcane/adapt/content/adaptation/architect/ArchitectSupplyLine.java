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

package art.arcane.adapt.content.adaptation.architect;

import art.arcane.adapt.AdaptCore;
import art.arcane.adapt.api.adaptation.Adaptation;
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
import art.arcane.adapt.content.item.BackpackItem;
import art.arcane.adapt.localization.AdaptLanguage;
import art.arcane.adapt.localization.catalog.ArchitectMessages;
import art.arcane.adapt.platform.entity.Hand;
import art.arcane.adapt.platform.entity.PlayerInventoryRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.block.BlockPlaceEvent;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.scheduling.Tasks;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.volmlib.util.math.M;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public class ArchitectSupplyLine extends SimpleAdaptation<ArchitectSupplyLine.Config> {
  public static final PlayerPreference<CommonPreferences.Toggle> LOOSE = CommonPreferences.toggle("loose", ArchitectMessages.ARCHITECTSUPPLYLINE_PREFERENCE_LOOSE, CommonPreferences.Toggle.ON);
  public static final PlayerPreference<CommonPreferences.Toggle> BUNDLES = CommonPreferences.toggle("bundles", ArchitectMessages.ARCHITECTSUPPLYLINE_PREFERENCE_BUNDLES, CommonPreferences.Toggle.ON);
  public static final PlayerPreference<CommonPreferences.Toggle> SHULKERS = CommonPreferences.toggle("shulkers", ArchitectMessages.ARCHITECTSUPPLYLINE_PREFERENCE_SHULKERS, CommonPreferences.Toggle.ON);
  public static final PlayerPreference<CommonPreferences.Toggle> BACKPACKS = CommonPreferences.toggle("backpacks", ArchitectMessages.ARCHITECTSUPPLYLINE_PREFERENCE_BACKPACKS, CommonPreferences.Toggle.ON);
  public static final PlayerPreference<CommonPreferences.Toggle> NAMED = CommonPreferences.toggle("named-containers", ArchitectMessages.ARCHITECTSUPPLYLINE_PREFERENCE_NAMED, CommonPreferences.Toggle.ON);

  private static final int STORAGE_SLOTS = 36;
  private final Map<UUID, RefillWindow> windows = playerState();
  private final Cooldowns failCd = cooldowns();

  public ArchitectSupplyLine() {
    super("architect-supply-line");
    registerConfiguration(ArchitectSupplyLine.Config.class);
    setIcon(Id.minecraft("shulker_box"));
    setInterval(13780);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("shulker_box"))
        .key("challenge_architect_supply_line_100")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .child(AdaptAdvancement.builder()
            .icon(Id.minecraft("shulker_box"))
            .key("challenge_architect_supply_line_1k")
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.VANILLA)
            .build())
        .build());
    registerMilestone("challenge_architect_supply_line_100", "architect.supply-line.refills", 100, 300);
    registerMilestone("challenge_architect_supply_line_1k", "architect.supply-line.refills", 1000, 1000);
  }

  @Override
  public List<PlayerPreference<?>> getPlayerPreferences() {
    return List.of(CommonPreferences.ENABLED, LOOSE, BUNDLES, SHULKERS, BACKPACKS, NAMED);
  }

  @Override
  public void addStats(int level, StatLines v) {
    v.addLore(C.GREEN + AdaptLanguage.text(ArchitectMessages.SUPPLY_LINE_LORE1));
    statLore(v, C.GREEN, "", getRefillsPerMinute(getLevelPercent(level)), 2);
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(BlockPlaceEvent e) {
    ItemRef hand = e.itemInHand();
    if (!consumesLastOfStack(hand)) {
      return;
    }

    PlayerRef p = e.player();
    Adaptation.BlockActionContext context = resolveBlockPlaceContext(p, e.block().location());
    if (context == null) {
      return;
    }

    UUID playerId = p.uuid();
    int allowed = getRefillsPerMinute(getLevelPercent(context.level()));
    if (!hasRefillCapacity(playerId, allowed)) {
      playRefillFailure(p);
      return;
    }

    ItemRef template = hand.copyWithAmount(1);
    Hand slot = e.hand();
    Tasks.runEntity(p, () -> refill(p, slot, template, allowed), 1);
  }

  /**
   * The placed stack is the last one when the hand holds a single item at
   * placement time, or already reports zero on servers that consume the item
   * before monitor handlers run.
   */
  static boolean consumesLastOfStack(ItemRef hand) {
    return hand != null && !hand.type().isAir() && hand.amount() <= 1;
  }

  private boolean hasRefillCapacity(UUID id, int allowed) {
    return hasRefillCapacity(windows.get(id), M.ms(), allowed);
  }

  private void refill(PlayerRef p, Hand slot, ItemRef template, int allowed) {
    if (!p.isOnline()) {
      return;
    }

    ItemRef current = slot == Hand.OFF_HAND ? p.inventory().offHand() : p.inventory().mainHand();
    if (current != null && !current.type().isAir()) {
      return;
    }

    UUID playerId = p.uuid();
    if (!hasRefillCapacity(playerId, allowed)) {
      playRefillFailure(p);
      return;
    }

    ItemRef pulled = pullMatching(p, template);
    if (pulled == null) {
      return;
    }

    if (slot == Hand.OFF_HAND) {
      p.inventory().setOffHand(pulled);
    } else {
      p.inventory().setMainHand(pulled);
    }
    windows.compute(playerId, (id, window) -> nextSuccessfulWindow(window, M.ms()));

    fx(p.location().add(0, 1, 0), FxPriority.TRANSITION)
        .particle(Particles.WAX_ON, 6, 0, 0, 0, 0.3D, 0)
        .column(Particles.END_ROD, 4, 0.8D)
        .chord(Sounds.BLOCK_COMPOSTER_FILL, 0.6f, 1.3f, Sounds.ITEM_BUNDLE_REMOVE_ONE, 0.4f, 1.4f);

    addStat(p, "architect.supply-line.refills", 1);
    xp(p, getConfig().xpPerRefill);
  }

  private void playRefillFailure(PlayerRef player) {
    UUID playerId = player.uuid();
    if (failCd.isReady(playerId, 1500)) {
      failCd.mark(playerId);
      fx(player.location(), FxPriority.TRANSITION).sound(Sounds.BLOCK_DISPENSER_FAIL, 0.3f, 0.9f);
    }
  }

  /**
   * Loose stacks first, then bundles, then Adapt backpacks, then shulker boxes.
   * Loose stacks are what a player expects to consume next, containers are the
   * deep supply.
   */
  private ItemRef pullMatching(PlayerRef p, ItemRef template) {
    PlayerInventoryRef inventory = p.inventory();
    ItemRef[] contents = new ItemRef[STORAGE_SLOTS];
    for (int slot = 0; slot < STORAGE_SLOTS; slot++) {
      contents[slot] = inventory.item(slot);
    }
    int loose = matchingSlot(contents, template, inventory.heldSlot());
    if (loose >= 0 && preferenceEnabled(p, LOOSE)) {
      ItemRef pulled = contents[loose].copy();
      inventory.clear(loose);
      return pulled;
    }

    for (int slot = 0; slot < contents.length; slot++) {
      ItemRef candidate = contents[slot];
      if (candidate == null || candidate.type().isAir()) {
        continue;
      }

      if (!preferenceEnabled(p, NAMED) && candidate.hasDisplayName()) {
        continue;
      }
      ItemRef pulled = preferenceEnabled(p, BUNDLES) ? pullFromBundle(candidate, template) : null;
      if (pulled == null && preferenceEnabled(p, BACKPACKS)) {
        pulled = pullFromBackpack(candidate, template);
      }
      if (pulled == null && preferenceEnabled(p, SHULKERS)) {
        pulled = pullFromShulker(candidate, template);
      }

      if (pulled != null) {
        inventory.setItem(slot, candidate);
        return pulled;
      }
    }

    return null;
  }

  /**
   * Mode agnostic: both backpack storage models keep an ordinary stack array,
   * so a refill never has to know whether the backpack is in slot or bundle
   * mode. An unreadable payload is left completely untouched.
   */
  private ItemRef pullFromBackpack(ItemRef container, ItemRef template) {
    if (!BackpackItem.isBackpack(container)) {
      return null;
    }

    ItemRef[] stored;
    try {
      stored = BackpackItem.readContents(container);
    } catch (IllegalStateException unreadable) {
      return null;
    }

    BackpackItem.BackpackExtraction extraction = BackpackItem.extractMatching(stored, template);
    if (extraction == null) {
      return null;
    }

    BackpackItem.Data data = BackpackItem.dataOf(container);
    ItemRef[] remaining = BackpackItem.modeOf(container) == BackpackItem.Mode.BUNDLE
        ? BackpackItem.compact(extraction.remaining())
        : extraction.remaining();
    BackpackItem.write(
        container,
        BackpackItem.Data.of(
            data.getId(),
            BackpackItem.Mode.parse(data.getMode()),
            data.getCapacity(),
            BackpackItem.modeOf(container) == BackpackItem.Mode.BUNDLE
                ? BackpackItem.heapWeight(BackpackItem.readHeap(remaining))
                : BackpackItem.countStored(remaining)
        ),
        remaining
    );
    return extraction.pulled();
  }

  private ItemRef pullFromShulker(ItemRef container, ItemRef template) {
    if (!isShulkerBox(container.type())) {
      return null;
    }

    Optional<List<ItemRef>> contents = container.containerContents();
    if (contents.isEmpty()) {
      return null;
    }

    List<ItemRef> stored = new ArrayList<>(contents.get());
    int slot = matchingSlot(stored.toArray(new ItemRef[0]), template);
    if (slot < 0) {
      return null;
    }

    ItemRef pulled = stored.get(slot).copy();
    stored.set(slot, AdaptCore.platform().host().items().empty());
    container.setContainerContents(stored);
    return pulled;
  }

  private ItemRef pullFromBundle(ItemRef container, ItemRef template) {
    Optional<List<ItemRef>> items = container.bundleContents();
    if (items.isEmpty()) {
      return null;
    }

    BundleExtraction extraction = extractFromBundle(items.get(), template);
    if (extraction == null) {
      return null;
    }

    container.setBundleContents(extraction.remaining());
    return extraction.pulled();
  }

  static boolean isShulkerBox(Id type) {
    return type != null && type.path().endsWith("shulker_box");
  }

  /**
   * Matches on the whole item, not just its material, so custom named or
   * enchanted stacks are never silently swapped for a plain one.
   */
  static int matchingSlot(ItemRef[] contents, ItemRef template) {
    return matchingSlot(contents, template, -1);
  }

  static int matchingSlot(ItemRef[] contents, ItemRef template, int skipSlot) {
    if (contents == null || template == null) {
      return -1;
    }

    for (int i = 0; i < contents.length; i++) {
      ItemRef candidate = contents[i];
      if (i == skipSlot || candidate == null || candidate.type().isAir() || candidate.amount() <= 0) {
        continue;
      }

      if (candidate.isSimilar(template)) {
        return i;
      }
    }

    return -1;
  }

  static BundleExtraction extractFromBundle(List<ItemRef> items, ItemRef template) {
    if (items == null || items.isEmpty() || template == null) {
      return null;
    }

    List<ItemRef> remaining = new ArrayList<>(items.size());
    ItemRef pulled = null;
    for (ItemRef item : items) {
      if (pulled == null && item != null && !item.type().isAir() && item.isSimilar(template)) {
        pulled = item.copy();
        continue;
      }

      remaining.add(item);
    }

    return pulled == null ? null : new BundleExtraction(pulled, remaining);
  }

  private int getRefillsPerMinute(double factor) {
    return (int) Math.max(1, M.lerp(getConfig().minRefillsPerMinute, getConfig().maxRefillsPerMinute, factor));
  }

  static boolean hasRefillCapacity(RefillWindow window, long now, int allowed) {
    return allowed > 0 && (window == null || now - window.start() >= 60000L || window.used() < allowed);
  }

  static RefillWindow nextSuccessfulWindow(RefillWindow window, long now) {
    if (window == null || now - window.start() >= 60000L) {
      return new RefillWindow(now, 1);
    }
    return new RefillWindow(window.start(), window.used() + 1);
  }

  record RefillWindow(long start, int used) {
  }

  record BundleExtraction(ItemRef pulled, List<ItemRef> remaining) {
  }

  @ConfigDescription("Automatically refill your hand from your inventory, bundles, or shulker boxes when a placed stack runs out.")
  protected static class Config extends AdaptationConfig {
    @art.arcane.adapt.util.config.ConfigDoc(value = "Hand refills allowed per minute at level 0 progression.", impact = "Higher values let low-level players refill more often.")
    int minRefillsPerMinute = 4;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Hand refills allowed per minute at maximum level progression.", impact = "Higher values let max-level players refill more often.")
    int maxRefillsPerMinute = 20;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Adaptation xp granted per successful refill.", impact = "Higher values speed up adaptation progression from refills.")
    double xpPerRefill = 2;

    public Config() {
      baseCost = 5;
      costFactor = 0.5;
    }
  }
}
