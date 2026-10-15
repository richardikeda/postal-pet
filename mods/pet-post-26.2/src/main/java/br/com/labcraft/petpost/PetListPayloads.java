package br.com.labcraft.petpost;

import br.com.labcraft.petpost.application.query.ListPetsQuery;
import br.com.labcraft.petpost.contract.network.PetListRequest;
import br.com.labcraft.petpost.contract.network.PetListResponse;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;

import java.util.List;

/**
 * Protocolo pontual da lista de pets da tela 26.2. O servidor e somente o
 * servidor cria a resposta; o nonce e a posicao evitam aplicar respostas
 * atrasadas a outro Postal Pet aberto pelo cliente.
 */
public final class PetListPayloads {
    private static final int MAX_PETS = 32;
    private static final int MAX_TEXT = 96;
    private static final int MAX_DIMENSION = 128;
    private static final ListPetsQuery LIST_PETS = new ListPetsQuery();
    public static final CustomPacketPayload.Type<Request> REQUEST =
            new CustomPacketPayload.Type<>(id("pet_list_request_v3"));
    public static final CustomPacketPayload.Type<Response> RESPONSE =
            new CustomPacketPayload.Type<>(id("pet_list_response_v3"));
    public static final StreamCodec<RegistryFriendlyByteBuf, Request> REQUEST_CODEC =
            StreamCodec.of(PetListPayloads::writeRequest, PetListPayloads::readRequest);
    public static final StreamCodec<RegistryFriendlyByteBuf, Response> RESPONSE_CODEC =
            StreamCodec.of(PetListPayloads::writeResponse, PetListPayloads::readResponse);

    private PetListPayloads() { }

    /** Registra codecs e a unica consulta C2S antes de qualquer conexao de jogo. */
    public static void register() {
        PayloadTypeRegistry.serverboundPlay().register(REQUEST, REQUEST_CODEC);
        PayloadTypeRegistry.clientboundPlay().register(RESPONSE, RESPONSE_CODEC);
        ServerPlayNetworking.registerGlobalReceiver(REQUEST, (request, context) -> {
            PetListRequest logicalRequest = PetPostContractMapper.toContract(request);
            ServerLevel level = context.player().level();
            BlockPos postPos = BlockPos.of(request.position());
            if (!level.hasChunkAt(postPos) || !level.getBlockState(postPos).is(PetPost.PET_POST)
                    || context.player().distanceToSqr(postPos.getX() + 0.5D, postPos.getY() + 0.5D,
                    postPos.getZ() + 0.5D) > 64.0D) {
                deny(context.player(), logicalRequest, level);
                return;
            }
            var post = PetPostSavedData.get(level).ownedAt(level, postPos, context.player().getUUID());
            if (post.isEmpty()) {
                deny(context.player(), logicalRequest, level);
                return;
            }

            PetListResponse response = new PetListResponse(logicalRequest.position(), logicalRequest.nonce(),
                    level.dimension().identifier().toString(), true,
                    LIST_PETS.execute(PetPostContractMapper.toContract(post.get()), uuid -> isLoaded(level, uuid))
                            .stream().map(pet -> {
                                Entity entity = level.getEntity(java.util.UUID.fromString(pet.uuid()));
                                return new br.com.labcraft.petpost.contract.network.PetListView(
                                        entity != null ? clean(entity.getName().getString(), MAX_TEXT) : pet.name(),
                                        pet.type(), pet.loaded(), pet.uuid(),
                                        entity != null && entity.isAlive() ? entity.getId() : -1,
                                        pet.guideMaterial());
                            }).toList(), post.get().name(), PetPostBlock.radius(level.getBlockState(postPos)));
            ServerPlayNetworking.send(context.player(), PetPostContractMapper.toAdapter(response));
        });
    }

    /** Confirma ao cliente que a consulta foi recusada, sem revelar dados do posto. */
    private static void deny(net.minecraft.server.level.ServerPlayer player, PetListRequest request, ServerLevel level) {
        PetListResponse denied = new PetListResponse(request.position(), request.nonce(),
                level.dimension().identifier().toString(), false, List.of());
        ServerPlayNetworking.send(player, PetPostContractMapper.toAdapter(denied));
    }

