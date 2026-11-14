# P1 — recuperação automática pela UI em dois AVDs

Continuação de [HOMOLOGACAO_AUTOMATICA_NOTAS_0220.md](HOMOLOGACAO_AUTOMATICA_NOTAS_0220.md).
Somente preparação/local nesta entrega; P1 hospedada, P2 e P3 não autorizadas.

## Etapa 1 — APK paralelo atualizado

[Build #208 / 43632816764](https://github.com/italocesarfr-sketch/CHATGPTCODEX/actions/runs/43632816764),
`success`, fonte `6f3ae05f8ccdd918600f99c2ef5b6c6ee6d15474`, cujo código coincide
com o aprovado `b8206c3`. Workflow `homologacao.yml` via dispatcher existente,
sem login hospedado. [APK paralelo](https://github.com/italocesarfr-sketch/CHATGPTCODEX/actions/runs/43632816764/artifacts/14128872479).

- `br.com.diariodeclasse.homologacao`, NOcTuA Homologação.
- `0.22.0-dev-e2-homologacao` / código 45, preservados.
- SHA-256 APK `3aa87abdc60f2f42184ca9a9a90a5783d3c09e97d60ab8a9954cea191252bc35`.
- Certificado Debug `8e9b7b97b14914182d42a6be2e1ed0f32a92a48926cbc94288a983ae9e2676a5`.
- Assinatura/AAPT/DEX/relatório de identidade conferidos offline. Endpoint exclusivo
  `sscexzxtqdcsqdlcytum`, callback próprio; novas funções presentes e marcadores
  privados/produção ausentes. O vínculo da chave opaca não foi consultado online.
- Certificado diferente do #206: **não atualiza aquela instalação**. Nenhum APK
  foi instalado nesta etapa; usar somente AVDs novos, jamais limpar/desinstalar
  aplicações existentes para contornar incompatibilidade.
- JVM Debug: 313 aprovados, cinco condicionais ignorados; Homologação: os mesmos
  313 aprovados/cinco ignorados, zero falhas. Não somar como 626 casos distintos.

[Identidade e hashes estruturados](evidence/APK_P1_208.json).

## Etapas 2/3 — executor

Implementação/validação local em andamento. Resultados devem ser consolidados
antes de declarar P1 pronta para autorização. Não reutilizar H05 manual como
prova de startup automático nem autorizações anteriores como consentimento P1.
