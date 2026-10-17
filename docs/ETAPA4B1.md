# Etapa 4B.1 — Primeiro incremento de relatórios completos

Versão validada **Noctua 0.5.0 / código 8**, sobre a 0.4.1 / código 7. Escopo autorizado: relatórios bimestrais, individuais, anuais, frequência e ranking de faltosos. Não inclui recuperação, exportação ou reformulação visual. Os resultados de compilação/testes estão registrados ao final.

## Uso e comportamento

Na página **Relatórios**, os seletores globais de ano, turma e bimestre continuam existentes. O seletor **Relatório** oferece:

- **Bimestral:** atividades da turma em ordem de data, notas por aluno, total obtido, pendências, F/FJ e frequência. Nomes ficam visíveis durante a rolagem horizontal. Ordenação pela chamada, mais faltas ou nota; alunos sem nota lançada ficam depois dos que possuem notas nas ordenações por nota. Cores/escala/faixas continuam as da turma.
- **Frequência:** aulas efetivamente realizadas e elegíveis pela matrícula, P, F, FJ, faltas efetivas, percentual, chamadas válidas e não registradas. Mostra também quantidade de horários realizados na turma e soma das oportunidades das matrículas. Estes totais são diferentes: uma aula pode ser elegível para vários alunos.
- **Mais faltosos:** somente alunos com faltas efetivas maiores que zero, em ordem decrescente. Faltas efetivas são F + FJ se a configuração da turma considera justificadas como ausência; caso contrário, somente F. F e FJ permanecem em colunas separadas. Desempate determinístico por número da chamada, nome e ID interno. A tela informa o critério administrativo utilizado.
- **Resumo anual:** quatro resultados separados por aluno, com notas, pendências/situação, faltas e frequência de cada bimestre, mais frequência anual. Não calcula média anual, aprovação ou recuperação. O modo anual abrange os quatro bimestres independentemente do bimestre destacado no seletor global. A frequência anual inclui também aulas fora dos intervalos bimestrais, dentro do ano/matrícula.

O tipo de relatório é mantido por turma. Reabrir a página, trocar modo ou bimestre posiciona a lista no topo para mostrar sua identificação; alterações de notas/chamadas com a página aberta não mudam a posição da leitura.

Toque no aluno para abrir o **relatório individual somente leitura**: identificação/matrícula, disciplina, período, atividades e notas, frequência, observações e histórico dos quatro bimestres. É possível trocar de bimestre sem voltar. O cadastro editável/perfil anterior continua disponível pelo gerenciamento de alunos; não foi removido.

Zero aparece como `0,00`. Sem qualquer nota lançada, o total mostra **Não lançado** e não um zero fictício. Cada atividade elegível sem nota mostra **Pendente**; atividades fora da matrícula são identificadas e não geram pendência. Sem atividades/dados, a tela informa essa condição. Pontuação disponível abaixo da escala mantém resultado parcial mesmo com todas as notas lançadas, como na regra existente. “Notas completas” descreve cobertura, não fechamento administrativo/aprovação.

## Arquitetura, cálculos e preservação

`reports/SchoolReports.kt` cria projeções imutáveis a partir do `DiarySnapshot` já observado pelo ViewModel. Delega notas a `DiarySnapshot.result` e frequência a `DiarySnapshot.frequency`/`SchoolRules.frequency`, sem escrever no banco. O novo `SchoolRules.aggregateFrequency` soma os contadores de matrículas e reutiliza o mesmo construtor de frequência, mantendo o cálculo ponderado pelas chamadas válidas, sem média de percentuais individuais. As funções anteriores e suas fórmulas não foram alteradas.

O modelo mantém alunos ativos, transferidos e remanejados para consultar o histórico, respeitando ingresso/saída inclusivos. NR/registro ausente não são faltas, canceladas/previstas não contam, e aulas duplas têm registros independentes. Um bimestre sem configuração não é passado como `null` ao cálculo legado (que significaria ano inteiro); sua frequência é indisponível até configurar o período.

Os relatórios recompõem quando os Flows existentes do Room emitem alterações. Não há tabelas/cache de relatórios para sincronizar nem novas dependências de rede. Observação: o `combine` existente pode emitir estados intermediários breves ao atualizar várias consultas; o resultado converge para os registros confirmados. Não foi criada uma segunda lógica de persistência.

