> **Noctua 0.9.0 / Etapa 5.2:** formato atual **6 / Room v5**, identidade `4fb9b076fb8a111f68f23ad0863c5f5c`. Inclui `classArchives` além das onze listas anteriores. Lê formatos 1–5; arquivos antigos não possuíam arquivamento e restauram suas turmas como ativas. A migração 4→5 cria apenas a tabela separada `class_archives`; não reescreve as demais. Todas as garantias transacionais abaixo permanecem. Consulte [ETAPA52.md](ETAPA52.md). As descrições do formato 5 abaixo são referência de compatibilidade; a 0.8.0 não lê o novo formato 6 nem o banco v5.

# Backup e restauração offline

O acesso fica no botão **Backup** da barra superior existente. A arquitetura da Etapa 1 permanece em uso; a versão 0.8.0 acrescenta disposição de exportação e preserva os vínculos de remanejamento e mantém a leitura dos backups antigos. Consulte [ETAPA51](ETAPA51.md) para a arquitetura e os resultados.

## Uso

1. **Criar backup completo** abre o seletor Android. Escolha o destino e aguarde “Backup salvo”. Não há envio a servidor; o destino pode ser armazenamento local. Provedores externos, se escolhidos no seletor do sistema pelo professor, são responsabilidade desse provedor.
2. **Selecionar backup para restaurar** apenas lê e valida o arquivo e mostra data/contagens. Não altera registros.
3. Confira turma(s), matrículas, aulas, frequências, atividades, notas, recomposições, vínculos de remanejamento, rascunhos e linhas de disposição de exportação. Digite **RESTAURAR** e toque em **Confirmar substituição dos dados** para substituir o conjunto completo. Não há mescla de bancos nesta versão.
4. Antes da substituição, o app grava e relê uma cópia íntegra dos dados atuais em `files/safety-backups/`, privado do aplicativo. Se não conseguir gravá-la/validá-la, a restauração é recusada.
5. Após restauração, o app retorna à página Cadastrar Turma para descartar campos de edição que poderiam mostrar valores antigos. **Exportar cópia anterior à restauração** permite guardar externamente a cópia de segurança mais recente. Ela também pode ser selecionada para restaurar, com o mesmo procedimento explícito.

Cancelar o seletor ou a prévia não restaura nada. Arquivo vazio/corrompido, versão incompatível, vínculo inválido ou mudança dos dados após a prévia bloqueiam a restauração. Nesse último caso, selecione novamente o arquivo para obter uma prévia atualizada.

## Conteúdo e formatos 1 a 5

Banco `diario.db`, Room **versão 4**, identidade `924bea88bcff9fe88f85edf884f4ab65`. Migração aditiva 3→4 cria somente `export_rows` e seus índices; as dez tabelas v3 permanecem idênticas. O formato atual **5** contém onze listas completas: `classes`, `students`, `terms`, `lessons`, `marks`, `activities`, `grades`, `import_drafts` (no payload, `drafts`), `recompositions`, `relocations` e `exportRows`. A última contém IDs persistentes das linhas/matrículas, turma, posição e ocultação manual; matrícula nula representa linha vazia explícita. Exclusão automática por remanejamento é calculada do histórico, não gravada como ocultação manual. Notas/chamadas continuam nos IDs de origem. Configurações por turma estão em `classes`, e `selection` contém a última turma/ano.

O leitor aceita formatos **1 e 2 / banco v1**, identidade `99b868cf68cf5dffb82edf267ddc78be`; **3 / banco v2**, identidade `32104b8df79c150ac61b09fadc450221`; **4 / banco v3**, identidade `9e864249a18922cfb9caa6e54ee6c3af`. Formatos 1/2 recebem listas vazias de recomposições e vínculos; formato 3 recebe vínculos vazios; todos os formatos anteriores a 5 recebem disposição vazia, usando a ordem padrão até o professor personalizá-la. Nenhum vínculo é inferido de nomes ou personId antigos. Formato 1 sem `selection` restaura contexto padrão; demais preservam contexto disponível.

Restaurar arquivo antigo substitui integralmente o conjunto, inclusive retirando preferências/vínculos/recomposições ausentes após confirmação; a cópia anterior preserva os dados atuais. Não há fusão automática. A versão 0.7.1 não lê formato 5 nem banco v4: não fazer downgrade.

