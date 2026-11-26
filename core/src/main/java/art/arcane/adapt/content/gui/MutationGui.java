package art.arcane.adapt.content.gui;

import art.arcane.adapt.AdaptConfig;
import art.arcane.adapt.AdaptCore;
import art.arcane.adapt.api.adaptation.Adaptation;
import art.arcane.adapt.api.mutation.MutatedSkill;
import art.arcane.adapt.api.mutation.MutationOffers;
import art.arcane.adapt.api.mutation.SkillMutation;
import art.arcane.adapt.api.mutation.SkillMutations;
import art.arcane.adapt.api.mutation.blocks.MutatedText;
import art.arcane.adapt.api.mutation.sound.SoundPalettes;
import art.arcane.adapt.api.mutation.sound.SoundStage;
import art.arcane.adapt.api.mutation.tool.MutationTools;
import art.arcane.adapt.api.mutation.upgrade.SkillUpgrades;
import art.arcane.adapt.api.skill.Skill;
import art.arcane.adapt.api.skill.SkillRegistry;
import art.arcane.adapt.api.world.AdaptDebugMode;
import art.arcane.adapt.api.world.AdaptPlayer;
import art.arcane.adapt.api.world.PlayerData;
import art.arcane.adapt.api.world.PlayerSkillLine;
import art.arcane.adapt.client.server.AdaptScreens;
import art.arcane.adapt.client.server.ClientUiService;
import art.arcane.adapt.command.PlayerCommandSource;
import art.arcane.adapt.localization.AdaptLanguage;
import art.arcane.adapt.localization.catalog.CommandRuntimeMessages;
import art.arcane.adapt.localization.catalog.MutationMessages;
import art.arcane.adapt.localization.catalog.SnippetsMessages;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.menu.MenuClick;
import art.arcane.adapt.platform.menu.MenuHint;
import art.arcane.adapt.platform.menu.MenuLayout;
import art.arcane.adapt.platform.menu.MenuRole;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.util.command.FConst;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.inventorygui.GuiConfig;
import art.arcane.adapt.util.common.inventorygui.GuiElement;
import art.arcane.adapt.util.common.inventorygui.GuiLayout;
import art.arcane.adapt.util.common.inventorygui.GuiTheme;
import art.arcane.adapt.util.common.inventorygui.GuiWindow;
import art.arcane.adapt.util.common.misc.CustomModel;
import art.arcane.adapt.util.common.scheduling.Tasks;
import art.arcane.volmlib.util.format.Form;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import static art.arcane.volmlib.util.localization.MessageArgument.trusted;
import static art.arcane.volmlib.util.localization.MessageArgument.untrusted;

/**
 * The Mutate flow as ordinary menus: a skill to lead (from {@code /adapt mutate}), then one menu with the partners to
 * pick from and, once one is picked, the preview with a lead swap and a two-step confirm. Players with the client mod
 * get the same menus as native panels unless their client draws its own spread.
 */
public final class MutationGui {
  private static final int COLUMNS = 9;
  private static final int MAX_PARTNER_ROWS = 3;
  private static final Id CONFIRM = Id.minecraft("lime_stained_glass_pane");
  private static final Id CONFIRM_ARMED = Id.minecraft("red_stained_glass_pane");
  private static final Id LOST = Id.minecraft("barrier");
  private static final Id SWAP = Id.minecraft("compass");
  private static final Id EMPTY_PARTNER = Id.minecraft("gray_stained_glass_pane");
  private static final Id UNMUTATE = Id.minecraft("grindstone");
  /** Lets a player undo anyone's mutations by command, and their own from the skill page without debug mode. */
  public static final String UNMUTATE_PERMISSION = "adapt.mutate.reset";

  private MutationGui() {
  }

