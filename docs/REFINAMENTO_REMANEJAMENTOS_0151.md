# Noctua 0.15.1 — correção de remanejamentos

Incremento sobre a 0.15.0 / 25, limitado à correção administrativa existente. Versão entregue: **0.15.1 / 26**, validada no CI e na atualização assinada sobre a release original 0.15.0.

## Fluxo

Em Editar aluno → Corrigir remanejamento, confira a data e escolha o tipo de destino. **Corrigir** abre a revisão; os campos deixam de ocupar espaço na prévia. As marcações aparecem por data brasileira, horário e tipo. Notas zero e pendentes continuam distintas.

Selecionar ou desmarcar uma marcação atualiza a prévia automaticamente, com breve indicador de carregamento. Leituras antigas são canceladas quando a seleção muda. Não há segunda ação “Conferir correção”. A seleção, por si só, não grava nem retira dados.

**Voltar** na revisão retorna à edição e mantém data, destino e seleções que continuem elegíveis. Sair do fluxo com alterações pede uma única confirmação de descarte. Cancelar a confirmação final ou o descarte mantém os registros intactos.

**Confirmar correção** abre um único diálogo com estudante, datas, destino, contagem por tipo das marcações selecionadas e indicação de que a matrícula continuará Remanejada. Apenas **Confirmar** executa a transação já existente. Nenhuma confirmação adicional é apresentada depois.

Se os registros mudarem durante a revisão, a prévia é atualizada, o diálogo antigo é fechado e as seleções são retiradas para nova conferência explícita. A transação também compara integralmente o snapshot imediatamente antes de gravar. Uma mudança entre a prévia e o commit produz um conflito específico, sem escrita parcial.

## Destinos suportados e bloqueados

- **Outra turma / destino externo:** correção de data, descrição ou ambas, com auditoria, mesmos identificadores e proteção de notas, recomposições e frequências legítimas.
- **Uma das minhas turmas:** seletor por ID, identificado por nome, disciplina, ano e turno. Mostra turmas compatíveis pelas regras existentes e não arquivadas. Alternar de tipo preserva o texto externo durante a sessão.
- **Conversão externa → interna:** deliberadamente bloqueada neste incremento, conforme a alternativa autorizada. A auditoria distribuída na 0.15.0 descreve correções externas; criar uma matrícula ou converter o vínculo exigiria outro contrato de auditoria. A interface explica o impedimento e desabilita Corrigir enquanto esse tipo estiver selecionado. O repositório também rejeita um ID interno, inclusive quando já existe uma matrícula da mesma identidade no destino. Nenhuma turma cadastrada é gravada como texto externo.
- **Remanejamento interno existente:** tipo, destino e data ficam protegidos. A correção exigiria ajustar origem e destino em conjunto; não há alteração unilateral nem reativação temporária.

Destinos não elegíveis, inexistentes, iguais à origem ou arquivados também são recusados no repositório. Não foi criada uma fórmula de compatibilidade alternativa: o seletor reutiliza `RelocationRules.compatible`.

## Preservação

Room **v8**, suas 14 tabelas, `diario.db`, pacote `br.com.diariodeclasse` e backup **formato 9** permanecem inalterados. Não há migração, limpeza de tabelas nem alteração de assinatura ou Secrets. As exportações, a entrada demonstrativa e os demais módulos permanecem como estavam.

A confirmação reutiliza a remoção exata por matrícula/horário/tipo, o ajuste do encerramento, a auditoria e a validação de backup na mesma transação Room. A única alteração no controle de concorrência é identificar o conflito para a interface poder atualizar a revisão. P/F/FJ só saem mediante seleção explícita; sua retirada não cria P, F nem NR. Notas e recomposições nunca são apagadas nesse fluxo.

## Interface e acessibilidade

