> **Marca oficial aprovada posteriormente:** a arte PNG enviada pelo proprietário substitui as corujas provisórias na 0.11.3. [Fonte, recursos e testes](LOGO_OFICIAL.md). As descrições e capturas antigas abaixo são históricas.

# Noctua — identidade e continuidade da assinatura

Incremento exclusivo sobre a versão 0.4.0: **0.4.1 / código 7**. A aprovação física do cadastro, chamadas, notas, edição, exclusão e proporcionalidade da 0.4.0 foi informada pelo professor na autorização deste incremento. Não houve reformulação dessas funcionalidades.

## Alterações

- Nome Android e cabeçalho passam a **Noctua**, com recurso único `app_name`. Coruja vetorial no ícone e no cabeçalho, mantendo cores e navegação.
- Permanecem pacote `br.com.diariodeclasse`, banco privado `diario.db`, esquema Room 1, oito tabelas, IDs, preferências e formatos de backup 1/2. O identificador `diario-de-classe-backup` continua deliberadamente igual para ler arquivos existentes. Nenhuma migração ou regravação na abertura.
- Assinatura release existente reaproveitada: PKCS12 externo, alias de chave privada, validade e SHA-256 esperado conferidos no Gradle; APK verificado novamente antes de disponibilizar. Ausência de configuração ou certificado divergente bloqueia release.
- Workflow release manual, ambiente `release`, sem cache Gradle, sem criação de chave e com remoção da cópia temporária do runner. Artefato inclui apenas APK e checksum. A chave original fica sob guarda do proprietário; GitHub Secrets recebe somente a cópia necessária ao build autorizado.
- [Guia do proprietário](ASSINATURA_PASSO_A_PASSO.md) com geração local, guarda, configuração, execução, verificação e transição segura.

## Testes e limites

A suíte existente de Room/backup/interface é mantida. O teste de identidade confere nome Android, cabeçalho e pacote. O CI mantém a atualização da referência v1 e acrescenta atualização **0.4.0 → Noctua** com certificado de teste controlado:

1. Gera duas chaves **descartáveis de teste**, fora do repositório. Não são a chave permanente.
2. Confirma bloqueio de release sem configuração e com fingerprint incorreto; compila `assembleRelease` com a primeira chave descartável e confere assinatura.
3. Assina a referência 0.4.0 e a instrumentação com a mesma chave. Instala no emulador descartável e carrega exclusivamente a fixture sintética versionada.
4. Confere cada campo das oito tabelas, guarda backup e seleção de turma/ano. Atualiza para o release de teste, sem desinstalar, e compara todo o snapshot e preferências.
5. Altera uma nota fictícia, restaura o backup e compara novamente todas as entidades/IDs/valores e a seleção.
6. Tenta instalar APK com outra assinatura: exige `INSTALL_FAILED_UPDATE_INCOMPATIBLE`. Sem limpar/desinstalar, reinicia e confere novamente banco, preferências e backup/restauração.

`scripts/ci/test-release-upgrade.sh` recusa execução fora de GitHub Actions/CI e de um emulador identificado como tal. As sondas adicionais também exigem hardware de emulador e argumento explícito. A limpeza inicial pertence somente ao sandbox de teste; não há scripts de limpeza no aplicativo. Chaves são removidas por trap; não são incluídas nos artefatos. O APK release com chave descartável também é removido e **não é distribuído como release definitivo**.

Os resultados da execução real do CI estão registrados abaixo. Emulador não substitui validação física do novo ícone e cabeçalho. A primeira release permanente depende da configuração pelo proprietário; não foi gerada uma chave permanente. Um teste com assinatura controlada não comprova compatibilidade com o APK debug do celular.

## Transição e próximos passos

Seguir [ASSINATURA_PASSO_A_PASSO.md](ASSINATURA_PASSO_A_PASSO.md), inclusive a verificação do backup antes de qualquer instalação limpa. A informação antiga sobre dados descartáveis não é presumida para registros novos feitos na 0.4.0. Não desinstalar ou limpar dados durante uma tentativa de atualização incompatível.

Relatórios, recuperação, exportação inteligente e os demais incrementos continuam aguardando autorização.

## Resultado verificado

