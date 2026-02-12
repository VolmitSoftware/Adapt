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

package com.volmit.adapt.content.adaptation.herbalism;

import com.volmit.adapt.Adapt;
import com.volmit.adapt.api.adaptation.SimpleAdaptation;
import com.volmit.adapt.api.advancement.AdaptAdvancement;
import com.volmit.adapt.api.advancement.AdaptAdvancementFrame;
import com.volmit.adapt.api.advancement.AdvancementVisibility;
import com.volmit.adapt.api.world.AdaptStatTracker;
import com.volmit.adapt.util.C;
import com.volmit.adapt.util.Element;
import com.volmit.adapt.util.Form;
import com.volmit.adapt.util.Localizer;
import com.volmit.adapt.util.SoundPlayer;
import com.volmit.adapt.util.config.ConfigDescription;
import lombok.NoArgsConstructor;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public class HerbalismSporeBloom extends SimpleAdaptation<HerbalismSporeBloom.Config> {
    private final Map<UUID, Long> cooldowns = new HashMap<>();

    public HerbalismSporeBloom() {
        super("herbalism-spore-bloom");
        registerConfiguration(Config.class);
        setDescription(Localizer.dLocalize("herbalism.spore_bloom.description"));
        setDisplayName(Localizer.dLocalize("herbalism.spore_bloom.name"));
        setIcon(Material.RED_MUSHROOM_BLOCK);
        setBaseCost(getConfig().baseCost);
        setMaxLevel(getConfig().maxLevel);
        setInitialCost(getConfig().initialCost);
        setCostFactor(getConfig().costFactor);
        setInterval(2100);
        registerAdvancement(AdaptAdvancement.builder()
                .icon(Material.BROWN_MUSHROOM)
                .key("challenge_herbalism_spore_500")
                .title(Localizer.dLocalize("advancement.challenge_herbalism_spore_500.title"))
                .description(Localizer.dLocalize("advancement.challenge_herbalism_spore_500.description"))
                .frame(AdaptAdvancementFrame.CHALLENGE)
                .visibility(AdvancementVisibility.PARENT_GRANTED)
                .build());
        registerStatTracker(AdaptStatTracker.builder()
                .advancement("challenge_herbalism_spore_500")
                .goal(500)
                .stat("herbalism.spore-bloom.blocks-spread")
                .reward(300)
                .build());
    }

    @Override
    public void addStats(int level, Element v) {
        v.addLore(C.GREEN + "+ " + getBloomAttempts(level) + C.GRAY + " " + Localizer.dLocalize("herbalism.spore_bloom.lore1"));
        v.addLore(C.GREEN + "+ " + Form.f(getBloomRadius(level)) + C.GRAY + " " + Localizer.dLocalize("herbalism.spore_bloom.lore2"));
        v.addLore(C.YELLOW + "* " + Form.duration(getCooldownMillis(level), 1) + C.GRAY + " " + Localizer.dLocalize("herbalism.spore_bloom.lore3"));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void on(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND || e.getAction() != Action.RIGHT_CLICK_BLOCK || e.getClickedBlock() == null) {
            return;
        }

        if (!hasAdaptation(e.getPlayer()) || !e.getPlayer().isSneaking()) {
            return;
        }

        Block clicked = e.getClickedBlock();
        if (!isBloomFloor(clicked.getType())) {
            return;
        }

        ItemStack hand = e.getPlayer().getInventory().getItemInMainHand();
        if (!isSporeItem(hand)) {
            return;
        }

        int level = getLevel(e.getPlayer());
        long now = System.currentTimeMillis();
        long ready = cooldowns.getOrDefault(e.getPlayer().getUniqueId(), 0L);
        if (now < ready) {
            return;
        }

        if (e.getPlayer().getFoodLevel() < getFoodCost(level)) {
            return;
        }

        if (!consumeOne(hand)) {
            return;
        }

        e.setCancelled(true);
        e.getPlayer().setFoodLevel(Math.max(0, e.getPlayer().getFoodLevel() - getFoodCost(level)));
        cooldowns.put(e.getPlayer().getUniqueId(), now + getCooldownMillis(level));

        clicked.getWorld().spawnParticle(Particle.SPORE_BLOSSOM_AIR, clicked.getLocation().add(0.5, 1.0, 0.5), 30, 0.35, 0.15, 0.35, 0.01);
        SoundPlayer.of(clicked.getWorld()).play(clicked.getLocation().add(0.5, 0.5, 0.5), Sound.ENTITY_ENDERMAN_AMBIENT, 0.45f, 0.55f);
        startBloom(e.getPlayer(), clicked, hand.getType(), level);
    }

    private void startBloom(org.bukkit.entity.Player player, Block center, Material catalyst, int level) {
        List<Block> path = buildSpiderPath(center, getBloomRadius(level), getSpokes(level), getBloomAttempts(level));
        if (path.isEmpty()) {
            return;
        }

        new BukkitRunnable() {
            int cursor = 0;
            int totalChanged = 0;

            @Override
            public void run() {
                if (!player.isOnline() || center.getWorld() == null) {
                    cancel();
                    return;
                }

                int pulseChanged = 0;
                int batch = getBlocksPerPulse(level);
                for (int i = 0; i < batch && cursor < path.size(); i++) {
                    pulseChanged += spreadAt(path.get(cursor++), catalyst);
                }

                if (pulseChanged > 0) {
                    totalChanged += pulseChanged;
                    center.getWorld().spawnParticle(Particle.SPORE_BLOSSOM_AIR, center.getLocation().add(0.5, 1.0, 0.5), 8, 0.55, 0.2, 0.55, 0.02);
                    center.getWorld().spawnParticle(Particle.CRIMSON_SPORE, center.getLocation().add(0.5, 1.0, 0.5), 8, 0.55, 0.2, 0.55, 0.01);
                    SoundPlayer sp = SoundPlayer.of(center.getWorld());
                    sp.play(center.getLocation().add(0.5, 0.5, 0.5), Sound.BLOCK_FUNGUS_PLACE, 0.45f, 0.75f);
                    sp.play(center.getLocation().add(0.5, 0.5, 0.5), Sound.ENTITY_ENDERMAN_AMBIENT, 0.22f, 0.45f + ThreadLocalRandom.current().nextFloat() * 0.45f);
                }

                if (cursor >= path.size()) {
                    if (totalChanged > 0) {
                        getPlayer(player).getData().addStat("herbalism.spore-bloom.blocks-spread", totalChanged);
                        xp(player, totalChanged * getConfig().xpPerMushroomPlaced);
                    }
                    cancel();
                }
            }
        }.runTaskTimer(Adapt.instance, 0L, getSpreadIntervalTicks(level));
    }

    private List<Block> buildSpiderPath(Block center, double radius, int spokes, int max) {
        int r = Math.max(1, (int) Math.round(radius));
        List<Block> out = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        out.add(center);
        seen.add(key(center));

        ThreadLocalRandom random = ThreadLocalRandom.current();
        for (int arm = 0; arm < spokes; arm++) {
            double angle = ((Math.PI * 2D) * arm / Math.max(1, spokes)) + (random.nextDouble() * 0.26);
            double cos = Math.cos(angle);
            double sin = Math.sin(angle);

            for (int step = 1; step <= r; step++) {
                int dx = (int) Math.round(cos * step);
                int dz = (int) Math.round(sin * step);
                Block block = center.getRelative(dx, 0, dz);
                if (seen.add(key(block))) {
                    out.add(block);
                }

                if (random.nextDouble() <= getConfig().branchChance) {
                    Block branch = block.getRelative(random.nextInt(-1, 2), 0, random.nextInt(-1, 2));
                    if (seen.add(key(branch))) {
                        out.add(branch);
                    }
                }

                if (out.size() >= max) {
                    return out;
                }
            }
        }

        return out;
    }

    private int spreadAt(Block floor, Material catalyst) {
        int changed = 0;
        Block above = floor.getRelative(0, 1, 0);

        if (getConfig().convertWoodToHyphae && isWoodLike(floor.getType())) {
            Material hyphae = getHyphaeForWood(floor.getType(), catalyst);
            if (hyphae != null && floor.getType() != hyphae) {
                floor.setType(hyphae, true);
                changed++;
            }
        }

        if (above.getType().isAir() || isReplaceablePlant(above.getType())) {
            Material replacement = getRandomMushroom(catalyst);
            if (replacement != null && above.getType() != replacement) {
                above.setType(replacement, true);
                changed++;
            }
        }

        return changed;
    }

    private Material getHyphaeForWood(Material floorType, Material catalyst) {
        String name = floorType.name();
        if (name.contains("WARPED")) {
            return Material.WARPED_HYPHAE;
        }

        if (name.contains("CRIMSON") || name.contains("NETHER")) {
            return Material.CRIMSON_HYPHAE;
        }

        return catalyst == Material.BROWN_MUSHROOM ? Material.WARPED_HYPHAE : Material.CRIMSON_HYPHAE;
    }

    private Material getRandomMushroom(Material catalyst) {
        List<Material> choices = new ArrayList<>();
        for (String key : getConfig().mushroomChoices) {
            Material m = Material.matchMaterial(key);
            if (m != null) {
                choices.add(m);
            }
        }

        if (choices.isEmpty()) {
            return catalyst == Material.BROWN_MUSHROOM ? Material.BROWN_MUSHROOM : Material.RED_MUSHROOM;
        }

        return choices.get(ThreadLocalRandom.current().nextInt(choices.size()));
    }

    private boolean isBloomFloor(Material type) {
        return type == Material.MYCELIUM || type == Material.PODZOL;
    }

    private boolean isSporeItem(ItemStack hand) {
        return isItem(hand) && (hand.getType() == Material.RED_MUSHROOM || hand.getType() == Material.BROWN_MUSHROOM);
    }

    private boolean consumeOne(ItemStack hand) {
        if (hand.getAmount() <= 0) {
            return false;
        }

        hand.setAmount(hand.getAmount() - 1);
        return true;
    }

    private boolean isWoodLike(Material type) {
        String n = type.name();
        return n.endsWith("_LOG") || n.endsWith("_WOOD") || n.endsWith("_STEM") || n.endsWith("_HYPHAE") || n.endsWith("_PLANKS");
    }

    private boolean isReplaceablePlant(Material type) {
        if (type == Material.RED_MUSHROOM || type == Material.BROWN_MUSHROOM || type == Material.CRIMSON_FUNGUS || type == Material.WARPED_FUNGUS) {
            return true;
        }

        String n = type.name();
        return n.endsWith("_FLOWER")
                || n.endsWith("_TULIP")
                || n.endsWith("_SAPLING")
                || n.endsWith("_SEEDS")
                || n.endsWith("_ROOTS")
                || n.endsWith("_CROP")
                || n.equals("TALL_GRASS")
                || n.equals("GRASS")
                || n.equals("FERN")
                || n.equals("LARGE_FERN")
                || n.equals("WHEAT")
                || n.equals("CARROTS")
                || n.equals("POTATOES")
                || n.equals("BEETROOTS")
                || n.equals("NETHER_WART")
                || n.equals("TORCHFLOWER_CROP")
                || n.equals("PITCHER_CROP");
    }

    private String key(Block block) {
        return block.getX() + ":" + block.getY() + ":" + block.getZ();
    }

    private int getBloomAttempts(int level) {
        return Math.max(1, (int) Math.round(getConfig().bloomAttemptsBase + (getLevelPercent(level) * getConfig().bloomAttemptsFactor)));
    }

    private double getBloomRadius(int level) {
        return getConfig().bloomRadiusBase + (getLevelPercent(level) * getConfig().bloomRadiusFactor);
    }

    private int getSpokes(int level) {
        return Math.max(4, (int) Math.round(getConfig().spokesBase + (getLevelPercent(level) * getConfig().spokesFactor)));
    }

    private int getBlocksPerPulse(int level) {
        return Math.max(1, (int) Math.round(getConfig().blocksPerPulseBase + (getLevelPercent(level) * getConfig().blocksPerPulseFactor)));
    }

    private int getSpreadIntervalTicks(int level) {
        return Math.max(1, (int) Math.round(getConfig().spreadIntervalTicksBase - (getLevelPercent(level) * getConfig().spreadIntervalTicksFactor)));
    }

    private int getFoodCost(int level) {
        return Math.max(1, (int) Math.round(getConfig().foodCostBase - (getLevelPercent(level) * getConfig().foodCostFactor)));
    }

    private long getCooldownMillis(int level) {
        return Math.max(250L, (long) Math.round(getConfig().cooldownMillisBase - (getLevelPercent(level) * getConfig().cooldownMillisFactor)));
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
    @ConfigDescription("Sneak-right-click mycelium with mushrooms to spread an outward spore-web that mutates nearby growth.")
    protected static class Config {
        @com.volmit.adapt.util.config.ConfigDoc(value = "Keeps this adaptation permanently active once learned.", impact = "True removes the normal learn/unlearn flow and treats it as always learned.")
        boolean permanent = false;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Enables or disables this feature.", impact = "Set to false to disable behavior without uninstalling files.")
        boolean enabled = true;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Convert Wood To Hyphae for the Herbalism Spore Bloom adaptation.", impact = "True enables this behavior and false disables it.")
        boolean convertWoodToHyphae = true;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Branch Chance for the Herbalism Spore Bloom adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double branchChance = 0.22;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Mushroom Choices for the Herbalism Spore Bloom adaptation.", impact = "Changing this alters the identifier or text used by the feature.")
        String[] mushroomChoices = {"RED_MUSHROOM", "BROWN_MUSHROOM", "CRIMSON_FUNGUS", "WARPED_FUNGUS"};
        @com.volmit.adapt.util.config.ConfigDoc(value = "Base knowledge cost used when learning this adaptation.", impact = "Higher values make each level cost more knowledge.")
        int baseCost = 4;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Maximum level a player can reach for this adaptation.", impact = "Higher values allow more levels; lower values cap progression sooner.")
        int maxLevel = 5;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Knowledge cost required to purchase level 1.", impact = "Higher values make unlocking the first level more expensive.")
        int initialCost = 4;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Scaling factor applied to higher adaptation levels.", impact = "Higher values increase level-to-level cost growth.")
        double costFactor = 0.7;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Bloom Attempts Base for the Herbalism Spore Bloom adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double bloomAttemptsBase = 26;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Bloom Attempts Factor for the Herbalism Spore Bloom adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double bloomAttemptsFactor = 58;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Bloom Radius Base for the Herbalism Spore Bloom adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double bloomRadiusBase = 5;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Bloom Radius Factor for the Herbalism Spore Bloom adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double bloomRadiusFactor = 10;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Spokes Base for the Herbalism Spore Bloom adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double spokesBase = 6;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Spokes Factor for the Herbalism Spore Bloom adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double spokesFactor = 7;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Blocks Per Pulse Base for the Herbalism Spore Bloom adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double blocksPerPulseBase = 2;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Blocks Per Pulse Factor for the Herbalism Spore Bloom adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double blocksPerPulseFactor = 4;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Spread Interval Ticks Base for the Herbalism Spore Bloom adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double spreadIntervalTicksBase = 3;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Spread Interval Ticks Factor for the Herbalism Spore Bloom adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double spreadIntervalTicksFactor = 1.6;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Food Cost Base for the Herbalism Spore Bloom adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double foodCostBase = 2;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Food Cost Factor for the Herbalism Spore Bloom adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double foodCostFactor = 1.2;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Cooldown Millis Base for the Herbalism Spore Bloom adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double cooldownMillisBase = 1700;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Cooldown Millis Factor for the Herbalism Spore Bloom adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double cooldownMillisFactor = 1100;
        @com.volmit.adapt.util.config.ConfigDoc(value = "Controls Xp Per Mushroom Placed for the Herbalism Spore Bloom adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double xpPerMushroomPlaced = 1.4;
    }
}
