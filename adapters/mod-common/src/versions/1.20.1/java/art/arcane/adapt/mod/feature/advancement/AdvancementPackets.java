package art.arcane.adapt.mod.feature.advancement;

import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.advancements.DisplayInfo;
import net.minecraft.network.protocol.game.ClientboundUpdateAdvancementsPacket;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Update packets; a client shows a toast for every completed node whose display has its toast flag set. */
final class AdvancementPackets {
    private AdvancementPackets() {
    }

    static ClientboundUpdateAdvancementsPacket update(boolean reset, List<PlacedAdvancement> added, Set<ResourceLocation> removed,
                                                      Map<ResourceLocation, AdvancementProgress> progress) {
        List<Advancement> advancements = new ArrayList<>(added.size());
        for (PlacedAdvancement placed : added) {
            DisplayInfo display = placed.advancement().getDisplay();
            if (display != null) {
                display.setLocation(placed.x(), placed.y());
            }
            advancements.add(placed.advancement());
        }
        return new ClientboundUpdateAdvancementsPacket(reset, advancements, removed, progress);
    }
}
