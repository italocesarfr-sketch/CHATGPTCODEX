# Backup e restauração offline

O acesso fica no botão **Backup** da barra superior existente. A arquitetura da Etapa 1 permanece em uso; a versão 0.6.0 acrescenta recomposições com leitura dos backups antigos. Consulte [ETAPA4B2](ETAPA4B2.md) para os resultados validados.

## Uso

1. **Criar backup completo** abre o seletor Android. Escolha o destino e aguarde “Backup salvo”. Não há envio a servidor; o destino pode ser armazenamento local. Provedores externos, se escolhidos no seletor do sistema pelo professor, são responsabilidade desse provedor.
2. **Selecionar backup para restaurar** apenas lê e valida o arquivo e mostra data/contagens. Não altera registros.
3. Confira turma(s), matrículas, aulas, frequências, atividades, notas, recomposições e rascunhos. Digite **RESTAURAR** e toque em **Confirmar substituição dos dados** para substituir o conjunto completo. Não há mescla de bancos nesta versão.
4. Antes da substituição, o app grava e relê uma cópia íntegra dos dados atuais em `files/safety-backups/`, privado do aplicativo. Se não conseguir gravá-la/validá-la, a restauração é recusada.
5. Após restauração, o app retorna à página Cadastrar Turma para descartar campos de edição que poderiam mostrar valores antigos. **Exportar cópia anterior à restauração** permite guardar externamente a cópia de segurança mais recente. Ela também pode ser selecionada para restaurar, com o mesmo procedimento explícito.

Cancelar o seletor ou a prévia não restaura nada. Arquivo vazio/corrompido, versão incompatível, vínculo inválido ou mudança dos dados após a prévia bloqueiam a restauração. Nesse último caso, selecione novamente o arquivo para obter uma prévia atualizada.

## Conteúdo e formatos 1, 2 e 3

Banco `diario.db`, Room **versão 2**, identidade `32104b8df79c150ac61b09fadc450221`. A migração aditiva 1→2 somente cria `recompositions`; as oito tabelas anteriores permanecem idênticas. O formato atual **3** inclui as nove listas completas: `classes`, `students`, `terms`, `lessons`, `marks`, `activities`, `grades`, `import_drafts` (no payload, `drafts`) e `recompositions`. Cada recomposição guarda matrícula, bimestre de origem, nota anulável e data. Configurações por turma estão em `classes`, e `selection` contém a última turma/ano.

O leitor aceita também os formatos **1 e 2 / banco v1**, com a identidade legada `99b868cf68cf5dffb82edf267ddc78be`. Formato 1 sem `selection` restaura o contexto padrão; formato 2 preserva o contexto disponível. Ambos recebem uma lista vazia de recomposições. Restaurar arquivo antigo substitui o conjunto completo, inclusive retirando as recomposições atuais após a confirmação; a cópia anterior à restauração preserva essas recomposições. Não há mescla automática. A versão 0.5.0 não lê o formato 3; não fazer downgrade sobre o banco v2.

Arquivo JSON UTF-8 com cabeçalho obrigatório: `format`, `formatVersion`, `applicationId`, `databaseVersion`, `schemaIdentityHash`, `createdAt`, `appVersionCode`, `sourceCertificateSha256`, `payloadSha256` e `payload`. O payload é uma **string JSON** com as listas completas e o contexto disponível. No formato 3, omitir `recompositions` é erro, mesmo com checksum válido. O SHA-256 corresponde exatamente aos bytes UTF-8 da string original; não depende da indentação externa. Arrays são normalizados por IDs/chaves compostas. Preserva notas nulas e zero, recomposição não realizada, datas, booleanos, nomes/observações Unicode, identidades e rascunhos incompletos.

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

A gravação de `selection` ocorre após o commit dos registros escolares: SQLite e SharedPreferences não são uma transação única. Se a preferência falhar, a restauração escolar permanece confirmada e a tela avisa para selecionar turma/ano novamente. A navegação resolve IDs ausentes sem criar nem apagar turmas. Na versão 0.6.0, as nove tabelas são validadas, restauradas e verificadas atomicamente; os formatos antigos preservam suas oito listas e deixam vazia a nona. A prévia/fingerprint e a cópia anterior também incluem o contexto visual disponível.
