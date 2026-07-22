package art.arcane.adapt.content.gui;

import art.arcane.adapt.Adapt;
import art.arcane.adapt.api.mutation.MutationManager;
import art.arcane.adapt.api.mutation.MutationDomain;
import art.arcane.adapt.api.mutation.MutationQualification;
import art.arcane.adapt.api.mutation.MutationSelectionResult;
import art.arcane.adapt.api.mutation.MutationSnapshot;
import art.arcane.adapt.api.mutation.MutationState;
import art.arcane.adapt.api.mutation.MutationType;
import art.arcane.adapt.api.mutation.PlayerMutationData;
import art.arcane.adapt.api.world.AdaptDebugMode;
import art.arcane.adapt.api.world.AdaptPlayer;
import art.arcane.adapt.localization.AdaptLanguage;
import art.arcane.adapt.localization.catalog.CommandRuntimeMessages;
import art.arcane.adapt.localization.catalog.GuiMessages;
import art.arcane.adapt.localization.catalog.MutationMessages;
import art.arcane.adapt.service.MutationRuntimeSVC;
import art.arcane.adapt.service.MutationSVC;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.inventorygui.GuiEffects;
import art.arcane.adapt.util.common.inventorygui.GuiLayout;
import art.arcane.adapt.util.common.inventorygui.GuiTheme;
import art.arcane.adapt.util.common.scheduling.J;
import art.arcane.volmlib.util.data.MaterialBlock;
import art.arcane.volmlib.util.format.Form;
import art.arcane.volmlib.util.inventorygui.Element;
import art.arcane.volmlib.util.inventorygui.UIElement;
import art.arcane.volmlib.util.inventorygui.UIWindow;
import art.arcane.volmlib.util.localization.TextKey;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static art.arcane.volmlib.util.localization.MessageArgument.trusted;
import static art.arcane.volmlib.util.localization.MessageArgument.untrusted;

public final class MutationGui {
  private static final int CARD_COLUMNS = 5;
  private static final int CARD_ROWS = 3;
  private static final int CARDS_PER_PAGE = CARD_COLUMNS * CARD_ROWS;

  private MutationGui() {
  }

  public static void open(Player player) {
    open(player, 0);
  }

  public static void open(Player player, int page) {
    if (!preparePlayerThread(player, () -> open(player, page))) {
      return;
    }
    if (!player.hasPermission("adapt.mutations") && !AdaptDebugMode.isActive(player)) {
      player.sendMessage(C.RED + AdaptLanguage.text(
          CommandRuntimeMessages.MISSING_PERMISSION,
          trusted("permission", "adapt.mutations")
      ));
      return;
    }

    MutationManager manager = manager();
    AdaptPlayer adaptPlayer = adaptPlayer(player);
    if (manager == null || adaptPlayer == null) {
      player.sendMessage(C.RED + AdaptLanguage.text(MutationMessages.MUTATIONS_UNAVAILABLE));
      return;
    }
    if (!manager.getConfig().isEnabled()) {
      player.sendMessage(C.YELLOW + AdaptLanguage.text(MutationMessages.GUI_DISABLED));
      return;
    }

    MutationSnapshot snapshot = manager.reconcile(player);
    MutationGuiModel.View view = MutationGuiModel.build(
        adaptPlayer.getData().getLevel(),
        manager.hasValidBookshelfAccess(player),
        snapshot
    );
    int pageCount = Math.max(1, (int) Math.ceil(view.cards().size() / (double) CARDS_PER_PAGE));
    int currentPage = Math.max(0, Math.min(pageCount - 1, page));
    int start = currentPage * CARDS_PER_PAGE;
    int end = Math.min(view.cards().size(), start + CARDS_PER_PAGE);

    UIWindow window = new UIWindow(Adapt.instance, player);
    GuiTheme.apply(window, "mutations/" + currentPage);
    window.setViewportHeight(6);
    applySummary(window, player, view, currentPage);

    List<GuiEffects.Placement> reveal = new ArrayList<>();
    for (int index = start; index < end; index++) {
      int localIndex = index - start;
      int row = 2 + (localIndex / CARD_COLUMNS);
      int column = localIndex % CARD_COLUMNS;
      int rowCount = Math.min(CARD_COLUMNS, end - (start + ((row - 2) * CARD_COLUMNS)));
      int position = GuiLayout.spacedFivePosition(column, rowCount);
      MutationGuiModel.Card card = view.cards().get(index);
      reveal.add(new GuiEffects.Placement(position, row, mutationCard(player, card, currentPage)));
    }
    GuiEffects.applyReveal(window, reveal);
    applyNavigation(window, player, currentPage, pageCount);

    window.setTitle(C.DARK_PURPLE + AdaptLanguage.text(
        MutationMessages.GUI_TITLE,
        trusted("level", view.playerLevel())
    ));
    openWindow(player, window);
  }

  public static void reopenFromTag(Player player, String tag) {
    if (tag == null || tag.isBlank() || "mutations".equals(tag)) {
      open(player);
      return;
    }

    String[] parts = tag.split("/");
    if (parts.length >= 3 && "detail".equals(parts[1])) {
      MutationManager manager = manager();
      MutationType type = manager == null ? null : manager.find(parts[2]);
      if (type != null) {
        openDetails(player, type, parsePage(parts, 3));
        return;
      }
    }

    open(player, parsePage(parts, 1));
  }

