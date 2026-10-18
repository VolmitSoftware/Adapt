package art.arcane.adapt.api.golem.node;

public record NodePlacementCheck(boolean rosterReady, boolean chunkReady, boolean faceFree, boolean canBuild,
                                 boolean needsContainer, boolean containerAnchor, boolean canAccessContainer,
                                 int chunkNodes, int maxNodesPerChunk, int ownedNodes, int nodeCap) {
}
