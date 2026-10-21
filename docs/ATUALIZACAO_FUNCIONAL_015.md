# Noctua 0.15.0

Esta atualização permite corrigir remanejamentos externos confirmados, conferir o que impede limpar horários e exportar registros de aula em XLSX. Mantém o pacote `br.com.diariodeclasse`, o banco `diario.db` e o processo de assinatura permanente. Utiliza somente dados fictícios nos testes.

## Correção de remanejamento externo

Em Gerenciar alunos, habilite o histórico, edite a matrícula remanejada e toque em **Corrigir remanejamento**. O formulário mostra a data e o destino registrados. Informe a data efetiva confirmada pelo professor; o aplicativo não a presume.

**Conferir correção** apresenta a mudança de data e destino e os lançamentos encontrados na data proposta ou depois. P, F, FJ e NR persistidos aparecem por data e horário. Notas, inclusive zero ou pendentes, e recomposições aparecem com seus valores. A ausência de uma linha de frequência não é apresentada como NR persistido. Ao adiar a transferência, a prévia também mostra os lançamentos que voltam a pertencer ao período corrigido, sem oferecer sua retirada.

Uma marcação indevida só é retirada quando o professor a seleciona, confere novamente a prévia e confirma a correção administrativa. A retirada exclui exatamente aquela linha de frequência, sem criar presença, falta ou NR. A matrícula continua Remanejada, com os mesmos IDs e observações. Notas e recomposições nunca são excluídas por esse fluxo: se houver lançamentos na data proposta ou posteriores, a correção fica bloqueada com indicação do motivo.

Remanejamentos internos permanecem protegidos: a interface informa que as duas matrículas precisariam ser ajustadas em conjunto. Esta versão não permite correção unilateral desses vínculos.

## Diagnóstico e limpeza de horários

Em Chamada, abra **Horários → Limpar horários do dia**. O diagnóstico enumera somente vínculos encontrados: marcações por horário e tipo, observações, diário, atividades, recomposições e referências históricas preservadas.

Horários sem vínculos podem ser removidos após confirmação. Se existirem somente NR, a ação **Remover horários e registros NR** retira essas linhas e os horários da turma e data conferidas. A data volta a não ter indicadores. P, F, FJ e outros vínculos relevantes impedem a limpeza; cancelar e reativar horários continua disponível.

A confirmação revalida o snapshot. Alterações posteriores à conferência invalidam a operação. A remoção dos NR e horários ocorre na mesma transação; qualquer falha desfaz todas as mudanças.

## Planos de aula em Excel

Acesse **Relatórios → Exportar → Outras planilhas → Exportar planos de aula (XLSX)**. A turma e o ano continuam sendo os selecionados no aplicativo. Escolha um bimestre cadastrado ou um intervalo de datas e confira a prévia antes de salvar ou compartilhar.

O arquivo contém uma folha com Data, Título e Descrição. As datas são células numéricas com formato `dd/mm/yyyy`. Os textos são literais, editáveis e preservam acentos, caracteres especiais e quebras de linha. A ordem é data seguida de ID persistente, mantendo títulos repetidos e vários registros no mesmo dia. Não inclui estudantes, frequência ou notas e não depende de chamadas.

A geração reutiliza `SpreadsheetWriter`, `ExportFiles`, o seletor de documentos e `SpreadsheetShare`. Apenas o novo modelo recebe larguras próprias, cabeçalhos congelados e quebra automática de texto. Os modelos anteriores mantêm suas projeções e formatos. Sem planos elegíveis, a interface informa o estado vazio e desabilita salvar e compartilhar.

O limite do Excel de 32.767 caracteres por célula permanece aplicado: o aplicativo recusa a geração em vez de truncar o texto. A altura de linha do Excel é limitada; textos muito extensos permanecem integralmente na célula e podem exigir ajuste de visualização no Excel.

## Persistência e atualização

