package br.com.labcraft.petpost;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import java.util.ArrayList;
import java.util.List;

/** Short, bounded visual jobs. No entity scans, terrain reads or chunk loading. */
public final class TerritoryPreview {
    private static final int DURATION = 40, MAX_WAVES = 16;
    private static final List<Wave> WAVES = new ArrayList<>();
    private TerritoryPreview() {}

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            var iterator = WAVES.iterator();
            while (iterator.hasNext()) {
                Wave wave = iterator.next();
                if (wave.level.getServer() != server) continue;
                wave.age++;
                if (wave.age > DURATION) { iterator.remove(); continue; }
                if (wave.age % 2 != 0) continue;
                double distance = wave.radius * (double) wave.age / DURATION;
                int points = Math.max(12, Math.min(48, PetPostConfig.get().previewPoints));
                for (int i = 0; i < points; i++) {
                    double angle = 2 * Math.PI * i / points;
                    wave.level.sendParticles(ParticleTypes.HAPPY_VILLAGER,
                            wave.pos.getX() + 0.5 + Math.cos(angle) * distance,
                            wave.pos.getY() + 1.15,
                            wave.pos.getZ() + 0.5 + Math.sin(angle) * distance, 1, 0, 0, 0, 0);
                }
            }
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> WAVES.removeIf(w -> w.level.getServer() == server));
    }

    public static void start(ServerLevel level, BlockPos pos, int radius) {
        WAVES.removeIf(w -> w.level == level && w.pos.equals(pos));
        if (WAVES.size() >= MAX_WAVES) WAVES.removeFirst();
        WAVES.add(new Wave(level, pos.immutable(), radius));
        level.sendParticles(ParticleTypes.END_ROD, pos.getX() + 0.5, pos.getY() + 1.15,
                pos.getZ() + 0.5, 8, 0.1, 0.1, 0.1, 0.01);
    }

    private static final class Wave {
        final ServerLevel level;
        final BlockPos pos;
        final int radius;
        int age;
        Wave(ServerLevel level, BlockPos pos, int radius) {
            this.level = level; this.pos = pos; this.radius = radius;
        }
    }
}
