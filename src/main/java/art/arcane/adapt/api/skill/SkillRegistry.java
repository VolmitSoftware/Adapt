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

package art.arcane.adapt.api.skill;

import art.arcane.adapt.Adapt;
import art.arcane.adapt.AdaptConfig;
import art.arcane.adapt.api.adaptation.Adaptation;
import art.arcane.adapt.api.potion.BrewingManager;
import art.arcane.adapt.api.recipe.AdaptRecipe;
import art.arcane.adapt.api.tick.TickedObject;
import art.arcane.adapt.api.world.AdaptPlayer;
import art.arcane.adapt.api.world.PlayerSkillLine;
import art.arcane.adapt.api.xp.XPMultiplier;
import art.arcane.adapt.content.gui.SkillsGui;
import art.arcane.adapt.content.skill.*;
import art.arcane.adapt.util.common.format.C;
import art.arcane.volmlib.util.format.Form;
import art.arcane.volmlib.util.math.M;
import art.arcane.adapt.util.common.misc.SoundPlayer;
import art.arcane.volmlib.util.collection.KMap;
import art.arcane.adapt.util.reflect.registries.Particles;
import org.bukkit.Bukkit;
import org.bukkit.Keyed;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerExpChangeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.lang.reflect.InvocationTargetException;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class SkillRegistry extends TickedObject {
    private static final long SLOW_SKILL_REG_MS = 300L;
    private static final int DEFERRED_SKILLS_PER_TICK = 2;

    public static final KMap<String, Skill<?>> skills = new KMap<>();
    private final KMap<String, Skill<?>> knownSkills = new KMap<>();
    private final KMap<String, Class<? extends Skill<?>>> skillTypes = new KMap<>();
    private final Map<NamespacedKey, Adaptation<?>> adaptationRecipeIndex = new ConcurrentHashMap<>();
    private final Deque<Skill<?>> deferredBootstrapRecipeRegistration = new ArrayDeque<>();
    private volatile BukkitTask deferredBootstrapRecipeTask;
    private volatile boolean bootstrapLoading = true;

    public SkillRegistry() {
        super("registry", UUID.randomUUID() + "-sk", 1250);
        registerSkill(SkillAgility.class);
        registerSkill(SkillArchitect.class);
        registerSkill(SkillAxes.class);
        registerSkill(SkillBlocking.class);
        registerSkill(SkillChronos.class);
        registerSkill(SkillCrafting.class);
        registerSkill(SkillDiscovery.class);
        registerSkill(SkillEnchanting.class);
        registerSkill(SkillHerbalism.class);
        registerSkill(SkillHunter.class);
        registerSkill(SkillPickaxes.class);
        registerSkill(SkillRanged.class);
        registerSkill(SkillRift.class);
        registerSkill(SkillSeaborne.class);
        registerSkill(SkillStealth.class);
        registerSkill(SkillSwords.class);
        registerSkill(SkillTaming.class);
        registerSkill(SkillTragOul.class);
        registerSkill(SkillUnarmed.class);
        registerSkill(SkillExcavation.class);
        registerSkill(SkillBrewing.class);
        registerSkill(SkillNether.class);
        bootstrapLoading = false;
        scheduleDeferredBootstrapRecipeRegistration();
    }

    @EventHandler
    public void on(PlayerExpChangeEvent e) {
        Player p = e.getPlayer();
        if (e.getAmount() > 0) {
            getPlayer(p).boostXPToRecents(0.03, 10000);
        }
    }

    private boolean canInteract(Player player, Location targetLocation) {
        return Adapt.instance.getProtectorRegistry().getAllProtectors().stream().allMatch(protector -> protector.canInteract(player, targetLocation, null));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void on(PlayerInteractEvent e) {
        Player p = e.getPlayer();

        boolean commonConditions = p.isSneaking() && e.getAction().equals(Action.RIGHT_CLICK_BLOCK) && e.getClickedBlock() != null;
        boolean isLectern = commonConditions && e.getClickedBlock().getType().equals(Material.LECTERN);
        boolean isObserver = commonConditions && e.getClickedBlock().getType().equals(Material.OBSERVER);
        boolean isAdaptActivator = !e.getBlockFace().equals(BlockFace.UP) && !e.getBlockFace().equals(BlockFace.DOWN) && !p.isSneaking() && e.getAction().equals(Action.RIGHT_CLICK_BLOCK)
                && e.getClickedBlock() != null
                && canInteract(p, e.getClickedBlock().getLocation())
                && e.getClickedBlock().getType().equals(Material.valueOf(AdaptConfig.get().adaptActivatorBlock)) && (p.getInventory().getItemInMainHand().getType().equals(Material.AIR)
                || !p.getInventory().getItemInMainHand().getType().isBlock()) &&
                (p.getInventory().getItemInOffHand().getType().equals(Material.AIR) || !p.getInventory().getItemInOffHand().getType().isBlock());

        if (isAdaptActivator) {
            SoundPlayer spw = SoundPlayer.of(e.getClickedBlock().getWorld());
            spw.play(e.getClickedBlock().getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 0.5f, 0.72f);
            spw.play(e.getClickedBlock().getLocation(), Sound.BLOCK_ENCHANTMENT_TABLE_USE, 0.35f, 0.755f);
            SkillsGui.open(p);
            e.setCancelled(true);
            p.getWorld().spawnParticle(Particles.CRIT_MAGIC, e.getClickedBlock().getLocation().clone().add(0.5, 1, 0.5), 25, 0, 0, 0, 1.1);
            p.getWorld().spawnParticle(Particles.ENCHANTMENT_TABLE, e.getClickedBlock().getLocation().clone().add(0.5, 1, 0.5), 12, 0, 0, 0, 1.1);
        }

        if (isLectern) {
            ItemStack it = p.getInventory().getItemInMainHand();
            if (it.getItemMeta() != null && !it.getItemMeta().getPersistentDataContainer().getKeys().isEmpty()) {
                e.setCancelled(true);
                playDebug(p);
                it.getItemMeta().getPersistentDataContainer().getKeys().forEach(k -> Bukkit.getServer().getConsoleSender().sendMessage(k + " = " + it.getItemMeta().getPersistentDataContainer().getOrDefault(k, PersistentDataType.STRING, "Not a String")));
            }
        }

        if (isObserver) {
            ItemStack it = p.getInventory().getItemInMainHand();
            if (it.getType().equals(Material.EXPERIENCE_BOTTLE)) {
                e.setCancelled(true);
                Bukkit.getServer().getConsoleSender().sendMessage("   ");
                p.setCooldown(Material.ENCHANTED_BOOK, 3);
                AdaptPlayer a = getPlayer(p);
                playDebug(p);

                String xv = a.getData().getMultiplier() - 1d > 0 ? "+" + Form.pc(a.getData().getMultiplier() - 1D) : Form.pc(a.getData().getMultiplier() - 1D);
                Bukkit.getServer().getConsoleSender().sendMessage("Global" + C.GRAY + ": " + C.GREEN + xv);

                for (XPMultiplier i : a.getData().getMultipliers()) {
                    String vv = i.getMultiplier() > 0 ? "+" + Form.pc(i.getMultiplier()) : Form.pc(i.getMultiplier());
                    Bukkit.getServer().getConsoleSender().sendMessage(C.GREEN + "* " + vv + C.GRAY + " for " + Form.duration(i.getGoodFor() - M.ms(), 0));
                }
                for (XPMultiplier i : Adapt.instance.getAdaptServer().getData().getMultipliers()) {
                    String vv = i.getMultiplier() > 0 ? "+" + Form.pc(i.getMultiplier()) : Form.pc(i.getMultiplier());
                    Bukkit.getServer().getConsoleSender().sendMessage(C.GREEN + "* " + vv + C.GRAY + " for " + Form.duration(i.getGoodFor() - M.ms(), 0));
                }

                for (PlayerSkillLine i : a.getData().getSkillLines().v()) {
                    Skill<?> s = i.getRawSkill(a);
                    if (s == null) {
                        continue;
                    }
                    String v = i.getMultiplier() - a.getData().getMultiplier() > 0 ? "+" + Form.pc(i.getMultiplier() - a.getData().getMultiplier()) : Form.pc(i.getMultiplier() - a.getData().getMultiplier());
                    Bukkit.getServer().getConsoleSender().sendMessage("  " + s.getDisplayName() + C.GRAY + ": " + s.getColor() + v);
                    for (XPMultiplier j : i.getMultipliers()) {
                        String vv = j.getMultiplier() > 0 ? "+" + Form.pc(j.getMultiplier()) : Form.pc(j.getMultiplier());
                        Bukkit.getServer().getConsoleSender().sendMessage("  " + s.getShortName() + C.GRAY + " " + vv + " for " + Form.duration(j.getGoodFor() - M.ms(), 0));
                    }
                }
            }
        }
    }

    private void playDebug(Player p) {
        SoundPlayer sp = SoundPlayer.of(p);
        sp.play(p.getLocation(), Sound.BLOCK_BELL_RESONATE, 1f, 0.6f);
        sp.play(p.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 1f, 0.1f);
        sp.play(p.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 1f, 1.6f);
        sp.play(p.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 1f, 1.2f);

    }

    public Skill<?> getSkill(String i) {
        if (i == null) {
            return null;
        }

        Skill<?> direct = skills.get(i);
        if (direct != null) {
            return direct;
        }

        return skills.get(normalizeSkillName(i));
    }

    public Skill<?> getAnySkill(String i) {
        if (i == null) {
            return null;
        }

        Skill<?> direct = knownSkills.get(i);
        if (direct != null) {
            return direct;
        }

        return knownSkills.get(normalizeSkillName(i));
    }

    public List<Skill<?>> getSkills() {
        return skills.v();
    }

    public List<Skill<?>> getAllSkills() {
        return new ArrayList<>(knownSkills.v());
    }

    public synchronized void registerSkill(Class<? extends Skill<?>> skillType) {
        long started = System.currentTimeMillis();
        long instantiateStarted = started;
        Skill<?> skill = instantiateSkill(skillType);
        long instantiateMs = System.currentTimeMillis() - instantiateStarted;
        if (skill == null) {
            return;
        }

        String skillName = normalizeSkillName(skill.getName());
        skillTypes.put(skillName, skillType);
        Skill<?> previous = knownSkills.put(skillName, skill);
        if (previous != null && previous != skill) {
            unregisterRecipes(previous);
            previous.unregister();
        }

        if (!skill.isEnabled()) {
            skill.unregister();
            skills.remove(skillName);
            return;
        }

        skills.put(skillName, skill);
        if (bootstrapLoading) {
            deferredBootstrapRecipeRegistration.addLast(skill);
        } else {
            registerRecipes(skill);
        }

        long totalMs = System.currentTimeMillis() - started;
        if (totalMs >= SLOW_SKILL_REG_MS || instantiateMs >= SLOW_SKILL_REG_MS) {
            Adapt.warn("Skill registration slow-path [" + skillName + "] total=" + totalMs + "ms instantiate=" + instantiateMs + "ms bootstrap=" + bootstrapLoading + ".");
        }
    }

    public synchronized boolean hotReloadSkillConfig(String skillName) {
        String normalized = normalizeSkillName(skillName);
        Skill<?> loaded = knownSkills.get(normalized);
        if (loaded instanceof SimpleSkill<?> simpleSkill) {
            boolean wasEnabled = loaded.isEnabled();
            boolean ok = simpleSkill.reloadConfigFromDisk(false);
            if (!ok) {
                return false;
            }

            if (!loaded.isEnabled()) {
                unregisterRecipes(loaded);
                if (wasEnabled) {
                    loaded.unregister();
                }
                skills.remove(normalized);
                return true;
            }

            if (!wasEnabled) {
                return replaceSkillInstance(normalized, inferSkillType(normalized, loaded), loaded);
            }

            skills.put(normalized, loaded);
            unregisterRecipes(loaded);
            registerRecipes(loaded);
            return true;
        }

        Class<? extends Skill<?>> skillType = inferSkillType(normalized, loaded);
        if (skillType == null) {
            Adapt.verbose("No known skill type for config hotload: " + skillName);
            return false;
        }

        return replaceSkillInstance(normalized, skillType, loaded);
    }

    @SuppressWarnings("unchecked")
    private Class<? extends Skill<?>> inferSkillType(String normalizedSkillName, Skill<?> loaded) {
        Class<? extends Skill<?>> skillType = skillTypes.get(normalizedSkillName);
        if (skillType != null) {
            return skillType;
        }

        if (loaded != null && Skill.class.isAssignableFrom(loaded.getClass())) {
            return (Class<? extends Skill<?>>) loaded.getClass();
        }

        return null;
    }

    private boolean replaceSkillInstance(String normalizedName, Class<? extends Skill<?>> skillType, Skill<?> previousLoaded) {
        Skill<?> replacement = instantiateSkill(skillType);
        if (replacement == null) {
            return false;
        }

        Skill<?> previousKnown = knownSkills.put(normalizedName, replacement);
        if (previousKnown != null && previousKnown != replacement) {
            unregisterRecipes(previousKnown);
            previousKnown.unregister();
        } else if (previousLoaded != null && previousLoaded != replacement) {
            unregisterRecipes(previousLoaded);
            previousLoaded.unregister();
        }

        if (!replacement.isEnabled()) {
            replacement.unregister();
            skills.remove(normalizedName);
            return true;
        }

        Skill<?> previous = skills.put(normalizedName, replacement);
        if (previous != null && previous != replacement) {
            unregisterRecipes(previous);
            previous.unregister();
        }

        registerRecipes(replacement);
        return true;
    }

    public synchronized void refreshRecipes(Skill<?> skill) {
        if (skill == null) {
            return;
        }

        unregisterRecipes(skill);
        if (skill.isEnabled()) {
            registerRecipes(skill);
        }
    }

    public boolean isKnownSkill(String skillName) {
        if (skillName == null) {
            return false;
        }

        return skillTypes.containsKey(normalizeSkillName(skillName));
    }

    public Adaptation<?> getRequiredAdaptation(Recipe recipe) {
        if (!(recipe instanceof Keyed keyed)) {
            return null;
        }

        return adaptationRecipeIndex.get(keyed.getKey());
    }

    private Skill<?> instantiateSkill(Class<? extends Skill<?>> skillType) {
        try {
            return skillType.getConstructor().newInstance();
        } catch (InstantiationException | IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {
            e.printStackTrace();
            return null;
        }
    }

    private void unregisterRecipes(Skill<?> s) {
        s.getRecipes().forEach(AdaptRecipe::unregister);
        s.getAdaptations().forEach(adaptation -> {
            adaptation.getRecipes().forEach(recipe -> {
                removeAdaptationRecipeIndex(recipe, adaptation);
                recipe.unregister();
            });
        });
    }

    private void registerRecipes(Skill<?> s) {
        if (!s.isEnabled()) {
            return;
        }
        s.getRecipes().forEach(AdaptRecipe::register);
        s.getAdaptations().forEach(adaptation -> {
            if (!adaptation.isEnabled()) {
                return;
            }
            adaptation.getRecipes().forEach(recipe -> {
                recipe.register();
                indexAdaptationRecipe(recipe, adaptation);
            });
            adaptation.getBrewingRecipes().forEach(r -> BrewingManager.registerRecipe(adaptation.getName(), r));
        });
    }

    @Override
    public void unregister() {
        BukkitTask pendingTask = deferredBootstrapRecipeTask;
        if (pendingTask != null) {
            pendingTask.cancel();
            deferredBootstrapRecipeTask = null;
        }
        deferredBootstrapRecipeRegistration.clear();
        for (Skill<?> i : knownSkills.v()) {
            i.unregister();
            unregisterRecipes(i);
        }
        skills.clear();
        knownSkills.clear();
        skillTypes.clear();
        adaptationRecipeIndex.clear();
    }

    @Override
    public void onTick() {

    }

    private String normalizeSkillName(String raw) {
        String normalized = raw.trim().toLowerCase(Locale.ROOT);
        if (normalized.startsWith("[skill]-")) {
            normalized = normalized.substring("[skill]-".length());
        }

        if (normalized.equals("chrono")) {
            normalized = "chronos";
        }

        return normalized;
    }

    private void indexAdaptationRecipe(AdaptRecipe recipe, Adaptation<?> adaptation) {
        if (recipe == null || adaptation == null) {
            return;
        }

        NamespacedKey key = recipe.getNSKey();
        Adaptation<?> previous = adaptationRecipeIndex.put(key, adaptation);
        if (previous != null && previous != adaptation) {
            Adapt.warn("Recipe key conflict for " + key + ": " + previous.getName() + " replaced by " + adaptation.getName());
        }
    }

    private void removeAdaptationRecipeIndex(AdaptRecipe recipe, Adaptation<?> adaptation) {
        if (recipe == null) {
            return;
        }

        NamespacedKey key = recipe.getNSKey();
        if (adaptation == null) {
            adaptationRecipeIndex.remove(key);
            return;
        }

        adaptationRecipeIndex.computeIfPresent(key, (k, current) -> current == adaptation ? null : current);
    }

    private synchronized void scheduleDeferredBootstrapRecipeRegistration() {
        if (deferredBootstrapRecipeRegistration.isEmpty() || deferredBootstrapRecipeTask != null) {
            return;
        }

        Adapt.info("Deferring recipe registration for " + deferredBootstrapRecipeRegistration.size() + " skills.");
        deferredBootstrapRecipeTask = Bukkit.getScheduler().runTaskTimer(Adapt.instance, () -> {
            int processed = 0;
            long started = System.currentTimeMillis();
            while (processed < DEFERRED_SKILLS_PER_TICK) {
                Skill<?> skill = deferredBootstrapRecipeRegistration.pollFirst();
                if (skill == null) {
                    break;
                }
                registerRecipes(skill);
                processed++;
                if (System.currentTimeMillis() - started > 8L) {
                    break;
                }
            }

            if (deferredBootstrapRecipeRegistration.isEmpty()) {
                BukkitTask task = deferredBootstrapRecipeTask;
                deferredBootstrapRecipeTask = null;
                if (task != null) {
                    task.cancel();
                }
                Adapt.info("Deferred recipe registration completed.");
            }
        }, 1L, 1L);
    }
}