    private static boolean isLoaded(ServerLevel level, java.util.UUID uuid) {
        Entity entity = level.getEntity(uuid);
        return entity != null && entity.isAlive();
    }

    private static void writeRequest(RegistryFriendlyByteBuf buffer, Request payload) {
        buffer.writeLong(payload.position());
        buffer.writeVarInt(payload.nonce());
    }

    private static Request readRequest(RegistryFriendlyByteBuf buffer) {
        return new Request(buffer.readLong(), buffer.readVarInt());
    }

    private static void writeResponse(RegistryFriendlyByteBuf buffer, Response payload) {
        buffer.writeLong(payload.position());
        buffer.writeVarInt(payload.nonce());
        buffer.writeUtf(clean(payload.dimension(), MAX_DIMENSION), MAX_DIMENSION);
        buffer.writeBoolean(payload.accepted());
        buffer.writeUtf(clean(payload.postName(), MAX_TEXT), MAX_TEXT);
        buffer.writeVarInt(payload.radius());
        buffer.writeVarInt(Math.min(payload.pets().size(), MAX_PETS));
        for (PetView pet : payload.pets().stream().limit(MAX_PETS).toList()) {
            buffer.writeUtf(clean(pet.name(), MAX_TEXT), MAX_TEXT);
            buffer.writeUtf(clean(pet.type(), MAX_TEXT), MAX_TEXT);
            buffer.writeBoolean(pet.loaded());
            buffer.writeUtf(pet.uuid(), 36);
            buffer.writeVarInt(pet.entityId());
            buffer.writeUtf(pet.guideMaterial(), 8);
        }
    }

    private static Response readResponse(RegistryFriendlyByteBuf buffer) {
        long position = buffer.readLong();
        int nonce = buffer.readVarInt();
        String dimension = buffer.readUtf(MAX_DIMENSION);
        boolean accepted = buffer.readBoolean();
        String postName = buffer.readUtf(MAX_TEXT);
        int radius = buffer.readVarInt();
        int count = buffer.readVarInt();
        if (count < 0 || count > MAX_PETS) throw new IllegalArgumentException("Lista de pets excede o limite");
        java.util.ArrayList<PetView> pets = new java.util.ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            pets.add(new PetView(buffer.readUtf(MAX_TEXT), buffer.readUtf(MAX_TEXT), buffer.readBoolean(),
                    buffer.readUtf(36), buffer.readVarInt(), buffer.readUtf(8)));
        }
        return new Response(position, nonce, dimension, accepted, List.copyOf(pets), postName, radius);
    }

    private static String clean(String value, int max) {
        return value == null ? "" : value.length() <= max ? value : value.substring(0, max);
    }

    /** Solicita o instantaneo do posto que a tela acabou de abrir. */
    public record Request(long position, int nonce) implements CustomPacketPayload {
        @Override public Type<Request> type() { return REQUEST; }
    }

    /** Resposta autorizada ou recusada; a recusa nunca inclui dados do posto. */
    public record Response(long position, int nonce, String dimension, boolean accepted, List<PetView> pets, String postName, int radius) implements CustomPacketPayload {
        public Response(long position, int nonce, String dimension, boolean accepted, List<PetView> pets) {
            this(position, nonce, dimension, accepted, pets, "", 0);
        }
        @Override public Type<Response> type() { return RESPONSE; }
    }

    /** Dado de apresentacao recebido do servidor; nao aceita mutacoes do cliente. */
    public record PetView(String name, String type, boolean loaded, String uuid, int entityId, String guideMaterial) {
        public PetView(String name, String type, boolean loaded, String uuid, int entityId) {
            this(name, type, loaded, uuid, entityId, "none");
        }
        public PetView(String name, String type, boolean loaded) { this(name, type, loaded, "", -1, "none"); }
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(PetPost.MOD_ID, path);
    }
}
