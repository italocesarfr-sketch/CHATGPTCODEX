# H12 — backup antigo e reconciliação explícita

Preparação em **13/11/2026 (America/Sao_Paulo)**, branch
`sync/academic-complete-0220`, a partir de `6cc7bef`.
**H12 hospedado não foi autorizado nem executado nesta preparação.**

## Implementação e escopo

Reutiliza `AccountBackup` (AES-GCM/PBKDF2), `BackupManager`, Room 14,
`AcademicReconciliation`, a engine E, recibos, inbox/outbox, revisões e cursores
existentes. Nenhum arquivo de produção, migração SQL, regra de remanejamento,
formato 12 de backup, criptografia, assinatura ou versão foi alterado.

`H12RestoreProbeTest` usa o mesmo startup controlado de H05, com argumento e
consentimento **H12 independentes**, sem iniciar jobs normais antes da revisão.
O inventário paginado limitado foi extraído de H05 para `RecoveryInventory`;
ambas as provas usam esse leitor. O runner Python H05 foi parametrizado com
`--case H12`, preservando os padrões anteriores e evitando outra infraestrutura
para Secrets, ADB, limpeza e relatórios.

### Prova descartável LOCAL

1. Novo AVD da CI, sem sessão/Room anteriores. Auth estritamente fictício e
   PostgreSQL 17/PostgREST local, com políticas e protocolo A–E reais.
2. Recupera o fixture existente de 171 registros, 15 tipos e 37 matrículas.
3. Cria backup cifrado verdadeiro pelo gerenciador existente. Tenta abri-lo
   com a identidade B: deve recusar e manter o banco inalterado.
4. Depois do backup, o peer HTTP descartável atualiza uma nota, um diário e
   acrescenta uma matrícula. Não utiliza o Supabase para produzir esses dados.
5. Restaura o backup antigo somente no workspace descartável. Exige `RESTORE`,
   sync inativa, cursor preservado e cópia de segurança validada. Duas chamadas
   da engine não podem escolher uma versão nem alterar a nuvem por conta própria.
6. Obtém prévia de versões antigas/remotas, incluindo registro ausente do backup
   e alterações de campos. Confirma explicitamente `KEEP_REMOTE`. A proposta
   precisa ter **zero alterações acadêmicas remotas**.
7. Perde intencionalmente a resposta de uma decisão aceita no servidor local;
   fecha/reabre Room e retoma pelo mesmo recibo, sem uma segunda escrita da decisão.
8. Exige igualdade de IDs/payloads/hashes por tipo, bases e revisões, cursor e
   recibos dos lotes, FKs, auditoria local `APPLIED`, filas vazias, ausência de
   duplicação e inventário acadêmico remoto exatamente igual ao anterior à restauração.

A prova adicional inclui os dez testes Room existentes de
`PhaseEReconciliationTest` (rollback, revalidação local/remota, migração 13→14,
backup em banco independente, escolhas explícitas e resposta perdida), além de
três testes de guardas H12. É reabertura do Room, **não** reboot/morte forçada
real do processo. Essa distinção não é substituída por uma afirmação de resiliência total.

### Futuro HOSTED — somente após autorização própria

Apenas `br.com.diariodeclasse.homologacao`, endpoint fixo
`https://sscexzxtqdcsqdlcytum.supabase.co`, conta fictícia A do Environment
`noctua-homologacao-h01-h04` e um novo AVD da CI. Reutiliza os dois Secrets
nominais existentes; não usa `secrets: inherit`. B fornece apenas o identificador
privado usado na rejeição de titularidade do backup; não abre sessão de B.

Não cria registros nem alterações acadêmicas para fabricar um backup antigo.
Lê páginas já confirmadas do histórico remoto e procura uma revisão anterior,
com grafo válido, fixtureClassId conhecida, ao menos um registro posterior e
uma diferença de payload. Em Room temporário novo, produz um backup cifrado
representando essa revisão e o restaura no workspace descartável recuperado.
O relatório informa `historical_backup_reconstructed=true`: é uma reconstrução
fiel de revisão comprometida no servidor, **não** um arquivo antigo fornecido
pelo proprietário. Se não existir base segura, bloqueia com
`H12_NO_SAFE_OLD_BASELINE`, sem aprovação nem criação de dados na nuvem.