  private static void applySummary(
      UIWindow window,
      Player player,
      MutationGuiModel.View view,
      int page
  ) {
    window.setElement(-3, 0, slotElement(player, view.slotOne(), page));
    window.setElement(3, 0, slotElement(player, view.slotTwo(), page));
    window.setElement(0, 0, new UIElement("mutation-perfect")
        .setMaterial(new MaterialBlock(view.perfect() ? Material.NETHER_STAR : Material.AMETHYST_SHARD))
        .setName((view.perfect() ? C.GOLD : C.LIGHT_PURPLE) + AdaptLanguage.text(MutationMessages.GUI_PERFECT))
        .addLore(view.perfect()
            ? C.WHITE + AdaptLanguage.text(MutationMessages.GUI_PERFECT_ACTIVE)
            : C.GRAY + AdaptLanguage.text(MutationMessages.GUI_PERFECT_LOCKED))
        .setProgress(view.perfect() ? 1D : 0D));

    window.setElement(-2, 1, new UIElement("mutation-cooperative")
        .setMaterial(new MaterialBlock(view.cooperativeOptIn() ? Material.LIME_DYE : Material.GRAY_DYE))
        .setName((view.cooperativeOptIn() ? C.GREEN : C.GRAY) + AdaptLanguage.text(
            view.cooperativeOptIn() ? MutationMessages.GUI_COOPERATIVE_ON : MutationMessages.GUI_COOPERATIVE_OFF
        ))
        .addLore(C.GRAY + AdaptLanguage.text(MutationMessages.GUI_COOPERATIVE_ALLOW))
        .addLore(C.WHITE + AdaptLanguage.text(
            view.cooperativeOptIn() ? MutationMessages.GUI_COOPERATIVE_OPT_OUT : MutationMessages.GUI_COOPERATIVE_OPT_IN
        ))
        .onLeftClick(event -> toggleCooperative(player, view.cooperativeOptIn(), page)));

    window.setElement(2, 1, new UIElement("mutation-access")
        .setMaterial(new MaterialBlock(view.bookshelfAuthorized() ? Material.BOOKSHELF : Material.LECTERN))
        .setName((view.bookshelfAuthorized() ? C.GREEN : C.YELLOW) + AdaptLanguage.text(
            view.bookshelfAuthorized() ? MutationMessages.GUI_CHANGES_AVAILABLE : MutationMessages.GUI_VIEW_ONLY
        ))
        .addLore(view.bookshelfAuthorized()
            ? C.GRAY + AdaptLanguage.text(MutationMessages.GUI_MAY_CHANGE)
            : C.GRAY + AdaptLanguage.text(MutationMessages.GUI_VISIT_BOOKSHELF)));

    if (!view.unavailableDiscoveries().isEmpty()) {
      Element unavailable = new UIElement("mutation-unavailable-discoveries")
          .setMaterial(new MaterialBlock(Material.WRITABLE_BOOK))
          .setName(C.YELLOW + AdaptLanguage.text(MutationMessages.GUI_UNAVAILABLE_DISCOVERIES))
          .addLore(C.GRAY + AdaptLanguage.text(MutationMessages.GUI_SAVED_NOT_INSTALLED));
      for (String mutationId : view.unavailableDiscoveries()) {
        unavailable.addLore(C.DARK_GRAY + "• " + mutationId);
      }
      window.setElement(0, 1, unavailable);
    }
  }

  private static Element slotElement(Player player, MutationGuiModel.Slot slot, int page) {
    MutationType selected = findType(slot.mutationId());
    Material material = selected == null ? (slot.unlocked() ? Material.GLASS_BOTTLE : Material.BARRIER) : selected.icon();
    Element element = new UIElement("mutation-slot-" + slot.index())
        .setMaterial(new MaterialBlock(material))
        .setName(slot.unlocked()
            ? C.AQUA + AdaptLanguage.text(
                MutationMessages.GUI_SLOT_VALUE,
                trusted("slot", slot.index()),
                trusted("mutation", slot.displayName())
            )
            : C.DARK_GRAY + AdaptLanguage.text(MutationMessages.GUI_SLOT_LOCKED, trusted("slot", slot.index())))
        .addLore(C.GRAY + slot.reason());

    if (selected != null) {
      element.onLeftClick(event -> openDetails(player, selected, page));
    }
    if (slot.canClear()) {
      element.addLore(C.RED + AdaptLanguage.text(MutationMessages.GUI_RIGHT_CLICK_CLEAR))
          .onRightClick(event -> confirmClear(player, slot.index(), page));
    }
    return element;
  }

  private static Element mutationCard(Player player, MutationGuiModel.Card card, int page) {
    C stateColor = stateColor(card.state());
    Element element = new UIElement("mutation-" + card.type().id())
        .setMaterial(new MaterialBlock(card.type().icon()))
        .setName(stateColor + card.type().displayName())
        .addLore(C.DARK_GRAY + AdaptLanguage.text(
            MutationMessages.GUI_SKILL_GROUPS,
            trusted("first", formatDomain(card.type().firstDomain())),
            trusted("second", formatDomain(card.type().secondDomain()))
        ))
        .addLore(C.GRAY + card.reason())
        .addLore((card.discovered() ? C.GOLD : C.DARK_GRAY) + AdaptLanguage.text(
            card.discovered() ? MutationMessages.GUI_DISCOVERED : MutationMessages.GUI_UNDISCOVERED
        ))
        .addLore(card.selectedSlot() > 0
            ? C.AQUA + AdaptLanguage.text(MutationMessages.GUI_EQUIPPED_SLOT, trusted("slot", card.selectedSlot()))
            : C.WHITE + AdaptLanguage.text(MutationMessages.GUI_CLICK_INSPECT))
        .setProgress(card.expressed() ? 1D : 0D)
        .onLeftClick(event -> openDetails(player, card.type(), page));
    return element;
  }