O caminho `/run/codex-environment/codex-home/skills/impeccable/SKILL.md` não existe neste ambiente. Não foi alegado uso da skill. Foi aplicado o fallback autorizado: Material 3 e Compose nativos, tema existente, formulário rolável, controles de toque padrão e rótulos de retirada que identificam data, horário e marcação. A atualização da prévia tem indicador acessível e mensagens de conflito anunciadas discretamente. Não foram usadas skills de planilhas ou documentos, pois este incremento não as exige.

## Testes implementados

- JVM: seleção/revalidação, datas brasileiras, destinos elegíveis e bloqueio de conversão mesmo com identidade já cadastrada, além da regressão completa.
- Android/Room: correções sucessivas de data/destino, trilha de auditoria, reinício, backup/restauração, ausência de duplicação e todos os testes anteriores de rollback e preservação.
- Compose: seleção sem conferência repetida, retorno com rascunho, descarte/cancelamento, confirmação única, destino interno bloqueado, texto externo preservado e mudança concorrente de marcação. Inclui fonte ampliada.
- Atualização assinada: APK original **0.15.0**, fixado por SHA-256, mesmo certificado, instalação por cima e comparação dos registros. A semente inclui uma correção administrativa já confirmada na release anterior.

## Resultados executados

Execução aprovada: [39678619659](https://github.com/italocesarfr-sketch/CHATGPTCODEX/actions/runs/39678619659), commit do aplicativo [`dbfde9848a912950336c1b0d937ca87f47953b50`](https://github.com/italocesarfr-sketch/CHATGPTCODEX/commit/dbfde9848a912950336c1b0d937ca87f47953b50). Os dois jobs terminaram com sucesso.

| Verificação | Resultado |
| --- | --- |
| JVM local e CI | 129 testes aprovados, zero falhas |
| Android direcionado | 23 testes aprovados, zero falhas |
| Regressão Android completa | 230 casos XML: 226 aprovados e quatro sondas condicionais fora dessa invocação; zero falhas |
| Layout existente | Uma verificação inicial aprovada |
| Frequência e reinício | Escrita e leitura em processos separados aprovadas |
| Atualização legada | Sondas antes/depois aprovadas |
| Exportações existentes | XLSX/CSV/TSV e planos conferidos pelos leitores independentes existentes, sem mudar os geradores |
| Release original 0.15.0 → 0.15.1 | Seis fases aprovadas: antes, depois, restauração, reabertura, exclusão com histórico e nova reabertura |
| Pacote e assinatura | `br.com.diariodeclasse`, certificado idêntico e código aumentado de 25 para 26 |
| Banco e backup | Room v8 e backup 9, sem alteração de esquema ou formato |

As quatro sondas condicionais são `FrequencyProcessProbeTest`, `UpgradeProbeTest`, `StableUpgradeProbeTest` e `ReleaseContinuityProbeTest`. Frequência, atualização legada e assinatura permanente tiveram invocações próprias aprovadas. A última pertence ao workflow legado de continuidade e não foi invocada; não foi contada como aprovada.

A primeira execução, [39677519881](https://github.com/italocesarfr-sketch/CHATGPTCODEX/actions/runs/39677519881), encontrou somente uma falha de automação: a captura tentava escolher um único diálogo quando havia formulário e confirmação sobrepostos. A captura passou a identificar a confirmação pelo título. Nenhum teste de integridade foi removido ou enfraquecido. A segunda execução concluiu os testes direcionados e a regressão completa.

O teste de atualização usa o APK original da 0.15.0, fixado por SHA-256, e semeia nessa versão uma correção externa com retirada explícita de FJ. A comparação integral do backup após instalar a candidata comprova também a preservação dessa auditoria. Os testes usam exclusivamente dados fictícios em emuladores descartáveis; nenhum banco escolar real foi acessado.

## APK e evidências

[Baixar release assinado](https://github.com/italocesarfr-sketch/CHATGPTCODEX/actions/runs/39678619659/artifacts/12383402953). O ZIP contém `app-release.apk`, identidade, SHA-256 e comparação de atualização. O nome histórico `noctua-etapa6-release` foi preservado no workflow; o manifesto do APK confirma **0.15.1 / 26**.

O APK foi baixado e conferido localmente com `apksigner`, `aapt` e `check-apk-update.py`, comparando com a release original 0.15.0. A assinatura v2 é válida para o mínimo Android API 26 do aplicativo. O arquivo conferido encontra-se em `build/noctua-0151-final/release/app-release.apk`.

- SHA-256 do APK: `9629a2bc5313dbb5acd357d1281ab79e130978b24688923e795aa556e931dcd7`.
- Certificado público SHA-256: `6dbbba2d930eb7f7fbeff1ee82512f656251bbec9f4b31b6c5614528d29f1aae` — igual ao da 0.15.0.
- [Contagens, hashes e fases executadas](REFINAMENTO_REMANEJAMENTOS_0151_RESULTADOS.json).
- [Prévia compacta](images/correcao-0151/previa.png).
- [Prévia com fonte ampliada configurada no teste](images/correcao-0151/previa-fonte-ampliada.png).
- [Confirmação final única](images/correcao-0151/confirmacao.png).

## Arquivos alterados

Arquivos de produção em `app/src/main/java/br/com/diariodeclasse`:

- `ui/RelocationCorrectionForm.kt`: edição/revisão, atualização automática, retorno, destinos, rótulos acessíveis e confirmação com texto rolável.
- `data/RelocationCorrection.kt`: seleção de destinos protegida, identificação dos bloqueios por horário para evitar repetir textos e conflito de concorrência específico. As mesmas regras acadêmicas geram os bloqueios.
- `data/DiaryRepository.kt`: leitura de destino tipado por ID e identificação do conflito; transação de confirmação e preservação existentes reutilizadas.

Testes em `app/src/test` e `app/src/androidTest`:

- `CorrectionRefinementTest.kt`: quatro novos testes JVM.
- `FunctionalFifteenInstrumentedTest.kt`: dois cenários Room adicionais, mantendo os anteriores de falha transacional, FJ, notas, histórico e backup.
- `FunctionalFifteenUiInstrumentedTest.kt`: adaptação ao fluxo sem segunda conferência e cinco cenários adicionais, incluindo Voltar Android e alteração concorrente na revisão e após retornar à edição.
- `StableUpgradeProbeTest.kt`: semente com auditoria na release anterior e expectativas relacionais preservadas.

Compilação e entrega:

- `app/build.gradle.kts`: 0.15.1 / 26.
- `.github/workflows/android.yml` e `docs/baseline/noctua-release-0.15.0.json`: referência real 0.15.0 fixada por hash; configuração de assinatura existente preservada.
- `scripts/ci/validate-android.sh`: 23 testes direcionados antes da regressão completa, com limite de duração ajustado ao acréscimo de cenários.
- Este documento, resultados JSON, capturas, README, especificação e roadmap: contrato e evidências da entrega.

[Comparação de código, testes e configuração](https://github.com/italocesarfr-sketch/CHATGPTCODEX/compare/aab4f2fd305dcc616fe31b4d2ad87f649b7ffca5...dbfde9848a912950336c1b0d937ca87f47953b50).

## Limitações

Conversão externa → interna e correção unilateral de vínculo interno permanecem bloqueadas de forma explícita. Não foram apresentadas como funcionalidades suportadas. A inspeção final no aparelho físico do proprietário e em outras dimensões/escalas de fonte permanece manual; o CI executou em Android API 30, incluindo a tela compacta e fonte ampliada configurada nos testes.

As seleções de retirada são intenções da sessão: permanecem ao voltar da revisão para edição; após recriar o formulário precisam de nova seleção. Se qualquer dado mudar enquanto o professor revisa, as seleções são desfeitas e o aviso pede nova conferência. Nenhuma intenção de retirada é aplicada automaticamente após perder sua referência de validação.
