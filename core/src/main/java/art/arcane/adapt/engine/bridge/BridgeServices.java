package art.arcane.adapt.engine.bridge;

import art.arcane.adapt.AdaptConfig;
import art.arcane.adapt.AdaptCore;
import art.arcane.adapt.api.AdaptSettings;
import art.arcane.adapt.AdaptPorts;
import art.arcane.adapt.api.content.Adaptation;
import art.arcane.adapt.api.content.Cooldowns;
import art.arcane.adapt.api.content.Skill;
import art.arcane.adapt.api.hud.HudGlyph;
import art.arcane.adapt.api.hud.Toast;
import art.arcane.adapt.api.internal.HudRuntime;
import art.arcane.adapt.api.internal.ItemRuntime;
import art.arcane.adapt.api.internal.MutationRuntime;
import art.arcane.adapt.api.internal.TextRuntime;
import art.arcane.adapt.api.item.CustomItem;
import art.arcane.adapt.api.mutation.BlockSlot;
import art.arcane.adapt.api.mutation.BlockUse;
import art.arcane.adapt.api.mutation.FuelBlock;
import art.arcane.adapt.api.mutation.LingeringSound;
import art.arcane.adapt.api.mutation.MutationBlock;
import art.arcane.adapt.api.mutation.MutationCue;
import art.arcane.adapt.api.mutation.MutationKit;
import art.arcane.adapt.api.mutation.MutationSlot;
import art.arcane.adapt.api.mutation.MutationTool;
import art.arcane.adapt.api.mutation.Recipe;
import art.arcane.adapt.api.mutation.Signature;
import art.arcane.adapt.api.mutation.SkillPair;
import art.arcane.adapt.api.mutation.SkillUpgrade;
import art.arcane.adapt.api.mutation.SoundPalette;
import art.arcane.adapt.api.mutation.TriggerBlock;
import art.arcane.adapt.api.text.Text;
import art.arcane.adapt.client.protocol.ToastKind;
import art.arcane.adapt.client.server.hud.AbilityGlyphs;
import art.arcane.adapt.client.server.hud.ClientToasts;
import art.arcane.adapt.engine.adaptation.AdaptationHost;
import art.arcane.adapt.api.item.ItemCooldowns;
import art.arcane.adapt.api.item.MaterialValues;
import art.arcane.adapt.engine.adaptation.ItemCooldownGroupPort;
import art.arcane.adapt.engine.adaptation.PlayerStateRegistry;
import art.arcane.adapt.engine.item.ItemDurability;
import art.arcane.adapt.engine.potion.AdaptPotionRegistry;
import art.arcane.adapt.engine.value.MaterialCount;
import art.arcane.adapt.engine.value.MaterialRecipe;
import art.arcane.adapt.engine.value.MaterialValuePort;
import art.arcane.adapt.engine.addon.BuildScope;
import art.arcane.adapt.engine.mutation.MutatedSkill;
import art.arcane.adapt.engine.mutation.engine.FarmRules;
import art.arcane.adapt.engine.mutation.engine.MutationAdaptation;
import art.arcane.adapt.engine.mutation.engine.MutationBlocks;
import art.arcane.adapt.engine.mutation.engine.TriggerEvent;
import art.arcane.adapt.engine.mutation.kit.Kits;
import art.arcane.adapt.engine.mutation.sound.SoundStage;
import art.arcane.adapt.engine.mutation.tool.MutationTools;
import art.arcane.adapt.engine.mutation.upgrade.Lending;
import art.arcane.adapt.engine.mutation.upgrade.SkillUpgrades;
import art.arcane.adapt.engine.mutation.upgrade.UpgradeXp;
import art.arcane.adapt.engine.notification.AdaptHud;
import art.arcane.adapt.engine.skill.LegacyContentNames;
import art.arcane.adapt.engine.skill.SkillHost;
import art.arcane.adapt.engine.skill.SkillRegistry;
import art.arcane.adapt.engine.world.AdaptPlayer;
import art.arcane.adapt.engine.world.AdaptServer;
import art.arcane.adapt.localization.AdaptLanguage;
import art.arcane.adapt.localization.MarkupPort;
import art.arcane.adapt.platform.data.DataKey;
import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.world.BlockRef;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.misc.CustomModel;
import art.arcane.adapt.util.common.misc.ItemLooks;
import art.arcane.volmlib.util.localization.MessageArgs;
import art.arcane.volmlib.util.localization.MessageArgument;
import art.arcane.volmlib.util.localization.MessageKey;
import art.arcane.volmlib.util.localization.PluralKey;
import org.jetbrains.annotations.Nullable;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.function.ToIntFunction;