  /** The Mutate button for a skill page, or null when the page does not offer it to this player. */
  public static GuiElement entry(Skill<?> skill, PlayerRef player, PlayerData data) {
    SkillRegistry registry = registry();
    if (registry == null || !MutationOffers.offered(data, skill, registry.getSkills(), config())) {
      return null;
    }
    return new GuiElement("mutate")
        .setMaterial(Id.minecraft("nether_star"))
        .setName(C.LIGHT_PURPLE + AdaptLanguage.text(MutationMessages.MUTATE))
        .setRole(MenuRole.ACTION)
        .onLeftClick((MenuClick e) -> AdaptScreens.openMutation(player, skill));
  }

  /** Skills the player can mutate now; picking one opens its partners. */
  public static void openLeads(PlayerRef player) {
    AdaptPlayer adaptPlayer = adaptPlayer(player);
    SkillRegistry registry = registry();
    if (adaptPlayer == null || registry == null) {
      return;
    }
    List<Skill<?>> leads = MutationOffers.leads(adaptPlayer.getData(), registry.getSkills(), config());
    skillMenu(player, adaptPlayer.getData(), leads, (Skill<?> lead) -> openPartners(player, lead),
        () -> AdaptScreens.openSkills(player));
  }

  /** The partners that can join {@code lead}, with nothing picked yet. */
  public static void openPartners(PlayerRef player, Skill<?> lead) {
    open(player, lead, null, false);
  }