Permanecem `br.com.diariodeclasse`, `diario.db`, Room v1/identidade `99b868cf68cf5dffb82edf267ddc78be`, todas as oito tabelas/IDs e formatos de backup 1/2. Nenhuma migração, limpeza/recriação de banco ou mudança de matrículas. Navegar/ordenar relatórios não modifica registros escolares. Backup e restauração continuam implementados no serviço anterior, sem alteração.

## Testes deste incremento

- **JVM:** zero, pendência, resultado parcial/sem atividades, quatro bimestres; matrícula tardia/transferência; P/F em aula dupla, NR, canceladas/previstas; ambas as políticas de FJ; ranking com entradas embaralhadas/desempate; somas ponderadas; bimestre ausente sem fallback anual; aulas fora do bimestre; identidade com cálculos anteriores e isolamento de turma.
- **Room:** fixture v1 completa, relatórios sem escrita, fluxo após nota/chamada/cancelamento, restauração, reabertura e quatro bimestres, zero e pendências preservados. Banco exclusivo de testes; limpeza restrita às fixtures de teste.
- **Compose:** boletim/zero/pendente/fora da matrícula, relatório individual e navegação bimestral, atualização automática com relatórios abertos, ranking e política de FJ, resumo anual, mudança de turma, recriação e comparação do snapshot antes/depois da consulta. Capturas com nomes exclusivamente fictícios.
- **Regressão:** suíte JVM/Android anterior, reinício forçado de chamada/nota confirmadas e atualização da referência v1.

Limites: testes em emulador API 30 não substituem teste físico, TalkBack/fonte ampliada, aparelhos grandes ou outras versões de Android. Não há exportação de arquivos neste incremento.

## Assinatura existente e atualização da release

