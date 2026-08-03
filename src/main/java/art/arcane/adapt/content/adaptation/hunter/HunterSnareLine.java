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

package art.arcane.adapt.content.adaptation.hunter;

import art.arcane.adapt.localization.AdaptLanguage;
import art.arcane.adapt.localization.catalog.HunterMessages;

import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.adaptation.ReceiveCancelledEvents;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.attribute.AdaptAttributeService;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.recipe.AdaptRecipe;
import art.arcane.adapt.api.recipe.MaterialChar;
import art.arcane.adapt.util.common.compat.PaperCompat;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.scheduling.J;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.adapt.util.reflect.registries.Attributes;
import art.arcane.adapt.util.reflect.registries.Particles;
import art.arcane.volmlib.util.format.Form;
import art.arcane.volmlib.util.inventorygui.Element;
import art.arcane.volmlib.util.math.M;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.block.Block;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Vector;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

public class HunterSnareLine extends SimpleAdaptation<HunterSnareLine.Config> {
  private static final NamespacedKey SNARE_ITEM_KEY = NamespacedKey.fromString("adapt:hunter-snare-item");
  private static final Color SNARE_GREEN = Color.fromRGB(120, 180, 90);
  private final List<Snare> snares = new CopyOnWriteArrayList<>();

  public HunterSnareLine() {
    super("hunter-snare-line");
    registerConfiguration(Config.class);
    setIcon(Material.TRIPWIRE_HOOK);
    setInterval(250);
    registerRecipe(AdaptRecipe.shaped()
        .key("hunter-snare")
        .ingredient(new MaterialChar('S', Material.STRING))
        .ingredient(new MaterialChar('I', Material.IRON_INGOT))
        .shapes(List.of(
            "S S",
            "SIS",
            "S S"))
        .result(buildSnareItem(2))
        .build());
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.TRIPWIRE_HOOK)
        .key("challenge_hunter_snare_200")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.PARENT_GRANTED)
        .child(AdaptAdvancement.builder()
            .icon(Material.LEAD)
            .key("challenge_hunter_snare_2k")
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.PARENT_GRANTED)
            .build())
        .build());
    registerMilestone("challenge_hunter_snare_200", "hunter.snare-line.mobs-snared", 200, 400);
    registerMilestone("challenge_hunter_snare_2k", "hunter.snare-line.mobs-snared", 2000, 1500);
  }

  @Override
  public void addStats(int level, Element v) {
    statLore(v, Form.f(getRootDurationTicks(level) / 20.0D, 1), 1);
    statLore(v, C.YELLOW, "+ ", getCharges(level), 2);
    v.addLore(C.GRAY + "- " + AdaptLanguage.text(HunterMessages.SNARE_LINE_LORE3));
  }

  static int scaleInt(double levelPercent, int base, double factor) {
    return Math.max(1, base + (int) Math.round(levelPercent * factor));
  }

  static double rootSpeedScalar(int amplifier) {
    return -Math.min(1.0D, 0.15D * (Math.max(0, amplifier) + 1.0D));
  }

  int getRootDurationTicks(int level) {
    return scaleInt(getLevelPercent(level), getConfig().rootDurationTicksBase, getConfig().rootDurationTicksFactor);
  }

  int getCharges(int level) {
    return scaleInt(getLevelPercent(level), getConfig().chargesBase, getConfig().chargesFactor);
  }

  static ItemStack buildSnareItem(int amount) {
    ItemStack item = new ItemStack(Material.TRIPWIRE_HOOK, amount);
    ItemMeta meta = item.getItemMeta();
    if (meta != null) {
      meta.setDisplayName(C.GREEN + AdaptLanguage.text(HunterMessages.SNARE_LINE_ITEM));
      if (SNARE_ITEM_KEY != null) {
        meta.getPersistentDataContainer().set(SNARE_ITEM_KEY, PersistentDataType.BYTE, (byte) 1);
      }
      item.setItemMeta(meta);
    }
    return item;
  }

  static boolean isSnareItem(ItemStack item) {
    if (item == null || item.getType() != Material.TRIPWIRE_HOOK || SNARE_ITEM_KEY == null || !item.hasItemMeta()) {
      return false;
    }
    ItemMeta meta = item.getItemMeta();
    return meta != null && meta.getPersistentDataContainer().has(SNARE_ITEM_KEY, PersistentDataType.BYTE);
  }

  @EventHandler
  public void on(PlayerQuitEvent e) {
    snares.removeIf(snare -> snare.owner.equals(e.getPlayer().getUniqueId()));
  }

  @ReceiveCancelledEvents
  @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
  public void on(PlayerInteractEvent e) {
    if (e.getAction() != Action.RIGHT_CLICK_BLOCK || e.getClickedBlock() == null) {
      return;
    }

    EquipmentSlot handSlot = e.getHand();
    if (handSlot == null) {
      return;
    }

    Player p = e.getPlayer();
    ItemStack hand = getItemInHand(p, handSlot);
    if (!isSnareItem(hand)) {
      return;
    }

    boolean blockUseDenied = e.useInteractedBlock() == Event.Result.DENY;
    e.setCancelled(true);
    if (!hasActiveAdaptation(p)) {
      return;
    }

    if (blockUseDenied) {
      return;
    }

    Block clicked = e.getClickedBlock();
    Location snareLocation = clicked.getLocation().add(0.5D, 1.0D, 0.5D);
    if (!canInteract(p, snareLocation)) {
      return;
    }

    if (countOwned(p.getUniqueId()) >= Math.max(1, getConfig().maxSnaresPerPlayer)) {
      fx(p.getEyeLocation(), FxPriority.GAMEPLAY).sound(Sound.BLOCK_NOTE_BLOCK_BASS, 0.5F, 0.6F);
      return;
    }
    if (snares.size() >= Math.max(1, getConfig().maxActiveSnares)) {
      return;
    }

    int level = getActiveLevel(p);
    consumeSnareItem(p, handSlot, hand);
    long now = M.ms();
    Snare snare = new Snare(
        p.getUniqueId(),
        snareLocation,
        getCharges(level),
        getRootDurationTicks(level),
        now + (getConfig().snareLifetimeTicks * 50L));
    snares.add(snare);

    fx(snareLocation, FxPriority.GAMEPLAY)
        .dustRing(SNARE_GREEN, getConfig().triggerRadius, 14, 1.0F)
        .particle(Particles.VILLAGER_HAPPY, 6, 0, 0.2D, 0, 0.3D, 0.05D)
        .chord(Sound.BLOCK_TRIPWIRE_ATTACH, 0.7F, 1.1F, Sound.ITEM_ARMOR_EQUIP_LEATHER, 0.5F, 1.3F);
  }

  private void consumeSnareItem(Player p, EquipmentSlot handSlot, ItemStack hand) {
    int remaining = hand.getAmount() - 1;
    ItemStack replacement = remaining <= 0 ? null : hand.clone();
    if (replacement != null) {
      replacement.setAmount(remaining);
    }
    if (handSlot == EquipmentSlot.OFF_HAND) {
      p.getInventory().setItemInOffHand(replacement);
    } else {
      p.getInventory().setItemInMainHand(replacement);
    }
  }

  private ItemStack getItemInHand(Player p, EquipmentSlot handSlot) {
    return handSlot == EquipmentSlot.OFF_HAND
        ? p.getInventory().getItemInOffHand()
        : p.getInventory().getItemInMainHand();
  }

  private int countOwned(UUID owner) {
    int count = 0;
    for (Snare snare : snares) {
      if (snare.owner.equals(owner)) {
        count++;
      }
    }
    return count;
  }

  @Override
  public boolean hasTickDemand() {
    return !snares.isEmpty();
  }

  @Override
  public void onTick() {
    long now = M.ms();
    for (Snare snare : snares) {
      if (snare.expiresAt <= now || snare.charges.get() <= 0) {
        if (snares.remove(snare)) {
          emitSnareBreak(snare);
        }
        continue;
      }
      J.runAt(snare.location, () -> scanSnare(snare, now));
    }
  }

  private void scanSnare(Snare snare, long scheduledAt) {
    World world = snare.location.getWorld();
    if (world == null || !snares.contains(snare) || snare.charges.get() <= 0) {
      return;
    }

    double radius = getConfig().triggerRadius;
    fx(snare.location, FxPriority.AMBIENT).dustRing(SNARE_GREEN, radius, 10, 0.8F);

    int budget = Math.max(1, getConfig().maxTargetsPerScan);
    int scheduled = 0;
    for (Monster monster : PaperCompat.nearbyEntitiesByType(Monster.class, snare.location, radius, radius, radius)) {
      if (scheduled >= budget || snare.charges.get() <= 0) {
        break;
      }
      Long rearm = snare.rearmUntil.get(monster.getUniqueId());
      if (rearm != null && rearm > scheduledAt) {
        continue;
      }
      scheduled++;
      J.runEntity(monster, () -> rootMonster(snare, monster));
    }
  }

  private void rootMonster(Snare snare, Monster monster) {
    if (!monster.isValid() || monster.isDead() || !snares.contains(snare)) {
      return;
    }

    if (isProtectedFriendlyOwned(snare.owner, monster)) {
      return;
    }
    if (!snare.tryConsumeCharge()) {
      return;
    }

    long now = M.ms();
    snare.rearmUntil.put(monster.getUniqueId(), now + (snare.rootDurationTicks * 50L) + getConfig().rearmBufferMillis);
    if (snare.rootDurationTicks > 0) {
      AdaptAttributeService.get().applyTimed(
          monster,
          getName(),
          "root",
          Attributes.MOVEMENT_SPEED,
          rootSpeedScalar(getConfig().rootAmplifier),
          AttributeModifier.Operation.MULTIPLY_SCALAR_1,
          snare.rootDurationTicks);
    }
    monster.setVelocity(new Vector());

    fx(monster.getLocation().add(0, monster.getHeight() * 0.4D, 0), FxPriority.COMBAT)
        .particle(Particles.CRIT_MAGIC, 6, 0, 0.3D, 0, 0.3D, 0.0D)
        .dustBurst(SNARE_GREEN, 6, 0.3D, 1.0F)
        .chord(Sound.BLOCK_TRIPWIRE_CLICK_ON, 0.8F, 0.8F, Sound.ITEM_LEAD_TIED, 0.6F, 0.9F);

    Player owner = Bukkit.getPlayer(snare.owner);
    if (owner != null) {
      J.runEntity(owner, () -> {
        if (owner.isOnline()) {
          addStat(owner, "hunter.snare-line.mobs-snared", 1);
          xp(owner, getConfig().xpPerSnare);
        }
      });
    }
  }

  private void emitSnareBreak(Snare snare) {
    J.runAt(snare.location, () -> fx(snare.location, FxPriority.TRANSITION)
        .particle(Particles.SMOKE, 6, 0, 0.2D, 0, 0.25D, 0.02D)
        .sound(Sound.BLOCK_TRIPWIRE_DETACH, 0.6F, 0.9F));
  }

  @ConfigDescription("Craft a string-and-iron snare; hostile mobs that cross it are rooted briefly.")
  protected static class Config extends AdaptationConfig {
    @art.arcane.adapt.util.config.ConfigDoc(value = "Base snare root duration in ticks for the Hunter Snare Line adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    int rootDurationTicksBase = 30;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Additional snare root duration in ticks gained across levels for the Hunter Snare Line adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double rootDurationTicksFactor = 50;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Base trigger charges per crafted snare for the Hunter Snare Line adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    int chargesBase = 3;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Additional trigger charges gained across levels for the Hunter Snare Line adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double chargesFactor = 5;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Snare trigger radius in blocks for the Hunter Snare Line adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double triggerRadius = 1.6;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Slowness-equivalent amplifier mapped to a movement speed reduction of 15 percent per level, capped at a full stop, while a mob is rooted for the Hunter Snare Line adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    int rootAmplifier = 6;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Milliseconds a snared mob is immune to re-triggering the same snare for the Hunter Snare Line adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    long rearmBufferMillis = 500;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Ticks a placed snare persists before decaying for the Hunter Snare Line adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    long snareLifetimeTicks = 2400;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Maximum concurrent snares one player may keep placed for the Hunter Snare Line adaptation.", impact = "Lower values reduce tracked snare state.")
    int maxSnaresPerPlayer = 4;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Maximum concurrent snares processed by this server for the Hunter Snare Line adaptation.", impact = "Lower values reduce scan scheduling work.")
    int maxActiveSnares = 64;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Maximum mobs scheduled for rooting by one snare per scan for the Hunter Snare Line adaptation.", impact = "Lower values reduce burst entity work in crowds.")
    int maxTargetsPerScan = 8;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Xp granted per mob snared for the Hunter Snare Line adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double xpPerSnare = 6;

    public Config() {
      baseCost = 5;
      costFactor = 0.5;
      initialCost = 6;
    }
  }

  private static final class Snare {
    private final UUID owner;
    private final Location location;
    private final AtomicInteger charges;
    private final int rootDurationTicks;
    private final long expiresAt;
    private final Map<UUID, Long> rearmUntil;

    private Snare(UUID owner, Location location, int charges, int rootDurationTicks, long expiresAt) {
      this.owner = owner;
      this.location = location.clone();
      this.charges = new AtomicInteger(Math.max(1, charges));
      this.rootDurationTicks = rootDurationTicks;
      this.expiresAt = expiresAt;
      this.rearmUntil = new ConcurrentHashMap<>();
    }

    private boolean tryConsumeCharge() {
      int current = charges.get();
      while (current > 0) {
        if (charges.compareAndSet(current, current - 1)) {
          return true;
        }
        current = charges.get();
      }
      return false;
    }
  }
}
