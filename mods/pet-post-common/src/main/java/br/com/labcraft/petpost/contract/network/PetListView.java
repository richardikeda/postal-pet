package br.com.labcraft.petpost.contract.network;

/** Point-in-time pet view for the screen; {@code loaded} does not guarantee future availability. */
public record PetListView(String name, String type, boolean loaded, String uuid, int entityId, String guideMaterial) {
    public PetListView(String name, String type, boolean loaded, String uuid, int entityId) {
        this(name, type, loaded, uuid, entityId, "none");
    }
    public PetListView(String name, String type, boolean loaded) {
        this(name, type, loaded, "", -1, "none");
    }
}
