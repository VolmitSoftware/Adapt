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

package art.arcane.adapt.content.adaptation.taming;

import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.world.AdaptStatTracker;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.inventorygui.Element;
import art.arcane.volmlib.util.format.Form;
import art.arcane.adapt.util.common.format.Localizer;
import art.arcane.adapt.util.common.misc.SoundPlayer;
import art.arcane.adapt.util.config.ConfigDescription;
import lombok.NoArgsConstructor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Tameable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;

public class TamingBeastRecall extends SimpleAdaptation<TamingBeastRecall.Config> {
    public TamingBeastRecall() {
        super("tame-beast-recall");
        registerConfiguration(Config.class);
        setDescription(Localizer.dLocalize("taming.beast_recall.description"));
        setDisplayName(Localizer.dLocalize("taming.beast_recall.name"));
        setIcon(Material.LEAD);
        setBaseCost(getConfig().baseCost);
        setMaxLevel(getConfig().maxLevel);
        setInitialCost(getConfig().initialCost);
        setCostFactor(getConfig().costFactor);
        setInterval(2200);
        registerAdvancement(AdaptAdvancement.builder()
                .icon(Material.LEAD)
                .key("challenge_taming_recall_100")
                .title(Localizer.dLocalize("advancement.challenge_taming_recall_100.title"))
                .description(Localizer.dLocalize("advancement.challenge_taming_recall_100.description"))
                .frame(AdaptAdvancementFrame.CHALLENGE)
                .visibility(AdvancementVisibility.PARENT_GRANTED)
                .child(AdaptAdvancement.builder()
                        .icon(Material.ENDER_PEARL)
                        .key("challenge_taming_recall_1k")
                        .title(Localizer.dLocalize("advancement.challenge_taming_recall_1k.title"))
                        .description(Localizer.dLocalize("advancement.challenge_taming_recall_1k.description"))
                        .frame(AdaptAdvancementFrame.CHALLENGE)
                        .visibility(AdvancementVisibility.PARENT_GRANTED)
                        .build())
                .build());
        registerMilestone("challenge_taming_recall_100", "taming.beast-recall.recalls", 100, 300);
        registerMilestone("challenge_taming_recall_1k", "taming.beast-recall.recalls", 1000, 1000);
    }

