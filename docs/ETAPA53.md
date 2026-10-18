# Etapa 5.3 — Registros de Aula livres e facultativos

**Noctua 0.10.0 / versionCode 15**, sobre a **0.9.0 / código 14**, aprovada pelo proprietário em aparelho físico. Autorização limitada a este módulo; a etapa visual final continua pendente.

## Uso e navegação

**Relatórios → Registros de Aula** é uma tela secundária, mantendo as quatro páginas principais. Usa o seletor global de turma/ano. A entrada fica ao lado de Exportar; não é necessário abrir o calendário ou fazer chamada. Não altera o bimestre dos relatórios.

**Criar registro** abre somente turma, data em dd/mm/aaaa, título e descrição. A data inicial usa hoje no ano da turma, ou 1º de janeiro daquele ano. Pode ser alterada livremente, inclusive retroativamente, sem exigir chamada, horários, atividades ou planejamento. Para evitar registro vazio acidental, basta algum texto no título **ou** na descrição; nenhum campo pedagógico adicional é exigido. Datas fora do ano letivo também são aceitas, pois o registro é livre: ajustar o filtro para consultá-las.

A lista apresenta data crescente, com ID como desempate estável. **Filtrar período** recolhe/expande os dois limites inclusivos; datas vazias consultam todo o histórico da turma. Período inválido informa o erro sem exibir dados de outra turma. Múltiplos registros no mesmo dia são permitidos.

**Editar** conserva o ID e permite alterar todos os campos. **Duplicar** abre uma nova edição com outro ID, sem escrever imediatamente; permite mudar turma/data e reutilizar o texto. **Salvar registro** confirma a gravação e fecha o editor somente após commit. O rascunho sobrevive à recriação da Activity. Sair de uma edição com texto alterado pede confirmação de descarte, incluindo voltar, navegação, seleção global e acesso ao backup. Falha mantém o editor e o erro; alteração concorrente confirmada exige reabrir o registro antes de sobrescrever/excluir.

**Excluir** pede confirmação explícita, identifica data/título e informa que chamadas e notas não serão alteradas. Cancelar não modifica o banco. Turmas arquivadas conservam os textos: Gerenciar turma → **Consultar registros de aula** permite leitura/cópia e duplicação para uma turma ativa; editar/excluir exige reativar a turma. A exclusão permanente de turma, já existente, passa a contar e remover também seus registros exclusivos, na mesma transação e após as mesmas proteções/confirmacões anteriores.

O aplicativo não cobra preenchimento nem gera pendências de registro. Ausência de texto não muda frequência, notas, relatórios, remanejamento ou exportação. Não há formulário de objetivos, BNCC, recursos ou metodologia.

## Copiar e colar no EducaDF

Cada registro apresenta **Copiar título** e **Copiar descrição**. A área de transferência padrão do Android recebe exatamente o texto salvo, inclusive acentos, aspas, tabulações e quebras de linha; nenhuma turma/data/cabeçalho é acrescentada. Depois, o professor abre o EducaDF e cola no campo correspondente. Não há autenticação, conexão ou envio automático ao EducaDF. A confirmação da cópia aparece junto ao registro, sem cobrir os botões seguintes; o Android também pode exibir seu indicador de cópia. A disponibilidade posterior da área de transferência segue as políticas do sistema.

## Arquitetura e dados

Compose → DiaryViewModel/fila de comandos do processo → DiaryRepository → Room. `LessonRecord` e funções puras `LessonRecords` ficam em `data/LessonRecords.kt`; a interface em `ui/LessonRecordsScreen.kt`. Nenhuma dependência foi acrescentada.

**Room v6**, migração aditiva **5→6**: cria apenas `lesson_records(id PK, classId FK RESTRICT, date, title, description)`, com índice não único `(classId,date)`. As doze tabelas existentes e seus IDs/índices não mudam; os JSONs v1–v5 ficam congelados. O novo texto não se vincula a `lessons` (horários da chamada), matrícula ou atividade. Navegar não cria registros.

