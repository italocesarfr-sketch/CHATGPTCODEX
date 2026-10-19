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

Testes Android e atualização assinada no CI ainda precisam ser conferidos; esta seção será completada com os resultados efetivos. Não há alegação de novo teste físico nem de abertura manual no Microsoft Excel. Os limites XLSX do escritor existente continuam valendo (16.384 colunas e 1.048.576 linhas); extrapolações produzem erro, sem modificar registros.
