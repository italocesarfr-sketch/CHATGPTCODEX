# NOcTuA Professor 0.17.1 — alcance dos horários e período da exportação

Versão validada: **0.17.1 / código 30**, sobre a **0.17.0 original / 29**. Compilação, regressão Android, escalas reais de fonte e atualização com a assinatura permanente aprovadas em 23/10/2026.

## Horários

O primeiro cadastro de cada ano começa automaticamente em 1º de janeiro. O formulário não solicita data ou alcance e Salvar grava diretamente, sem criar aulas ou conteúdos. Os períodos continuam ligados aos IDs de turmas daquele ano; não são transportados para anos seguintes.

Nas edições, os dois alcances são **A partir de hoje** e **Para o ano todo**. A data é mostrada antes de salvar. Hoje só é aceito no ano atual; nos demais anos, é necessário escolher a opção anual. Novos períodos da semana usam inicialmente o alcance anual; editar períodos existentes no ano atual sugere Hoje. Hoje salva diretamente, sem segunda etapa administrativa. A conferência anual mostra o intervalo, as datas com registros cuja previsão muda e, no alcance anual, quantas vigências posteriores serão substituídas. Não altera os lançamentos efetivos.

### Revisões imutáveis, sem mudança de esquema

A consulta continua usando a maior data efetiva e, nela, a maior revisão. Uma substituição anual insere uma nova revisão em 1º de janeiro **e em cada fronteira de vigência já existente naquele ano**, todas contendo o mesmo retrato semanal revisado. Assim, nem uma revisão futura antiga pode voltar a prevalecer silenciosamente. Não há UPDATE nem DELETE das revisões anteriores: elas seguem consultáveis em Vigências e incluídas no backup.

A edição parte do retrato semanal consultado, preservando os demais períodos desse retrato. A prévia anual informa que ele substituirá também previsões posteriores; não tenta mesclar duas rotinas potencialmente conflitantes. Edições posteriores ainda podem estabelecer novas vigências normalmente. Uma grade antiga iniciada em outubro só se torna anual mediante ação explícita do professor.

A confirmação revalida o conjunto completo de revisões do ano, conflitos de períodos/turnos, turmas, datas acadêmicas afetadas e cada revisão proposta. Todas as inserções pertencem à mesma transação Room. Uma falha na segunda inserção também desfaz a primeira. Nenhuma chamada, frequência, nota ou descrição do Diário é gravada por essa operação.

## Exportação

Ajustes → Abrangência oferece **1º, 2º, 3º, 4º bimestre e Quatro bimestres**. Chamada e Planos também conservam Intervalo de datas. Resumo anual mantém seu período anual fixo, sem seletor sem efeito.

Cada entrada em Exportar herda o bimestre consultado em Relatórios. Alterações posteriores pertencem apenas à visita à exportação e ao modelo escolhido; rotação conserva esses ajustes. Voltar não modifica Relatórios. Reentrar inicia novamente pelo bimestre de Relatórios, mantendo preferências de conteúdo/formato compatíveis e zerando a abrangência da visita anterior.

O mesmo parâmetro efetivo alimenta resumo, nome do arquivo, prévia, XLSX, CSV, TSV e cópia, inclusive somente notas. Os exportadores e cálculos acadêmicos existentes são reutilizados. Quatro bimestres em Chamada/Planos usa o início mínimo e término máximo dos quatro períodos configurados; sem os quatro períodos, solicita configuração ou intervalo explícito. Não inventa datas acadêmicas. Quatro bimestres não troca o modelo por Resumo anual.

## Integridade e compatibilidade

- Mesmo applicationId `br.com.diariodeclasse` e banco `diario.db`.
- **Room v9 e backup formato 11 inalterados**; nenhuma migração nova ou transformação automática das grades antigas.
- Nenhuma alteração em notas, matrículas, remanejamentos, frequência, recomposição, diários, identidade visual, login demonstrativo ou certificados.
- O CI compara a assinatura e testa `adb install -r` sobre o APK original **0.17.0**, identificado por hash em `docs/baseline/noctua-release-0.17.0.json`. A semente inclui grade antiga de outubro e revisão futura, registros acadêmicos, auditoria de remanejamentos, preferências e backups.

