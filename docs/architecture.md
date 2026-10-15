# Architecture

## Reading the source

| Component | Responsibility |
|---|---|
| pet-post-common/application | Bind, unbind, rename, range and authorized list-query rules |
| contract/persistence | Immutable pet/mailbox snapshots and schema envelope |
| contract/network | Version-independent request/response data |
| ui/model | Snapshot status, selection and scrolling |
| PetPost | Fabric entrypoint, registry, commands and physical interactions |
| PetPostSavedData | NBT in 1.21; Codec in 26.2; shared logical schema |
| PetPostContractMapper | Explicit conversion across game and common boundaries |
| PetListPayloads | Versioned list query, nonce, authorization and response |
| PetActionPayloads | Position-bound rename, range and unlink requests |
| PetHomeFollowGuard / FollowOwnerGoalMixin | Loaded-pet follow suppression and bounded home recovery |
| PostalPetClient / PostalPetScreen | Menu lifecycle, authorized snapshots and real tracked portraits |
| TerritoryPreview | Temporary range particles |

## A link from click to storage

The server receives a special-guide interaction, validates a vanilla tamed pet's owner and selects the nearest owned mailbox within eight blocks of the pet. BindPetUseCase creates a new snapshot or rejects an existing link. Only a successful persisted change consumes a guide. Removing a link is explicit.

The menu requests a list for an exact position and nonce. The server checks loaded chunk, block, distance and owner. The client accepts only the matching mailbox/dimension/nonce and renders unavailable or empty states honestly. Portraits resolve tracked client entities; listing never promises entity availability.

## Changing the project

Run the matching Java version and execute gradlew.bat --no-daemon build (Windows) or bash gradlew --no-daemon build inside each adapter. Both builds include common tests and adapter checks. API-specific code stays in adapters. Preserve petpost IDs and schema compatibility. Test save/restart and network authorization in disposable worlds.

Chronological age is omitted because birth dates are not recorded. The displayed stage uses the tracked entity's baby/adult state. A collar color or pendant shown in this UI does not imply a new equipped-world renderer.

The 1.21 adapter uses the saved-data name petpost_pet_posts; 26.2 uses the identifier petpost:pet_posts. Preserve each adapter's persisted identity unless a migration is tested. A runtime protocol change requires matching client/server builds.

The Java 21 core shares rules, contracts, use cases and UI state without Minecraft/Fabric imports. Version adapters own registration, persistence, networking, events, mixins and rendering. The server validates ownership and distance. Each new Minecraft target requires its own adapter, pinned dependencies, manifest/CI entries and gameplay tests. Preserve petpost IDs and saved data; one JAR does not support every release.

