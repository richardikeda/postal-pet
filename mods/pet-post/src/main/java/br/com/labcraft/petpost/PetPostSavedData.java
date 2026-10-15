package br.com.labcraft.petpost;

import br.com.labcraft.petpost.contract.persistence.PostRecord;
import br.com.labcraft.petpost.contract.persistence.PostalPetSaveState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Mailbox registry for 1.21. Preserves the logical fields shared with 26.2 while using the NBT adapter required by this SavedData API. */
public final class PetPostSavedData extends SavedData {
    private static final String FILE_NAME = "petpost_pet_posts";
    private final Map<String, PostEntry> posts = new LinkedHashMap<>();

    public static PetPostSavedData get(ServerLevel level) {
        SavedData.Factory<PetPostSavedData> factory = new SavedData.Factory<>(
                PetPostSavedData::new, PetPostSavedData::load, DataFixTypes.SAVED_DATA_COMMAND_STORAGE);
        return level.getServer().overworld().getDataStorage().computeIfAbsent(factory, FILE_NAME);
    }

    static PetPostSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        PetPostSavedData data = new PetPostSavedData();
        int schemaVersion = PostalPetSaveState.readVersion(
                tag.contains(PostalPetSaveState.SCHEMA_VERSION_FIELD, Tag.TAG_INT),
                tag.getInt(PostalPetSaveState.SCHEMA_VERSION_FIELD));
        for (String key : tag.getAllKeys()) {
            if (key.equals(PostalPetSaveState.SCHEMA_VERSION_FIELD)) continue;
            CompoundTag entry = tag.getCompound(key);
            if (!entry.contains("dimension") || !entry.contains("position") || !entry.contains("owner")) continue;
            List<PetEntry> pets = new java.util.ArrayList<>();
            for (var petTag : entry.getList("pets", Tag.TAG_COMPOUND)) {
                if (!(petTag instanceof CompoundTag pet) || !pet.hasUUID("uuid")) continue;
                pets.add(new PetEntry(pet.getUUID("uuid"), pet.getString("type"), pet.getString("name"),
                        pet.getString("lastDimension"), pet.getLong("lastPosition"), pet.getLong("lastSeen"),
                        pet.getString("guideMaterial")));
            }
            data.posts.put(key, new PostEntry(entry.getString("dimension"), entry.getLong("position"),
                    entry.getString("owner"), entry.getString("name"), entry.getInt("radius"), pets));
        }
        toSaveState(schemaVersion, data.posts);
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        PostalPetSaveState state = toSaveState(PostalPetSaveState.CURRENT_SCHEMA_VERSION, posts).forWrite();
        tag.putInt(PostalPetSaveState.SCHEMA_VERSION_FIELD, state.schemaVersion());
        state.posts().forEach((key, postRecord) -> {
            PostEntry post = PetPostContractMapper.toAdapter(postRecord);
            CompoundTag entry = new CompoundTag();
            entry.putString("dimension", post.dimension());
            entry.putLong("position", post.position());
            entry.putString("owner", post.owner());
            entry.putString("name", post.name());
            entry.putInt("radius", post.radius());
            ListTag pets = new ListTag();
            for (PetEntry pet : post.pets()) {
                CompoundTag petTag = new CompoundTag();
                petTag.putUUID("uuid", pet.uuid()); petTag.putString("type", pet.type());
                petTag.putString("name", pet.name()); petTag.putString("lastDimension", pet.lastDimension());
                petTag.putLong("lastPosition", pet.lastPosition()); petTag.putLong("lastSeen", pet.lastSeen());
                petTag.putString("guideMaterial", pet.guideMaterial());
                pets.add(petTag);
            }
            entry.put("pets", pets);
            tag.put(key, entry);
        });
        return tag;
    }

    private static PostalPetSaveState toSaveState(int schemaVersion, Map<String, PostEntry> posts) {
        Map<String, PostRecord> contracts = new LinkedHashMap<>();
        posts.forEach((key, post) -> contracts.put(key, PetPostContractMapper.toContract(post)));
        return new PostalPetSaveState(schemaVersion, contracts);
    }

    public void mark(ServerLevel level, BlockPos pos, UUID owner, int radius) {
        String key = key(level, pos);
        PostEntry previous = posts.get(key);
        String ownerText = owner.toString();
        String name = previous == null ? nextDefaultName(ownerText) : previous.name();
        posts.put(key, new PostEntry(dimension(level), pos.asLong(), ownerText, name, radius,
                previous == null ? List.of() : previous.pets()));
        setDirty();
    }

    public void remove(ServerLevel level, BlockPos pos) {
        if (posts.remove(key(level, pos)) != null) setDirty();
    }

    public List<PostEntry> ownedBy(UUID owner) {
        String ownerText = owner.toString();
        return posts.values().stream()
                .filter(entry -> entry.owner().equals(ownerText))
                .sorted(Comparator.comparing(PostEntry::name, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    public Optional<PostEntry> nearestOwned(ServerLevel level, BlockPos origin, UUID owner, int maxDistance) {
        String ownerText = owner.toString();
        String dimension = dimension(level);
        PostEntry nearest = null;
        long nearestDistance = Long.MAX_VALUE;
        long limit = (long) maxDistance * maxDistance;
        for (PostEntry entry : posts.values()) {
            if (!entry.owner().equals(ownerText) || !entry.dimension().equals(dimension)) continue;
            BlockPos postPos = BlockPos.of(entry.position());
            long dx = postPos.getX() - origin.getX();
            long dy = postPos.getY() - origin.getY();
            long dz = postPos.getZ() - origin.getZ();
            long distance = dx * dx + dy * dy + dz * dz;
            if (distance <= limit && distance < nearestDistance) {
                nearest = entry;
                nearestDistance = distance;
            }
        }
        return Optional.ofNullable(nearest);
    }

    /** Finds an exact mailbox in the current dimension and confirms player ownership. */
    public Optional<PostEntry> ownedAt(ServerLevel level, BlockPos pos, UUID owner) {
        PostEntry entry = posts.get(key(level, pos));
        return entry != null && entry.owner().equals(owner.toString()) ? Optional.of(entry) : Optional.empty();
    }

    public Optional<PostEntry> at(ServerLevel level, BlockPos pos) {
        return Optional.ofNullable(posts.get(key(level, pos)));
    }

    public Optional<PostEntry> at(String dimension, long position) {
        return Optional.ofNullable(posts.get(key(dimension, position)));
    }

    public Optional<PostEntry> boundPost(UUID petId, UUID ownerId) {
        return posts.values().stream()
                .filter(post -> post.owner().equals(ownerId.toString())
                        && post.pets().stream().anyMatch(pet -> pet.uuid().equals(petId))).findFirst();
    }

    /** Exposes shared-contract snapshots to pure use cases without changing the adapter's serialization format. */
    public List<PostRecord> ownedContracts(UUID owner) {
        String ownerText = owner.toString();
        return posts.values().stream().filter(post -> post.owner().equals(ownerText))
                .map(PetPostContractMapper::toContract).toList();
    }

    /** Replaces only the complete set of mailboxes owned by the specified player. */
    public boolean replaceOwnedContracts(String owner, List<PostRecord> replacements) {
        Map<String, PostRecord> byKey = new HashMap<>();
        for (PostRecord replacement : replacements) {
            if (!replacement.owner().equals(owner) || byKey.put(replacement.storageKey(), replacement) != null) return false;
        }
        long currentCount = posts.values().stream().filter(post -> post.owner().equals(owner)).count();
        if (currentCount != byKey.size() || posts.entrySet().stream()
                .filter(entry -> entry.getValue().owner().equals(owner)).anyMatch(entry -> !byKey.containsKey(entry.getKey()))) return false;
        posts.replaceAll((entryKey, current) -> current.owner().equals(owner)
                ? PetPostContractMapper.toAdapter(byKey.get(entryKey)) : current);
        setDirty();
        return true;
    }

    /** Applies one use-case-authorized contract while preserving its logical storage key. */
    public boolean replaceContract(PostRecord replacement) {
        String storageKey = replacement.storageKey();
        PostEntry current = posts.get(storageKey);
        if (current == null || !current.owner().equals(replacement.owner())) return false;
        posts.put(storageKey, PetPostContractMapper.toAdapter(replacement));
        setDirty();
        return true;
    }

    private String nextDefaultName(String owner) {
        long count = posts.values().stream().filter(entry -> entry.owner().equals(owner)).count();
        return "Pet Mailbox " + (count + 1);
    }

    private static String key(ServerLevel level, BlockPos pos) {
        return key(dimension(level), pos.asLong());
    }

    private static String key(String dimension, long position) {
        return dimension + "|" + position;
    }

    private static String dimension(ServerLevel level) {
        return level.dimension().location().toString();
    }

    /** Version-independent fields for one marked mailbox. */
    public record PostEntry(String dimension, long position, String owner, String name, int radius, List<PetEntry> pets) {
        public BlockPos blockPos() {
            return BlockPos.of(position);
        }
    }

    /** Persistent snapshot: position refers to the stored link, not a claim that the entity is currently loaded. */
    public record PetEntry(UUID uuid, String type, String name, String lastDimension, long lastPosition, long lastSeen,
                           String guideMaterial) {
        public PetEntry(UUID uuid, String type, String name, String lastDimension, long lastPosition, long lastSeen) {
            this(uuid, type, name, lastDimension, lastPosition, lastSeen, "none");
        }
        public PetEntry {
            if (!"silver".equals(guideMaterial) && !"gold".equals(guideMaterial)) guideMaterial = "none";
        }
    }
}