Foi identificada a release 0.4.1 publicada pelo proprietário na execução [38992921225](https://github.com/italocesarfr-sketch/CHATGPTCODEX/actions/runs/38992921225). Seu APK foi baixado e os metadados públicos registrados em [noctua-release-0.4.1.json](baseline/noctua-release-0.4.1.json). Isso atualiza a informação histórica de assinatura ainda pendente em NOCTUA.md, sem mudar seus resultados antigos.

O arquivo `.github/workflows/release.yml` e a configuração Gradle de assinatura permanecem intactos. O workflow Android acrescenta um job após a regressão, no mesmo ambiente `release`, reutilizando **somente** os secrets/PKCS12/fingerprint existentes. Não gera nem substitui chaves. O ensaio histórico que gerava chaves descartáveis não é executado neste incremento; seu script anterior fica preservado como histórico. O SDK utiliza sua assinatura debug padrão para os testes internos; ela não é apresentada como atualização da release.

O novo job baixa o APK release 0.4.1 de referência, confere seu SHA-256/certificado e o compara com o candidato. Num emulador novo, instala a versão anterior, cria apenas dados sintéticos pelo serviço de backup existente, instala o candidato por cima com `adb install -r` e confere todo o snapshot das oito tabelas/IDs/vínculos e as preferências. Também exercita restauração e reinício. Não desinstala/limpa uma instalação existente para contornar incompatibilidade. O script recusa aparelhos físicos e emuladores já contendo o aplicativo.

Só após passar esse ensaio o APK release é disponibilizado, com hash/metadados públicos. Cópias temporárias da chave e instrumentação são removidas; senhas/chaves não entram em logs ou artefatos. Se secrets, certificado ou referência não conferirem, o job falha sem publicar um APK como atualização compatível.

A referência usa artefato do Actions com vencimento em 16/11/2026 (UTC). Quando expirar ou mudar a referência de distribuição, o mantenedor deverá fixar um novo APK release legítimo e seus metadados públicos; não desativar a comparação. Aprovações do ambiente `release`, se configuradas pelo proprietário, continuam obrigatórias. O certificado do aparelho físico não foi inspecionado: a prova de compatibilidade será com o APK release de referência, não com antigos debug de outra assinatura. Faça backup antes de uma atualização real.

Próximos incrementos de recuperação, exportação e outros relatórios permanecem aguardando autorização.

## Resultado da entrega — 17/10/2026

[Baixar APK release Noctua 0.5.0](https://github.com/italocesarfr-sketch/CHATGPTCODEX/actions/runs/39032589083/artifacts/12101495282) • [GitHub Actions aprovado](https://github.com/italocesarfr-sketch/CHATGPTCODEX/actions/runs/39032589083) • [Regressão e capturas](https://github.com/italocesarfr-sketch/CHATGPTCODEX/actions/runs/39032589083/artifacts/12100961482) • [Provas da atualização release](https://github.com/italocesarfr-sketch/CHATGPTCODEX/actions/runs/39032589083/artifacts/12100986865). O ZIP contém `app-release.apk`, checksum, identidade pública e comparação com a versão anterior. Retenção do APK: 30 dias. Código testado: [`7a482a2`](https://github.com/italocesarfr-sketch/CHATGPTCODEX/commit/7a482a29bcda1fcb5eccf68b38bfce88fa3d5977). O commit documental posterior mantém o mesmo código.

| Verificação | Resultado |
| --- | --- |
| JVM | 31 aprovados, zero falhas |
| Android / emulador API 30 | 70 aprovados, zero falhas |
| Chamadas/notas após reinício forçado | Duas sondas aprovadas |
| Atualização controlada da referência v1 | Duas sondas aprovadas |
| Atualização da release real 0.4.1 → 0.5.0 | Quatro sondas aprovadas: antes, depois, backup/restauração e nova abertura |
| Compilação | `assembleDebug`, `assembleDebugAndroidTest` e `assembleRelease` aprovados |
| Esquema Room / backup | v1 / formatos 1 e 2 preservados; nenhuma migração |
| Assinatura release | Verificada criptograficamente; certificado idêntico ao da release 0.4.1 de referência |
| Regras Java locais | 28 cenários aprovados |

Quatro testes exclusivos são ignorados na suíte normal. As sondas de reinício, atualização v1 e atualização estável são executadas separadamente, em oito fases ao todo. A sonda histórica de chaves descartáveis não é acionada neste incremento, conforme a restrição de não gerar chaves. XMLs JUnit e todas as saídas das oito fases atuais foram baixados/conferidos. Capturas dos quatro tipos de relatório foram conferidas no artefato, incluindo tabela em tela pequena com nomes fixos. As primeiras execuções identificaram uma importação de teste indisponível, comparações de texto e uma expectativa que excluía NR do total realizado; foram corrigidas. A regressão também exigiu manter o texto anterior de presença/frequência no novo relatório individual e reposicionar o cabeçalho ao reabrir a página. Nenhuma fórmula foi alterada para fazer testes passarem.

O APK release foi baixado, sua existência, manifesto binário, versão e hash foram verificados em `/workspace/diario-de-classe/app/build/outputs/apk/release/app-release.apk`: **7,097,810 bytes**, versão 0.5.0 / código 8, pacote `br.com.diariodeclasse`.

- SHA-256 do APK release: `dd30896be3b4b70eb7dc228ef0bab0c675adf624742a5d1df25708ae736c0133`.
- SHA-256 do certificado público release: `6dbbba2d930eb7f7fbeff1ee82512f656251bbec9f4b31b6c5614528d29f1aae`.
- Esquema v1 SHA-256: `43fe5f77af75d462dff34128582fdeb6cf759b9504fa2bb6ee468a6951ec3ed7`.
- [Metadados completos da entrega](baseline/etapa4b1-validacao.json).

**Compatibilidade comprovada:** instalação sobre o APK release 0.4.1 publicado pelo proprietário, com a mesma chave existente, preservando todas as entidades/IDs, chamadas, notas e preferências. Backup/restauração e reabertura também passaram. Não foram criadas/substituídas chaves permanentes, nem exibidas/publicadas chaves ou senhas. O workflow manual `release.yml` continua idêntico ao da referência. A assinatura debug interna dos testes não é a assinatura do APK entregue.

O aparelho físico não foi inspecionado. A comprovação vale para a release 0.4.1 de referência; debug antigos com certificado diferente não passam a ser compatíveis. Faça backup antes de atualizar a instalação real, sem desinstalar/limpar dados para contornar uma divergência de assinatura. Não foram utilizados dados escolares reais. Testes Android/Gradle executados no Actions com JDK 17/SDK 35, sem depender de SDK local.

Somente este primeiro incremento da Etapa 4B.1 está concluído. Recuperação, exportação e os próximos incrementos continuam aguardando autorização.