  /**
   * One menu for the whole choice: the lead, the picked partner and the result on the top row with the swap, the lost
   * list and the confirm, then the partners to pick from, then the upgrades and the mutation's own adaptations. The
   * confirm arms on the first click and mutates on the second; picking another partner or swapping disarms it.
   */
  public static void open(PlayerRef player, Skill<?> lead, Skill<?> partner, boolean armed) {
    AdaptPlayer adaptPlayer = adaptPlayer(player);
    SkillRegistry registry = registry();
    if (adaptPlayer == null || registry == null) {
      return;
    }
    if (!Tasks.isPrimaryThread()) {
      Tasks.runEntity(player, () -> open(player, lead, partner, armed));
      return;
    }
    PlayerData data = adaptPlayer.getData();
    List<Skill<?>> partners = MutationOffers.partners(data, lead, registry.getSkills(), config());
    Skill<?> picked = partner != null && partners.contains(partner) ? partner : null;
    MutationOffers.Preview preview = picked == null ? null : MutationOffers.preview(data, lead, picked);
    int partnerRows = Math.max(1, Math.min(MAX_PARTNER_ROWS, (partners.size() + COLUMNS - 1) / COLUMNS));
    GuiWindow window = new GuiWindow(player);
    GuiTheme.apply(window, "mutation/preview");
    window.setViewportHeight(1 + partnerRows + (preview == null ? 0 : 2));
    window.setLayout(MenuLayout.GRID);

    window.setElement(-4, 0, GuiTheme.element("mutation-back", Id.minecraft("arrow"), "gui", "navigation", "back")
        .setName(C.GRAY + AdaptLanguage.text(SnippetsMessages.GUI_BACK))
        .onLeftClick((MenuClick e) -> lead.openGui(player)));
    window.setElement(-3, 0, skillTile("mutation-lead", lead, data)
        .setName(C.WHITE + AdaptLanguage.text(MutationMessages.LEAD, trusted("skill", lead.getLocalizedName())))
        .setRole(MenuRole.HEADER));
    if (preview == null) {
      window.setElement(-1, 0, new GuiElement("mutation-partner")
          .setMaterial(EMPTY_PARTNER)
          .setName(C.GRAY + AdaptLanguage.text(MutationMessages.PICK_PARTNER))
          .setRole(MenuRole.INFO));
    } else {
      window.setElement(-2, 0, new GuiElement("mutation-swap")
          .setMaterial(SWAP)
          .setName(C.WHITE + AdaptLanguage.text(MutationMessages.SWAP_LEAD))
          .setRole(MenuRole.ACTION)
          .onLeftClick((MenuClick e) -> open(player, picked, lead, false)));
      window.setElement(-1, 0, skillTile("mutation-partner", picked, data).setRole(MenuRole.INFO));
      CustomModel leadModel = GuiConfig.skillModel(lead.getName(), lead.getIcon(), lead.getModel());
      window.setElement(0, 0, new GuiElement("mutation-result")
          .setMaterial(leadModel.material())
          .setModel(leadModel)
          .setName(C.RESET + "" + C.BOLD + lead.getColor() + preview.name())
          .addLore(C.GRAY + AdaptLanguage.text(MutationMessages.LEVEL, trusted("level", C.WHITE + "" + preview.level())))
          .addLore(C.GRAY + AdaptLanguage.text(MutationMessages.KNOWLEDGE, trusted("knowledge", C.WHITE + "" + preview.knowledge())))
          .setRole(MenuRole.HEADER));
      GuiElement lost = new GuiElement("mutation-lost")
          .setMaterial(LOST)
          .setName(C.RED + AdaptLanguage.text(MutationMessages.LOST))
          .setRole(MenuRole.INFO);
      for (String line : lostLines(preview)) {
        lost.addLore(line);
      }
      window.setElement(2, 0, lost);
      window.setElement(4, 0, armed
          ? new GuiElement("mutation-confirm")
              .setMaterial(CONFIRM_ARMED)
              .setName(C.RED + AdaptLanguage.text(MutationMessages.CONFIRM_AGAIN))
              .addLore(C.RED + AdaptLanguage.text(MutationMessages.PERMANENT))
              .setHint(MenuHint.destructive(AdaptLanguage.text(MutationMessages.CONFIRM_AGAIN)))
              .setRole(MenuRole.ACTION)
              .onLeftClick((MenuClick e) -> {
                window.close();
                complete(player, lead, picked);
              })
          : new GuiElement("mutation-confirm")
              .setMaterial(CONFIRM)
              .setName(C.GREEN + AdaptLanguage.text(MutationMessages.MUTATE))
              .addLore(C.RED + AdaptLanguage.text(MutationMessages.PERMANENT))
              .setRole(MenuRole.ACTION)
              .onLeftClick((MenuClick e) -> open(player, lead, picked, true)));
    }

    int shown = Math.min(partners.size(), partnerRows * COLUMNS);
    for (int index = 0; index < shown; index++) {
      Skill<?> candidate = partners.get(index);
      int row = index / COLUMNS;
      int inRow = Math.min(COLUMNS, shown - (row * COLUMNS));
      boolean chosen = candidate == picked;
      window.setElement(GuiLayout.centeredPosition(index % COLUMNS, inRow), row + 1,
          skillTile("mutation-skill-" + candidate.getName(), candidate, data)
              .setEnchanted(chosen)
              .setRole(MenuRole.NAVIGATION)
              .onLeftClick((MenuClick e) -> open(player, lead, candidate, false)));
    }
    if (preview != null) {
      adaptationRow(window, partnerRows + 1, preview.upgrades(), false);
      adaptationRow(window, partnerRows + 2, preview.mutated(), true);
    }
    window.setTitle(C.GRAY + (preview == null ? AdaptLanguage.text(MutationMessages.MUTATE) : preview.name()));
    window.open();
  }

  private static GuiElement skillTile(String id, Skill<?> skill, PlayerData data) {
    PlayerSkillLine line = data.getSkillLineNullable(skill.getName());
    CustomModel model = GuiConfig.skillModel(skill.getName(), skill.getIcon(), skill.getModel());
    return new GuiElement(id)
        .setMaterial(model.material())
        .setModel(model)
        .setName(skill.getDisplayName())
        .addLore(C.GRAY + AdaptLanguage.text(MutationMessages.LEVEL, trusted("level", C.WHITE + "" + (line == null ? 0 : line.getLevel()))));
  }

