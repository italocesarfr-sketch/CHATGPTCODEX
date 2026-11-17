# Produção 0.22.0 — Fase 2: implantação transacional A–E

**Registro histórico do procedimento preparado; execução não realizada pelo Codex.**
Branch: `temp/academic-production-phase1`. Código/SQL de referência:
`ac56867ef3c6093670fafd91ddd4f591bc589f8a`.

**Atualização informada pelo proprietário em 17/11/2026:** pacote A–E implantado
em `mpsgdchpgyxoqowfjoqm`, 16 tabelas com RLS e auditorias aprovadas; conta fictícia
individual confirmada e autorizada. Não se executou consulta independente nesta
preparação. **Não executar novamente o roteiro SQL abaixo.** Continuidade:
[Fase 3 — debug em AVDs novos](PRODUCAO_FASE3_DEBUG_0220.md).

Nesta continuação, Fase 2 significa implantação do pacote SQL. Não confundir com o
item 2 antigo de `PRODUCAO_FASE1_0220.md`, que descreve liberar uma conta fictícia.
Allowlist, teste de conta e candidato assinado permanecem operações separadas.

## 1. Pré-condições imediatas

- Fase 1 consolidada em [PRODUCAO_FASE1_0220.md](PRODUCAO_FASE1_0220.md): backup e
  ensaio de restauração dos dados confirmados pelo proprietário. Ownership, ACL e
  configurações externas não têm restauração comprovada.
- Confirmar projeto aberto com o proprietário **antes de cada execução**:
  `noctua-auth`, referência **`mpsgdchpgyxoqowfjoqm`**. O projeto
  `sscexzxtqdcsqdlcytum` não recebe nenhuma operação desta implantação.
- Confirmar backup acessível e suficientemente atual para a janela, com catálogo,
  definições e evidências privadas das permissões v1. Não solicitar dump, credenciais
  ou configurações secretas pelo chat. Não presumir que `--no-owner --no-acl`
  permite reconstruir essas permissões sozinho.
- Confirmar baseline v1 + guarda sem objetos acadêmicos v2/parciais e sem mudanças
  desde a conferência. Não repetir checagens já feitas se o estado segue atual.
  Havendo mudança ou dúvida, executar pelo administrador **somente** o inventário
  existente `supabase/checks/prehomo_0220_readonly.sql`; nenhuma consulta adicional
  é necessária. Não invoca RPCs e não lê linhas acadêmicas/Auth.
- Qualquer divergência bloqueia o pacote padrão: parar sem tentar corrigir ou
  reaplicar v1/guarda. Escolher janela administrativa e registrar limites efetivos;
  não aumentar timeout ou mudar configurações de produção para contornar falhas.

Pacote local já pronto (não regenerar nem sobrescrever por rotina):

```text
build/producao-0220.sql
120010 bytes / 1504 linhas
SHA-256 7fd28b6dbce5f72724abc1a2644041f19f373fc7675eb37a24bd17fa9a46659b
```

O [SQL versionado para revisão](homologacao/0220/homologacao-0220.sql) é idêntico,
byte a byte. Conferir o hash do arquivo que será usado no SQL Editor, sem alterar
seu conteúdo. Sete fontes na ordem A, B, C, D, E1, E2/E3, E4; **não** inclui H13
nem PT409. Fontes/hashes no [manifesto](evidence/PRODUCAO_PACOTE_0220.json).

## 2. Autorização e execução única

Texto que o proprietário poderá usar **em nova autorização**, após as confirmações:

> Confirmo o projeto de produção noctua-auth, mpsgdchpgyxoqowfjoqm, o baseline
> v1 + guarda sem objetos v2 e a disponibilidade/atualidade do backup e dos
> registros de recuperação. Autorizo somente a execução integral de
> build/producao-0220.sql, SHA-256
> 7fd28b6dbce5f72724abc1a2644041f19f373fc7675eb37a24bd17fa9a46659b,
> em uma única sessão do SQL Editor, seguida das conferências somente leitura.
> Não autorizo allowlist, contas, alterações acadêmicas, APK ou assinatura.

Após essa autorização, o administrador cola/executa o arquivo **inteiro**, uma vez,
no SQL Editor do projeto confirmado. Sem seleções parciais, commits intermediários,
fontes separados ou reaplicação dos scripts v1/guarda. O pacote contém:

1. `BEGIN;`, `SET LOCAL lock_timeout='5s'` e preflight do baseline.
2. Sete migrações com corpos originais, na ordem revisada.
3. Notificação de recarga do schema PostgREST e um único `COMMIT;` final.