/** The per-core runtimes the bridge hands out that do not belong to one component. */
final class BridgeServices {
  private final AdaptCore core;
  private final TextRuntime text = new Texts();
  private final HudRuntime hud = new Hud();
  private final ItemRuntime items = new Items();
  private final MutationRuntime mutations = new Mutations();
  private final AdaptSettings settings = new Settings();

  BridgeServices(AdaptCore core) {
    this.core = Objects.requireNonNull(core, "core");
  }

  TextRuntime text() {
    return text;
  }

  HudRuntime hud() {
    return hud;
  }

  ItemRuntime items() {
    return items;
  }

  MutationRuntime mutations() {
    return mutations;
  }

  AdaptSettings settings() {
    return settings;
  }

  static String resolve(Text text) {
    return resolve(text, null);
  }

  /** {@code text} in {@code locale}, nested texts included; a null locale is the language of the current render. */
  static String resolve(Text text, @Nullable String locale) {
    if (text.isLiteral()) {
      return text.literal();
    }
    MessageKey key = AdaptLanguage.key(text.key());
    String selector = key instanceof PluralKey plural ? plural.selectorArgument() : null;
    MessageArgs.Builder arguments = MessageArgs.builder();
    for (Text.Argument argument : text.arguments()) {
      Object value = argument.value();
      String shown = value instanceof Text nested ? resolve(nested, locale) : String.valueOf(value);
      Object passed = argument.name().equals(selector) ? quantity(shown) : shown;
      arguments.add(argument.trusted() ? MessageArgument.trusted(argument.name(), passed)
          : MessageArgument.untrusted(argument.name(), passed));
    }
    MessageArgs fitted = TextArguments.fit(key, arguments.build());
    return locale == null ? AdaptLanguage.text(key, fitted) : AdaptLanguage.text(locale, key, fitted);
  }

  /** The number a plural message picks its form by; a value that is not a number counts as zero. */
  private static Number quantity(String shown) {
    try {
      return new BigDecimal(shown.strip());
    } catch (NumberFormatException notNumeric) {
      return 0;
    }
  }

  private static final class Texts implements TextRuntime {
    @Override
    public String resolve(Text text) {
      return BridgeServices.resolve(text);
    }

    @Override
    public String resolve(Text text, PlayerRef viewer) {
      return text.isLiteral() ? text.literal()
          : art.arcane.volmlib.util.localization.LanguageAudience.call(viewer.uuid(), () -> BridgeServices.resolve(text));
    }

    @Override
    public String resolve(Text text, String locale) {
      return BridgeServices.resolve(text, locale);
    }

    @Override
    public <T> T renderFor(@Nullable UUID viewer, Supplier<T> action) {
      return art.arcane.volmlib.util.localization.LanguageAudience.call(viewer, action);
    }

    @Override
    public String markupLegacy(String markup) {
      return MarkupPort.current().legacy(markup);
    }

    @Override
    public String markupEscape(String text) {
      return MarkupPort.current().escapeTags(text);
    }

    @Override
    public String markupFromJson(String json) {
      return MarkupPort.current().jsonToMarkup(json);
    }
  }

  private static final class Hud implements HudRuntime {
    @Override
    public void actionBar(PlayerRef player, @Nullable Adaptation<?> source, String message) {
      AdaptHud.actionBar(player, message);
    }

    @Override
    public void ambient(PlayerRef player, String purpose, String message) {
      AdaptHud.ambientStatus(player, purpose, message);
    }

