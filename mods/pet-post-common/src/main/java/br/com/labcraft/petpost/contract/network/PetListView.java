package br.com.labcraft.petpost.contract.network;

/** Visão pontual de um pet para a tela; {@code loaded} não garante presença futura. */
public record PetListView(String name, String type, boolean loaded, String uuid, int entityId, String guideMaterial) {
    public PetListView(String name, String type, boolean loaded, String uuid, int entityId) {
        this(name, type, loaded, uuid, entityId, "none");
    }
    public PetListView(String name, String type, boolean loaded) {
        this(name, type, loaded, "", -1, "none");
    }
}
