# 0.22.0 — fase B: integração Android

Base `1a366ffd68d3`, mesma branch `sync/academic-complete-0220`.

## Plano de implementação

1. Migração Room 11→12 aditiva, apenas metadados `sync_academic_*`. Gatilhos
   nas três tabelas capturam inclusão/alteração/exclusão na transação acadêmica.
   Pedidos preparados conservam UUID, corpo e contadores até recibo confirmado.
2. Transporte HTTPS para as duas RPCs existentes e negociação de protocolo.
   Nova migração SQL protege v1/v2 sob o mesmo bloqueio por proprietário, compara
   o inventário v1 e impede novos escritores antigos depois de transição explícita.
3. Recebimento por lote inteiro, revisão da fase A, validação do grafo completo e
   regras de identidade da matrícula/ano/número. Não restaurar banco inteiro.
   Lote incompatível permanece em inbox com cursor parado e variantes preservadas.
4. Coordenador único por workspace: alterações locais, foreground, reconexão e
   JobScheduler persistente. Retry transitório com backoff e sem reiniciar app.
5. Controle experimental separado. Não ativar contas legadas com dados reais,
   não apagar conflitos v1, não remover proteção de backup nem publicar release.
6. Evidências: testes Room e migração, testes PostgreSQL de barreira/isolamento,
   transporte Android com PostgREST/PostgreSQL reais em ambiente descartável,
   recuperação depois de limpeza **somente do emulador sintético do CI**, regressão.

O endpoint de produção continua fixo HTTPS/Supabase e chave pública. Um endpoint
HTTP é permitido exclusivamente no debug para o host fixo 10.0.2.2 do emulador,
com JWTs fictícios assinados para o PostgREST descartável. Isso não é validação do
Supabase hospedado nem confirmação de duas sessões Supabase reais.

## Ordem futura de SQL hospedado (não executada automaticamente)

Já instalados: `202610300001_class_sync.sql`, depois
`202610310001_sync_session_guard.sql`. Após revisão/aprovação, aplicar somente os
novos arquivos em ordem: `202611010001_academic_protocol.sql`, seguido da migração
da fase B. Nunca reaplicar v1/guard sobre as funções protegidas mais recentes.

Resultados, hashes e limitações serão consolidados depois da execução dos testes.