## Testes acrescentados

- JVM: cadastro anual em outubro/dezembro; todas as fronteiras futuras; revisões sucessivas; Hoje/ano diferente; períodos acadêmicos afetados; conflitos; restauração; seleção efetiva de todos os bimestres/seis modelos; quatro períodos e intervalo; equivalência da matriz de saída e cópia.
- Room: preservação integral dos registros com P/F/FJ/NR e Diário; reinício e restauração de múltiplas revisões; falha após a primeira inserção com rollback; concorrência, novas datas e turma arquivada.
- UI: primeiro cadastro sem data ou alcance; dois alcances; substituição anual confirmada; bimestres diretos, prévia/cópia, rotação, retorno/reentrada e separação entre modelos; telas compactas com fontes 1,3 e 1,6.
- Leitor independente openpyxl/Python csv: nova matriz de 48 XLSX, 48 CSV e 144 TSV, além das massas anteriores. Datas Excel reais são conferidas como datas formatadas e seriais equivalentes; nenhum escritor é usado para reler o arquivo.

## Limitações

A validação física final permanece com o proprietário. Leitura independente de XLSX não substitui abrir os arquivos na interface do Microsoft Excel. Uma substituição anual aplica o retrato completo revisado e pode substituir previsões posteriores de outras turmas; a confirmação informa esse alcance. Os lançamentos reais permanecem intocados.

## Resultado da validação

- **162 testes JVM aprovados**, sem falhas.
- **269 casos Android: 265 aprovados, quatro sondas condicionais**, sem falhas. Os grupos focados (20 de horários, três de layout, 35 de regressão anterior e 24 de relatórios/exportação) fazem parte da regressão e não devem ser somados como casos únicos.
- Mais dois testes de diálogo executados em cada escala **real do Android 1,3 e 1,6**. O teste confere a densidade do próprio texto do diálogo, além de rolagem e controles de pelo menos 48 dp. Apenas simular LocalDensity no pai Compose não alterava a janela nativa.
- Leitores independentes: **89 conjuntos XLSX/CSV/TSV** (incluindo a nova matriz de 48), mais **dois XLSX de planos**. Tipos, zeros, vazios, ordem, Unicode, datas e matrizes de cópia conferidos.
- Sondas de frequência após reinício e atualização antiga com chave descartável executadas separadamente.
- **Seis fases da atualização com assinatura permanente aprovadas**: antes, depois de `adb install -r`, backup/restauração, reinício, exclusão com histórico preservado e novo reinício. A semente foi criada na **release 0.17.0 original**, incluindo grade iniciada em outubro e vigência futura; nenhuma grade antiga é modificada ao simplesmente atualizar.
- As quatro sondas condicionais da suíte geral são FrequencyProcessProbe, UpgradeProbe, StableUpgradeProbe e ReleaseContinuityProbe. As três primeiras têm execução específica; a última é uma sonda legada substituída pelo teste atual de atualização permanente.
- Certificado comparado ao fingerprint permanente, assinatura APK v2 verificada e hash do APK conferido novamente antes do upload. **Sem criação ou alteração de chaves/Secrets.**

A primeira rodada detectou Salvar com 40 dp. O componente passou a exigir **48 dp**, mantendo a asserção. A inspeção das capturas detectou depois que a escala simulada não chegava aos diálogos; acrescentaram-se testes com a escala real do sistema. A rodada final passou integralmente.

## Interface e arquivos alterados

O **Impeccable 4.7.0** disponível em `.agents/skills/impeccable/` foi lido e utilizado: contexto do alvo, Android, simplificação, craft e adaptação nativa. Foram preservados identidade visual, componentes Material 3 e Compose. Nenhuma tecnologia web foi introduzida.

Arquivos de produção, sob `app/src/main/java/br/com/diariodeclasse/`:

