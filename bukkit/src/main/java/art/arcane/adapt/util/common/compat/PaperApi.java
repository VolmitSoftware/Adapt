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

package art.arcane.adapt.util.common.compat;

import art.arcane.adapt.Adapt;
import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.Tool;
import io.papermc.paper.registry.RegistryKey;
import io.papermc.paper.registry.TypedKey;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.util.TriState;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.EntityEffect;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.OfflinePlayer;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.advancement.Advancement;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.BlockType;
import org.bukkit.block.DoubleChest;
import org.bukkit.block.Lidded;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.ExperienceOrb;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.TextDisplay;
import org.bukkit.entity.Zombie;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityDismountEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerItemDamageEvent;
import org.bukkit.event.world.TimeSkipEvent;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.projectiles.ProjectileSource;
import org.bukkit.util.Vector;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * The one place that decides whether the server has Paper's API. Every Paper-only call the Bukkit platform layer makes
 * goes through here or through a class that is only loaded after {@link #isPaper()} returned true; Spigot gets the
 * plain Bukkit equivalent, slower or coarser where Bukkit has nothing better.
 */
public final class PaperApi {
  private static final String MARKER_CLASS = "io.papermc.paper.registry.RegistryAccess";
  private static final boolean PAPER = PaperCompat.hasClass(MARKER_CLASS);
  private static final long NANOS_PER_TICK = 50_000_000L;
  private static final long DEEP_SLEEP_TICKS = 100L;
  private static final long DAY_END_TICK = 12_542L;
  private static final long DAY_START_TICK = 23_460L;
  private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.builder()
      .character('§')
      .hexColors()
      .useUnusualXRepeatedCharacterHexFormat()
      .build();
  private static final long START_NANOS = System.nanoTime();

  private PaperApi() {
  }

  public static boolean isPaper() {
    return PAPER;
  }

  /** The vanilla correct-for-drops answer of the stack's tool component: its first rule naming the block decides. */
  public static boolean isCorrectTool(ItemStack stack, BlockData data) {
    Tool tool = stack == null || stack.getType().isAir() ? null : stack.getData(DataComponentTypes.TOOL);
    if (tool == null) {
      return false;
    }
    TypedKey<BlockType> block = TypedKey.create(RegistryKey.BLOCK, data.getMaterial().getKey());
    for (Tool.Rule rule : tool.rules()) {
      if (rule.correctForDrops() != TriState.NOT_SET && rule.blocks().contains(block)) {
        return rule.correctForDrops() == TriState.TRUE;
      }
    }
    return false;
  }

  public static String minecraftVersion(Server server) {
    if (PAPER) {
      return server.getMinecraftVersion();
    }
    String bukkit = server.getBukkitVersion();
    int dash = bukkit.indexOf('-');
    return dash < 0 ? bukkit : bukkit.substring(0, dash);
  }

  /** Spigot has no tick counter, so there it counts 50 ms steps of wall time since this class loaded. */
  public static long currentTick(Server server) {
    if (PAPER) {
      return server.getCurrentTick();
    }
    return (System.nanoTime() - START_NANOS) / NANOS_PER_TICK;
  }

  public static double recentTps() {
    if (!PAPER) {
      return 20.0D;
    }
    double[] tps = Bukkit.getTPS();
    return tps == null || tps.length == 0 ? 20.0D : tps[0];
  }

  public static OfflinePlayer cachedPlayer(Server server, String name) {
    if (PAPER) {
      return server.getOfflinePlayerIfCached(name);
    }
    Player online = server.getPlayerExact(name);
    if (online != null) {
      return online;
    }
    for (OfflinePlayer known : server.getOfflinePlayers()) {
      if (name.equalsIgnoreCase(known.getName())) {
        return known;
      }
    }
    return null;
  }

  public static World world(Server server, NamespacedKey key) {
    if (PAPER) {
      return server.getWorld(key);
    }
    for (World world : server.getWorlds()) {
      if (world.getKey().equals(key)) {
        return world;
      }
    }
    return null;
  }

  public static ItemStack emptyItem() {
    return PAPER ? ItemStack.empty() : new ItemStack(Material.AIR);
  }

  public static boolean isEmpty(ItemStack stack) {
    if (PAPER) {
      return stack.isEmpty();
    }
    return stack.getType().isAir() || stack.getAmount() <= 0;
  }

  public static RecipeChoice emptyRecipeChoice() {
    return PAPER ? RecipeChoice.empty() : null;
  }

  public static InventoryHolder holder(Inventory inventory) {
    return PAPER ? inventory.getHolder(false) : inventory.getHolder();
  }

  public static InventoryHolder leftSide(DoubleChest chest) {
    return PAPER ? chest.getLeftSide(false) : chest.getLeftSide();
  }

  public static boolean deathCancelled(EntityDeathEvent event) {
    return PAPER && event.isCancelled();
  }

  public static boolean dismountCancellable(EntityDismountEvent event) {
    return !PAPER || event.isCancellable();
  }

  public static Location interactionPoint(PlayerInteractEvent event) {
    return PAPER ? event.getInteractionPoint() : null;
  }

  public static ItemStack consumeReplacement(PlayerItemConsumeEvent event) {
    return PAPER ? event.getReplacement() : null;
  }

  public static void setConsumeReplacement(PlayerItemConsumeEvent event, ItemStack replacement) {
    if (PAPER) {
      event.setReplacement(replacement);
    }
  }

  public static int originalDamage(PlayerItemDamageEvent event) {
    return PAPER ? event.getOriginalDamage() : event.getDamage();
  }

  /** True when the server skipped the night because players slept. */
  public static boolean nightSkip(TimeSkipEvent event) {
    if (PAPER) {
      return event.getSkipReason() == TimeSkipEvent.SkipReason.NIGHT_SKIP;
    }
    return "NIGHT_SKIP".equals(enumName(invoke(event, "getSkipReason")));
  }

  public static boolean deeplySleeping(HumanEntity human) {
    if (PAPER) {
      return human.isDeeplySleeping();
    }
    return human.isSleeping() && human.getSleepTicks() >= DEEP_SLEEP_TICKS;
  }

  /** The advancement's frame name (TASK, GOAL or CHALLENGE), or null when it has no display. */
  public static String advancementFrame(Advancement advancement) {
    if (PAPER) {
      io.papermc.paper.advancement.AdvancementDisplay display = advancement.getDisplay();
      return display == null ? null : display.frame().name();
    }
    Object display = invoke(advancement, "getDisplay");
    return display == null ? null : enumName(invoke(display, "getType"));
  }

  public static boolean isDayTime(World world) {
    if (PAPER) {
      return world.isDayTime();
    }
    long time = world.getTime();
    return time < DAY_END_TICK || time >= DAY_START_TICK;
  }

  public static boolean isInLava(Entity entity) {
    if (PAPER) {
      return entity.isInLava();
    }
    return entity.getLocation().getBlock().getType() == Material.LAVA;
  }

  public static boolean isSolid(Block block) {
    return PAPER ? block.isSolid() : block.getType().isSolid();
  }

  /** Spigot cannot read a lid's state, so a lid reads as closed there. */
  public static boolean isOpen(Lidded lidded) {
    return PAPER && lidded.isOpen();
  }

  public static Collection<BlockState> tileEntities(Chunk chunk) {
    if (PAPER) {
      return chunk.getTileEntities((Block block) -> true, false);
    }
    return List.of(chunk.getTileEntities());
  }

  /** Spawns with the custom spawn reason on Paper; Spigot cannot pick the reason here. */
  public static Entity spawnEntity(World world, Location location, EntityType type, Consumer<Entity> prepare) {
    if (PAPER) {
      return world.spawnEntity(location, type, CreatureSpawnEvent.SpawnReason.CUSTOM, prepare);
    }
    Class<? extends Entity> entityClass = type.getEntityClass();
    return entityClass == null ? null : world.spawn(location, entityClass, prepare);
  }

  public static void setOrbCount(ExperienceOrb orb, int count) {
    if (PAPER) {
      orb.setCount(count);
    }
  }

  /** Paper's flying fall damage flag; Spigot has none and always reports {@link TriState#NOT_SET}. */
  public static TriState flyingFallDamage(Player player) {
    return PAPER ? player.hasFlyingFallDamage() : TriState.NOT_SET;
  }

  public static void setFlyingFallDamage(Player player, TriState state) {
    if (PAPER) {
      player.setFlyingFallDamage(state);
    }
  }

  public static void heal(LivingEntity living, double amount) {
    if (PAPER) {
      living.heal(amount);
      return;
    }
    AttributeInstance max = living.getAttribute(Attribute.MAX_HEALTH);
    double ceiling = max == null ? living.getHealth() : max.getValue();
    living.setHealth(Math.max(0.0D, Math.min(ceiling, living.getHealth() + amount)));
  }

  public static AttributeModifier modifier(AttributeInstance instance, NamespacedKey key) {
    if (PAPER) {
      return instance.getModifier(key);
    }
    for (AttributeModifier modifier : instance.getModifiers()) {
      if (key.equals(modifier.getKey())) {
        return modifier;
      }
    }
    return null;
  }

  public static void removeModifier(AttributeInstance instance, NamespacedKey key) {
    if (PAPER) {
      instance.removeModifier(key);
      return;
    }
    AttributeModifier existing = modifier(instance, key);
    if (existing != null) {
      instance.removeModifier(existing);
    }
  }

  public static void setDropChance(EntityEquipment equipment, EquipmentSlot slot, float chance) {
    if (PAPER) {
      equipment.setDropChance(slot, chance);
      return;
    }
    switch (slot) {
      case HAND -> equipment.setItemInMainHandDropChance(chance);
      case OFF_HAND -> equipment.setItemInOffHandDropChance(chance);
      case HEAD -> equipment.setHelmetDropChance(chance);
      case CHEST -> equipment.setChestplateDropChance(chance);
      case LEGS -> equipment.setLeggingsDropChance(chance);
      case FEET -> equipment.setBootsDropChance(chance);
      default -> throw new UnsupportedOperationException("No drop chance for " + slot);
    }
  }

  /** Spigot applies the damage directly, without unbreaking or the break event. */
  public static void damageItem(LivingEntity living, EquipmentSlot slot, int amount) {
    if (PAPER) {
      living.damageItemStack(slot, amount);
      return;
    }
    EntityEquipment equipment = living.getEquipment();
    ItemStack item = equipment == null ? null : equipment.getItem(slot);
    if (item == null || item.getType().isAir() || !(item.getItemMeta() instanceof Damageable damageable)) {
      return;
    }
    int max = damageable.hasMaxDamage() ? damageable.getMaxDamage() : item.getType().getMaxDurability();
    if (max <= 0) {
      return;
    }
    int damage = damageable.getDamage() + amount;
    if (damage >= max) {
      equipment.setItem(slot, null);
      breakEffect(living, slot);
      return;
    }
    damageable.setDamage(damage);
    item.setItemMeta(damageable);
    equipment.setItem(slot, item);
  }

  public static void breakEffect(LivingEntity living, EquipmentSlot slot) {
    if (PAPER) {
      living.broadcastSlotBreak(slot);
      return;
    }
    EntityEffect effect = switch (slot) {
      case HAND -> EntityEffect.BREAK_EQUIPMENT_MAIN_HAND;
      case OFF_HAND -> EntityEffect.BREAK_EQUIPMENT_OFF_HAND;
      case HEAD -> EntityEffect.BREAK_EQUIPMENT_HELMET;
      case CHEST -> EntityEffect.BREAK_EQUIPMENT_CHESTPLATE;
      case LEGS -> EntityEffect.BREAK_EQUIPMENT_LEGGINGS;
      case FEET -> EntityEffect.BREAK_EQUIPMENT_BOOTS;
      default -> null;
    };
    if (effect != null) {
      living.playEffect(effect);
    }
  }

  public static void lookAt(Mob mob, Location target) {
    if (PAPER) {
      mob.lookAt(target);
      return;
    }
    Location eye = mob.getEyeLocation();
    Vector direction = target.toVector().subtract(eye.toVector());
    if (direction.lengthSquared() < 1.0E-6D) {
      return;
    }
    Location facing = eye.setDirection(direction);
    mob.setRotation(facing.getYaw(), facing.getPitch());
  }

  public static void setShouldBurnInDay(Zombie zombie, boolean burns) {
    if (PAPER) {
      zombie.setShouldBurnInDay(burns);
    }
  }

  public static UUID shooterId(Projectile projectile) {
    if (PAPER) {
      UUID owner = projectile.getOwnerUniqueId();
      if (owner != null) {
        return owner;
      }
    }
    ProjectileSource shooter = projectile.getShooter();
    return shooter instanceof Entity entity ? entity.getUniqueId() : null;
  }

  /** Spigot cannot run code before the projectile spawns, so {@code beforeSpawn} runs right after it. */
  public static Projectile launchProjectile(Player player, Class<? extends Projectile> type, Consumer<Projectile> beforeSpawn) {
    if (PAPER) {
      return player.<Projectile>launchProjectile(type, null, beforeSpawn);
    }
    Projectile projectile = player.launchProjectile(type);
    beforeSpawn.accept(projectile);
    return projectile;
  }

  /** Spigot keys cooldowns by item type only, so a group that is not an item id reads as idle there. */
  public static int cooldown(Player player, NamespacedKey group) {
    if (PAPER) {
      return player.getCooldown(group);
    }
    Material material = Material.matchMaterial(group.toString());
    return material == null || !material.isItem() ? 0 : player.getCooldown(material);
  }

  public static void setCooldown(Player player, NamespacedKey group, int ticks) {
    if (PAPER) {
      player.setCooldown(group, ticks);
      return;
    }
    Material material = Material.matchMaterial(group.toString());
    if (material != null && material.isItem()) {
      player.setCooldown(material, ticks);
    }
  }

  /** Sends {@code message} as Paper's own component, so styles Spigot's chat components lack (text shadow colour) arrive. */
  public static void sendActionBar(Player player, Component message) {
    player.sendActionBar(message);
  }

  public static void showBossBar(Player player, BossBar bar) {
    if (PAPER) {
      player.showBossBar(bar);
    } else {
      Adapt.platform.getAudienceProvider().player(player).showBossBar(bar);
    }
  }

  public static void hideBossBar(Player player, BossBar bar) {
    if (PAPER) {
      player.hideBossBar(bar);
    } else {
      Adapt.platform.getAudienceProvider().player(player).hideBossBar(bar);
    }
  }

  public static Component text(TextDisplay display) {
    if (PAPER) {
      return display.text();
    }
    String legacy = display.getText();
    return legacy == null ? Component.empty() : LEGACY.deserialize(legacy);
  }

  public static void setDisplayName(ItemMeta meta, Component name) {
    if (PAPER) {
      meta.displayName(name);
    } else {
      meta.setDisplayName(name == null ? null : LEGACY.serialize(name));
    }
  }

  public static List<Component> lore(ItemMeta meta) {
    if (PAPER) {
      return meta.lore();
    }
    List<String> legacy = meta.getLore();
    if (legacy == null) {
      return null;
    }
    List<Component> lines = new ArrayList<>(legacy.size());
    for (String line : legacy) {
      lines.add(LEGACY.deserialize(line));
    }
    return lines;
  }

  public static void setLore(ItemMeta meta, List<Component> lines) {
    if (PAPER) {
      meta.lore(lines);
      return;
    }
    if (lines == null) {
      meta.setLore(null);
      return;
    }
    List<String> legacy = new ArrayList<>(lines.size());
    for (Component line : lines) {
      legacy.add(LEGACY.serialize(line));
    }
    meta.setLore(legacy);
  }

  public static String effectiveName(ItemStack stack) {
    if (PAPER) {
      return LegacyComponentSerializer.legacySection().serialize(stack.effectiveName());
    }
    ItemMeta meta = stack.getItemMeta();
    if (meta != null && meta.hasItemName()) {
      return meta.getItemName();
    }
    String name = stack.getType().getKey().getKey().replace('_', ' ');
    StringBuilder words = new StringBuilder(name.length());
    for (String word : name.split(" ")) {
      if (!words.isEmpty()) {
        words.append(' ');
      }
      words.append(word.substring(0, 1).toUpperCase(Locale.ROOT)).append(word.substring(1));
    }
    return words.toString();
  }

  @SuppressWarnings("removal")
  public static Component potionName(PotionMeta meta) {
    if (PAPER) {
      return meta.customName();
    }
    return meta.hasCustomName() ? LEGACY.deserialize(meta.getCustomName()) : null;
  }

  @SuppressWarnings("removal")
  public static void setPotionName(PotionMeta meta, Component name) {
    if (PAPER) {
      meta.customName(name);
    } else {
      meta.setCustomName(name == null ? null : LEGACY.serialize(name));
    }
  }

  /** Paper writes the item's NBT; Spigot writes a Bukkit object stream, which Paper cannot read back. */
  public static byte[] serialize(ItemStack stack) {
    if (PAPER) {
      return stack.serializeAsBytes();
    }
    try (ByteArrayOutputStream buffer = new ByteArrayOutputStream();
         BukkitObjectOutputStream out = new BukkitObjectOutputStream(buffer)) {
      out.writeObject(stack);
      out.flush();
      return buffer.toByteArray();
    } catch (IOException error) {
      throw new IllegalStateException("Item could not be written", error);
    }
  }

  public static ItemStack deserialize(byte[] data) {
    if (PAPER) {
      return ItemStack.deserializeBytes(data);
    }
    try (ByteArrayInputStream buffer = new ByteArrayInputStream(data);
         BukkitObjectInputStream in = new BukkitObjectInputStream(buffer)) {
      return (ItemStack) in.readObject();
    } catch (IOException | ClassNotFoundException error) {
      throw new IllegalStateException("Item could not be read", error);
    }
  }

  public static com.google.common.collect.Multimap<Attribute, AttributeModifier> defaultModifiers(Material type) {
    if (PAPER) {
      return type.getDefaultAttributeModifiers();
    }
    com.google.common.collect.Multimap<Attribute, AttributeModifier> merged = com.google.common.collect.LinkedHashMultimap.create();
    for (EquipmentSlot slot : EquipmentSlot.values()) {
      merged.putAll(type.getDefaultAttributeModifiers(slot));
    }
    return merged;
  }

  public static ItemStack enchantWithLevels(ItemStack stack, int levels, boolean treasure, Random random) {
    if (PAPER) {
      return Bukkit.getItemFactory().enchantWithLevels(stack, levels, treasure, random);
    }
    return Bukkit.getItemFactory().enchantItem(stack, levels, treasure);
  }

  public static boolean canPlayerPickup(org.bukkit.entity.Item item) {
    return !PAPER || item.canPlayerPickup();
  }

  /** Paper only; callers on Spigot use the platform's level formula instead. */
  public static int totalExperience(Player player) {
    return player.calculateTotalExperiencePoints();
  }

  /** Paper only; callers on Spigot use the platform's level formula instead. */
  public static void setExperience(Player player, int total) {
    player.setExperienceLevelAndProgress(total);
  }

  private static Object invoke(Object target, String name) {
    try {
      return target.getClass().getMethod(name).invoke(target);
    } catch (ReflectiveOperationException | RuntimeException unreadable) {
      return null;
    }
  }

  private static String enumName(Object value) {
    return value instanceof Enum<?> constant ? constant.name() : null;
  }
}
