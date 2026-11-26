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

package art.arcane.adapt.content.adaptation.crafting;

import art.arcane.adapt.AdaptCore;
import art.arcane.adapt.api.adaptation.Adaptation;
import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.adaptation.StatLines;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.attribute.Attributes;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.fx.Particles;
import art.arcane.adapt.api.fx.Sounds;
import art.arcane.adapt.api.item.ItemDurability;
import art.arcane.adapt.api.mutation.upgrade.UpgradeLoans;
import art.arcane.adapt.api.preference.CommonPreferences;
import art.arcane.adapt.api.preference.PlayerPreference;
import art.arcane.adapt.api.support.PlatformNeed;
import art.arcane.adapt.localization.AdaptLanguage;
import art.arcane.adapt.localization.catalog.CraftingMessages;
import art.arcane.adapt.platform.EngineFeature;
import art.arcane.adapt.platform.RegistryKind;
import art.arcane.adapt.platform.data.DataCodecs;
import art.arcane.adapt.platform.data.DataKey;
import art.arcane.adapt.platform.entity.AttributeMod;
import art.arcane.adapt.platform.entity.AttributeOperation;
import art.arcane.adapt.platform.entity.EquipmentSlotGroup;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.inventory.CraftItemEvent;
import art.arcane.adapt.platform.event.inventory.ItemCraftedEvent;
import art.arcane.adapt.platform.event.inventory.PrepareItemCraftEvent;
import art.arcane.adapt.platform.item.ItemOps;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.menu.ClickKind;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.scheduling.Tasks;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.volmlib.util.format.Form;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.DoubleSupplier;

import static art.arcane.volmlib.util.localization.MessageArgument.trusted;

public class CraftingMasterwork extends SimpleAdaptation<CraftingMasterwork.Config> {
  public static final PlayerPreference<CommonPreferences.Toggle> DURABILITY = CommonPreferences.toggle("durability", CraftingMessages.PREFERENCE_CRAFTINGMASTERWORK_DURABILITY, CommonPreferences.Toggle.ON);
  public static final PlayerPreference<CommonPreferences.Toggle> ENCHANTMENTS = CommonPreferences.toggle("enchantments", CraftingMessages.PREFERENCE_CRAFTINGMASTERWORK_ENCHANTMENTS, CommonPreferences.Toggle.ON);
  public static final PlayerPreference<CommonPreferences.Toggle> ATTRIBUTES = CommonPreferences.toggle("attributes", CraftingMessages.PREFERENCE_CRAFTINGMASTERWORK_ATTRIBUTES, CommonPreferences.Toggle.ON);

  private static final byte MASTERWORK_FLAG = 1;
  private static final byte ATTRIBUTE_FLAG = 2;
  private static final byte ENCHANT_FLAG = 4;
  private static final byte CONVERTED_FLAG = 8;
  private static final Set<String> BENEFICIAL_ENCHANTMENTS = Set.of(
      "protection",
      "fire_protection",
      "feather_falling",
      "blast_protection",
      "projectile_protection",
      "respiration",
      "aqua_affinity",
      "depth_strider",
      "sharpness",
      "smite",
      "bane_of_arthropods",
      "knockback",
      "fire_aspect",
      "looting",
      "sweeping_edge",
      "efficiency",
      "unbreaking",
      "fortune"
  );

  private static final Id ATTRIBUTE_KEY = Id.adapt("masterwork_bonus");
  private static final DataKey<Byte> MASTERWORK_KEY = DataKey.adapt("masterwork_roll", DataCodecs.BYTE);
  private static final DataKey<Integer> REFORGES_KEY = DataKey.adapt("masterwork_reforges", DataCodecs.INT);
  private static final DataKey<Integer> BONUS_KEY = DataKey.adapt("masterwork_durability", DataCodecs.INT);
  private final Map<UUID, ShiftBatch> shiftBatches = new ConcurrentHashMap<>();
  private final Set<UUID> pendingSingleMasterworks = ConcurrentHashMap.newKeySet();