    @Override
    public void clearAmbient(PlayerRef player, String purpose) {
      AdaptHud.clearAmbientStatus(player, purpose);
    }

    @Override
    public boolean glyph(UUID player, Adaptation<?> source, HudGlyph glyph, int lit, int max, long startedAtMillis,
                         long durationMillis, boolean on) {
      return switch (glyph) {
        case COOLDOWN -> AbilityGlyphs.cooldown(player, source, startedAtMillis, durationMillis);
        case CHARGES -> AbilityGlyphs.charges(player, source, lit, max, startedAtMillis, durationMillis);
        case DURATION -> AbilityGlyphs.duration(player, source, startedAtMillis, durationMillis, lit, max);
        case TOGGLE -> AbilityGlyphs.toggle(player, source, on);
      };
    }

    @Override
    public void clearGlyph(UUID player, Adaptation<?> source, HudGlyph glyph) {
      AbilityGlyphs.clear(player, source, art.arcane.adapt.client.protocol.HudGlyph.valueOf(glyph.name()));
    }

    @Override
    public boolean capable(UUID player) {
      return AbilityGlyphs.capable(player);
    }

    @Override
    public void toast(PlayerRef player, Toast toast) {
      String title = BridgeServices.resolve(toast.title());
      String body = BridgeServices.resolve(toast.body());
      boolean sent = ClientToasts.capable(player.uuid()) && ClientToasts.send(player.uuid(),
          ClientToasts.toast(ToastKind.valueOf(toast.kind().name()), ClientToasts.icon(toast.icon().item(), null),
              title, body, toast.skill().map(Id::toString).orElse(null)));
      if (!sent) {
        AdaptHud.actionBar(player, title);
      }
    }
  }

  private final class Items implements ItemRuntime {
    private final Map<Id, ConvertedRecipes> materialRecipes = new ConcurrentHashMap<>();

    @Override
    public Cooldowns cooldowns(@Nullable Adaptation<?> owner) {
      return owner == null ? PlayerStateRegistry.newCooldowns() : PlayerStateRegistry.newCooldowns(owner);
    }

    @Override
    public boolean stampCooldownGroup(ItemRef stack, Id group, float markerSeconds) {
      return AdaptPorts.get(ItemCooldownGroupPort.class, ItemCooldownGroupPort.NONE).stampGroup(stack, group, markerSeconds);
    }

    @Override
    public void recordAppliedEffect(UUID player, Id effect) {
      AdaptPotionRegistry.record(player, effect);
    }

    @Override
    public List<MaterialValues.Recipe> materialRecipes(Id material) {
      List<MaterialRecipe> source = AdaptPorts.get(MaterialValuePort.class, MaterialValuePort.NONE).recipesFor(material);
      ConvertedRecipes cached = materialRecipes.get(material);
      if (cached != null && cached.source() == source) {
        return cached.recipes();
      }
      List<MaterialValues.Recipe> recipes = new ArrayList<>(source.size());
      for (MaterialRecipe recipe : source) {
        MaterialValues.Recipe converted = convert(recipe);
        if (converted != null) {
          recipes.add(converted);
        }
      }
      ConvertedRecipes fresh = new ConvertedRecipes(source, List.copyOf(recipes));
      materialRecipes.put(material, fresh);
      return fresh.recipes();
    }

    private static @Nullable MaterialValues.Recipe convert(MaterialRecipe recipe) {
      MaterialValues.Count output = count(recipe.getOutput());
      List<MaterialCount> inputs = recipe.getInput();
      if (output == null || inputs == null || recipe.getKind() == null) {
        return null;
      }
      List<MaterialValues.Count> counts = new ArrayList<>(inputs.size());
      for (MaterialCount input : inputs) {
        MaterialValues.Count count = count(input);
        if (count == null) {
          return null;
        }
        counts.add(count);
      }
      return new MaterialValues.Recipe(MaterialValues.Kind.valueOf(recipe.getKind().name()), counts, output);
    }

