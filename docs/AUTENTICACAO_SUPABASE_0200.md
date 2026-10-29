# Autenticação real — candidato 0.20.0 / código 38

Estado: implementação em validação na branch `auth/supabase-protected-workspaces`.
A release estável permanece 0.19.1. Este documento não atesta liberação para uso real.

## Arquitetura e limites

O serviço `noctua-auth` é utilizado exclusivamente pelo GoTrue HTTPS (`/auth/v1`),
com a URL e Publishable Key públicas fornecidas pelo proprietário. Não há acesso
acadêmico ao Supabase, chave administrativa, senha armazenada, logger HTTP nem
sincronização. `SupabaseAuthClient` usa HTTPS nativo, limites de resposta/tempo,
identidade confirmada por GET `/user` e mensagens locais sem respostas sensíveis.
Nenhuma dependência nova. Android 8+, Compose/Material 3 e arquitetura acadêmica
preservados. Room **v10**, migrações e backup acadêmico **formato 12** inalterados.

`AuthController` serializa operações; uma geração invalida respostas atrasadas
após logout ou novas credenciais. Recuperação não admite acesso acadêmico.
`AuthRoot` só cria ViewModels/Room após autenticação e seleção do espaço autorizado;
separa seus ViewModelStores por conta e mantém o mesmo store na rotação.
`DiaryApplication` não abre a base escolar na inicialização não autenticada.
Os clientes e identidades fictícios de regressão existem somente no APK separado
de instrumentação. O APK de produção não oferece entrada demonstrativa.

## Cadastro, entrada e recuperação

- Cadastro: nome, e-mail, senha mínima de oito caracteres e confirmação; solicitação
  real ao Supabase, seguida de confirmação por e-mail. Cadastro não abre o diário.
- Entrada: campos obrigatórios; senha inválida, identidade não confirmada ou anônima
  não admitem acesso. GET `/user` verifica a identidade do token obtido.
- Recuperação: solicitação real, PKCE S256 e retorno
  `br.com.diariodeclasse://auth/callback?state=...&code=...`.
  A aplicação só aceita esquema/host/caminho exatos, estado aleatório próprio,
  parâmetros únicos, código válido e prazo de 30 minutos. Verificador cifrado
  neste aparelho; sem tokens implícitos, redirecionamento arbitrário ou logs.
- Após o link, a sessão de recuperação fica em memória e só permite trocar senha.
  Depois disso é necessário entrar novamente. Se o processo morrer durante essa
  etapa, solicitar novo link; não existe desvio offline.
- Logout: remove imediatamente a admissão local e tokens, tenta revogar a sessão
  remotamente e fecha o espaço acadêmico. Falta de rede não restaura a admissão.
  Não apaga dados escolares nem a titularidade já confirmada.

O proprietário informou a inclusão do retorno na lista do Supabase. Essa informação
não equivale a inspeção administrativa nem prova de ida e volta no Android.
Custom schemes podem ser interceptados por outro app; PKCE protege a troca do
código. Links HTTPS verificados exigiriam domínio próprio e não foram inventados.

## Sessão offline

Sessão, refresh token, estado PKCE e titularidade usam Android Keystore AES-GCM,
AtomicFile e diretório privado `noBackupFilesDir`. A titularidade possui arquivo
cifrado separado, preservado no logout. Corrupção/perda da chave bloqueia acesso;
nenhum fallback limpa ou recria dados. Auto Backup Android permanece desativado.

Após autenticação online confirmada, admite-se uso da mesma conta por **até sete
dias desde a última verificação online**, mesmo sem rede. A aplicação revalida na
entrada, no retorno e periodicamente; renova tokens próximos de expirar ou após
15 minutos sem verificação. Erros temporários de rede/servidor não se confundem
com credenciais inválidas. Sessão revogada é recusada quando percebida online.
Relógio voltando além da tolerância ou prazo esgotado bloqueia até revalidar.
A política depende do relógio do aparelho; não é relógio seguro de hardware.
Revogação remota não é detectável imediatamente sem conexão.

## Dados antigos e isolamento

A conta do proprietário foi identificada por UUID imutável retornado no login real
confirmado; o e-mail não é uma regra editável de titularidade. Outra conta abre
somente uma base vazia própria. Não pode vincular nem consultar `diario.db` antigo.

A primeira vinculação do proprietário exige cumulativamente:

1. Conta autorizada confirmada e sessão válida.
2. Consentimento explícito identificando o e-mail verificado.
3. PIN/senha do bloqueio Android confirmado pelo próprio sistema.
4. Escolha de destino e senha de backup com pelo menos 12 caracteres.
5. Gravação completa, releitura, decifragem e comparação exata do backup e do
   snapshot original, antes de gravar atomicamente a titularidade.

Sem bloqueio Android, cancelamento, erro de arquivo ou troca de identidade, a
vinculação não é concluída. Os IDs e linhas originais não são copiados, renumerados
ou reassociados. Para o proprietário o caminho continua `databases/diario.db`.
Demais contas usam `databases/accounts/<SHA-256 do UUID>/diario.db`; seleção de
ano/turma, cópias de segurança e cache também são separados. Ao trocar de conta,
a fila de escritas anterior termina antes de fechar sua base. Nenhuma conta usa
os ViewModels ou cópias de segurança da anterior.

Compartilhamentos internos ficam em cache por conta e o FileProvider revalida a
conta atual em consulta e abertura. URIs de outra conta ou após logout são
recusados. A área de transferência do Noctua é limpa ao bloquear/sair. Arquivos
já compartilhados/salvos fora do aplicativo e backups legados em texto não podem
ser recolhidos ou protegidos retroativamente; o professor deve guardá-los.

