# Noctua — identidade e continuidade da assinatura

Incremento exclusivo sobre a versão 0.4.0: **0.4.1 / código 7**. A aprovação física do cadastro, chamadas, notas, edição, exclusão e proporcionalidade da 0.4.0 foi informada pelo professor na autorização deste incremento. Não houve reformulação dessas funcionalidades.

## Alterações

- Nome Android e cabeçalho passam a **Noctua**, com recurso único `app_name`. Coruja vetorial no ícone e no cabeçalho, mantendo cores e navegação.
- Permanecem pacote `br.com.diariodeclasse`, banco privado `diario.db`, esquema Room 1, oito tabelas, IDs, preferências e formatos de backup 1/2. O identificador `diario-de-classe-backup` continua deliberadamente igual para ler arquivos existentes. Nenhuma migração ou regravação na abertura.
- Assinatura release existente reaproveitada: PKCS12 externo, alias de chave privada, validade e SHA-256 esperado conferidos no Gradle; APK verificado novamente antes de disponibilizar. Ausência de configuração ou certificado divergente bloqueia release.
- Workflow release manual, ambiente `release`, sem cache Gradle, sem criação de chave e com remoção da cópia temporária do runner. Artefato inclui apenas APK e checksum. A chave original fica sob guarda do proprietário; GitHub Secrets recebe somente a cópia necessária ao build autorizado.
- [Guia do proprietário](ASSINATURA_PASSO_A_PASSO.md) com geração local, guarda, configuração, execução, verificação e transição segura.

## Testes previstos e limites

A suíte existente de Room/backup/interface é mantida. O teste de identidade confere nome Android, cabeçalho e pacote. O CI mantém a atualização da referência v1 e acrescenta atualização **0.4.0 → Noctua** com certificado de teste controlado:

1. Gera duas chaves **descartáveis de teste**, fora do repositório. Não são a chave permanente.
2. Confirma bloqueio de release sem configuração e com fingerprint incorreto; compila `assembleRelease` com a primeira chave descartável e confere assinatura.
3. Assina a referência 0.4.0 e a instrumentação com a mesma chave. Instala no emulador descartável e carrega exclusivamente a fixture sintética versionada.
4. Confere cada campo das oito tabelas, guarda backup e seleção de turma/ano. Atualiza para o release de teste, sem desinstalar, e compara todo o snapshot e preferências.
5. Altera uma nota fictícia, restaura o backup e compara novamente todas as entidades/IDs/valores e a seleção.
6. Tenta instalar APK com outra assinatura: exige `INSTALL_FAILED_UPDATE_INCOMPATIBLE`. Sem limpar/desinstalar, reinicia e confere novamente banco, preferências e backup/restauração.

`scripts/ci/test-release-upgrade.sh` recusa execução fora de GitHub Actions/CI e de um emulador identificado como tal. As sondas adicionais também exigem hardware de emulador e argumento explícito. A limpeza inicial pertence somente ao sandbox de teste; não há scripts de limpeza no aplicativo. Chaves são removidas por trap; não são incluídas nos artefatos. O APK release com chave descartável também é removido e **não é distribuído como release definitivo**.

Os resultados serão registrados após a execução real do CI. Emulador não substitui validação física do novo ícone e cabeçalho. A primeira release permanente depende da configuração pelo proprietário; não foi gerada uma chave permanente. Um teste com assinatura controlada não comprova compatibilidade com o APK debug do celular.

## Transição e próximos passos

Seguir [ASSINATURA_PASSO_A_PASSO.md](ASSINATURA_PASSO_A_PASSO.md), inclusive a verificação do backup antes de qualquer instalação limpa. A informação antiga sobre dados descartáveis não é presumida para registros novos feitos na 0.4.0. Não desinstalar ou limpar dados durante uma tentativa de atualização incompatível.

Relatórios, recuperação, exportação inteligente e os demais incrementos continuam aguardando autorização.
