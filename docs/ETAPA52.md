# Etapa 5.2 — Exportação inteligente e gerenciamento de turmas

Noctua **0.9.0 / versionCode 14**, base **0.8.0 / 13** aprovada em aparelho físico pelo proprietário. Mesmo pacote `br.com.diariodeclasse`, banco `diario.db`, funcionamento offline e assinatura permanente existente. Sem novas bibliotecas de planilha no APK. Esta autorização inclui XLSX, CSV, cópia, arquivamento e exclusão protegida, sem liberar outras etapas.

## Uso

Relatórios → **Exportar** abre uma tela secundária. O ano/turma seguem o seletor global; em **Opções de exportação**, recolhidas inicialmente, escolha bimestre/Resumo anual, modelo Notas/Frequência/Consolidado, XLSX/CSV, cabeçalhos e número da chamada. A prévia acompanha a mesma matriz tipada dos arquivos/cópias. Campos não lançados/não aplicáveis ficam vazios; zero é valor numérico. Situações e pendências têm colunas descritivas próprias.

- **Notas:** atividades com identificação da origem, máximo no cabeçalho e em linha numérica própria quando os cabeçalhos estão habilitados; original, recomposição, vigente, situação e pendências. Resumo anual apresenta os quatro bimestres separadamente.
- **Frequência:** aulas realizadas elegíveis, registros válidos, P/F/FJ, faltas efetivas, NR e percentual. Resumo anual inclui cada bimestre e o consolidado anual calculado pelas regras existentes.
- **Consolidado:** original/recomposição/vigente, situação/pendências e frequência de cada período. O anual não soma notas nem inventa média ou aprovação.

**Exportar arquivo** prepara uma fotografia consistente da prévia em arquivo privado temporário e abre o seletor Android. Nome sugerido identifica turma/disciplina, ano e período. Cancelamento não grava destino nem mostra sucesso. Erro de abertura, cópia, flush ou fechamento apresenta falha; sucesso só após conclusão. O temporário da operação é removido ao entregar/cancelar. As ações aguardam salvamentos pendentes; durante o seletor, o contexto fica protegido. Um temporário abandonado por encerramento forçado do processo fica no cache, sem garantia de retenção pelo Android; tente exportar novamente se o sistema o remover.

**Copiar dados** segue a opção de cabeçalhos/número. **Copiar somente notas** não leva nomes, situação, máximos ou cabeçalhos; por padrão copia apenas o **resultado vigente** (uma coluna por bimestre selecionado). A opção recolhida **Todas as notas** inclui atividades, original, recomposição e vigente, mantendo exatamente a ordem identificada em **Prévia somente notas**. Frequência não tem cópia somente notas. Linhas vazias são mantidas. A exportação/cópia é solicitada pelo professor, sem envio remoto nem leitura automática da área de transferência.

## Disposição e regras escolares

`ExportProjection` combina `ExportLayout.rows/project` (Etapa 5.1) e **SchoolReports**, sem recalcular notas ou frequência. IDs são das matrículas. Ocultação manual e exclusão automática da origem remanejada valem igualmente para os três formatos; o destino usa o histórico consolidado já existente, sem duplicar notas ou presenças. Linhas vazias explícitas mantêm posição. Nomes novos/renomeados e homônimos seguem o contrato 5.1. Arquivos não alteram as preferências nem os registros escolares.

Não há nova recuperação, fórmula aditiva ou média anual. NR, aulas canceladas, matrícula parcial, FJ e recomposição usam os resultados aprovados das projeções. O estado de lançamento é compartilhado com a tela de relatórios. Descrição de aplicação distingue pendências e atividades não aplicáveis, mesmo quando ambas têm célula numérica vazia.

## XLSX real, CSV e TSV

`SpreadsheetWriter` gera **OOXML SpreadsheetML** com `ZipOutputStream`: tipos de conteúdo, relacionamentos, workbook, worksheet e estilos. Não é CSV renomeado. Células numéricas usam `<v>` e tipo numérico; textos são inline strings com escape XML, sem fórmulas/macros. Não há proteção de planilha: valores são editáveis no Excel. São valores exportados, não fórmulas ligadas ao aplicativo; editar a planilha não reimporta notas no Noctua.

