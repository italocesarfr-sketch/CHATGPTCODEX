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

## Implementação e validação concluída em ambiente descartável

- `6336462`: avaliação e decisão antes da alteração funcional.
- `bbeb0c0`: escopo automático bloqueado no planejador e retirado das opções comuns; manual restrito ao ano letivo da origem; campos de data por destino removidos; preservada a revalidação transacional existente.
- Somente dois arquivos de produção alterados: `data/ActivityCopies.kt` e `ui/Forms.kt`. Nenhum código de chamada, cálculo, exportação, backup ou transporte foi alterado.
- Testes atualizados: `ActivityCopiesTest`, `ActivityCopiesRoomTest`, `ActivityCopiesUiTest` e `FunctionalNetworkProbeTest`. As provas de criação independente, rollback, duplicatas, reabertura, resposta perdida e sincronização continuam presentes; não foi reduzida a cobertura de segurança.
- Comando local: JDK 17, `./gradlew --offline --no-daemon testDebugUnitTest assembleDebug assembleDebugAndroidTest`. BUILD SUCCESSFUL; 309 casos JVM, 304 aprovados, zero falhas e cinco condicionais hospedados não executados. Nenhum resultado condicional foi contado como aprovação.
- [CI 43597997812 aprovada](https://github.com/italocesarfr-sketch/CHATGPTCODEX/actions/runs/43597997812), SHA `bbeb0c056d17e5c8204dd6b0327667dc42e6d7e6`, job `functional / tests`.

| Prova executada | Aprovados | Falhas | Não executados/ignorados |
|---|---:|---:|---:|
| JVM completo | 304 | 0 | 5 condicionais hospedados |
| Android selecionado, nove classes (inclui dez testes Room de cópias) | 59 | 0 | 0 |
| Repetição UI a 360×640 dp e fonte nativa 150% | 3 | 0 | 0 |
| Room/HTTP real em dois AVDs independentes | 10 fases | 0 | 0 |
| HTTP de segurança em PostgreSQL/PostgREST descartável | 9 | 0 | 0 |

Os cinco condicionais JVM são H01–H04 e SupabaseLiveAuthTest, sem credenciais/autorização nesta rodada. Os jobs H131/H132 ficaram fora da seleção e não representam aprovações. Não foi reexecutada a suíte Android global nem a investigação de volume.

**Provas específicas:** origem 1º K, pares 1º H e turmas do 2º/3º anos com a mesma etapa não habilitam o automático sem série estruturada. A interface não oferece esse escopo nem turmas de 2027 quando a origem é de 2026. Selecionar todas no modo manual inclui somente as três turmas elegíveis de 2026. Seleção individual entre 1º/2º/3º anos, resumo, salvamento e notas independentes foram testados; não se usa análise do nome para decidir compatibilidade.

No Room, pedidos forjados que acrescentam 2027, com ou sem data individual, e o escopo automático antigo foram rejeitados sem alterar o snapshot, versões ou outbox. Uma atividade antiga de 2027 com nota zero foi sincronizada pelo Remote controlado do teste Room e preservada após a rejeição e a reabertura do banco; isso não é homologação hospedada. Um novo destino não selecionado não entra no pedido existente.

Na prova HTTP real local, duas instalações recuperaram as três atividades de 1º/2º/3º anos de 2026 com os mesmos IDs; zero, pendente e 5,0 permaneceram distintos. Alterar a nota da segunda turma para 6,5 no outro emulador preservou zero na primeira e 5,0 na terceira. A tentativa com 2027 não criou atividades nem operações pendentes; a turma de 2027 pode existir e sincronizar normalmente sem participar da criação em lote. A regressão P/F/FJ/NR continuou aprovada.

[Artefato completo 14114513955](https://github.com/italocesarfr-sketch/CHATGPTCODEX/actions/runs/43597997812/artifacts/14114513955). Capturas nativas inspecionadas e copiadas sem edição: [normal](evidence/ESCOPO_ATIVIDADES_20261114/selecao-manual-2026.png), [fonte 150%](evidence/ESCOPO_ATIVIDADES_20261114/selecao-manual-fonte-150.png). [Manifesto com contagens e hashes](evidence/ESCOPO_ATIVIDADES_20261114/manifest.json).

**Pendência deliberada:** a seleção automática por série não está implementada como função utilizável. Está bloqueada até avaliação/aprovação da evolução estrutural descrita acima e seus testes de migração/compatibilidade. A seleção manual segura está funcional e validada localmente; não equivale à homologação física/hospedada da nova regra.

A restrição vale para o comando local de criação em lote. O protocolo continua transportando registros independentes de múltiplos anos letivos, inclusive atividades antigas de 2027: impedir isso no transporte corromperia a recuperação normal da conta. Nenhum lote antigo é reinterpretado como novo pedido de criação.

A CI antiga não aprova automaticamente a regra nova. Nenhum teste hospedado ou instalação existente foi utilizado.
