# Roadmap

Os itens descrevem trabalho pretendido, sem prometer recursos disponíveis ou datas.

## Fundação atual

Receita e ativação da caixa; guias de prata/ouro; vínculo único; desvínculo explícito; nome e raio da caixa; retrato, vida, fase de crescimento e armadura do pet; informações de coleira/pingente; persistência e rede validadas no servidor. O núcleo comum e os dois adapters têm verificações automatizadas de build e contratos.

## Próximos marcos

1. Validar a versão atual em cliente e servidor dedicado por alvo: save/restart, posse, entidades descarregadas e caixas sobrepostas.
2. Estabilizar API e fronteiras, preservando IDs persistidos.
3. Implementar ações tipadas e autorizadas no servidor para chamar, voltar e seguir, além de modos Ficar em Casa / Circular pela Área / Livre.
4. Melhorar descoberta com busca, filtros, localização e acessibilidade.

## Expansões opcionais

- Field Guide: scanner de criaturas somente de leitura e, depois, diário de campo.
- Habitats: casinha de cachorro, cama de gato, poleiro de papagaio e baia.
- Accessories: coleiras, tags, rastreadores, cosméticos e bússola com última localização conhecida.
- Care: tigelas, água, petiscos, escovação e brinquedos; falta de cuidado não deve matar pets.
- Training: apito, clicker e alvos, após definir permissões e limites de comportamento.

## Política de versões

Manter paridade entre 1.21 e 26.2. Novas versões exigem adapter, dependências, CI e aceitação no jogo próprios. Add-ons mantêm dados próprios e devem ser removíveis sem danificar os vínculos principais.
