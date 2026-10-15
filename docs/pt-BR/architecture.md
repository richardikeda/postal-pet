# Arquitetura

## Como ler os fontes

| Componente | Responsabilidade |
|---|---|
| pet-post-common/application | Regras de vínculo, desvínculo, nome, raio e consulta autorizada |
| contract/persistence | Snapshots imutáveis de pets/caixas e envelope de schema |
| contract/network | Dados de requisição/resposta independentes da versão |
| ui/model | Estado da consulta, seleção e rolagem |
| PetPost | Entrada Fabric, registros, comandos e interações físicas |
| PetPostSavedData | NBT na 1.21; Codec na 26.2; schema lógico compartilhado |
| PetPostContractMapper | Conversão explícita entre jogo e núcleo |
| PetListPayloads | Consulta versionada, nonce, autorização e resposta |
| PetActionPayloads | Requisições de nome, raio e desvínculo ligadas à posição |
| PetHomeFollowGuard / FollowOwnerGoalMixin | Suspensão de seguir e recuperação de pets carregados |
| PostalPetClient / PostalPetScreen | Ciclo da tela, snapshots autorizados e retratos reais |
| TerritoryPreview | Partículas temporárias da área |

## Do clique à persistência

O servidor recebe a interação com guia especial, valida o dono do pet vanilla domesticado e escolhe a caixa do dono mais próxima, a até oito blocos do pet. BindPetUseCase cria um snapshot ou rejeita vínculo existente. Só uma alteração persistida com sucesso consome guia. Remover vínculo é explícito.

O menu consulta posição e nonce exatos. O servidor verifica chunk carregado, bloco, distância e dono. O cliente aceita apenas caixa/dimensão/nonce correspondentes e distingue lista indisponível de vazia. Retratos usam entidades acompanhadas pelo cliente; listagem não promete disponibilidade da entidade.

## Alterar o projeto

Use o Java do alvo e execute gradlew.bat --no-daemon build (Windows) ou bash gradlew --no-daemon build dentro de cada adapter. Ambas as builds incluem testes comuns e verificações do adapter. Código específico da API fica nos adapters. Preserve IDs petpost e schema. Teste save/restart e autorização de rede em mundos descartáveis.

A idade cronológica é omitida porque não se registra nascimento. A fase usa filhote/adulto da entidade acompanhada. Coleira ou pingente mostrado na UI não implica um renderer novo de equipamento no mundo.

O adapter 1.21 usa o nome persistido petpost_pet_posts; a 26.2 usa o identificador petpost:pet_posts. Preserve a identidade persistida de cada adapter, salvo migração testada. Mudança de protocolo exige builds correspondentes no cliente/servidor.

O núcleo Java 21 compartilha regras, contratos, casos de uso e estado da UI sem imports Minecraft/Fabric. Adapters por versão tratam registro, persistência, rede, eventos, mixins e renderização. O servidor valida posse e distância. Cada alvo novo exige adapter próprio, dependências fixadas, manifesto/CI e testes no jogo. Preserve IDs petpost e dados salvos; um JAR não suporta todas as releases.

