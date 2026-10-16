# Correção da Etapa 3 — Matrícula e frequência retroativa

Versão **0.3.1 / versionCode 5**. Correção autorizada após a entrega [0.3.0](ETAPA3.md), sem iniciar a Etapa 4. Compilação, testes Android e existência do APK foram verificados; resultados abaixo.

## Comportamento corrigido

A função compartilhada pelo cadastro individual e pela conferência de colagem/CSV usava o dia atual para o ingresso quando o ano da turma era o ano corrente. Isso confundia o dia de digitação no aplicativo com o início da matrícula, bloqueando corretamente a chamada segundo uma data padrão inadequada.

Agora novos alunos recebem **01/01 do ano letivo** como ingresso padrão. Em uma turma de 2026 cadastrada em outubro, basta cadastrar os alunos normalmente para registrar agosto; não é preciso editar o ingresso de cada aluno. Não se usa a data de cadastro no app como início da participação na turma. A data efetiva de cadastro não é gravada como um campo novo: esta correção não altera o esquema.

Os campos existentes de ingresso e saída continuam disponíveis para exceções reais. Ingresso posterior e saída anterior limitam a participação ao intervalo inclusivo configurado. Transferências continuam usando a data informada explicitamente. Os textos de ajuda explicam essa distinção, nos mesmos formulários; o layout, a navegação e a tabela P/F foram preservados.

## Preservação

- Nenhuma matrícula existente é reescrita, mesmo se seu ingresso estiver em outubro. Na base 0.3.0, não há marcador capaz de distinguir uma data escolhida manualmente de outra preenchida pelo padrão antigo. Alterar essas datas automaticamente poderia apagar informação real. Caso necessário, a revisão de um registro antigo deve ser explícita no formulário existente; esta correção não oferece reclassificação em massa.
- Room permanece na versão 1, com as mesmas oito entidades, IDs e relacionamentos, sem migração destrutiva. Backup/restauração e seus formatos permanecem iguais. Não há SQL de atualização de datas na abertura do app.
- As regras de frequência não foram afrouxadas: somente matrículas elegíveis recebem marcações, FJ segue separada, NR não vira falta e aulas canceladas não contam.
- Cadastrar aluno ou importar nomes não cria aulas nem frequência. Aulas/chamadas continuam dependendo das ações explícitas já existentes; não há presenças ou faltas criadas em dias sem chamada.
- Pacote, SDK e configuração de assinatura permanecem iguais; somente versão/código foram incrementados. Nenhum dado real ou chave privada foi publicado.

## Validação

1. Testes JVM do padrão de matrícula para o ano corrente e anos anteriores/futuros, inclusive bissexto.
2. Testes Compose do formulário individual e da conferência de lista verificam 01/01 pré-preenchido. Alterar uma data real e editar o aluno preserva essa data. Colagem confirmada persiste o novo padrão sem editar o ingresso.
3. Teste Room cria a turma com referência em outubro, cadastra aluno pelo padrão e importa CSV, realiza dois horários em agosto e usa presença coletiva. Matrículas com ingresso posterior ou saída anterior são excluídas e rejeitam marcações manuais. Cadastro/importação sem chamada não gera aulas nem marcas. Datas existentes e a falta registrada permanecem iguais depois de fechar/reabrir o banco.
4. Teste Compose cadastra um novo aluno sem tocar na data, registra agosto com dois horários, usa presença coletiva e P/F independentes. Verifica o bloqueio do aluno realmente ingressante depois, uma falta/50% no perfil acessado por Relatórios, correção para P, zero faltas/100%, recriação da Activity e reutilização das mesmas aulas. Relatórios não foram reformulados; refletem as marcações pelo cálculo existente.
5. Suíte anterior de Room, backup/restauração, navegação, notas, importação e frequência; sondas de reinício forçado após commit e atualização com fixture v1 sintética e a mesma chave de teste.

Os testes usam somente dados fictícios. Scripts de atualização são exclusivos do emulador descartável do CI. O funcionamento offline e a arquitetura de preservação existentes foram mantidos.

## Limites

