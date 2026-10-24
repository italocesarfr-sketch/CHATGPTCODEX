# NOcTuA Professor 0.17.2 — Relatórios e seis períodos por turno

Versão validada: **0.17.2 / código 31**, sobre a release **0.17.1 original / 30**. CI, regressão Android, fontes ampliadas e atualização com a assinatura permanente aprovados em 24/10/2026.

## Relatórios

A tela mantém o bimestre e apresenta **Visualizar: Geral / Notas / Frequência**, depois **Ordenar alunos**, Buscar com opções secundárias e o boletim. Os três modos reutilizam as projeções existentes, sem reduzir as informações de Geral ou confundir zero, pendência e não aplicável.

A ordenação oferece Nº da chamada e Nome A–Z em todos os modos. Geral acrescenta maior/menor nota e mais/menos faltas; Notas acrescenta apenas os critérios de notas, e Frequência apenas os de faltas. Ao mudar a visualização, uma ordem incompatível volta imediatamente à chamada; uma compatível permanece. Os cálculos e desempates continuam em `SchoolReports.ordered`. A seleção repetida de um critério ativo continua restaurando a chamada, comportamento já existente.

A busca permanece global, em todas as turmas e anos, com os mesmos IDs, critérios de matrícula, acentuação e histórico. Agora ela respeita o modo e a ordem escolhidos. Cada grupo conserva a identificação da turma e suas próprias regras de cálculo. Rotação e mudança de bimestre preservam a consulta e os controles aplicáveis.

O ícone ao lado de Buscar contém somente Mostrar remanejados, quando aplicável, e Informações do relatório. Nenhum modo ou ordenação é repetido nesse menu.

O antigo Organizar passa a **Personalizar planilha** e continua abrindo o mesmo editor persistente de ordem, ocultação e linhas vazias. Exportar continua usando o bimestre e as preferências próprias da exportação. Ordenar o boletim nunca grava essas preferências. Os dois acessos ficam no fluxo de Relatórios, após Buscar e antes do conteúdo. Em fontes ampliadas, as ações passam a uma coluna para preservar a legibilidade.

A seleção comum usa o controle segmentado Material 3. Em larguras muito compactas ou fonte ampliada, as mesmas três opções usam chips de seleção única Material 3 com quebra de linha; não há redução da fonte, corte de palavras ou rolagem horizontal para navegar entre os modos.

## Grade semanal

O cadastro comum mostra somente 1º a 6º períodos, relativos ao turno já cadastrado da turma. O mesmo período pode coexistir em Matutino, Vespertino e Noturno; o bloqueio de conflitos dentro do mesmo turno é preservado.

A leitura histórica e a validação do backup continuam aceitando períodos antigos de 7º a 12º. O editor conserva esses períodos e os identifica em uma área separada, com Retirar e confirmação. A retirada altera apenas o rascunho até Salvar; a revisão anterior continua disponível. Nenhum horário acadêmico, frequência ou conteúdo é apagado ou renumerado.

Além de retirar os botões comuns acima do 6º, as prévias de edição rejeitam novos períodos acima desse limite. Somente períodos históricos identificados pelo mesmo ID, turma, dia e ordinal podem ser conservados. A validação geral dos dados históricos não foi estreitada para seis, evitando invalidar bancos ou backups antigos.

O primeiro cadastro anual e os alcances Hoje/ano inteiro da 0.17.1 permanecem intactos. Chamada e Diário continuam lendo a mesma grade, sem gerar lançamentos automaticamente.

## Integridade

- `br.com.diariodeclasse`, banco `diario.db`, Room v9 e backup formato 11 preservados.
- Sem nova migração, alteração de entidades Room ou formato de backup.
- Sem mudança nas fórmulas, frequência, recomposição, remanejamentos, diários ou exportadores.
- Certificado permanente e workflow de release preservados. O CI verifica atualização sobre o APK original 0.17.1, incluindo período antigo acima do 6º, com dados exclusivamente fictícios.

## Arquivos

Produção: `reports/ReportDisplay.kt`, `ui/ReportsScreen.kt`, `ui/DiaryApp.kt`, `ui/Components.kt`, `ui/TimetableScreen.kt`, `data/WeeklyTimetable.kt` (sob `app/src/main/java/br/com/diariodeclasse/`) e `app/build.gradle.kts`.

Testes de regras, Room e UI foram acrescentados ou adaptados aos novos pontos de acesso, mantendo as verificações de integridade, valores, persistência, layout, exportação e histórico. CI: `.github/workflows/android.yml`, `scripts/ci/validate-android.sh` e `docs/baseline/noctua-release-0.17.1.json`.

## Design e limitações

Impeccable 4.7.0 foi lido e aplicado: contexto, simplificação, diretrizes Android e craft. Implementação nativa Kotlin/Compose, identidade visual preservada, alvos de pelo menos 48 dp e testes de texto/rolagem em 320/360 dp e fontes ampliadas.

Foram inspecionadas capturas reais do emulador em tela compacta: modos, ordenação, boletim com fonte 1,6, ações responsivas e seis períodos. A rolagem conserva o acesso aos controles e à tabela. Validação física e abertura na interface do Microsoft Excel permanecem com o proprietário; os arquivos são conferidos por leitores independentes no CI.


## Regressão executada