Arquivo JSON UTF-8 com cabeçalho obrigatório: `format`, `formatVersion`, `applicationId`, `databaseVersion`, `schemaIdentityHash`, `createdAt`, `appVersionCode`, `sourceCertificateSha256`, `payloadSha256` e `payload`. O payload é uma **string JSON** com as listas completas e o contexto disponível. Nos formatos 3–5, omitir `recompositions` é erro; nos formatos 4/5, omitir `relocations` também é erro; no formato 5, `exportRows` é obrigatório, mesmo com checksum válido. Vínculos são validados quanto a identidade, cronologia, ciclos, compatibilidade e recomposições conflitantes. O SHA-256 corresponde exatamente aos bytes UTF-8 da string original; não depende da indentação externa. Arrays são normalizados por IDs/chaves compostas; disposição por turma e posição, validando referências da própria matrícula/turma, IDs únicos e posições contínuas. Preserva notas nulas e zero, recomposição não realizada, datas, booleanos, nomes/observações Unicode, identidades e rascunhos incompletos.

A leitura é limitada a **32 MiB**; arquivos maiores são recusados sem tocar no banco. O checksum detecta corrupção, **não autentica o autor**. O arquivo é texto sem criptografia: contém dados pessoais e deve ser guardado em destino protegido pelo professor. Nenhum conteúdo escolar é escrito em logs.

## Garantias implementadas e limites

- O snapshot é lido com `Room.withTransaction`, incluindo registros já confirmados no WAL. Não se copia um banco aberto com `File.copyTo`.
- A restauração valida o arquivo antes da prévia, compara um fingerprint da base atual na confirmação e faz a substituição inteira em uma transação, com chaves estrangeiras ativas.
- A seleção da cópia mais recente usa um índice `AtomicFile`, independente da resolução das datas de arquivo/alterações do relógio. Todas as cópias completas anteriores continuam guardadas.
- A cópia anterior é gravada com `AtomicFile`, relida e comparada ao estado atual antes de modificar tabelas. Se uma inserção/verificação falhar, o SQLite desfaz as exclusões/inserções da transação. O arquivo ativo do banco não é apagado ou substituído.
- Nenhum `clearAllTables`, `deleteDatabase` ou migração destrutiva faz parte do serviço de produção. Exclusões de registros durante a restauração só ocorrem após a confirmação literal e dentro da transação protegida. Chamadas/notas continuam sendo salvas pelo fluxo atual.
- O diálogo aguarda a fila de gravações existente antes de habilitar suas ações. Alterações concorrentes concluídas depois da prévia são detectadas pelo fingerprint.
- Cópias internas não substituem backup externo: desinstalar o aplicativo ou perder o aparelho também elimina seus arquivos privados. Não são apagadas automaticamente nesta versão. A exportação oferece a mais recente; cópias antigas permanecem no diretório privado para suporte autorizado.
- Não há promessa de restauração entre esquemas/formato futuros desconhecidos; uma futura versão precisará implementar e testar a conversão explicitamente.

## Resgate de APK debug legado sem chave

Consulte [ASSINATURA.md](ASSINATURA.md) antes de instalar qualquer atualização. `scripts/rescue-debug-data.py` exporta localmente o banco de uma instalação depurável por ADB, sem instalar, desinstalar ou limpar o aparelho. Encerra o processo do app, copia banco/WAL/SHM para pasta nova privada, verifica SQLite/FKs/esquema e gera `resgate-diario.json` compatível com o importador desta versão. O procedimento só aceita a versão Room 1 conhecida; o arquivo exige conferência e confirmação no aplicativo de destino.

Não execute scripts de `scripts/ci/` em celulares: eles são exclusivos de emulador descartável e recusam execução fora do GitHub Actions/emulador. Os testes usam somente dados sintéticos e nunca versionam um banco pessoal.

## Contexto visual na Etapa 2

A gravação de `selection` ocorre após o commit dos registros escolares: SQLite e SharedPreferences não são uma transação única. Se a preferência falhar, a restauração escolar permanece confirmada e a tela avisa para selecionar turma/ano novamente. A navegação resolve IDs ausentes sem criar nem apagar turmas. Na versão 0.8.0, as onze tabelas são validadas, restauradas e verificadas atomicamente; formatos anteriores deixam vazias somente as listas que não possuíam. A prévia/fingerprint e a cópia anterior também incluem o contexto visual disponível.
