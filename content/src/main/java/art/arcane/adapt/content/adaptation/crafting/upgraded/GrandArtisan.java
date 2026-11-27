package art.arcane.adapt.content.adaptation.crafting.upgraded;

import art.arcane.adapt.api.AdaptApi;
import art.arcane.adapt.api.config.ConfigDescription;
import art.arcane.adapt.api.config.ConfigDoc;
import art.arcane.adapt.api.content.Adaptation;
import art.arcane.adapt.api.content.StatLines;
import art.arcane.adapt.api.hud.HudStatus;
import art.arcane.adapt.api.mutation.MutationCue;
import art.arcane.adapt.api.mutation.SkillUpgrade;
import art.arcane.adapt.api.preference.CommonPreferences;
import art.arcane.adapt.api.preference.PlayerPreference;
import art.arcane.adapt.api.text.Format;
import art.arcane.adapt.api.text.Text;
import art.arcane.adapt.api.text.TextFormat;
import art.arcane.adapt.content.adaptation.crafting.CraftingMasterwork;
import art.arcane.adapt.platform.entity.InventoryRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.inventory.InventoryClickEvent;
import art.arcane.adapt.platform.event.inventory.InventoryKind;
import art.arcane.adapt.platform.event.inventory.PrepareResultEvent;
import art.arcane.adapt.platform.event.inventory.SlotKind;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.menu.ClickKind;
import art.arcane.adapt.platform.value.Id;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;


/**
 * Masterwork, Tinkerer and Thrifty Hands fused: crafted gear rolls masterwork bonuses, stronger than Masterwork's own,
 * damaged tools combine their enchantments, and crafts refund an ingredient. A smithing table reforges gear with its
 * own base material: the preview lists the offered rolls, a click takes the first and a number key picks another.
 */
public class GrandArtisan extends SkillUpgrade<GrandArtisan.Config> {
  public static final Id ID = Id.adapt("crafting/upgrade/grand_artisan");

  public static final PlayerPreference<CraftingMasterwork.ReforgeLock> LOCK = PlayerPreference.of("reforge-lock", CraftingMasterwork.ReforgeLock.class, CraftingMasterwork.ReforgeLock.NONE, List.of(
      new PlayerPreference.Choice<>(CraftingMasterwork.ReforgeLock.NONE, Text.key("adapt.upgrade.crafting.grand_artisan.lock_none"),
          Id.minecraft("smithing_table"), 1),
      new PlayerPreference.Choice<>(CraftingMasterwork.ReforgeLock.DURABILITY,
          Text.key("adapt.upgrade.crafting.grand_artisan.lock_durability"), Id.minecraft("anvil"), 1),
      new PlayerPreference.Choice<>(CraftingMasterwork.ReforgeLock.EDGE, Text.key("adapt.upgrade.crafting.grand_artisan.lock_edge"),
          Id.minecraft("netherite_ingot"), 1))).labelled(Text.key("adapt.upgrade.crafting.grand_artisan.lock"));

  private static final Id MASTERWORK = Id.adapt("crafting/masterwork");
  private static final int TEMPLATE_SLOT = 0;
  private static final int GEAR_SLOT = 1;
  private static final int MATERIAL_SLOT = 2;
  private static final int RESULT_SLOT = 3;

  private final Map<UUID, Reforge> previews = playerState();

