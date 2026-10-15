package br.com.labcraft.petpost.contract;

import br.com.labcraft.petpost.PostRadius;
import br.com.labcraft.petpost.application.CoreUseCasesTest;
import br.com.labcraft.petpost.contract.network.PetListResponse;
import br.com.labcraft.petpost.contract.network.PetListView;
import br.com.labcraft.petpost.contract.persistence.PetRecord;
import br.com.labcraft.petpost.contract.persistence.PostalPetSaveState;
import br.com.labcraft.petpost.contract.persistence.PostRecord;
import br.com.labcraft.petpost.ui.model.PostalPetUiState;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Testes sem framework e sem APIs Minecraft/Fabric, executados nas duas variantes. */
public final class CoreContractsTest {
    private CoreContractsTest() {
    }

    public static void main(String[] args) {
        assertRadiusRules();
        assertPersistenceMirrorIsImmutable();
        assertPersistenceSchemaEvolution();
        assertNetworkContractKeepsRefusalPrivate();
        assertScreenUiState();
        CoreUseCasesTest.main(args);
    }

    private static void assertRadiusRules() {
        require(PostRadius.atStep(0) == 8, "A primeira etapa deve continuar em 8");
        require(PostRadius.atStep(PostRadius.nextStep(3)) == 8, "O ciclo de raio deve retornar a 8");
        require(PostRadius.closestStep(25) == 2, "25 deve escolher o raio 24 já suportado");
        require(!PostRadius.outsideHorizontalArea(8.5, 0.5, 0, 0, 8), "A borda pertence à área");
        require(PostRadius.outsideHorizontalArea(8.51, 0.5, 0, 0, 8), "Fora da borda deve parar de seguir");
    }

    private static void assertPersistenceMirrorIsImmutable() {
        PetRecord pet = new PetRecord(UUID.fromString("00000000-0000-0000-0000-000000000001"),
                "minecraft:wolf", "Lobo", "minecraft:overworld", 42L, 99L);
        List<PetRecord> mutablePets = new ArrayList<>(List.of(pet));
        PostRecord post = new PostRecord("minecraft:overworld", 42L,
                "00000000-0000-0000-0000-000000000002", "Postal Pet 1", 8, mutablePets);
        mutablePets.clear();

        require(post.pets().size() == 1, "O contrato não deve reter lista mutável do adapter");
        require(post.storageKey().equals("minecraft:overworld|42"), "A chave lógica deve permanecer dimensão|posição");
    }

    private static void assertNetworkContractKeepsRefusalPrivate() {
        PetListView pet = new PetListView("Lobo", "minecraft:wolf", false);
        PetListResponse accepted = new PetListResponse(42L, 7, "minecraft:overworld", true, List.of(pet));
        require(accepted.pets().size() == 1 && !accepted.pets().getFirst().loaded(),
                "loaded deve permanecer observação pontual");
        expectIllegalArgument(() -> new PetListResponse(42L, 7, "minecraft:overworld", false, List.of(pet)));
    }

    private static void assertPersistenceSchemaEvolution() {
        PostRecord post = new PostRecord("minecraft:overworld", 42L,
                "00000000-0000-0000-0000-000000000002", "Postal Pet 1", 8, List.of());
        PostalPetSaveState legacy = PostalPetSaveState.legacy(java.util.Map.of(post.storageKey(), post));
        require(legacy.schemaVersion() == PostalPetSaveState.LEGACY_SCHEMA_VERSION,
                "Dados sem schemaVersion devem continuar legados");
        require(legacy.forWrite().schemaVersion() == PostalPetSaveState.CURRENT_SCHEMA_VERSION,
                "Nova gravação deve usar schemaVersion atual");
        require(legacy.forWrite().posts().equals(legacy.posts()),
                "Evolução de schema não pode perder entradas");
        require(PostalPetSaveState.readVersion(false, 123) == PostalPetSaveState.LEGACY_SCHEMA_VERSION,
                "Ausência de schemaVersion deve selecionar o legado");
        expectIllegalArgument(() -> PostalPetSaveState.readVersion(true, 2));
    }

    private static void assertScreenUiState() {
        List<PetListView> pets = List.of(
                new PetListView("Lobo", "minecraft:wolf", true),
                new PetListView("Gato", "minecraft:cat", false),
                new PetListView("Papagaio", "minecraft:parrot", true),
                new PetListView("Raposa", "minecraft:fox", false),
                new PetListView("Coelho", "minecraft:rabbit", true),
                new PetListView("Cavalo", "minecraft:horse", false));
        PostalPetUiState loading = PostalPetUiState.loading(42L, 7, "minecraft:overworld");
        require(loading.snapshotStatus() == PostalPetUiState.SnapshotStatus.LOADING && loading.visiblePets(4).isEmpty(),
                "A tela deve iniciar carregando sem apresentar uma lista vazia");

        PetListResponse accepted = new PetListResponse(42L, 7, "minecraft:overworld", true, pets);
        PostalPetUiState ready = loading.accept(accepted, 4);
        require(ready.hasSnapshot() && ready.pets().size() == 6 && !ready.pets().get(1).loaded(),
                "A resposta autorizada deve preservar a observação pontual loaded");
        require(ready.visiblePets(4).size() == 4 && ready.visiblePets(4).getFirst().name().equals("Lobo"),
                "A primeira página deve manter quatro linhas visíveis");

        PostalPetUiState scrolled = ready.scroll(false, 4).scroll(false, 4).scroll(false, 4);
        require(scrolled.firstPet() == 2 && scrolled.visiblePets(4).getLast().name().equals("Cavalo"),
                "A rolagem deve parar no último intervalo válido");
        require(scrolled.scroll(true, 4).firstPet() == 1, "A rolagem de retorno deve mover uma linha");
        PostalPetUiState selected = scrolled.select(5);
        require(selected.selectedPet() == 5 && selected.select(9).selectedPet() == 5,
                "Seleção inválida não pode apagar a seleção local existente");
        require(selected.toggleSelection(5).selectedPet() == -1,
                "Clicar novamente no pet selecionado deve voltar ao contexto da caixa");
        require(selected.clearSelection().selectedPet() == -1 && selected.clearSelection().firstPet() == selected.firstPet(),
                "Limpar detalhe deve preservar a rolagem local");

        PetListResponse delayed = new PetListResponse(43L, 7, "minecraft:overworld", true, pets);
        require(scrolled.accept(delayed, 4).equals(scrolled), "Resposta atrasada ou de outro posto deve ser descartada");
        PetListResponse refused = new PetListResponse(42L, 7, "minecraft:overworld", false, List.of());
        require(ready.accept(refused, 4).snapshotStatus() == PostalPetUiState.SnapshotStatus.UNAVAILABLE,
                "Recusa autorizada deve tornar a sincronização indisponível");
    }

    private static void expectIllegalArgument(Runnable action) {
        try {
            action.run();
            throw new AssertionError("Era esperada IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            // Contrato confirmado.
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
