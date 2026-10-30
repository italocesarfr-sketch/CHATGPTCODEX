# 0.21.0 — Fundação da sincronização acadêmica (experimental)

Base: `auth/supabase-protected-workspaces`, documentação `34bbcaf`, código de autenticação `c24a7d9`, CI aprovado [40812203803](https://github.com/italocesarfr-sketch/CHATGPTCODEX/actions/runs/40812203803). A `main` permanece na 0.19.1. Desenvolvimento isolado em `sync/academic-foundation-0210`, versão 0.21.0 / código 39. Não é promoção estável nem sincronização acadêmica completa.

## Escopo e consentimento

A primeira unidade é **configuração da turma + seus quatro bimestres**, preservando os IDs existentes. Não envia alunos, nomes de estudantes, notas, frequências, justificativas, conteúdos do diário, remanejamentos nem a grade semanal. Essas entidades foram mapeadas para fases futuras; não estão sincronizadas.

A autenticação não habilita o envio. Em **Conta → Sincronização de turmas • teste**, é necessário ativar o teste e confirmar individualmente a participação de cada turma. Nenhuma turma histórica é inscrita automaticamente. Uma instalação da mesma conta que ative o teste recebe as configurações autorizadas da conta; não recupera seus lançamentos acadêmicos. Novas turmas locais também exigem inscrição explícita nesta etapa. Pausar conserva fila e banco.

O status sempre começa com “Turmas”; “Turmas sincronizadas” significa somente configurações/bimestres participantes, com confirmação do servidor e consulta concluída. Não significa backup completo na nuvem. O professor deve manter o backup local protegido.

## Mapeamento das entidades reais

| Room v10 acadêmico | Identidade estável / vínculos | Representação remota e fase |
|---|---|---|
| `classes` | `id`; ano letivo é `year`, sem tabela própria | `noctua_class_bundles.payload.school` — implementado |
| `terms` | `(classId, number)` → turma | `payload.terms` com exatamente quatro períodos — implementado, atomicamente com turma |
| `students` | `id` da matrícula; `personId` identifica pessoa, `classId` turma | Futuro: matrícula por `(owner_id,id)`, FK composta da turma; pessoa não deve fundir matrículas pelo nome |
| `lessons` | `id`, único `(classId,date,ordinal)` | Futuro: aula; estado/note preservados, sem geração a partir da grade |
| `marks` | `(lessonId,studentId)`, `mark` e `reason` opcional | Futuro: frequência com ambas FKs compostas e verificação de turma; NR continua diferente de ausência de linha |
| `activities` | `id` → turma; data, bimestre, tipo, máximo | Futuro: atividade; preservar Produção textual e categorias antigas |
| `grades` | `(activityId,studentId)`, valor nullable | Futuro: nota; zero não equivale a null; nenhuma fórmula remota nova |
| `recompositions` | `(studentId,term)`, valor nullable/data | Futuro: recomposição, não recalcular resultado histórico |
| `relocations` | PK `sourceStudentId`, destino nullable único | Futuro: vínculo dirigido, mesma pessoa, origem/destino, correções/auditoria JSON versionada; sem fusão silenciosa |
| `preserved_histories` | `classId`, `content` agregado | Futuro: agregado histórico independente; não reatribuir à turma sobrevivente |
| `lesson_records` | `id` → turma; data/título/descrição | Futuro: diário facultativo, independente da frequência |
| `weekly_timetables` | `id`, único `(year,effective,revision)`, slots JSON | Futuro: revisão imutável, referências de turma em slots, inclusive legadas/excluídas |
| `class_archives` | PK/FK `classId` | Futuro: arquivamento; ainda não é distribuído nesta etapa |
| `import_drafts` | `id` → turma | Futuro: rascunho; não equivale a matrícula confirmada |
| `export_rows` | `id`, turma, matrícula opcional, posição/ocultação | Futuro: preferências com FKs e unicidade; não deriva da ordenação temporária do boletim |

Todas as futuras PKs/FKs remotas devem incluir o proprietário; um ID isolado não autoriza referenciar dados de outra conta. Datas e IDs existentes não serão recriados. O calendário escolar de referência (`SchoolCalendar`) não é uma tabela pessoal persistida: não inventar uma entidade institucional. Recuperação/resultados vigentes são projeções das atividades/notas/recomposições existentes, não uma nova fórmula ou tabela redundante.

## PostgreSQL e RLS

Migração reproduzível: [`../supabase/migrations/202610300001_class_sync.sql`](../supabase/migrations/202610300001_class_sync.sql). Não aplicada ao projeto hospedado nesta execução. Cria:

- `noctua_sync_heads`: contador por proprietário; bloqueio da linha ordena commits e evita que o cursor ultrapasse uma transação ainda não confirmada.
- `noctua_class_bundles`: projeção atual por `(owner_id,id)`, revisão, sequência e tombstone.
- `noctua_class_versions`: versões aceitas imutáveis, incluindo a anterior à exclusão.
- `noctua_sync_receipts`: operação UUID, requisição e resposta confirmada para reenvio idempotente.

RLS habilitada nas quatro tabelas. `authenticated` só recebe SELECT com `owner_id=auth.uid()`. Não há INSERT/UPDATE/DELETE/TRUNCATE direto, nem acesso anônimo. As únicas gravações passam por `noctua_push_class`; `noctua_pull_classes` consulta a conta atual. As funções definidoras usam `search_path=pg_catalog`, objetos qualificados, verificam usuário confirmado/não anônimo em `auth.users`, e derivam o proprietário de `auth.uid()`. **Não existe parâmetro owner no RPC**. Nenhuma chave administrativa é incorporada ao Android.

O servidor valida campos/tipos, quatro bimestres, ano, datas sem sobreposição, escala e limite de 16 KiB por agregado. Não aceita campos de alunos escondidos no payload. A FK de versões conserva histórico; não há exclusão física via API.

O cliente usa HTTPS no projeto já autorizado, chave pública e bearer da própria sessão. Não segue redirecionamentos nem registra payloads/tokens/erros crus. O transporte de autenticação existente e a proteção PKCE não foram substituídos. Não foram criadas permissões institucionais.

## Room, fila e transações

Room **10→11**: somente três tabelas adicionais, `sync_control`, `sync_classes`, `sync_operations`. As quinze tabelas acadêmicas anteriores, IDs, migrações e regras permanecem intactas. O proprietário da fila é conferido antes do uso; os bancos continuam separados por UUID conforme a 0.20.0. Nenhum banco é limpo ou recriado.

Após inscrição, triggers de INSERT/UPDATE/DELETE em `classes` e `terms` incrementam a versão suja dentro da própria transação acadêmica. Uma falha desfaz os dois. Antes do envio, uma transação materializa um payload imutável da turma e bimestres confirmados e registra UUID, proprietário, entidade/ID, UPSERT ou DELETE, revisão esperada, versão local, criação e tentativas. Existe no máximo uma operação em trânsito por turma; novas edições ficam sujas até a confirmação da anterior.

A operação continua pendente após timeout, interrupção, processo encerrado ou ausência de sessão. Seu UUID não muda no reenvio. O servidor retorna a mesma resposta para a mesma operação; reutilizar UUID com outro conteúdo é rejeitado. Somente resposta válida correspondente à operação, registro e revisão confirma a versão enviada. Uma edição local mais recente jamais é confirmada por engano.

Recebimento aplica no máximo 100 agregados por página, alteração e cursor na mesma transação. O sinal `applying` impede eco dos triggers; falha desfaz sinal, dados e cursor. Não aplica dados parciais de turma sem bimestres. Mesmo fora de ordem, revisões antigas não substituem revisões já conhecidas.

`JobScheduler` nativo, sem dependências novas: job com conectividade para alterações locais/ativação, e periódico de 15 minutos, persistidos pelo Android. Não há promessa de tempo exato: Doze, bateria, fabricante e force-stop podem adiar execução. Ao reabrir e após a conexão, a fila continua. O serviço só abre o banco da conta admitida, nunca contorna a vinculação legada. A tolerância offline de autenticação admite apenas uso local; o servidor exige bearer válido para cada RPC.

## Conflitos, dependências e exclusões

Não há last-write-wins. O RPC compara a revisão esperada com a atual sob bloqueio por conta. Operação atrasada fica em conflito; o banco local e o payload remoto ficam preservados, assim como base/operacão e versões confirmadas do servidor. Nenhuma nota/frequência é mesclada ou descartada.

Uma colisão com ID local não participante bloqueia a importação. Uma atualização ou tombstone remoto de turma com estudantes, aulas, atividades, rascunhos, arquivos de histórico, grade, diário, preferências de exportação ou arquivamento local **não altera nem exclui esses dados**: registra conflito. Alterações remotas aplicam automaticamente somente a turmas sem esses vínculos. Isso é propositalmente conservador.

Exclusão local continua passando pelo mecanismo acadêmico atual com diagnóstico/preservação/transação. Para turma participante, o trigger produz tombstone; não exporta seu histórico e não força a exclusão desse histórico em outro aparelho.

Resolução de conflito ainda não possui botão de sobrescrever. O status identifica o bloqueio. Estratégia verificável: comparar payload base, operação pendente, banco local e versão remota preservados; só então implementar uma escolha explícita com nova revisão esperada e novo UUID. Não editar SQL/filas manualmente em dados reais para “destravar”. Enquanto houver dúvida, manter ambas as versões e os backups. A Etapa 1 não autoriza fusão de matrículas ou exclusão de históricos.

## Backup e restauração

Backup acadêmico **formato 12** e envelope protegido da 0.20.0 permanecem inalterados, incluindo criptografia, titularidade, senha, leitura de formatos anteriores e cópia de segurança. O `databaseVersion=10` do envelope continua identificando o **payload acadêmico v10**, idêntico ao anterior; o banco físico v11 tem somente tabelas extras de transporte. `BackupManager` verifica separadamente a identidade física v11. O backup não declara ser cópia das filas de rede.

Uma restauração acadêmica é transacional e **pausa a sincronização**. Preserva operações/metadados existentes para auditoria, marca bloqueio RESTORE e não envia deleções/reescritas produzidas pela restauração. Uma resposta de rede tardia não retoma nem confirma a fila pausada. A aplicação não oferece retomada automática desse caso antes de reconciliação segura. Um backup aberto em instalação nova não herda titularidade, cursor, tokens ou consentimento de nuvem.

A sincronização parcial não substitui o backup completo e não altera a vinculação do banco antigo: continuam exigidos conta autorizada confirmada, consentimento, credencial Android e backup protegido. A migração Room não atribui proprietário automaticamente nem inscreve nenhuma turma.

## Pequenos ajustes da homologação

Mensagem de recuperação: **“Solicitação recebida. Verifique seu e-mail.”** Somente após aceitação do pedido pelo cliente. Senha e confirmação têm controles Mostrar/Ocultar independentes, sem armazenamento. Título “Conectando” e indicador centralizados horizontalmente. Nenhum controle de autenticação foi relaxado.

Impeccable v4.7 do repositório foi consultada (`SKILL.md`, contexto do alvo, harden, craft-floor e android): Material 3, tema atual, rolagem, alvos de toque e mensagens curtas sem afirmar proteção/recuperação que não existe.

## Validação e evidências

Resultados locais efetivamente executados na primeira rodada desta branch:

- `testDebugUnitTest`: **215 casos, 214 aprovados, 1 condicional ignorado, 0 falhas**. O condicional é a integração Auth real, que exige credencial privada; não foi repetido nem confundido com sincronização real.
- `assembleDebug` e `assembleDebugAndroidTest`: compilação aprovada.
- `scripts/test-sync-postgres.py`: **22 testes aprovados em PostgreSQL 17 local descartável**, com políticas RLS e funções reais, usuários sintéticos e `auth.uid()` de fixture. Não é uma validação de JWT ou Supabase hospedado.
- Consulta real, somente leitura da disponibilidade do RPC hospedado: **HTTP 404 / PGRST202**. Função `noctua_pull_classes` ainda ausente. Nenhum registro acadêmico foi enviado.

Testes Android adicionados: fila transacional, rollback, reenvio/perda de ACK, alteração local durante envio, dois bancos por mesma conta, isolamento de conta, credenciais recusadas, concorrência/conflito, colisão de IDs, histórico protegido, exclusão vazia/tombstone, reinício, migração 10→11, backup e restauração pausada, saída durante envio, cancelamento, consentimento e fonte ampliada. O transporte desses testes é determinístico e fictício. Há uma sonda em dois processos para operação persistente. A regressão acadêmica e os leitores independentes de XLSX/CSV/TSV continuam obrigatórios no CI.

**Resultados Android, capturas e atualização assinada: aguardam a execução desta branch.** Não transferir a aprovação da 0.20.0 para o código novo. O workflow fará atualização sobre o APK 0.20.0 original assinado, sem desinstalar nem criar certificado. Artefatos desta branch são somente candidatos experimentais.

## Configuração gratuita pendente e prova real

1. No projeto Supabase existente, executar a migração SQL versionada pelo SQL Editor com uma sessão administrativa do proprietário. Não colocar credenciais administrativas no aplicativo, chat ou Actions.
2. Conferir RLS/GRANTs, funções e resposta via API com usuário confirmado. Plano gratuito, Postgres/Auth/PostgREST existentes bastam; não precisa domínio, SMTP personalizado ou Resend para transportar dados acadêmicos.
3. Instalar candidato somente em ambientes de teste com backup. Autenticar a conta de teste já autorizada. Ativar o teste e inscrever **somente turma fictícia sem registros reais**.
4. Criar/editar offline; conferir estado pendente. Reconectar; verificar receipt/revisão no servidor e ausência de ACK antes de confirmação.
5. Em segunda instalação da mesma conta, ativar teste e verificar turma, ID e quatro bimestres. Notas/alunos não devem surgir.
6. Com uma **segunda conta de teste autorizada pelo proprietário** (ainda não fornecida para esta etapa), comprovar impossibilidade de ler/alterar as linhas da primeira via RPC e SELECT direto. Não criar contas do piloto.
7. Modificar o mesmo registro em dois dispositivos, incluindo um offline; confirmar conflito preservado, sem sobrescrita. Simular morte de processo/perda da resposta e conferir idempotência.

Não foi criada conta de teste nova nem solicitado acesso administrativo. SMTP continua com as limitações da 0.20.0 para novas contas/confirmações; não desabilitar controles para testar. A recuperação Android por link ainda depende da homologação física da autenticação anterior.

## Limitações e próximo passo

Sincronização hospedada **não homologada** enquanto faltarem SQL e teste real com sessões autorizadas. Os demais treze tipos persistidos não são enviados; arquivamento e alterações com vínculos protegidos podem gerar divergência que exige conferência. Conflitos/restauração pausam aplicação automática; não há interface de resolução ainda. O agendador depende das políticas do Android e não supera um force-stop do usuário até a reabertura. Quotas gratuitas de armazenamento/banda e crescimento das versões precisam ser monitorados; nada é truncado automaticamente.

Próximo passo: aplicar e revisar SQL no ambiente de teste, executar a prova real acima com turmas fictícias e duas contas autorizadas, homologar atualização e depois projetar o próximo agregado. **Não promover main nem anunciar recuperação integral na nuvem.**
