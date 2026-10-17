# Etapa 4A — Primeiro incremento: atividades e recálculo

Entrega validada **0.4.0 / código 6**, sobre a [0.3.1](CORRECAO_ETAPA3.md). Este incremento autorizado cobre somente ações na lista de atividades e alteração explícita da pontuação máxima. A aprovação do cadastro, chamadas, lançamento e salvamento automático pelo professor em celular físico refere-se à base 0.3.1; não equivale à validação física deste candidato. Compilação, testes e identidade do APK foram verificados; resultados ao final.

## Uso

Cada cartão em **Atividades e Notas** tem ações visíveis **Lançar notas**, **Editar** e **Excluir**. Tocar no título também abre as notas. Editar reutiliza o formulário e admite nome, tipo, data, bimestre, máximo, descrição e observações. Após editar, a lista permanece aberta no bimestre salvo; criar uma nova atividade continua abrindo seu lançamento de notas. A data precisa pertencer ao bimestre escolhido, como antes.

Excluir abre uma prévia com o nome e a quantidade de notas efetivamente lançadas, contando zero e excluindo pendentes. A confirmação **Excluir permanentemente** informa que a atividade e todas as suas notas, inclusive linhas pendentes, serão apagadas. **Cancelar** não altera dados. A mesma confirmação é usada pela exclusão já existente dentro do lançamento. A contagem é lida do banco após a fila de gravações; se houver mudança posterior, a confirmação é recusada até atualizar/reabrir a prévia.

Ao alterar o máximo de uma atividade que tenha qualquer nota lançada, inclusive zero, o formulário mostra máximo anterior, novo máximo, contagem e até quatro exemplos de conversão com nomes. Há três escolhas:

- **Recalcular proporcionalmente:** `nota anterior × novo máximo ÷ máximo anterior`, tanto para reduzir quanto aumentar. Todas as linhas de notas da atividade são consideradas; pendência permanece pendência e zero permanece zero. Nenhuma linha inexistente é criada para preencher pendências.
- **Manter notas existentes:** conserva exatamente os valores; indisponível se alguma nota superar o novo máximo. A validação também existe no Repository, não apenas no botão.
- **Cancelar:** descarta a prévia e volta ao formulário, sem salvar a atividade nem notas. Fechar o formulário mantém os dados persistidos.

Se não houver nota lançada ou se o máximo não mudar, o formulário salva sem solicitar conversão. A soma do boletim é atualizada automaticamente pelas regras existentes; não há normalização proporcional da escala da turma, recuperação ou alteração de outras atividades.

## Precisão e consistência

O cálculo proporcional usa `BigDecimal` com `DECIMAL128` (34 algarismos) e só converte para `Double` ao persistir no campo REAL existente. Não há arredondamento para centésimos em cada alteração. `Double` mantém aproximadamente 15–17 algarismos significativos; não é uma representação decimal exata de todo número. Boletins continuam mostrando duas casas decimais. Campos de edição usam a precisão armazenada, evitando que editar apenas um nome arredonde o máximo ou que reabrir uma nota a reduza às duas casas exibidas no boletim. Um resultado positivo pequeno demais para ser representado não é convertido silenciosamente em zero: a operação é recusada.

A prévia contém a atividade e todas as suas linhas de notas. Antes de confirmar, uma transação Room verifica se esse conjunto continua igual. Se mudou, pede nova prévia. Na mesma transação, salva a atividade e cada nota convertida mantendo IDs/chaves. Uma falha em qualquer escrita desfaz tudo, inclusive o novo máximo. A exclusão usa a FK CASCADE existente dentro da transação; falha na exclusão de nota também desfaz a atividade. Consultas e totais são atualizados pelos fluxos Room existentes.

**Room v1 e suas oito tabelas permanecem iguais**, sem migração, campos novos, fallback destrutivo ou regravação na abertura. Backup/restauração e seus formatos permanecem intactos. Pacote e configuração de assinatura não mudam. Chamadas, turmas, matrícula padrão 01/01, notas pendentes, zero e persistência anterior continuam cobertos pela regressão. A edição de atividade a partir de uma tela com nota inválida passa pelo aviso existente antes de descartar aquele valor não salvo.

## Testes deste incremento

- JVM: exemplos 10→1 (8→0,8), 5→2 (4→1,6), 2→5 (1,5→3,75); zero/pendente; 1.000 ciclos de três conversões com tolerância de 1e−12; limites inválidos e perda por subfluxo.
- Room: exemplos exigidos, manter em aumento/redução compatíveis, bloqueio de manter/incompatível e de alteração sem prévia, cancelamento sem escrita, prévia desatualizada, identificação das linhas e isolamento de outras atividades, edição dos metadados, restauração de backup e reabertura do banco.
- Falhas simuladas por triggers no banco exclusivo de teste: abortar após a atividade e uma nota já terem sido escritas; abortar na exclusão em cascata. Em ambos os casos, comparar todo o snapshot anterior/posterior.
- Compose: editar todos os campos pela lista sem abrir notas, excluir com/sem notas, contagem incluindo zero, cancelar exclusão, prévia/guardar compatível/bloquear incompatível/cancelar recálculo, zeros/pendências, atualização do perfil via Relatórios, reabertura/recriação e precisão dos campos.
- A sonda de reinício forçado existente agora verifica também máximo 1 e nota 0,8 após conversão confirmada. A suíte anterior e o teste de atualização da referência v1 com chave igual continuam no CI.

