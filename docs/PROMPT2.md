# Noctua — melhorias de funcionamento (Prompt 2)

Base inspecionada: `e10c0a7`, Noctua **0.11.4 / 20**, Git limpo. Incremento **0.12.0 / 21**, restrito às quatro melhorias autorizadas. Os dados reais permanecem exclusivamente no aparelho do professor; todos os testes usam fixtures fictícias.

## Conferência de importação

**Selecionar todos** é um checkbox com três estados: nenhum, todos e seleção parcial. Seleciona ou desmarca somente os IDs dos rascunhos apresentados na conferência da turma. A sessão existente consolida os lotes/páginas ainda pendentes daquela turma; rascunhos de outra turma e linhas acrescentadas depois do clique não são incluídos na operação capturada.

A atualização altera exclusivamente `reviewed`, em transação, com comandos limitados a 900 IDs para compatibilidade com SQLite do Android 8. Não regrava nomes/números, não autoriza homônimos e não cria matrículas. A conferência individual permanece disponível. **Confirmar cadastro** e sua confirmação definitiva continuam separados; nomes, números e duplicidades continuam validados pela interface e pelo Repository.

## Chamada em XLSX

Acesso: **Relatórios → Exportar planilha → Exportar chamada (XLSX)**. A turma/ano vêm do seletor existente; o professor informa início e fim, dentro do ano letivo, confere a prévia e pode salvar pelo seletor de documentos Android ou abrir o compartilhamento do Android.

Usa `SchoolReports.attendanceHistory`, `ExportTable`, `SpreadsheetWriter` e `ExportFiles`; não calcula frequência por outra fórmula nem altera registros. Cada **horário realizado e elegível** dentro do intervalo tem uma coluna, em ordem de data, horário e IDs de origem. Um dia com dois horários tem duas colunas: P/F não pode ser reduzido silenciosamente a um único estado. Não gera datas semanais ou colunas para dias sem aula; aulas canceladas/previstas ficam fora. Horários realizados ainda sem chamada podem aparecer, com células vazias.

Números são células numéricas reais; nomes e P/F/FJ são texto. NR, ausência de lançamento e registros fora da matrícula são células vazias. Há identificação de turma, disciplina, ano e intervalo. O histórico remanejado acompanha a matrícula do destino, mantendo a identificação da turma original nas colunas. Origens excluídas continuam acessíveis pelas mesmas projeções históricas.

Esta chamada usa **a ordem oficial**, conforme solicitado, sem alterar a ordem armazenada. Respeita a ocultação manual/automática existente na exportação; não transporta a ordem personalizada ou linhas vazias destinadas às planilhas de notas. As exportações anteriores continuam usando integralmente sua organização personalizada.

Salvar reutiliza SAF/CreateDocument e só declara sucesso após gravar, flush e close; cancelamento não grava nem declara sucesso. Compartilhar usa o FileProvider já disponível em AndroidX Core, sem nova dependência, exportado=false e com concessão temporária de leitura para a única planilha. Somente `cache/exports-share/` é exposto; banco e backups não são mapeados. Arquivos de compartilhamento com mais de 24 horas são limpos ao preparar outro; abrir o seletor de compartilhamento não é anunciado como envio concluído.

## Busca global em Relatórios

O campo **Buscar aluno em todas as turmas** filtra durante a digitação, ignorando maiúsculas, acentos, espaços e pontuação pela normalização existente de nomes. Pesquisa em todas as turmas/anos cadastrados, inclusive arquivadas, mesmo quando o ano selecionado não tem turma ativa. Não pesquisa um servidor e não modifica a seleção global apenas para ler resultados.

