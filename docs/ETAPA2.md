# Etapa 2 — Navegação e gestão de turmas

Escopo autorizado: incrementos 2.1–2.3 do ROADMAP. A autorização informa que a instalação original tem somente dados de teste descartáveis. A assinatura permanente continua obrigatória antes do uso real, mas não bloqueia esta implementação. A Etapa 3 não está autorizada.

## Implementação

- Quatro páginas independentes na navegação inferior: Cadastrar Turma, Fazer Chamada, Atividades e Notas e Relatórios. Perfis, lista e lançamentos são destinos secundários. Backup continua na barra superior.
- Seletor comum por ano e ID de turma, com disciplina/turno para distinguir nomes iguais. Último ano/turma gravados em SharedPreferences privadas; ID ausente usa uma turma disponível no mesmo ano, ou estado vazio. Nenhuma entidade escolar é criada pela navegação.
- Trocas aguardam a fila das gravações válidas, que conserva os IDs de origem. Uma nota inválida pede confirmação antes de sair. Formulários modais mantêm a turma de origem.
- Cadastro começa em Nova Turma / Editar Turma Existente, com nome, disciplina, etapa, turno e ano. Horário semanal é opcional, nas configurações adicionais; valores antigos continuam no banco. Edições sem mudança de horário não regeneram aulas. Ano da turma existente permanece bloqueado para preservar histórico.
- Lista de alunos com edição, ingresso/saída/status/observações, perfil e transferências; visualização alfabética ou número e reordenação por Subir/Descer.
- Colagem e CSV UTF-8 com conferência persistida em `import_drafts`. BOM, delimitadores, aspas, acentos, ordem e números explícitos são tratados. Linhas inválidas/ambíguas produzem erro sem importação parcial. Nome semelhante exige correção/exclusão ou confirmação de homônimo. Número repetido bloqueia o cadastro. Números vazios são atribuídos após o maior utilizado/informado.
- Nenhuma importação cadastra automaticamente. Todas as linhas precisam ser conferidas e a matrícula exige confirmação final; cancelar conserva o rascunho. Rascunhos de fotografia das versões antigas são recuperados na mesma conferência textual, inclusive incompletos.
- Câmera, galeria, recorte, ML Kit, Exif e integração de tarefas Play Services foram removidos. Matrículas já importadas e a tabela de rascunhos permanecem. Imagens sintéticas antigas de teste são apenas referências históricas, sem biblioteca OCR no APK.

## Preservação e limites

`applicationId=br.com.diariodeclasse`, banco `diario.db`, Room v1 e seu JSON/identidade permanecem iguais. Nenhuma migração foi necessária. Nenhuma rotina de inicialização apaga ou troca o banco. Backup, cópia anterior validada, confirmação literal e transação escolar da Etapa 1 continuam ativos.

O backup passa a emitir formato **2**, acrescentando `selection` ao payload para guardar o contexto visual; lê os formatos 1 e 2. Backups anteriores, inclusive resgate ADB, continuam aceitos. O app antigo só lê formato 1 e não deve receber backups novos. Preferências são aplicadas após o commit escolar; se sua gravação falhar, os registros restaurados permanecem e um aviso solicita selecionar turma/ano novamente. SQLite e SharedPreferences não formam uma transação única. Não há perda de notas/chamadas nessa falha de contexto visual.

As quatro páginas reutilizam calendário, chamada P/F/FJ/NR, atividades, cálculos e boletim existentes. A nova tabela P/F e definição simplificada dos horários por data ficam para a Etapa 3. Enquanto isso, numa turma sem horário semanal, use **+ Reposição / aula extraordinária** para criar cada aula na data escolhida. Não existe bloqueio pela data de criação da turma; ingresso real do aluno continua sendo respeitado.

Relatórios permanecem consultas na tela; exportação avançada, recuperação integrada e outras etapas não foram antecipadas. Tema acompanha o sistema. A validação de usabilidade com TalkBack, fonte ampliada, tablets e aparelho físico permanece para refinamento posterior.

## Validação

Candidato 0.2.0 (`versionCode=3`). Resultados do Actions e identificação do APK serão registrados após a execução; esta seção não declara compilação ou testes Android aprovados antecipadamente.

Localmente, a suíte Java de 28 cenários foi executada. O Gradle Wrapper não conseguiu baixar a distribuição neste sandbox; compilação Kotlin/Android, testes JVM adicionais e emulador serão executados no GitHub Actions. O CI verifica também Room v1 intacto, bloqueio de release sem chave estável e atualização com a mesma chave descartável do runner, comparando a fixture escolar antes/depois.
