# 0.22.0 — fase E: consolidação experimental

Base: branch `sync/academic-complete-0220`, HEAD documental `25c61e7`, código D
`9717146b6b5c63ac9aa560f57ac778efed654858`, CI `41527729515` aprovado (tentativa 2).
Working tree inicial: apenas `.agents/` não versionado; preservado. Main intacta.

## Plano incremental e critérios

1. **E1 — transporte paginado durável.** Reutilizar batches, versões, recibos,
   validadores D e fila Room. Novos RPCs de escopo E para enviar partes limitadas e
   confirmar um lote inteiro; receber partes imutáveis em armazenamento Room de
   estágio e só aplicar o grafo completo. Migração local aditiva para metadados,
   nunca alteração destrutiva das quinze tabelas acadêmicas. Cursor acadêmico
   permanece anterior enquanto o lote não for integralmente validado/aplicado.
2. **E2 — decisão explícita.** Revisão local/remota por campo e conjunto atômico,
   decisão com evidências persistentes e CAS remoto. Conservar conflito se mudar
   qualquer versão. Não oferecer sobrescrita unilateral de cadeia/auditoria.
3. **E3 — restauração.** Manter pausa RESTORE, comparar estado restaurado com remoto,
   reaproveitar a revisão/decisão E2; nunca tratar backup antigo como autoritativo.
4. **E4/E5 — continuidade/segurança.** Mesmo mutex, coordenador, JobScheduler e
   isolamento; PostgreSQL/PostgREST descartáveis, sessões fictícias, testes negativos.
5. **E6/E7 — compatibilidade/regressão.** Esquemas anteriores, backup criptografado,
   referência de atualização com chave descartável e dois AVDs. Preservar toda
   regressão A–D e leitores independentes de planilhas.

Cada marco deve registrar implementação, resultados efetivos, commits e limites.
Não declarar E concluída com base em testes de codec/SQL sem transporte Android.

## Segurança e implantação

- Nada aplicado ao Supabase hospedado. Migrações A/B/C/D continuam dependentes de
  revisão administrativa, seguidas das migrações E que forem efetivamente criadas.
- Consentimento experimental e allowlist permanecem; nenhuma conta real é ativada
  por atualizar o APK. O conflito legado do proprietário não é resolvido.
- Manter Room operacional, IDs/titularidade, package, assinatura e criptografia.
- Não instalar no aparelho principal, não publicar release nem promover main.
- Atualização original 0.21.0/c40 com certificado permanente, duas contas reais
  hospedadas e homologação física continuam pendentes até execução autorizada.

## Estado desta execução

Inspeção inicial confirmou a base D e sua CI aprovada. E1–E7 ainda não aprovadas.
Registros grandes exigirão limites explícitos por parte e por conjunto atômico;
ultrapassar um limite deve bloquear e preservar dados, nunca truncar/dividir cadeia.

## Contingência

Pausar o teste, preservar bancos, outbox/inbox, partes, recibos e evidências.
Corrigir aditivamente; não rebaixar o escopo, limpar filas ou reaplicar SQL antigo.
Não confundir sincronização com backup administrativo independente.