Os resultados permanecem na própria página, agrupados por turma, disciplina e ano. Reutilizam o mesmo componente de tabela e `SchoolReports`, com atividades próprias de cada turma, notas zero/pendentes/não aplicáveis, original, recomposição, vigente e frequência. Relatório e Ordenação permanecem disponíveis; agregações do grupo filtrado reutilizam `SchoolRules`. Não há média entre turmas ou anos. Cada matrícula tem seu ID e coluna de origem; homônimos não são fundidos pelo nome. Remanejados da origem ficam ocultos por padrão, conforme a regra existente; a opção de histórico os torna consultáveis explicitamente. O destino mostra o histórico consolidado, inclusive após excluir origens.

Não é necessário tocar para consultar notas. O toque opcional continua abrindo o perfil existente, no contexto correto da turma/ano, inclusive a consulta de arquivadas. **Limpar** restaura o relatório normal da turma selecionada ou a orientação de selecionar/pesquisar quando não há turma. Emissões de notas/chamadas atualizam as projeções; pequenas atualizações não reiniciam a rolagem. Apenas mudança explícita de pesquisa, turma, tipo ou período reposiciona a leitura.

## Segundo toque na chamada

P → P, F → F e FJ → FJ passam a salvar **NR**; trocar de opção salva a nova marcação. O item explícito NR continua limpando. A decisão é resolvida lendo a marcação atual **na transação Room**, pela fila de comandos existente, para que dois toques rápidos não dependam de um Flow ainda desatualizado.

NR já faz parte do esquema e do backup. Mantém os mesmos IDs de aula/matrícula e não equivale a P/F/FJ; apenas deixa de ser um registro válido no cálculo existente. Contagens, percentuais e indicadores reagem ao Flow. Continuam válidas as proteções de matrícula, remanejamento e estado da aula. Não há exclusão de outros horários, datas ou alunos.

## Preservação e arquivos

**Room v7, 14 tabelas, banco `diario.db`, backup formato 8, pacote `br.com.diariodeclasse` e assinatura permanente mantidos.** Nenhuma migração, limpeza, recriação, alteração de entidade ou dependência. `Database.kt` recebe somente uma consulta de atualização de rascunhos; os JSONs de esquema devem continuar idênticos. Backup/restauração, exclusão segura de turmas e regras de notas, recomposição e remanejamento não são substituídos.

Arquivos de implementação modificados: `data/Database.kt`, `data/DiaryRepository.kt`, `ui/ImportReviewScreen.kt`, `ui/DayAttendanceScreen.kt`, `ui/ReportsScreen.kt`, `ui/DiaryApp.kt`, `ui/ExportScreen.kt`. Criados: `reports/GlobalStudentSearch.kt`, `export/AttendanceExportProjection.kt`, `export/SpreadsheetShare.kt`, `ui/AttendanceExportScreen.kt`, `res/xml/export_paths.xml`. O manifesto recebe somente o provider privado de planilhas. Caminhos Kotlin relativos a `app/src/main/java/br/com/diariodeclasse/`.

Testes novos: `PromptTwoProjectionTest`, `PromptTwoRoomInstrumentedTest`, `PromptTwoUiInstrumentedTest`. CI preserva a configuração de assinatura, aponta a referência para a release real 0.11.4, inclui a validação independente da chamada e coleta capturas fictícias. Arquivos de suporte: `app/build.gradle.kts`, `.github/workflows/android.yml`, `scripts/ci/validate-android.sh` e `docs/baseline/noctua-release-0.11.4.json`. Nenhum Secret ou chave é criado/substituído.

## Validação

107 testes JVM e compilação local debug/instrumentação aprovados. A chamada XLSX foi lida por openpyxl com uma matriz esperada independente: ordem oficial, número numérico, P/F/FJ, NR/vazio, matrícula parcial, transferência, ocultação e dois horários. Projeções com remanejamentos sucessivos continuam iguais após a exclusão de origens. O primeiro compile de testes identificou argumentos invertidos no construtor Java da fixture CSV; foram corrigidos antes da publicação de código.

Validação final do código `6ae325015e0b22b03aa84078c2ca3364f63deb7c`, executada em 19/10/2026:

