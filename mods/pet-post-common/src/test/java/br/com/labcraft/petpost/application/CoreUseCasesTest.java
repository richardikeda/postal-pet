package br.com.labcraft.petpost.application;

import br.com.labcraft.petpost.application.query.ListPetsQuery;
import br.com.labcraft.petpost.contract.network.PetListView;
import br.com.labcraft.petpost.contract.persistence.PetRecord;
import br.com.labcraft.petpost.contract.persistence.PostRecord;

import java.util.List;
import java.util.UUID;

/** Testes puros dos primeiros casos de uso PPCA, executados nas duas variantes. */
public final class CoreUseCasesTest {
    private static final String OWNER = "00000000-0000-0000-0000-000000000001";
    private static final String OTHER_OWNER = "00000000-0000-0000-0000-000000000002";
    private static final String DIMENSION = "minecraft:overworld";
    private static final UUID PET_ID = UUID.fromString("00000000-0000-0000-0000-000000000010");

    private CoreUseCasesTest() {
    }

    public static void main(String[] args) {
        assertBindKeepsOneOwnerBinding();
        assertUnbindOnlyChangesTheRequestedPost();
        assertRenameKeepsExistingRules();
        assertRadiusCycleKeepsExistingSteps();
        assertListPetsKeepsOrderLimitsAndLoadedObservation();
    }

    private static void assertBindKeepsOneOwnerBinding() {
        PetRecord oldPet = pet("Antigo", 1L);
        PetRecord newPet = pet("Novo", 2L);
        List<PostRecord> posts = List.of(post(11L, OWNER, List.of()), post(22L, OWNER, List.of()),
                post(33L, OTHER_OWNER, List.of(oldPet)));

        BindPetUseCase.Result result = new BindPetUseCase().execute(posts, OWNER, DIMENSION, 22L, newPet);
        require(result.changed(), "O vínculo autorizado deve ser aceito");
        require(result.posts().get(0).pets().isEmpty(), "Outro posto deve permanecer vazio");
        require(result.posts().get(1).pets().equals(List.of(newPet)), "O novo vínculo deve entrar no posto escolhido");
        require(result.posts().get(2).pets().equals(List.of(oldPet)), "Outro dono não pode ser alterado");
        require(!new BindPetUseCase().execute(posts, OWNER, DIMENSION, 44L, newPet).changed(),
                "Posto inexistente deve recusar o vínculo");
        require(!new BindPetUseCase().execute(result.posts(), OWNER, DIMENSION, 22L, newPet).changed(),
                "Clique repetido não pode criar novo vínculo ou cobrar outra guia");
        require(!new BindPetUseCase().execute(result.posts(), OWNER, DIMENSION, 11L, newPet).changed(),
                "Transferência precisa de desvínculo explícito");
    }

    private static void assertUnbindOnlyChangesTheRequestedPost() {
        PetRecord pet = pet("Lobo", 3L);
        List<PostRecord> posts = List.of(post(11L, OWNER, List.of(pet)), post(22L, OWNER, List.of(pet)));
        UnbindPetUseCase.Result result = new UnbindPetUseCase().execute(posts, OWNER, DIMENSION, 11L, PET_ID);
        require(result.changed() && result.posts().get(0).pets().isEmpty(), "O vínculo solicitado deve ser removido");
        require(result.posts().get(1).pets().equals(List.of(pet)), "Outro posto deve manter seu vínculo");
        require(!new UnbindPetUseCase().execute(posts, OWNER, DIMENSION, 11L,
                UUID.fromString("00000000-0000-0000-0000-000000000099")).changed(), "UUID ausente deve recusar");
    }

    private static void assertRenameKeepsExistingRules() {
        PostRecord source = post(11L, OWNER, List.of());
        String longName = "123456789012345678901234567890123456";
        PostRecord renamed = new RenamePostUseCase().execute(source, OWNER, "  " + longName + "  ").orElseThrow();
        require(renamed.name().equals(longName.substring(0, 32)), "O nome deve ser aparado e limitado a 32 caracteres");
        require(new RenamePostUseCase().execute(source, OWNER, "  ").isEmpty(), "Nome vazio deve recusar");
        require(new RenamePostUseCase().execute(source, OTHER_OWNER, "Casa").isEmpty(), "Outro dono deve recusar");
    }

    private static void assertRadiusCycleKeepsExistingSteps() {
        ChangeRadiusUseCase.Result result = new ChangeRadiusUseCase().execute(3);
        require(result.step() == 0 && result.radius() == 8, "A última etapa deve voltar ao raio 8");
    }

    private static void assertListPetsKeepsOrderLimitsAndLoadedObservation() {
        List<PetRecord> pets = new java.util.ArrayList<>();
        for (int index = 0; index < 33; index++) {
            UUID uuid = new UUID(0L, index + 1L);
            pets.add(new PetRecord(uuid, "minecraft:wolf", "Pet " + index, DIMENSION, index, index));
        }
        pets.set(0, new PetRecord(pets.getFirst().uuid(), "minecraft:wolf", "A".repeat(97), DIMENSION, 0L, 0L));
        List<PetListView> views = new ListPetsQuery().execute(post(44L, OWNER, pets),
                uuid -> uuid.equals(pets.get(1).uuid()));

        require(views.size() == 32, "A consulta deve preservar o limite físico de 32 pets");
        require(views.getFirst().name().length() == 96 && views.getFirst().type().equals("minecraft:wolf"),
                "A consulta deve preservar os limites e campos do buffer");
        require(views.get(1).name().equals("Pet 1") && views.get(1).loaded(),
                "A observação loaded deve ser pontual e manter a ordem dos vínculos");
        require(views.get(2).name().equals("Pet 2") && !views.get(2).loaded(),
                "A consulta não pode inferir loaded para pets não observados");
        require(views.stream().noneMatch(view -> view.name().equals("Pet 32")),
                "O vínculo além do limite não pode entrar no snapshot");
    }

    private static PostRecord post(long position, String owner, List<PetRecord> pets) {
        return new PostRecord(DIMENSION, position, owner, "Postal Pet", 16, pets);
    }

    private static PetRecord pet(String name, long lastSeen) {
        return new PetRecord(PET_ID, "minecraft:wolf", name, DIMENSION, 22L, lastSeen);
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