Pontuações preservam a precisão interna em números; a exibição XLSX usa duas casas. Contagens são inteiras. Frequência usa número de **0 a 100**, correspondente ao relatório, com símbolo `%` literal no formato (por exemplo, valor numérico 50 exibido 50,00%). Não é fração 0,5. O máximo de atividades é numérico na linha própria de cabeçalhos. Sem cabeçalhos, saem apenas as linhas dos alunos e espaços, sem títulos ou máximos.

CSV tem BOM UTF-8, `;`, vírgula decimal e CRLF; aspas duplicadas e aspas em campos com separador, aspas ou quebras. TSV usa tabulações e o mesmo escape para texto multilinha/tabulações. Valores numéricos não são arredondados silenciosamente no CSV/TSV. XLSX usa decimal interno OOXML com ponto, independente da localidade. A representação é lida por programa independente e comparada célula a célula à matriz esperada.

Limites do XLSX: 1.048.576 linhas, 16.384 colunas, 32.767 unidades de texto por célula; caracteres inválidos para XML são recusados em vez de removidos silenciosamente. O escritor comprime diretamente no stream e libera os recursos ZIP; mantém em memória a projeção da turma, sem carregar biblioteca de escritório. O APK não inclui openpyxl: ele é usado somente nos testes locais/CI.

## Arquivar, consultar e reativar

Cadastrar Turma → **Gerenciar turma** → **Arquivar turma** pede confirmação. O estado é um vínculo separado por ID em `class_archives`; nenhuma entidade escolar muda. Turmas arquivadas saem do seletor global ativo. **Turmas arquivadas** lista anos/disciplinas, permite consultar relatórios/perfis somente leitura, exportar e reativar. A consulta identifica seu contexto próprio e não mostra seletor global de outra turma. Reativar devolve a turma aos seletores sem recriar IDs. Históricos consolidados de outras turmas continuam incluindo matrículas da origem arquivada. Novos remanejamentos para destino arquivado exigem reativação prévia; vínculos já realizados são preservados.

## Exclusão permanente

Gerenciar turma → **Conferir exclusão da turma** é somente leitura: identifica turma/disciplina/ano, matrículas, horários, chamadas por horário, marcações, atividades, notas, recomposições, bimestres, rascunhos, disposição e vínculos. Recomenda backup completo, informa irreversibilidade e exige digitar a identificação exibida antes de habilitar **Excluir permanentemente**. Cancelar não altera nada.

A operação é bloqueada se qualquer matrícula da turma participa de remanejamento interno/externo, compartilha personId com outra turma ou possui referência escolar/de exportação entre turmas. Não há tentativa de cortar vínculos ou copiar histórico para viabilizar a exclusão: a mensagem orienta **arquivar**.

Repository revalida o fingerprint da base e os bloqueios dentro de uma única transação. Remove explicitamente apenas filhos exclusivos da turma, respeitando FKs, e depois a turma. Não usa cascata de classes nem recria o banco. Confere `foreign_key_check` e igualdade integral dos dados restantes com a projeção esperada. Qualquer falha provoca rollback, inclusive se as remoções de filhos já tiverem começado. Alterações depois da prévia exigem nova conferência. Nenhum dado de outras turmas deve ser removido.

## Room e backup

**Room v5**, migração aditiva **4→5**, cria somente `class_archives(classId PK/FK RESTRICT)`; as onze tabelas anteriores e os JSONs 1–4 permanecem idênticos. Nenhuma alteração de esquema foi necessária para exportar. **Backup formato 6 / schema 5** inclui `classArchives`; lê formatos 1–5 anteriores. Arquivo anterior não informa arquivamento e restaura todas as suas turmas como ativas, conservando os registros e as preferências de exportação que aquele formato possuía. A prévia mostra as contagens antes da confirmação literal; a cópia de segurança privada anterior continua obrigatória. Não há mescla automática ou downgrade do banco.

