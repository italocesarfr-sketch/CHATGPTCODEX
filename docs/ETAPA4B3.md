# Etapa 4B.3 — Consolidação final dos relatórios

Incremento **Noctua 0.6.1 / código 10**, baseado na **0.6.0 / código 9**, aprovada em celular físico pelo proprietário. Implementação restrita à consolidação dos relatórios, sem novas regras pedagógicas, exportação ou reformulação visual. Resultados de compilação, testes e atualização registrados ao final.

## Relatórios consolidados

- **Bimestral:** número/nome, situação atual da matrícula, atividades, original, recomposição e vigente em colunas próprias, pendências, aulas elegíveis, P, F, FJ, faltas efetivas e percentual. Zero continua numérico; pendente e fora da matrícula são estados distintos. Uma recomposição não registrada difere de um registro não realizado e de zero.
- **Individual:** identificação, turma/ano, intervalo de matrícula e cobertura parcial, atividades e os três resultados, data da recomposição, frequência do período e histórico dos quatro bimestres. Consulta somente leitura.
- **Anual:** quatro resultados independentes com situação dos lançamentos, faltas efetivas, F/FJ separados e percentual; frequência anual por matrícula e soma das oportunidades da turma. Inclui aulas elegíveis do ano fora dos intervalos bimestrais. Não calcula média anual nem aprovação. A porcentagem vem das chamadas válidas agregadas; não é a média dos quatro percentuais.
- **Frequência e faltosos:** mesma projeção numérica e política de FJ da turma, informada na tela. Ranking por faltas efetivas decrescentes, desempate por número, nome e ID. NR, dias sem chamada, cancelamentos e períodos fora da matrícula não viram faltas.

Sem atividades aplicáveis, aulas realizadas ou recomposições registradas, o período aparece como “Sem dados”. Atividades aplicáveis sem notas continuam pendentes, não zero. Lançamentos parciais continuam parciais mesmo com recomposição. A distinção de atividade não aplicável depende da matrícula na data da atividade, como antes. Transferidos/remanejados permanecem consultáveis pelo histórico; o status atual não apaga suas notas ou chamadas. Bimestres sem configuração são identificados, sem usar acidentalmente a frequência anual.

## Projeções e consistência

`SchoolReports` permanece a entrada compartilhada somente leitura para o futuro módulo de exportação: resultados numéricos, ordem estável, estados das atividades, cobertura da matrícula, quatro períodos e `FrequencySummary`. Não há uma segunda fórmula: `DiarySnapshot.result`, `DiarySnapshot.frequency`, `SchoolRules` e `RecompositionRules` continuam responsáveis pelos cálculos. O resultado original é recalculado das atividades atuais; a recomposição permanece separada por aluno/bimestre e não é somada às atividades.

O Repository passou a observar invalidações das nove tabelas e ler um `DiarySnapshot` completo em uma única transação Room de leitura. Isso impede combinar estados intermediários de consultas independentes durante alterações transacionais, como o novo máximo de uma atividade com notas ainda antigas. Não altera comandos de gravação, dados, IDs, esquema ou backup. Emissões iguais são descartadas.

A tabela conserva o nome visível durante a rolagem horizontal, aceita nomes longos e mantém a posição vertical/horizontal em atualizações dos dados. Apenas mudanças explícitas de turma, período, aluno ou tipo de relatório reposicionam a leitura. Os filtros globais e as quatro páginas existentes foram preservados. Não há nova identidade visual.

## Preservação e assinatura

Permanecem `br.com.diariodeclasse`, `diario.db`, Room **v2**, nove tabelas e IDs, migração aditiva 1→2, backup **formato 3**, leitores dos formatos 1/2, preferências e funcionamento offline. Nenhuma limpeza, recriação, migração destrutiva ou alteração do esquema. Arquivos de esquema anteriores permanecem idênticos. O código de backup/restauração e as regras de notas, frequência, recálculo proporcional e recomposição não foram alterados.

