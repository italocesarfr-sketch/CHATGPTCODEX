# Melhorias funcionais — chamada, atividades e notas

Branch: `sync/academic-complete-0220`. Continuidade da base funcional H13 preservada; os candidatos H13.1/H13.2 não foram integrados. Somente dados sintéticos e infraestrutura descartável são autorizados nesta rodada.

## Etapa 1 — chamada

- `08ffc24`: segundo toque em P/F consulta o estado real dentro de `Room.withTransaction` e exclui a ocorrência. Ausência não é NR.
- `mark` continua atribuindo estados diretamente para menus, correções e importações. NR explícito e registros anteriores não são convertidos. FJ e seus motivos mantêm as validações por ocorrência.
- O DELETE existente dispara a captura transacional da sincronização. A outbox transmite `payload: null`, com revisão conhecida; o recebimento exclui a mesma chave composta aula/matrícula. Nenhum novo formato, migração ou protocolo.
- `478894c`: prova HTTP real em dois AVDs novos; `836d06c`: correção de expectativas antigas de automação; `358a272`: inicialização PostgREST espera o fim do HTTP 503 de carga do cache.
- Não há autorização para corrigir chamadas de matrículas remanejadas ou romper históricos. Os bloqueios anteriores permanecem aplicados antes de excluir.

## Etapa 2 — atividades e notas (código `85bf615`, validado localmente)

Busca normalizada por nome sem acentos/caixa e número exato da chamada. Ordenação por número (padrão) ou nome com desempate estável. Nenhuma renumeração, matrícula ou nota muda ao ordenar. Texto inválido não salvo permanece identificado mesmo ao filtrar o aluno e durante recriação da tela.

Criação: turma atual por padrão, todas as compatíveis do mesmo ano letivo ou seleção manual. A seleção manual permite todas/nenhuma/individual, agrupadas por ano letivo e etapa. Turmas de outro ano letivo exigem data própria explícita; nenhuma data é deslocada automaticamente. O cadastro não possui campo estruturado de série: não se infere série a partir do nome livre da turma. A elegibilidade utiliza etapa, disciplina, arquivamento, ano da data e período real; todas as turmas envolvidas aparecem no resumo.

`ActivityCopies.plan` é uma prévia sem escrita, revalidada pelo repositório dentro de uma única transação. Se uma turma, período ou elegibilidade mudou, ou existir duplicata, nada é inserido. As cópias possuem IDs independentes determinísticos por pedido/turma; repetir o mesmo pedido não sobrescreve atividades ou notas. As notas não são copiadas. Um erro de inserção reverte o lote e a captura da fila. A sincronização incremental existente transporta as atividades e depois suas notas, respeitando dependências.

Não houve alteração de Room 14, backup 12, SQL acadêmico, assinatura, applicationId, regras de remanejamento ou configurações hospedadas. Os limites de lote C/D/E continuam válidos; ultrapassá-los bloqueia o envio com os dados locais preservados.

## Evidências e limites

Comando local: `./gradlew --offline --no-daemon testDebugUnitTest assembleDebugAndroidTest`, JDK 17/SDK local. Resultado mais recente: BUILD SUCCESSFUL; 306 casos JVM, 301 aprovados, cinco condicionais não executados (quatro H01–H04 hospedados e um SupabaseLiveAuthTest), zero falhas. Esses condicionais não representam homologação.

A rodada `43546621371` aprovou 28/30 testes instrumentados: expectativa antiga de P no segundo toque e procura de título lazy fora da área composta falharam. Ambos foram corrigidos preservando asserções. A rodada `43547135005` interrompeu antes do Android: o health check antigo tratava HTTP 503 de inicialização como pronto. Correção: aguardar HTTP de sucesso; nenhum teste de permissão foi flexibilizado.

### Etapa 1 aprovada