  private static void openDetails(Player player, MutationType type, int page) {
    if (!preparePlayerThread(player, () -> openDetails(player, type, page))) {
      return;
    }

    MutationManager manager = manager();
    AdaptPlayer adaptPlayer = adaptPlayer(player);
    if (manager == null || adaptPlayer == null) {
      open(player, page);
      return;
    }
    if (!manager.getConfig().isEnabled()) {
      player.sendMessage(C.YELLOW + AdaptLanguage.text(MutationMessages.GUI_DISABLED));
      return;
    }

    MutationSnapshot snapshot = manager.reconcile(player);
    MutationGuiModel.View view = MutationGuiModel.build(
        adaptPlayer.getData().getLevel(),
        manager.hasValidBookshelfAccess(player),
        snapshot
    );
    MutationGuiModel.Card card = card(view, type);
    if (card == null) {
      open(player, page);
      return;
    }

    UIWindow window = new UIWindow(Adapt.instance, player);
    GuiTheme.apply(window, "mutations/detail/" + type.id() + "/" + page);
    window.setViewportHeight(5);
    MutationQualification qualification = manager.qualification(player, type);
    Element detail = new UIElement("mutation-detail-" + type.id())
        .setMaterial(new MaterialBlock(type.icon()))
        .setName(stateColor(card.state()) + type.displayName())
        .addLore(C.DARK_GRAY + AdaptLanguage.text(
            MutationMessages.GUI_SKILL_GROUPS,
            trusted("first", formatDomain(type.firstDomain())),
            trusted("second", formatDomain(type.secondDomain()))
        ))
        .addLore(C.WHITE + AdaptLanguage.text(
            MutationMessages.GUI_REQUIRES,
            trusted("level", manager.getConfig().getMinimumAdaptationLevel())
        ))
        .addLore(C.GREEN + AdaptLanguage.text(MutationMessages.GUI_BENEFIT, trusted("benefit", type.benefit())))
        .addLore(C.RED + AdaptLanguage.text(MutationMessages.GUI_BURDEN, trusted("burden", type.burden())))
        .addLore(C.GOLD + AdaptLanguage.text(
            MutationMessages.GUI_PERFECT_RESULT,
            trusted("result", type.perfectResult())
        ))
        .addLore(C.LIGHT_PURPLE + AdaptLanguage.text(MutationMessages.GUI_TELL, trusted("tell", type.tell())))
        .addLore(C.AQUA + AdaptLanguage.text(MutationMessages.GUI_CONTROL, trusted("control", type.control())))
        .addLore(C.DARK_GRAY + AdaptLanguage.text(type.pvpRelevant() ? MutationMessages.GUI_PVP : MutationMessages.GUI_NO_PVP))
        .addLore(C.WHITE + AdaptLanguage.text(
            MutationMessages.GUI_STATUS,
            trusted("state", stateColor(card.state()) + formatState(card.state()))
        ))
        .addLore(C.GRAY + card.reason());
    detail.addLore(qualification.qualifyingAdaptations().isEmpty()
        ? C.DARK_GRAY + AdaptLanguage.text(MutationMessages.GUI_MATCHING_NONE)
        : C.GRAY + AdaptLanguage.text(
            MutationMessages.GUI_MATCHING,
            trusted("adaptations", String.join(", ", qualification.qualifyingAdaptations()))
        ));
    window.setElement(0, 0, detail);

    window.setElement(-2, 2, selectSlotElement(player, type, card, view.slotOne(), view.slotTwo(), page));
    window.setElement(2, 2, selectSlotElement(player, type, card, view.slotTwo(), view.slotOne(), page));
    applyEquipmentControl(window, player, type, card, view, adaptPlayer, page);
    window.setElement(0, 4, mutationBackElement(player, page));
    window.setTitle(C.DARK_PURPLE + type.displayName());
    openWindow(player, window);
  }

  private static Element selectSlotElement(
      Player player,
      MutationType type,
      MutationGuiModel.Card card,
      MutationGuiModel.Slot slot,
      MutationGuiModel.Slot otherSlot,
      int page
  ) {
    if (!slot.unlocked()) {
      return new UIElement("mutation-select-locked-" + slot.index())
          .setMaterial(new MaterialBlock(Material.BARRIER))
          .setName(C.DARK_GRAY + AdaptLanguage.text(
              MutationMessages.GUI_SLOT_LOCKED_NAME,
              trusted("slot", slot.index())
          ))
          .addLore(C.GRAY + slot.reason());
    }

    if (!managerHasAccess(player)) {
      return new UIElement("mutation-select-view-only-" + slot.index())
          .setMaterial(new MaterialBlock(Material.LECTERN))
          .setName(C.YELLOW + AdaptLanguage.text(
              MutationMessages.GUI_SLOT_VIEW_ONLY,
              trusted("slot", slot.index())
          ))
          .addLore(C.GRAY + AdaptLanguage.text(MutationMessages.GUI_BOOKSHELF_BEFORE_CHANGE));
    }

    if (card.selectedSlot() == slot.index()) {
      return new UIElement("mutation-selected-" + slot.index())
          .setMaterial(new MaterialBlock(type.icon()))
          .setName(C.AQUA + AdaptLanguage.text(
              MutationMessages.GUI_EQUIPPED_IN_SLOT,
              trusted("slot", slot.index())
          ))
          .addLore(C.GRAY + card.reason());
    }

    MutationType other = findType(otherSlot.mutationId());
    if (other == type) {
      return new UIElement("mutation-duplicate-" + slot.index())
          .setMaterial(new MaterialBlock(Material.GRAY_DYE))
          .setName(C.RED + AdaptLanguage.text(
              MutationMessages.GUI_ALREADY_EQUIPPED_SLOT,
              trusted("slot", otherSlot.index())
          ))
          .addLore(C.GRAY + AdaptLanguage.text(MutationMessages.DUPLICATE_OCCUPANCY));
    }
    if (other != null && mutationsConflict(type, other)) {
      return new UIElement("mutation-conflict-" + slot.index())
          .setMaterial(new MaterialBlock(Material.BARRIER))
          .setName(C.RED + AdaptLanguage.text(
              MutationMessages.GUI_CONFLICTS_WITH,
              trusted("mutation", other.displayName())
          ))
          .addLore(C.GRAY + AdaptLanguage.text(
              MutationMessages.GUI_CLEAR_OTHER_FIRST,
              trusted("slot", otherSlot.index())
          ));
    }

    if (card.state() != MutationState.AVAILABLE) {
      return new UIElement("mutation-unavailable-" + slot.index())
          .setMaterial(new MaterialBlock(Material.GRAY_DYE))
          .setName(C.DARK_GRAY + AdaptLanguage.text(
              MutationMessages.GUI_UNAVAILABLE_SLOT,
              trusted("slot", slot.index())
          ))
          .addLore(C.GRAY + card.reason());
    }

    return new UIElement("mutation-select-" + slot.index())
        .setMaterial(new MaterialBlock(Material.ENCHANTED_BOOK))
        .setName(C.GREEN + AdaptLanguage.text(MutationMessages.GUI_EQUIP_SLOT, trusted("slot", slot.index())))
        .addLore(C.GRAY + AdaptLanguage.text(
            MutationMessages.GUI_CURRENT,
            trusted("mutation", slot.displayName())
        ))
        .addLore(C.WHITE + AdaptLanguage.text(MutationMessages.GUI_CLICK_PREVIEW))
        .onLeftClick(event -> confirmSelect(player, slot.index(), type, page));
  }