    @Override
    public void addStats(int level, Element v) {
        v.addLore(C.GREEN + "+ " + Form.f(getSearchRadius(level)) + C.GRAY + " " + Localizer.dLocalize("taming.beast_recall.lore1"));
        v.addLore(C.YELLOW + "* " + Form.duration(getCooldownTicks(level) * 50D, 1) + C.GRAY + " " + Localizer.dLocalize("taming.beast_recall.lore2"));
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void on(PlayerInteractEvent e) {
        if (e.isCancelled() || e.getHand() != EquipmentSlot.HAND) {
            return;
        }

        Action action = e.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        Player p = e.getPlayer();
        if (!hasAdaptation(p) || !p.isSneaking() || p.getInventory().getItemInMainHand().getType() != Material.LEAD || p.hasCooldown(Material.LEAD)) {
            return;
        }

        int level = getLevel(p);
        Tameable tameable = findNearestOwnedTameable(p, getSearchRadius(level));
        if (tameable == null) {
            return;
        }

        Location safe = findSafeRecallLocation(p);
        if (safe == null || !canPVE(p, safe)) {
            return;
        }

        tameable.teleport(safe);
        tameable.setFallDistance(0);
        p.setCooldown(Material.LEAD, getCooldownTicks(level));
        e.setCancelled(true);

        SoundPlayer sp = SoundPlayer.of(p.getWorld());
        sp.play(p.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 0.75f, 1.45f);
        sp.play(safe, Sound.ITEM_LEAD_BREAK, 0.6f, 1.2f);
        xp(p, getConfig().xpOnRecall);
        getPlayer(p).getData().addStat("taming.beast-recall.recalls", 1);
    }

    private Tameable findNearestOwnedTameable(Player p, double radius) {
        double best = Double.MAX_VALUE;
        Tameable out = null;
        double minDistSq = getConfig().minRecallDistanceSquared;
        for (Entity entity : p.getWorld().getNearbyEntities(p.getLocation(), radius, radius, radius)) {
            if (!(entity instanceof Tameable t) || !t.isTamed() || !(t.getOwner() instanceof Player owner) || !owner.getUniqueId().equals(p.getUniqueId())) {
                continue;
            }

            double d = t.getLocation().distanceSquared(p.getLocation());
            if (d <= minDistSq || d >= best) {
                continue;
            }

            out = t;
            best = d;
        }

        return out;
    }

    private Location findSafeRecallLocation(Player p) {
        Location base = p.getLocation();
        int[][] offsets = {
                {0, 0}, {1, 0}, {-1, 0}, {0, 1}, {0, -1},
                {1, 1}, {1, -1}, {-1, 1}, {-1, -1},
                {2, 0}, {-2, 0}, {0, 2}, {0, -2}
        };

        for (int y = 0; y <= 2; y++) {
            for (int[] offset : offsets) {
                Location candidate = base.clone().add(offset[0], y, offset[1]);
                if (isSafeSpot(candidate)) {
                    return candidate;
                }
            }
        }

        return null;
    }

    private boolean isSafeSpot(Location location) {
        Block feet = location.getBlock();
        Block head = location.clone().add(0, 1, 0).getBlock();
        Block below = location.clone().add(0, -1, 0).getBlock();
        return feet.isPassable() && head.isPassable() && below.getType().isSolid();
    }

    private double getSearchRadius(int level) {
        return getConfig().radiusBase + (getLevelPercent(level) * getConfig().radiusFactor);
    }

    private int getCooldownTicks(int level) {
        return Math.max(40, (int) Math.round(getConfig().cooldownTicksBase - (getLevelPercent(level) * getConfig().cooldownTicksFactor)));
    }

    @Override
    public void onTick() {

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
    @ConfigDescription("Sneak-right-click with a lead to recall your nearest tamed companion.")
    protected static class Config {
        @art.arcane.adapt.util.config.ConfigDoc(value = "Keeps this adaptation permanently active once learned.", impact = "True removes the normal learn/unlearn flow and treats it as always learned.")
        boolean permanent = false;
        @art.arcane.adapt.util.config.ConfigDoc(value = "Enables or disables this feature.", impact = "Set to false to disable behavior without uninstalling files.")
        boolean enabled = true;
        @art.arcane.adapt.util.config.ConfigDoc(value = "Base knowledge cost used when learning this adaptation.", impact = "Higher values make each level cost more knowledge.")
        int baseCost = 4;
        @art.arcane.adapt.util.config.ConfigDoc(value = "Maximum level a player can reach for this adaptation.", impact = "Higher values allow more levels; lower values cap progression sooner.")
        int maxLevel = 5;
        @art.arcane.adapt.util.config.ConfigDoc(value = "Knowledge cost required to purchase level 1.", impact = "Higher values make unlocking the first level more expensive.")
        int initialCost = 4;
        @art.arcane.adapt.util.config.ConfigDoc(value = "Scaling factor applied to higher adaptation levels.", impact = "Higher values increase level-to-level cost growth.")
        double costFactor = 0.72;
        @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Radius Base for the Taming Beast Recall adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double radiusBase = 20;
        @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Radius Factor for the Taming Beast Recall adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double radiusFactor = 38;
        @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Min Recall Distance Squared for the Taming Beast Recall adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double minRecallDistanceSquared = 9.0;
        @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Cooldown Ticks Base for the Taming Beast Recall adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double cooldownTicksBase = 420;
        @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Cooldown Ticks Factor for the Taming Beast Recall adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double cooldownTicksFactor = 280;
        @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Xp On Recall for the Taming Beast Recall adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double xpOnRecall = 26;
    }
}
