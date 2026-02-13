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

package com.volmit.adapt.api.adaptation;

import com.google.common.collect.ImmutableSet;
import com.volmit.adapt.Adapt;
import com.volmit.adapt.AdaptConfig;
import com.volmit.adapt.api.Component;
import com.volmit.adapt.api.advancement.AdaptAdvancement;
import com.volmit.adapt.api.potion.BrewingRecipe;
import com.volmit.adapt.api.protection.Protector;
import com.volmit.adapt.api.recipe.AdaptRecipe;
import com.volmit.adapt.api.skill.Skill;
import com.volmit.adapt.api.tick.Ticked;
import com.volmit.adapt.api.world.AdaptPlayer;
import com.volmit.adapt.api.world.PlayerAdaptation;
import com.volmit.adapt.api.world.PlayerData;
import com.volmit.adapt.api.world.PlayerSkillLine;
import com.volmit.adapt.content.event.AdaptAdaptationUseEvent;
import com.volmit.adapt.util.*;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Recipe;

import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public interface Adaptation<T> extends Ticked, Component {
    Map<String, Long> PERMANENT_LEARN_CONFIRMATIONS = new ConcurrentHashMap<>();
    Map<String, Long> USAGE_BASELINE_XP_COOLDOWNS = new ConcurrentHashMap<>();
    long PERMANENT_LEARN_CONFIRM_WINDOW_MS = 6_000L;

    int getMaxLevel();

    default void xp(Player p, double amount) {
        xp(p, amount, null);
    }

    default void xp(Player p, double amount, String rewardKey) {
        getSkill().xp(p, amount, adaptationRewardKey(rewardKey));
    }

    default void xp(Player p, Location l, double amount) {
        xp(p, l, amount, null);
    }

    default void xp(Player p, Location l, double amount, String rewardKey) {
        getSkill().xp(p, l, amount, adaptationRewardKey(rewardKey));
    }

    default void xpSilent(Player p, double amount, String rewardKey) {
        getSkill().xpSilent(p, amount, adaptationRewardKey(rewardKey));
    }

    default void xpSilent(Player p, double amount) {
        xpSilent(p, amount, null);
    }

    default String adaptationRewardKey(String rewardKey) {
        String suffix = rewardKey == null ? "" : rewardKey.trim();
        if (suffix.isEmpty()) {
            suffix = "use";
        }
        return "adaptation:" + getName() + ":" + suffix;
    }

    @Override
    default boolean areParticlesEnabled() {
        if (!Component.super.areParticlesEnabled()) {
            return false;
        }

        AdaptConfig.Effects effects = AdaptConfig.get().getEffects();
        if (effects != null && effects.getAdaptationParticleOverrides() != null && !effects.getAdaptationParticleOverrides().isEmpty()) {
            String key = getName();
            Boolean override = effects.getAdaptationParticleOverrides().get(key);
            if (override == null && key != null) {
                override = effects.getAdaptationParticleOverrides().get(key.toLowerCase(Locale.ROOT));
            }
            if (override != null && !override) {
                return false;
            }
        }

        Object config = getConfig();
        if (config == null) {
            return true;
        }

        Boolean directToggle = readBooleanField(config, "showParticles");
        if (directToggle != null) {
            return directToggle;
        }

        Boolean genericToggle = readBooleanField(config, "showParticleEffects");
        if (genericToggle != null) {
            return genericToggle;
        }

        return true;
    }

    @Override
    default boolean areSoundsEnabled() {
        if (!Component.super.areSoundsEnabled()) {
            return false;
        }

        Object config = getConfig();
        if (config == null) {
            return true;
        }

        Boolean directToggle = readBooleanField(config, "showSounds");
        if (directToggle != null) {
            return directToggle;
        }

        return true;
    }

    private static Boolean readBooleanField(Object source, String fieldName) {
        if (source == null || fieldName == null || fieldName.isBlank()) {
            return null;
        }

        Class<?> current = source.getClass();
        while (current != null) {
            try {
                Field field = current.getDeclaredField(fieldName);
                field.setAccessible(true);
                Object value = field.get(source);
                if (value instanceof Boolean bool) {
                    return bool;
                }
                return null;
            } catch (NoSuchFieldException ignored) {
                current = current.getSuperclass();
            } catch (Throwable ignored) {
                return null;
            }
        }

        return null;
    }

    default void awardUsageBaselineXp(Player p, int level) {
        if (p == null || level <= 0 || !p.getClass().getSimpleName().equals("CraftPlayer")) {
            return;
        }

        AdaptConfig.AdaptationXp cfg = AdaptConfig.get().getAdaptationXp();
        if (cfg == null || !cfg.isUsageBaselineEnabled()) {
            return;
        }

        long now = M.ms();
        long cooldown = Math.max(250L, cfg.getUsageBaselineCooldownMillis());
        String key = p.getUniqueId() + "|" + getName();
        Long next = USAGE_BASELINE_XP_COOLDOWNS.get(key);
        if (next != null && next > now) {
            return;
        }

        if (USAGE_BASELINE_XP_COOLDOWNS.size() > 6000) {
            USAGE_BASELINE_XP_COOLDOWNS.entrySet().removeIf(i -> i.getValue() <= now);
        }

        double reward = cfg.getUsageBaselineXp() + ((Math.max(1, level) - 1) * cfg.getUsageBaselineXpPerLevel());
        if (reward <= 0) {
            return;
        }

        USAGE_BASELINE_XP_COOLDOWNS.put(key, now + cooldown);
        xpSilent(p, reward, "baseline-use");
    }

    default <F> F getStorage(Player p, String key, F defaultValue) {
        PlayerData data = getPlayer(p).getData();
        PlayerSkillLine line = data.getSkillLineNullable(getSkill().getName());
        if (line == null) return defaultValue;
        PlayerAdaptation adaptation = line.getAdaptation(getName());
        if (adaptation == null) return defaultValue;
        Object o = adaptation.getStorage().get(key);
        return o == null ? defaultValue : (F) o;
    }

    default <F> F getStorage(Player p, String key) {
        return getStorage(p, key, null);
    }

    default boolean setStorage(Player p, String key, Object value) {
        PlayerData data = getPlayer(p).getData();
        PlayerSkillLine line = data.getSkillLineNullable(getSkill().getName());
        if (line == null) return false;
        PlayerAdaptation adaptation = line.getAdaptation(getName());
        if (adaptation == null) return false;
        if (value == null) {
            adaptation.getStorage().remove(key);
            return true;
        }

        adaptation.getStorage().put(key, value);
        return true;
    }

    default boolean canUse(AdaptPlayer player) {
        Adapt.verbose("Checking if " + player.getPlayer().getName() + " can use " + getName() + "...");
        AdaptAdaptationUseEvent e = new AdaptAdaptationUseEvent(!Bukkit.isPrimaryThread(), player, this);
        Bukkit.getServer().getPluginManager().callEvent(e);
        return (!e.isCancelled());
    }

    default boolean canUse(Player player) {
        return canUse(getPlayer(player));
    }

    default boolean hasBlacklistPermission(Player p, Adaptation a) {
        if (p.isOp()) { // If the player is an operator, bypass the permission check
            return false;
        }
        String blacklistPermission = "adapt.blacklist." + a.getName().replaceAll("-", "");
        Adapt.verbose("Checking if player " + p.getName() + " has blacklist permission " + blacklistPermission);

        return p.hasPermission(blacklistPermission);
    }

    default String getStorageString(Player p, String key, String defaultValue) {
        return getStorage(p, key, defaultValue);
    }

    default String getStorageString(Player p, String key) {
        return getStorage(p, key);
    }

    default Integer getStorageInt(Player p, String key, Integer defaultValue) {
        return getStorage(p, key, defaultValue);
    }

    default Integer getStorageInt(Player p, String key) {
        return getStorage(p, key);
    }

    default Double getStorageDouble(Player p, String key, Double defaultValue) {
        return getStorage(p, key, defaultValue);
    }

    default Double getStorageDouble(Player p, String key) {
        return getStorage(p, key);
    }

    default Boolean getStorageBoolean(Player p, String key, Boolean defaultValue) {
        return getStorage(p, key, defaultValue);
    }

    default Boolean getStorageBoolean(Player p, String key) {
        return getStorage(p, key);
    }

    default Long getStorageLong(Player p, String key, Long defaultValue) {
        return getStorage(p, key, defaultValue);
    }

    default Long getStorageLong(Player p, String key) {
        return getStorage(p, key);
    }

    Class<T> getConfigurationClass();

    void registerConfiguration(Class<T> type);

    boolean isEnabled();

    boolean isPermanent();

    T getConfig();

    AdaptAdvancement buildAdvancements();

    void addStats(int level, Element v);

    int getBaseCost();

    String getDescription();

    Material getIcon();

    Skill<?> getSkill();

    void setSkill(Skill<?> skill);

    String getName();

    int getInitialCost();

    double getCostFactor();

    List<AdaptRecipe> getRecipes();

    List<BrewingRecipe> getBrewingRecipes();

    void onRegisterAdvancements(List<AdaptAdvancement> advancements);

    default Set<Protector> getProtectors() {
        Set<Protector> protectors = new HashSet<>(Adapt.instance.getProtectorRegistry().getDefaultProtectors());
        Map<String, Boolean> overrides = AdaptConfig.get().getProtectionOverrides().getOrDefault(this.getName(), Collections.emptyMap());
        overrides.forEach((protector, enabled) -> {
            if (enabled) {
                Protector p = Adapt.instance.getProtectorRegistry().getAllProtectors()
                        .stream()
                        .filter(pr -> pr.getName().equals(protector))
                        .findFirst()
                        .orElse(null);
                if (p == null) {
                    Adapt.error("Could not find protector " + protector + " for adaptation " + this.getName() + ". Skipping...");
                } else {
                    protectors.add(p);
                }
            } else {
                protectors.removeIf(pr -> pr.getName().equals(protector));
            }
        });
        return ImmutableSet.copyOf(protectors);
    }

    default boolean canBlockBreak(Player player, Location blockLocation) {
        return getProtectors().stream().allMatch(protector -> protector.canBlockBreak(player, blockLocation, this));
    }

    default boolean canBlockPlace(Player player, Location blockLocation) {
        return getProtectors().stream().allMatch(protector -> protector.canBlockPlace(player, blockLocation, this));
    }

    default boolean canPVP(Player player, Location victimLocation) {
        return getProtectors().stream().allMatch(protector -> protector.canPVP(player, victimLocation, this));
    }

    default boolean canPVE(Player player, Location victimLocation) {
        return getProtectors().stream().allMatch(protector -> protector.canPVE(player, victimLocation, this));
    }

    default boolean canInteract(Player player, Location targetLocation) {
        return getProtectors().stream().allMatch(protector -> protector.canInteract(player, targetLocation, this));
    }

    default boolean canAccessChest(Player player, Location chestLocation) {
        return getProtectors().stream().allMatch(protector -> protector.canAccessChest(player, chestLocation, this));
    }

    default boolean checkRegion(Player player) {
        return getProtectors().stream().allMatch(protector -> protector.checkRegion(player, player.getLocation(), this));
    }

    default boolean hasUsageConflict(Player p) {
        Map<String, List<String>> conflicts = AdaptConfig.get().getAdaptationUsageConflicts();
        if (conflicts == null || conflicts.isEmpty()) {
            return false;
        }

        String me = getName().toLowerCase(Locale.ROOT);
        Set<String> denied = new HashSet<>();
        for (Map.Entry<String, List<String>> entry : conflicts.entrySet()) {
            if (entry.getKey() != null && entry.getValue() != null && entry.getKey().equalsIgnoreCase(me)) {
                entry.getValue().stream()
                        .filter(Objects::nonNull)
                        .map(i -> i.toLowerCase(Locale.ROOT))
                        .forEach(denied::add);
            }
        }

        for (Map.Entry<String, List<String>> entry : conflicts.entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null) {
                continue;
            }

            boolean containsThisAdaptation = entry.getValue().stream()
                    .filter(Objects::nonNull)
                    .map(i -> i.toLowerCase(Locale.ROOT))
                    .anyMatch(me::equals);
            if (containsThisAdaptation) {
                denied.add(entry.getKey().toLowerCase(Locale.ROOT));
            }
        }

        denied.remove(me);
        for (String conflict : denied) {
            if (getPlayer(p).hasAdaptation(conflict)) {
                Adapt.verbose("Player " + p.getName() + " has conflicting adaptation " + conflict + " and cannot use " + getName());
                return true;
            }
        }

        return false;
    }

    default int getActiveLevel(Player p) {
        try {
            if (p == null || p.isDead()) { // Check if player is not invalid
                return 0;
            }

            int level = getLevel(p);
            if (level <= 0) {
                return 0;
            }

            if (AdaptConfig.get().blacklistedWorlds.contains(p.getWorld().getName())) {
                Adapt.verbose("Player " + p.getName() + " is in a blacklisted world. Skipping adaptation " + this.getName());
                return 0;
            }
            if (p.getGameMode().equals(GameMode.CREATIVE) || p.getGameMode().equals(GameMode.SPECTATOR)) {
                Adapt.verbose("Player " + p.getName() + " is in creative or spectator mode. Skipping adaptation " + this.getName());
                return 0;
            }
            if (!checkRegion(p)) {
                Adapt.verbose("Player " + p.getName() + " don't have adaptation - " + this.getName() + " permission.");
                return 0;
            }

            if (hasBlacklistPermission(p, this)) {
                Adapt.verbose("Player " + p.getName() + " has blacklist permission for adaptation " + this.getName());
                return 0;
            }
            if (hasUsageConflict(p)) {
                return 0;
            }
            if (!canUse(p)) {
                Adapt.verbose("Player " + p.getName() + " can't use adaptation, This is an API restriction" + this.getName());
                return 0;
            }
            awardUsageBaselineXp(p, level);
            Adapt.verbose("Player " + p.getName() + " used adaptation " + this.getName());
            return level;
        } catch (Exception e) {
            if (e instanceof IndexOutOfBoundsException) { // This is that fucking bug with Citizens Spoofing Players. I hate it.
                Adapt.verbose("Citizens/PacketSpoofing is Messing stuff up again. I hate it.");
                Adapt.verbose(e.getMessage());
            } else {
                e.printStackTrace();
            }
            return 0;
        }
    }

    default boolean hasAdaptation(Player p) {
        return getActiveLevel(p) > 0;
    }

    default int getLevel(Player p) {
        if (p == null) {
            return 0;
        }
        if (!p.getClass().getSimpleName().equals("CraftPlayer")) {
            Adapt.verbose("Simple name: " + p.getClass().getSimpleName());
            return 0;
        }
        if (!this.isEnabled()) {
            return 0;
        }
        if (!this.getSkill().isEnabled()) {
            return 0;
        }
        AdaptPlayer adaptPlayer = getPlayer(p);
        PlayerSkillLine line = adaptPlayer.getData().getSkillLine(getSkill().getName());
        if (line == null) {
            return 0;
        }
        return line.getAdaptationLevel(getName());
    }

    default double getLevelPercent(Player p) {
        if (!this.isEnabled()) {
            return 0;
        }
        if (!this.getSkill().isEnabled()) {
            return 0;
        }
        if (!p.getClass().getSimpleName().equals("CraftPlayer")) {
            return 0.0;
        }
        return Math.min(Math.max(0, M.lerpInverse(0, getMaxLevel(), getLevel(p))), 1);
    }

    default double getLevelPercent(int p) {
        return Math.min(Math.max(0, M.lerpInverse(0, getMaxLevel(), p)), 1);
    }

    default int getCostFor(int level) {
        return (int) (Math.max(1, getBaseCost() + (getBaseCost() * (level * getCostFactor())))) + (level == 1 ? getInitialCost() : 0);
    }

    default int getPowerCostFor(int level, int myLevel) {
        return level - myLevel;
    }

    default int getCostFor(int level, int myLevel) {
        if (myLevel >= level) {
            return 0;
        }


        int c = 0;

        for (int i = myLevel + 1; i <= level; i++) {
            c += getCostFor(i);
        }

        return c;
    }

    default int getRefundCostFor(int level, int myLevel) {
        if (myLevel <= level) {
            return 0;
        }

        int c = 0;

        for (int i = level + 1; i <= myLevel; i++) {
            c += getCostFor(i);
        }

        return c;
    }

    default String getDisplayName() {
        if (!this.isEnabled()) {
            return C.DARK_GRAY + Form.capitalizeWords(getName().replaceAll("\\Q" + getSkill().getName() + "-\\E", "").replaceAll("\\Q-\\E", " "));
        }
        if (!this.getSkill().isEnabled()) {
            return C.DARK_GRAY + Form.capitalizeWords(getName().replaceAll("\\Q" + getSkill().getName() + "-\\E", "").replaceAll("\\Q-\\E", " "));
        }
        return C.RESET + "" + C.BOLD + getSkill().getColor().toString() + Form.capitalizeWords(getName().replaceAll("\\Q" + getSkill().getName() + "-\\E", "").replaceAll("\\Q-\\E", " "));
    }

    default String getDisplayName(int level) {
        if (!this.isEnabled()) {
            return getDisplayName();
        }
        if (!this.getSkill().isEnabled()) {
            return getDisplayName();
        }
        if (level >= 1) {
            return getDisplayName() + C.RESET + " " + C.UNDERLINE + C.WHITE + Form.toRoman(level) + C.RESET;
        }

        return getDisplayName();
    }

    default String getDisplayNameNoRoman(int level) {
        if (level >= 1) {
            return getDisplayName() + C.RESET + " " + C.UNDERLINE + C.WHITE + level + C.RESET;
        }

        return getDisplayName();
    }

    default BlockFace getBlockFace(Player player, int maxrange) {
        List<Block> lastTwoTargetBlocks = player.getLastTwoTargetBlocks(null, maxrange);
        if (lastTwoTargetBlocks.size() != 2 || !lastTwoTargetBlocks.get(1).getType().isOccluding()) return null;
        Block targetBlock = lastTwoTargetBlocks.get(1);
        Block adjacentBlock = lastTwoTargetBlocks.get(0);
        return targetBlock.getFace(adjacentBlock);
    }

    default CustomModel getModel() {
        return CustomModel.get(getIcon(), "adaptation", getName(), "icon");
    }

    default CustomModel getModel(int level) {
        var model = CustomModel.get(getIcon(), "adaptation", getName(), "level-" + level);
        if (model.material() == getIcon() && model.model() == 0)
            model = CustomModel.get(Material.PAPER, "snippets", "gui", "level", String.valueOf(level));
        if (model.material() == Material.PAPER && model.model() == 0)
            model = getModel();
        return model;
    }

    default boolean openGui(Player player, boolean checkPermissions) {
        if (hasBlacklistPermission(player, this)) {
            return false;
        } else {
            openGui(player);
            return true;
        }
    }

    default void openGui(Player player) {
        openGui(player, 0);
    }

    default void openGui(Player player, int page) {
        if (!isEnabled()) {
            return;
        }
        if (!getSkill().isEnabled()) {
            return;
        }
        if (!Bukkit.isPrimaryThread()) {
            int targetPage = page;
            J.s(() -> openGui(player, targetPage));
            return;
        }

        SoundPlayer spw = SoundPlayer.of(player.getWorld());
        spw.play(player.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 1.1f, 1.255f);
        spw.play(player.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 0.7f, 0.655f);
        spw.play(player.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 0.3f, 0.855f);

        boolean reserveNavigation = AdaptConfig.get().isGuiBackButton();
        GuiLayout.PagePlan plan = GuiLayout.plan(getMaxLevel(), reserveNavigation);
        int currentPage = GuiLayout.clampPage(page, plan.pageCount());
        int start = currentPage * plan.itemsPerPage();
        int end = Math.min(getMaxLevel(), start + plan.itemsPerPage());

        int mylevel = getPlayer(player).getSkillLine(getSkill().getName()).getAdaptationLevel(getName());

        long k = getPlayer(player).getData().getSkillLine(getSkill().getName()).getKnowledge();

        Window w = new UIWindow(player);
        GuiTheme.apply(w, "skill/" + getSkill().getName() + "/" + getName());
        w.setViewportHeight(plan.rows());

        List<GuiEffects.Placement> reveal = new ArrayList<>();
        for (int row = 0; row < plan.contentRows(); row++) {
            int rowStart = start + (row * GuiLayout.WIDTH);
            if (rowStart >= end) {
                break;
            }

            int rowCount = Math.min(GuiLayout.WIDTH, end - rowStart);
            for (int i = 0; i < rowCount; i++) {
                int lvl = rowStart + i + 1;
                int pos = GuiLayout.centeredPosition(i, rowCount);
                int c = getCostFor(lvl, mylevel);
                int rc = getRefundCostFor(lvl - 1, mylevel);
                int pc = getPowerCostFor(lvl, mylevel);
                boolean pendingPermanentConfirm = isPermanentLearnConfirmationPending(player, lvl);
                Element de = new UIElement("lp-" + lvl + "g")
                        .setMaterial(new MaterialBlock(getIcon()))
                        .setModel(getModel(lvl))
                        .setName(getDisplayName(lvl))
                        .setEnchanted(mylevel >= lvl)
                        .setProgress(1D)
                        .addLore(Form.wrapWordsPrefixed(getDescription(), "" + C.GRAY, 40))
                        .addLore(mylevel >= lvl ? ("") : ("" + C.WHITE + c + C.GRAY + " " + Localizer.dLocalize("snippets.adapt_menu.knowledge_cost") + " " + (AdaptConfig.get().isHardcoreNoRefunds() ? C.DARK_RED + "" + C.BOLD + Localizer.dLocalize("snippets.adapt_menu.no_refunds") : "")))
                        .addLore(mylevel >= lvl ? AdaptConfig.get().isHardcoreNoRefunds() ? (C.GREEN + Localizer.dLocalize("snippets.adapt_menu.already_learned") + " " + C.DARK_RED + "" + C.BOLD + Localizer.dLocalize("snippets.adapt_menu.no_refunds")) : (isPermanent() ? "" : (C.GREEN + Localizer.dLocalize("snippets.adapt_menu.already_learned") + " " + C.GRAY + Localizer.dLocalize("snippets.adapt_menu.unlearn_refund") + " " + C.GREEN + rc + " " + Localizer.dLocalize("snippets.adapt_menu.knowledge_cost"))) : (k >= c ? (C.BLUE + Localizer.dLocalize("snippets.adapt_menu.click_learn") + " " + getDisplayName(lvl)) : (k == 0 ? (C.RED + Localizer.dLocalize("snippets.adapt_menu.no_knowledge")) : (C.RED + "(" + Localizer.dLocalize("snippets.adapt_menu.you_only_have") + " " + C.WHITE + k + C.RED + " " + Localizer.dLocalize("snippets.adapt_menu.knowledge_available") + ")"))))
                        .addLore(mylevel < lvl && getPlayer(player).getData().hasPowerAvailable(pc) ? C.GREEN + "" + lvl + " " + Localizer.dLocalize("snippets.adapt_menu.power_drain") : mylevel >= lvl ? C.GREEN + "" + lvl + " " + Localizer.dLocalize("snippets.adapt_menu.power_drain") : C.RED + Localizer.dLocalize("snippets.adapt_menu.not_enough_power") + "\n" + C.RED + Localizer.dLocalize("snippets.adapt_menu.how_to_level_up"))
                        .addLore((isPermanent() ? C.RED + "" + C.BOLD + Localizer.dLocalize("snippets.adapt_menu.may_not_unlearn") : ""))
                        .addLore(isPermanent() && mylevel < lvl
                                ? (pendingPermanentConfirm
                                ? C.GOLD + "" + C.BOLD + "Click again now to confirm permanent learn."
                                : C.YELLOW + "Double-click required to confirm permanent learn.")
                                : "")
                        .onLeftClick((e) -> {
                            if (mylevel >= lvl) {
                                unlearn(player, lvl, false);
                                spw.play(player.getLocation(), Sound.BLOCK_NETHER_GOLD_ORE_PLACE, 0.7f, 1.355f);
                                spw.play(player.getLocation(), Sound.BLOCK_BEACON_DEACTIVATE, 0.4f, 0.755f);
                                w.close();
                                if (AdaptConfig.get().getLearnUnlearnButtonDelayTicks() != 0) {
                                    if (isPermanent()) {
                                        spw.play(player.getLocation(), Sound.ENTITY_BLAZE_DEATH, 0.5f, 1.355f);
                                        player.sendTitle(" ", C.RED + "" + C.BOLD + Localizer.dLocalize("snippets.adapt_menu.may_not_unlearn") + " " + getDisplayName(mylevel), 1, 10, 11);
                                    } else {
                                        player.sendTitle(" ", C.GRAY + Localizer.dLocalize("snippets.adapt_menu.unlearned") + " " + getDisplayName(mylevel), 1, 10, 11);
                                    }
                                }
                                J.s(() -> openGui(player, currentPage), AdaptConfig.get().getLearnUnlearnButtonDelayTicks());
                                return;
                            }

                            if (k >= c && getPlayer(player).getData().hasPowerAvailable(pc)) {
                                if (isPermanent() && !consumePermanentLearnConfirmation(player, lvl)) {
                                    spw.play(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.7f, 0.85f);
                                    player.sendTitle(" ", C.GOLD + "" + C.BOLD + "Click again to confirm permanent learn", 1, 16, 8);
                                    J.s(() -> openGui(player, currentPage), 1);
                                    return;
                                }

                                if (getPlayer(player).getData().getSkillLine(getSkill().getName()).spendKnowledge(c)) {
                                    getPlayer(player).getData().getSkillLine(getSkill().getName()).setAdaptation(this, lvl);
                                    spw.play(player.getLocation(), Sound.BLOCK_NETHER_GOLD_ORE_PLACE, 0.9f, 1.355f);
                                    spw.play(player.getLocation(), Sound.BLOCK_ENCHANTMENT_TABLE_USE, 1.7f, 0.355f);
                                    spw.play(player.getLocation(), Sound.BLOCK_BEACON_POWER_SELECT, 0.4f, 0.155f);
                                    spw.play(player.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 0.2f, 1.455f);
                                    if (isPermanent()) {
                                        spw.play(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.7f, 1.355f);
                                        spw.play(player.getLocation(), Sound.ITEM_GOAT_HORN_SOUND_1, 0.7f, 1.355f);
                                    }
                                    w.close();
                                    if (AdaptConfig.get().getLearnUnlearnButtonDelayTicks() != 0) {
                                        player.sendTitle(" ", C.GRAY + Localizer.dLocalize("snippets.adapt_menu.learned") + " " + getDisplayName(lvl), 1, 5, 11);
                                    }
                                    J.s(() -> openGui(player, currentPage), AdaptConfig.get().getLearnUnlearnButtonDelayTicks());
                                } else {
                                    spw.play(player.getLocation(), Sound.BLOCK_BAMBOO_HIT, 0.7f, 1.855f);
                                }
                            } else {
                                spw.play(player.getLocation(), Sound.BLOCK_BAMBOO_HIT, 0.7f, 1.855f);
                            }
                        });
                de.addLore(" ");
                addStats(lvl, de);
                reveal.add(new GuiEffects.Placement(pos, row, de));
            }
        }
        GuiEffects.applyReveal(w, reveal);

        if (plan.hasNavigationRow()) {
            int navRow = plan.rows() - 1;
            int jumpPages = 5;
            int jumpBack = Math.max(0, currentPage - jumpPages);
            int jumpForward = Math.min(plan.pageCount() - 1, currentPage + jumpPages);
            if (currentPage > 0) {
                w.setElement(-4, navRow, new UIElement("adapt-prev")
                        .setMaterial(new MaterialBlock(Material.ARROW))
                        .setName(C.WHITE + "Previous")
                        .addLore(C.GRAY + "Right click: jump -" + jumpPages + " pages")
                        .onLeftClick((e) -> openGui(player, currentPage - 1))
                        .onRightClick((e) -> openGui(player, jumpBack)));
                w.setElement(-3, navRow, new UIElement("adapt-first")
                        .setMaterial(new MaterialBlock(Material.LECTERN))
                        .setName(C.GRAY + "First")
                        .onLeftClick((e) -> openGui(player, 0)));
            }
            if (currentPage < plan.pageCount() - 1) {
                w.setElement(4, navRow, new UIElement("adapt-next")
                        .setMaterial(new MaterialBlock(Material.ARROW))
                        .setName(C.WHITE + "Next")
                        .addLore(C.GRAY + "Right click: jump +" + jumpPages + " pages")
                        .onLeftClick((e) -> openGui(player, currentPage + 1))
                        .onRightClick((e) -> openGui(player, jumpForward)));
                w.setElement(3, navRow, new UIElement("adapt-last")
                        .setMaterial(new MaterialBlock(Material.LECTERN))
                        .setName(C.GRAY + "Last")
                        .onLeftClick((e) -> openGui(player, plan.pageCount() - 1)));
            }

            int from = getMaxLevel() <= 0 ? 0 : (start + 1);
            int to = getMaxLevel() <= 0 ? 0 : end;
            w.setElement(-1, navRow, new UIElement("adapt-page-info")
                    .setMaterial(new MaterialBlock(Material.PAPER))
                    .setName(C.AQUA + "Page " + (currentPage + 1) + "/" + plan.pageCount())
                    .addLore(C.GRAY + "Showing " + from + "-" + to + " of " + getMaxLevel())
                    .setProgress(1D));

            if (AdaptConfig.get().isGuiBackButton()) {
                w.setElement(0, navRow, new UIElement("back")
                        .setMaterial(new MaterialBlock(Material.ARROW))
                        .setName("" + C.RESET + C.GRAY + Localizer.dLocalize("snippets.gui.back"))
                        .onLeftClick((e) -> onGuiClose(player, true)));
            }

        }

        AdaptPlayer a = Adapt.instance.getAdaptServer().getPlayer(player);
        String pageSuffix = plan.pageCount() > 1 ? " [" + (currentPage + 1) + "/" + plan.pageCount() + "]" : "";
        w.setTitle(getDisplayName() + " " + C.DARK_GRAY + " " + Form.f(a.getSkillLine(getSkill().getName()).getKnowledge()) + " " + Localizer.dLocalize("snippets.adapt_menu.knowledge") + pageSuffix);
        w.onClosed((vv) -> J.s(() -> onGuiClose(player, !AdaptConfig.get().isEscClosesAllGuis())));
        w.open();
        Adapt.instance.getGuiLeftovers().put(player.getUniqueId().toString(), w);
    }

    private void onGuiClose(Player player, boolean openPrevGui) {
        SoundPlayer spw = SoundPlayer.of(player.getWorld());
        spw.play(player.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 1.1f, 1.255f);
        spw.play(player.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 0.7f, 0.655f);
        spw.play(player.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 0.3f, 0.855f);
        if (openPrevGui) {
            getSkill().openGui(player);
        } else {
            Adapt.instance.getGuiLeftovers().remove(player.getUniqueId().toString());
        }
    }

    private static String permanentConfirmPrefix(Player player, Adaptation<?> adaptation) {
        return player.getUniqueId() + "|" + adaptation.getName() + "|";
    }

    private static String permanentConfirmKey(Player player, Adaptation<?> adaptation, int level) {
        return permanentConfirmPrefix(player, adaptation) + level;
    }

    private static boolean isPermanentLearnConfirmationPending(Player player, Adaptation<?> adaptation, int level) {
        if (player == null || adaptation == null) {
            return false;
        }

        Long until = PERMANENT_LEARN_CONFIRMATIONS.get(permanentConfirmKey(player, adaptation, level));
        return until != null && until >= M.ms();
    }

    default boolean isPermanentLearnConfirmationPending(Player player, int level) {
        return isPermanentLearnConfirmationPending(player, this, level);
    }

    default boolean consumePermanentLearnConfirmation(Player player, int level) {
        if (player == null) {
            return false;
        }

        long now = M.ms();
        PERMANENT_LEARN_CONFIRMATIONS.entrySet().removeIf(e -> e.getValue() < now);

        String key = permanentConfirmKey(player, this, level);
        Long until = PERMANENT_LEARN_CONFIRMATIONS.get(key);
        if (until != null && until >= now) {
            PERMANENT_LEARN_CONFIRMATIONS.remove(key);
            return true;
        }

        String prefix = permanentConfirmPrefix(player, this);
        PERMANENT_LEARN_CONFIRMATIONS.keySet().removeIf(existing -> existing.startsWith(prefix));
        PERMANENT_LEARN_CONFIRMATIONS.put(key, now + PERMANENT_LEARN_CONFIRM_WINDOW_MS);
        return false;
    }

    default void unlearn(Player player, int lvl, boolean force) {
        if (isPermanent() && !force) {
            return;
        }
        int myLevel = getPlayer(player).getSkillLine(getSkill().getName()).getAdaptationLevel(getName());
        int rc = getRefundCostFor(lvl - 1, myLevel);
        if (!AdaptConfig.get().isHardcoreNoRefunds()) {
            getPlayer(player).getData().getSkillLine(getSkill().getName()).giveKnowledge(rc);
        }
        getPlayer(player).getData().getSkillLine(getSkill().getName()).setAdaptation(this, lvl - 1);
    }

    default void learn(Player player, int lvl, boolean force) {
        int myLevel = getPlayer(player).getSkillLine(getSkill().getName()).getAdaptationLevel(getName());
        int c = getCostFor(lvl, myLevel);
        if (getPlayer(player).getData().hasPowerAvailable(c) || force) {
            if (getPlayer(player).getData().getSkillLine(getSkill().getName()).spendKnowledge(c) || force) {
                getPlayer(player).getData().getSkillLine(getSkill().getName()).setAdaptation(this, lvl);
            }
        }
    }

    default boolean isAdaptationRecipe(Recipe recipe) {
        if (!this.isEnabled()) {
            return false;
        }
        if (!this.getSkill().isEnabled()) {
            return false;
        }
        for (AdaptRecipe i : getRecipes()) {
            if (i.is(recipe)) {
                return true;
            }
        }
        return false;
    }
}
