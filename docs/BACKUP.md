# Backup e restauração offline

O acesso fica no botão **Backup** da barra superior existente. A arquitetura da Etapa 1 permanece em uso; a versão 0.7.0 acrescenta vínculos de remanejamento e mantém a leitura dos backups antigos. Consulte [ETAPA4C](ETAPA4C.md) para a arquitetura e os resultados.

## Uso

1. **Criar backup completo** abre o seletor Android. Escolha o destino e aguarde “Backup salvo”. Não há envio a servidor; o destino pode ser armazenamento local. Provedores externos, se escolhidos no seletor do sistema pelo professor, são responsabilidade desse provedor.
2. **Selecionar backup para restaurar** apenas lê e valida o arquivo e mostra data/contagens. Não altera registros.
3. Confira turma(s), matrículas, aulas, frequências, atividades, notas, recomposições, vínculos de remanejamento e rascunhos. Digite **RESTAURAR** e toque em **Confirmar substituição dos dados** para substituir o conjunto completo. Não há mescla de bancos nesta versão.
4. Antes da substituição, o app grava e relê uma cópia íntegra dos dados atuais em `files/safety-backups/`, privado do aplicativo. Se não conseguir gravá-la/validá-la, a restauração é recusada.
5. Após restauração, o app retorna à página Cadastrar Turma para descartar campos de edição que poderiam mostrar valores antigos. **Exportar cópia anterior à restauração** permite guardar externamente a cópia de segurança mais recente. Ela também pode ser selecionada para restaurar, com o mesmo procedimento explícito.

Cancelar o seletor ou a prévia não restaura nada. Arquivo vazio/corrompido, versão incompatível, vínculo inválido ou mudança dos dados após a prévia bloqueiam a restauração. Nesse último caso, selecione novamente o arquivo para obter uma prévia atualizada.

## Conteúdo e formatos 1, 2, 3 e 4

Banco `diario.db`, Room **versão 3**, identidade `9e864249a18922cfb9caa6e54ee6c3af`. A migração aditiva 2→3 somente cria `relocations` e seu índice; as nove tabelas v2 permanecem idênticas. O formato atual **4** contém dez listas completas: `classes`, `students`, `terms`, `lessons`, `marks`, `activities`, `grades`, `import_drafts` (no payload, `drafts`), `recompositions` e `relocations`. O vínculo guarda matrículas de origem/destino, data efetiva e nome externo quando não houver destino cadastrado. Notas/chamadas continuam nos IDs de origem. Configurações por turma estão em `classes`, e `selection` contém a última turma/ano.

O leitor aceita formatos **1 e 2 / banco v1**, com identidade `99b868cf68cf5dffb82edf267ddc78be`, e **3 / banco v2**, com identidade `32104b8df79c150ac61b09fadc450221`. Os formatos 1/2 recebem listas vazias de recomposições e vínculos; o formato 3 recebe somente vínculos vazios. Nenhum vínculo é inferido de nomes ou personId antigos. Formato 1 sem `selection` restaura o contexto padrão; os demais preservam o contexto disponível.

Restaurar arquivo antigo substitui integralmente o conjunto, inclusive retirando os vínculos/recomposições ausentes do arquivo após confirmação; a cópia anterior preserva esses dados atuais. Não há fusão automática. A versão 0.6.1 não lê o formato 4 nem o banco v3: não fazer downgrade.

Arquivo JSON UTF-8 com cabeçalho obrigatório: `format`, `formatVersion`, `applicationId`, `databaseVersion`, `schemaIdentityHash`, `createdAt`, `appVersionCode`, `sourceCertificateSha256`, `payloadSha256` e `payload`. O payload é uma **string JSON** com as listas completas e o contexto disponível. Nos formatos 3/4, omitir `recompositions` é erro; no formato 4, omitir `relocations` também é erro, mesmo com checksum válido. Vínculos são validados quanto a identidade, cronologia, ciclos, compatibilidade e recomposições conflitantes. O SHA-256 corresponde exatamente aos bytes UTF-8 da string original; não depende da indentação externa. Arrays são normalizados por IDs/chaves compostas. Preserva notas nulas e zero, recomposição não realizada, datas, booleanos, nomes/observações Unicode, identidades e rascunhos incompletos.

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

A gravação de `selection` ocorre após o commit dos registros escolares: SQLite e SharedPreferences não são uma transação única. Se a preferência falhar, a restauração escolar permanece confirmada e a tela avisa para selecionar turma/ano novamente. A navegação resolve IDs ausentes sem criar nem apagar turmas. Na versão 0.7.0, as dez tabelas são validadas, restauradas e verificadas atomicamente; formatos anteriores deixam vazias somente as listas que não possuíam. A prévia/fingerprint e a cópia anterior também incluem o contexto visual disponível.