Na execução [40044377918](https://github.com/italocesarfr-sketch/CHATGPTCODEX/actions/runs/40044377918), a validação principal passou:

- **167 testes JVM**, todos aprovados, sem falhas, erros ou ignorados.
- **275 casos Android nos XML: 271 aprovados, quatro sondas condicionais**, sem falhas ou erros. As sondas FrequencyProcessProbe, UpgradeProbe e StableUpgradeProbe têm execução específica; ReleaseContinuityProbe é legada e foi substituída pela sonda atual. Não se contam os quatro casos como aprovados na suíte geral.
- Grupos focados: 23 de horários, quatro de layout, 35 de regressão anterior e 27 de relatórios/exportação. São sobrepostos à suíte completa, não casos únicos adicionais.
- Dois testes em cada escala **real do Android 1,3 e 1,6**, além dos testes de layout em 320/360 dp. Verificam alvos de 48 dp, ausência de texto cortado, rolagem e ações acessíveis.
- Leitura independente de **91 XLSX, 89 CSV e 267 TSV**, com openpyxl 3.1.5 e Python csv: seis modelos, abrangências, dados completos/somente notas, ordem, Unicode, datas, zero e vazios. Os dois XLSX de planos integram o total de 91.
- Room, backup/restauração e persistência após reinício: preservação da grade histórica, dados reais de aula sintéticos P/F/FJ/NR, diário, notas, recomposição e remanejamentos. Nenhum dado escolar real foi usado.

Testes novos verificam menus compatíveis, retorno à chamada para critérios incompatíveis, preservação de critérios compatíveis, busca combinada com visualização/ordenação, rotação e bimestre. As preferências de exportação são comparadas antes/depois da consulta. Para horários, verificam seis períodos nos três turnos, bloqueio de conflito no mesmo turno e rejeição de um novo 7º período, mantendo a leitura de 7º–12º históricos. A retirada explícita de um período antigo preserva a revisão anterior e todos os lançamentos acadêmicos, inclusive nove aulas reais sintéticas no dia.

Os testes anteriores foram adaptados apenas para os novos pontos de acesso: ajuda e histórico no menu secundário; modos sempre acessíveis; ações roláveis dentro do cabeçalho. As verificações de valores, IDs, rollback, restauração, exportação e histórico permanecem.

### Capturas reais

As imagens abaixo vêm do artefato de resultados dessa execução, sem simulação gráfica ou dados reais de estudantes:

| Tela | Captura |
| --- | --- |
| Visualizar, Ordenar alunos e Buscar | [Relatórios em 320 dp](images/relatorios-0172/relatorios-visualizar-ordenar.png) |
| Boletim, nomes extensos e fonte ampliada | [Boletim com fonte 1,6](images/relatorios-0172/boletim-320-fonte-1.6.png) |
| Editor comum somente com 1º–6º | [Seis períodos](images/relatorios-0172/horarios-seis-periodos.png) |
| Ações com espaço para fonte ampliada | [Personalizar planilha e Exportar](images/relatorios-0172/personalizar-exportar-fonte-1.6.png) |

[Artefato completo de testes e capturas](https://github.com/italocesarfr-sketch/CHATGPTCODEX/actions/runs/40044377918/artifacts/12540628164), incluindo calendários de Chamada e Diário, histórico, telas de exportação e testes com escala real do sistema.


## Atualização assinada e entrega

O job de release passou com o certificado permanente existente. As **seis fases** foram conferidas nos arquivos de resultado baixados: antes da atualização, depois de `adb install -r`, restauração de backup, reinício, exclusão de turma com histórico preservado e novo reinício. Cada fase terminou em `OK (1 test)`. A semente é inteiramente fictícia e foi criada no **APK original da 0.17.1 / 30**, não em uma reconstrução aproximada.

A massa inclui período antigo 9º na grade, notas zero/pendentes, recomposição, remanejamentos com auditoria, frequências e conteúdos do diário. A atualização conserva a base; o teste posterior de edição anual conserva a revisão antiga. Não houve desinstalação, redefinição ou acesso a dispositivos reais.

Depois do download do artefato publicado, foram novamente verificados localmente assinatura APK v2, hash do APK, applicationId, versão e compatibilidade com o APK original 0.17.1. O certificado é idêntico. O hash do APK baixado coincide com o testado antes do upload. Nenhuma chave ou Secret foi criado ou modificado.

- **APK SHA-256:** `23b8123637ebbfbf31002ff66938b880906bdc3f592e818e00c345c3f420fc8a`.
- **Certificado SHA-256:** `6dbbba2d930eb7f7fbeff1ee82512f656251bbec9f4b31b6c5614528d29f1aae` (informação pública de verificação).
- [Commit de código aprovado](https://github.com/italocesarfr-sketch/CHATGPTCODEX/commit/6c31af5505d3e13925c8487f8ed7e0598320aa0e).
- [GitHub Actions aprovado](https://github.com/italocesarfr-sketch/CHATGPTCODEX/actions/runs/40044377918).
- [APK release assinado — baixar o ZIP e instalar app-release.apk sobre a instalação existente](https://github.com/italocesarfr-sketch/CHATGPTCODEX/actions/runs/40044377918/artifacts/12541357071).
- [Resultados das seis fases da atualização permanente](https://github.com/italocesarfr-sketch/CHATGPTCODEX/actions/runs/40044377918/artifacts/12541397123).
- [Resultados estruturados, contagens e lista integral de arquivos alterados](RELATORIOS_TURNOS_0172_RESULTADOS.json).

O artefato conserva o nome histórico `noctua-etapa6-release`; seu conteúdo verificado é **0.17.2 / 31**. Os digests dos ZIPs no JSON não devem ser confundidos com o hash do APK interno. O acesso aos artefatos exige permissão no repositório e está sujeito à retenção do Actions.

Esta validação não substitui a conferência final no aparelho do proprietário nem a abertura na interface do Microsoft Excel. Os leitores independentes e a atualização controlada foram efetivamente executados; não há falhas de teste pendentes. Depois desse commit de código, a finalização altera somente documentação e capturas.