  public GrandArtisan() {
    super(ID);
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("smithing_table"));
  }

  @Override
  protected List<PlayerPreference<?>> upgradePreferences() {
    return List.of(CommonPreferences.ENABLED, LOCK);
  }

  @Override
  public void addStats(int level, StatLines v) {
    grantedLore(v, level);
    statLore(v, Format.pc(getMasterworkStrength(level) - 1D, 0), 2);
    statLore(v, choices(level), 3);
    statLore(v, TextFormat.RED, "- ", reforgeLevels(level), 4);
    if (level >= getConfig().lockLevel) {
      v.addLore(TextFormat.GREEN + "+ " + TextFormat.GRAY + Text.key("adapt.upgrade.crafting.grand_artisan.lore5").resolve());
    }
  }

  @Override
  public double strength(Adaptation<?> original, int upgradeLevel) {
    return MASTERWORK.equals(original.getId()) ? getMasterworkStrength(upgradeLevel) : 1D;
  }

  double getMasterworkStrength(int level) {
    return 1D + Math.max(0D, getConfig().masterworkBonusBase + (level * getConfig().masterworkBonusPerLevel));
  }

  int choices(int level) {
    return Math.max(1, getConfig().reforgeChoicesBase + ((level - 1) * getConfig().reforgeChoicesPerLevel));
  }

  int reforgeLevels(int level) {
    return Math.max(0, getConfig().reforgeLevelsBase - ((level - 1) * getConfig().reforgeLevelsPerLevel));
  }

  @Handle(priority = EventPriority.HIGH)
  public void on(PrepareResultEvent e) {
    PlayerRef p = e.player();
    previews.remove(p.uuid());
    if (e.kind() != InventoryKind.SMITHING || e.inputs().size() <= MATERIAL_SLOT) {
      return;
    }
    ItemRef gear = e.inputs().get(GEAR_SLOT);
    ItemRef material = e.inputs().get(MATERIAL_SLOT);
    int level = getActiveLevel(p);
    CraftingMasterwork masterwork = masterwork();
    if (level <= 0 || masterwork == null || !empty(e.inputs().get(TEMPLATE_SLOT)) || empty(gear) || gear.amount() != 1
        || empty(material) || !reforgeMaterial(gear.type(), material.type())) {
      return;
    }
    CraftingMasterwork.ReforgeLock lock = level >= getConfig().lockLevel ? preference(p, LOCK)
        : CraftingMasterwork.ReforgeLock.NONE;
    Random rolls = new Random(CraftingMasterwork.reforgeSeed(gear));
    List<ItemRef> offered = new ArrayList<>();
    for (int choice = 0; choice < choices(level); choice++) {
      ItemRef reforged = masterwork.reforge(p, gear, lock, rolls::nextDouble);
      if (reforged == null) {
        return;
      }
      offered.add(reforged);
    }
    int cost = reforgeLevels(level);
    ItemRef preview = offered.getFirst().copy();
    List<String> lore = new ArrayList<>(preview.lore());
    for (int choice = 0; choice < offered.size(); choice++) {
      lore.add(TextFormat.WHITE + "[" + (choice + 1) + "] " + CraftingMasterwork.describe(offered.get(choice)));
    }
    lore.add(TextFormat.YELLOW + levelsText(cost));
    preview.setLore(lore);
    e.setResult(preview);
    previews.put(p.uuid(), new Reforge(gear.copy(), material.type(), List.copyOf(offered), cost));
  }

  @Handle(priority = EventPriority.HIGHEST)
  public void on(InventoryClickEvent e) {
    PlayerRef p = e.player();
    Reforge reforge = previews.get(p.uuid());
    if (reforge == null || e.viewKind() != InventoryKind.SMITHING || e.slotKind() != SlotKind.RESULT) {
      return;
    }
    e.setCancelled(true);
    int choice = e.click() == ClickKind.NUMBER_KEY ? e.hotbarButton() : e.click().isLeft() ? 0 : -1;
    InventoryRef top = p.openMenuInventory();
    if (choice < 0 || choice >= reforge.offered().size() || top == null || getActiveLevel(p) <= 0
        || !reforge.matches(top)) {
      return;
    }
    if (!payExperienceCost(p, "experience-levels", reforge.cost(), () -> spendLevels(p, reforge.cost()))) {
      HudStatus.show(p, TextFormat.RED + levelsText(reforge.cost()));
      return;
    }
    ItemRef material = top.item(MATERIAL_SLOT);
    ItemRef nothing = AdaptApi.platform().host().items().empty();
    top.setItem(GEAR_SLOT, nothing);
    top.setItem(MATERIAL_SLOT, material.amount() > 1 ? material.copyWithAmount(material.amount() - 1) : nothing);
    top.setItem(RESULT_SLOT, nothing);
    previews.remove(p.uuid());
    for (ItemRef leftover : p.inventory().addItem(reforge.offered().get(choice).copy())) {
      p.world().dropItemNaturally(p.location().position(), leftover);
    }
    addStat(p, "crafting.grand-artisan.reforges", 1);
    cue(p, MutationCue.WORK);
  }

  private static boolean spendLevels(PlayerRef p, int levels) {
    if (p.level() < levels) {
      return false;
    }
    p.giveExpLevels(-levels);
    return true;
  }

  private static String levelsText(int levels) {
    return Text.key("adapt.upgrade.crafting.grand_artisan.levels").withMarkup("levels", String.valueOf(levels)).resolve();
  }

  private CraftingMasterwork masterwork() {
    for (Adaptation<?> adaptation : getSkill().getAdaptations()) {
      if (adaptation instanceof CraftingMasterwork masterwork) {
        return masterwork;
      }
    }
    return null;
  }

  private static boolean empty(ItemRef item) {
    return item == null || item.isEmpty() || item.amount() <= 0 || item.type().isAir();
  }

  /** Whether {@code material} is the base material {@code gear} is made from. */
  static boolean reforgeMaterial(Id gear, Id material) {
    if (!gear.isMinecraft() || !material.isMinecraft()) {
      return false;
    }
    String piece = gear.path();
    String base = material.path();
    if (piece.startsWith("wooden_")) {
      return base.endsWith("_planks");
    }
    if (piece.startsWith("stone_")) {
      return base.equals("cobblestone") || base.equals("cobbled_deepslate") || base.equals("blackstone");
    }
    if (piece.startsWith("iron_") || piece.startsWith("chainmail_")) {
      return base.equals("iron_ingot");
    }
    if (piece.equals("turtle_helmet")) {
      return base.equals("scute") || base.equals("turtle_scute");
    }
    for (String[] pair : new String[][] {{"golden_", "gold_ingot"}, {"diamond_", "diamond"},
        {"netherite_", "netherite_ingot"}, {"leather_", "leather"}, {"copper_", "copper_ingot"}}) {
      if (piece.startsWith(pair[0])) {
        return base.equals(pair[1]);
      }
    }
    return false;
  }

  private record Reforge(ItemRef gear, Id material, List<ItemRef> offered, int cost) {
    boolean matches(InventoryRef top) {
      ItemRef held = top.item(GEAR_SLOT);
      ItemRef base = top.item(MATERIAL_SLOT);
      return empty(top.item(TEMPLATE_SLOT)) && !empty(held) && held.amount() == 1 && held.isSimilar(gear)
          && !empty(base) && base.type().equals(material);
    }
  }

  @ConfigDescription("Crafted gear rolls stronger masterwork bonuses, damaged tools combine their enchantments, crafts refund an ingredient, and a smithing table reforges masterwork bonuses.")
  public static class Config extends SkillUpgrade.Config {
    @ConfigDoc(value = "Fraction added to Masterwork's roll chance, bonus durability and enchantment and attribute chances, before level scaling.", impact = "Higher values make masterwork rolls more frequent and stronger.")
    double masterworkBonusBase = 0.10;
    @ConfigDoc(value = "Fraction added to the masterwork boost per level.", impact = "Higher values reward higher levels more.")
    double masterworkBonusPerLevel = 0.15;
    @ConfigDoc(value = "Reforge rolls offered to pick from at level 1.", impact = "Higher values give more rolls to choose between.")
    int reforgeChoicesBase = 1;
    @ConfigDoc(value = "Reforge rolls added per level after the first.", impact = "Higher values give higher levels more choice.")
    int reforgeChoicesPerLevel = 1;
    @ConfigDoc(value = "Experience levels a reforge costs at level 1.", impact = "Higher values make reforging more expensive.")
    int reforgeLevelsBase = 5;
    @ConfigDoc(value = "Experience levels removed from the reforge cost per level after the first.", impact = "Higher values make reforging cheaper at higher levels.")
    int reforgeLevelsPerLevel = 1;
    @ConfigDoc(value = "Upgrade level from which a reforge can keep the durability bonus or the Masterwork Edge.", impact = "Lower values unlock the reforge lock sooner.")
    int lockLevel = 3;
  }
}
