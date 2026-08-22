package art.arcane.adapt.content.adaptation.chronos;

record Snapshot(long timestamp,
                String worldKey,
                double x,
                double y,
                double z,
                float yaw,
                float pitch,
                double health,
                int foodLevel,
                float saturation,
                float exhaustion,
                int fireTicks) {
}

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
