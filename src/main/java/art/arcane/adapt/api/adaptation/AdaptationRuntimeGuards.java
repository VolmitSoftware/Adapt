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

import art.arcane.adapt.Adapt;
import art.arcane.adapt.AdaptConfig;
import art.arcane.adapt.api.protection.Protector;
import art.arcane.adapt.api.telemetry.AbilityCheckTelemetry;
import art.arcane.adapt.api.world.AdaptPlayer;
import art.arcane.adapt.api.world.PlayerAdaptation;
import art.arcane.adapt.api.world.PlayerData;
import art.arcane.adapt.api.world.PlayerSkillLine;
import art.arcane.adapt.content.event.AdaptAdaptationUseEvent;
import art.arcane.adapt.util.common.scheduling.J;
import art.arcane.volmlib.util.math.M;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.Cancellable;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.inventory.ItemStack;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.IntConsumer;
import java.util.function.Predicate;

final class AdaptationRuntimeGuards {
    private static final Map<String, Long> USAGE_BASELINE_XP_COOLDOWNS = new ConcurrentHashMap<>();
    private static final Map<String, ActiveLevelCacheEntry> ACTIVE_LEVEL_CACHE = new ConcurrentHashMap<>();
    private static final Map<String, ProtectorCacheEntry> PROTECTOR_CACHE = new ConcurrentHashMap<>();
    private static final Map<String, UsageConflictCacheEntry> USAGE_CONFLICT_CACHE = new ConcurrentHashMap<>();
    private static final int ACTIVE_LEVEL_CACHE_SOFT_LIMIT = 16_384;
    private static final int ACTIVE_LEVEL_CACHE_SWEEP_INTERVAL_TICKS = 64;
    private static final int ACTIVE_LEVEL_CACHE_RETENTION_TICKS = 2;
    private static volatile long lastActiveCacheSweepTick = Long.MIN_VALUE;

    private AdaptationRuntimeGuards() {
    }

    static void withPlayerThread(Adaptation<?> adaptation, Player p, Runnable runnable) {
        try {
            if (p == null || runnable == null) {
                return;
            }

            if (J.isFoliaThreading() && !J.isOwnedByCurrentRegion(p)) {
                J.runEntity(p, () -> withPlayerThread(adaptation, p, runnable));
                return;
            }

            runnable.run();
        } catch (Exception ex) {
            Adapt.verbose("Failed guarded player runnable for adaptation " + adaptation.getName() + ": "
                    + ex.getClass().getSimpleName()
                    + (ex.getMessage() == null ? "" : " - " + ex.getMessage()));
        }
    }

    static void withPlayerThread(Adaptation<?> adaptation, Player p, Cancellable cancellable, Runnable runnable) {
        try {
            if (p == null || cancellable == null || runnable == null) {
                return;
            }

            if (cancellable.isCancelled()) {
                return;
            }

            if (J.isFoliaThreading() && !J.isOwnedByCurrentRegion(p)) {
                J.runEntity(p, () -> withPlayerThread(adaptation, p, cancellable, runnable));
                return;
            }

            if (cancellable.isCancelled()) {
                return;
            }

            runnable.run();
        } catch (Exception ex) {
            Adapt.verbose("Failed guarded cancellable player runnable for adaptation " + adaptation.getName() + ": "
                    + ex.getClass().getSimpleName()
                    + (ex.getMessage() == null ? "" : " - " + ex.getMessage()));
        }
    }

    static void withAdaptedPlayer(Adaptation<?> adaptation, Player p, Runnable runnable) {
        withPlayerThread(adaptation, p, () -> {
            if (!adaptation.hasActiveAdaptation(p)) {
                return;
            }
            runnable.run();
        });
    }

    static void withAdaptedPlayer(Adaptation<?> adaptation, Player p, Cancellable cancellable, Runnable runnable) {
        withPlayerThread(adaptation, p, cancellable, () -> {
            if (!adaptation.hasActiveAdaptation(p)) {
                return;
            }
            runnable.run();
        });
    }

    static void withActiveLevel(Adaptation<?> adaptation, Player p, IntConsumer consumer) {
        withPlayerThread(adaptation, p, () -> {
            int level = getActiveLevel(adaptation, p);
            if (level <= 0) {
                return;
            }
            consumer.accept(level);
        });
    }

    static void withActiveLevel(Adaptation<?> adaptation, Player p, Cancellable cancellable, IntConsumer consumer) {
        withPlayerThread(adaptation, p, cancellable, () -> {
            int level = getActiveLevel(adaptation, p);
            if (level <= 0) {
                return;
            }
            consumer.accept(level);
        });
    }