Room passa de v7 para v8 com uma única operação aditiva: `ALTER TABLE relocations ADD COLUMN corrections TEXT NOT NULL DEFAULT '[]'`. Não há recriação de tabelas nem migração destrutiva.

A coluna guarda uma lista serializada de correções administrativas: data e destino anteriores e novos, instante da confirmação, identificador da correção e cópias de auditoria das marcações explicitamente retiradas e seus horários. Essas cópias nunca entram nas projeções acadêmicas. As observações do professor não são utilizadas como campo técnico.

Como a trilha pertence ao próprio vínculo, acompanha os históricos já preservados na exclusão de turmas, sem duplicar notas ou frequência. Vínculo e matrícula são corrigidos na mesma transação, com revalidação antes da escrita e validação de integridade antes do commit.

O backup passa ao formato 9, com leitura dos formatos anteriores 1 a 8. A nova coluna tem valor vazio ao migrar dados antigos. O certificado e os Secrets não são modificados. A compatibilidade de instalação foi conferida no CI sobre o APK original da 0.14.0, com dados fictícios. O APK entregue também teve pacote, versão e certificado conferidos localmente.

## Interface e skills

O caminho informado de Impeccable (`/run/codex-environment/codex-home/skills/impeccable/SKILL.md`) não existe neste ambiente. Foi aplicado o fallback autorizado pelo proprietário: componentes nativos Material 3 e Jetpack Compose, formulários com rolagem, confirmação antes de efeitos e mensagens específicas. A identidade visual existente foi mantida.

Foram consultadas as skills locais `spreadsheets` e `documents`, seus arquivos `SKILL.md` e referências pertinentes. A orientação de planilhas foi aplicada à tipagem de datas, colunas, quebra de texto e validação independente da saída nativa. Os dois XLSX fictícios foram importados com `artifact-tool`, tiveram seus textos conferidos e foram renderizados para inspeção visual, sem regravar os arquivos do gerador Kotlin. A leitura com openpyxl verifica valores, tipos, formatação, dimensões e conteúdo exato. A orientação documental foi aplicada à revisão de clareza deste Markdown, sem criar DOCX ou PDF.

## Prévia da planilha

[Planos fictícios renderizados para conferência](images/funcional-015/planilha-planos.png). Os textos extensos continuam integrais nas células, sujeitos ao limite visual de altura do Excel descrito acima.

## Validação

