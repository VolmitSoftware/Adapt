package art.arcane.adapt.engine.gui;

import art.arcane.adapt.AdaptConfig;
import art.arcane.adapt.api.content.Adaptation;
import art.arcane.adapt.api.content.Skill;
import art.arcane.adapt.api.player.MutationResult;
import art.arcane.adapt.engine.component.ComponentHost;
import art.arcane.adapt.engine.player.PlayerApi;
import art.arcane.adapt.AdaptCore;
import art.arcane.adapt.engine.mutation.MutatedSkill;
import art.arcane.adapt.engine.mutation.MutationOffers;
import art.arcane.adapt.api.mutation.SkillPair;
import art.arcane.adapt.engine.mutation.SkillMutations;
import art.arcane.adapt.engine.mutation.blocks.MutatedText;
import art.arcane.adapt.engine.mutation.kit.Kits;
import art.arcane.adapt.engine.mutation.pair.PairRegistry;
import art.arcane.adapt.engine.mutation.sound.SoundStage;
import art.arcane.adapt.engine.mutation.tool.MutationTools;
import art.arcane.adapt.engine.mutation.upgrade.SkillUpgrades;
import art.arcane.adapt.engine.skill.SkillHost;
import art.arcane.adapt.engine.skill.SkillRegistry;
import art.arcane.adapt.engine.world.AdaptDebugMode;
import art.arcane.adapt.engine.world.AdaptPlayer;
import art.arcane.adapt.engine.world.PlayerData;
import art.arcane.adapt.engine.world.PlayerSkillLine;
import art.arcane.adapt.client.server.AdaptScreens;
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
import art.arcane.adapt.api.text.TextFormat;
import art.arcane.adapt.util.common.inventorygui.GuiConfig;
import art.arcane.adapt.util.common.inventorygui.GuiElement;
import art.arcane.adapt.util.common.inventorygui.GuiLayout;
import art.arcane.adapt.util.common.inventorygui.GuiTheme;
import art.arcane.adapt.util.common.inventorygui.GuiWindow;
import art.arcane.adapt.util.common.misc.CustomModel;
import art.arcane.adapt.api.AdaptApi;
import art.arcane.adapt.api.text.Format;

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
        .setName(TextFormat.LIGHT_PURPLE + AdaptLanguage.text(MutationMessages.MUTATE))
        .setRole(MenuRole.ACTION)
        .onLeftClick((MenuClick e) -> AdaptScreens.openMutation(player, skill));
  }

  /** Skills the player can mutate now (the pack lead picker for pack viewers); picking one opens its Mutate flow. */
  public static void openLeads(PlayerRef player) {
    AdaptPlayer adaptPlayer = adaptPlayer(player);
    SkillRegistry registry = registry();
    if (adaptPlayer == null || registry == null || AdaptScreens.openMutationLeads(player)) {
      return;
    }
    List<Skill<?>> leads = MutationOffers.leads(adaptPlayer.getData(), registry.getSkills(), config());
    skillMenu(player, adaptPlayer.getData(), leads, (Skill<?> lead) -> AdaptScreens.openMutation(player, lead),
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
    if (!AdaptApi.scheduler().isTickThread()) {
      AdaptApi.scheduler().runEntity(player, () -> open(player, lead, partner, armed));
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
        .setName(TextFormat.GRAY + AdaptLanguage.text(SnippetsMessages.GUI_BACK))
        .onLeftClick((MenuClick e) -> ComponentHost.of(lead).openGui(player)));
    window.setElement(-3, 0, skillTile("mutation-lead", lead, data)
        .setName(TextFormat.WHITE + AdaptLanguage.text(MutationMessages.LEAD, trusted("skill", lead.getLocalizedName())))
        .setRole(MenuRole.HEADER));
    if (preview == null) {
      window.setElement(-1, 0, new GuiElement("mutation-partner")
          .setMaterial(EMPTY_PARTNER)
          .setName(TextFormat.GRAY + AdaptLanguage.text(MutationMessages.PICK_PARTNER))
          .setRole(MenuRole.INFO));
    } else {
      window.setElement(-2, 0, new GuiElement("mutation-swap")
          .setMaterial(SWAP)
          .setName(TextFormat.WHITE + AdaptLanguage.text(MutationMessages.SWAP_LEAD))
          .setRole(MenuRole.ACTION)
          .onLeftClick((MenuClick e) -> open(player, picked, lead, false)));
      window.setElement(-1, 0, skillTile("mutation-partner", picked, data).setRole(MenuRole.INFO));
      CustomModel leadModel = GuiConfig.skillModel(SkillHost.lineKey(lead), lead.getIcon().item(), ComponentHost.of(lead).getModel());
      window.setElement(0, 0, new GuiElement("mutation-result")
          .setMaterial(leadModel.material())
          .setModel(leadModel)
          .setName(TextFormat.RESET + "" + TextFormat.BOLD + SkillHost.legacyColor(lead.getColor()) + preview.name())
          .addLore(TextFormat.GRAY + AdaptLanguage.text(MutationMessages.LEVEL, trusted("level", TextFormat.WHITE + "" + preview.level())))
          .addLore(TextFormat.GRAY + AdaptLanguage.text(MutationMessages.KNOWLEDGE, trusted("knowledge", TextFormat.WHITE + "" + preview.knowledge())))
          .setRole(MenuRole.HEADER));
      GuiElement lost = new GuiElement("mutation-lost")
          .setMaterial(LOST)
          .setName(TextFormat.RED + AdaptLanguage.text(MutationMessages.LOST))
          .setRole(MenuRole.INFO);
      for (String line : lostLines(preview)) {
        lost.addLore(line);
      }
      window.setElement(2, 0, lost);
      window.setElement(4, 0, armed
          ? new GuiElement("mutation-confirm")
              .setMaterial(CONFIRM_ARMED)
              .setName(TextFormat.RED + AdaptLanguage.text(MutationMessages.CONFIRM_AGAIN))
              .addLore(TextFormat.RED + AdaptLanguage.text(MutationMessages.PERMANENT))
              .setHint(MenuHint.destructive(AdaptLanguage.text(MutationMessages.CONFIRM_AGAIN)))
              .setRole(MenuRole.ACTION)
              .onLeftClick((MenuClick e) -> {
                window.close();
                complete(player, lead, picked);
              })
          : new GuiElement("mutation-confirm")
              .setMaterial(CONFIRM)
              .setName(TextFormat.GREEN + AdaptLanguage.text(MutationMessages.MUTATE))
              .addLore(TextFormat.RED + AdaptLanguage.text(MutationMessages.PERMANENT))
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
          skillTile("mutation-skill-" + SkillHost.lineKey(candidate), candidate, data)
              .setEnchanted(chosen)
              .setRole(MenuRole.NAVIGATION)
              .onLeftClick((MenuClick e) -> open(player, lead, candidate, false)));
    }
    if (preview != null) {
      adaptationRow(window, partnerRows + 1, preview.upgrades(), false);
      adaptationRow(window, partnerRows + 2, preview.mutated(), true);
    }
    window.setTitle(TextFormat.GRAY + (preview == null ? AdaptLanguage.text(MutationMessages.MUTATE) : preview.name()));
    window.open();
  }

  private static GuiElement skillTile(String id, Skill<?> skill, PlayerData data) {
    PlayerSkillLine line = data.getSkillLineNullable(SkillHost.lineKey(skill));
    CustomModel model = GuiConfig.skillModel(SkillHost.lineKey(skill), skill.getIcon().item(), ComponentHost.of(skill).getModel());
    return new GuiElement(id)
        .setMaterial(model.material())
        .setModel(model)
        .setName(skill.getDisplayName())
        .addLore(TextFormat.GRAY + AdaptLanguage.text(MutationMessages.LEVEL, trusted("level", TextFormat.WHITE + "" + (line == null ? 0 : line.getLevel()))));
  }

  /** One tile per adaptation with its description, costs and tools; {@code own} marks the mutation's own with a glint. */
  private static void adaptationRow(GuiWindow window, int row, List<Adaptation<?>> adaptations, boolean own) {
    for (int index = 0; index < adaptations.size(); index++) {
      Adaptation<?> adaptation = adaptations.get(index);
      CustomModel model = GuiConfig.adaptationModel(adaptation.getId().toString(), adaptation.getIcon().item(), ComponentHost.of(adaptation).getModel());
      GuiElement tile = new GuiElement("mutation-" + adaptation.getId().toString())
          .setMaterial(model.material())
          .setModel(model)
          .setEnchanted(own)
          .setName(adaptation.getShortName())
          .addLore(TextFormat.GRAY + adaptation.getDescription())
          .addLore(TextFormat.GRAY + AdaptLanguage.text(MutationMessages.KNOWLEDGE, trusted("knowledge", TextFormat.WHITE + costs(adaptation) + TextFormat.GRAY)))
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
  public static MutationResult.Status complete(PlayerRef player, Skill<?> lead, Skill<?> partner) {
    AdaptPlayer adaptPlayer = adaptPlayer(player);
    if (adaptPlayer == null) {
      return MutationResult.Status.UNKNOWN_SKILL;
    }
    MutationResult.Status result = merge(player, adaptPlayer, lead, partner);
    String text = resultText(result, lead, partner);
    (result == MutationResult.Status.MUTATED ? FConst.success(text) : FConst.error(text))
        .send(new PlayerCommandSource(player));
    if (result != MutationResult.Status.MUTATED) {
      return result;
    }
    SkillRegistry registry = registry();
    MutatedSkill mutated = registry == null ? null
        : registry.getMutatedSkill(adaptPlayer.getData().mutationOf(SkillHost.lineKey(lead)));
    if (mutated != null) {
      AdaptScreens.openSkill(player, mutated);
    }
    return result;
  }

  /** Mutates with every rule checked again and plays the mutation sound when it went through. */
  public static MutationResult.Status merge(PlayerRef player, AdaptPlayer adaptPlayer, Skill<?> lead, Skill<?> partner) {
    MutationResult.Status result = PlayerApi.profile(adaptPlayer).mutate(lead.getId(), partner.getId()).status();
    if (result == MutationResult.Status.MUTATED) {
      SkillUpgrades.SOUNDS.mutation(Kits.of(lead).sounds(), Kits.of(partner).sounds(), SoundStage.following(lead, player));
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
          .setName(TextFormat.RED + AdaptLanguage.text(MutationMessages.UNMUTATE))
          .onLeftClick((MenuClick e) -> window.updateElement(position, row,
              unmutateButton(window, position, row, mutated, player, true)));
    }
    return button.setMaterial(CONFIRM_ARMED)
        .setName(TextFormat.RED + AdaptLanguage.text(MutationMessages.UNMUTATE_CONFIRM))
        .setHint(MenuHint.destructive(AdaptLanguage.text(MutationMessages.UNMUTATE_CONFIRM)))
        .onLeftClick((MenuClick e) -> {
          window.close();
          AdaptPlayer adaptPlayer = adaptPlayer(player);
          SkillPair mutation = mutated.getPair();
          if (adaptPlayer == null || !mayUnmutate(player) || !unmutate(player, adaptPlayer, mutation)) {
            return;
          }
          FConst.success(unmutatedText(mutation, player.name())).send(new PlayerCommandSource(player));
          SkillRegistry registry = registry();
          Skill<?> lead = registry == null ? null : registry.getSkill(mutation.lead());
          if (lead != null) {
            ComponentHost.of(lead).openGui(player);
          }
        });
  }

  /**
   * Undoes {@code mutation} for an online player through their profile, which tells their client to refresh. False
   * when they do not hold it or a listener refused.
   */
  public static boolean unmutate(PlayerRef player, AdaptPlayer adaptPlayer, SkillPair mutation) {
    return PlayerApi.profile(adaptPlayer).unmutate(mutation.lead()).succeeded();
  }

  /** What an unmutate says: the mutation removed and both parents back. */
  public static String unmutatedText(SkillPair mutation, String playerName) {
    SkillRegistry registry = registry();
    return AdaptLanguage.text(CommandRuntimeMessages.MUTATION_UNMUTATED,
        trusted("mutation", mutationName(registry, mutation)),
        untrusted("player", playerName),
        trusted("lead", skillName(registry, mutation.lead())),
        trusted("partner", skillName(registry, mutation.partner())));
  }

  /** The pair's name in the viewer's language; its line key while either parent is not registered. */
  private static String mutationName(SkillRegistry registry, SkillPair pair) {
    Skill<?> lead = registry == null ? null : registry.getSkill(pair.lead());
    Skill<?> partner = registry == null ? null : registry.getSkill(pair.partner());
    return lead == null || partner == null ? pair.key() : mutationName(lead, partner);
  }

  private static String mutationName(Skill<?> lead, Skill<?> partner) {
    SkillRegistry registry = registry();
    PairRegistry claims = registry == null ? new PairRegistry() : registry.getPairRegistry();
    return AdaptLanguage.render(MutatedText.mutationName(MutatedText.VIEWER, new MutatedText.Parents(lead, partner, claims)));
  }

  private static String skillName(SkillRegistry registry, Id skillId) {
    Skill<?> skill = registry == null ? null : registry.getSkill(skillId);
    return skill == null ? skillId.toString() : skill.getLocalizedName();
  }

  /** The player-facing text of a merge result. */
  public static String resultText(MutationResult.Status result, Skill<?> lead, Skill<?> partner) {
    AdaptConfig.Mutations config = config();
    return switch (result) {
      case MUTATED -> AdaptLanguage.text(CommandRuntimeMessages.MUTATION_CREATED,
          trusted("lead", lead.getLocalizedName()), trusted("partner", partner.getLocalizedName()),
          trusted("mutation", mutationName(lead, partner)));
      case UNKNOWN_SKILL -> AdaptLanguage.text(CommandRuntimeMessages.MUTATION_UNKNOWN_SKILL);
      case UNAVAILABLE_SKILL -> AdaptLanguage.text(CommandRuntimeMessages.MUTATION_UNAVAILABLE_SKILL);
      case SAME_SKILL -> AdaptLanguage.text(CommandRuntimeMessages.MUTATION_SAME_SKILL);
      case ALREADY_MUTATED -> AdaptLanguage.text(CommandRuntimeMessages.MUTATION_ALREADY_MUTATED);
      case LEVEL_TOO_LOW -> AdaptLanguage.text(CommandRuntimeMessages.MUTATION_LEVEL_TOO_LOW,
          trusted("level", config.getRequiredSkillLevel()));
      case LIMIT_REACHED -> AdaptLanguage.text(CommandRuntimeMessages.MUTATION_LIMIT_REACHED,
          trusted("limit", config.getMaxMutations()));
      case CANCELLED -> AdaptLanguage.text(CommandRuntimeMessages.MUTATION_CANCELLED);
      case UNMUTATED, NOT_MUTATED -> throw new IllegalArgumentException("Not a merge result: " + result);
    };
  }

  static List<String> lostLines(MutationOffers.Preview preview) {
    List<String> lines = new ArrayList<>();
    String higher = preview.higher().getLocalizedName();
    if (preview.lostLevels() > 0) {
      lines.add(TextFormat.GRAY + AdaptLanguage.text(MutationMessages.LOST_LEVELS,
          trusted("count", TextFormat.WHITE + "" + preview.lostLevels() + TextFormat.GRAY), trusted("skill", higher)));
    }
    if (preview.lostKnowledge() > 0) {
      lines.add(TextFormat.GRAY + AdaptLanguage.text(MutationMessages.LOST_KNOWLEDGE,
          trusted("count", TextFormat.WHITE + "" + preview.lostKnowledge() + TextFormat.GRAY), trusted("skill", higher)));
    }
    for (SkillMutations.HeldAdaptation held : preview.unlearned()) {
      lines.add(TextFormat.GRAY + AdaptLanguage.text(held.adaptation().isPermanent()
              ? MutationMessages.LOST_PERMANENT_ADAPTATION : MutationMessages.LOST_ADAPTATION,
          trusted("adaptation", TextFormat.strip(held.adaptation().getDisplayName())),
          trusted("level", Format.toRoman(held.level()))));
    }
    if (lines.isEmpty()) {
      lines.add(TextFormat.GRAY + AdaptLanguage.text(MutationMessages.NOTHING_LOST));
    }
    return lines;
  }

  private static void skillMenu(PlayerRef player, PlayerData data, List<Skill<?>> skills, Consumer<Skill<?>> pick,
                                Runnable back) {
    if (!AdaptApi.scheduler().isTickThread()) {
      AdaptApi.scheduler().runEntity(player, () -> skillMenu(player, data, skills, pick, back));
      return;
    }
    int skillRows = Math.max(1, (skills.size() + COLUMNS - 1) / COLUMNS);
    GuiWindow window = new GuiWindow(player);
    GuiTheme.apply(window, "mutation/pick");
    window.setViewportHeight(skillRows + 2);
    window.setLayout(MenuLayout.GRID);
    window.setElement(0, 0, new GuiElement("mutation-heading")
        .setMaterial(Id.minecraft("nether_star"))
        .setName(TextFormat.LIGHT_PURPLE + AdaptLanguage.text(MutationMessages.PICK_LEAD))
        .setRole(MenuRole.HEADER));
    for (int index = 0; index < skills.size(); index++) {
      Skill<?> skill = skills.get(index);
      int row = index / COLUMNS;
      int inRow = Math.min(COLUMNS, skills.size() - (row * COLUMNS));
      window.setElement(GuiLayout.centeredPosition(index % COLUMNS, inRow), row + 1,
          skillTile("mutation-skill-" + SkillHost.lineKey(skill), skill, data)
              .setRole(MenuRole.NAVIGATION)
              .onLeftClick((MenuClick e) -> pick.accept(skill)));
    }
    window.setElement(0, skillRows + 1, GuiTheme.element("mutation-back", Id.minecraft("arrow"), "gui", "navigation", "back")
        .setName(TextFormat.GRAY + AdaptLanguage.text(SnippetsMessages.GUI_BACK))
        .onLeftClick((MenuClick e) -> back.run()));
    window.setTitle(TextFormat.GRAY + AdaptLanguage.text(MutationMessages.MUTATE));
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