    static String adaptationRewardKey(Adaptation<?> adaptation, String rewardKey) {
        String suffix = rewardKey == null ? "" : rewardKey.trim();
        if (suffix.isEmpty()) {
            suffix = "use";
        }
        return "adaptation:" + adaptation.getName() + ":" + suffix;
    }

    static void awardUsageBaselineXp(Adaptation<?> adaptation, Player p, int level) {
        if (p == null || level <= 0 || !p.getClass().getSimpleName().equals("CraftPlayer")) {
            return;
        }

        AdaptConfig.AdaptationXp cfg = AdaptConfig.get().getAdaptationXp();
        if (cfg == null || !cfg.isUsageBaselineEnabled()) {
            return;
        }

        long now = M.ms();
        long cooldown = Math.max(250L, cfg.getUsageBaselineCooldownMillis());
        String key = p.getUniqueId() + "|" + adaptation.getName();
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
        adaptation.xpSilent(p, reward, "baseline-use");
    }

    static boolean canUse(Adaptation<?> adaptation, AdaptPlayer player) {
        Adapt.verbose("Checking if " + player.getPlayer().getName() + " can use " + adaptation.getName() + "...");
        AdaptAdaptationUseEvent e = new AdaptAdaptationUseEvent(!Bukkit.isPrimaryThread(), player, adaptation);
        Bukkit.getServer().getPluginManager().callEvent(e);
        return (!e.isCancelled());
    }

    static boolean canUse(Adaptation<?> adaptation, Player player) {
        return canUse(adaptation, adaptation.getPlayer(player));
    }

    static boolean hasBlacklistPermission(Adaptation<?> adaptation, Player p, Adaptation<?> targetAdaptation) {
        if (p.isOp()) {
            return false;
        }
        Adaptation<?> target = targetAdaptation == null ? adaptation : targetAdaptation;
        String blacklistPermission = "adapt.blacklist." + target.getName().replaceAll("-", "");
        Adapt.verbose("Checking if player " + p.getName() + " has blacklist permission " + blacklistPermission);

        return p.hasPermission(blacklistPermission);
    }

    static boolean canDamageTarget(Adaptation<?> adaptation, Player attacker, Entity target) {
        if (attacker == null || target == null) {
            return false;
        }

        if (target instanceof Player victim) {
            return adaptation.canPVP(attacker, victim.getLocation());
        }

        return adaptation.canPVE(attacker, target.getLocation());
    }

    static int getActiveSurvivalLevel(Adaptation<?> adaptation, Player player) {
        if (player == null || player.getGameMode() != GameMode.SURVIVAL) {
            return 0;
        }

        return getActiveLevel(adaptation, player);
    }

    static int getActiveLevel(Adaptation<?> adaptation, Player player, Predicate<Player> requirement) {
        int level = getActiveLevel(adaptation, player);
        if (level <= 0) {
            return 0;
        }

        if (requirement != null && !requirement.test(player)) {
            return 0;
        }

        return level;
    }

    static int getActiveSurvivalLevel(Adaptation<?> adaptation, Player player, Predicate<Player> requirement) {
        int level = getActiveSurvivalLevel(adaptation, player);
        if (level <= 0) {
            return 0;
        }

        if (requirement != null && !requirement.test(player)) {
            return 0;
        }

        return level;
    }

    static int getActiveInteractLevel(Adaptation<?> adaptation, Player player, Location location) {
        int level = getActiveLevel(adaptation, player);
        if (level <= 0 || location == null) {
            return 0;
        }

        return adaptation.canInteract(player, location) ? level : 0;
    }

    static int getActiveBlockBreakLevel(Adaptation<?> adaptation, Player player, Location location) {
        int level = getActiveLevel(adaptation, player);
        if (level <= 0 || location == null) {
            return 0;
        }

        return adaptation.canBlockBreak(player, location) ? level : 0;
    }

    static int getActiveBlockPlaceLevel(Adaptation<?> adaptation, Player player, Location location) {
        int level = getActiveLevel(adaptation, player);
        if (level <= 0 || location == null) {
            return 0;
        }

        return adaptation.canBlockPlace(player, location) ? level : 0;
    }

