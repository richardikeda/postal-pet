package br.com.labcraft.petpost;

import br.com.labcraft.petpost.application.ChangeRadiusUseCase;
import br.com.labcraft.petpost.application.UnbindPetUseCase;
import br.com.labcraft.petpost.application.RenamePostUseCase;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import java.util.UUID;

/** Small, position-bound mutations. The server checks the clicked post and ownership. */
public final class PetActionPayloads {
    public static final CustomPacketPayload.Type<Radius> RADIUS =
            new CustomPacketPayload.Type<>(id("post_radius_v1"));
    public static final CustomPacketPayload.Type<Unbind> UNBIND =
            new CustomPacketPayload.Type<>(id("pet_unbind_v1"));
    public static final CustomPacketPayload.Type<Rename> RENAME =
            new CustomPacketPayload.Type<>(id("post_rename_v1"));
    public static final StreamCodec<RegistryFriendlyByteBuf, Radius> RADIUS_CODEC =
            StreamCodec.of((buf, value) -> buf.writeLong(value.position()), buf -> new Radius(buf.readLong()));
    public static final StreamCodec<RegistryFriendlyByteBuf, Unbind> UNBIND_CODEC =
            StreamCodec.of((buf, value) -> { buf.writeLong(value.position()); buf.writeUUID(value.petId()); },
                    buf -> new Unbind(buf.readLong(), buf.readUUID()));
    public static final StreamCodec<RegistryFriendlyByteBuf, Rename> RENAME_CODEC =
            StreamCodec.of((buf, value) -> { buf.writeLong(value.position()); buf.writeUtf(value.name(), 32); },
                    buf -> new Rename(buf.readLong(), buf.readUtf(32)));

    private PetActionPayloads() { }

    /** Registers bounded requests; every mutation rechecks the exact mailbox on the server. */
    public static void register() {
        PayloadTypeRegistry.playC2S().register(RADIUS, RADIUS_CODEC);
        PayloadTypeRegistry.playC2S().register(UNBIND, UNBIND_CODEC);
        PayloadTypeRegistry.playC2S().register(RENAME, RENAME_CODEC);
        ServerPlayNetworking.registerGlobalReceiver(RADIUS, (request, context) -> {
            ServerLevel level = context.player().serverLevel();
            BlockPos pos = BlockPos.of(request.position());
            if (!authorized(level, pos, context.player())) return;
            BlockState state = level.getBlockState(pos);
            ChangeRadiusUseCase.Result change = new ChangeRadiusUseCase().execute(state.getValue(PetPostBlock.RADIUS_STEP));
            BlockState updated = state.setValue(PetPostBlock.RADIUS_STEP, change.step())
                    .setValue(PetPostBlock.FLAG_RAISED, true);
            level.setBlock(pos, updated, Block.UPDATE_ALL);
            PetPostSavedData data = PetPostSavedData.get(level);
            data.mark(level, pos, context.player().getUUID(), change.radius());
            TerritoryPreview.start(level, pos, change.radius());
            var post = data.ownedAt(level, pos, context.player().getUUID()).orElseThrow();
            context.player().displayClientMessage(
                    net.minecraft.network.chat.Component.translatable("message.petpost.radius_changed",
                            post.name(), change.radius()), true);
        });
        ServerPlayNetworking.registerGlobalReceiver(UNBIND, (request, context) -> {
            ServerLevel level = context.player().serverLevel();
            BlockPos pos = BlockPos.of(request.position());
            if (!authorized(level, pos, context.player())) return;
            PetPostSavedData data = PetPostSavedData.get(level);
            var post = data.ownedAt(level, pos, context.player().getUUID()).orElseThrow();
            String owner = context.player().getUUID().toString();
            var result = new UnbindPetUseCase().execute(data.ownedContracts(context.player().getUUID()),
                    owner, post.dimension(), post.position(), request.petId());
            if (!result.changed() || !data.replaceOwnedContracts(owner, result.posts())) return;
            PetHomeFollowGuard.forget(level, request.petId());
            var pet = post.pets().stream().filter(entry -> entry.uuid().equals(request.petId())).findFirst();
            context.player().sendSystemMessage(net.minecraft.network.chat.Component.translatable(
                    "message.petpost.pet_unbound", pet.map(PetPostSavedData.PetEntry::name).orElse("Pet"), post.name()));
        });
        ServerPlayNetworking.registerGlobalReceiver(RENAME, (request, context) -> {
            ServerLevel level = context.player().serverLevel();
            BlockPos pos = BlockPos.of(request.position());
            if (!authorized(level, pos, context.player())) return;
            PetPostSavedData data = PetPostSavedData.get(level);
            var post = data.ownedAt(level, pos, context.player().getUUID()).orElseThrow();
            var renamed = new RenamePostUseCase().execute(PetPostContractMapper.toContract(post),
                    context.player().getUUID().toString(), request.name());
            if (renamed.isEmpty() || !data.replaceContract(renamed.get())) return;
            context.player().sendSystemMessage(net.minecraft.network.chat.Component.translatable(
                    "message.petpost.renamed", renamed.get().name()));
        });
    }

    /** Rejects unloaded chunks before reading blocks, then checks distance and ownership. */
    private static boolean authorized(ServerLevel level, BlockPos pos, net.minecraft.server.level.ServerPlayer player) {
        return level.hasChunkAt(pos) && level.getBlockState(pos).is(PetPost.PET_POST)
                && player.distanceToSqr(pos.getCenter()) <= 64.0D
                && PetPostSavedData.get(level).ownedAt(level, pos, player.getUUID()).isPresent();
    }

    public record Radius(long position) implements CustomPacketPayload {
        @Override public Type<Radius> type() { return RADIUS; }
    }
    public record Unbind(long position, UUID petId) implements CustomPacketPayload {
        @Override public Type<Unbind> type() { return UNBIND; }
    }
    public record Rename(long position, String name) implements CustomPacketPayload {
        @Override public Type<Rename> type() { return RENAME; }
    }
    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(PetPost.MOD_ID, path);
    }
}