O workflow manual `release.yml` e a configuração de assinatura permanecem intactos. O CI reutiliza a chave existente, sem criar/substituir chaves ou modificar Secrets. A referência de atualização agora é a release real **0.6.0**, execução `39045605081`, artefato `12105803073`; metadados públicos em [baseline/noctua-release-0.6.0.json](baseline/noctua-release-0.6.0.json).

O ensaio instala essa referência em emulador descartável, sem dados reais, preenche as nove tabelas com dados fictícios e recomposições zero, positiva e não realizada antes da atualização, atualiza por cima e compara todos os registros/IDs/preferências. Confere relatórios, backup/restauração, correção explícita de recomposição e reabertura do processo. Scripts recusam aparelhos físicos. A comparação criptográfica com o certificado de referência continua obrigatória.

Faça backup externo válido antes de atualizar. Não desinstale nem limpe os dados para contornar uma divergência de assinatura. Não faça downgrade para versões que não reconheçam o esquema v2. APK debug não é indicado para atualizar a instalação release.

## Cobertura executada

- Regressão JVM, compilação debug/instrumentação e cenários Java locais.
- Seis novos testes JVM de projeção: quatro períodos, zero/pendente/não aplicável, recomposição e correções posteriores, frequência anual ponderada, elegibilidade/política FJ, ranking/ordem estável e isolamento de turma/ano.
- Três novos testes Room: consistência de todas as emissões durante recálculos proporcionais, reinício/backup das nove tabelas e mudança/exclusão de atividade sem deslocar a recomposição de origem.
- Quatro novos testes Compose, além da regressão existente: campos do boletim, integração anual/individual, nomes extensos/posição de leitura e trocas rápidas de ano/turma/período.
- Regressão de frequência, atividades, recálculo proporcional, recomposição, migração e backups anteriores; sondas de encerramento/reabertura e atualização real 0.6.0 → 0.6.1.

## Resultados verificados — 17/10/2026