## Validação

Local: testes JVM, compilação debug e instrumentação Android. Leitor independente **openpyxl 3.1.5**, mais `csv` do Python, validam 30 XLSX, 30 CSV e 90 TSV gerados nos testes JVM. Conferem dimensões, tipos numéricos, zero/vazio, máximos, percentuais, ausência de fórmulas/proteção, acentos/aspas/multilinha, linhas vazias, ordem/visibilidade e correspondência com a projeção dos relatórios.

Novos testes Room exercitam arquivar/reabrir/reativar, histórico de origem arquivada, destino arquivado bloqueado, backup antigo/novo, exclusão confirmada/cancelada/bloqueada, referências entre turmas, falha transacional e migração 4→5. Testes de UI cobrem prévia/cópia, opções recolhidas, quatro bimestres, seletor Android cancelado, arquivamento/consulta/reativação e confirmação de exclusão. O CI também valida com openpyxl arquivos produzidos **pelo Android**.

O workflow mantém a assinatura existente e testa atualização do APK **real 0.8.0** do [run 39073693052](https://github.com/italocesarfr-sketch/CHATGPTCODEX/actions/runs/39073693052), com hash/certificado público fixados em `docs/baseline/noctua-release-0.8.0.json`. A semente agora inclui preferências 5.1, além das onze tabelas anteriores; as fases seguintes conferem migração, backup, arquivos, arquivamento e reinício. A regressão Transferida → Ativa → Remanejada permanece na suíte/sondas.

**Resultado final:** 83 testes JVM e 135 Android aprovados, sem falhas; quatro casos exclusivos ignorados na suíte comum. Três são executados pelas oito fases de sondas separadas; o ensaio histórico ReleaseContinuity permanece ignorado e é substituído pela atualização real 0.8.0 → 0.9.0, sem novas chaves. 28 cenários Java também aprovados.

O leitor independente validou **33 XLSX reais, 33 CSV e 99 TSV**: 30 conjuntos gerados pela JVM e três produzidos pelo Android. A suíte de regressão de notas/recálculo/recomposição/frequência/remanejamento permaneceu incluída. A atualização assinada fez instalação real da 0.8.0, semente fictícia com preferências 5.1, `adb install -r` da 0.9.0, migração 4→5, conferência integral, backup antigo/novo e reinício com arquivamento preservado. Todas as fases before/after/backup/reopened passaram. A sonda de frequência/reinício mantém Transferida → Ativa → Remanejada sem duplicação.

**Validação física da 0.9.0 permanece pendente**, assim como abertura/colagem manual no Microsoft Excel do proprietário.

### Artefatos e integridade

- [GitHub Actions aprovado](https://github.com/italocesarfr-sketch/CHATGPTCODEX/actions/runs/39131368000), commit de código `3b34563f202ebab7176892b0a0593f3ec7b3bc4f`.
- [Baixar APK release (ZIP)](https://github.com/italocesarfr-sketch/CHATGPTCODEX/actions/runs/39131368000/artifacts/12137479823), artefato **noctua-etapa52-release**. Extrair `app-release.apk`.
- [Resultados JVM, Android, Room, planilhas e capturas](https://github.com/italocesarfr-sketch/CHATGPTCODEX/actions/runs/39131368000/artifacts/12138481486); [provas da atualização assinada](https://github.com/italocesarfr-sketch/CHATGPTCODEX/actions/runs/39131368000/artifacts/12138700492).
- [APK debug interno](https://github.com/italocesarfr-sketch/CHATGPTCODEX/actions/runs/39131368000/artifacts/12138108458), sem promessa de atualização sobre a release permanente.
- [Metadados e hashes](ETAPA52_RESULTADOS.json).

APK release baixado e verificado em `app/build/outputs/apk/release/app-release.apk`; debug em `app/build/outputs/apk/debug/app-debug.apk`. Ambos existem, informam Noctua 0.9.0 / código 14, pacote preservado, mínimo API 26 e sem permissão INTERNET. SHA-256 release: `9638cc30aaf1cee9f7ee20a5aa484beb5af7e9015eaafa7bfd9cefaa7a0791e5`. Certificado público SHA-256: `6dbbba2d930eb7f7fbeff1ee82512f656251bbec9f4b31b6c5614528d29f1aae`, igual ao APK 0.8.0 fixado. Assinatura v2 e hashes dos quatro arquivos ZIP confrontados com o Actions. Os DEX contêm ExportProjection, SpreadsheetWriter e ClassLifecycle. Nenhum banco pessoal, chave ou certificado privado integra os artefatos.

Os onze esquemas anteriores permanecem idênticos no v5, e os JSONs v1–v4 foram conferidos byte a byte. Esquema v5 SHA-256 `ffe06a1148e6a10869a4db6eaf77e5e8be25f15b3b9ea122ef388222528c1ce8`, identidade `4fb9b076fb8a111f68f23ad0863c5f5c`. O workflow release manual está inalterado. A prova de atualização cobre a release 0.8.0 do repositório com esse certificado; não identifica APKs arbitrários instalados. Criar backup antes de atualizar; usar a release, sem desinstalar uma instalação compatível. Guardar o APK: os artefatos de APK do Actions expiram após 30 dias.

Primeiro ensaio Android: [run 39127648173](https://github.com/italocesarfr-sketch/CHATGPTCODEX/actions/runs/39127648173), 132 aprovados e três falhas nos testes novos. A montagem de identidade compartilhada passou a usar DAO apenas no banco fictício, pois a API de produção corretamente impede trocar a identidade de uma matrícula. O seletor ficou restrito ao popup, e a prévia é acionada antes das notificações de cópia para evitar toque interceptado. Nenhuma proteção foi removida. Também foi acrescentada preservação explícita de CR no XML, com fixtures CRLF na JVM e no Android, evitando normalização silenciosa de quebras de linha.

Segundo ensaio: [run 39129055875](https://github.com/italocesarfr-sketch/CHATGPTCODEX/actions/runs/39129055875), 134 Android aprovados e um teste de seleção CSV falhou por não encontrar o menu. Os testes agora aguardam explicitamente o encerramento da última notificação de cópia e selecionam itens apenas dentro do popup. A validação completa é repetida; critérios de dados e proteções não foram reduzidos.

Terceiro ensaio: [run 39130427689](https://github.com/italocesarfr-sketch/CHATGPTCODEX/actions/runs/39130427689), todos os testes novos passaram; 134 Android aprovados e uma falha intermitente no teste anterior do relatório individual. O stack aponta `SnapshotStateObserver` ao pesquisar recursivamente a semântica durante a rolagem. A fixture passou a rolar diretamente até o item conhecido e mantém a mesma asserção do quarto bimestre sem dados; asserções de zero, pendência, matrícula e ausência de gravação permanecem. A implementação dos relatórios não foi alterada para contornar o teste.

## Limitações e escopo

Arquivos validados por openpyxl e parser CSV/TSV, sem execução do Microsoft Excel/LibreOffice neste ambiente. A conferência de edição/colagem no Excel do proprietário e a validação física da 0.9.0 permanecem pendentes. Os testes Android usam API 30; não comprovam todos os fabricantes/API 26+. As planilhas não contêm fórmulas de cálculo futuro. Um programa consumidor pode interpretar texto iniciado por sinais como fórmula em CSV/TSV; XLSX mantém texto explicitamente tipado, sem fórmulas. As escolhas do provedor de documentos do Android são do professor; o aplicativo não possui permissão INTERNET.

Sem PDF, registro facultativo de aulas, identidade visual definitiva ou outras etapas. Nenhuma chave, certificado privado ou Secret foi criado/substituído/publicado. Release manual preservado. A entrega depende do CI, do exame do certificado e da prova de atualização; debug não é indicado para atualizar a release permanente. Faça backup antes de atualizar.
