package art.arcane.adapt.mod.feature.advancement;

import art.arcane.adapt.mod.platform.ModRefs;
import art.arcane.adapt.mod.platform.ModText;
import art.arcane.adapt.platform.advancement.AdvancementFrame;
import art.arcane.adapt.platform.advancement.AdvancementNode;
import art.arcane.adapt.platform.advancement.AdvancementVisibility;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Id;
import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.advancements.AdvancementRequirements;
import net.minecraft.advancements.AdvancementType;
import net.minecraft.advancements.DisplayInfo;
import net.minecraft.core.ClientAsset;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

final class AdvancementCatalog {
    static final AdvancementCatalog EMPTY = new AdvancementCatalog(Collections.emptyMap());

    private final Map<Id, Entry> entries;

    private AdvancementCatalog(Map<Id, Entry> entries) {
        this.entries = entries;
    }

    static AdvancementCatalog build(List<AdvancementNode> roots) {
        Map<Id, Entry> entries = new LinkedHashMap<>();
        for (AdvancementNode root : roots) {
            add(entries, root, null);
        }
        return new AdvancementCatalog(Collections.unmodifiableMap(entries));
    }

    private static void add(Map<Id, Entry> entries, AdvancementNode node, Entry parent) {
        Identifier id = ModRefs.identifier(node.key());
        List<String> criteria = criteria(node.maxProgress());
        Advancement.Builder builder = Advancement.Builder.advancement();
        if (parent != null) {
            builder.parent(parent.placed().holder().id());
        }
        Optional<ClientAsset.ResourceTexture> background = parent == null ? Optional.of(new ClientAsset.ResourceTexture(texture(node.background())))
                : Optional.empty();
        builder.display(new DisplayInfo(icon(node.icon()), ModText.component(node.title()), ModText.component(node.description()), background,
                type(node.frame()), false, false, false));
        for (String criterion : criteria) {
            AdvancementCriteria.impossible(builder, criterion);
        }
        AdvancementRequirements requirements = AdvancementRequirements.allOf(criteria);
        builder.requirements(requirements);
        AdvancementHolder holder = builder.build(id);
        Entry entry = new Entry(node, parent, new PlacedAdvancement(holder, node.x(), node.y()), requirements, criteria);
        entries.put(node.key(), entry);
        for (AdvancementNode child : node.children()) {
            add(entries, child, entry);
        }
    }

    static List<String> criteria(int count) {
        List<String> names = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            names.add(Integer.toString(index));
        }
        return names;
    }

    static ItemStackTemplate icon(ItemRef icon) {
        ItemStack stack = icon == null ? ItemStack.EMPTY : ModRefs.item(icon);
        if (stack == null || stack.isEmpty()) {
            stack = new ItemStack(Items.PAPER);
        }
        return ItemStackTemplate.fromNonEmptyStack(stack);
    }

    static AdvancementType type(AdvancementFrame frame) {
        return switch (frame) {
            case TASK -> AdvancementType.TASK;
            case GOAL -> AdvancementType.GOAL;
            case CHALLENGE -> AdvancementType.CHALLENGE;
        };
    }

    static Identifier texture(Id background) {
        if (background == null) {
            return Identifier.withDefaultNamespace("gui/advancements/backgrounds/stone");
        }
        String path = background.path();
        if (path.startsWith("textures/")) {
            path = path.substring("textures/".length());
        }
        if (path.endsWith(".png")) {
            path = path.substring(0, path.length() - ".png".length());
        }
        return ModRefs.identifier(background.withPath(path));
    }

    static AdvancementProgress progress(AdvancementRequirements requirements, List<String> criteria, int done) {
        AdvancementProgress progress = new AdvancementProgress();
        progress.update(requirements);
        int limit = Math.min(done, criteria.size());
        for (int index = 0; index < limit; index++) {
            progress.grantProgress(criteria.get(index));
        }
        return progress;
    }

    Entry get(Id key) {
        return entries.get(key);
    }

    boolean contains(Id key) {
        return entries.containsKey(key);
    }

    Iterable<Entry> entries() {
        return entries.values();
    }

    record Entry(AdvancementNode node, Entry parent, PlacedAdvancement placed, AdvancementRequirements requirements, List<String> criteria) {
        Id key() {
            return node.key();
        }

        Identifier id() {
            return placed.holder().id();
        }

        int max() {
            return node.maxProgress();
        }

        AdvancementVisibility visibility() {
            return node.visibility();
        }

        AdvancementProgress progress(int done) {
            return AdvancementCatalog.progress(requirements, criteria, done);
        }
    }
}