  private static void confirmSelect(Player player, int slot, MutationType type, int page) {
    if (!preparePlayerThread(player, () -> confirmSelect(player, slot, type, page))) {
      return;
    }

    MutationManager manager = manager();
    AdaptPlayer adaptPlayer = adaptPlayer(player);
    if (manager == null || adaptPlayer == null || !manager.hasValidBookshelfAccess(player)) {
      openDetails(player, type, page);
      return;
    }

    MutationGuiModel.View view = MutationGuiModel.build(
        adaptPlayer.getData().getLevel(),
        true,
        manager.snapshot(player)
    );
    MutationGuiModel.Slot target = slot == 1 ? view.slotOne() : view.slotTwo();
    MutationGuiModel.Slot other = slot == 1 ? view.slotTwo() : view.slotOne();
    String expectedCurrentId = normalizeSlotId(target.mutationId());
    String expectedOtherId = normalizeSlotId(other.mutationId());
    UIWindow window = new UIWindow(Adapt.instance, player);
    GuiTheme.apply(window, "mutations/confirm/select/" + slot + "/" + type.id() + "/" + page);
    window.setViewportHeight(3);
    window.setElement(0, 0, new UIElement("mutation-confirm-preview")
        .setMaterial(new MaterialBlock(type.icon()))
        .setName(C.LIGHT_PURPLE + AdaptLanguage.text(
            MutationMessages.GUI_CONFIRM_SLOT,
            trusted("mutation", type.displayName()),
            trusted("slot", slot)
        ))
        .addLore(C.GRAY + AdaptLanguage.text(
            MutationMessages.GUI_REPLACING,
            trusted("mutation", target.displayName())
        ))
        .addLore(C.GREEN + AdaptLanguage.text(MutationMessages.GUI_BENEFIT, trusted("benefit", type.benefit())))
        .addLore(C.RED + AdaptLanguage.text(MutationMessages.GUI_BURDEN, trusted("burden", type.burden())))
        .addLore(C.GOLD + AdaptLanguage.text(
            MutationMessages.GUI_PERFECT_RESULT,
            trusted("result", type.perfectResult())
        ))
        .addLore(C.AQUA + AdaptLanguage.text(
            MutationMessages.GUI_OTHER_SLOT,
            trusted("mutation", other.displayName())
        ))
        .addLore(C.GREEN + AdaptLanguage.text(
            MutationMessages.GUI_WITH_OTHER,
            trusted("interaction", interactionPreview(type, other))
        ))
        .addLore(C.YELLOW + AdaptLanguage.text(
            MutationMessages.GUI_SLOT_COOLDOWN,
            trusted("duration", Form.duration(manager.getConfig().getSwitchCooldownMillis(), 1))
        )));
    window.setElement(-2, 2, new UIElement("mutation-confirm-yes")
        .setMaterial(new MaterialBlock(Material.LIME_CONCRETE))
        .setName(C.GREEN + AdaptLanguage.text(MutationMessages.GUI_CONFIRM_CHANGE))
        .onLeftClick(event -> select(player, slot, type, expectedCurrentId, expectedOtherId, page)));
    window.setElement(2, 2, new UIElement("mutation-confirm-no")
        .setMaterial(new MaterialBlock(Material.RED_CONCRETE))
        .setName(C.RED + AdaptLanguage.text(GuiMessages.CANCEL))
        .onLeftClick(event -> openDetails(player, type, page)));
    window.setTitle(C.DARK_PURPLE + AdaptLanguage.text(MutationMessages.GUI_CONFIRM_CHANGE_TITLE));
    openWindow(player, window);
  }

  private static void confirmClear(Player player, int slot, int page) {
    if (!preparePlayerThread(player, () -> confirmClear(player, slot, page))) {
      return;
    }

    MutationManager manager = manager();
    AdaptPlayer adaptPlayer = adaptPlayer(player);
    if (manager == null || adaptPlayer == null || !manager.hasValidBookshelfAccess(player)) {
      open(player, page);
      return;
    }

    MutationGuiModel.View view = MutationGuiModel.build(
        adaptPlayer.getData().getLevel(),
        true,
        manager.snapshot(player)
    );
    MutationGuiModel.Slot target = slot == 1 ? view.slotOne() : view.slotTwo();
    String expectedCurrentId = normalizeSlotId(target.mutationId());
    UIWindow window = new UIWindow(Adapt.instance, player);
    GuiTheme.apply(window, "mutations/confirm/clear/" + slot + "/" + page);
    window.setViewportHeight(3);
    window.setElement(0, 0, new UIElement("mutation-clear-preview")
        .setMaterial(new MaterialBlock(Material.GLASS_BOTTLE))
        .setName(C.RED + AdaptLanguage.text(MutationMessages.GUI_CLEAR_SLOT, trusted("slot", slot)))
        .addLore(C.GRAY + AdaptLanguage.text(
            MutationMessages.GUI_UNEQUIP,
            trusted("mutation", target.displayName())
        ))
        .addLore(C.YELLOW + AdaptLanguage.text(MutationMessages.GUI_NORMAL_COOLDOWN)));
    window.setElement(-2, 2, new UIElement("mutation-clear-yes")
        .setMaterial(new MaterialBlock(Material.LIME_CONCRETE))
        .setName(C.GREEN + AdaptLanguage.text(MutationMessages.GUI_CONFIRM_CLEAR))
        .onLeftClick(event -> clear(player, slot, expectedCurrentId, page)));
    window.setElement(2, 2, new UIElement("mutation-clear-no")
        .setMaterial(new MaterialBlock(Material.RED_CONCRETE))
        .setName(C.RED + AdaptLanguage.text(GuiMessages.CANCEL))
        .onLeftClick(event -> open(player, page)));
    window.setTitle(C.DARK_PURPLE + AdaptLanguage.text(MutationMessages.GUI_CONFIRM_CHANGE_TITLE));
    openWindow(player, window);
  }

  private static void select(
      Player player,
      int slot,
      MutationType type,
      String expectedCurrentId,
      String expectedOtherId,
      int page
  ) {
    MutationManager manager = manager();
    if (manager == null) {
      open(player, page);
      return;
    }
    MutationSelectionResult result = manager.select(
        player,
        slot,
        type,
        false,
        expectedCurrentId,
        expectedOtherId
    );
    sendResult(player, result);
    open(player, page);
  }