O novo padrão se aplica aos novos cadastros, sem inferir a intenção das datas antigas. Não houve migração nem revisão automática de alunos existentes. A cobertura de emulador não substitui a conferência no celular físico. A assinatura debug de cada runner continua descartável; compatibilidade com APK anterior deve ser demonstrada comparando certificados, e a chave permanente segue pendente antes do uso real.

## Resultados finais — 16/10/2026

[Baixar APK 0.3.1 (ZIP)](https://github.com/italocesarfr-sketch/CHATGPTCODEX/actions/runs/38978133186/artifacts/12079149881) • [Actions aprovado](https://github.com/italocesarfr-sketch/CHATGPTCODEX/actions/runs/38978133186) • [Relatórios e capturas](https://github.com/italocesarfr-sketch/CHATGPTCODEX/actions/runs/38978133186/artifacts/12079179558). Extrair `app-debug.apk`. Artefato do APK disponível por 30 dias conforme workflow.

Código testado: [`5a071d5`](https://github.com/italocesarfr-sketch/CHATGPTCODEX/commit/5a071d547b5b0b8c8d9afac938395b4115e1d7fd). A documentação final é publicada em commit separado sem alterar esse código.

| Validação | Resultado |
| --- | --- |
| `testDebugUnitTest` | 20 aprovados, zero falhas |
| `connectedDebugAndroidTest`, API 30 | 46 aprovados, zero falhas |
| Reinício forçado após última gravação confirmada | Duas sondas aprovadas, processos separados |
| Atualização sobre referência v1 com mesma chave de teste | Duas sondas aprovadas; todas as oito tabelas preservadas |
| Testes de domínio locais | 28 cenários aprovados |
| `assembleDebug` e `assembleDebugAndroidTest` | Sucesso |
| Esquema Room v1 | JSON idêntico ao da referência |
| APK / assinatura | Arquivo baixado, hash conferido e assinatura v2 aprovada por `apksigner` no CI |
| Release sem chave permanente | Continua bloqueada, conforme projeto anterior |

Há duas sondas intencionalmente ignoradas na invocação principal do runner: ambas são executadas nas quatro fases exclusivas de reinício/atualização da tabela. Totais conferidos nos XMLs JUnit. Os novos testes validaram o cadastro sem editar o ingresso, colagem/CSV, exceções reais de matrícula, P/F, presença coletiva, correção de falta para presença e atualização imediata do perfil aberto pelos Relatórios. As suítes anteriores de notas e backup/restauração também passaram.

Arquivo verificado em `/workspace/diario-de-classe/app/build/outputs/apk/debug/app-debug.apk`, **10,325,025 bytes**, pacote `br.com.diariodeclasse`, versão **0.3.1 / código 5**.

- SHA-256 do APK: `15fe709c45bfaa63fb77e99c7c2d8cd9f2d6ef0f8d2e023c78ed46a3400e7714`.
- Certificado público SHA-256: `d6f78d14b655b13e69566cf4b05b70bdea3d3bbe732e7c5b5c62a32396ee02ff`.
- Evidências públicas: [etapa3-correcao-validacao.json](baseline/etapa3-correcao-validacao.json).

**Assinatura:** O certificado do APK debug difere do APK 0.3.0 analisado: este artefato não o atualiza diretamente. A instalação física não foi inspecionada. A configuração de assinatura não foi alterada; os runners geram chaves debug descartáveis. O teste controlado de atualização com chave igual comprova preservação nesse cenário e não comprova continuidade da assinatura real. Se houver dados a conservar, exportar/conferir backup antes de qualquer troca de instalação. A chave estável continua pendente antes do uso real. Nenhuma chave privada ou dado real foi publicado.

O SDK/Gradle foram usados no GitHub Actions com JDK 17, SDK 35 e emulador API 30; o ambiente local continua impedido de baixar Gradle pelo shell. O APK produzido no CI foi baixado e verificado localmente. A nova captura `04-matricula-retroativa.png` está nos relatórios, junto às capturas anteriores. Não houve validação em aparelho físico. **Somente esta correção da Etapa 3 foi implementada; a Etapa 4 aguarda autorização.**