    private static @Nullable MaterialValues.Count count(@Nullable MaterialCount count) {
      return count == null || count.getMaterial() == null ? null : new MaterialValues.Count(count.getMaterial(), count.getAmount());
    }

    @Override
    public ItemRef modelled(Id fallback, List<String> path) {
      return CustomModel.get(fallback, path.toArray(String[]::new)).toItemStack();
    }

    @Override
    public long modelsRevision() {
      return CustomModel.generation();
    }

    @Override
    public void applyLook(ItemRef item, String key) {
      ItemLooks.apply(item, key);
    }

    @Override
    public boolean maxDurabilityPerStack() {
      return ItemDurability.perStack();
    }

    @Override
    public int maxDurability(ItemRef item) {
      return ItemDurability.maxDamage(item);
    }

    @Override
    public void setMaxDurability(ItemRef item, int maxDamage) {
      ItemDurability.setMaxDamage(item, maxDamage);
    }

    @Override
    public <D> ItemRef create(CustomItem.Definition<D> definition, D data, int amount) {
      ItemRef item = core.getPlatform().host().items().create(definition.baseItem(), Math.max(1, amount));
      setUp(definition, item, data);
      return item;
    }

    @Override
    public <D> void replace(CustomItem.Definition<D> definition, ItemRef item, D data) {
      item.clearComponents();
      setUp(definition, item, data);
    }

    private <D> void setUp(CustomItem.Definition<D> definition, ItemRef item, D data) {
      if (definition.maxStackSize() > 0) {
        item.setMaxStackSize(definition.maxStackSize());
      }
      ItemLooks.apply(item, LegacyContentNames.of(definition.id()));
      item.data().set(key(definition), data);
      definition.itemModel().ifPresent(item::setItemModel);
      definition.cooldownGroup().ifPresent((Id group) -> ItemCooldowns.stampGroup(item, group));
      label(definition, item);
    }

    @Override
    public boolean is(CustomItem.Definition<?> definition, ItemRef item) {
      return item != null && !item.isEmpty() && item.data().hasAny(definition.dataKey());
    }

    @Override
    public <D> @Nullable D read(CustomItem.Definition<D> definition, ItemRef item) {
      return is(definition, item) ? item.data().get(key(definition)) : null;
    }

    @Override
    public <D> boolean write(CustomItem.Definition<D> definition, ItemRef item, D data) {
      if (!is(definition, item)) {
        return false;
      }
      item.data().set(key(definition), data);
      label(definition, item);
      return true;
    }

    /**
     * Names the stack from the item's messages and sets its lore to the declared lines, so lore a mutation tool adds
     * for its data never piles up when the data is rewritten.
     */
    private static void label(CustomItem.Definition<?> definition, ItemRef item) {
      String name = ItemTexts.name(definition.id());
      if (name != null) {
        item.setDisplayName(C.AQUA + name);
      }
      List<String> lore = new ArrayList<>();
      for (String line : ItemTexts.lore(definition.id())) {
        lore.add(C.GRAY + line);
      }
      item.setLore(lore);
    }

    private <D> DataKey<D> key(CustomItem.Definition<D> definition) {
      return DataKey.of(definition.dataKey(), definition.codec());
    }
  }

  private record ConvertedRecipes(List<MaterialRecipe> source, List<MaterialValues.Recipe> recipes) {
  }

  private final class Mutations implements MutationRuntime {
    @Override
    public void fire(TriggerBlock<?> trigger, PlayerRef player, @Nullable EntityRef target, @Nullable Loc at,
                     boolean slain, boolean farmed) {
      SkillRegistry registry = registry();
      if (registry != null) {
        registry.getMutationEngine().fire(trigger, new TriggerEvent(player, target, at, slain, farmed));
      }
    }

    @Override
    public boolean fire(Signature<?> signature, PlayerRef player, @Nullable EntityRef target, @Nullable Loc at,
                        boolean farmed) {
      SkillRegistry registry = registry();
      return registry != null && registry.getMutationEngine().signature(signature, player, target, at, farmed);
    }