- **107 testes JVM** aprovados, sem falhas/erros/skips.
- **181 testes Android aprovados**, sem falhas/erros, em emulador API 30. O XML geral tem 185 casos, dos quais quatro sondas são condicionais. Frequência/reinício (duas fases), atualização legada (duas fases) e atualização release permanente (seis fases) foram executadas separadamente. A sonda antiga de continuidade com chaves descartáveis foi substituída pelo ensaio com a chave permanente existente.
- **40 XLSX reais, 40 CSV e 120 TSV** lidos e conferidos por openpyxl 3.1.5/scripts independentes (30 regulares, seis com histórico, um da nova chamada e três gerados no Android). Conferidos tipos numéricos, zero/vazio, P/F/FJ, dimensões, Unicode, ordem, ocultação e linhas vazias aplicáveis. Não há abertura manual no Microsoft Excel.
- Fluxos de seleção coletiva/parcial/individual, busca em anos diferentes/sem turma selecionada, limpeza de P/F/FJ, recriação da tela, totais, seletor de arquivos cancelado e compartilhamento aprovados. Os testes Room verificaram rollback, duplicidades, independência de registros, reinício e backup/restauração de NR e conferência.
- Atualização **release 0.11.4 / 20 → 0.12.0 / 21** instalada com `adb install -r` em emulador novo, com o mesmo certificado. A referência original foi validada pelo hash fixado no repositório. As seis fases passaram: preparar dados, atualizar/conferir, restaurar backup, reabrir, excluir origens preservando histórico e reabrir o histórico. Nenhum aparelho real foi apagado ou usado nesse ensaio.
- Esquemas Room, backup, entidades e workflow manual de release preservados; CI confirmou esquema idêntico e bloqueio de release sem assinatura configurada. Nenhuma chave/Secret foi criada ou substituída. A marca PNG original também passou na auditoria.

