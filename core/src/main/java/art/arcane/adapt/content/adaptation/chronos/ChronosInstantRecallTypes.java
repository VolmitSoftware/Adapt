package art.arcane.adapt.content.adaptation.chronos;

record RecallXPContext(String fromWorldKey,
                       double fromX,
                       double fromY,
                       double fromZ,
                       String toWorldKey,
                       double toX,
                       double toY,
                       double toZ,
                       double distance,
                       double healthRecovered,
                       double hungerRecovered,
                       double saturationRecovered) {
}

record RecallXPFarmStamp(long awardedAt,
                         String fromWorldKey,
                         double fromX,
                         double fromY,
                         double fromZ,
                         String toWorldKey,
                         double toX,
                         double toY,
                         double toZ) {
}
