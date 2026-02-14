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

package art.arcane.adapt.content.adaptation.herbalism;

import art.arcane.adapt.Adapt;
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
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.Comparator;
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
        registerMilestone("challenge_herbalism_spore_500", "herbalism.spore-bloom.blocks-spread", 500, 300);
    }

    @Override
    public void addStats(int level, Element v) {
        v.addLore(C.GREEN + "+ " + getBloomAttempts(level) + C.GRAY + " " + Localizer.dLocalize("herbalism.spore_bloom.lore1"));
        v.addLore(C.GREEN + "+ " + Form.f(getBloomRadius(level)) + C.GRAY + " " + Localizer.dLocalize("herbalism.spore_bloom.lore2"));
        v.addLore(C.YELLOW + "* " + Form.duration(getCooldownMillis(level), 1) + C.GRAY + " " + Localizer.dLocalize("herbalism.spore_bloom.lore3"));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void on(BlockPlaceEvent e) {
        if (e.getHand() != EquipmentSlot.HAND) {
            return;
        }

        if (!hasAdaptation(e.getPlayer()) || !e.getPlayer().isSneaking()) {
            return;
        }

        if (!isSporeItem(e.getItemInHand())) {
            return;
        }

        Block floor = e.getBlockPlaced().getRelative(0, -1, 0);
        if (!isBloomFloor(floor.getType())) {
            return;
        }

        // Place-trigger activation: sneak-place mushroom on valid floor to bloom.
        e.setCancelled(true);
        attemptBloom(e.getPlayer(), floor, e.getItemInHand().getType());
    }

    private void startBloom(org.bukkit.entity.Player player, Block center, Material catalyst, Material spreadSurface, int level) {
        List<Block> path = buildSpiderPath(center, getBloomRadius(level), getSpokes(level), getBloomAttempts(level), getGuaranteedReach(level));
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
                    pulseChanged += spreadAt(path.get(cursor++), catalyst, spreadSurface);
                }

                if (pulseChanged > 0) {
                    totalChanged += pulseChanged;
                    if (areParticlesEnabled()) {
                        center.getWorld().spawnParticle(Particle.SPORE_BLOSSOM_AIR, center.getLocation().add(0.5, 1.0, 0.5), 8, 0.55, 0.2, 0.55, 0.02);
                    }
                    if (areParticlesEnabled()) {
                        center.getWorld().spawnParticle(Particle.CRIMSON_SPORE, center.getLocation().add(0.5, 1.0, 0.5), 8, 0.55, 0.2, 0.55, 0.01);
                    }
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

    private List<Block> buildSpiderPath(Block center, double radius, int spokes, int max, int guaranteedReach) {
        int r = Math.max(1, (int) Math.ceil(radius));
        int sectors = Math.max(8, Math.min(48, Math.max(1, spokes) * 3));
        double maxDistance = radius + 0.35D;
        double maxDistanceSq = maxDistance * maxDistance;
        List<Block> out = new ArrayList<>(Math.max(8, max));
        Set<String> seen = new HashSet<>();
        out.add(center);
        seen.add(key(center));

        if (out.size() >= max) {
            return out;
        }

        ThreadLocalRandom random = ThreadLocalRandom.current();
        double offset = random.nextDouble(Math.PI * 2D);

        int forcedReach = Math.max(0, Math.min(r, guaranteedReach));
        for (int step = 1; step <= forcedReach; step++) {
            addRingSamples(center, out, seen, step, sectors, offset, maxDistanceSq, max);
            if (out.size() >= max) {
                return out;
            }
        }

        for (int step = 1; step <= r; step++) {
            addRingSamples(center, out, seen, step, sectors, offset, maxDistanceSq, max);
            if (out.size() >= max) {
                return out;
            }

            // Offset pass fills sector rounding gaps so rings look uniform.
            addRingSamples(center, out, seen, step, sectors, offset + (Math.PI / sectors), maxDistanceSq, max);
            if (out.size() >= max) {
                return out;
            }
        }

        fillRemainingFromCircle(center, out, seen, r, offset, maxDistanceSq, max);
        return out;
    }

    private void addRingSamples(Block center, List<Block> out, Set<String> seen, int step, int sectors, double offset, double maxDistanceSq, int max) {
        for (int i = 0; i < sectors && out.size() < max; i++) {
            double angle = offset + ((Math.PI * 2D) * i / sectors);
            int dx = (int) Math.round(Math.cos(angle) * step);
            int dz = (int) Math.round(Math.sin(angle) * step);
            if (dx == 0 && dz == 0) {
                continue;
            }

            if ((dx * dx) + (dz * dz) > maxDistanceSq) {
                continue;
            }

            Block block = center.getRelative(dx, 0, dz);
            if (seen.add(key(block))) {
                out.add(block);
            }
        }
    }

    private void fillRemainingFromCircle(Block center, List<Block> out, Set<String> seen, int r, double offset, double maxDistanceSq, int max) {
        List<int[]> candidates = new ArrayList<>();
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                if (dx == 0 && dz == 0) {
                    continue;
                }

                if ((dx * dx) + (dz * dz) > maxDistanceSq) {
                    continue;
                }

                candidates.add(new int[]{dx, dz});
            }
        }

        candidates.sort(Comparator.<int[]>comparingInt(v -> (v[0] * v[0]) + (v[1] * v[1]))
                .thenComparingDouble(v -> normalizeAngle(Math.atan2(v[1], v[0]) - offset)));

        for (int[] c : candidates) {
            if (out.size() >= max) {
                return;
            }

            Block block = center.getRelative(c[0], 0, c[1]);
            if (seen.add(key(block))) {
                out.add(block);
            }
        }
    }

    private double normalizeAngle(double angle) {
        double out = angle % (Math.PI * 2D);
        return out < 0 ? out + (Math.PI * 2D) : out;
    }

    private int spreadAt(Block floor, Material catalyst, Material spreadSurface) {
        int changed = 0;
        Block ground = resolveTopSurfaceSoil(floor);
        if (ground == null) {
            return 0;
        }
        Block above = ground.getRelative(0, 1, 0);

        if (spreadSurface != null && isConvertibleSoil(ground.getType()) && ground.getType() != spreadSurface) {
            ground.setType(spreadSurface, false);
            changed++;
        }

        if (getConfig().swapFlowersToMushrooms && isFlower(above.getType())) {
            Material replacement = getFlowerReplacement(above.getType(), catalyst);
            if (replacement != null && above.getType() != replacement) {
                above.setType(replacement, false);
                changed++;
            }
        }

        return changed;
    }

    private Block resolveTopSurfaceSoil(Block sample) {
        int x = sample.getX();
        int z = sample.getZ();
        int highestY = sample.getWorld().getHighestBlockYAt(x, z);
        int minY = sample.getWorld().getMinHeight();

        for (int y = highestY; y >= minY; y--) {
            Block block = sample.getWorld().getBlockAt(x, y, z);
            if (!isConvertibleSoil(block.getType())) {
                continue;
            }

            Block above = block.getRelative(0, 1, 0);
            if (above.getType().isAir() || isReplaceablePlant(above.getType()) || isFlower(above.getType())) {
                return block;
            }
        }

        return null;
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

    private boolean attemptBloom(org.bukkit.entity.Player player, Block center, Material catalyst) {
        Material spreadSurface = resolveSpreadSurface(center.getType());
        if (spreadSurface == null) {
            return false;
        }

        int level = getLevel(player);
        long now = System.currentTimeMillis();
        long ready = cooldowns.getOrDefault(player.getUniqueId(), 0L);
        if (now < ready) {
            SoundPlayer.of(center.getWorld()).play(center.getLocation().add(0.5, 0.5, 0.5), Sound.BLOCK_NOTE_BLOCK_BASS, 0.5f, 0.75f);
            return false;
        }

        if (player.getFoodLevel() < getFoodCost(level)) {
            SoundPlayer.of(center.getWorld()).play(center.getLocation().add(0.5, 0.5, 0.5), Sound.BLOCK_NOTE_BLOCK_BASS, 0.5f, 0.75f);
            return false;
        }

        if (!consumeCatalystFromMainHandIfPresent(player, catalyst)) {
            return false;
        }

        player.setFoodLevel(Math.max(0, player.getFoodLevel() - getFoodCost(level)));
        cooldowns.put(player.getUniqueId(), now + getCooldownMillis(level));

        if (areParticlesEnabled()) {
            center.getWorld().spawnParticle(Particle.SPORE_BLOSSOM_AIR, center.getLocation().add(0.5, 1.0, 0.5), 30, 0.35, 0.15, 0.35, 0.01);
        }
        SoundPlayer.of(center.getWorld()).play(center.getLocation().add(0.5, 0.5, 0.5), Sound.ENTITY_ENDERMAN_AMBIENT, 0.45f, 0.55f);
        startBloom(player, center, catalyst, spreadSurface, level);
        return true;
    }

    private boolean consumeCatalystFromMainHandIfPresent(org.bukkit.entity.Player player, Material catalyst) {
        ItemStack held = player.getInventory().getItemInMainHand();
        if (!isItem(held)) {
            // Some server flows decrement the placed stack before cancelled placement is finalized.
            // In that case, allow activation without double-consuming.
            return true;
        }

        if (held.getType() != catalyst) {
            return true;
        }

        return consumeOne(held);
    }

    private Material resolveSpreadSurface(Material floorType) {
        if (floorType == Material.MYCELIUM) {
            return Material.MYCELIUM;
        }

        if (floorType == Material.PODZOL) {
            return Material.PODZOL;
        }

        return null;
    }

    private int getGuaranteedReach(int level) {
        return level >= 5 ? 6 : 0;
    }

    private boolean isConvertibleSoil(Material type) {
        return type == Material.DIRT
                || type == Material.GRASS_BLOCK
                || type == Material.COARSE_DIRT
                || type == Material.ROOTED_DIRT
                || type == Material.MYCELIUM
                || type == Material.PODZOL;
    }

    private boolean isFlower(Material type) {
        String n = type.name();
        return n.endsWith("_FLOWER")
                || n.endsWith("_TULIP")
                || type == Material.DANDELION
                || type == Material.POPPY
                || type == Material.BLUE_ORCHID
                || type == Material.ALLIUM
                || type == Material.AZURE_BLUET
                || type == Material.OXEYE_DAISY
                || type == Material.CORNFLOWER
                || type == Material.LILY_OF_THE_VALLEY
                || type == Material.WITHER_ROSE
                || type == Material.SUNFLOWER
                || type == Material.LILAC
                || type == Material.ROSE_BUSH
                || type == Material.PEONY
                || type == Material.TORCHFLOWER
                || type == Material.PINK_PETALS
                || type == Material.SPORE_BLOSSOM;
    }

    private Material getFlowerReplacement(Material flower, Material catalyst) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        if (isWarmFlower(flower)) {
            // Warm flowers lean red.
            return random.nextDouble() <= 0.7 ? Material.RED_MUSHROOM : Material.BROWN_MUSHROOM;
        }

        if (isCoolFlower(flower)) {
            // Cool flowers lean brown.
            return random.nextDouble() <= 0.7 ? Material.BROWN_MUSHROOM : Material.RED_MUSHROOM;
        }

        // Fallback uses catalyst flavor.
        return catalyst == Material.BROWN_MUSHROOM ? Material.BROWN_MUSHROOM : Material.RED_MUSHROOM;
    }

    private boolean isWarmFlower(Material flower) {
        return flower == Material.DANDELION
                || flower == Material.POPPY
                || flower == Material.RED_TULIP
                || flower == Material.ORANGE_TULIP
                || flower == Material.PINK_TULIP
                || flower == Material.SUNFLOWER
                || flower == Material.ROSE_BUSH
                || flower == Material.PEONY
                || flower == Material.WITHER_ROSE
                || flower == Material.TORCHFLOWER
                || flower == Material.PINK_PETALS;
    }

    private boolean isCoolFlower(Material flower) {
        return flower == Material.BLUE_ORCHID
                || flower == Material.ALLIUM
                || flower == Material.AZURE_BLUET
                || flower == Material.WHITE_TULIP
                || flower == Material.OXEYE_DAISY
                || flower == Material.CORNFLOWER
                || flower == Material.LILY_OF_THE_VALLEY
                || flower == Material.LILAC
                || flower == Material.SPORE_BLOSSOM;
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
        double scaled = getConfig().bloomAttemptsBase + (getLevelPercent(level) * getConfig().bloomAttemptsFactor);
        double perLevel = Math.max(0, level - 1) * getConfig().bloomAttemptsPerLevel;
        return Math.max(1, (int) Math.round(scaled + perLevel));
    }

    private double getBloomRadius(int level) {
        double radius = getConfig().bloomRadiusBase + (getLevelPercent(level) * getConfig().bloomRadiusFactor);
        if (level >= 5) {
            radius = Math.max(6D, radius);
        }
        return radius;
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
        @art.arcane.adapt.util.config.ConfigDoc(value = "Keeps this adaptation permanently active once learned.", impact = "True removes the normal learn/unlearn flow and treats it as always learned.")
        boolean permanent = false;
        @art.arcane.adapt.util.config.ConfigDoc(value = "Enables or disables this feature.", impact = "Set to false to disable behavior without uninstalling files.")
        boolean enabled = true;
        @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Convert Wood To Hyphae for the Herbalism Spore Bloom adaptation.", impact = "True enables this behavior and false disables it.")
        boolean convertWoodToHyphae = true;
        @art.arcane.adapt.util.config.ConfigDoc(value = "Allows flowers hit by the bloom to be replaced with mushrooms.", impact = "Disable this to keep flowers untouched while still converting soil into mushroom blocks.")
        boolean swapFlowersToMushrooms = true;
        @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Branch Chance for the Herbalism Spore Bloom adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double branchChance = 0.22;
        @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Mushroom Choices for the Herbalism Spore Bloom adaptation.", impact = "Changing this alters the identifier or text used by the feature.")
        String[] mushroomChoices = {"RED_MUSHROOM", "BROWN_MUSHROOM", "CRIMSON_FUNGUS", "WARPED_FUNGUS"};
        @art.arcane.adapt.util.config.ConfigDoc(value = "Base knowledge cost used when learning this adaptation.", impact = "Higher values make each level cost more knowledge.")
        int baseCost = 4;
        @art.arcane.adapt.util.config.ConfigDoc(value = "Maximum level a player can reach for this adaptation.", impact = "Higher values allow more levels; lower values cap progression sooner.")
        int maxLevel = 5;
        @art.arcane.adapt.util.config.ConfigDoc(value = "Knowledge cost required to purchase level 1.", impact = "Higher values make unlocking the first level more expensive.")
        int initialCost = 4;
        @art.arcane.adapt.util.config.ConfigDoc(value = "Scaling factor applied to higher adaptation levels.", impact = "Higher values increase level-to-level cost growth.")
        double costFactor = 0.7;
        @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Bloom Attempts Base for the Herbalism Spore Bloom adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double bloomAttemptsBase = 26;
        @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Bloom Attempts Factor for the Herbalism Spore Bloom adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double bloomAttemptsFactor = 58;
        @art.arcane.adapt.util.config.ConfigDoc(value = "Additional bloom attempts granted each adaptation level.", impact = "Higher values make each level spread across more total blocks.")
        double bloomAttemptsPerLevel = 12;
        @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Bloom Radius Base for the Herbalism Spore Bloom adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double bloomRadiusBase = 5;
        @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Bloom Radius Factor for the Herbalism Spore Bloom adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double bloomRadiusFactor = 10;
        @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Spokes Base for the Herbalism Spore Bloom adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double spokesBase = 6;
        @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Spokes Factor for the Herbalism Spore Bloom adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double spokesFactor = 7;
        @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Blocks Per Pulse Base for the Herbalism Spore Bloom adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double blocksPerPulseBase = 2;
        @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Blocks Per Pulse Factor for the Herbalism Spore Bloom adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double blocksPerPulseFactor = 4;
        @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Spread Interval Ticks Base for the Herbalism Spore Bloom adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double spreadIntervalTicksBase = 3;
        @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Spread Interval Ticks Factor for the Herbalism Spore Bloom adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double spreadIntervalTicksFactor = 1.6;
        @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Food Cost Base for the Herbalism Spore Bloom adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double foodCostBase = 2;
        @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Food Cost Factor for the Herbalism Spore Bloom adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double foodCostFactor = 1.2;
        @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Cooldown Millis Base for the Herbalism Spore Bloom adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double cooldownMillisBase = 1700;
        @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Cooldown Millis Factor for the Herbalism Spore Bloom adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double cooldownMillisFactor = 1100;
        @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Xp Per Mushroom Placed for the Herbalism Spore Bloom adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
        double xpPerMushroomPlaced = 1.4;
    }
}
