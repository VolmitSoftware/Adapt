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

import art.arcane.adapt.Adapt;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.world.AdaptStatTracker;
import art.arcane.adapt.content.item.BoundEnderPearl;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.inventorygui.Element;
import art.arcane.volmlib.util.format.Form;
import art.arcane.adapt.util.common.scheduling.J;
import art.arcane.adapt.util.common.format.Localizer;
import art.arcane.adapt.util.common.misc.SoundPlayer;
import art.arcane.adapt.util.config.ConfigDescription;
import lombok.NoArgsConstructor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Animals;
import org.bukkit.entity.Ambient;
import org.bukkit.entity.EnderPearl;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.entity.Slime;
import org.bukkit.entity.Villager;
import org.bukkit.entity.WaterMob;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.metadata.MetadataValue;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import art.arcane.adapt.util.common.inventorygui.Window;
import art.arcane.adapt.util.common.nbt.Tag;

public class RiftEnderTaglock extends SimpleAdaptation<RiftEnderTaglock.Config> {
    private static final String PROJECTILE_TARGET_META = "adapt-rift-taglock-target";
    private final NamespacedKey targetKey;
    private final Map<UUID, Long> suppressPearlTeleportUntil = new ConcurrentHashMap<>();

    public RiftEnderTaglock() {
        super("rift-ender-taglock");
        registerConfiguration(Config.class);
        setDescription(Localizer.dLocalize("rift.ender_taglock.description"));
        setDisplayName(Localizer.dLocalize("rift.ender_taglock.name"));
        setIcon(Material.ENDER_PEARL);
        setBaseCost(getConfig().baseCost);
        setMaxLevel(getConfig().maxLevel);
        setInitialCost(getConfig().initialCost);
        setCostFactor(getConfig().costFactor);
        setInterval(1200);
        targetKey = new NamespacedKey(Adapt.instance, "rift_taglock_target_uuid");
        registerAdvancement(AdaptAdvancement.builder()
                .icon(Material.ENDER_PEARL)
                .key("challenge_rift_taglock_100")
                .title(Localizer.dLocalize("advancement.challenge_rift_taglock_100.title"))
                .description(Localizer.dLocalize("advancement.challenge_rift_taglock_100.description"))
                .frame(AdaptAdvancementFrame.CHALLENGE)
                .visibility(AdvancementVisibility.PARENT_GRANTED)
                .child(AdaptAdvancement.builder()
                        .icon(Material.ENDER_EYE)
                        .key("challenge_rift_taglock_500")
                        .title(Localizer.dLocalize("advancement.challenge_rift_taglock_500.title"))
                        .description(Localizer.dLocalize("advancement.challenge_rift_taglock_500.description"))
                        .frame(AdaptAdvancementFrame.CHALLENGE)
                        .visibility(AdvancementVisibility.PARENT_GRANTED)
                        .build())
                .build());
        registerMilestone("challenge_rift_taglock_100", "rift.ender-taglock.entities-tagged", 100, 400);
        registerMilestone("challenge_rift_taglock_500", "rift.ender-taglock.taglocked-teleports", 500, 1000);
    }