  /** One tile per adaptation with its description, costs and tools; {@code own} marks the mutation's own with a glint. */
  private static void adaptationRow(GuiWindow window, int row, List<Adaptation<?>> adaptations, boolean own) {
    for (int index = 0; index < adaptations.size(); index++) {
      Adaptation<?> adaptation = adaptations.get(index);
      CustomModel model = GuiConfig.adaptationModel(adaptation.getName(), adaptation.getIcon(), adaptation.getModel());
      GuiElement tile = new GuiElement("mutation-" + adaptation.getName())
          .setMaterial(model.material())
          .setModel(model)
          .setEnchanted(own)
          .setName(adaptation.getShortName())
          .addLore(C.GRAY + adaptation.getDescription())
          .addLore(C.GRAY + AdaptLanguage.text(MutationMessages.KNOWLEDGE, trusted("knowledge", C.WHITE + costs(adaptation) + C.GRAY)))
          .setRole(MenuRole.INFO);
      String tools = MutationTools.lore(adaptation);
      if (!tools.isEmpty()) {
        tile.addLore(tools);
      }
      window.setElement(GuiLayout.centeredPosition(index, adaptations.size()), row, tile);
    }
  }

  /** The knowledge each level costs, such as "15 / 14 / 18". */
  public static String costs(Adaptation<?> adaptation) {
    List<String> costs = new ArrayList<>(adaptation.getMaxLevel());
    for (int level = 1; level <= adaptation.getMaxLevel(); level++) {
      costs.add(String.valueOf(adaptation.getCostFor(level)));
    }
    return String.join(" / ", costs);
  }

  /**
   * Mutates with every rule checked again, then says the result, and on success plays the mutation sound and opens
   * the mutated skill. Returns the merge result.
   */
  public static SkillMutations.Result complete(PlayerRef player, Skill<?> lead, Skill<?> partner) {
    AdaptPlayer adaptPlayer = adaptPlayer(player);
    if (adaptPlayer == null) {
      return SkillMutations.Result.UNKNOWN_SKILL;
    }
    SkillMutations.Result result = merge(player, adaptPlayer, lead, partner);
    String text = resultText(result, lead, partner);
    (result == SkillMutations.Result.MUTATED ? FConst.success(text) : FConst.error(text))
        .send(new PlayerCommandSource(player));
    if (result != SkillMutations.Result.MUTATED) {
      return result;
    }
    SkillRegistry registry = registry();
    MutatedSkill mutated = registry == null ? null
        : registry.getMutatedSkill(adaptPlayer.getData().mutationOf(lead.getName()).id());
    if (mutated != null) {
      AdaptScreens.openSkill(player, mutated);
    }
    return result;
  }

  /** Mutates with every rule checked again and plays the mutation sound when it went through. */
  public static SkillMutations.Result merge(PlayerRef player, AdaptPlayer adaptPlayer, Skill<?> lead, Skill<?> partner) {
    SkillMutations.Result result = SkillMutations.mutate(adaptPlayer.getData(), lead, partner, player.name());
    if (result == SkillMutations.Result.MUTATED) {
      SkillUpgrades.SOUNDS.mutation(SoundPalettes.of(lead.getName()), SoundPalettes.of(partner.getName()),
          SoundStage.following(lead, player));
    }
    return result;
  }

  /** Whether {@code player} may undo a mutation: in Adapt's debug mode, or with {@link #UNMUTATE_PERMISSION}. */
  public static boolean mayUnmutate(PlayerRef player) {
    return player != null && (AdaptDebugMode.isActive(player) || player.hasPermission(UNMUTATE_PERMISSION));
  }

  /** Whether a skill's menu offers Unmutate to {@code player}: a mutated skill, viewed in debug mode. */
  public static boolean offersUnmutate(Skill<?> skill, PlayerRef player) {
    return skill instanceof MutatedSkill && AdaptDebugMode.isActive(player);
  }

  /**
   * Puts the Unmutate button on a mutated skill's menu when {@link #offersUnmutate} allows it. The first click arms it
   * and the second undoes the mutation and opens the lead skill's menu.
   */
  public static void placeUnmutate(GuiWindow window, int position, int row, Skill<?> skill, PlayerRef player) {
    if (offersUnmutate(skill, player)) {
      window.setElement(position, row, unmutateButton(window, position, row, (MutatedSkill) skill, player, false));
    }
  }