A escolha será exclusivamente **manter nuvem**, após revisão explícita, com
`hasChanges=false`. Ainda assim, `resolveE` **grava uma auditoria/recibo de decisão
no servidor**. Portanto, a futura autorização precisa abranger essa escrita de
metadados; a autorização somente leitura do H05 não basta. O guard H12 permite
somente a requisição congelada e sua operação exata, uma única vez. Bloqueia
staging, push, tombstones, promoção de escopos, `KEEP_LOCAL`, cancelamento e
qualquer decisão com mudanças acadêmicas. Revalidação de versões continua no
servidor. O head acadêmico e todas as versões devem permanecer iguais.

Sem injeção de falhas hospedadas, carga, contas novas, SQL ou alteração de RLS.
Se outro dispositivo editar a conta durante o ensaio, o teste não aprova.

## Limites e evidências

Limites herdados de H05: 1.000 registros vivos/por lote, 4.096 versões e 8 MiB
por inventário, 128 páginas/inventário, 256 lotes, até cinco minutos e 512
leituras no transporte. Usa volume pequeno; não executa 5.171 registros.

Artefato único: `build/h12/result.json`, estritamente validado antes de publicar.
Só contém contagens, hashes agregados, tempos, códigos/estágios fixos e flags.
Sem e-mails, UUIDs, nomes, notas individuais, tokens, respostas privadas,
bancos, backups, screenshots/logcat ou APKs. Entrada temporária 0600 e diretório
0700 fora do checkout; transferência ao AVD por stdin e remoção ao final.
Nenhum dispositivo preexistente pode ser selecionado: exige runner GitHub,
um único emulator-5554 e nome AVD exclusivo do run/attempt.

O conjunto **H05 hospedado #201** continha **24 registros / nove tipos**.
Os seis tipos ausentes eram `recompositions`, `import_drafts`, `export_rows`,
`class_archives`, `preserved_histories` e `weekly_timetables`.
A cobertura LOCAL dos 15 tipos **não aprova esses seis no ambiente hospedado**.
O futuro H12 informará as contagens presentes/zero reais; não os semeará.

## Validações desta preparação

- Python offline: 16 testes do executor Auth + 15 H05 + 14 H12 = **45 aprovados**.
- JVM debug reexecutada: **298 casos, 293 aprovados, cinco condicionais ignorados,
  zero falhas/erros**. Os condicionais reais de Auth não são aprovados por esta rodada.
- Compilação Kotlin da instrumentação debug: aprovada.
- `actionlint` 1.7.7 (sem ShellCheck/pyflakes), py_compile e `git diff --check`: aprovados.
- Prova nova Android/PostgREST/PostgreSQL: **aguardando execução LOCAL da CI**.
- Homologação H12 hospedada: **não executada; depende de autorização posterior**.

As contagens e os links da prova LOCAL serão consolidados após o run, sem
transformar condicionais/steps não executados em aprovações.

## Procedimento mínimo

Não exige novos Secrets, migrações nem manipulação dos emuladores do proprietário.

```bash
# Pré-requisitos de formato somente, sem Supabase/AVD:
gh workflow run android.yml --repo italocesarfr-sketch/CHATGPTCODEX \
 --ref sync/academic-complete-0220 -f h12_only=true -f h12_mode=PREFLIGHT

# Simulação completa descartável, sem Secrets/Environment ou Supabase:
gh workflow run android.yml --repo italocesarfr-sketch/CHATGPTCODEX \
 --ref sync/academic-complete-0220 -f h12_only=true -f h12_mode=LOCAL
```

Para o futuro ensaio hospedado, após aprovação das evidências locais:

1. Autorizar explicitamente login A, leituras status/pull E, restauração somente
   no novo AVD e **uma auditoria RESTORE/KEEP_REMOTE sem alterações acadêmicas**,
   consulta do recibo e logout da sessão criada, exclusivamente em homologação.
2. Disparar `android.yml` na branch experimental com `h12_only=true`,
   `h12_mode=HOSTED`, `authorize_hosted_h12=true`; aprovar o Environment, se solicitado.
3. Não editar os dados fictícios dessa conta durante o ensaio. Conferir resultado
   sanitizado e tipos realmente presentes. Não repetir automaticamente uma falha.

Sem autorização dessa auditoria, é possível apenas pré-verificar e ler a prévia;
não declarar H12 integralmente aprovado. Não instalar esta instrumentação sobre
qualquer aplicativo existente nem anunciar release estável.