    static int getActiveDamageLevel(Adaptation<?> adaptation, Player attacker, Entity target) {
        int level = getActiveLevel(adaptation, attacker);
        if (level <= 0 || target == null) {
            return 0;
        }

        return canDamageTarget(adaptation, attacker, target) ? level : 0;
    }

    static Adaptation.BlockActionContext resolveInteractContext(Adaptation<?> adaptation, Player player, Location location) {
        return resolveInteractContext(adaptation, player, location, null, false);
    }

    static Adaptation.BlockActionContext resolveInteractContext(Adaptation<?> adaptation, Player player, Location location, Predicate<Player> requirement) {
        return resolveInteractContext(adaptation, player, location, requirement, false);
    }

    static Adaptation.BlockActionContext resolveInteractContext(Adaptation<?> adaptation, Player player, Location location, Predicate<Player> requirement, boolean survivalOnly) {
        int level = resolveActionLevel(adaptation, player, requirement, survivalOnly);
        if (level <= 0 || location == null || !adaptation.canInteract(player, location)) {
            return null;
        }

        return new Adaptation.BlockActionContext(player, location, level);
    }

    static Adaptation.BlockActionContext resolveBlockBreakContext(Adaptation<?> adaptation, Player player, Location location) {
        return resolveBlockBreakContext(adaptation, player, location, null, false);
    }

    static Adaptation.BlockActionContext resolveBlockBreakContext(Adaptation<?> adaptation, Player player, Location location, Predicate<Player> requirement) {
        return resolveBlockBreakContext(adaptation, player, location, requirement, false);
    }

    static Adaptation.BlockActionContext resolveBlockBreakContext(Adaptation<?> adaptation, Player player, Location location, Predicate<Player> requirement, boolean survivalOnly) {
        int level = resolveActionLevel(adaptation, player, requirement, survivalOnly);
        if (level <= 0 || location == null || !adaptation.canBlockBreak(player, location)) {
            return null;
        }

        return new Adaptation.BlockActionContext(player, location, level);
    }

    static Adaptation.BlockActionContext resolveBlockPlaceContext(Adaptation<?> adaptation, Player player, Location location) {
        return resolveBlockPlaceContext(adaptation, player, location, null, false);
    }

    static Adaptation.BlockActionContext resolveBlockPlaceContext(Adaptation<?> adaptation, Player player, Location location, Predicate<Player> requirement) {
        return resolveBlockPlaceContext(adaptation, player, location, requirement, false);
    }

    static Adaptation.BlockActionContext resolveBlockPlaceContext(Adaptation<?> adaptation, Player player, Location location, Predicate<Player> requirement, boolean survivalOnly) {
        int level = resolveActionLevel(adaptation, player, requirement, survivalOnly);
        if (level <= 0 || location == null || !adaptation.canBlockPlace(player, location)) {
            return null;
        }

        return new Adaptation.BlockActionContext(player, location, level);
    }

    static Adaptation.BlockActionContext resolveInteractBreakContext(Adaptation<?> adaptation, Player player, Location location) {
        return resolveInteractBreakContext(adaptation, player, location, null, false);
    }

    static Adaptation.BlockActionContext resolveInteractBreakContext(Adaptation<?> adaptation, Player player, Location location, Predicate<Player> requirement) {
        return resolveInteractBreakContext(adaptation, player, location, requirement, false);
    }

    static Adaptation.BlockActionContext resolveInteractBreakContext(Adaptation<?> adaptation, Player player, Location location, Predicate<Player> requirement, boolean survivalOnly) {
        Adaptation.BlockActionContext context = resolveInteractContext(adaptation, player, location, requirement, survivalOnly);
        if (context == null || !adaptation.canBlockBreak(context.player(), context.location())) {
            return null;
        }

        return context;
    }

    static ItemStack readyMainHand(Adaptation<?> adaptation, Player player, Predicate<ItemStack> requirement) {
        if (player == null) {
            return null;
        }

        ItemStack hand = player.getInventory().getItemInMainHand();
        if (!adaptation.isItem(hand)) {
            return null;
        }

        if (requirement != null && (!requirement.test(hand) || player.hasCooldown(hand.getType()))) {
            return null;
        }

        return hand;
    }

    static Adaptation.MeleeContext resolveMeleeContext(Adaptation<?> adaptation, EntityDamageByEntityEvent event, Predicate<ItemStack> mainHandRequirement) {
        Adaptation.AttackContext attack = resolveAttackContext(adaptation, event, mainHandRequirement);
        if (attack == null || !(attack.target() instanceof LivingEntity target)) {
            return null;
        }

        return new Adaptation.MeleeContext(attack.attacker(), target, attack.mainHand(), attack.level());
    }