- `data/WeeklyTimetable.kt`: alcance e composição anual de revisões imutáveis.
- `data/DiaryRepository.kt`: revalidação e inserção atômica do lote.
- `ui/TimetableScreen.kt`: cadastro sem data, dois alcances, confirmação anual e alvos de toque.
- `export/ExportPresentation.kt`: período escolhido encaminhado às projeções existentes.
- `ui/ExportScreen.kt`: Abrangência, resumo, arquivo, prévia e cópia coerentes.
- `ui/DiaryApp.kt`: identidade da visita à exportação, preservando Relatórios e rotação.
- `ui/AttendanceExportScreen.kt`: compatibilidade com o componente reutilizado.
- `app/build.gradle.kts`: versão 0.17.1 / 30.

Testes: TimetablesTest, ExportPresentationTest, TimetableInstrumentedTest, TimetableUiInstrumentedTest, TimetableScopeLayoutInstrumentedTest, ExportLifecycleUiInstrumentedTest, FunctionalFifteenUiInstrumentedTest, PromptTwoUiInstrumentedTest e StableUpgradeProbeTest. Os testes anteriores preservam suas verificações; apenas acompanham os novos rótulos quando necessário.

Infraestrutura: `.github/workflows/android.yml`, `scripts/ci/validate-android.sh`, `scripts/validate-spreadsheets.py` e `docs/baseline/noctua-release-0.17.0.json`. Workflow manual de release, Room, migrações, codecs de backup e exportadores de arquivos permanecem intactos.

## Entrega e capturas reais

- [GitHub Actions aprovado](https://github.com/italocesarfr-sketch/CHATGPTCODEX/actions/runs/39992962752).
- [APK release assinado — baixar ZIP e instalar app-release.apk](https://github.com/italocesarfr-sketch/CHATGPTCODEX/actions/runs/39992962752/artifacts/12519885935).
- [Resultados e capturas do emulador](https://github.com/italocesarfr-sketch/CHATGPTCODEX/actions/runs/39992962752/artifacts/12519900080).
- [Evidências das seis fases da atualização assinada](https://github.com/italocesarfr-sketch/CHATGPTCODEX/actions/runs/39992962752/artifacts/12519562217).
- [Commit validado](https://github.com/italocesarfr-sketch/CHATGPTCODEX/commit/e632aae295556c0f66171c60fb792b9a30cbbd2f).
- [Última alteração no código do aplicativo](https://github.com/italocesarfr-sketch/CHATGPTCODEX/commit/5739d9266554e1ec539c4a26e9dda090cfae57ff). O commit posterior altera somente testes/CI.

No artefato de resultados, `build/weekly-telas.tar` contém os PNG reais em `files/weekly-screenshots/`:

| Captura | Conteúdo |
| --- | --- |
| `02-editar-grade.png` | Primeiro cadastro sem data nem alcance |
| `09-alcance-anual.png` | Duas opções de alcance |
| `10-confirmacao-anual.png` | Substituição das previsões futuras |
| `system-1.3-11-alcance-fonte-1.3.png` | Editor com fonte real Android 1,3 |
| `system-1.6-11-alcance-fonte-1.6.png` | Editor com fonte real Android 1,6 |
| `system-1.6-12-abrangencia-fonte-1.6.png` | Abrangência com fonte real Android 1,6 |

`build/etapa52-telas.tar` contém também `08-abrangencia-bimestres.png`. Os arquivos `build/weekly-system-font-1.3.txt` e `build/weekly-system-font-1.6.txt` registram a aprovação dos testes com fonte real.

O ambiente local ficou indisponível durante o download das evidências finais. O CI continuou e aprovou todas as etapas; a documentação foi finalizada pelo conector GitHub. As capturas permanecem no artefato, sem cópia adicional de PNG para este diretório. Cadastro, alcance e confirmação foram inspecionados visualmente antes dessa interrupção; a última revisão visual das imagens com escala real, assim como a validação física, permanece manual. Não foi feita uma nova conferência local do APK baixado após a perda do ambiente: assinatura, atualização e correspondência do artefato foram comprovadas no CI.

Os hashes registrados em [HORARIOS_EXPORTACAO_0171_RESULTADOS.json](HORARIOS_EXPORTACAO_0171_RESULTADOS.json) são dos **ZIPs dos artefatos**, não do APK interno. O ZIP release inclui o hash e a identidade do APK.
