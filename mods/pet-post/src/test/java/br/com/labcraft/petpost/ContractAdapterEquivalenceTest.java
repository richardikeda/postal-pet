package br.com.labcraft.petpost;

import br.com.labcraft.petpost.contract.network.PetListRequest;
import br.com.labcraft.petpost.contract.network.PetListResponse;
import br.com.labcraft.petpost.contract.network.PetListView;
import br.com.labcraft.petpost.contract.persistence.PetRecord;
import br.com.labcraft.petpost.contract.persistence.PostRecord;
import br.com.labcraft.petpost.contract.persistence.PostalPetSaveState;
import net.minecraft.nbt.CompoundTag;

import java.util.List;
import java.util.UUID;

/** Local executable that checks field equivalence between the 1.21 adapter and shared contracts. */
public final class ContractAdapterEquivalenceTest {
    private ContractAdapterEquivalenceTest() {
    }

    public static void main(String[] args) {
        assertPersistenceRoundTrip();
        assertLegacyNbtReadAndCurrentRewrite();
        assertNetworkRoundTrip();
    }

    private static void assertLegacyNbtReadAndCurrentRewrite() {
        String key = "minecraft:overworld|123";
        CompoundTag legacy = new CompoundTag();
        CompoundTag entry = new CompoundTag();
        entry.putString("dimension", "minecraft:overworld");
        entry.putLong("position", 123L);
        entry.putString("owner", "00000000-0000-0000-0000-000000000012");
        entry.putString("name", "Casa");
        entry.putInt("radius", 24);
        CompoundTag pet = new CompoundTag();
        pet.putUUID("uuid", UUID.fromString("00000000-0000-0000-0000-000000000011"));
        pet.putString("type", "minecraft:wolf");
        pet.putString("name", "Lobo");
        pet.putString("lastDimension", "minecraft:overworld");
        pet.putLong("lastPosition", 456L);
        pet.putLong("lastSeen", 789L);
        net.minecraft.nbt.ListTag pets = new net.minecraft.nbt.ListTag();
        pets.add(pet);
        entry.put("pets", pets);
        legacy.put(key, entry);

        PetPostSavedData loadedLegacy = PetPostSavedData.load(legacy, null);
        CompoundTag current = loadedLegacy.save(new CompoundTag(), null);
        require(current.getInt(PostalPetSaveState.SCHEMA_VERSION_FIELD) == PostalPetSaveState.CURRENT_SCHEMA_VERSION,
                "Regravação NBT deve registrar schemaVersion atual");
        CompoundTag savedEntry = current.getCompound(key);
        CompoundTag savedPet = savedEntry.getList("pets", net.minecraft.nbt.Tag.TAG_COMPOUND).getCompound(0);
        require(savedEntry.getString("name").equals("Casa") && savedEntry.getInt("radius") == 24
                        && savedPet.getUUID("uuid").equals(pet.getUUID("uuid"))
                        && savedPet.getString("name").equals("Lobo")
                        && savedPet.getLong("lastSeen") == 789L
                        && savedPet.getString("guideMaterial").equals("none"),
                "Regravação NBT deve preservar vínculo legado e marcar ausência de guia");

        CompoundTag reread = PetPostSavedData.load(current, null).save(new CompoundTag(), null);
        require(reread.equals(current), "Payload NBT atual deve sobreviver ao read-back");
    }

    private static void assertPersistenceRoundTrip() {
        PetPostSavedData.PetEntry pet = new PetPostSavedData.PetEntry(
                UUID.fromString("00000000-0000-0000-0000-000000000011"), "minecraft:wolf", "Lobo",
                "minecraft:overworld", 123L, 456L, "gold");
        PetPostSavedData.PostEntry post = new PetPostSavedData.PostEntry(
                "minecraft:the_nether", 789L, "00000000-0000-0000-0000-000000000012", "Casa", 24, List.of(pet));

        PostRecord contract = PetPostContractMapper.toContract(post);
        require(contract.dimension().equals(post.dimension()) && contract.position() == post.position()
                        && contract.owner().equals(post.owner()) && contract.name().equals(post.name()) && contract.radius() == post.radius(),
                "PostEntry deve conservar dimensão, posição, dono, nome e raio");
        PetRecord contractPet = contract.pets().getFirst();
        require(contractPet.uuid().equals(pet.uuid()) && contractPet.type().equals(pet.type()) && contractPet.name().equals(pet.name())
                        && contractPet.lastDimension().equals(pet.lastDimension()) && contractPet.lastPosition() == pet.lastPosition()
                        && contractPet.lastSeen() == pet.lastSeen() && contractPet.guideMaterial().equals("gold"),
                "PetEntry deve conservar UUID, tipo, nome, última dimensão, posição e avistamento");
        require(PetPostContractMapper.toAdapter(contract).equals(post), "PostEntry deve sobreviver ao round-trip");
    }

    private static void assertNetworkRoundTrip() {
        PetListPayloads.Request request = new PetListPayloads.Request(999L, 13);
        PetListRequest requestContract = PetPostContractMapper.toContract(request);
        require(requestContract.position() == request.position() && requestContract.nonce() == request.nonce(),
                "Request deve conservar posição e nonce");
        require(PetPostContractMapper.toAdapter(requestContract).equals(request), "Request deve sobreviver ao round-trip");

        PetListPayloads.PetView pet = new PetListPayloads.PetView("Gato", "minecraft:cat", true,
                "00000000-0000-0000-0000-000000000123", 42, "silver");
        PetListPayloads.PetView secondPet = new PetListPayloads.PetView("Lobo", "minecraft:wolf", false);
        PetListPayloads.Response response = new PetListPayloads.Response(999L, 13, "minecraft:overworld", true,
                List.of(pet, secondPet), "Casa dos pets", 24);
        PetListResponse responseContract = PetPostContractMapper.toContract(response);
        PetListView viewContract = responseContract.pets().getFirst();
        require(responseContract.position() == response.position() && responseContract.nonce() == response.nonce()
                        && responseContract.dimension().equals(response.dimension()) && responseContract.accepted() == response.accepted()
                        && viewContract.name().equals(pet.name()) && viewContract.type().equals(pet.type()) && viewContract.loaded() == pet.loaded(),
                "Response e PetView devem conservar todos os campos");
        PetListView secondViewContract = responseContract.pets().get(1);
        require(secondViewContract.name().equals(secondPet.name()) && secondViewContract.type().equals(secondPet.type())
                        && secondViewContract.loaded() == secondPet.loaded(),
                "Response deve preservar ordem e observação loaded de cada pet");
        require(PetPostContractMapper.toAdapter(responseContract).equals(response), "Response deve sobreviver ao round-trip");
        var buffer = new net.minecraft.network.RegistryFriendlyByteBuf(
                io.netty.buffer.Unpooled.buffer(), net.minecraft.core.RegistryAccess.EMPTY);
        try {
            PetListPayloads.RESPONSE_CODEC.encode(buffer, response);
            require(PetListPayloads.RESPONSE_CODEC.decode(buffer).equals(response),
                    "Wire codec must preserve mailbox name/radius and pet UUID/entity ID");
            require(buffer.readableBytes() == 0, "Wire codec must consume the entire response");
        } finally { buffer.release(); }

        PetListResponse denied = PetPostContractMapper.toContract(new PetListPayloads.Response(999L, 14,
                "minecraft:overworld", false, List.of()));
        require(!denied.accepted() && denied.pets().isEmpty(), "Resposta recusada deve continuar sem pets");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
