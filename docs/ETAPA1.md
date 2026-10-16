# Etapa 1 — Registro de implementação e validação

Implementação restrita aos incrementos 1.1–1.3 do [ROADMAP](ROADMAP.md). Etapa 2 não iniciada. Código validado: `ee46413ecb92839bf24a358fc4f2d04794300f1f`. A execução final passou; a distribuição com assinatura estável continua dependendo da configuração externa descrita abaixo.

## Alterações

- Esquema exato Room v1 versionado, sem alteração da versão/identidade das oito tabelas. Metadados do APK/certificado original registrados em `docs/baseline/`.
- Backup JSON completo, íntegro e offline; prévia, confirmação literal e fingerprint da base para impedir aplicação sobre alterações posteriores não conferidas.
- Cópia privada anterior obrigatória, validada antes de substituir dados; restauração transacional com FKs, conferência integral e rollback em erro; banco ativo não é apagado/trocado.
- Botão isolado Backup e diálogo do seletor Android. Cadastro, chamada, OCR, notas e navegação existentes continuam disponíveis. O retorno ao painel após restaurar evita campos de edição antigos.
- Build e testes de atualização no mesmo runner: o SHA-256 confirma que o APK disponibilizado é o mesmo testado, sem compartilhar chave privada entre jobs.
- Testes de codec, oito tabelas, erros, rollback, persistência, esquema congelado, atualização de APK e resgate local, sempre com fixture fictícia.
- Versão 0.1.1/código 2; debug para teste e configuração de assinatura estável separada, bloqueada se secrets/fingerprint não estiverem configurados.

## Verificações locais

A suíte Java original com 33 cenários passou. A fixture sintética e sua criação SQLite foram verificadas com `integrity_check` e `foreign_key_check`. O ambiente local continua sem Android SDK/Gradle utilizável; compilação Kotlin/Android e testes instrumentados são executados no Actions.

## Impedimentos conhecidos

Não houve acesso ao APK instalado no celular nem à chave privada do debug original. Não há secrets de assinatura estável configurados por esta integração. Portanto, não se afirma atualização direta garantida do celular ou release estável pronto. [ASSINATURA.md](ASSINATURA.md) documenta a verificação, os limites e a alternativa de resgate local sem desinstalar a origem.

## Resultados comprovados no GitHub Actions

[Execução 38947014687](https://github.com/italocesarfr-sketch/CHATGPTCODEX/actions/runs/38947014687) concluída com **sucesso** em 16/10/2026:

| Verificação | Resultado |
| --- | --- |
| `testDebugUnitTest assembleDebug assembleDebugAndroidTest` | Sucesso |
| Testes JVM | 8 aprovados: suíte dos 33 cenários anteriores e 7 testes do codec de backup |
| Instrumentados Android 11/API 30 | 19 aprovados, nenhum com falha |
| Sonda exclusiva da atualização na suíte comum | 1 ignorada intencionalmente; executada separadamente, com sucesso, antes e depois da atualização |
| Esquema Room v1 após KSP | Idêntico ao congelado; nenhuma migração necessária |
| Release sem chave externa | Recusado pelo Gradle, conforme esperado |
| Backup/restauração e cópia anterior | Comparação integral das oito tabelas e reabertura aprovadas |
| Falha no meio da restauração | Rollback preservou todas as tabelas e a cópia anterior |
| Falha ao salvar cópia, arquivo inválido e base alterada após prévia | Restauração bloqueada; origem preservada |
| Datas de modificação iguais entre cópias | Seleção por índice atômico aprovada |
| Abertura/cancelamento do diálogo | Sem alteração do banco |
| APK antigo recompilado v1 → candidato v2, mesmo certificado de teste | `adb install -r` e comparação de cada campo das oito tabelas aprovados; nenhuma desinstalação entre as versões |
| Resgate local por ADB, dados fictícios | SQLite íntegro e JSON idêntico à fixture completa |
| APK publicado | SHA-256 permaneceu igual antes/depois dos testes; assinatura v2 verificada |

O teste inicial encontrou uma falha de seleção da cópia anterior quando duas cópias tinham o mesmo `mtime`. Ela foi corrigida com índice `AtomicFile` e regressão determinística; não se enfraqueceu o teste nem se removeu a verificação de rollback. A dependência BOM dos testes Compose também foi corrigida.

## Artefatos verificados

- [APK debug de teste + SHA-256 + relatório de compatibilidade](https://github.com/italocesarfr-sketch/CHATGPTCODEX/actions/runs/38947014687/artifacts/12067351863).
- [Relatórios de testes e atualização](https://github.com/italocesarfr-sketch/CHATGPTCODEX/actions/runs/38947014687/artifacts/12066198236).
- APK: `app/build/outputs/apk/debug/app-debug.apk`, **54.677.980 bytes**.
- SHA-256: `6d31f64b2db1454c56c3d974e40ab5d0d19785b1401d486168eed34377f59885`.
- Registro estruturado: [baseline/etapa1-validacao.json](baseline/etapa1-validacao.json).

O artefato foi baixado, o ZIP foi conferido, o APK foi encontrado e seu SHA-256/certificado foram recalculados localmente. O prazo configurado do APK no Actions é de 30 dias. Não houve teste em celular físico do professor ou em todos os provedores de arquivos Android; o fluxo básico do diálogo e os mecanismos de leitura/escrita foram testados no emulador/integração.

## Gate de distribuição e próxima etapa

O candidato debug tem certificado `6bd2549307f169482a58ba18f43fabf084109e67bbcf79fea8838b74bea37fb5`, **diferente** do APK original registrado (`3d813f8d…`). O cenário de atualização aprovado recompilou o código antigo com a mesma chave de teste do candidato; não prova que este debug atualiza o APK original.

**Implementação da Etapa 1 e testes sintéticos concluídos. Gate de distribuição estável pendente:** é preciso configurar/guardar a chave externa e verificar o certificado da instalação real. A chave original, caso disponível com o responsável, deve ser conferida antes de escolher uma nova. O resgate só foi ensaiado em dados fictícios; não foi executado no celular. Não orientar desinstalação nem afirmar preservação dessa instalação sem essa comprovação.

As instruções seguras estão em [ASSINATURA.md](ASSINATURA.md) e o uso/formato em [BACKUP.md](BACKUP.md). **Etapa 2 não iniciada; aguarda autorização.**