Execução [43550063150](https://github.com/italocesarfr-sketch/CHATGPTCODEX/actions/runs/43550063150), código `f36c60c2afa2b78ba07c12d57f52af698b48f78d`: 293 JVM aprovados, cinco condicionais não executados; 30 testes Android aprovados, zero falhas/ignorados; sete fases HTTP em dois AVDs aprovadas; nove verificações HTTP de segurança descartáveis aprovadas. Artefato `14094934710`.

A falha de busca era automação: o controle Limpar estava fora da viewport; não bastava compor o primeiro item lazy para torná-lo clicável. O teste agora oculta IME, recompõe o item, rola até o controle e comprova sua visibilidade antes do toque. Também aguarda o bimestre selecionado e readquire a célula após substituição da lista. Nenhuma asserção acadêmica foi removida; nenhum código de Relatórios foi alterado.

O primeiro fechamento HTTP do ensaio consultava apenas o lote inicial. O protocolo C retorna um lote por chamada: a prova agora percorre todas as páginas e exige exatamente os dois tombstones de revisão 2. As etapas anteriores já haviam comprovado a remoção nos dois bancos independentes. O protocolo não mudou.

### Etapa 2: primeira execução

[43550410048](https://github.com/italocesarfr-sketch/CHATGPTCODEX/actions/runs/43550410048), código `85bf615`: dez fases HTTP aprovadas (sete P/F e três de atividades/notas, inclusive outra turma/ano letivo), oito novos testes Room aprovados, busca/ordenação/rotação aprovada. Total Android normal: 55/57 aprovados, duas falhas nas asserções do resumo de turmas. A API `assertTextContains` exigia item textual exato por padrão; os textos reais já continham o resumo correto. `b5b486e` exige o texto integral com quantidade e nomes. Os salvamentos desses dois cenários e os testes com fonte 150% não foram aprovados pela primeira rodada; foram comprovados na execução final abaixo.

### Execução final aprovada — as duas etapas

[43552621657](https://github.com/italocesarfr-sketch/CHATGPTCODEX/actions/runs/43552621657), SHA **`8974312ef3e7eec414cd04f4635869bc14a3f759`**, job `functional / tests` aprovado. Os jobs H131/H132 ficaram fora da seleção (não são testes aprovados).

| Prova efetivamente executada | Aprovados | Falhas | Não executados/ignorados |
|---|---:|---:|---:|
| JVM completo | 301 | 0 | 5 condicionais hospedados |
| Android selecionado (nove classes, incluindo oito novos casos Room e três UI) | 57 | 0 | 0 |
| Repetição Android a 360×640 dp e fonte nativa 150% | 3 | 0 | 0 |
| HTTP real / Room em dois AVDs independentes (sete P/F + três atividades/notas) | 10 fases | 0 | 0 |
| Verificações HTTP de sessão e permissão no PostgreSQL/PostgREST descartável | 9 | 0 | 0 |

Os cinco condicionais JVM são H01–H04 e SupabaseLiveAuthTest: não receberam credenciais nem autorização hospedada. A regressão Android foi selecionada para os módulos alterados; a suíte Android global e o benchmark H13 não foram repetidos.

No transporte real: exclusões nos dois sentidos, reabertura do processo com outbox persistente, arquivo de identificação da operação conservado, estados/cursores/acknowledgements íntegros, duas revisões de exclusão confirmadas, FJ/Outro e NR explícito intactos. Atividades de 2026 e 2027 (data própria autorizada no pedido), notas zero/null e edição inversa 6,5 sem alterar a nota da outra atividade. No Room: rollback induzido, duplicata em destino, seleção desatualizada, concorrência de pedidos repetidos, IDs independentes e recuperação em outro banco; perda de confirmação nesse teste Room usa Remote controlado, não uma falha hospedada.

`43551111500` comprovou os 57 testes normais e falhou na preparação dos três casos de fonte ampliada (card ainda não composto). `24adc86` solicitou a composição lazy; `43551820284` aprovou 57 normais e dois ampliados, faltando rolar até o botão interno de um card alto. `8974312` exige que esse botão esteja visível antes do toque. `43552093828` foi cancelada por substituição, sem representar aprovação. Nenhum timeout de produto foi ampliado, nenhum teste foi ignorado e nenhum predicado acadêmico foi removido.

O helper de captura passou a usar a janela nativa Android (`1d4108d`), incluindo Dialog; a enumeração de roots Compose podia fotografar apenas a Activity subjacente. As capturas finais foram inspecionadas e copiadas sem edição. [Artefato integral 14096378442](https://github.com/italocesarfr-sketch/CHATGPTCODEX/actions/runs/43552621657/artifacts/14096378442); hashes e contagens em [manifest.json](evidence/FUNCIONAIS_20261114/manifest.json).

Capturas: [chamada vazia](evidence/FUNCIONAIS_20261114/chamada-sem-marcacao.png), [busca e ordenação](evidence/FUNCIONAIS_20261114/notas-busca-ordenacao.png), [seleção de turmas](evidence/FUNCIONAIS_20261114/atividades-selecao.png), [seleção com fonte 150%](evidence/FUNCIONAIS_20261114/atividades-fonte-150.png) e [notas com fonte 150%](evidence/FUNCIONAIS_20261114/notas-fonte-150.png).

Limitações: não existe série estruturada no cadastro; o agrupamento usa ano letivo e etapa, sem inferir série pelo nome. Em 360 dp/fonte 150%, a marca e alguns rótulos da navegação global existente quebram em linhas (a marca fica parcialmente cortada); os controles novos continuam alcançáveis por rolagem e passaram nos testes. Esse ajuste global de cabeçalho/navegação permanece fora desta etapa. Não houve homologação física ou hospedada destes fluxos novos, nem nova comprovação de capacidade em grandes acervos. A versão de desenvolvimento permanece 0.22.0-dev-e2/c45; sem release ou atualização assinada nesta entrega.

Nenhuma execução hospedada, instalação em dispositivo existente ou release oficial. O fluxo novo deverá ser homologado fisicamente em variante isolada antes de distribuição.

## Arquivos e reprodução

Código da chamada: `data/DiaryRepository.kt`, `ui/DayAttendanceScreen.kt`, `ui/FrequencyGrid.kt`. Registros acadêmicos históricos não são migrados.

Código de atividades: `data/ActivityCopies.kt`, `data/Database.kt` (somente DAO, sem alteração de esquema), `data/DiaryRepository.kt`, `ui/Forms.kt`, `ui/GradeScreen.kt`, `ui/DiaryApp.kt`.

Testes: `ActivityCopiesTest`, `ActivityCopiesRoomTest`, `ActivityCopiesUiTest`, `PhaseCRoomTest`, `PromptTwoRoomInstrumentedTest`, `PromptTwoUiInstrumentedTest`, `FjPopupInstrumentedTest` e `FunctionalNetworkProbeTest`.

Execução Android: `.github/workflows/functional-improvements.yml`, seleção `FUNCTIONAL` no dispatcher já registrado `h131-investigation.yml`, `scripts/ci/run-functional-validation.sh`, `scripts/ci/test-functional-android.sh`, `scripts/ci/test-functional-network.sh` e `scripts/check-instrumentation.py`. Ajustados também a prontidão do PostgREST descartável e o total esperado de testes focados C (10 anteriores + novo teste de tombstone = 11). Os jobs H131/H132 não são acionados nesta seleção; as investigações rejeitadas não participam do backend.

Comando de CI: `gh workflow run h131-investigation.yml --ref sync/academic-complete-0220 -f suite=FUNCTIONAL`. Não recebe Secrets ou Environment hospedado; o transporte usa somente PostgreSQL/PostgREST locais do runner e identidades sintéticas da infraestrutura anterior. Dois AVDs novos pertencem ao job. As limpezas de fixture e mudanças de fonte/tela são restritas a esse runner; não há conexão com emuladores do proprietário.

O artefato `functional-improvements-evidence` contém resultados JVM XML, saída integral dos testes instrumentados, dez fases de rede, verificações HTTP e capturas PNG obtidas no Android. A configuração compacta é 360×640 dp, fonte nativa 150%, API 30; não utiliza injeção de Density para simular acessibilidade. Nenhum APK oficial é publicado.