As fixtures são exclusivamente fictícias e não são carregadas no aplicativo do professor. Capturas ficam nos artefatos do CI. Limites: automação em emulador não substitui teste físico desta versão, TalkBack, fonte ampliada e aparelhos adicionais. A fila em memória continua não garantindo gravação ainda pendente após encerramento forçado; a sonda verifica valores já confirmados no Room.

## Escopo seguinte

Exportação inteligente, recuperação, duplicação de atividades, relatórios avançados e os demais incrementos do roadmap não foram implementados. Aguardar autorização. A assinatura permanente continua pendente antes do uso com dados reais; cada APK debug precisa ter seu certificado comparado antes de instalação sobre outro.

## Resultado da entrega — 16/10/2026 (São Paulo)

[Baixar APK 0.4.0 (ZIP)](https://github.com/italocesarfr-sketch/CHATGPTCODEX/actions/runs/38985770801/artifacts/12083077686) • [Actions aprovado](https://github.com/italocesarfr-sketch/CHATGPTCODEX/actions/runs/38985770801) • [Relatórios e capturas](https://github.com/italocesarfr-sketch/CHATGPTCODEX/actions/runs/38985770801/artifacts/12083451234). Extrair `app-debug.apk`. Retenção do APK: 30 dias. Código testado: [`962a18d`](https://github.com/italocesarfr-sketch/CHATGPTCODEX/commit/962a18db56416d974b6559cac052aa910415fd6c). O commit documental posterior mantém o mesmo código.

| Verificação | Resultado |
| --- | --- |
| `testDebugUnitTest` | 24 aprovados, zero falhas |
| `connectedDebugAndroidTest`, emulador API 30 | 62 aprovados, zero falhas |
| Reinício forçado após commit de chamada e recálculo | Duas sondas aprovadas, em processos separados |
| Atualização controlada da referência v1 | Duas sondas aprovadas com mesma chave; oito tabelas preservadas |
| Regras Java locais | 28 cenários aprovados |
| `assembleDebug` / `assembleDebugAndroidTest` | Sucesso |
| Room v1 | JSON idêntico; identity `99b868cf68cf5dffb82edf267ddc78be` |
| Assinatura | `apksigner verify` aprovado, esquema APK v2 |
| Release sem chave estável | Bloqueada, conforme política existente |

As duas sondas ignoradas na suíte principal são executadas com argumentos exclusivos nas quatro fases de reinício/atualização acima. Contagens conferidas nos XMLs JUnit. Novos cenários: quatro testes JVM de proporcionalidade/precisão/limites, dez de integração e seis de interface. As regressões anteriores de cadastro, CSV, frequência, notas, persistência e backup também passaram. As capturas da janela principal/lista estão em `build/etapa4a-telas.tar` nos relatórios. As tentativas de capturar os diálogos gravaram a janela principal; a contagem, as prévias e os botões dos diálogos foram verificados pelos testes de interação, sem inspeção visual dos diálogos nesta entrega.

APK baixado do CI, ZIP íntegro, SHA-256 conferido e existência verificada em `/workspace/diario-de-classe/app/build/outputs/apk/debug/app-debug.apk`. Tamanho: **10,357,793 bytes**; pacote `br.com.diariodeclasse`; versão **0.4.0 / código 6**.

- SHA-256 do APK: `f876a9951c040f876c3f0b400bb26981fb27b521cbc3130274bec6511bb2b1dd`.
- Certificado público SHA-256: `0f9ee6e89bc8307d5359ab594f8514d511afef3e63585901d2117d14f689dea8`.
- Evidências: [etapa4a-validacao.json](baseline/etapa4a-validacao.json).

**Assinatura:** O certificado debug difere do APK 0.3.1 publicado; este artefato não o atualiza diretamente. A configuração de assinatura não foi alterada; os runners usam chaves debug descartáveis. O teste controlado com a mesma chave comprova preservação nesse cenário, sem comprovar continuidade da assinatura do celular. Antes de qualquer troca de instalação com dados a conservar, exportar e conferir o backup. Nenhuma chave privada ou dado real foi publicado. A chave permanente continua pendente antes do uso real.

A compilação real e os testes Android ocorreram no Actions com JDK 17, SDK 35 e emulador API 30. O ambiente local segue sem conseguir baixar Gradle pela rede do shell; o APK do CI foi baixado e verificado neste workspace. Esta versão ainda requer ensaio físico pelo professor; a aprovação física anterior é da base 0.3.1. **Somente o primeiro incremento solicitado foi concluído; os próximos incrementos aguardam autorização.**