    static Adaptation.AttackContext resolveAttackContext(Adaptation<?> adaptation, EntityDamageByEntityEvent event, Predicate<ItemStack> mainHandRequirement) {
        if (event == null || !(event.getDamager() instanceof Player attacker)) {
            return null;
        }

        int level = getActiveLevel(adaptation, attacker);
        if (level <= 0) {
            return null;
        }

        ItemStack hand = readyMainHand(adaptation, attacker, mainHandRequirement);
        if (mainHandRequirement != null && hand == null) {
            return null;
        }

        Entity target = event.getEntity();
        if (!canDamageTarget(adaptation, attacker, target)) {
            return null;
        }

        return new Adaptation.AttackContext(attacker, target, hand, level);
    }

    static Adaptation.ProjectileContext resolveProjectileContext(Adaptation<?> adaptation, EntityDamageByEntityEvent event, Predicate<Projectile> projectileRequirement) {
        if (event == null
                || !(event.getDamager() instanceof Projectile projectile)
                || !(projectile.getShooter() instanceof Player attacker)
                || !(event.getEntity() instanceof LivingEntity target)) {
            return null;
        }

        if (projectileRequirement != null && !projectileRequirement.test(projectile)) {
            return null;
        }

        int level = getActiveLevel(adaptation, attacker);
        if (level <= 0) {
            return null;
        }

        if (!canDamageTarget(adaptation, attacker, target)) {
            return null;
        }

        return new Adaptation.ProjectileContext(attacker, target, projectile, level);
    }

    static boolean hasUsageConflict(Adaptation<?> adaptation, Player p) {
        if (adaptation == null || p == null) {
            return false;
        }

        Set<String> denied = resolveUsageConflicts(adaptation);
        if (denied.isEmpty()) {
            return false;
        }

        AdaptPlayer adaptPlayer = adaptation.getPlayer(p);
        for (String conflict : denied) {
            if (adaptPlayer.hasAdaptation(conflict)) {
                Adapt.verbose("Player " + p.getName() + " has conflicting adaptation " + conflict + " and cannot use " + adaptation.getName());
                return true;
            }
        }

        return false;
    }

    static Set<Protector> getProtectors(Adaptation<?> adaptation) {
        if (adaptation == null) {
            return Collections.emptySet();
        }

        List<Protector> defaults = Adapt.instance.getProtectorRegistry().getDefaultProtectors();
        List<Protector> allProtectors = Adapt.instance.getProtectorRegistry().getAllProtectors();
        Map<String, Boolean> overrides = AdaptConfig.get().getProtectionOverrides().getOrDefault(adaptation.getName(), Collections.emptyMap());
        int signature = buildProtectorSignature(defaults, allProtectors, overrides);
        String cacheKey = adaptation.getName();
        ProtectorCacheEntry cached = PROTECTOR_CACHE.get(cacheKey);
        if (cached != null && cached.signature() == signature) {
            return cached.protectors();
        }

        Map<String, Protector> byName = new HashMap<>();
        for (Protector protector : allProtectors) {
            byName.put(protector.getName(), protector);
        }

        Set<Protector> resolved = new HashSet<>(defaults);
        for (Map.Entry<String, Boolean> entry : overrides.entrySet()) {
            String protectorName = entry.getKey();
            Boolean enabled = entry.getValue();
            if (protectorName == null || enabled == null) {
                continue;
            }

            if (enabled) {
                Protector protector = byName.get(protectorName);
                if (protector == null) {
                    Adapt.error("Could not find protector " + protectorName + " for adaptation " + adaptation.getName() + ". Skipping...");
                    continue;
                }
                resolved.add(protector);
                continue;
            }

            resolved.removeIf(existing -> existing.getName().equals(protectorName));
        }

        Set<Protector> immutable = Collections.unmodifiableSet(new HashSet<>(resolved));
        PROTECTOR_CACHE.put(cacheKey, new ProtectorCacheEntry(signature, immutable));
        return immutable;
    }

