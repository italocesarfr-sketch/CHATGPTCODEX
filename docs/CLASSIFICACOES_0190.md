# NOcTuA Professor 0.19.0 — Produção textual e motivos opcionais de FJ

Base validada: 0.18.2 / 35. Nova versão: 0.19.0 / 36. **Compilação, testes JVM/Android, fontes reais e atualização release assinada aprovados.**

## Atividades

Tipo passa a oferecer, na ordem: Prova, Trabalho, Redação, Produção textual, Atividade prática, Seminário, Estudo dirigido e Outro. O campo já era texto: nenhuma atividade existente é reclassificada. Relatórios e exportadores continuam recebendo a atividade e suas notas, sem colunas novas ou cálculos diferentes.

## Faltas justificadas

Ao registrar FJ, o menu da própria célula passa a oferecer os motivos opcionais. Continua sendo possível seguir a chamada sem abrir ou preencher esse seletor. Opções: Atestado médico, Acompanhamento médico, Falecimento familiar, Convocação oficial, Atividade escolar ou esportiva e Outro. Sem motivo limpa a seleção; não é uma nova categoria. Sem texto livre, documentos ou confirmação adicional. Salvamento automático pela fila de comandos existente, com feedback de erro já utilizado pela chamada.

A chave continua sendo aula/horário e matrícula. Aula determina turma e data. Motivos de ocorrências e alunos diferentes permanecem independentes. Trocar FJ por P, F ou NR limpa o motivo; o segundo toque em FJ continua produzindo NR sem motivo. A ação explícita de copiar a primeira chamada copia também o motivo de uma FJ; marcar todos como presentes limpa os motivos substituídos. Cancelar um horário preserva seu histórico.

Um motivo alterado depois da abertura da seleção é revalidado dentro da transação. Uma seleção atrasada não recria FJ nem sobrescreve uma chamada concorrente. Matrículas e horários protegidos continuam bloqueados. A remoção administrativa de FJ preserva o motivo no registro de auditoria; exclusões individuais removem somente o campo da ocorrência excluída. Históricos preservados de turmas excluídas conservam seus motivos e IDs.

P, F, FJ e NR mantêm a semântica e os cálculos existentes. Motivo não abona a falta nem transforma FJ em presença. Relatórios, XLSX, CSV e TSV conservam suas colunas e dados acadêmicos; não exportam motivos em colunas inesperadas.

## Persistência e compatibilidade

Room **9 → 10**, com uma única migração aditiva: `ALTER TABLE marks ADD COLUMN reason TEXT DEFAULT NULL`. As quinze tabelas, chaves e registros permanecem. FJ antigas recebem valor nulo, sem preenchimento ou reinterpretação. Esquemas 1–9 congelados permanecem no repositório; esquema 10 é gerado pelo Room/KSP.

Backup **11 → 12**, banco 10. Leitura dos formatos 1–11 preservada. O campo opcional integra automaticamente o backup completo, os históricos independentes de turma e a auditoria das frequências retiradas. Validação rejeita motivos fora das seis categorias, motivos em P/F/NR e motivos não nulos declarados em backups de formatos anteriores. Não há limpeza de banco ou migração destrutiva.

Identificador `br.com.diariodeclasse`, banco `diario.db`, funcionamento offline e assinatura permanente mantidos. A referência da atualização assinada é o APK original 0.18.2, com hash fixado, não uma recompilação.

## Interface e testes

Impeccable 4.7.0 efetivamente consultado: contexto do alvo, modo Operate, diretrizes Android e craft. Componentes Material 3 existentes; seletor no próprio menu de FJ, com área de toque mínima de 48 dp e menu rolável, sem aumentar a altura das linhas. Identidade noturna e demais controles preservados.

Sete testes JVM novos verificam opções exatas, motivos opcionais, tipos inválidos, equivalência das projeções/cálculos/exportações, backup 12, leitura de backup 11 sem campo e auditoria antiga. As fixtures já lidas independentemente em XLSX/CSV/TSV também incluem Produção textual e FJ com motivo. Os testes anteriores de versão esperada foram atualizados para banco 10/backup 12, preservando as verificações de dados e esquemas antigos.

Dez testes Android novos cobrem migração, seis categorias, conversões P/F/FJ/NR, edição, limpeza, concorrência, rollback SQL, dois alunos/horários, reinício, backup/restauração, auditoria administrativa, exclusão da turma de origem com histórico e exclusão individual, criação/edição/reabertura de atividade e layout 320 dp/fontes 1,0/1,3/1,6.

Resultados finais, arquivos, capturas e APK registrados abaixo após validação efetiva. Dados exclusivamente fictícios; aparelho físico e leitor de tela permanecem validações manuais.

A validação de fonte do seletor inclui também `font_scale` real do Android em 1,3 e 1,6, com conferência da densidade do texto dentro da janela do menu. O teste de interface respeita o contrato anterior: criar abre as notas; salvar uma edição retorna à lista. A reabertura após recriação é feita a partir dessa lista, preservando as verificações de nome, categoria e demais atividades.

Na regressão completa, um teste anterior de lista longa encontrou acesso concorrente ao SnapshotStateObserver durante a busca iterativa por 100 cards. O teste passou a usar o índice determinístico da fixture, mantendo a rolagem até o último aluno, os controles de legibilidade/alinhamento e a comparação integral do snapshot. Nenhuma tela de alunos foi modificada nesta atualização.

