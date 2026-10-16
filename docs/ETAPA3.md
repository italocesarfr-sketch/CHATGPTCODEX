# Etapa 3 — Chamada por data e horário

Entrega validada: **0.3.0 / versionCode 4**. Escopo autorizado: somente Etapa 3 do [ROADMAP](ROADMAP.md), seguindo [ESPECIFICACAO](ESPECIFICACAO.md) e [ETAPA2](ETAPA2.md). A Etapa 4 não foi iniciada. Os resultados de compilação, testes e identidade do APK estão registrados abaixo.

## Fluxo da chamada

1. No seletor global, escolher ano/turma e abrir **Fazer Chamada**.
2. Escolher qualquer mês e data do ano letivo, inclusive retroativa. Navegar não grava nem gera aulas.
3. Informar **1–12 horários**, usando os atalhos 1, 2, 3 ou Mais, e tocar em **Registrar horários**. O limite inicial segue o ROADMAP. Nenhuma grade semanal é necessária.
4. Tocar diretamente em **P** ou **F** em cada horário. O menu secundário mostra a marcação atual e oferece **FJ** ou **NR**. Os nomes ficam fixos ao deslizar as colunas. Tocar no nome mostra o texto completo e as datas de matrícula.
5. **Marcar todos como presentes** pede confirmação. O menu do cabeçalho de cada horário permite copiar a primeira chamada realizada anterior, cancelar, reativar ou guardar uma observação; cópia e reativação também pedem confirmação.
6. **Horários → Alterar** admite 0–12. Reduzir cancela explicitamente os excedentes depois da confirmação, sem excluir aulas ou marcações. Aumentar reutiliza os mesmos ordinais/IDs; um horário cancelado só volta aos totais após reativação explícita.
7. **Visão bimestral** reúne alunos nas linhas, datas/horários cronológicos nas colunas e totais **F / FJ** ao final, com rolagem horizontal e vertical. Tocar em uma célula abre a data para correção. A composição das colunas é limitada à janela visível para não criar todas as células de um período simultaneamente.

O calendário distingue chamada preenchida (✓), parcial (•), sem registros válidos (NR), cancelada (×) e aula prevista legada (◇). O dia mostra a cobertura dos registros válidos entre os alunos matriculados. Fora dos intervalos dos bimestres, há indicação de participação somente no resumo anual. Alterar os bimestres valida os intervalos e pede confirmação com prévia de quantas aulas realizadas mudam de classificação; não reescreve chamadas. Atividades mantêm os bimestres já atribuídos.

## Regras e preservação

- **Room v1, oito tabelas e JSON de esquema permanecem iguais**, sem migração destrutiva. `applicationId` continua `br.com.diariodeclasse`. Backup, restauração validada/cópia anterior e formato 2 (leitura de 1/2) continuam existentes.
- `setDayHours` usa transação e a chave única turma/data/ordinal; preserva IDs antigos, inclusive IDs arbitrários, notas e marcações. Reabrir uma data não cria aulas. Confirmar horários não apaga cancelamentos anteriores.
- A geração semanal automática foi retirada do Repository. Edição da turma ou dos períodos não regenera nem remove aulas. Campos semanais legados permanecem armazenados, sem comandar novas aulas. Aulas PREVISTA antigas continuam visíveis e podem ser confirmadas individualmente ou no dia; não contam enquanto não realizadas.
- NR ou ausência de linha não são faltas; canceladas/previstas não participam da frequência. O intervalo de matrícula é inclusivo; marcações anteriores preservadas fora dele ficam excluídas dos totais, e novos lançamentos fora da matrícula são bloqueados.
- Cada horário é independente: P/F resulta em uma falta, F/F em duas. FJ permanece separada e segue a configuração administrativa existente da turma. A fórmula compartilhada por perfil e boletim continua a considerar apenas aulas realizadas e registros válidos. Notas e demais funcionalidades não foram reformuladas.
- Por padrão começa em NR; a preferência anterior da turma para iniciar com P é respeitada. Nunca há criação automática de F.

## Salvamento e interrupções

As ações são serializadas por uma fila pertencente ao processo do aplicativo. Cada ação captura IDs de origem; trocar turma, data ou recriar Activity/ViewModel não cancela uma gravação já enviada à fila. A navegação aguarda as ações anteriores. O estado exibe **Salvando…**, **Salvo no aparelho** ou erro com opção de tentar novamente. Uma gravação bem-sucedida em outra célula/turma não esconde falhas de frequência pendentes; nova gravação bem-sucedida da mesma célula substitui sua intenção anterior. Descartar pendências pede confirmação e não altera o banco.

Somente o **commit Room concluído** é durável. Uma edição ainda em “Salvando…” pode não sobreviver ao encerramento forçado do processo, falta de energia ou perda do aparelho. Não há promessa de persistência da fila em memória. O teste de reinício forçado verifica a última marcação **confirmada**, não uma gravação ainda pendente. Após restauração confirmada, intenções que falharam no conjunto anterior são descartadas, impedindo reenvio sobre os dados restaurados. Falhas de restauração não descartam essas intenções.

## Validação reproduzível

- `./scripts/test-domain.sh`: regras Java offline.
- `./gradlew testDebugUnitTest assembleDebug assembleDebugAndroidTest`: testes JVM e APKs.
- `./gradlew connectedDebugAndroidTest`: integração Room/backup/seleção, Compose das quatro páginas e da chamada, fila de gravações e regressões anteriores.
- `scripts/ci/validate-android.sh`: somente em emulador descartável do GitHub Actions; recolhe capturas sintéticas, executa sondas de gravação/reinício em processos separados e o teste anterior de atualização por cima da referência v1. Nunca executar os scripts de fixture/upgrade em um aparelho com dados reais.