  private static void clear(Player player, int slot, String expectedCurrentId, int page) {
    MutationManager manager = manager();
    if (manager == null) {
      open(player, page);
      return;
    }
    MutationSelectionResult result = manager.clear(player, slot, false, expectedCurrentId);
    sendResult(player, result);
    open(player, page);
  }

  private static void applyEquipmentControl(
      UIWindow window,
      Player player,
      MutationType type,
      MutationGuiModel.Card card,
      MutationGuiModel.View view,
      AdaptPlayer adaptPlayer,
      int page
  ) {
    EquipmentControl control = equipmentControl(
        type,
        card.selectedSlot() > 0,
        card.expressed(),
        view.bookshelfAuthorized(),
        player.hasPermission("adapt.mutations") || AdaptDebugMode.isActive(player),
        adaptPlayer.getData().getMutationData(),
        System.currentTimeMillis()
    );
    if (control == null) {
      return;
    }
    window.setElement(0, 2, equipmentControlElement(player, type, control, page));
  }

  private static Element equipmentControlElement(
      Player player,
      MutationType type,
      EquipmentControl control,
      int page
  ) {
    return switch (control.action()) {
      case ATTUNE_TEMPERBOUND -> new UIElement("mutation-temperbound-attune")
          .setMaterial(new MaterialBlock(Material.ANVIL))
          .setName(C.GREEN + AdaptLanguage.text(MutationMessages.GUI_LINK_ARMOR))
          .addLore(C.GRAY + AdaptLanguage.text(MutationMessages.GUI_LINK_ARMOR_LORE))
          .onLeftClick(event -> runEquipmentAction(player, type, control.action(), page));
      case DISSOLVE_TEMPERBOUND -> new UIElement("mutation-temperbound-dissolve")
          .setMaterial(new MaterialBlock(Material.SHEARS))
          .setName(C.RED + AdaptLanguage.text(MutationMessages.GUI_UNLINK_ARMOR))
          .addLore(C.GRAY + AdaptLanguage.text(MutationMessages.GUI_UNLINK_ARMOR_LORE))
          .addLore(C.YELLOW + AdaptLanguage.text(MutationMessages.GUI_REVIEW_CONFIRM))
          .onLeftClick(event -> confirmEquipmentAction(player, type, control.action(), page));
      case BIND_MASTERWORK -> masterworkBindElement(player, type, control, page);
      case ABANDON_MASTERWORK -> new UIElement("mutation-masterwork-abandon")
          .setMaterial(new MaterialBlock(Material.FLINT_AND_STEEL))
          .setName(C.RED + AdaptLanguage.text(MutationMessages.GUI_UNLINK_MASTERWORK))
          .addLore(C.GRAY + AdaptLanguage.text(MutationMessages.GUI_UNLINK_MASTERWORK_LORE))
          .addLore(C.YELLOW + AdaptLanguage.text(MutationMessages.GUI_REPLACEMENT_COOLDOWN_BEGINS))
          .onLeftClick(event -> confirmEquipmentAction(player, type, control.action(), page));
      case BIND_DEEPBLOOD -> new UIElement("mutation-deepblood-bind")
          .setMaterial(new MaterialBlock(Material.DEEPSLATE_DIAMOND_ORE))
          .setName(C.GREEN + AdaptLanguage.text(MutationMessages.GUI_BIND_HELD_TOOL))
          .addLore(C.GRAY + AdaptLanguage.text(MutationMessages.GUI_DEEPBLOOD_BIND_LORE))
          .addLore(C.YELLOW + AdaptLanguage.text(MutationMessages.GUI_DEEPBLOOD_REPLACE_LORE))
          .onLeftClick(event -> runEquipmentAction(player, type, control.action(), page));
    };
  }

  private static Element masterworkBindElement(
      Player player,
      MutationType type,
      EquipmentControl control,
      int page
  ) {
    Element element = new UIElement("mutation-masterwork-bind")
        .setMaterial(new MaterialBlock(control.cooldownRemainingMillis() > 0L ? Material.CLOCK : Material.SMITHING_TABLE))
        .setName(control.cooldownRemainingMillis() > 0L
            ? C.YELLOW + AdaptLanguage.text(MutationMessages.GUI_BIND_COOLING)
            : C.GREEN + AdaptLanguage.text(MutationMessages.GUI_BIND_HELD_TOOL))
        .addLore(C.GRAY + AdaptLanguage.text(MutationMessages.GUI_MASTERWORK_BIND_LORE));
    if (control.cooldownRemainingMillis() > 0L) {
      return element.addLore(C.RED + AdaptLanguage.text(
          MutationMessages.GUI_REPLACEMENT_AVAILABLE,
          trusted("duration", Form.duration(control.cooldownRemainingMillis(), 1))
      ));
    }
    return element.onLeftClick(event -> runEquipmentAction(player, type, control.action(), page));
  }

