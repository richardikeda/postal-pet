package br.com.labcraft.petpost;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.level.block.Blocks;

/** Observa apenas pets vinculados e carregados, sem carregar chunks. */
public final class PetHomeFollowGuard {
    private static final Map<ServerLevel, Map<UUID, Tracked>> LOADED = new WeakHashMap<>();

    private PetHomeFollowGuard() { }

    static void register() {
        ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
            if (entity instanceof TamableAnimal pet && pet.getOwnerUUID() != null) {
                PetPostSavedData.get(level).boundPost(pet.getUUID(), pet.getOwnerUUID())
                        .ifPresent(post -> track(level, pet, post));
            }
        });
        ServerEntityEvents.ENTITY_UNLOAD.register((entity, level) -> forget(level, entity.getUUID()));
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> LOADED.keySet().removeIf(level -> level.getServer() == server));
        ServerTickEvents.END_WORLD_TICK.register(level -> {
            if (level.getGameTime() % 5 != 0) return;
            Map<UUID, Tracked> pets = LOADED.get(level);
            if (pets != null) pets.values().removeIf(tracked -> keepInsideHome(level, tracked));
        });
    }

    static void track(ServerLevel level, TamableAnimal pet, PetPostSavedData.PostEntry post) {
        LOADED.computeIfAbsent(level, ignored -> new HashMap<>())
                .put(pet.getUUID(), new Tracked(pet, post.blockPos(), post.dimension(), post.owner()));
    }

    static void forget(ServerLevel level, UUID petId) {
        Map<UUID, Tracked> pets = LOADED.get(level);
        if (pets != null) pets.remove(petId);
    }

    static void forgetPost(ServerLevel level, BlockPos postPos) {
        Map<UUID, Tracked> pets = LOADED.get(level);
        if (pets != null) pets.values().removeIf(pet -> pet.dimension().equals(level.dimension().location().toString())
                && pet.postPos().equals(postPos));
    }

    /** Used by the follow-owner mixin; only pets already tracked by this guard are affected. */
    public static boolean suppressFollowing(TamableAnimal pet) {
        if (!(pet.level() instanceof ServerLevel level)) return false;
        Map<UUID, Tracked> pets = LOADED.get(level);
        Tracked tracked = pets == null ? null : pets.get(pet.getUUID());
        if (tracked == null || tracked.pet() != pet) return false;
        if (!tracked.dimension().equals(level.dimension().location().toString())) return true;
        var post = PetPostSavedData.get(level).at(tracked.dimension(), tracked.postPos().asLong());
        if (post.isEmpty()) return false;
        if (pet.getOwnerUUID() == null) return false;
        var owner = level.getServer().getPlayerList().getPlayer(pet.getOwnerUUID());
        return owner == null || owner.serverLevel() != level || PostRadius.outsideHorizontalArea(
                owner.getX(), owner.getZ(), tracked.postPos().getX(), tracked.postPos().getZ(), post.get().radius());
    }

    /** Returns true when tracking should be removed; sitting and danger suspend recovery. */
    private static boolean keepInsideHome(ServerLevel level, Tracked tracked) {
        TamableAnimal pet = tracked.pet();
        if (pet.isRemoved() || !pet.isAlive() || pet.getOwnerUUID() == null
                || !tracked.owner().equals(pet.getOwnerUUID().toString())) return true;
        var post = PetPostSavedData.get(level).at(tracked.dimension(), tracked.postPos().asLong());
        if (post.isEmpty() || !post.get().owner().equals(tracked.owner())
                || post.get().pets().stream().noneMatch(link -> link.uuid().equals(pet.getUUID()))) return true;
        if (pet.isOrderedToSit() || !tracked.dimension().equals(level.dimension().location().toString())
                || !suppressFollowing(pet)) { tracked.resetEpisode(); return false; }
        int radius = post.get().radius();
        if (!PostRadius.outsideHorizontalArea(pet.getX(), pet.getZ(),
                tracked.postPos().getX(), tracked.postPos().getZ(), radius)) {
            tracked.resetEpisode();
            return false;
        }
        if (pet.getTarget() != null || (pet.getLastHurtByMob() != null
                && pet.tickCount - pet.getLastHurtByMobTimestamp() < 100)
                || pet.isOnFire() || pet.isInWater() || pet.isInLava()) {
            tracked.bestDistance = Double.POSITIVE_INFINITY;
            tracked.stuckTicks = 0;
            return false;
        }
        long now = level.getGameTime();
        double distanceToHome = pet.distanceToSqr(tracked.postPos().getX() + 0.5,
                tracked.postPos().getY() + 1.0, tracked.postPos().getZ() + 0.5);
        if (distanceToHome + 4.0 < tracked.bestDistance) {
            tracked.bestDistance = distanceToHome;
            tracked.stuckTicks = 0;
        } else if (tracked.pathAttempted) {
            tracked.stuckTicks += 5;
        }
        if (now >= tracked.nextPathTick && (pet.getNavigation().isDone() || !tracked.pathAttempted)) {
            pet.getNavigation().moveTo(tracked.postPos().getX() + 0.5,
                    tracked.postPos().getY() + 1.0, tracked.postPos().getZ() + 0.5, 1.0);
            tracked.pathAttempted = true;
            tracked.nextPathTick = now + 40;
        }
        if (tracked.pathAttempted && tracked.stuckTicks >= 120 && !tracked.recoveryUsed
                && now >= tracked.cooldownUntil) {
            tracked.recoveryUsed = true;
            tracked.cooldownUntil = now + 1200;
            safeRecovery(level, pet, tracked.postPos());
        }
        return false;
    }

    /** Last resort after a stalled path: examine loaded, supported, fluid-free positions only. */
    private static void safeRecovery(ServerLevel level, TamableAnimal pet, BlockPos postPos) {
        for (int dy = 1; dy >= -2; dy--) {
            for (int distance = 1; distance <= 3; distance++) {
                for (int dx = -distance; dx <= distance; dx++) {
                    for (int dz = -distance; dz <= distance; dz++) {
                        if (Math.max(Math.abs(dx), Math.abs(dz)) != distance) continue;
                        BlockPos feet = postPos.offset(dx, dy, dz);
                        if (!level.hasChunkAt(feet) || !level.hasChunkAt(feet.below())) continue;
                        var floor = level.getBlockState(feet.below());
                        if (!floor.isFaceSturdy(level, feet.below(), Direction.UP)
                                || floor.is(Blocks.MAGMA_BLOCK) || floor.is(Blocks.CACTUS)
                                || floor.is(Blocks.CAMPFIRE) || floor.is(Blocks.SOUL_CAMPFIRE)) continue;
                        if (!level.getBlockState(feet).getFluidState().isEmpty()
                                || !level.getBlockState(feet.above()).getFluidState().isEmpty()) continue;
                        double x = feet.getX() + 0.5, y = feet.getY(), z = feet.getZ() + 0.5;
                        if (!level.noCollision(pet, pet.getBoundingBox().move(x - pet.getX(), y - pet.getY(), z - pet.getZ()))) continue;
                        pet.teleportTo(x, y, z);
                        pet.getNavigation().stop();
                        return;
                    }
                }
            }
        }
    }

    private static final class Tracked {
        private final TamableAnimal pet;
        private final BlockPos postPos;
        private final String dimension;
        private final String owner;
        private double bestDistance = Double.POSITIVE_INFINITY;
        private long nextPathTick;
        private long cooldownUntil;
        private int stuckTicks;
        private boolean pathAttempted;
        private boolean recoveryUsed;

        private Tracked(TamableAnimal pet, BlockPos postPos, String dimension, String owner) {
            this.pet = pet; this.postPos = postPos; this.dimension = dimension; this.owner = owner;
        }
        private TamableAnimal pet() { return pet; }
        private BlockPos postPos() { return postPos; }
        private String dimension() { return dimension; }
        private String owner() { return owner; }
        private void resetEpisode() {
            bestDistance = Double.POSITIVE_INFINITY; stuckTicks = 0; pathAttempted = false; recoveryUsed = false;
        }
    }
}
