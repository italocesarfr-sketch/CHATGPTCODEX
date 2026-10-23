# NOcTuA Professor 0.17.0 — Horários, Chamada e Diário

Base: 0.16.0 / código 28. Incremento: 0.17.0 / código 29. Escopo exclusivamente local, sem sincronização ou novos cálculos acadêmicos.

## Uso

A segunda área reúne **Horários / Chamada / Diário**. No rodapé, os três nomes ocupam linhas separadas para permanecerem legíveis em celulares compactos. A Chamada continua sendo o modo inicial para preservar os caminhos anteriores. Com fonte ampliada, abas Material preservam os três nomes em uma linha; em larguras extremas, os controles se distribuem sem quebrar palavras. As áreas de toque mantêm no mínimo 48 dp.

Em **Horários**, consulte segunda a domingo; o dia atual é selecionado inicialmente. A grade independe da seleção de turma. **+ Horários** permite escolher uma turma ativa do ano, dia, períodos de 1º a 12º e vigência. A quantidade deriva dos períodos escolhidos. O mesmo período não pode ser ocupado por duas turmas no mesmo dia e turno. O turno é reaproveitado do cadastro da turma, permitindo, por exemplo, 1º horário matutino e 1º horário noturno sem conflito. A lista ordena turnos e períodos.

O botão **Abrir chamada** seleciona a turma sem criar aulas. Para mudar a rotina, edite os períodos e informe a nova vigência. A revisão mostra o intervalo atingido e datas que já possuem chamadas ou conteúdos. Retirar horários da grade cria outra revisão, sem apagar aulas. **Vigências** permite consultar outras datas e as revisões anteriores, inclusive suas turmas e períodos.

Os dois calendários usam a mesma grade: pontos menores indicam uma ou duas aulas previstas; quantidades maiores aparecem numericamente. Leitores de tela recebem a quantidade em texto. Os indicadores atuais de frequência, cancelamento e diário continuam independentes.

Na chamada de uma data sem horários, a quantidade prevista vem selecionada; apenas **Registrar horários** grava aulas. Aulas existentes, inclusive canceladas, nunca são substituídas pela sugestão. As posições da grade pertencem à rotina do professor; os horários já utilizados pela chamada permanecem sequenciais por turma/data e não são renumerados pela grade. É possível registrar aulas excepcionais fora da grade. Um único conteúdo do Diário pode corresponder a várias aulas e o preenchimento continua facultativo.

## Persistência e vigência

Room **v9** acrescenta somente `weekly_timetables`. As quatorze tabelas v8 permanecem intactas. `MIGRATION_8_9` cria a tabela e o índice único `(year, effective, revision)`. Não há migração destrutiva, geração de aulas ou alteração de registros existentes.

Cada linha é um retrato imutável da semana inteira: UUID, ano, início de vigência, número da revisão, instante de criação e períodos serializados. Cada período tem UUID, dia ISO 1–7, posição, ID da turma e identificação descritiva congelada. Para uma data, prevalece a maior revisão da última vigência daquele ano. O fim é implícito: véspera da próxima vigência ou 31/12. Não há intervalos sobrepostos; revisões de uma mesma data são auditáveis e apenas a última é vigente.

Uma alteração futura parte da rotina vigente na data indicada e não modifica versões posteriores já cadastradas. A prévia explicita seu intervalo. Corrigir a mesma vigência acrescenta uma revisão; não apaga a anterior. O campo legado `classes.schedule` continua preservado, sem conversão automática: suas quantidades não identificam posições concretas e convertê-las poderia inventar conflitos.

O retrato da turma no período permite consultar revisões mesmo depois de sua exclusão. Não é um vínculo acadêmico órfão: é uma referência histórica autocontida, sem FK que bloqueie a exclusão ou apague revisões por cascata. Turmas arquivadas mantêm seus períodos com indicação histórica; não podem ganhar novos períodos até serem reativadas. Para deixar de reservar períodos de uma turma arquivada/excluída, retire-os explicitamente em nova vigência.

A confirmação revalida todas as revisões do ano, conflitos, turmas ativas para períodos novos, intervalo e datas com registros. Alteração concorrente exige nova conferência. A gravação acontece em transação Room. Falhas não deixam revisão parcial. Nenhuma operação de grade escreve em `lessons`, `marks`, atividades, notas ou diários.

## Backup e atualização

Backup **formato 11**, incluindo todas as revisões e períodos. Leitura dos formatos **1–10** mantida com grade inicialmente vazia. Backup 11 incompleto, adulterado ou conflitante é rejeitado antes da restauração. Restauração conserva a cópia privada de segurança, validação, transação e rollback existentes.

Pacote `br.com.diariodeclasse`, banco `diario.db`, funcionamento offline e workflows de assinatura permanente preservados. Referência pública do teste de atualização: `docs/baseline/noctua-release-0.16.0.json`. Nenhuma chave, senha ou Secret é criado/alterado.

## Interface e limitações

Impeccable 4.7.0 consultado no diretório local `.agents/skills/impeccable`, com contexto, diretrizes nativas Android e craft floor. Aplicação: componentes Material 3, identidade atual, alvos de 48 dp, revisão antes de alterar vigências, estados vazios curtos e fontes ampliadas. Nenhuma tecnologia web foi introduzida.

Não há base institucional de feriados/recessos no esquema existente. A atualização não inventa essas datas e não cria qualquer registro automaticamente. Horários cancelados continuam identificados como cancelados, mesmo quando a rotina prevê aula. Horários de início/término não foram acrescentados: os períodos concretos bastam para esta versão.

## Validação

Resultados efetivos, capturas, commit e release serão registrados após execução do CI. A compilação local não substitui os testes Android nem a atualização assinada. Todos os cenários automatizados utilizam dados fictícios.