  private static void confirmEquipmentAction(
      Player player,
      MutationType type,
      EquipmentAction action,
      int page
  ) {
    if (!preparePlayerThread(player, () -> confirmEquipmentAction(player, type, action, page))) {
      return;
    }
    EquipmentControl current = currentEquipmentControl(player, type);
    if (current == null || current.action() != action) {
      player.sendMessage(C.RED + AdaptLanguage.text(MutationMessages.GUI_EQUIPMENT_CHANGED));
      openDetails(player, type, page);
      return;
    }

    MutationManager manager = manager();
    if (manager == null) {
      openDetails(player, type, page);
      return;
    }
    String expectedBondId = equipmentBondId(player, type);
    UIWindow window = new UIWindow(Adapt.instance, player);
    GuiTheme.apply(window, "mutations/confirm/equipment/" + type.id() + "/" + action.name().toLowerCase(Locale.ROOT));
    window.setViewportHeight(3);
    if (action == EquipmentAction.DISSOLVE_TEMPERBOUND) {
      window.setElement(0, 0, new UIElement("mutation-temperbound-dissolve-preview")
          .setMaterial(new MaterialBlock(Material.SHEARS))
          .setName(C.RED + AdaptLanguage.text(MutationMessages.GUI_UNLINK_TEMPERBOUND))
          .addLore(C.GRAY + AdaptLanguage.text(MutationMessages.GUI_TEMPERBOUND_UNLINKED))
          .addLore(C.YELLOW + AdaptLanguage.text(MutationMessages.GUI_NO_REPAIR_RETURN)));
    } else {
      window.setElement(0, 0, new UIElement("mutation-masterwork-abandon-preview")
          .setMaterial(new MaterialBlock(Material.FLINT_AND_STEEL))
          .setName(C.RED + AdaptLanguage.text(MutationMessages.GUI_UNLINK_MASTERWORK_QUESTION))
          .addLore(C.GRAY + AdaptLanguage.text(MutationMessages.GUI_MASTERWORK_UNLINKED))
          .addLore(C.YELLOW + AdaptLanguage.text(
              MutationMessages.GUI_REPLACEMENT_COOLDOWN,
              trusted("duration", Form.duration(manager.getConfig().getMasterworkBond().getAbandonCooldownMillis(), 1))
          )));
    }
    window.setElement(-2, 2, new UIElement("mutation-equipment-confirm-yes")
        .setMaterial(new MaterialBlock(Material.LIME_CONCRETE))
        .setName(C.GREEN + AdaptLanguage.text(GuiMessages.CONFIRM))
        .onLeftClick(event -> runEquipmentAction(player, type, action, page, expectedBondId)));
    window.setElement(2, 2, new UIElement("mutation-equipment-confirm-no")
        .setMaterial(new MaterialBlock(Material.RED_CONCRETE))
        .setName(C.RED + AdaptLanguage.text(GuiMessages.CANCEL))
        .onLeftClick(event -> openDetails(player, type, page)));
    window.setTitle(C.DARK_PURPLE + AdaptLanguage.text(MutationMessages.GUI_CONFIRM_EQUIPMENT_TITLE));
    openWindow(player, window);
  }

  private static void runEquipmentAction(
      Player player,
      MutationType type,
      EquipmentAction action,
      int page
  ) {
    runEquipmentAction(player, type, action, page, null);
  }

  private static void runEquipmentAction(
      Player player,
      MutationType type,
      EquipmentAction action,
      int page,
      String expectedBondId
  ) {
    if (!preparePlayerThread(player, () -> runEquipmentAction(player, type, action, page, expectedBondId))) {
      return;
    }
    EquipmentControl current = currentEquipmentControl(player, type);
    if (current == null || current.action() != action) {
      player.sendMessage(C.RED + AdaptLanguage.text(MutationMessages.GUI_OPTION_UNAVAILABLE));
      openDetails(player, type, page);
      return;
    }
    if (expectedBondId != null && !expectedBondId.equals(equipmentBondId(player, type))) {
      player.sendMessage(C.RED + AdaptLanguage.text(MutationMessages.GUI_EQUIPMENT_CHANGED_REVIEW));
      openDetails(player, type, page);
      return;
    }

    MutationRuntimeSVC service = MutationRuntimeSVC.get();
    if (service == null) {
      player.sendMessage(C.RED + AdaptLanguage.text(MutationMessages.GUI_CONTROLS_UNAVAILABLE));
      openDetails(player, type, page);
      return;
    }
    boolean success = switch (action) {
      case ATTUNE_TEMPERBOUND -> service.attuneCurrentArmor(player);
      case DISSOLVE_TEMPERBOUND -> service.dissolveTemperbound(player);
      case BIND_MASTERWORK -> service.bindMasterwork(player);
      case ABANDON_MASTERWORK -> service.abandonMasterwork(player);
      case BIND_DEEPBLOOD -> service.bindDeepbloodTool(player);
    };
    sendEquipmentResult(player, action, success);
    openDetails(player, type, page);
  }

  private static void sendEquipmentResult(Player player, EquipmentAction action, boolean success) {
    if (success) {
      player.sendMessage(C.GREEN + equipmentSuccessMessage(player, action));
      return;
    }
    player.sendMessage(C.RED + equipmentFailureMessage(player, action));
  }

  private static String equipmentSuccessMessage(Player player, EquipmentAction action) {
    if (action != EquipmentAction.ABANDON_MASTERWORK) {
      return switch (action) {
        case ATTUNE_TEMPERBOUND -> AdaptLanguage.text(MutationMessages.EQUIPMENT_TEMPERBOUND_SUCCESS);
        case DISSOLVE_TEMPERBOUND -> AdaptLanguage.text(MutationMessages.EQUIPMENT_TEMPERBOUND_UNLINKED);
        case BIND_MASTERWORK -> AdaptLanguage.text(MutationMessages.EQUIPMENT_MASTERWORK_SUCCESS);
        case BIND_DEEPBLOOD -> AdaptLanguage.text(MutationMessages.EQUIPMENT_DEEPBLOOD_SUCCESS);
        case ABANDON_MASTERWORK -> "";
      };
    }
    long remaining = masterworkCooldownRemaining(player, System.currentTimeMillis());
    return remaining > 0L
        ? AdaptLanguage.text(
            MutationMessages.EQUIPMENT_MASTERWORK_UNLINKED_DELAY,
            trusted("duration", Form.duration(remaining, 1))
        )
        : AdaptLanguage.text(MutationMessages.EQUIPMENT_MASTERWORK_UNLINKED);
  }

  private static String equipmentFailureMessage(Player player, EquipmentAction action) {
    if (action == EquipmentAction.BIND_MASTERWORK) {
      long remaining = masterworkCooldownRemaining(player, System.currentTimeMillis());
      if (remaining > 0L) {
        return AdaptLanguage.text(
            MutationMessages.EQUIPMENT_REPLACEMENT_DELAY,
            trusted("duration", Form.duration(remaining, 1))
        );
      }
    }
    return switch (action) {
      case ATTUNE_TEMPERBOUND -> AdaptLanguage.text(MutationMessages.EQUIPMENT_TEMPERBOUND_REQUIREMENT);
      case DISSOLVE_TEMPERBOUND -> AdaptLanguage.text(MutationMessages.EQUIPMENT_TEMPERBOUND_FAILURE);
      case BIND_MASTERWORK -> AdaptLanguage.text(MutationMessages.EQUIPMENT_MASTERWORK_REQUIREMENT);
      case ABANDON_MASTERWORK -> AdaptLanguage.text(MutationMessages.EQUIPMENT_MASTERWORK_FAILURE);
      case BIND_DEEPBLOOD -> AdaptLanguage.text(MutationMessages.EQUIPMENT_DEEPBLOOD_REQUIREMENT);
    };
  }