O snapshot consistente do Repository observa a nova tabela; edições e exclusões usam transação e comparação com o registro conferido. O módulo não modifica `SchoolRules`, `SchoolReports`, notas, recomposição ou projeções de exportação. A gestão de exclusão de turma e o fingerprint completo incluem a nova tabela, preservando os registros das demais turmas e rollback em falha.

**Backup formato 7 / schema 6** inclui `lessonRecords`, IDs e texto completo; a prévia apresenta a contagem. O leitor mantém formatos 1–6, incluindo backups reais da 0.9.0 com arquivamento e disposição de exportação. Backup antigo não contém registros de aula: uma restauração completa explicitamente confirmada substitui o conjunto e deixa a nova lista vazia; a cópia privada prévia preserva o conjunto anterior. Não há mescla automática. Arquivos incompletos do formato novo, referências inexistentes e IDs duplicados são recusados antes da restauração. A restauração continua transacional e com verificação integral.

Mantidos `br.com.diariodeclasse`, `diario.db`, armazenamento privado e ausência de permissão INTERNET. Nenhuma migração destrutiva, limpeza na atualização ou mudança de chave/Secrets. O workflow release manual permanece preservado. A validação de atualização usa o APK **real 0.9.0** do [Actions 39131368000](https://github.com/italocesarfr-sketch/CHATGPTCODEX/actions/runs/39131368000), com hash e certificado públicos fixados em `docs/baseline/noctua-release-0.9.0.json`.

## Testes e evidências

Primeiro ensaio [39136000130](https://github.com/italocesarfr-sketch/CHATGPTCODEX/actions/runs/39136000130): 89 JVM e 147 Android aprovados, três falhas de UI. Dois testes procuravam itens ainda não renderizados pela LazyColumn; passaram a rolar até o índice da fixture antes da consulta, mantendo as asserções. A notificação de cópia cobria o segundo botão; a confirmação foi movida para o próprio registro, permitindo as duas cópias em sequência. Os nove testes Room novos passaram. A repetição completa e a atualização assinada foram aprovadas, conforme as evidências abaixo. Os quatro testes exclusivos ignorados na suíte comum permanecem identificados; não são contados como aprovados nessa suíte.

Cenários acrescentados: filtros turma/data; dois registros no mesmo dia; texto livre; rascunho/recriação; criação/edição/mudança de turma/duplicação/exclusão; confirmações/cancelamento; cópia exata; reinício Room; backup novo e formato 6 real; referência/arquivo inválido; conflito de edição; falha de restauração e de exclusão de turma com rollback; migração 5→6 e preservação das doze tabelas; ausência de impacto nos relatórios/exportações. A regressão anterior permanece na suíte. A sonda release sem desinstalar verifica dados fictícios antes/depois, novo registro, backup, reinício e a correção Transferida → Ativa → Remanejada.


### Resultado final e APK

- **89 testes JVM e 150 Android aprovados, sem falhas** na execução final. Os 15 novos testes Android (nove Room e seis de interface) passaram. Quatro sondas exclusivas são ignoradas na suíte comum: três recebem execução separada nas oito fases abaixo; o teste histórico ReleaseContinuity permanece ignorado, substituído pela atualização real assinada. Os 28 cenários Java seguem na regressão.
- Duas fases de frequência/reinício e duas de atualização do esquema legado aprovadas; quatro fases da atualização **release real 0.9.0 → 0.10.0** aprovadas: semente fictícia nas doze tabelas antigas, instalação `adb install -r` sem desinstalar/limpar dados, conferência integral, restauração de backup 6 e novo backup 7, novo registro de aula e reinício com o texto/IDs preservados. Transferida → Ativa → Remanejada continua coberto.
- Regressão de planilhas: **33 XLSX reais, 33 CSV e 99 TSV** novamente conferidos com openpyxl 3.1.5 e parser independente, incluindo três conjuntos produzidos no Android. O novo módulo não acrescenta formato de exportação.
- [Actions aprovado](https://github.com/italocesarfr-sketch/CHATGPTCODEX/actions/runs/39137573909), commit de código `412118bf5e1b0a43622f68352bb4b0bb48fc8d41`.
- [**Baixar APK release — ZIP**](https://github.com/italocesarfr-sketch/CHATGPTCODEX/actions/runs/39137573909/artifacts/12140304165), artefato **noctua-etapa53-release**; extrair `app-release.apk`. Este é o APK assinado com a chave permanente existente.
- [Resultados JVM/Android/Room, planilhas e capturas](https://github.com/italocesarfr-sketch/CHATGPTCODEX/actions/runs/39137573909/artifacts/12140367156) • [Provas da atualização assinada](https://github.com/italocesarfr-sketch/CHATGPTCODEX/actions/runs/39137573909/artifacts/12140926164) • [Debug interno](https://github.com/italocesarfr-sketch/CHATGPTCODEX/actions/runs/39137573909/artifacts/12139962985). Debug não é indicado para atualizar a instalação release.
- [Metadados verificados](ETAPA53_RESULTADOS.json).

O release foi baixado e verificado em `app/build/outputs/apk/release/app-release.apk` (7,294,418 bytes); debug em `app/build/outputs/apk/debug/app-debug.apk`. Ambos existem, identificam **0.10.0 / código 15**, Noctua, pacote `br.com.diariodeclasse`, mínimo API 26 e sem permissão INTERNET. O DEX contém o módulo novo. Os hashes dos ZIPs foram confrontados com os artefatos do Actions; os XMLs de testes e seis esquemas foram examinados localmente.

SHA-256 do release: `6ccfa6a440ea5acd11cca00cb3176ce7114ab7f4ddb10e96c4a146aee9807b58`. Certificado público SHA-256: `6dbbba2d930eb7f7fbeff1ee82512f656251bbec9f4b31b6c5614528d29f1aae`, igual ao APK real 0.9.0 fixado. Assinatura v2 verificada com apksigner. O certificado da instalação física não foi lido diretamente nesta execução: a prova cobre o APK 0.9.0 publicado com esse certificado. Antes de atualizar, crie backup; use a release correspondente sem desinstalar uma instalação compatível. Guarde o APK, pois o artefato de APK do Actions expira após 30 dias.

Room v6: identidade `0bb463bface3e8d6c547f9424574818b`, SHA-256 `35fe2e187d63b5763b03952a17473d63fcb96251a1e79a72533d8c68a8c712fe`. Os JSONs v1–v5 e as doze entidades anteriores no JSON v6 permanecem idênticos. `release.yml` e o bloco de assinatura Gradle permanecem preservados. Nenhuma chave/Secret foi criada, substituída ou publicada. Os testes usam exclusivamente dados fictícios.

## Limitações e próximas decisões

Sem integração automática com EducaDF; a colagem é manual. Não há anexos/fotos, busca textual ou sincronização; filtros são de turma e período. A lista mostra o texto completo e usa rolagem vertical. A data é digitada em dd/mm/aaaa, sem novo seletor visual de calendário. Textos e demais dados compartilham o limite de backup já existente de 32 MB. O desempenho com milhares de textos longos e fabricantes/API diferentes exige futura medição; não foi prometido armazenamento ilimitado. Ensaios de CI usam emulador API 30; validação física da nova versão fica com o proprietário.

Pendências futuras de UX, **somente documentadas**:

1. Centralizar “Organizar exportação” junto de “Relatórios”. Sua posição atual permanece.
2. Aplicar identidade visual noturna, azul escuro/preto, com foco em conforto visual. Tema atual permanece.

Não foram autorizados/implementados IA, preenchimento obrigatório, cronograma rígido, BNCC ou reformulação visual ampla. PDF continua fora da versão 1.0; a antiga seção histórica 5.3/PDF foi substituída por esta autorização de registros facultativos, sem reintroduzir PDF. Aguardar autorização antes da etapa visual final.