  private static GuiElement unmutateButton(GuiWindow window, int position, int row, MutatedSkill mutated, PlayerRef player,
                                           boolean armed) {
    GuiElement button = new GuiElement("mutation-unmutate").setRole(MenuRole.ACTION);
    if (!armed) {
      return button.setMaterial(UNMUTATE)
          .setName(C.RED + AdaptLanguage.text(MutationMessages.UNMUTATE))
          .onLeftClick((MenuClick e) -> window.updateElement(position, row,
              unmutateButton(window, position, row, mutated, player, true)));
    }
    return button.setMaterial(CONFIRM_ARMED)
        .setName(C.RED + AdaptLanguage.text(MutationMessages.UNMUTATE_CONFIRM))
        .setHint(MenuHint.destructive(AdaptLanguage.text(MutationMessages.UNMUTATE_CONFIRM)))
        .onLeftClick((MenuClick e) -> {
          window.close();
          AdaptPlayer adaptPlayer = adaptPlayer(player);
          SkillMutation mutation = mutated.getMutation();
          if (adaptPlayer == null || !mayUnmutate(player) || !unmutate(player, adaptPlayer, mutation)) {
            return;
          }
          FConst.success(unmutatedText(mutation, player.name())).send(new PlayerCommandSource(player));
          SkillRegistry registry = registry();
          Skill<?> lead = registry == null ? null : registry.getSkill(mutation.lead());
          if (lead != null) {
            lead.openGui(player);
          }
        });
  }

  /**
   * Undoes {@code mutation} for an online player as {@link SkillMutations#unmutate} does and tells their client to
   * refresh. False when they do not hold it.
   */
  public static boolean unmutate(PlayerRef player, AdaptPlayer adaptPlayer, SkillMutation mutation) {
    if (!SkillMutations.unmutate(adaptPlayer.getData(), mutation, player.name())) {
      return false;
    }
    ClientUiService.changed(adaptPlayer);
    return true;
  }

  /** What an unmutate says: the mutation removed and both parents back. */
  public static String unmutatedText(SkillMutation mutation, String playerName) {
    SkillRegistry registry = registry();
    return AdaptLanguage.text(CommandRuntimeMessages.MUTATION_UNMUTATED,
        trusted("mutation", AdaptLanguage.render(MutatedText.mutationName(MutatedText.VIEWER, mutation.lead(), mutation.partner()))),
        untrusted("player", playerName),
        trusted("lead", skillName(registry, mutation.lead())),
        trusted("partner", skillName(registry, mutation.partner())));
  }

  private static String skillName(SkillRegistry registry, String skillId) {
    Skill<?> skill = registry == null ? null : registry.getSkill(skillId);
    return skill == null ? skillId : skill.getLocalizedName();
  }

  /** The player-facing text of a merge result. */
  public static String resultText(SkillMutations.Result result, Skill<?> lead, Skill<?> partner) {
    AdaptConfig.Mutations config = config();
    return switch (result) {
      case MUTATED -> AdaptLanguage.text(CommandRuntimeMessages.MUTATION_CREATED,
          trusted("lead", lead.getLocalizedName()), trusted("partner", partner.getLocalizedName()),
          trusted("mutation", AdaptLanguage.render(MutatedText.mutationName(MutatedText.VIEWER, lead.getName(), partner.getName()))));
      case UNKNOWN_SKILL -> AdaptLanguage.text(CommandRuntimeMessages.MUTATION_UNKNOWN_SKILL);
      case UNAVAILABLE_SKILL -> AdaptLanguage.text(CommandRuntimeMessages.MUTATION_UNAVAILABLE_SKILL);
      case SAME_SKILL -> AdaptLanguage.text(CommandRuntimeMessages.MUTATION_SAME_SKILL);
      case ALREADY_MUTATED -> AdaptLanguage.text(CommandRuntimeMessages.MUTATION_ALREADY_MUTATED);
      case LEVEL_TOO_LOW -> AdaptLanguage.text(CommandRuntimeMessages.MUTATION_LEVEL_TOO_LOW,
          trusted("level", config.getRequiredSkillLevel()));
      case LIMIT_REACHED -> AdaptLanguage.text(CommandRuntimeMessages.MUTATION_LIMIT_REACHED,
          trusted("limit", config.getMaxMutations()));
    };
  }

