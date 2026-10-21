# Noctua 0.15.2 — conversão de destino externo para interno

Incremento restrito sobre a 0.15.1 / 26. Entrega **0.15.2 / 27**, aprovada no CI e no teste de atualização assinada sobre a release original 0.15.1.

## Fluxo preservado

Em Editar aluno → Corrigir remanejamento, escolha **Uma das minhas turmas**, selecione o destino e informe a data efetiva. **Corrigir** abre a mesma revisão da 0.15.1. A prévia informa origem, destino externo anterior, turma interna, data e se a matrícula será criada ou vinculada. As marcações afetadas continuam disponíveis para seleção explícita, com atualização automática da prévia.

**Confirmar correção** abre um único diálogo **Cancelar | Confirmar**. Voltar, descarte e cancelamento mantêm o comportamento anterior. Nenhum aluno, vínculo ou lançamento é gravado ao apenas conferir a prévia.

## Matrículas e regras compartilhadas

`RelocationRules.internalTarget` concentra a resolução já utilizada pelo remanejamento inicial: compatibilidade por `RelocationRules.compatible`, turma não arquivada, ano/disciplina/etapa/escala/bimestres/política de FJ, ausência de ciclos, identidade, matrícula existente e prevenção de homônimos ou duplicidades. O remanejamento inicial reutiliza essa função, preservando suas regras.

Sem matrícula correspondente no destino, a conversão cria um ID próprio, mantém o `personId`, ingresso na data efetiva, situação Ativo e número de chamada livre. Não cria notas, presenças, faltas, FJ, NR, atividades ou recomposições.

Quando já existe matrícula da mesma pessoa, ela só é vinculada se estiver ativa, sem saída, ingresso exatamente na data efetiva e sem outro vínculo. Nome, número, observações e registros do destino não são sobrescritos. Na conversão, qualquer frequência, nota ou recomposição anterior ao ingresso, inclusive NR e valores pendentes, impede o vínculo. Nenhuma data é ajustada silenciosamente.

Conflitos de recomposição no mesmo bimestre são recusados pelas regras existentes da cadeia. Não há soma alternativa, fusão de identidades ou nova fórmula de notas/frequência. Os relatórios e exportações continuam consultando os registros originais pelos vínculos já existentes.

## Auditoria

A coluna JSON `relocations.corrections` comporta os novos campos sem alterar o esquema Room. Correções antigas são lidas como **CORRECAO_EXTERNA**, com os novos campos opcionais vazios.

A conversão acrescenta uma entrada **CONVERSAO_INTERNA**, preservando integralmente a lista anterior. A entrada registra:

- ID e momento da confirmação;
- datas anterior e nova;
- descrição externa anterior;
- IDs da matrícula de origem, matrícula de destino e turma de destino;
- identificação da turma na confirmação;
- cópias de auditoria das marcações explicitamente retiradas nesta operação.

O destino atual passa a ser `targetStudentId`; `Relocation.externalTarget` fica nulo. A auditoria da conversão também tem `externalTarget` nulo, pois a turma cadastrada não é simulada como uma descrição externa.

`validateLinks` verifica o encadeamento das datas e descrições anteriores, o tipo de operação e os IDs reais. A conversão deve ser o último ato dessa sequência de correções externas e corresponder ao destino atual. Remanejamentos posteriores do aluno usam os vínculos normais e não alteram essa auditoria. Renomear a turma não reescreve a identificação histórica registrada.

Os históricos preservados após excluir origem ou intermediária continuam contendo os mesmos vínculos e auditorias, sem duplicar notas ou frequência. As cópias de marcações retiradas são somente auditoria e não entram nos cálculos.

## Transação

A confirmação compara o snapshot integral, recalcula a prévia, revalida a matrícula do destino e mantém o UUID apresentado para uma matrícula nova. Na mesma transação Room, retira somente as marcações selecionadas, atualiza o encerramento da origem, cria ou vincula o destino e atualiza o vínculo com a auditoria acrescentada. Antes do commit, valida toda a cadeia e o conjunto de dados.

A matrícula de origem continua Remanejada, com os mesmos IDs, notas, observações e registros legítimos. Notas e recomposições não são excluídas. Uma FJ retirada deixa de ter linha contabilizável; não é convertida em P, F ou NR. Falha em qualquer etapa desfaz também a matrícula nova e a auditoria. Alterações concorrentes invalidam a prévia e exigem nova revisão, conforme o fluxo já existente.