  private static EquipmentControl currentEquipmentControl(Player player, MutationType type) {
    MutationManager manager = manager();
    AdaptPlayer adaptPlayer = adaptPlayer(player);
    if (manager == null || adaptPlayer == null || player == null || type == null) {
      return null;
    }
    MutationSnapshot snapshot = manager.reconcile(player);
    boolean selected = type.id().equalsIgnoreCase(snapshot.slotOneId())
        || type.id().equalsIgnoreCase(snapshot.slotTwoId());
    return equipmentControl(
        type,
        selected,
        snapshot.expressed().contains(type),
        manager.hasValidBookshelfAccess(player),
        player.hasPermission("adapt.mutations") || AdaptDebugMode.isActive(player),
        adaptPlayer.getData().getMutationData(),
        System.currentTimeMillis()
    );
  }

  static EquipmentControl equipmentControl(
      MutationType type,
      boolean selected,
      boolean expressed,
      boolean bookshelfAuthorized,
      boolean basePermission,
      PlayerMutationData data,
      long now
  ) {
    if (type == null || !selected || !expressed || !bookshelfAuthorized || !basePermission || data == null) {
      return null;
    }
    return switch (type) {
      case TEMPERBOUND -> new EquipmentControl(
          data.getTemperboundBondId().isBlank()
              ? EquipmentAction.ATTUNE_TEMPERBOUND
              : EquipmentAction.DISSOLVE_TEMPERBOUND,
          0L
      );
      case MASTERWORK_BOND -> new EquipmentControl(
          data.getMasterworkItemId().isBlank()
              ? EquipmentAction.BIND_MASTERWORK
              : EquipmentAction.ABANDON_MASTERWORK,
          data.getMasterworkItemId().isBlank()
              ? Math.max(0L, data.getMasterworkAbandonReadyAt() - Math.max(0L, now))
              : 0L
      );
      case DEEPBLOOD -> new EquipmentControl(EquipmentAction.BIND_DEEPBLOOD, 0L);
      default -> null;
    };
  }

  private static long masterworkCooldownRemaining(Player player, long now) {
    AdaptPlayer adaptPlayer = adaptPlayer(player);
    if (adaptPlayer == null) {
      return 0L;
    }
    return Math.max(0L, adaptPlayer.getData().getMutationData().getMasterworkAbandonReadyAt() - now);
  }

  private static String equipmentBondId(Player player, MutationType type) {
    AdaptPlayer adaptPlayer = adaptPlayer(player);
    if (adaptPlayer == null || type == null) {
      return "";
    }
    PlayerMutationData data = adaptPlayer.getData().getMutationData();
    return switch (type) {
      case TEMPERBOUND -> data.getTemperboundBondId();
      case MASTERWORK_BOND -> data.getMasterworkItemId();
      case DEEPBLOOD -> data.getDeepbloodToolId();
      default -> "";
    };
  }

  private static void toggleCooperative(Player player, boolean currentlyEnabled, int page) {
    MutationManager manager = manager();
    if (manager == null) {
      return;
    }
    manager.setCooperativeOptIn(player, !currentlyEnabled);
    open(player, page);
  }

  private static void sendResult(Player player, MutationSelectionResult result) {
    String message = result.message() == null || result.message().isBlank()
        ? AdaptLanguage.text(result.success() ? MutationMessages.RESULT_UPDATED : MutationMessages.RESULT_NOT_CHANGED)
        : result.message();
    if (!result.success() && result.cooldownRemainingMillis() > 0L) {
      message = AdaptLanguage.text(
          MutationMessages.REMAINING_COOLDOWN,
          trusted("message", message),
          trusted("duration", Form.duration(result.cooldownRemainingMillis(), 1))
      );
    }
    player.sendMessage((result.success() ? C.GREEN : C.RED) + message);
  }

  private static void applyNavigation(UIWindow window, Player player, int page, int pageCount) {
    window.setElement(0, 5, backElement(player, page, pageCount));
    if (pageCount > 1 && page > 0) {
      window.setElement(-4, 5, new UIElement("mutation-first")
          .setMaterial(new MaterialBlock(Material.LECTERN))
          .setName(C.GRAY + AdaptLanguage.text(GuiMessages.FIRST))
          .onLeftClick(event -> open(player, 0)));
      window.setElement(-3, 5, new UIElement("mutation-previous")
          .setMaterial(new MaterialBlock(Material.ARROW))
          .setName(C.WHITE + AdaptLanguage.text(GuiMessages.PREVIOUS))
          .onLeftClick(event -> open(player, page - 1)));
    } else if (pageCount > 1) {
      window.setElement(-4, 5, boundaryElement("mutation-first-disabled", GuiMessages.FIRST_PAGE));
      window.setElement(-3, 5, boundaryElement("mutation-previous-disabled", GuiMessages.NO_PREVIOUS_PAGE));
    }
    if (pageCount > 1 && page < pageCount - 1) {
      window.setElement(3, 5, new UIElement("mutation-next")
          .setMaterial(new MaterialBlock(Material.ARROW))
          .setName(C.WHITE + AdaptLanguage.text(GuiMessages.NEXT))
          .onLeftClick(event -> open(player, page + 1)));
      window.setElement(4, 5, new UIElement("mutation-last")
          .setMaterial(new MaterialBlock(Material.LECTERN))
          .setName(C.GRAY + AdaptLanguage.text(GuiMessages.LAST))
          .onLeftClick(event -> open(player, pageCount - 1)));
    } else if (pageCount > 1) {
      window.setElement(3, 5, boundaryElement("mutation-next-disabled", GuiMessages.NO_NEXT_PAGE));
      window.setElement(4, 5, boundaryElement("mutation-last-disabled", GuiMessages.LAST_PAGE));
    }
  }