[APK debug Noctua 0.4.1 (ZIP)](https://github.com/italocesarfr-sketch/CHATGPTCODEX/actions/runs/38989743182/artifacts/12084473884) • [Actions aprovado](https://github.com/italocesarfr-sketch/CHATGPTCODEX/actions/runs/38989743182) • [Relatórios e capturas](https://github.com/italocesarfr-sketch/CHATGPTCODEX/actions/runs/38989743182/artifacts/12084449177). Extrair `app-debug.apk`. Retenção dos artefatos APK: 30 dias. Código testado: [`f30f6fb`](https://github.com/italocesarfr-sketch/CHATGPTCODEX/commit/f30f6fbfc0af799641a8b37c1a1c05e42555c8f2). Um commit documental posterior mantém o mesmo código.

| Verificação | Resultado |
| --- | --- |
| Testes JVM | 24 aprovados, zero falhas |
| Testes Android, emulador API 30 | 63 aprovados, zero falhas |
| Reinício forçado de chamada/nota já confirmadas | Duas sondas aprovadas |
| Atualização controlada da referência v1 | Duas sondas aprovadas; todas as oito tabelas preservadas |
| Atualização 0.4.0 → release Noctua com mesma chave descartável | Seis sondas aprovadas: antes, snapshot/preferência, depois, restauração, após rejeição e restauração após rejeição |
| Tentativa de assinatura diferente | Android recusou com `INSTALL_FAILED_UPDATE_INCOMPATIBLE`; banco/preferências continuaram íntegros |
| Release sem chave / fingerprint incorreto | Ambos bloqueados |
| `assembleDebug`, `assembleDebugAndroidTest`, `assembleRelease` de teste | Sucesso; release de teste verificada por apksigner e fingerprint esperado |
| Domínio Java local | 28 cenários aprovados |
| Room | JSON v1 idêntico; identidade `99b868cf68cf5dffb82edf267ddc78be` |

As três sondas ignoradas na suíte principal são exclusivas dos scripts, executadas separadamente nas dez fases acima. Os XMLs JUnit e as saídas de cada sonda foram baixados e conferidos. A regressão inclui backup/restauração, preservação de IDs, pendências/zero, cadastro, frequência, edição/exclusão e proporcionalidade existentes. As imagens sintéticas das quatro páginas permitem conferir o cabeçalho Noctua; não foi feito teste físico deste incremento nem inspeção do launcher em aparelho real. As capturas dos diálogos antigos continuam limitadas à janela principal, conforme ETAPA4A.md.

O artefato foi baixado, o ZIP/manifesto interno e o SHA-256 foram conferidos, e o APK existe em `/workspace/diario-de-classe/app/build/outputs/apk/debug/app-debug.apk`: **10,358,890 bytes**, pacote `br.com.diariodeclasse`, versão 0.4.1 / código 7.

- SHA-256 do APK debug: `02bafb2c112bb306b0be4740a02dc04c8949566ccff018ff27f8bf5853b5a47a`.
- SHA-256 do certificado debug: `9738399843c3751b6ea0ba5516a5273cc5bc7a1c3fc56818a89c028612fe74f8`.
- Compatibilidade com a referência 0.4.0: difere do certificado do APK debug 0.4.0 analisado; este debug não o atualiza diretamente.
- Evidência estruturada: [noctua-validacao.json](baseline/noctua-validacao.json). O certificado da instalação física não foi inspecionado.

**Não foi gerada a primeira release permanente.** O release compilado/testado utilizou uma chave descartável e foi removido; não é oferecido para distribuição. O proprietário precisa criar/guardar sua chave no computador, cadastrar os quatro secrets e a variável pública e executar o workflow manual conforme o [guia](ASSINATURA_PASSO_A_PASSO.md). Nenhuma chave/senha privada ou registro escolar real foi publicado. O teste controlado comprova preservação com assinatura igual, sem prometer atualização do debug antigo para uma chave permanente diferente.

A compilação real ocorreu no Actions com JDK 17, SDK 35 e emulador API 30. Localmente os cenários Java e as proteções dos scripts passaram; o shell bloqueou o download do Gradle com `SocketException: Operation not permitted`, mesmo usando diretório gravável. Isso não impediu a compilação e verificação pelo CI. Nenhum script destrutivo foi executado em aparelho físico.

Sem alterações em relatórios, recuperação ou exportação inteligente. Aguardar autorização para os próximos incrementos.

## Atualização posterior — assinatura configurada pelo proprietário

O proprietário gerou uma release 0.4.1 na execução [38992921225](https://github.com/italocesarfr-sketch/CHATGPTCODEX/actions/runs/38992921225), depois da entrega histórica acima. Na [Etapa 4B.1](ETAPA4B1.md), a 0.5.0 reutilizou essa assinatura e foi instalada por cima daquela release com preservação integral comprovada em emulador. Nenhuma nova chave foi criada por este incremento. [Actions validado](https://github.com/italocesarfr-sketch/CHATGPTCODEX/actions/runs/39032589083).
