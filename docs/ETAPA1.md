# Etapa 1 — Registro de implementação e validação

Implementação restrita aos incrementos 1.1–1.3 do [ROADMAP](ROADMAP.md). Etapa 2 não iniciada. Estado dos testes remotos será preenchido após a execução.

## Alterações

- Esquema exato Room v1 versionado, sem alteração da versão/identidade das oito tabelas. Metadados do APK/certificado original registrados em `docs/baseline/`.
- Backup JSON completo, íntegro e offline; prévia, confirmação literal e fingerprint da base para impedir aplicação sobre alterações posteriores não conferidas.
- Cópia privada anterior obrigatória, validada antes de substituir dados; restauração transacional com FKs, conferência integral e rollback em erro; banco ativo não é apagado/trocado.
- Botão isolado Backup e diálogo do seletor Android. Cadastro, chamada, OCR, notas e navegação existentes continuam disponíveis. O retorno ao painel após restaurar evita campos de edição antigos.
- Testes de codec, oito tabelas, erros, rollback, persistência, esquema congelado, atualização de APK e resgate local, sempre com fixture fictícia.
- Versão 0.1.1/código 2; debug para teste e configuração de assinatura estável separada, bloqueada se secrets/fingerprint não estiverem configurados.

## Verificações locais

A suíte Java original com 33 cenários passou. A fixture sintética e sua criação SQLite foram verificadas com `integrity_check` e `foreign_key_check`. O ambiente local continua sem Android SDK/Gradle utilizável; compilação Kotlin/Android e testes instrumentados são executados no Actions.

## Impedimentos conhecidos

Não houve acesso ao APK instalado no celular nem à chave privada do debug original. Não há secrets de assinatura estável configurados por esta integração. Portanto, não se afirma atualização direta garantida do celular ou release estável pronto. [ASSINATURA.md](ASSINATURA.md) documenta a verificação, os limites e a alternativa de resgate local sem desinstalar a origem.

Detalhes de uso/formato em [BACKUP.md](BACKUP.md). Resultados finais e links da execução serão registrados após o CI.
