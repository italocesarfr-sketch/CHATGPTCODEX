# Etapa 5.3 — Registros de Aula livres e facultativos

Candidato **Noctua 0.10.0 / versionCode 15**, sobre a **0.9.0 / código 14**, aprovada pelo proprietário em aparelho físico. Autorização limitada a este módulo; a etapa visual final continua pendente.

## Uso e navegação

**Relatórios → Registros de Aula** é uma tela secundária, mantendo as quatro páginas principais. Usa o seletor global de turma/ano. A entrada fica ao lado de Exportar; não é necessário abrir o calendário ou fazer chamada. Não altera o bimestre dos relatórios.

**Criar registro** abre somente turma, data em dd/mm/aaaa, título e descrição. A data inicial usa hoje no ano da turma, ou 1º de janeiro daquele ano. Pode ser alterada livremente, inclusive retroativamente, sem exigir chamada, horários, atividades ou planejamento. Para evitar registro vazio acidental, basta algum texto no título **ou** na descrição; nenhum campo pedagógico adicional é exigido. Datas fora do ano letivo também são aceitas, pois o registro é livre: ajustar o filtro para consultá-las.

A lista apresenta data crescente, com ID como desempate estável. **Filtrar período** recolhe/expande os dois limites inclusivos; datas vazias consultam todo o histórico da turma. Período inválido informa o erro sem exibir dados de outra turma. Múltiplos registros no mesmo dia são permitidos.

**Editar** conserva o ID e permite alterar todos os campos. **Duplicar** abre uma nova edição com outro ID, sem escrever imediatamente; permite mudar turma/data e reutilizar o texto. **Salvar registro** confirma a gravação e fecha o editor somente após commit. O rascunho sobrevive à recriação da Activity. Sair de uma edição com texto alterado pede confirmação de descarte, incluindo voltar, navegação, seleção global e acesso ao backup. Falha mantém o editor e o erro; alteração concorrente confirmada exige reabrir o registro antes de sobrescrever/excluir.

**Excluir** pede confirmação explícita, identifica data/título e informa que chamadas e notas não serão alteradas. Cancelar não modifica o banco. Turmas arquivadas conservam os textos: Gerenciar turma → **Consultar registros de aula** permite leitura/cópia e duplicação para uma turma ativa; editar/excluir exige reativar a turma. A exclusão permanente de turma, já existente, passa a contar e remover também seus registros exclusivos, na mesma transação e após as mesmas proteções/confirmacões anteriores.

O aplicativo não cobra preenchimento nem gera pendências de registro. Ausência de texto não muda frequência, notas, relatórios, remanejamento ou exportação. Não há formulário de objetivos, BNCC, recursos ou metodologia.

## Copiar e colar no EducaDF

Cada registro apresenta **Copiar título** e **Copiar descrição**. A área de transferência padrão do Android recebe exatamente o texto salvo, inclusive acentos, aspas, tabulações e quebras de linha; nenhuma turma/data/cabeçalho é acrescentada. Depois, o professor abre o EducaDF e cola no campo correspondente. Não há autenticação, conexão ou envio automático ao EducaDF. O Android pode exibir seu indicador de cópia. A disponibilidade posterior da área de transferência segue as políticas do sistema.

## Arquitetura e dados

Compose → DiaryViewModel/fila de comandos do processo → DiaryRepository → Room. `LessonRecord` e funções puras `LessonRecords` ficam em `data/LessonRecords.kt`; a interface em `ui/LessonRecordsScreen.kt`. Nenhuma dependência foi acrescentada.

**Room v6**, migração aditiva **5→6**: cria apenas `lesson_records(id PK, classId FK RESTRICT, date, title, description)`, com índice não único `(classId,date)`. As doze tabelas existentes e seus IDs/índices não mudam; os JSONs v1–v5 ficam congelados. O novo texto não se vincula a `lessons` (horários da chamada), matrícula ou atividade. Navegar não cria registros.

O snapshot consistente do Repository observa a nova tabela; edições e exclusões usam transação e comparação com o registro conferido. O módulo não modifica `SchoolRules`, `SchoolReports`, notas, recomposição ou projeções de exportação. A gestão de exclusão de turma e o fingerprint completo incluem a nova tabela, preservando os registros das demais turmas e rollback em falha.

**Backup formato 7 / schema 6** inclui `lessonRecords`, IDs e texto completo; a prévia apresenta a contagem. O leitor mantém formatos 1–6, incluindo backups reais da 0.9.0 com arquivamento e disposição de exportação. Backup antigo não contém registros de aula: uma restauração completa explicitamente confirmada substitui o conjunto e deixa a nova lista vazia; a cópia privada prévia preserva o conjunto anterior. Não há mescla automática. Arquivos incompletos do formato novo, referências inexistentes e IDs duplicados são recusados antes da restauração. A restauração continua transacional e com verificação integral.

Mantidos `br.com.diariodeclasse`, `diario.db`, armazenamento privado e ausência de permissão INTERNET. Nenhuma migração destrutiva, limpeza na atualização ou mudança de chave/Secrets. O workflow release manual permanece preservado. A validação de atualização usa o APK **real 0.9.0** do [Actions 39131368000](https://github.com/italocesarfr-sketch/CHATGPTCODEX/actions/runs/39131368000), com hash e certificado públicos fixados em `docs/baseline/noctua-release-0.9.0.json`.

## Testes e evidências

Aguardando resultados finais do CI. Não confundir compilação local da instrumentação com execução Android. Os resultados e links serão registrados após a execução.

Cenários acrescentados: filtros turma/data; dois registros no mesmo dia; texto livre; rascunho/recriação; criação/edição/mudança de turma/duplicação/exclusão; confirmações/cancelamento; cópia exata; reinício Room; backup novo e formato 6 real; referência/arquivo inválido; conflito de edição; falha de restauração e de exclusão de turma com rollback; migração 5→6 e preservação das doze tabelas; ausência de impacto nos relatórios/exportações. A regressão anterior permanece na suíte. A sonda release sem desinstalar verifica dados fictícios antes/depois, novo registro, backup, reinício e a correção Transferida → Ativa → Remanejada.

## Limitações e próximas decisões

Sem integração automática com EducaDF; a colagem é manual. Não há anexos/fotos, busca textual ou sincronização; filtros são de turma e período. A lista mostra o texto completo e usa rolagem vertical. A data é digitada em dd/mm/aaaa, sem novo seletor visual de calendário. Textos e demais dados compartilham o limite de backup já existente de 32 MB. O desempenho com milhares de textos longos e fabricantes/API diferentes exige futura medição; não foi prometido armazenamento ilimitado. Ensaios de CI usam emulador API 30; validação física da nova versão fica com o proprietário.

Pendências futuras de UX, **somente documentadas**:

1. Centralizar “Organizar exportação” junto de “Relatórios”. Sua posição atual permanece.
2. Aplicar identidade visual noturna, azul escuro/preto, com foco em conforto visual. Tema atual permanece.

Não foram autorizados/implementados IA, preenchimento obrigatório, cronograma rígido, BNCC ou reformulação visual ampla. PDF continua fora da versão 1.0; a antiga seção histórica 5.3/PDF foi substituída por esta autorização de registros facultativos, sem reintroduzir PDF. Aguardar autorização antes da etapa visual final.