    static int getActiveLevel(Adaptation<?> adaptation, Player p) {
        try {
            if (p == null || p.isDead()) {
                return 0;
            }

            if (J.isFoliaThreading() && !J.isOwnedByCurrentRegion(p)) {
                return 0;
            }

            AbilityCheckTelemetry.recordCheckAttempt();
            long tick = M.tick;
            String key = cacheKey(adaptation, p);
            ActiveLevelCacheEntry cached = ACTIVE_LEVEL_CACHE.get(key);
            if (cached != null && cached.tick() == tick) {
                if (cached.level() > 0) {
                    AbilityCheckTelemetry.recordSuccessfulCheck();
                }
                return cached.level();
            }

            int level = resolveActiveLevelUncached(adaptation, p);
            ACTIVE_LEVEL_CACHE.put(key, new ActiveLevelCacheEntry(tick, level));
            sweepActiveLevelCache(tick);

            if (level > 0) {
                AbilityCheckTelemetry.recordSuccessfulCheck();
            }
            return level;
        } catch (Exception e) {
            if (e instanceof IndexOutOfBoundsException) {
                Adapt.verbose("Citizens/PacketSpoofing is Messing stuff up again. I hate it.");
                Adapt.verbose(e.getMessage());
            } else {
                e.printStackTrace();
            }
            return 0;
        }
    }

    static int getLevel(Adaptation<?> adaptation, Player p) {
        if (p == null) {
            return 0;
        }
        if (J.isFoliaThreading() && !J.isOwnedByCurrentRegion(p)) {
            return 0;
        }
        if (!p.getClass().getSimpleName().equals("CraftPlayer")) {
            Adapt.verbose("Simple name: " + p.getClass().getSimpleName());
            return 0;
        }
        if (!adaptation.isEnabled()) {
            return 0;
        }
        if (!adaptation.getSkill().isEnabled()) {
            return 0;
        }
        AdaptPlayer adaptPlayer = adaptation.getPlayer(p);
        PlayerSkillLine line = adaptPlayer.getData().getSkillLine(adaptation.getSkill().getName());
        if (line == null) {
            return 0;
        }
        return line.getAdaptationLevel(adaptation.getName());
    }

    static <F> F getStorage(Adaptation<?> adaptation, Player p, String key, F defaultValue) {
        PlayerData data = adaptation.getPlayer(p).getData();
        PlayerSkillLine line = data.getSkillLineNullable(adaptation.getSkill().getName());
        if (line == null) return defaultValue;
        PlayerAdaptation playerAdaptation = line.getAdaptation(adaptation.getName());
        if (playerAdaptation == null) return defaultValue;
        Object o = playerAdaptation.getStorage().get(key);
        return o == null ? defaultValue : (F) o;
    }

    static boolean setStorage(Adaptation<?> adaptation, Player p, String key, Object value) {
        PlayerData data = adaptation.getPlayer(p).getData();
        PlayerSkillLine line = data.getSkillLineNullable(adaptation.getSkill().getName());
        if (line == null) return false;
        PlayerAdaptation playerAdaptation = line.getAdaptation(adaptation.getName());
        if (playerAdaptation == null) return false;
        if (value == null) {
            playerAdaptation.getStorage().remove(key);
            return true;
        }

        playerAdaptation.getStorage().put(key, value);
        return true;
    }

    private static int resolveActionLevel(Adaptation<?> adaptation, Player player, Predicate<Player> requirement, boolean survivalOnly) {
        if (survivalOnly) {
            return getActiveSurvivalLevel(adaptation, player, requirement);
        }

        return getActiveLevel(adaptation, player, requirement);
    }

    private static int resolveActiveLevelUncached(Adaptation<?> adaptation, Player p) {
        int level = adaptation.getLevel(p);
        if (level <= 0) {
            return 0;
        }

        if (AdaptConfig.get().blacklistedWorlds.contains(p.getWorld().getName())) {
            Adapt.verbose("Player " + p.getName() + " is in a blacklisted world. Skipping adaptation " + adaptation.getName());
            return 0;
        }
        if (p.getGameMode().equals(GameMode.CREATIVE) || p.getGameMode().equals(GameMode.SPECTATOR)) {
            Adapt.verbose("Player " + p.getName() + " is in creative or spectator mode. Skipping adaptation " + adaptation.getName());
            return 0;
        }
        if (!adaptation.checkRegion(p)) {
            Adapt.verbose("Player " + p.getName() + " don't have adaptation - " + adaptation.getName() + " permission.");
            return 0;
        }

        if (adaptation.hasBlacklistPermission(p, adaptation)) {
            Adapt.verbose("Player " + p.getName() + " has blacklist permission for adaptation " + adaptation.getName());
            return 0;
        }
        if (hasUsageConflict(adaptation, p)) {
            return 0;
        }
        if (!adaptation.canUse(p)) {
            Adapt.verbose("Player " + p.getName() + " can't use adaptation, This is an API restriction" + adaptation.getName());
            return 0;
        }

        awardUsageBaselineXp(adaptation, p, level);
        Adapt.verbose("Player " + p.getName() + " used adaptation " + adaptation.getName());
        return level;
    }