Não há chamada de ativação nem inscrição automática de contas/dados. A fase B
altera wrappers e privilégios de RPCs globais: a ausência de upload automático
não significa ausência de efeito sobre as definições da API.

Qualquer erro antes do commit impede confirmação integral da transação. Se a
resposta for perdida ou houver timeout, o resultado é **incerto até conferir o
catálogo**. Não clicar novamente em executar; seguir a contingência abaixo.

## 3. Conferência posterior

Confirmar de novo o projeto antes de cada execução. Depois de sucesso inequívoco:

1. Repetir `supabase/checks/prehomo_0220_readonly.sql`.
2. Executar `supabase/checks/phase_e_readonly.sql`.
3. Conferir e guardar somente resultados sanitizados de catálogo:

| Conferência | Esperado |
| --- | --- |
| Tabelas NOcTuA conhecidas | **16**: quatro v1 + doze A–E |
| RLS e políticas | 16 tabelas com RLS e políticas SELECT por proprietário; sem políticas permissivas adicionais |
| Privilégios das tabelas | `anonymous_read=false`, `direct_write=false`; SELECT authenticated conforme política |
| Funções públicas e privadas | RPCs autorizadas somente conforme manifesto; helpers privados sem EXECUTE de clientes; search_path e SECURITY DEFINER conforme fontes |
| Guarda de sessão | Preservada; consulta sessão, confirmação e permissão do owner para inspecionar sessões |
| Escopo | CHECK final aceita 2, 3, 4 e 5; não foi autorizado nenhum usuário por este pacote |
| Conferência E | Três tabelas E, respectivas ACL/políticas e funções/guardas correspondem aos fontes |

Nenhum teste com contas, consulta de dados pessoais, leitura de conteúdo Auth ou
fixture integra esta conferência. Catálogo administrativo **não comprova** isolamento
real de duas contas em produção. Testes hospedados de homologação anteriores não
substituem a futura verificação de uso no projeto de produção.

Qualquer diferença bloqueia a declaração de implantação conferida. Não executar
smoke tests que escrevam, ativem contas ou criem auditorias para tentar diagnosticar.

## 4. Contingência

| Situação | Conduta |
| --- | --- |
| Erro/timeout/resposta incerta | Parar; não repetir. Confirmar projeto e executar somente o inventário existente |
| Quatro tabelas v1, nenhum objeto v2 e definições v1 preservadas | Estado compatível com rollback; registrar resultado. Nova tentativa exige diagnóstico e autorização |
| Dezesseis tabelas e resultado coerente | Commit possivelmente concluído; fazer toda a conferência posterior antes de concluir |
| Objetos parciais, privilégios divergentes ou resultado ambíguo | Interromper; guardar catálogo sanitizado; não reparar automaticamente |
| Problema demonstrado após commit | Preservar objetos, filas/recibos e dados; planejar correção específica. Não derrubar A–E nem reinstalar v1/guarda |
| Restauração remota considerada necessária | Autorização administrativa separada, janela, análise das gravações após o backup e plano de recomposição de ownership/ACL/configurações. Não restaurar automaticamente |

Backup de referência informado: 426.467 bytes, SHA-256
`20f89b35813718d9a732cbc73c29767da74aef80cae4c54610a90c93a6784877`,
criado em 15/11/2026, cópia 7z AES-256 testada e externa. O ensaio local comprovou
as seis contagens relatadas, não a restauração de ownership/ACL. Manter os registros
de segurança do baseline junto ao material de recuperação, em armazenamento privado.
Não alterar Auth, revogar sessões globalmente ou sacrificar dados posteriores para
forçar a volta ao backup. O aplicativo e seus backups cifrados permanecem intactos.

## 5. Limite desta aprovação e continuidade do aplicativo

O risco de capacidade permanece: referência de oito segundos da API autenticada e
benchmark local histórico de 8,647 segundos para lote grande. Nenhuma carga ou ajuste
de timeout será executado na produção. É limite para uso/capacidade posterior,
não evidência de que o DDL vá falhar nem motivo para integrar candidatas H13/PT409.

Somente depois da implantação conferida, preparar autorização separada para uma
conta fictícia e seu consentimento legítimo até E. O caminho de assinatura,
propriedade `noctuaAcademicProduction`, ensaio do candidato oficial e migração do
workspace legado continuam fora desta tarefa. Não remover as exclusões da conta
proprietária nem resolver automaticamente seu conflito v1.

Nenhuma promoção para main, release, APK ou instalação é autorizada por este roteiro.