    @Override
    public void addStats(int level, Element v) {
        v.addLore(C.GREEN + "+ " + Localizer.dLocalize("rift.ender_taglock.lore1"));
        if (level >= 2) {
            v.addLore(C.GREEN + "+ " + Localizer.dLocalize("rift.ender_taglock.lore2"));
        }
        if (level >= 3) {
            v.addLore(C.GREEN + "+ " + Localizer.dLocalize("rift.ender_taglock.lore3"));
        }
        v.addLore(C.YELLOW + "* " + Form.duration(getThrowCooldownTicks(level) * 50D, 1) + C.GRAY + " " + Localizer.dLocalize("rift.ender_taglock.lore4"));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void on(PlayerQuitEvent e) {
        suppressPearlTeleportUntil.remove(e.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void on(EntityDamageByEntityEvent e) {
        if (!(e.getDamager() instanceof Player p) || !(e.getEntity() instanceof LivingEntity target) || !hasAdaptation(p)) {
            return;
        }

        if (!p.isSneaking()) {
            return;
        }

        ItemStack hand = p.getInventory().getItemInMainHand();
        if (!isItem(hand) || hand.getType() != Material.ENDER_PEARL || BoundEnderPearl.isBindableItem(hand)) {
            return;
        }

        int level = getLevel(p);
        if (!isTaggable(target, level)) {
            return;
        }

        if (target instanceof Player victim) {
            if (!canPVP(p, victim.getLocation())) {
                return;
            }
        } else if (!canPVE(p, target.getLocation())) {
            return;
        }

        e.setCancelled(true);
        tagIntoPearl(p, hand, target);
        SoundPlayer.of(p).play(p.getLocation(), Sound.BLOCK_RESPAWN_ANCHOR_CHARGE, 0.55f, 1.4f);
        if (areParticlesEnabled()) {
            p.getWorld().spawnParticle(Particle.PORTAL, target.getLocation().add(0, 1, 0), 14, 0.25, 0.4, 0.25, 0.04);
        }
        getPlayer(p).getData().addStat("rift.ender-taglock.entities-tagged", 1);
        xp(p, getConfig().xpOnTag);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void on(PlayerInteractEvent e) {
        EquipmentSlot slot = e.getHand();
        if (slot == null) {
            return;
        }

        Action action = e.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        Player p = e.getPlayer();
        if (!hasAdaptation(p)) {
            return;
        }

        ItemStack hand = slot == EquipmentSlot.HAND
                ? p.getInventory().getItemInMainHand()
                : p.getInventory().getItemInOffHand();

        UUID target = getTaggedTarget(hand);
        if (target == null) {
            return;
        }

        e.setCancelled(true);
        if (p.hasCooldown(Material.ENDER_PEARL)) {
            SoundPlayer.of(p).play(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_DIDGERIDOO, 0.55f, 0.6f);
            return;
        }

        decrementTaggedPearl(p, slot, hand);

        EnderPearl pearl = p.launchProjectile(EnderPearl.class);
        pearl.setMetadata(PROJECTILE_TARGET_META, new FixedMetadataValue(Adapt.instance, target.toString()));
        suppressPearlTeleportUntil.put(p.getUniqueId(), System.currentTimeMillis() + getSuppressPearlTeleportWindowMillis());
        p.setCooldown(Material.ENDER_PEARL, getThrowCooldownTicks(getLevel(p)));
        SoundPlayer.of(p).play(p.getLocation(), Sound.ENTITY_ENDER_EYE_LAUNCH, 0.65f, 1.25f);
        xp(p, getConfig().xpOnThrow);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void on(PlayerTeleportEvent e) {
        if (e.getCause() != PlayerTeleportEvent.TeleportCause.ENDER_PEARL) {
            return;
        }

        UUID id = e.getPlayer().getUniqueId();
        long until = suppressPearlTeleportUntil.getOrDefault(id, 0L);
        if (until <= System.currentTimeMillis()) {
            suppressPearlTeleportUntil.remove(id);
            return;
        }

        e.setCancelled(true);
        e.getPlayer().setFallDistance(0f);
        suppressPearlTeleportUntil.remove(id);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void on(ProjectileHitEvent e) {
        if (!(e.getEntity() instanceof EnderPearl pearl) || !(pearl.getShooter() instanceof Player p) || !hasAdaptation(p)) {
            return;
        }

        String raw = getMetadataString(pearl, PROJECTILE_TARGET_META);
        if (raw == null) {
            return;
        }

        UUID targetId;
        try {
            targetId = UUID.fromString(raw);
        } catch (IllegalArgumentException ex) {
            return;
        }

        Entity entity = Bukkit.getEntity(targetId);
        if (!(entity instanceof LivingEntity target) || !target.isValid() || target.isDead()) {
            return;
        }

        Location destination = resolveDestination(e);
        if (target instanceof Player victim) {
            if (!canPVP(p, victim.getLocation()) || !canPVP(p, destination)) {
                return;
            }
        } else if (!canPVE(p, target.getLocation()) || !canPVE(p, destination)) {
            return;
        }

        destination.getChunk().load();
        target.teleport(destination);
        if (areParticlesEnabled()) {
            target.getWorld().spawnParticle(Particle.REVERSE_PORTAL, destination.clone().add(0, 0.75, 0), 18, 0.3, 0.35, 0.3, 0.05);
        }
        SoundPlayer.of(target.getWorld()).play(destination, Sound.ENTITY_ENDERMAN_TELEPORT, 0.75f, 1.35f);
        SoundPlayer.of(target.getWorld()).play(destination, Sound.ENTITY_ELDER_GUARDIAN_CURSE, 0.5f, 1.9f);
        if (target instanceof Player victim && areSoundsEnabled()) {
            victim.playSound(victim.getLocation(), Sound.ENTITY_ELDER_GUARDIAN_CURSE, 0.75f, 1.9f);
        }
        getPlayer(p).getData().addStat("rift.ender-taglock.taglocked-teleports", 1);
        xp(p, getConfig().xpOnTeleport);
        J.s(() -> suppressPearlTeleportUntil.remove(p.getUniqueId()), 2);
    }

    private Location resolveDestination(ProjectileHitEvent e) {
        Location base = e.getEntity().getLocation().clone();
        if (e.getHitBlock() != null && e.getHitBlockFace() != null) {
            Vector n = e.getHitBlockFace().getDirection().clone().normalize();
            base = e.getHitBlock().getLocation().add(0.5, 0.5, 0.5).add(n.multiply(0.6));
        }

        return base.add(0, 0.05, 0);
    }

    private void decrementTaggedPearl(Player p, EquipmentSlot slot, ItemStack stack) {
        if (!isItem(stack)) {
            return;
        }

        if (stack.getAmount() <= 1) {
            if (slot == EquipmentSlot.HAND) {
                p.getInventory().setItemInMainHand(new ItemStack(Material.AIR));
            } else {
                p.getInventory().setItemInOffHand(new ItemStack(Material.AIR));
            }
            return;
        }

        stack.setAmount(stack.getAmount() - 1);
        if (slot == EquipmentSlot.HAND) {
            p.getInventory().setItemInMainHand(stack);
        } else {
            p.getInventory().setItemInOffHand(stack);
        }
    }

    private void tagIntoPearl(Player p, ItemStack hand, LivingEntity target) {
        ItemStack tagged = makeTaggedPearl(target);

        if (hand.getAmount() <= 1) {
            p.getInventory().setItemInMainHand(tagged);
            return;
        }

        hand.setAmount(hand.getAmount() - 1);
        p.getInventory().addItem(tagged).values().forEach(i -> p.getWorld().dropItemNaturally(p.getLocation(), i));
    }

    private ItemStack makeTaggedPearl(LivingEntity target) {
        ItemStack item = new ItemStack(Material.ENDER_PEARL, 1);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }

        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(targetKey, PersistentDataType.STRING, target.getUniqueId().toString());

        meta.setDisplayName(C.LIGHT_PURPLE + "Taglocked Ender Pearl");
        List<String> lore = new ArrayList<>();
        lore.add(C.GRAY + "Target: " + C.WHITE + getTargetName(target));
        lore.add(C.DARK_GRAY + "Right click to throw");
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private UUID getTaggedTarget(ItemStack item) {
        if (!isItem(item) || item.getType() != Material.ENDER_PEARL || item.getItemMeta() == null) {
            return null;
        }

        String raw = item.getItemMeta().getPersistentDataContainer().get(targetKey, PersistentDataType.STRING);
        if (raw == null || raw.isEmpty()) {
            return null;
        }

        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private String getTargetName(LivingEntity target) {
        if (target instanceof Player player) {
            return player.getName();
        }

        if (target.getCustomName() != null && !target.getCustomName().isEmpty()) {
            return target.getCustomName();
        }

        return Form.capitalizeWords(target.getType().name().toLowerCase().replaceAll("\\Q_\\E", " "));
    }

    private boolean isTaggable(LivingEntity target, int level) {
        if (target instanceof Player) {
            return level >= 3;
        }

        if (level >= 3) {
            return true;
        }

        if (level == 2) {
            return isLevel1Taggable(target) || target instanceof Villager || isLargeTarget(target);
        }

        return isLevel1Taggable(target);
    }

    private boolean isLevel1Taggable(LivingEntity target) {
        if (!(target instanceof Mob)) {
            return false;
        }

        if (target instanceof Villager) {
            return false;
        }

        if (isLargeTarget(target)) {
            return false;
        }

        return target instanceof Animals
                || target instanceof Monster
                || target instanceof WaterMob
                || target instanceof Ambient
                || target instanceof Slime;
    }

    private boolean isLargeTarget(LivingEntity target) {
        BoundingBox box = target.getBoundingBox();
        double width = Math.max(box.getWidthX(), box.getWidthZ());
        double height = box.getHeight();
        return width >= getConfig().largeWidthThreshold || height >= getConfig().largeHeightThreshold;
    }

    private String getMetadataString(Entity entity, String key) {
        for (MetadataValue value : entity.getMetadata(key)) {
            if (value.getOwningPlugin() == Adapt.instance) {
                return value.asString();
            }
        }

        return null;
    }

    private int getThrowCooldownTicks(int level) {
        return Math.max(4, (int) Math.round(getConfig().throwCooldownTicksBase - (getLevelPercent(level) * getConfig().throwCooldownTicksFactor)));
    }

    private long getSuppressPearlTeleportWindowMillis() {
        return Math.max(1000L, getConfig().suppressPearlTeleportWindowMillis);
    }

    @Override
    public void onTick() {
        long now = System.currentTimeMillis();
        suppressPearlTeleportUntil.entrySet().removeIf(i -> i.getValue() <= now);
    }

    @Override
    public boolean isEnabled() {
        return getConfig().enabled;
    }

    @Override
    public boolean isPermanent() {
        return getConfig().permanent;
    }

    @NoArgsConstructor
    @ConfigDescription("Tag entities into ender pearls and throw those pearls to reposition the tagged target.")
    protected static class Config {
        @art.arcane.adapt.util.config.ConfigDoc(value = "Keeps this adaptation permanently active once learned.", impact = "True removes the normal learn/unlearn flow and treats it as always learned.")
        boolean permanent = false;
        @art.arcane.adapt.util.config.ConfigDoc(value = "Enables or disables this feature.", impact = "Set to false to disable behavior without uninstalling files.")
        boolean enabled = true;
        @art.arcane.adapt.util.config.ConfigDoc(value = "Base knowledge cost used when learning this adaptation.", impact = "Higher values make each level cost more knowledge.")
        int baseCost = 7;
        @art.arcane.adapt.util.config.ConfigDoc(value = "Maximum level a player can reach for this adaptation.", impact = "Higher values allow more levels; lower values cap progression sooner.")
        int maxLevel = 3;
        @art.arcane.adapt.util.config.ConfigDoc(value = "Knowledge cost required to purchase level 1.", impact = "Higher values make unlocking the first level more expensive.")
        int initialCost = 7;
        @art.arcane.adapt.util.config.ConfigDoc(value = "Scaling factor applied to higher adaptation levels.", impact = "Higher values increase level-to-level cost growth.")
        double costFactor = 0.95;
        @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Throw Cooldown Ticks Base for the Rift Ender Taglock adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double throwCooldownTicksBase = 30;
        @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Throw Cooldown Ticks Factor for the Rift Ender Taglock adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double throwCooldownTicksFactor = 14;
        @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Suppress Pearl Teleport Window Millis for the Rift Ender Taglock adaptation.", impact = "This should be long enough to catch the teleport event from a taglocked throw.")
        long suppressPearlTeleportWindowMillis = 180000L;
        @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Large Width Threshold for the Rift Ender Taglock adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double largeWidthThreshold = 1.3;
        @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Large Height Threshold for the Rift Ender Taglock adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double largeHeightThreshold = 2.35;
        @art.arcane.adapt.util.config.ConfigDoc(value = "Controls XP On Tag for the Rift Ender Taglock adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double xpOnTag = 8;
        @art.arcane.adapt.util.config.ConfigDoc(value = "Controls XP On Throw for the Rift Ender Taglock adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double xpOnThrow = 5;
        @art.arcane.adapt.util.config.ConfigDoc(value = "Controls XP On Teleport for the Rift Ender Taglock adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double xpOnTeleport = 14;
    }
}