## Backup portátil protegido

Os dados acadêmicos continuam sendo serializados pelo BackupCodec 12 existente,
mas o arquivo externo novo usa contêiner binário versionado 1, extensão `.noctua`:

- assinatura `NOCTUA-ACCOUNT-BACKUP-1\n`, tamanho do cabeçalho e JSON pequeno;
- UUID proprietário, versão, sal aleatório e IV no cabeçalho;
- AES-256-GCM, PBKDF2-HMAC-SHA256 com 600.000 iterações, sal de 16 bytes;
- cabeçalho autenticado como AAD e ciphertext bruto (sem cópias base64 do payload);
- entrada limitada a 33 MiB; payload acadêmico limitado a 32 MiB.

A senha não é persistida e não é a senha da conta. É necessária na restauração,
inclusive em outro aparelho com a mesma conta. **Senha de backup esquecida não
pode ser recuperada.** Conta diferente, senha incorreta e arquivo adulterado são
rejeitados antes de restaurar. Backups acadêmicos antigos continuam legíveis
somente pela conta proprietária, com autorização do bloqueio Android; confirmação
`RESTAURAR` e cópia de segurança anteriores permanecem. O fechamento/flush do
arquivo precisa ter êxito antes da mensagem de sucesso.

## Evidência real já obtida (sem dados de alunos)

Uma conta de teste autorizada, que também é a conta indicada para titularidade,
foi cadastrada realmente, sem criar contas do piloto. O serviço aceitou cadastro;
antes da confirmação recusou login com `email_not_confirmed`. Após o proprietário
abrir o e-mail, login, GET `/user`, renovação e alteração autenticada de senha
foram aceitos. Senha incorreta foi recusada. O cliente Kotlin real também executou
login, verificação, refresh, logout e rejeição do refresh revogado.

A recuperação PKCE foi aceita pelo serviço e o proprietário confirmou o recebimento
da mensagem. **Troca do código pelo app instalado e definição de senha pelo link
continuam pendentes de validação real.** Senha temporária automatizada, tokens e
verificadores não constam do código, documentos, logs ou artefatos. Não excluir nem
recriar a conta autorizada: sua identidade é vinculada ao UUID verificado.

O SMTP personalizado/Resend e domínio de envio ainda não estão configurados.
Entrega ao endereço autorizado não comprova entrega a qualquer destinatário.
Não desativamos confirmação de e-mail ou outras proteções para aprovar testes.

## Validação automatizada

O CI deve executar JVM completa; regressão Android; isolamento entre duas contas;
vinculação com backup verificado; Keystore/corrupção; negação de URI cruzada;
formulários e fontes nativas 1,3/1,6; reinício real do emulador com sessão offline;
XLSX/CSV/TSV com leitores independentes; e atualização sobre o APK **original
0.19.1 / código 37** com assinatura permanente e snapshots escolares exatos.

Os testes de UI/isolamento usam clientes injetados exclusivamente na instrumentação;
não são alegados como prova do backend real. O teste JVM online é opt-in, executado
separadamente com arquivo privado e nunca com senha em variáveis de build ou APK.
A suíte local executou 204 casos: 203 aprovados e o teste online opt-in ignorado.
O opt-in foi executado separadamente e passou com o cliente real; resultado
sanitarizado em `evidencias/auth-0200/kotlin-live-result.json`. Um leitor Python
independente validou decifragem do contêiner, senhas ASCII/Unicode, preservação do
texto e rejeição de adulteração do titular. Os números Android e links finais
serão registrados após consultar o resultado real do CI.

## Primeiro acesso seguro (somente após homologação)

Antes de instalar, guarde backup da 0.19.1 estável em local seguro. Não desinstale
nem limpe dados. No candidato instalado, use **Esqueci minha senha** para receber
um link novo gerado pelo próprio aparelho e definir sua senha pessoal. O link da
sondagem automatizada anterior não possui o verificador local do app.

Entre com a conta autorizada confirmada. Configure o bloqueio Android se necessário,
confirme a vinculação, salve e guarde o backup protegido e sua senha. Só depois
os registros antigos são disponibilizados à conta. Um usuário diferente não pode
assumi-los. O logout guarda o banco; para voltar aos dados é necessário entrar na
mesma conta. Erro de proteção exige diagnóstico/backup, nunca reinstalação.

Impeccable 4.7 foi consultada em `.agents/skills/impeccable`: contexto Android,
robustez, campos, áreas de toque, rolagem, estados de falha e preservação da marca.
Não foram usadas skills de planilha, PDF ou documentos externos.

### Primeira rodada Android e correção

A execução [40804656462](https://github.com/italocesarfr-sketch/CHATGPTCODEX/actions/runs/40804656462)
aprovou os 11 testes focados de autenticação/isolamento e os formulários nas fontes
nativas 1,3/1,6. A regressão revelou um defeito real: o ViewModelStoreOwner por conta
não fornecia AndroidViewModelFactory, causando tentativa de construtor sem Application
em DiaryViewModel. O owner passou a implementar HasDefaultViewModelProviderFactory
com a fábrica Android, sem recolocar modelos acadêmicos no store global. Um teste
JVM garante o uso da fábrica com dependência e a retenção do mesmo modelo. A nova
rodada permanece obrigatória; a aprovação focada não foi tratada como release pronta.
