# Escopo de atividades — série escolar e ano letivo

## Avaliação anterior à alteração estrutural

Base preservada: `9e91b76`; código validado `8974312`, CI `43552621657`. Esta correção substitui somente as regras de seleção de destinos. O relatório anterior permanece como evidência histórica; a criação entre 2026 e 2027 deixa de ser comportamento permitido.

`SchoolClass` (`data/Database.kt`) tem `year` (ano letivo), `stage` (etapa, como Médio) e `name` livre. Não existe série escolar estruturada. O registro remoto `classes` no contrato `supabase/contracts/academic-v2.json` e no registro SQL `202611010001_academic_protocol.sql` possui os mesmos campos. Etapa não identifica série e o nome não é fonte confiável para classificação automática.

**Decisão segura desta rodada:** não acrescentar campo ou inferência. A opção automática "Todas as turmas da mesma série" ficará indisponível no seletor comum, e o planejador rejeitará explicitamente esse escopo mesmo que chamado diretamente ou por estado antigo. A alternativa é a seleção manual explícita, incluindo Selecionar todas/Desmarcar todas, limitada às turmas compatíveis do mesmo ano letivo. Assim, nenhuma seleção automática originada no 1º K poderá incluir indevidamente 2º ou 3º anos.

Na seleção manual, diferentes séries podem coexistir em 2026; nenhuma turma de 2027 poderá integrar esse pedido, nem fornecendo uma data específica para ela. A validação no repositório continua transacional. Um destino incompatível impede o pedido inteiro; ele não é descartado silenciosamente.

## O que exigiria identificar a série de forma persistente

Proposta para avaliação posterior, **não implementada nem autorizada para implantação**:

1. Campo estruturado opcional de série por turma, com códigos coerentes com a etapa. Turmas antigas começariam com série desconhecida; o professor a confirmaria explicitamente. Não usar nome, etapa, ano letivo ou idade para preencher automaticamente.
2. Migração Room aditiva, leitura de backups antigos como série desconhecida e formato lógico atualizado; preservar IDs, notas, matrículas, históricos e titularidade.
3. Versionamento/negociação do contrato de sincronização para transportar a série entre dispositivos. O contrato atual valida campos e snapshots; acrescentar apenas uma propriedade Kotlin não garante compatibilidade. Exige plano de evolução dos clientes, validadores, hashes, versões, históricos preservados e restauração. As políticas de proprietário/sessão/RLS não poderiam ser relaxadas.
4. Somente depois disso, a seleção automática deveria exigir série conhecida da origem e dos destinos, mesmo ano letivo, mesma série, etapa e disciplina compatíveis, turma não arquivada e período/data válidos. Série desconhecida não seria incluída automaticamente.

Uma preferência só local ou associação transitória pelo nome não resolve a identidade da série em outro dispositivo. Por isso não foi criada uma fonte paralela de metadados nesta correção.

## Preservação

- Nenhuma migração Room, SQL hospedado, alteração de contrato, backup, criptografia, assinatura ou `applicationId` nesta rodada.
- Atividades anteriormente criadas, inclusive as cópias de 2027 do ensaio anterior, continuam legíveis, editáveis e sincronizáveis como registros independentes de suas turmas. Não há varredura, renumeração, conversão ou exclusão retroativa.
- A seleção manual é congelada no pedido; uma turma nova não entra silenciosamente em uma confirmação já preparada.
- Permanecem transação única, prevenção de duplicatas, IDs independentes/estáveis para repetição do pedido, notas próprias e outbox incremental existente.
- O campo transitório `dates` do pedido anterior não faz parte do Room/backup/protocolo remoto: permanece apenas para rejeitar pedidos antigos com datas individuais, sem reaproveitá-los silenciosamente.

## Plano de validação

Fixtures de 1º, 2º e 3º anos de 2026, outra turma de 2027 e nomes livres sem série confiável. Testar bloqueio do automático sem metadado, seleção manual entre séries, visibilidade apenas de 2026, rejeição de mistura 2026/2027 e de datas individuais antigas, ausência de criação parcial/outbox em erro, idempotência, duplicatas, notas independentes e recuperação Android por HTTP em dois AVDs descartáveis. Preservar a regressão de chamada e os testes anteriores de criação atômica.

Resultados desta correção serão registrados após execução; a CI antiga não aprova automaticamente a regra nova. Nenhum teste hospedado ou instalação existente será utilizado.