  public CraftingMasterwork() {
    super("crafting-masterwork");
    needs(PlatformNeed.emulatedFeature(EngineFeature.ITEM_STACK_LIMITS));
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("netherite_ingot"));
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("iron_chestplate"))
        .key("challenge_crafting_masterwork_50")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .child(AdaptAdvancement.builder()
            .icon(Id.minecraft("netherite_chestplate"))
            .key("challenge_crafting_masterwork_500")
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.VANILLA)
            .build())
        .build());
    registerMilestone("challenge_crafting_masterwork_50", "crafting.masterwork.pieces-forged", 50, 400);
    registerMilestone("challenge_crafting_masterwork_500", "crafting.masterwork.pieces-forged", 500, 1500);
  }

  @Override
  public void unregister() {
    shiftBatches.clear();
    pendingSingleMasterworks.clear();
    super.unregister();
  }


  @Override
  public List<PlayerPreference<?>> getPlayerPreferences() {
    return List.of(CommonPreferences.ENABLED, DURABILITY, ENCHANTMENTS, ATTRIBUTES);
  }

  @Override
  public void addStats(int level, StatLines v) {
    statLore(v, Form.pc(getRollChance(level), 0), 1);
    statLore(v, Form.pc(getBonusPercent(level), 0), 2);
    statLore(v, Form.pc(clampChance(getConfig().enchantmentChance), 0), 3);
    v.addLore(C.LIGHT_PURPLE + "~ " + C.GRAY + AdaptLanguage.text(CraftingMessages.MASTERWORK_LORE4));
  }

  static double masterworkChance(double base, double factor, double max, double levelPercent) {
    return clampChance(Math.min(max, base + (levelPercent * factor)));
  }

  static double bonusPercent(double base, double factor, double levelPercent) {
    return Math.max(0.0D, base + (levelPercent * factor));
  }

  static double rolledBonusPercent(double maximum, double minimumFraction, double randomUnit) {
    double safeMaximum = Math.max(0.0D, maximum);
    double safeMinimumFraction = Math.max(0.0D, Math.min(1.0D, minimumFraction));
    double safeRandomUnit = Math.max(0.0D, Math.min(1.0D, randomUnit));
    double minimum = safeMaximum * safeMinimumFraction;
    return minimum + ((safeMaximum - minimum) * safeRandomUnit);
  }

  static int bonusDurability(int baseMaxDurability, double bonusPercent) {
    return Math.max(1, (int) Math.round(Math.max(1, baseMaxDurability) * bonusPercent));
  }

  static double clampChance(double chance) {
    return Math.max(0.0D, Math.min(1.0D, chance));
  }

  private double getRollChance(int level) {
    return masterworkChance(getConfig().rollChanceBase, getConfig().rollChanceFactor, getConfig().rollChanceMax, getLevelPercent(level));
  }

  private double getBonusPercent(int level) {
    return bonusPercent(getConfig().bonusPercentBase, getConfig().bonusPercentFactor, getLevelPercent(level));
  }

  @Override
  public int getActiveLevel(PlayerRef p) {
    return UpgradeLoans.level(this, super.getActiveLevel(p), (Adaptation<?> upgrade) -> upgrade.getActiveLevel(p));
  }

  @Handle(priority = EventPriority.HIGHEST)
  public void on(CraftItemEvent e) {
    PlayerRef p = e.player();
    UUID playerId = p.uuid();
    shiftBatches.remove(playerId);
    pendingSingleMasterworks.remove(playerId);

    int level = getActiveLevel(p);
    if (level <= 0) {
      return;
    }

    if (e.recipeKind() != CraftItemEvent.RecipeKind.SHAPED && e.recipeKind() != CraftItemEvent.RecipeKind.SHAPELESS) {
      return;
    }

    ItemRef result = e.currentItem();
    if (result == null || result.isEmpty() || result.type().isAir()) {
      return;
    }

    boolean tool = isTool(result.type());
    boolean armor = isArmor(result.type());
    if (!tool && !armor) {
      return;
    }

    ShiftBatch batch = null;
    if (e.click() == ClickKind.SHIFT_LEFT || e.click() == ClickKind.SHIFT_RIGHT) {
      Id recipeKey = e.recipeKey();
      if (recipeKey == null) {
        return;
      }
      ShiftBatch createdBatch = new ShiftBatch(new ShiftBatchSpec(result.type(), recipeKey, level), result);
      batch = createdBatch;
      shiftBatches.put(playerId, createdBatch);
      Tasks.s(() -> shiftBatches.remove(playerId, createdBatch), 1);
    }

    ItemRef forged = forge(p, result, level, tool);
    boolean masterwork = forged != result;
    if (batch != null) {
      batch.expectMasterwork(masterwork);
    } else if (masterwork) {
      pendingSingleMasterworks.add(playerId);
      Tasks.s(() -> pendingSingleMasterworks.remove(playerId), 1);
    }
    if (forged != result) {
      e.setCurrentItem(forged);
    }
  }

  @Handle(priority = EventPriority.HIGHEST)
  public void on(PrepareItemCraftEvent e) {
    PlayerRef p = e.player();

    ShiftBatch batch = shiftBatches.get(p.uuid());
    ItemRef result = e.result();
    if (batch == null || !batch.matches(e.recipeKind(), e.recipeKey(), result)) {
      return;
    }

    ItemRef prepared = batch.getPreparedResult();
    if (prepared == null && batch.isAwaitingNextResult()) {
      ItemRef baseResult = batch.createBaseResult(result.amount());
      boolean tool = isTool(baseResult.type());
      prepared = forge(p, baseResult, batch.getLevel(), tool);
      batch.cachePreparedResult(prepared, prepared != baseResult);
    }
    if (prepared != null) {
      e.setResult(prepared.copy());
    }
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(ItemCraftedEvent e) {
    PlayerRef p = e.player();
    ItemRef crafted = e.crafted();
    ShiftBatch batch = shiftBatches.get(p.uuid());
    boolean expectedMasterwork;
    if (batch != null && batch.matchesType(crafted)) {
      expectedMasterwork = batch.consumeExpectedMasterwork();
      batch.awaitNextResult();
    } else {
      expectedMasterwork = pendingSingleMasterworks.remove(p.uuid());
    }

    if (!expectedMasterwork) {
      return;
    }
    if (crafted == null || crafted.isEmpty()) {
      return;
    }
    Byte flags = crafted.data().get(MASTERWORK_KEY);
    if (flags == null || (flags & MASTERWORK_FLAG) == 0) {
      return;
    }

    addStat(p, "crafting.masterwork.pieces-forged", 1);
    UpgradeLoans.landed(this, p);
    if (batch == null || batch.claimEffect()) {
      playForgeEffect(p, (flags & ATTRIBUTE_FLAG) != 0, (flags & ENCHANT_FLAG) != 0);
    }
  }

  private ItemRef forge(PlayerRef player, ItemRef result, int level, boolean tool) {
    double strength = UpgradeLoans.strength(this, player);
    if (ThreadLocalRandom.current().nextDouble() >= clampChance(getRollChance(level) * strength)) {
      return result;
    }

    ItemRef forged = result.copy();
    int baseMax = Math.max(1, items().maxDurability(forged.type()));
    double rolledPercent = rolledBonusPercent(
        getBonusPercent(level) * strength,
        getConfig().bonusRollMinimumFraction,
        ThreadLocalRandom.current().nextDouble()
    );
    int bonus = preferenceEnabled(player, DURABILITY) ? bonusDurability(baseMax, rolledPercent) : 0;
    ItemDurability.setMaxDamage(forged, baseMax + bonus);

    boolean gotAttribute = level >= getMaxLevel()
        && ThreadLocalRandom.current().nextDouble() < clampChance(getConfig().attributeChance * strength)
        && preferenceEnabled(player, ATTRIBUTES) && applyAttribute(forged, tool);
    boolean gotEnchant = ThreadLocalRandom.current().nextDouble() < clampChance(getConfig().enchantmentChance * strength)
        && preferenceEnabled(player, ENCHANTMENTS) && applyBeneficialEnchant(forged);

    List<String> lore = new ArrayList<>(forged.lore());
    lore.add(bonusLine(bonus));
    if (gotAttribute) {
      lore.add(edgeLine());
    }
    forged.setLore(lore);
    byte flags = MASTERWORK_FLAG;
    if (gotAttribute) {
      flags |= ATTRIBUTE_FLAG;
    }
    if (gotEnchant) {
      flags |= ENCHANT_FLAG;
    }
    forged.data().set(MASTERWORK_KEY, flags);
    forged.data().set(BONUS_KEY, bonus);
    return forged;
  }

  /** Which part of a masterwork piece a reforge keeps. */
  public enum ReforgeLock {
    NONE, DURABILITY, EDGE
  }

  /**
   * {@code gear} reforged with the player's Masterwork: the bonus durability rerolls and, at full level, the Masterwork
   * Edge rerolls, unless {@code lock} keeps one of them. Remaining durability is kept, enchantments are never added or
   * taken. Plain gear reforges once to gain a bonus and never again. Null when Masterwork does not run for the player,
   * the item is not gear or it was plain gear already reforged.
   */
  public ItemRef reforge(PlayerRef player, ItemRef gear, ReforgeLock lock, DoubleSupplier random) {
    int level = getActiveLevel(player);
    boolean tool = isTool(gear.type());
    if (level <= 0 || (!tool && !isArmor(gear.type())) || items().maxDurability(gear.type()) <= 0
        || (flags(gear) & CONVERTED_FLAG) != 0) {
      return null;
    }
    double strength = UpgradeLoans.strength(this, player);
    ItemRef reforged = gear.copy();
    int baseMax = Math.max(1, items().maxDurability(reforged.type()));
    int oldMax = ItemDurability.maxDamage(reforged);
    byte flags = flags(reforged);
    boolean masterwork = (flags & MASTERWORK_FLAG) != 0;
    int oldBonus = masterwork ? bonusDurabilityOf(reforged) : 0;
    boolean hadEdge = (flags & ATTRIBUTE_FLAG) != 0;

    double rolledPercent = rolledBonusPercent(getBonusPercent(level) * strength, getConfig().bonusRollMinimumFraction,
        random.getAsDouble());
    int bonus = lock == ReforgeLock.DURABILITY && masterwork ? oldBonus
        : preferenceEnabled(player, DURABILITY) ? bonusDurability(baseMax, rolledPercent) : 0;
    boolean edgeRoll = random.getAsDouble() < clampChance(getConfig().attributeChance * strength);
    boolean edge = lock == ReforgeLock.EDGE ? hadEdge
        : level >= getMaxLevel() && edgeRoll && preferenceEnabled(player, ATTRIBUTES);

    int remaining = Math.max(1, oldMax - reforged.damage());
    int newMax = Math.max(1, oldMax - oldBonus) + bonus;
    ItemDurability.setMaxDamage(reforged, newMax);
    if (ItemDurability.perStack()) {
      reforged.setDamage(Math.max(0, newMax - Math.min(remaining, newMax)));
    }
    if (hadEdge && !edge) {
      reforged.removeAttributeModifier(tool ? Attributes.ATTACK_DAMAGE : Attributes.ARMOR, ATTRIBUTE_KEY);
    } else if (edge && !hadEdge) {
      applyAttribute(reforged, tool);
    }

    List<String> lore = new ArrayList<>(reforged.lore());
    if (masterwork) {
      lore.remove(bonusLine(oldBonus));
    }
    if (hadEdge) {
      lore.remove(edgeLine());
    }
    lore.add(bonusLine(bonus));
    if (edge) {
      lore.add(edgeLine());
    }
    reforged.setLore(lore);
    byte reforgedFlags = (byte) (MASTERWORK_FLAG | (flags & ENCHANT_FLAG) | (edge ? ATTRIBUTE_FLAG : 0)
        | (masterwork ? 0 : CONVERTED_FLAG));
    reforged.data().set(MASTERWORK_KEY, reforgedFlags);
    reforged.data().set(BONUS_KEY, bonus);
    reforged.data().set(REFORGES_KEY, reforges(gear) + 1);
    return reforged;
  }

  /** A piece's Masterwork bonus: its bonus durability as a share of the base durability, and whether it has the Edge. */
  public record Bonus(double durabilityShare, boolean edge) {
  }

  /** The Masterwork bonus of {@code gear}, or null when it has none. */
  public static @Nullable Bonus bonus(ItemRef gear) {
    if (gear == null || gear.isEmpty() || (flags(gear) & MASTERWORK_FLAG) == 0) {
      return null;
    }
    int baseMax = items().maxDurability(gear.type());
    if (baseMax <= 0) {
      return null;
    }
    return new Bonus(bonusDurabilityOf(gear) / (double) baseMax, (flags(gear) & ATTRIBUTE_FLAG) != 0);
  }

  /**
   * A copy of {@code gear} without its Masterwork bonus, keeping its enchantments and remaining durability, and marked
   * so a reforge never rolls it a new one; null when it has no bonus.
   */
  public static @Nullable ItemRef strip(ItemRef gear) {
    if (bonus(gear) == null) {
      return null;
    }
    ItemRef stripped = gear.copy();
    int oldMax = ItemDurability.maxDamage(stripped);
    int oldBonus = bonusDurabilityOf(stripped);
    int newMax = Math.max(1, oldMax - oldBonus);
    int remaining = Math.max(1, oldMax - stripped.damage());
    byte flags = flags(stripped);
    ItemDurability.setMaxDamage(stripped, newMax);
    if (ItemDurability.perStack()) {
      stripped.setDamage(Math.max(0, newMax - Math.min(remaining, newMax)));
    }
    List<String> lore = new ArrayList<>(stripped.lore());
    lore.remove(bonusLine(oldBonus));
    if ((flags & ATTRIBUTE_FLAG) != 0) {
      stripped.removeAttributeModifier(isTool(stripped.type()) ? Attributes.ATTACK_DAMAGE : Attributes.ARMOR, ATTRIBUTE_KEY);
      lore.remove(edgeLine());
    }
    stripped.setLore(lore);
    stripped.data().set(MASTERWORK_KEY, (byte) ((flags & ENCHANT_FLAG) | CONVERTED_FLAG));
    stripped.data().remove(BONUS_KEY);
    return stripped;
  }

  /**
   * A copy of {@code gear} carrying {@code bonus}, scaled to its own base durability; null when it is not tool or armor
   * gear with durability, or already has a Masterwork bonus.
   */
  public @Nullable ItemRef apply(ItemRef gear, Bonus bonus) {
    if (gear == null || gear.isEmpty() || bonus(gear) != null || (!isTool(gear.type()) && !isArmor(gear.type()))
        || items().maxDurability(gear.type()) <= 0) {
      return null;
    }
    ItemRef applied = gear.copy();
    boolean tool = isTool(applied.type());
    int baseMax = items().maxDurability(applied.type());
    int oldMax = ItemDurability.maxDamage(applied);
    int remaining = Math.max(1, oldMax - applied.damage());
    int extra = (int) Math.round(Math.max(0D, bonus.durabilityShare()) * baseMax);
    int newMax = oldMax + extra;
    ItemDurability.setMaxDamage(applied, newMax);
    if (ItemDurability.perStack()) {
      applied.setDamage(Math.max(0, newMax - Math.min(remaining, newMax)));
    }
    List<String> lore = new ArrayList<>(applied.lore());
    lore.add(bonusLine(extra));
    if (bonus.edge()) {
      applyAttribute(applied, tool);
      lore.add(edgeLine());
    }
    applied.setLore(lore);
    byte flags = flags(applied);
    applied.data().set(MASTERWORK_KEY, (byte) (MASTERWORK_FLAG | (flags & (ENCHANT_FLAG | CONVERTED_FLAG))
        | (bonus.edge() ? ATTRIBUTE_FLAG : 0)));
    applied.data().set(BONUS_KEY, extra);
    return applied;
  }

  /** A seed fixed by the piece and its reforge count, so the same piece always offers the same rolls. */
  public static long reforgeSeed(ItemRef gear) {
    return Objects.hash(gear.type().toString(), reforges(gear), flags(gear), ItemDurability.maxDamage(gear),
        gear.enchantments());
  }

  /** The Masterwork Edge line and bonus durability of a piece, for a reforge preview. */
  public static String describe(ItemRef gear) {
    int bonus = bonusDurabilityOf(gear);
    return (flags(gear) & ATTRIBUTE_FLAG) != 0 ? bonusLine(bonus) + " " + edgeLine() : bonusLine(bonus);
  }

  /**
   * The bonus durability Masterwork rolled onto a piece, as recorded on it; for a piece rolled before that record, all
   * durability above the base. 0 without a Masterwork roll.
   */
  private static int bonusDurabilityOf(ItemRef gear) {
    if ((flags(gear) & MASTERWORK_FLAG) == 0) {
      return 0;
    }
    Integer recorded = gear.data().get(BONUS_KEY);
    return recorded != null ? Math.max(0, recorded)
        : Math.max(0, ItemDurability.maxDamage(gear) - items().maxDurability(gear.type()));
  }

  private static int reforges(ItemRef gear) {
    Integer count = gear.data().get(REFORGES_KEY);
    return count == null ? 0 : count;
  }

  private static byte flags(ItemRef gear) {
    Byte flags = gear.data().get(MASTERWORK_KEY);
    return flags == null ? 0 : flags;
  }

  private static String bonusLine(int bonus) {
    return C.AQUA + AdaptLanguage.text(CraftingMessages.MASTERWORK_BONUS, trusted("bonus", C.GRAY + String.valueOf(bonus)));
  }

  private static String edgeLine() {
    return C.LIGHT_PURPLE + AdaptLanguage.text(CraftingMessages.MASTERWORK_ATTRIBUTE_TAG);
  }

  private boolean applyAttribute(ItemRef item, boolean tool) {
    Id attribute = tool ? Attributes.ATTACK_DAMAGE : Attributes.ARMOR;
    double amount = tool ? getConfig().attackDamageBonus : getConfig().armorBonus;
    EquipmentSlotGroup slot = tool ? EquipmentSlotGroup.MAINHAND : EquipmentSlotGroup.ARMOR;
    item.setAttributeModifier(attribute, new AttributeMod(ATTRIBUTE_KEY, amount, AttributeOperation.ADD_VALUE), slot);
    return true;
  }

  private boolean applyBeneficialEnchant(ItemRef item) {
    List<Id> candidates = new ArrayList<>();
    for (Id enchantment : AdaptCore.platform().host().registries().ids(RegistryKind.ENCHANTMENT)) {
      if (!enchantment.isMinecraft()
          || !BENEFICIAL_ENCHANTMENTS.contains(enchantment.path())
          || !items().canEnchant(enchantment, item)
          || conflictsWithExisting(item, enchantment)) {
        continue;
      }
      candidates.add(enchantment);
    }
    if (candidates.isEmpty()) {
      return false;
    }

    Id selected = candidates.get(ThreadLocalRandom.current().nextInt(candidates.size()));
    item.setEnchantment(selected, Math.max(1, items().enchantmentMinLevel(selected)));
    return true;
  }

  private boolean conflictsWithExisting(ItemRef item, Id candidate) {
    for (Id existing : item.enchantments().keySet()) {
      if (items().enchantmentsConflict(existing, candidate)) {
        return true;
      }
    }
    return false;
  }

  private void playForgeEffect(PlayerRef p, boolean gotAttribute, boolean gotEnchant) {
    float chimePitch = gotAttribute ? 1.9F : gotEnchant ? 1.65F : 1.4F;
    fx(p.location().add(0, 1, 0), FxPriority.TRANSITION)
        .burst(Particles.CRIT_MAGIC, 8, 0.2D)
        .particle(Particles.ENCHANTMENT_TABLE, 10, 0, 0.3D, 0, 0.4D, 0.4D)
        .chord(Sounds.BLOCK_ANVIL_USE, 0.5F, 1.3F, Sounds.BLOCK_AMETHYST_BLOCK_CHIME, 0.5F, chimePitch);
  }

  public static boolean isTool(Id type) {
    String name = type.path().toUpperCase(Locale.ROOT);
    return name.endsWith("_PICKAXE") || name.endsWith("_AXE") || name.endsWith("_SHOVEL")
        || name.endsWith("_HOE") || name.endsWith("_SWORD");
  }

  public static boolean isArmor(Id type) {
    String name = type.path().toUpperCase(Locale.ROOT);
    return name.endsWith("_HELMET") || name.endsWith("_CHESTPLATE")
        || name.endsWith("_LEGGINGS") || name.endsWith("_BOOTS");
  }

  private static ItemOps items() {
    return AdaptCore.platform().host().items();
  }

  @ConfigDescription("Each crafted tool and armor piece independently rolls variable bonus durability, a minor beneficial enchantment, and a full-level attribute bonus.")
  protected static class Config extends AdaptationConfig {
    @art.arcane.adapt.util.config.ConfigDoc(value = "Chance to roll a masterwork piece at level 1.", impact = "Higher values roll bonus durability more often at low levels.")
    double rollChanceBase = 0.2;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Additional masterwork roll chance gained across levels.", impact = "Higher values scale the roll chance more steeply.")
    double rollChanceFactor = 0.55;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Maximum masterwork roll chance at full level.", impact = "Higher values raise the ceiling on the roll chance.")
    double rollChanceMax = 0.75;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Fraction of base durability granted as a bonus at level 1.", impact = "Higher values grant more bonus durability even at low levels.")
    double bonusPercentBase = 0.1;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Additional bonus durability fraction gained across levels.", impact = "Higher values scale the durability bonus more steeply.")
    double bonusPercentFactor = 0.4;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Minimum fraction of the level-scaled durability bonus rolled by a masterwork piece.", impact = "Higher values make masterwork durability rolls more consistent and closer to their maximum.")
    double bonusRollMinimumFraction = 0.5;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Chance for a masterwork piece to gain one compatible level-one beneficial enchantment.", impact = "Higher values add minor beneficial enchantments to masterwork gear more often.")
    double enchantmentChance = 0.1;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Chance for a minor attribute bonus at full level.", impact = "Higher values roll the extra attribute bonus more often.")
    double attributeChance = 0.15;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Attack damage granted by a masterwork tool attribute bonus.", impact = "Higher values make masterwork tools hit harder.")
    double attackDamageBonus = 1.0;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Armor granted by a masterwork armor attribute bonus.", impact = "Higher values make masterwork armor more protective.")
    double armorBonus = 1.0;

    public Config() {
      baseCost = 3;
      costFactor = 0.6;
      maxLevel = 5;
      initialCost = 2;
    }
  }

  static final class ShiftBatch {
    private final ShiftBatchSpec spec;
    private final ItemRef baseResult;
    private ItemRef preparedResult;
    private boolean awaitingNextResult;
    private boolean expectedMasterwork;
    private boolean effectClaimed;

    ShiftBatch(ShiftBatchSpec spec, ItemRef baseResult) {
      this.spec = spec;
      this.baseResult = baseResult.copy();
    }

    int getLevel() {
      return spec.level();
    }

    boolean matches(CraftItemEvent.RecipeKind kind, Id recipeKey, ItemRef result) {
      if (result == null || result.isEmpty() || !result.type().equals(spec.resultType())) {
        return false;
      }
      if (kind != CraftItemEvent.RecipeKind.SHAPED && kind != CraftItemEvent.RecipeKind.SHAPELESS) {
        return false;
      }
      return spec.recipeKey().equals(recipeKey);
    }

    boolean matchesType(ItemRef item) {
      return item != null && !item.isEmpty() && item.type().equals(spec.resultType());
    }

    ItemRef createBaseResult(int amount) {
      ItemRef created = baseResult.copy();
      created.setAmount(amount);
      return created;
    }

    void awaitNextResult() {
      preparedResult = null;
      awaitingNextResult = true;
    }

    boolean isAwaitingNextResult() {
      return awaitingNextResult;
    }

    void cachePreparedResult(ItemRef result, boolean masterwork) {
      preparedResult = result.copy();
      awaitingNextResult = false;
      expectedMasterwork = masterwork;
    }

    ItemRef getPreparedResult() {
      return preparedResult == null ? null : preparedResult.copy();
    }

    void expectMasterwork(boolean masterwork) {
      expectedMasterwork = masterwork;
    }

    boolean consumeExpectedMasterwork() {
      boolean expected = expectedMasterwork;
      expectedMasterwork = false;
      return expected;
    }

    boolean claimEffect() {
      if (effectClaimed) {
        return false;
      }
      effectClaimed = true;
      return true;
    }
  }

  record ShiftBatchSpec(Id resultType, Id recipeKey, int level) {
  }
}
