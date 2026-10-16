# Backup e restauração offline — Etapa 1

O acesso fica no botão **Backup** da barra superior existente. Não foram reformulados cadastro, chamada ou navegação da Etapa 2.

## Uso

1. **Criar backup completo** abre o seletor Android. Escolha o destino e aguarde “Backup salvo”. Não há envio a servidor; o destino pode ser armazenamento local. Provedores externos, se escolhidos no seletor do sistema pelo professor, são responsabilidade desse provedor.
2. **Selecionar backup para restaurar** apenas lê e valida o arquivo e mostra data/contagens. Não altera registros.
3. Confira turma(s), matrículas, aulas, frequências, atividades, notas e rascunhos. Digite **RESTAURAR** e toque em **Confirmar substituição dos dados** para substituir o conjunto completo. Não há mescla de bancos nesta versão.
4. Antes da substituição, o app grava e relê uma cópia íntegra dos dados atuais em `files/safety-backups/`, privado do aplicativo. Se não conseguir gravá-la/validá-la, a restauração é recusada.
5. Após restauração, o app retorna ao painel para descartar campos de edição que poderiam mostrar valores antigos. **Exportar cópia anterior à restauração** permite guardar externamente a cópia de segurança mais recente. Ela também pode ser selecionada para restaurar, com o mesmo procedimento explícito.

Cancelar o seletor ou a prévia não restaura nada. Arquivo vazio/corrompido, versão incompatível, vínculo inválido ou mudança dos dados após a prévia bloqueiam a restauração. Nesse último caso, selecione novamente o arquivo para obter uma prévia atualizada.

## Conteúdo e formato 1

Banco `diario.db`, Room **versão 1**, identidade `99b868cf68cf5dffb82edf267ddc78be`, sem alteração de esquema/migração nesta etapa. Inclui as oito tabelas: `classes`, `students`, `terms`, `lessons`, `marks`, `activities`, `grades` e `import_drafts` (no payload, `drafts`). Configurações atuais por turma estão em `classes`. Ainda não existem preferências persistentes externas ao banco ou ordens de exportação; não foram inventadas nesta etapa.

Arquivo JSON UTF-8 com cabeçalho obrigatório: `format`, `formatVersion`, `applicationId`, `databaseVersion`, `schemaIdentityHash`, `createdAt`, `appVersionCode`, `sourceCertificateSha256`, `payloadSha256` e `payload`. O payload é uma **string JSON** com as oito listas completas. O SHA-256 corresponde exatamente aos bytes UTF-8 dessa string; não depende da indentação externa do arquivo. Arrays são normalizados por IDs/chaves compostas. O formato preserva notas nulas e zero, booleanos, nomes/observações Unicode, identidades e rascunhos incompletos.

A leitura é limitada a **32 MiB**; arquivos maiores são recusados sem tocar no banco. O checksum detecta corrupção, **não autentica o autor**. O arquivo é texto sem criptografia: contém dados pessoais e deve ser guardado em destino protegido pelo professor. Nenhum conteúdo escolar é escrito em logs.

## Garantias implementadas e limites

- O snapshot é lido com `Room.withTransaction`, incluindo registros já confirmados no WAL. Não se copia um banco aberto com `File.copyTo`.
- A restauração valida o arquivo antes da prévia, compara um fingerprint da base atual na confirmação e faz a substituição inteira em uma transação, com chaves estrangeiras ativas.
- A cópia anterior é gravada com `AtomicFile`, relida e comparada ao estado atual antes de modificar tabelas. Se uma inserção/verificação falhar, o SQLite desfaz as exclusões/inserções da transação. O arquivo ativo do banco não é apagado ou substituído.
- Nenhum `clearAllTables`, `deleteDatabase` ou migração destrutiva faz parte do serviço de produção. Exclusões de registros durante a restauração só ocorrem após a confirmação literal e dentro da transação protegida. Chamadas/notas continuam sendo salvas pelo fluxo atual.
- O diálogo aguarda a fila de gravações existente antes de habilitar suas ações. Alterações concorrentes concluídas depois da prévia são detectadas pelo fingerprint.
- Cópias internas não substituem backup externo: desinstalar o aplicativo ou perder o aparelho também elimina seus arquivos privados. Não são apagadas automaticamente nesta versão. A exportação oferece a mais recente; cópias antigas permanecem no diretório privado para suporte autorizado.
- Não há promessa de restauração entre esquemas/formato futuros desconhecidos; uma futura versão precisará implementar e testar a conversão explicitamente.

## Resgate de APK debug legado sem chave

Consulte [ASSINATURA.md](ASSINATURA.md) antes de instalar qualquer atualização. `scripts/rescue-debug-data.py` exporta localmente o banco de uma instalação depurável por ADB, sem instalar, desinstalar ou limpar o aparelho. Encerra o processo do app, copia banco/WAL/SHM para pasta nova privada, verifica SQLite/FKs/esquema e gera `resgate-diario.json` compatível com o importador desta versão. O procedimento só aceita a versão Room 1 conhecida; o arquivo exige conferência e confirmação no aplicativo de destino.

Não execute scripts de `scripts/ci/` em celulares: eles são exclusivos de emulador descartável e recusam execução fora do GitHub Actions/emulador. Os testes usam somente dados sintéticos e nunca versionam um banco pessoal.
