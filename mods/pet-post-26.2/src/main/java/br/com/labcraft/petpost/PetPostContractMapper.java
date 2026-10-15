package br.com.labcraft.petpost;

import br.com.labcraft.petpost.contract.network.PetListRequest;
import br.com.labcraft.petpost.contract.network.PetListResponse;
import br.com.labcraft.petpost.contract.network.PetListView;
import br.com.labcraft.petpost.contract.persistence.PetRecord;
import br.com.labcraft.petpost.contract.persistence.PostRecord;

/**
 * Conversões explícitas da variante 26.2 para os contracts puros do PPCA.
 *
 * <p>O adapter converte somente na fronteira dos casos de uso puros. Codec,
 * codecs de pacote e seus formatos atuais permanecem responsabilidade desta
 * variante.</p>
 */
public final class PetPostContractMapper {
    private PetPostContractMapper() {
    }

    public static PostRecord toContract(PetPostSavedData.PostEntry source) {
        return new PostRecord(source.dimension(), source.position(), source.owner(), source.name(), source.radius(),
                source.pets().stream().map(PetPostContractMapper::toContract).toList());
    }

    public static PetPostSavedData.PostEntry toAdapter(PostRecord source) {
        return new PetPostSavedData.PostEntry(source.dimension(), source.position(), source.owner(), source.name(), source.radius(),
                source.pets().stream().map(PetPostContractMapper::toAdapter).toList());
    }

    public static PetRecord toContract(PetPostSavedData.PetEntry source) {
        return new PetRecord(source.uuid(), source.type(), source.name(), source.lastDimension(), source.lastPosition(), source.lastSeen(), source.guideMaterial());
    }

    public static PetPostSavedData.PetEntry toAdapter(PetRecord source) {
        return new PetPostSavedData.PetEntry(source.uuid(), source.type(), source.name(), source.lastDimension(), source.lastPosition(), source.lastSeen(), source.guideMaterial());
    }

    public static PetListRequest toContract(PetListPayloads.Request source) {
        return new PetListRequest(source.position(), source.nonce());
    }

    public static PetListPayloads.Request toAdapter(PetListRequest source) {
        return new PetListPayloads.Request(source.position(), source.nonce());
    }

    public static PetListResponse toContract(PetListPayloads.Response source) {
        return new PetListResponse(source.position(), source.nonce(), source.dimension(), source.accepted(),
                source.pets().stream().map(PetPostContractMapper::toContract).toList(), source.postName(), source.radius());
    }

    public static PetListPayloads.Response toAdapter(PetListResponse source) {
        return new PetListPayloads.Response(source.position(), source.nonce(), source.dimension(), source.accepted(),
                source.pets().stream().map(PetPostContractMapper::toAdapter).toList(), source.postName(), source.radius());
    }

    public static PetListView toContract(PetListPayloads.PetView source) {
        return new PetListView(source.name(), source.type(), source.loaded(), source.uuid(), source.entityId(), source.guideMaterial());
    }

    public static PetListPayloads.PetView toAdapter(PetListView source) {
        return new PetListPayloads.PetView(source.name(), source.type(), source.loaded(), source.uuid(), source.entityId(), source.guideMaterial());
    }
}