[Baixar APK com assinatura release existente](https://github.com/italocesarfr-sketch/CHATGPTCODEX/actions/runs/39049512375/artifacts/12106789781) • [Actions aprovado](https://github.com/italocesarfr-sketch/CHATGPTCODEX/actions/runs/39049512375) • [Testes e capturas](https://github.com/italocesarfr-sketch/CHATGPTCODEX/actions/runs/39049512375/artifacts/12107565969) • [Provas de atualização](https://github.com/italocesarfr-sketch/CHATGPTCODEX/actions/runs/39049512375/artifacts/12107880437). O ZIP release contém `app-release.apk`, checksum, identidade pública e comparação de compatibilidade. Código testado: [`706b8a0`](https://github.com/italocesarfr-sketch/CHATGPTCODEX/commit/706b8a0bfc7ead1206db94259b741f3ddbe143df). O commit documental posterior conserva esse código.

| Verificação | Resultado |
| --- | --- |
| JVM, incluindo seis novos testes de projeção | 45 aprovados, zero falhas, localmente e no CI |
| Android API 30, incluindo três novos testes Room e quatro Compose | 87 aprovados, zero falhas |
| Sondas de reinício de chamadas/notas | 2 fases aprovadas |
| Atualização legada Room v1 → v2 | 2 fases aprovadas, preservação de todas as oito tabelas anteriores |
| Release real 0.6.0 → 0.6.1 | 4 fases aprovadas: antes, depois, backup/restauração e reabertura |
| Preservação da release 0.6.0 | Nove tabelas, recomposições pré-existentes zero/positiva/não realizada, IDs e preferências conferidos |
| Backup e restauração | Formato 3 e regressão dos formatos anteriores aprovados; código de produção intacto |
| Esquema Room | v2 intacto; JSONs v1 e v2 idênticos à referência |
| Compilação | `assembleDebug`, `assembleDebugAndroidTest` e `assembleRelease` aprovados |
| Assinatura | `apksigner verify` aprovado, mesmo certificado da release 0.6.0 |
| Domínio Java local | 28 cenários aprovados |

Quatro testes exclusivos de sondas são ignorados na suíte Android normal. As três sondas ativas são executadas separadamente em oito fases: reinício (duas), atualização legada (duas) e release permanente (quatro). A quarta, `ReleaseContinuityProbeTest`, é histórica e não foi acionada: o teste atual reutiliza a assinatura permanente, sem executar o script antigo de geração de chaves de teste. Foram conferidos os XMLs JUnit e as saídas das oito fases. Testes e capturas usam exclusivamente dados fictícios. Nenhum banco real integra o repositório ou os artefatos.

A primeira execução ([39049036348](https://github.com/italocesarfr-sketch/CHATGPTCODEX/actions/runs/39049036348)) aprovou os 87 testes Android e as sondas de reinício, mas detectou uma corrida no teste legado de atualização: a Activity já estava aberta antes de a primeira leitura/migração assíncrona do Room terminar. A sonda passou a aguardar, com limite de dez segundos, o `user_version` esperado por consultas SQLite somente leitura; não abre Room por conta própria nem modifica o banco. A suíte completa e todas as sondas foram repetidas e aprovadas. Nenhuma regra ou migração foi alterada para contornar o teste.

Os APKs debug/instrumentação e testes JVM também foram compilados localmente com JDK 17 completo/SDK 35. Os ensaios Android e a release assinada foram executados no CI; o APK assinado foi baixado e novamente conferido pelo manifesto binário, checksum e `apksigner`.

- Release: **7,130,574 bytes**, `br.com.diariodeclasse`, **0.6.1 / código 10**, Android 8.0+.
- Caminho verificado: `app/build/outputs/apk/release/app-release.apk` (absoluto: `/workspace/diario-de-classe/app/build/outputs/apk/release/app-release.apk`).
- APK SHA-256: `ac69dfee0d908255a7041798934f27fde5a39eaf0c7b632e3639125b0acd382e`.
- Certificado público SHA-256: `6dbbba2d930eb7f7fbeff1ee82512f656251bbec9f4b31b6c5614528d29f1aae`.
- [Metadados completos](baseline/etapa4b3-validacao.json).
- [APK debug interno](https://github.com/italocesarfr-sketch/CHATGPTCODEX/actions/runs/39049512375/artifacts/12107466325): certificado diferente da release, sem promessa de atualização sobre ela. Caminho verificado: `app/build/outputs/apk/debug/app-debug.apk`.

**Compatibilidade comprovada:** atualização por cima da release 0.6.0 de referência, com a mesma assinatura, preservando integralmente dados sintéticos das nove tabelas e preferências, sem migração de esquema nesta versão. Recomposições anteriores e correções posteriores permaneceram presentes após backup/restauração e encerramento/reabertura. As chamadas e notas originais foram conferidas.

O workflow manual `release.yml` e sua configuração continuam intactos para o proprietário compilar com a mesma chave. Nenhuma chave permanente foi criada ou substituída; nenhum arquivo privado, senha ou Secret foi publicado ou modificado. Artefatos APK têm retenção de 30 dias no workflow; a referência anterior também depende da retenção do Actions. Quando expirar, o mantenedor deve fixar uma referência legítima e seus metadados públicos, conservando a comparação obrigatória de assinatura.

**Limites:** a aprovação em aparelho físico informada pelo proprietário refere-se à 0.6.0. A 0.6.1 foi validada em emulador API 30; ainda requer conferência no celular. Não foi inspecionado o certificado da instalação física, nem feitos novos ensaios em API 26, tablets, TalkBack, fonte ampliada ou bases escolares extensas. As capturas em tela pequena confirmam nomes fixos e legíveis; não substituem esses ensaios. Faça backup externo válido antes de atualizar e não desinstale para contornar assinatura incompatível.

Somente a Etapa 4B.3 foi implementada. Não há média anual, aprovação, nova recuperação, exportação, cópia de tabelas ou módulo de registros de aula. A Etapa 5 aguarda autorização.