A validação principal do CI foi aprovada na execução [39629368679](https://github.com/italocesarfr-sketch/CHATGPTCODEX/actions/runs/39629368679), commit do aplicativo `84025a31094af2c7d7da480d67aaddb9792ede3c`.

| Verificação | Resultado executado |
| --- | --- |
| JVM | 125 testes, zero falhas |
| Android completo | 223 casos no XML, 219 aprovados e quatro sondas condicionais não executadas nessa invocação |
| Fluxos direcionados | 16 testes aprovados antes da regressão completa |
| Componente de interface existente | Uma verificação inicial aprovada |
| Atualização legada | Sondas antes/depois aprovadas, esquema v1 até v8 |
| Frequência após reinício de processo | Escrita e leitura aprovadas em processos separados |
| Planilhas de registros de aula | Dois XLSX validados com openpyxl e importados/renderizados com artifact-tool |
| Exportações anteriores | 38 XLSX, 38 CSV e 114 TSV JVM, mais três XLSX e formatos correspondentes produzidos no Android, validados com leitor independente |

As quatro sondas condicionais são `FrequencyProcessProbeTest`, `UpgradeProbeTest`, `StableUpgradeProbeTest` e `ReleaseContinuityProbeTest`. As duas primeiras têm invocações específicas nesta execução. A sonda de assinatura permanente tem job próprio; a última pertence ao fluxo de release legado e não foi invocada nesta rodada. Nenhuma foi somada aos 219 testes aprovados.

A atualização assinada **0.14.0 / 24 → 0.15.0 / 25** foi aprovada nas seis fases: antes da atualização, depois, restauração, reabertura do processo, exclusão de origem/intermediária com histórico e nova reabertura. O pacote e o certificado são os mesmos da referência. O APK foi baixado e validado localmente com `apksigner` e `check-apk-update.py`.

[Baixar APK release assinado](https://github.com/italocesarfr-sketch/CHATGPTCODEX/actions/runs/39629368679/artifacts/12359971428). O ZIP contém `app-release.apk`, identidade, SHA-256 e comparação de atualização. O nome histórico do artefato no workflow permanece `noctua-etapa6-release`; o manifesto confirma **0.15.0 / código 25**.

SHA-256 do APK: `52c7b47e148c06b69c1a45e06fbac00b576ed958109165f85c580729a04e76b3`.

Certificado público SHA-256: `6dbbba2d930eb7f7fbeff1ee82512f656251bbec9f4b31b6c5614528d29f1aae`.

[Evidências e contagens](ATUALIZACAO_FUNCIONAL_015_RESULTADOS.json). O arquivo local conferido nesta execução está em `build/noctua-015-final/release/app-release.apk`.

As rodadas anteriores identificaram fixtures de esquemas antigos tentando inserir a coluna nova, rolagem recursiva instável em testes Compose e uma falha de visibilidade na conferência. As fixtures agora contêm apenas colunas da versão de origem, com comparação integral após migração. A automação usa chaves persistentes no histórico. O formulário de correção separa edição e conferência, e o teste exige a FJ visível antes de confirmar. A execução final não tem falhas.

A validação foi feita em emulador API 30, incluindo a fonte ampliada no diagnóstico. A conferência no aparelho físico do proprietário e no Microsoft Excel permanece manual. Não foi acessado nenhum banco escolar real.

## Arquivos principais

- `data/RelocationCorrection.kt`, `RelocationRules.kt`, `DiaryRepository.kt` e `Database.kt`: prévia, auditoria e transação da correção administrativa.
- `data/EmptyDayHours.kt` e `ui/DayAttendanceScreen.kt`: diagnóstico por vínculo e confirmação de limpeza de NR.
- `data/DiaryMigrations.kt`, esquema `8.json`, `backup/BackupCodec.kt` e `BackupManager.kt`: migração aditiva e backup 9 lendo anteriores.
- `export/LessonPlanExportProjection.kt`, `ExportProjection.kt` e `SpreadsheetWriter.kt`: projeção independente, datas tipadas e apresentação do novo XLSX.
- `ui/RelocationCorrectionForm.kt`, `Forms.kt`, `AttendanceExportScreen.kt`, `ExportScreen.kt` e `DiaryApp.kt`: acesso, prévia e navegação reutilizados.
- `FunctionalFifteenTest.kt`, `FunctionalFifteenInstrumentedTest.kt` e `FunctionalFifteenUiInstrumentedTest.kt`: cenários novos. Testes legados foram adaptados ao esquema aditivo e à rolagem por chaves, mantendo as asserções de integridade.
- `app/build.gradle.kts`, `.github/workflows/android.yml`, `scripts/ci/validate-android.sh`, `scripts/validate-lesson-plans.py` e a referência pública `docs/baseline/noctua-release-0.14.0.json`: versão, execução e validação. Nenhum Secret foi alterado.

Os arquivos de produção acima ficam em `app/src/main/java/br/com/diariodeclasse`; os testes ficam em `app/src/test` e `app/src/androidTest`. A comparação dos commits identifica também os ajustes de expectativa de versão nos testes antigos.

## Capturas da interface

- [Conferência do remanejamento com FJ visível](images/funcional-015/previa-remanejamento.png).
- [Confirmação de remoção de NR em fonte ampliada](images/funcional-015/diagnostico-nr.png).
- [Exportação independente dos planos](images/funcional-015/planos-xlsx.png).

[Comparação completa dos arquivos de código e testes](https://github.com/italocesarfr-sketch/CHATGPTCODEX/compare/8e1bdde9d6880aec58bfe7a6e83cc235ed61a769...84025a31094af2c7d7da480d67aaddb9792ede3c).