## Compatibilidade

- Pacote `br.com.diariodeclasse`, banco `diario.db`, Room **v8**, suas 14 tabelas e identidade de esquema permanecem inalterados. Não há migração nem recriação de tabelas.
- Backup passa ao **formato 10**, identificando a auditoria estendida, com leitura dos formatos **1 a 9**. A leitura do formato 9 aplica os valores padrão às correções antigas. Conversões não podem ser disfarçadas como backups antigos; versões anteriores do aplicativo não leem o novo formato.
- Nenhuma chave, certificado, senha ou Secret é criado ou modificado. O workflow usa a assinatura permanente já configurada e fixa o APK original da 0.15.1 por hash.
- Login demonstrativo, funcionamento offline, exportações e identidade visual são preservados.

## Interface e skill

O arquivo `/run/codex-environment/codex-home/skills/impeccable/SKILL.md` não existe neste ambiente. Foi utilizado o fallback autorizado com Material 3 e Compose: mesmo formulário rolável, seletor existente, identificação completa do destino, rótulos acessíveis e confirmação com texto rolável. Não foi alegado uso da Impeccable nem utilizadas skills de planilhas ou documentos.

## Testes e resultados

Execução aprovada: [39690176006](https://github.com/italocesarfr-sketch/CHATGPTCODEX/actions/runs/39690176006), commit do aplicativo [`8f7b465da7fd8e07c777f99f42b83a56a8620479`](https://github.com/italocesarfr-sketch/CHATGPTCODEX/commit/8f7b465da7fd8e07c777f99f42b83a56a8620479). Os dois jobs terminaram com sucesso.

| Verificação executada | Resultado |
| --- | --- |
| JVM local e CI | 139 testes aprovados, zero falhas |
| Android direcionado | 35 testes aprovados, zero falhas |
| Regressão Android completa | 242 casos XML: 238 aprovados, quatro sondas condicionais fora dessa invocação, zero falhas |
| Layout existente | Uma verificação inicial aprovada |
| Frequência após reinício | Escrita/leitura em processos separados aprovadas |
| Atualização legada | Sondas antes/depois aprovadas |
| Exportações anteriores | Leitura independente dos XLSX, CSV e TSV existentes aprovada, sem alterar seus geradores |
| Atualização assinada 0.15.1 → 0.15.2 | Seis fases aprovadas: antes, depois, restauração, reabertura, exclusão com histórico e nova reabertura |
| Pacote, certificado e versão | Mesmo pacote/certificado; código aumentado de 26 para 27 |
| Room | v8 e 14 tabelas, sem alteração de esquema |
| Backup | Formato 10; leitura dos formatos anteriores preservada |

As quatro sondas condicionais são `FrequencyProcessProbeTest`, `UpgradeProbeTest`, `StableUpgradeProbeTest` e `ReleaseContinuityProbeTest`. Frequência, atualização legada e assinatura permanente tiveram invocações próprias aprovadas. A última pertence ao workflow legado de continuidade e não foi invocada; não foi contada como aprovada.

Os novos testes cobrem conversão sem auditoria, com correção de data, múltiplas correções e FJ anteriormente retirada; criação ou vínculo de matrícula; identidade, duplicidade, ciclos, turma arquivada/incompatível; notas zero/pendentes e recomposições; retirada explícita e ausência de lançamentos fictícios; concorrência, rollback, reinício, relatórios e exportação após exclusões sucessivas; backup antigo/novo com auditoria preservada.

O teste de falha transacional injeta uma interrupção na atualização do vínculo, depois de inserir a matrícula e retirar a FJ. A comparação integral comprova que nenhuma dessas mudanças permanece. A versão anterior do banco também é simulada com JSON de auditoria sem os novos campos, seguida de reabertura, conversão e restauração.

Na atualização assinada, uma correção externa com FJ retirada é semeada no **APK original da 0.15.1**, fixado por hash. Após instalar a candidata sem desinstalar, o teste compara o backup completo, realiza a conversão, exige a auditoria anterior como prefixo e verifica novamente após reiniciar. A restauração e a preservação de históricos após exclusões também passam.

A instrumentação de interface mantém a navegação simplificada, verifica cancelamento sem escrita, prévias de criação/vínculo, confirmação única e fonte ampliada configurada no teste. As duas capturas abaixo foram geradas no CI e inspecionadas. A execução final não apresentou falhas. Todos os dados são fictícios e os emuladores descartáveis; nenhum banco escolar real foi acessado.

## Release e capturas

[Baixar APK release assinado](https://github.com/italocesarfr-sketch/CHATGPTCODEX/actions/runs/39690176006/artifacts/12389310277). O ZIP contém `app-release.apk`, identidade, SHA-256 e comparação de atualização. O nome histórico do artefato `noctua-etapa6-release` foi preservado; o manifesto confirma **0.15.2 / código 27**, pacote `br.com.diariodeclasse`, mínimo API 26 e destino API 35.

O arquivo foi baixado e verificado localmente com `apksigner`, `aapt` e `check-apk-update.py`, comparando com a release original 0.15.1. A assinatura v2 é válida e o certificado é o mesmo. O arquivo conferido está em `build/noctua-0152-final/release/app-release.apk`.

- SHA-256 do APK: `909b4a54f5b29121a708824da42ebdcc169803bedb60eca34d837fdf5757d84d`.
- Certificado público SHA-256: `6dbbba2d930eb7f7fbeff1ee82512f656251bbec9f4b31b6c5614528d29f1aae`.
- [Contagens e evidências verificadas](CONVERSAO_REMANEJAMENTO_0152_RESULTADOS.json).
- [Prévia da conversão](images/conversao-0152/previa.png).
- [Confirmação final](images/conversao-0152/confirmacao.png).

## Arquivos modificados

Produção em `app/src/main/java/br/com/diariodeclasse`:

- `data/RelocationCorrection.kt`: auditoria tipada, destino proposto, prévia e projeção transacional reutilizadas.
- `data/RelocationRules.kt`: resolução de destino compartilhada e validação da sequência administrativa.
- `data/DiaryRepository.kt`: revalidação, criação/vínculo e gravação atômica, preservando os IDs.
- `ui/RelocationCorrectionForm.kt`: libera o seletor existente para conversão e apresenta a prévia/confirmação na interface já estabelecida.
- `backup/BackupCodec.kt`: formato 10 e leitura dos formatos anteriores, recusando conversões disfarçadas em formato antigo.

Testes em `app/src/test` e `app/src/androidTest`:

- `RelocationConversionTest.kt`: dez testes JVM novos.
- `RelocationConversionInstrumentedTest.kt`: dez testes Room novos.
- `FunctionalFifteenUiInstrumentedTest.kt`: dois fluxos novos e adaptação do teste que antes exigia o bloqueio da conversão.
- `FunctionalFifteenInstrumentedTest.kt` e `CorrectionRefinementTest.kt`: matrícula existente agora vinculada com segurança, mantendo as verificações de ausência de duplicação e escrita indevida.
- `StableUpgradeProbeTest.kt`: conversão após atualizar da release 0.15.1, com auditoria anterior preservada e reabertura.
- `BackupCodecTest.kt`, `ExportLayoutTest.kt`, `LessonRecordsTest.kt`, `RecompositionBackupTest.kt` e `RelocationRulesTest.kt`: expectativas de versão do backup atualizadas, mantendo os cenários de integridade.

Compilação e documentação:

- `app/build.gradle.kts`: 0.15.2 / 27.
- `.github/workflows/android.yml` e `docs/baseline/noctua-release-0.15.1.json`: referência assinada original 0.15.1 fixada por hash, sem alteração das credenciais.
- `scripts/ci/validate-android.sh`: inclui os dez testes Room novos e mantém a regressão completa.
- Este documento, resultados JSON, capturas, README, especificação e roadmap: contrato e validação da entrega.

[Comparação completa de código e testes](https://github.com/italocesarfr-sketch/CHATGPTCODEX/compare/c7421dc5923cba400ab364865ac4d330fa1b99e4...8f7b465da7fd8e07c777f99f42b83a56a8620479).

## Limitações

O incremento permite **externo → interno**. Correção unilateral de remanejamento já interno, troca entre destinos internos e reversão interno → externo continuam protegidas e não são implementadas. Conflitos acadêmicos precisam ser resolvidos pelo professor nas operações existentes; a conversão não decide por ele quais notas ou recomposições descartar.

A inspeção final no aparelho físico do proprietário e em outras dimensões/escalas de fonte permanece manual. O CI executou em Android API 30. Não foram modificados os módulos de relatórios, exportação, diário ou autenticação demonstrativa.