  static List<String> lostLines(MutationOffers.Preview preview) {
    List<String> lines = new ArrayList<>();
    String higher = preview.higher().getLocalizedName();
    if (preview.lostLevels() > 0) {
      lines.add(C.GRAY + AdaptLanguage.text(MutationMessages.LOST_LEVELS,
          trusted("count", C.WHITE + "" + preview.lostLevels() + C.GRAY), trusted("skill", higher)));
    }
    if (preview.lostKnowledge() > 0) {
      lines.add(C.GRAY + AdaptLanguage.text(MutationMessages.LOST_KNOWLEDGE,
          trusted("count", C.WHITE + "" + preview.lostKnowledge() + C.GRAY), trusted("skill", higher)));
    }
    for (SkillMutations.HeldAdaptation held : preview.unlearned()) {
      lines.add(C.GRAY + AdaptLanguage.text(held.adaptation().isPermanent()
              ? MutationMessages.LOST_PERMANENT_ADAPTATION : MutationMessages.LOST_ADAPTATION,
          trusted("adaptation", C.stripColor(held.adaptation().getDisplayName())),
          trusted("level", Form.toRoman(held.level()))));
    }
    if (lines.isEmpty()) {
      lines.add(C.GRAY + AdaptLanguage.text(MutationMessages.NOTHING_LOST));
    }
    return lines;
  }

  private static void skillMenu(PlayerRef player, PlayerData data, List<Skill<?>> skills, Consumer<Skill<?>> pick,
                                Runnable back) {
    if (!Tasks.isPrimaryThread()) {
      Tasks.runEntity(player, () -> skillMenu(player, data, skills, pick, back));
      return;
    }
    int skillRows = Math.max(1, (skills.size() + COLUMNS - 1) / COLUMNS);
    GuiWindow window = new GuiWindow(player);
    GuiTheme.apply(window, "mutation/pick");
    window.setViewportHeight(skillRows + 2);
    window.setLayout(MenuLayout.GRID);
    window.setElement(0, 0, new GuiElement("mutation-heading")
        .setMaterial(Id.minecraft("nether_star"))
        .setName(C.LIGHT_PURPLE + AdaptLanguage.text(MutationMessages.PICK_LEAD))
        .setRole(MenuRole.HEADER));
    for (int index = 0; index < skills.size(); index++) {
      Skill<?> skill = skills.get(index);
      int row = index / COLUMNS;
      int inRow = Math.min(COLUMNS, skills.size() - (row * COLUMNS));
      window.setElement(GuiLayout.centeredPosition(index % COLUMNS, inRow), row + 1,
          skillTile("mutation-skill-" + skill.getName(), skill, data)
              .setRole(MenuRole.NAVIGATION)
              .onLeftClick((MenuClick e) -> pick.accept(skill)));
    }
    window.setElement(0, skillRows + 1, GuiTheme.element("mutation-back", Id.minecraft("arrow"), "gui", "navigation", "back")
        .setName(C.GRAY + AdaptLanguage.text(SnippetsMessages.GUI_BACK))
        .onLeftClick((MenuClick e) -> back.run()));
    window.setTitle(C.GRAY + AdaptLanguage.text(MutationMessages.MUTATE));
    window.open();
  }

  private static AdaptPlayer adaptPlayer(PlayerRef player) {
    AdaptCore core = AdaptCore.current();
    return core == null || core.getAdaptServer() == null || player == null ? null : core.getAdaptServer().getPlayer(player);
  }

  private static SkillRegistry registry() {
    AdaptCore core = AdaptCore.current();
    return core == null || core.getAdaptServer() == null ? null : core.getAdaptServer().getSkillRegistry();
  }

  private static AdaptConfig.Mutations config() {
    return AdaptConfig.get().getMutations();
  }
}
