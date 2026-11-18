package art.arcane.adapt.mod.feature.advancement;

import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.network.protocol.game.ClientboundUpdateAdvancementsPacket;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class AdvancementPackets {
    private AdvancementPackets() {
    }

    static ClientboundUpdateAdvancementsPacket update(boolean reset, List<PlacedAdvancement> added, Set<Identifier> removed,
                                                      Map<Identifier, AdvancementProgress> progress, boolean showToasts) {
        List<ClientboundUpdateAdvancementsPacket.PositionedAdvancement> positioned = new ArrayList<>(added.size());
        for (PlacedAdvancement placed : added) {
            positioned.add(new ClientboundUpdateAdvancementsPacket.PositionedAdvancement(placed.holder(), placed.x(), placed.y()));
        }
        return new ClientboundUpdateAdvancementsPacket(reset, positioned, removed, progress, showToasts);
    }
}
