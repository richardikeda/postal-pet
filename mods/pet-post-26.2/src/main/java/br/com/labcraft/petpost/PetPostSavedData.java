package br.com.labcraft.petpost;

import br.com.labcraft.petpost.contract.persistence.PostRecord;
import br.com.labcraft.petpost.contract.persistence.PostalPetSaveState;
import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Persistent mailbox registry. Supports naming and listing independently of loaded chunks. */
public final class PetPostSavedData extends SavedData {
    private static final Codec<PetEntry> PET_CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.fieldOf("uuid").forGetter(pet -> pet.uuid().toString()),
            Codec.STRING.fieldOf("type").forGetter(PetEntry::type),
            Codec.STRING.fieldOf("name").forGetter(PetEntry::name),
            Codec.STRING.fieldOf("lastDimension").forGetter(PetEntry::lastDimension),
            Codec.LONG.fieldOf("lastPosition").forGetter(PetEntry::lastPosition),
            Codec.LONG.fieldOf("lastSeen").forGetter(PetEntry::lastSeen),
            Codec.STRING.optionalFieldOf("guideMaterial", "none").forGetter(PetEntry::guideMaterial)
    ).apply(instance, (uuid, type, name, lastDimension, lastPosition, lastSeen, guideMaterial) ->
            new PetEntry(UUID.fromString(uuid), type, name, lastDimension, lastPosition, lastSeen, guideMaterial)));

    private static final Codec<PostEntry> ENTRY_CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.fieldOf("dimension").forGetter(PostEntry::dimension),
            Codec.LONG.fieldOf("position").forGetter(PostEntry::position),
            Codec.STRING.fieldOf("owner").forGetter(PostEntry::owner),
            Codec.STRING.fieldOf("name").forGetter(PostEntry::name),
            Codec.INT.fieldOf("radius").forGetter(PostEntry::radius),
            Codec.list(PET_CODEC).optionalFieldOf("pets", List.of()).forGetter(PostEntry::pets)
    ).apply(instance, PostEntry::new));

    private static final Codec<Map<String, PostEntry>> LEGACY_POSTS_CODEC = Codec.unboundedMap(Codec.STRING, ENTRY_CODEC);
    private static final Codec<VersionedPosts> VERSIONED_POSTS_CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.fieldOf(PostalPetSaveState.SCHEMA_VERSION_FIELD).forGetter(VersionedPosts::schemaVersion),
            LEGACY_POSTS_CODEC.fieldOf("posts").forGetter(VersionedPosts::posts)
    ).apply(instance, VersionedPosts::new));

    /** Reads the unversioned map written by 0.5.2 and writes a versioned envelope. The physical representation remains specific to the Codec adapter. */
    public static final Codec<PetPostSavedData> CODEC = Codec.either(VERSIONED_POSTS_CODEC, LEGACY_POSTS_CODEC)
            .xmap(value -> value.map(PetPostSavedData::fromVersioned, PetPostSavedData::fromLegacy),
                    data -> Either.left(forWrite(data)));
    public static final SavedDataType<PetPostSavedData> TYPE = new SavedDataType<>(
            Identifier.fromNamespaceAndPath(PetPost.MOD_ID, "pet_posts"),
            PetPostSavedData::new,
            CODEC,
            DataFixTypes.SAVED_DATA_COMMAND_STORAGE);

    private final Map<String, PostEntry> posts;

    public PetPostSavedData() {
        this.posts = new LinkedHashMap<>();
    }

    private PetPostSavedData(Map<String, PostEntry> posts) {
        this.posts = new LinkedHashMap<>(posts);
    }

    private static PetPostSavedData fromVersioned(VersionedPosts state) {
        return new PetPostSavedData(state.posts());
    }

    private static PetPostSavedData fromLegacy(Map<String, PostEntry> posts) {
        toSaveState(PostalPetSaveState.LEGACY_SCHEMA_VERSION, posts);
        return new PetPostSavedData(posts);
    }

    private static VersionedPosts forWrite(PetPostSavedData data) {
        PostalPetSaveState state = toSaveState(PostalPetSaveState.CURRENT_SCHEMA_VERSION, data.posts).forWrite();
        return new VersionedPosts(state.schemaVersion(), data.posts);
    }

    private static PostalPetSaveState toSaveState(int schemaVersion, Map<String, PostEntry> posts) {
        Map<String, PostRecord> contracts = new LinkedHashMap<>();
        posts.forEach((key, post) -> contracts.put(key, PetPostContractMapper.toContract(post)));
        return new PostalPetSaveState(schemaVersion, contracts);
    }

    public static PetPostSavedData get(ServerLevel level) {
        return level.getServer().overworld().getDataStorage().computeIfAbsent(TYPE);
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

    /** Finds the player's nearest mailbox without loading chunks. */
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
        return level.dimension().identifier().toString();
    }

    /** Serializable record of a marked, player-owned mailbox. */
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

    private record VersionedPosts(int schemaVersion, Map<String, PostEntry> posts) {
        private VersionedPosts {
            int readableVersion = PostalPetSaveState.readVersion(true, schemaVersion);
            if (readableVersion != schemaVersion) throw new IllegalArgumentException("schemaVersion inválida");
            toSaveState(schemaVersion, posts);
            posts = new LinkedHashMap<>(posts);
        }
    }
}