    @Override
    public String signatureOwner(MutationSlot slot) {
      return BuildScope.owner();
    }

    @Override
    public boolean parentsEnabled(SkillPair pair) {
      SkillRegistry registry = registry();
      if (registry == null) {
        return false;
      }
      Skill<?> lead = registry.getSkill(pair.lead());
      Skill<?> partner = registry.getSkill(pair.partner());
      return lead != null && partner != null && lead.isEnabled() && partner.isEnabled();
    }

    @Override
    public double overcharge(SkillUpgrade<?> upgrade, PlayerRef player) {
      SkillRegistry registry = registry();
      AdaptPlayer adaptPlayer = AdaptationHost.of(upgrade).readyPlayer(player);
      Skill<?> skill = upgrade.getSkill();
      SkillPair pair = adaptPlayer == null || skill == null || registry == null ? null
          : adaptPlayer.getData().mutationOf(SkillHost.lineKey(skill));
      if (pair == null) {
        return 1D;
      }
      MutationBlock<?> block = MutationBlocks.find(registry.getSkill(pair.other(skill.getId())), BlockSlot.FUEL);
      MutatedSkill mutated = registry.getMutatedSkill(pair);
      List<MutationAdaptation> own = mutated == null ? List.of() : mutated.getMutationAdaptations();
      if (!(block instanceof FuelBlock<?> fuel) || !fuel.usable() || own.isEmpty()) {
        return 1D;
      }
      MutationAdaptation carrier = own.getFirst();
      BlockUse use = new BlockUse(player, carrier, carrier.getSlot(), Math.max(1, upgrade.getLevel(player)), null,
          player.location(), skill.getId(), upgrade);
      if (!fuel.canPay(use) || !fuel.pay(use)) {
        return 1D;
      }
      return overchargeStrength();
    }

    @Override
    public List<Id> sources(SkillUpgrade<?> upgrade) {
      return Lending.sources(upgrade);
    }

    @Override
    public Set<Id> fuses(SkillUpgrade<?> upgrade) {
      return Lending.fuses(upgrade);
    }

    @Override
    public boolean farmed(PlayerRef player, EntityRef entity) {
      return FarmRules.farmed(player, entity);
    }

    @Override
    public boolean farmed(BlockRef block) {
      return FarmRules.farmed(block);
    }

    @Override
    public boolean farmedHarvest(BlockRef crop) {
      return FarmRules.farmedHarvest(crop);
    }

    @Override
    public boolean fused(Adaptation<?> original) {
      return Lending.fused(original);
    }

    @Override
    public int lentLevel(Adaptation<?> original, ToIntFunction<Adaptation<?>> upgradeLevel) {
      return Lending.lentLevel(original, upgradeLevel);
    }

    @Override
    public Adaptation.@Nullable BlockActionContext lentContext(
        Adaptation<?> original, Function<Adaptation<?>, Adaptation.BlockActionContext> upgradeContext) {
      return Lending.lentContext(original, upgradeContext);
    }

    @Override
    public void landed(Adaptation<?> original, PlayerRef player) {
      Lending.landed(original, player);
    }

    @Override
    public double strength(Adaptation<?> original, PlayerRef player) {
      return Lending.strength(original, player);
    }

    @Override
    public MutationKit kit(Skill<?> skill) {
      return Kits.of(skill);
    }

    @Override
    public MutationKit kit(Id skill) {
      SkillRegistry registry = registry();
      Skill<?> found = registry == null ? null : registry.getAnySkill(skill);
      if (found == null) {
        throw new IllegalArgumentException("No registered skill has the id " + skill);
      }
      return Kits.of(found);
    }

    @Override
    public double overchargeStrength() {
      return AdaptConfig.get().getMutations().getOverchargeStrength();
    }

    @Override
    public long recipeCooldownMillis(Recipe recipe, int level) {
      return MutationAdaptation.recipeCooldownMillis(recipe, level);
    }

    @Override
    public String grantedLine(Adaptation<?> original, int level) {
      return SkillUpgrades.grantedLine(original, level);
    }