## Validação Android executada

- **183 testes JVM aprovados**, sem falhas, erros ou casos ignorados.
- **303 casos Android nos XML: 299 aprovados e quatro sondas condicionais**, sem falhas ou erros. Frequência e atualização têm execuções próprias; ReleaseContinuityProbe é legada e não é contada como aprovada na suíte geral.
- Grupo novo de **10 testes** aprovado, também executado na regressão completa (sem contar os mesmos casos duas vezes).
- Menu de motivos validado adicionalmente com `font_scale` real 1,3 e 1,6: **um teste em cada escala**, ambos aprovados, incluindo densidade do texto da própria janela e rolagem para limpar o motivo.
- Grupos anteriores de alunos, exclusão individual, horários, históricos/remanejamentos e relatórios/exportação aprovados. Sondas de frequência/reinício e atualização debug também passaram.
- Leitura independente das planilhas existentes aprovada, incluindo as fixtures de Produção textual e FJ com motivo, com valores zero e pendentes preservados.

[Commit de código validado](https://github.com/italocesarfr-sketch/CHATGPTCODEX/commit/89cc459b2144d9b60b0cebe3ae8b817d7c33f7f6) · [Execução](https://github.com/italocesarfr-sketch/CHATGPTCODEX/actions/runs/40616867694) · [XML, testes e capturas](https://github.com/italocesarfr-sketch/CHATGPTCODEX/actions/runs/40616867694/artifacts/12785772621).

## Capturas reais conferidas

| Captura | Verificação |
| --- | --- |
| [Produção textual](images/classificacoes-0190/producao-textual.png) | Categoria selecionada no formulário existente |
| [Motivo da FJ](images/classificacoes-0190/motivo-fj.png) | Seis motivos, Sem motivo e indicação da seleção atual |
| [Fonte real 1,6](images/classificacoes-0190/motivos-fonte-1.6.png) | Texto completo, quebra de linha e opção de limpeza |
| [Chamada compacta](images/classificacoes-0190/chamada-compacta.png) | Controles P/F/FJ com a altura de linha anterior |

As capturas vêm de emulador API 30 e dados fictícios. O seletor usa o próprio menu da célula FJ; selecionar novamente a ação FJ limpa a marcação para NR, enquanto Sem motivo limpa somente a classificação e mantém FJ. As permissões de edição de chamadas históricas continuam as mesmas.

## Release assinada e preservação

O APK original da **0.18.2 / 35** foi instalado em emulador descartável e recebeu dados fictícios, antes de atualizar com `adb install -r` para **0.19.0 / 36**, sem desinstalação. As oito fases passaram: antes, após atualizar, backup/restauração, reinício, exclusão com histórico preservado, novo reinício, exclusão individual e reabertura. A comparação integral de snapshots confirmou a preservação de matrículas, atividades, notas, frequências, diários, auditorias, exportação e grade anteriores; FJ antigas continuam com motivo nulo.

A abertura e as restaurações verificaram Room v10 e seu hash gerado. Backups anteriores foram lidos e os novos backups formato 12 restaurados. O APK publicado foi baixado e verificado novamente, com assinatura v2 válida, mesmo certificado e identificador `br.com.diariodeclasse`, código maior e hash idêntico ao arquivo efetivamente testado. Nenhuma chave ou Secret foi alterado.

- **APK SHA-256:** `e0acaf8b5d46b4550da22ad19c8bbe64fd8d767ace50a55b4f6a520988d1fc97`.
- **Certificado SHA-256:** `6dbbba2d930eb7f7fbeff1ee82512f656251bbec9f4b31b6c5614528d29f1aae` (identificação pública).
- [GitHub Actions aprovado](https://github.com/italocesarfr-sketch/CHATGPTCODEX/actions/runs/40616867694).
- [APK release assinado — extrair o ZIP e instalar app-release.apk](https://github.com/italocesarfr-sketch/CHATGPTCODEX/actions/runs/40616867694/artifacts/12786033435).
- [Oito fases da atualização assinada](https://github.com/italocesarfr-sketch/CHATGPTCODEX/actions/runs/40616867694/artifacts/12786352645).
- [Resultados estruturados e lista completa de arquivos](CLASSIFICACOES_0190_RESULTADOS.json).

## Arquivos e limites

Implementação: `data/Classifications.kt`, `data/Database.kt`, `data/DiaryMigrations.kt`, `data/DiaryRepository.kt`, `data/RelocationRules.kt`, `backup/BackupCodec.kt`, `backup/BackupManager.kt`, `ui/Forms.kt`, `ui/FrequencyGrid.kt` e `ui/DayAttendanceScreen.kt`, sob `app/src/main/java/br/com/diariodeclasse`. Versionamento em `app/build.gradle.kts`; esquema novo em `app/schemas/.../10.json`. Também foram atualizados testes, referência original da release, automação de CI, README e esta documentação. O JSON acima lista todos os caminhos.

Aparelho físico e leitor de tela continuam como conferências manuais. A validação automatizada usa exclusivamente dados fictícios em emulador API 30. Motivos são classificações opcionais: não abonam faltas, não aparecem em colunas novas e seguem as permissões anteriores de edição. Não há falhas automatizadas pendentes. A finalização posterior ao commit de código `89cc459b2144d9b60b0cebe3ae8b817d7c33f7f6` altera somente documentação, README e capturas.