Novos cenários: retroatividade, 1/2/3/12 horários, limite do ano e ano bissexto, reabertura idempotente, cancelamento/reativação, FJ/NR, matrícula tardia, cópia que limpa o destino quando a origem é NR, bimestre editado, backup de IDs legados, reabertura do banco e fila com falha simulada. Os testes de interface verificam P/F independentes, nome fixo durante rolagem, troca de turma/recriação, marcação coletiva, justificativa/cancelamento e consulta bimestral sem escrita por navegação. Todas as fixtures são fictícias e separadas do banco do professor.

## Limites da entrega

A chave estável segue pendente antes do uso real, conforme autorização da Etapa 2. A configuração de assinatura não foi trocada, mas cada runner pode gerar outro certificado debug: mesmo pacote não prova atualização compatível. A comparação com o APK de teste anterior e o teste controlado com a mesma chave constam abaixo. Não haverá declaração de compatibilidade com a instalação física sem verificar seu certificado.

Capturas/automação em emulador não substituem ensaio em celular físico, TalkBack, fonte ampliada, tablet, API 26 e Android atual. Não foi introduzido serviço remoto, permissão de Internet ou exportação inteligente.

## Resultado final — 16/10/2026

Código testado: [`656c8c4`](https://github.com/italocesarfr-sketch/CHATGPTCODEX/commit/656c8c43961aa9e1d0339258c1397d1d502dcb94). [GitHub Actions aprovado](https://github.com/italocesarfr-sketch/CHATGPTCODEX/actions/runs/38964999986) • [Baixar APK de teste](https://github.com/italocesarfr-sketch/CHATGPTCODEX/actions/runs/38964999986/artifacts/12073719360) • [Relatórios e capturas](https://github.com/italocesarfr-sketch/CHATGPTCODEX/actions/runs/38964999986/artifacts/12074790782). O download do artefato é um ZIP; extrair `app-debug.apk`. Retenção do APK: 30 dias, conforme workflow.

| Verificação | Resultado |
| --- | --- |
| `testDebugUnitTest` | 18 aprovados, zero falhas |
| `connectedDebugAndroidTest`, emulador API 30 | 44 aprovados, zero falhas; duas sondas reservadas ignoradas nesta invocação |
| Sondas de gravação/reinício forçado | 2 aprovadas, em processos separados, antes/depois de `am force-stop` |
| Sondas de atualização controlada | 2 aprovadas; referência v1 → candidato sem desinstalar, oito tabelas iguais |
| Regras Java executadas localmente | 28 cenários aprovados |
| `assembleDebug` / `assembleDebugAndroidTest` | Sucesso |
| Esquema Room v1 | JSON idêntico; identity `99b868cf68cf5dffb82edf267ddc78be` |
| Assinatura criptográfica | `apksigner verify` aprovado, esquema APK v2 |
| Release sem chave estável | Bloqueada, como esperado |

As duas sondas marcadas como ignoradas na suíte principal são executadas pelo CI com parâmetros exclusivos nas quatro fases separadas indicadas acima; não representam cenários sem execução. A saída bruta do runner contém contagem de eventos adicional; os totais acima foram conferidos nos XMLs JUnit, com zero falhas/erros.

APK baixado do Actions, ZIP testado e hash conferido com o arquivo publicado pelo CI. Existência verificada em **`/workspace/diario-de-classe/app/build/outputs/apk/debug/app-debug.apk`**, com **10,325,025 bytes**, versão **0.3.0 / código 4**, pacote `br.com.diariodeclasse`.

- SHA-256 do APK: `b75d0b569209af4a9d90a97174dbb05dc955f4a2f9e6ea823bc2f350716ab74f`.
- Certificado público SHA-256: `b15f0b96fff091d348531952ed5ef92a320b3d820771eeb7c71d915b664624e9`.
- JSON público de evidências: [etapa3-validacao.json](baseline/etapa3-validacao.json).

**O certificado debug difere do APK 0.2.0 da Etapa 2 e do original analisado; este artefato não atualiza diretamente esses APKs.** A chave permanente continua pendente. O celular físico não foi inspecionado. O teste de atualização usou a mesma chave descartável nos dois APKs exclusivamente no emulador, comprovando preservação quando a assinatura é compatível. Não houve publicação de chaves privadas ou dados reais. Não é necessário recuperar os testes descartáveis da instalação original, conforme autorização; uma instalação limpa com chave permanente ainda deverá ser planejada antes do uso real. Se houver registros novos a conservar, exportar e conferir backup antes de qualquer troca de instalação.

Capturas revistas em tela de 320 px: calendário, chamada dupla com P/F independentes, matrícula tardia bloqueada, cancelamento com FJ preservada, nomes fixos na rolagem e tabela bimestral. O rótulo do total foi abreviado para caber na coluna estreita. A cobertura automática não equivale a homologação de TalkBack/fonte ampliada ou ensaio no aparelho físico; esses limites continuam descritos acima.

O ambiente local não permitiu baixar a distribuição Gradle pela rede do shell; a compilação real e os testes Android foram executados no Actions com JDK 17, SDK 35 e emulador API 30. O APK entregue foi baixado dessa execução e verificado localmente. A documentação final é publicada separadamente, sem alterar o código testado. **A Etapa 4 e a exportação inteligente não foram iniciadas; aguardam autorização.**