    @Override
    public double claimXp(SkillUpgrade<?> upgrade, PlayerRef player, double amount) {
      return UpgradeXp.claim(upgrade, player, amount);
    }

    @Override
    public boolean naturalMob(PlayerRef player, EntityRef mob) {
      return UpgradeXp.naturalMob(player, mob);
    }

    @Override
    public boolean realAttacker(PlayerRef player, EntityRef damager) {
      return UpgradeXp.realAttacker(player, damager);
    }

    @Override
    public boolean cue(Adaptation<?> source, SoundPalette palette, PlayerRef player, MutationCue cue) {
      return SkillUpgrades.SOUNDS.cue(player.uuid(), source.getId().toString(), palette, cue,
          SoundStage.following(source, player));
    }

    @Override
    public LingeringSound linger(Adaptation<?> source, SoundPalette palette, EntityRef entity) {
      return SkillUpgrades.SOUNDS.linger(palette, SoundStage.following(source, entity));
    }

    @Override
    public @Nullable MutationTool<?> tool(ItemRef item) {
      return MutationTools.of(item);
    }

    @Override
    public @Nullable MutationTool<?> tool(Id id) {
      return MutationTools.find(id);
    }

    @Override
    public boolean isToolRecipe(Id recipe) {
      return MutationTools.isToolRecipe(recipe);
    }

    private @Nullable SkillRegistry registry() {
      AdaptServer server = core.getAdaptServer();
      return server == null ? null : server.getSkillRegistry();
    }
  }

  private static final class Settings implements AdaptSettings {
    @Override
    public boolean advancements() {
      return AdaptConfig.get().isAdvancements();
    }

    @Override
    public boolean adaptationsInCreative() {
      return AdaptConfig.get().allowAdaptationsInCreative;
    }

    @Override
    public boolean particles() {
      return AdaptConfig.get().getEffects().isParticlesEnabled();
    }

    @Override
    public boolean sounds() {
      return AdaptConfig.get().getEffects().isSoundsEnabled();
    }

    @Override
    public boolean isGrowthBlacklisted(Id block) {
      return AdaptConfig.get().isGrowthBlacklisted(block);
    }

    @Override
    public Set<Id> growthBlacklist() {
      Set<Id> blocks = new HashSet<>();
      for (String block : AdaptConfig.get().getBlacklistedGrowableBlocks()) {
        Id id = Id.tryParse(block.toLowerCase(Locale.ROOT));
        if (id != null) {
          blocks.add(id);
        }
      }
      return Collections.unmodifiableSet(blocks);
    }

    @Override
    public double overchargeStrength() {
      return AdaptConfig.get().getMutations().getOverchargeStrength();
    }

    @Override
    public String activatorBlockName() {
      return AdaptConfig.get().adaptActivatorBlockName;
    }

    @Override
    public boolean progressionSounds() {
      return AdaptConfig.get().isProgressionSoundsEnabled();
    }

    @Override
    public boolean hungerEffectPausesHunterAdaptations() {
      return AdaptConfig.get().isPreventHunterSkillsWhenHungerApplied();
    }

    @Override
    public boolean hardcoreResetOnDeath() {
      return AdaptConfig.get().isHardcoreResetOnPlayerDeath();
    }

    @Override
    public boolean hardcoreNoRefunds() {
      return AdaptConfig.get().isHardcoreNoRefunds();
    }

    @Override
    public boolean customModels() {
      return AdaptConfig.get().isCustomModels();
    }

    @Override
    public boolean menuBackButton() {
      return AdaptConfig.get().isGuiBackButton();
    }

    @Override
    public boolean escapeClosesAllMenus() {
      return AdaptConfig.get().isEscClosesAllGuis();
    }

    @Override
    public boolean isWorldBlacklisted(Id world) {
      return AdaptConfig.get().isWorldBlacklisted(world.toString());
    }

    @Override
    public boolean bonemealTracking() {
      return AdaptConfig.get().getXpIntegrity().isBonemealTrackingEnabled();
    }
  }
}