    private static String cacheKey(Adaptation<?> adaptation, Player player) {
        return player.getUniqueId() + "|" + adaptation.getName();
    }

    private static void sweepActiveLevelCache(long tick) {
        if (ACTIVE_LEVEL_CACHE.size() <= ACTIVE_LEVEL_CACHE_SOFT_LIMIT) {
            return;
        }

        long previousSweep = lastActiveCacheSweepTick;
        if (previousSweep != Long.MIN_VALUE && tick - previousSweep < ACTIVE_LEVEL_CACHE_SWEEP_INTERVAL_TICKS) {
            return;
        }
        lastActiveCacheSweepTick = tick;

        long minTick = tick - ACTIVE_LEVEL_CACHE_RETENTION_TICKS;
        ACTIVE_LEVEL_CACHE.entrySet().removeIf(entry -> entry.getValue().tick() < minTick);
    }

    private static Set<String> resolveUsageConflicts(Adaptation<?> adaptation) {
        Map<String, List<String>> conflicts = AdaptConfig.get().getAdaptationUsageConflicts();
        if (conflicts == null || conflicts.isEmpty()) {
            return Collections.emptySet();
        }

        String me = adaptation.getName().toLowerCase(Locale.ROOT);
        int signature = buildUsageConflictSignature(conflicts);
        UsageConflictCacheEntry cached = USAGE_CONFLICT_CACHE.get(me);
        if (cached != null && cached.signature() == signature) {
            return cached.denied();
        }

        Set<String> denied = new HashSet<>();
        for (Map.Entry<String, List<String>> entry : conflicts.entrySet()) {
            String key = entry.getKey();
            List<String> values = entry.getValue();
            if (key == null || values == null) {
                continue;
            }

            if (key.equalsIgnoreCase(me)) {
                for (String value : values) {
                    if (value != null) {
                        denied.add(value.toLowerCase(Locale.ROOT));
                    }
                }
                continue;
            }

            boolean containsThisAdaptation = false;
            for (String value : values) {
                if (value != null && me.equals(value.toLowerCase(Locale.ROOT))) {
                    containsThisAdaptation = true;
                    break;
                }
            }
            if (containsThisAdaptation) {
                denied.add(key.toLowerCase(Locale.ROOT));
            }
        }

        denied.remove(me);
        Set<String> immutable = Collections.unmodifiableSet(new HashSet<>(denied));
        USAGE_CONFLICT_CACHE.put(me, new UsageConflictCacheEntry(signature, immutable));
        return immutable;
    }

    private static int buildUsageConflictSignature(Map<String, List<String>> conflicts) {
        int hash = 1;
        for (Map.Entry<String, List<String>> entry : conflicts.entrySet()) {
            String key = entry.getKey();
            List<String> values = entry.getValue();
            int local = 0;
            if (key != null) {
                local += key.toLowerCase(Locale.ROOT).hashCode();
            }
            if (values != null) {
                local += values.size() * 17;
                for (String value : values) {
                    if (value != null) {
                        local += value.toLowerCase(Locale.ROOT).hashCode();
                    }
                }
            }
            hash += local;
        }
        return hash;
    }

    private static int buildProtectorSignature(List<Protector> defaults, List<Protector> allProtectors, Map<String, Boolean> overrides) {
        int hash = 1;
        hash += defaults.size() * 31;
        for (Protector protector : defaults) {
            hash += protector.getName().hashCode();
        }
        hash += allProtectors.size() * 17;
        for (Protector protector : allProtectors) {
            hash += protector.getName().hashCode();
        }
        hash += overrides.size() * 13;
        for (Map.Entry<String, Boolean> entry : overrides.entrySet()) {
            String name = entry.getKey();
            Boolean enabled = entry.getValue();
            if (name != null) {
                hash += name.hashCode();
            }
            if (enabled != null) {
                hash += enabled ? 1 : 2;
            }
        }
        return hash;
    }

    private record ActiveLevelCacheEntry(long tick, int level) {
    }

    private record ProtectorCacheEntry(int signature, Set<Protector> protectors) {
    }

    private record UsageConflictCacheEntry(int signature, Set<String> denied) {
    }
}