[GitHub Actions aprovado](https://github.com/italocesarfr-sketch/CHATGPTCODEX/actions/runs/39343066823) · [APK release assinado](https://github.com/italocesarfr-sketch/CHATGPTCODEX/actions/runs/39343066823/artifacts/12231005533) · [Testes/capturas](https://github.com/italocesarfr-sketch/CHATGPTCODEX/actions/runs/39343066823/artifacts/12229953385) · [Atualização assinada](https://github.com/italocesarfr-sketch/CHATGPTCODEX/actions/runs/39343066823/artifacts/12230421514)

Extraia `app-release.apk` do ZIP da release; o artefato debug não é o instalador de atualização. O APK release foi baixado novamente e sua assinatura, pacote, versão e hash foram conferidos localmente, além dos checks do CI:

- APK SHA-256: `e6335cf7f4d5e508fd349665aaa7482b2871605c71563d1bca0b45304803307c`.
- Certificado público SHA-256: `6dbbba2d930eb7f7fbeff1ee82512f656251bbec9f4b31b6c5614528d29f1aae`.
- Arquivo: 8.486.091 bytes; artefato retido pelo GitHub por 30 dias.

Ainda depende de validação do proprietário o uso no aparelho físico e a abertura no Microsoft Excel. A compatibilidade de atualização foi comprovada para a release 0.11.4 identificada; uma instalação com outro certificado não é coberta por esse resultado. Os limites XLSX do escritor existente continuam valendo (16.384 colunas e 1.048.576 linhas); extrapolações produzem erro, sem modificar registros.

### Ajustes identificados na primeira instrumentação

A primeira execução encontrou sete falhas de interface/automação. Um cenário antigo tocava P depois de marcar todos como presentes; com o comportamento solicitado, isso agora limpa a célula. O cenário continua comprovando P/F em aulas duplas, verificando o P já selecionado, e o novo teste cobre explicitamente o segundo toque. Os demais ajustes tornam os seletores específicos para checkbox individual/menu aberto, aguardam a projeção do ViewModel e rolam até os controles antes do toque. Não foram removidas verificações de notas, backup ou integridade.

O controle coletivo passou a aparecer logo após o título da conferência. A orientação após limpar a pesquisa sem turma selecionada fica no cabeçalho visível, sem filtros de relatório sem contexto. Na sincronização da publicação foi restaurada a permissão executável do script Android. Esses ajustes não alteram entidades nem regras de cálculo.

Testes existentes adaptados: `FrequencyUiInstrumentedTest.kt`, `ReportsUiInstrumentedTest.kt` e `StageTwoUiInstrumentedTest.kt`, em `app/src/androidTest/java/br/com/diariodeclasse/`.


### Capturas verificadas (somente dados fictícios)

| Fluxo | Captura |
|---|---|
| Última semana visível e dia 28 tocável | [Calendário](capturas/prompt2/00-calendario-final-do-mes.png) |
| Seleção coletiva em estado parcial | [Conferência](capturas/prompt2/01-conferencia-parcial.png) |
| Notas e identificação de turma/ano na busca | [Busca global](capturas/prompt2/02-busca-global.png) |
| P/F desmarcados, NR persistido e total atualizado | [Chamada](capturas/prompt2/03-chamada-sem-marcacao.png) |
| Período, salvamento e compartilhamento XLSX | [Exportação](capturas/prompt2/04-exportar-chamada.png) |

O último timeout era causado pelo alvo de teste na última semana: trazer o item lazy do grid à vista não assegurava que o dia 28 estivesse visível verticalmente. O teste passou a deslocar o calendário até a legenda, verificar os limites da célula na área tocável e só então executar o toque real. A captura e a execução final confirmaram a abertura, sem mudar o componente do calendário.

### Relação completa de arquivos de implementação

- `.github/workflows/android.yml`
- `app/build.gradle.kts`
- `app/src/androidTest/java/br/com/diariodeclasse/FrequencyUiInstrumentedTest.kt`
- `app/src/androidTest/java/br/com/diariodeclasse/PromptTwoRoomInstrumentedTest.kt`
- `app/src/androidTest/java/br/com/diariodeclasse/PromptTwoUiInstrumentedTest.kt`
- `app/src/androidTest/java/br/com/diariodeclasse/ReportsUiInstrumentedTest.kt`
- `app/src/androidTest/java/br/com/diariodeclasse/StageTwoUiInstrumentedTest.kt`
- `app/src/main/AndroidManifest.xml`
- `app/src/main/java/br/com/diariodeclasse/data/Database.kt`
- `app/src/main/java/br/com/diariodeclasse/data/DiaryRepository.kt`
- `app/src/main/java/br/com/diariodeclasse/export/AttendanceExportProjection.kt`
- `app/src/main/java/br/com/diariodeclasse/export/SpreadsheetShare.kt`
- `app/src/main/java/br/com/diariodeclasse/reports/GlobalStudentSearch.kt`
- `app/src/main/java/br/com/diariodeclasse/ui/AttendanceExportScreen.kt`
- `app/src/main/java/br/com/diariodeclasse/ui/DayAttendanceScreen.kt`
- `app/src/main/java/br/com/diariodeclasse/ui/DiaryApp.kt`
- `app/src/main/java/br/com/diariodeclasse/ui/ExportScreen.kt`
- `app/src/main/java/br/com/diariodeclasse/ui/ImportReviewScreen.kt`
- `app/src/main/java/br/com/diariodeclasse/ui/ReportsScreen.kt`
- `app/src/main/res/xml/export_paths.xml`
- `app/src/test/java/br/com/diariodeclasse/PromptTwoProjectionTest.kt`
- `docs/ESPECIFICACAO.md`
- `docs/PROMPT2.md`
- `docs/ROADMAP.md`
- `docs/baseline/noctua-release-0.11.4.json`
- `scripts/ci/validate-android.sh`

Evidências adicionais: este documento, `docs/PROMPT2_RESULTADOS.json` e as cinco capturas acima. O commit final de documentação não altera os binários validados.