  private static Element backElement(Player player, int page, int pageCount) {
    return new UIElement("mutation-back")
        .setMaterial(new MaterialBlock(Material.ARROW))
        .setName(C.GRAY + AdaptLanguage.text(GuiMessages.BACK_TO_SKILLS))
        .addLore(C.DARK_GRAY + AdaptLanguage.text(
            MutationMessages.GUI_PAGE_COUNT,
            trusted("page", page + 1),
            trusted("pages", pageCount),
            trusted("count", MutationType.values().length)
        ))
        .onLeftClick(event -> SkillsGui.open(player));
  }

  private static Element boundaryElement(String id, TextKey name) {
    return new UIElement(id)
        .setMaterial(new MaterialBlock(Material.GRAY_STAINED_GLASS_PANE))
        .setName(C.DARK_GRAY + AdaptLanguage.text(name));
  }

  private static Element mutationBackElement(Player player, int page) {
    return new UIElement("mutation-back-to-overview")
        .setMaterial(new MaterialBlock(Material.ARROW))
        .setName(C.GRAY + AdaptLanguage.text(GuiMessages.BACK_TO_MUTATIONS))
        .onLeftClick(event -> open(player, page));
  }

  private static MutationGuiModel.Card card(MutationGuiModel.View view, MutationType type) {
    for (MutationGuiModel.Card card : view.cards()) {
      if (card.type() == type) {
        return card;
      }
    }
    return null;
  }

  private static MutationType findType(String id) {
    if (id == null || id.isBlank()) {
      return null;
    }
    MutationManager manager = manager();
    return manager == null ? null : manager.find(id);
  }

  private static MutationManager manager() {
    MutationSVC service = MutationSVC.get();
    return service == null ? null : service.getManager();
  }

  private static AdaptPlayer adaptPlayer(Player player) {
    if (player == null || Adapt.instance == null || Adapt.instance.getAdaptServer() == null) {
      return null;
    }
    return Adapt.instance.getAdaptServer().getPlayer(player);
  }

  private static boolean managerHasAccess(Player player) {
    MutationManager manager = manager();
    return manager != null && manager.hasValidBookshelfAccess(player);
  }

  private static boolean mutationsConflict(MutationType first, MutationType second) {
    MutationManager manager = manager();
    if (manager == null || first == null || second == null) {
      return false;
    }
    return containsIgnoreCase(manager.getConfig().profile(first).getConflicts(), second.id())
        || containsIgnoreCase(manager.getConfig().profile(second).getConflicts(), first.id());
  }

  private static boolean containsIgnoreCase(List<String> values, String expected) {
    if (values == null || expected == null) {
      return false;
    }
    for (String value : values) {
      if (expected.equalsIgnoreCase(value)) {
        return true;
      }
    }
    return false;
  }

  private static String interactionPreview(MutationType type, MutationGuiModel.Slot otherSlot) {
    MutationType other = findType(otherSlot.mutationId());
    if (other == null) {
      return AdaptLanguage.text(MutationMessages.GUI_NO_OTHER_SELECTED);
    }
    if (other == type) {
      return AdaptLanguage.text(MutationMessages.GUI_SAME_NOT_ALLOWED);
    }
    if (mutationsConflict(type, other)) {
      return AdaptLanguage.text(
          MutationMessages.GUI_CONFLICTS_WITH,
          trusted("mutation", other.displayName())
      );
    }
    return AdaptLanguage.text(
        MutationMessages.GUI_COMPATIBLE_WITH,
        trusted("mutation", other.displayName())
    );
  }

  private static String normalizeSlotId(String mutationId) {
    return mutationId == null ? "" : mutationId;
  }

  private static boolean preparePlayerThread(Player player, Runnable retry) {
    if (player == null || !player.isOnline()) {
      return false;
    }
    if ((!J.isFoliaThreading() && J.isPrimaryThread())
        || (J.isFoliaThreading() && J.isOwnedByCurrentRegion(player))) {
      return true;
    }
    J.runEntity(player, retry);
    return false;
  }

  private static void openWindow(Player player, UIWindow window) {
    String key = player.getUniqueId().toString();
    window.onClosed(event -> Adapt.instance.getGuiLeftovers().remove(key, window));
    window.open();
    Adapt.instance.getGuiLeftovers().put(key, window);
  }

  private static int parsePage(String[] parts, int index) {
    if (parts == null || index < 0 || index >= parts.length) {
      return 0;
    }
    try {
      return Math.max(0, Integer.parseInt(parts[index]));
    } catch (NumberFormatException ignored) {
      return 0;
    }
  }

  private static String formatDomain(MutationDomain domain) {
    if (domain == null) {
      return AdaptLanguage.text(MutationMessages.GUI_UNKNOWN);
    }
    return domain.displayName();
  }

  private static String formatState(MutationState state) {
    if (state == null) {
      return AdaptLanguage.text(MutationMessages.GUI_UNKNOWN);
    }
    return switch (state) {
      case AVAILABLE -> AdaptLanguage.text(MutationMessages.GUI_STATE_READY);
      case EXPRESSED -> AdaptLanguage.text(MutationMessages.GUI_STATE_ACTIVE);
      case DORMANT -> AdaptLanguage.text(MutationMessages.GUI_STATE_INACTIVE);
      case LOCKED -> AdaptLanguage.text(MutationMessages.GUI_STATE_LOCKED);
      case DISABLED -> AdaptLanguage.text(MutationMessages.GUI_STATE_OFF);
      case RESTRICTED -> AdaptLanguage.text(MutationMessages.GUI_STATE_UNAVAILABLE);
      case CONFLICT -> AdaptLanguage.text(MutationMessages.GUI_STATE_BLOCKED);
    };
  }

  private static C stateColor(MutationState state) {
    return switch (state) {
      case AVAILABLE -> C.GREEN;
      case EXPRESSED -> C.AQUA;
      case DORMANT -> C.YELLOW;
      case LOCKED, DISABLED -> C.DARK_GRAY;
      case RESTRICTED, CONFLICT -> C.RED;
    };
  }

  enum EquipmentAction {
    ATTUNE_TEMPERBOUND,
    DISSOLVE_TEMPERBOUND,
    BIND_MASTERWORK,
    ABANDON_MASTERWORK,
    BIND_DEEPBLOOD
  }

  record EquipmentControl(EquipmentAction action, long cooldownRemainingMillis) {
  }
}
