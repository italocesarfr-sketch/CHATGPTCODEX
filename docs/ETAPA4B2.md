# Etapa 4B.2 — Recomposição de Notas

Candidato **Noctua 0.6.0 / código 9**, baseado na 0.5.0 / código 8. Implementação restrita à recomposição e sua integração aos relatórios. Resultados de execução serão acrescentados após a validação, sem apresentar testes pendentes como aprovados.

## Uso

Em **Atividades e Notas → Recomposição de Notas**, escolha o ano/turma nos seletores globais e o **bimestre de origem** na tela. A data da avaliação é independente desse bimestre: uma recomposição feita em novembro pode alterar somente o 3º bimestre.

O filtro inicial mostra somente resultados vigentes conhecidos abaixo de **5,00**. **Todos os alunos** permite consultar pendências, alunos que já atingiram 5 e corrigir qualquer lançamento, incluindo matrículas transferidas com histórico. Atingir 5 após salvar remove o aluno do filtro inicial, sem apagar a matrícula ou o registro.

Toque em **Lançar recomposição / Editar recomposição**. O formulário apresenta original, nota de 0 a 10, data e prévia do resultado. Confirme em **Salvar recomposição**. Vazio significa não realizada; `0` é nota zero. O formulário bloqueia a interação com filtros, turma, ano e navegação de fundo; sair com alterações requer confirmação de descarte. Voltar e cancelar a confirmação conservam o rascunho. A recriação da Activity conserva os campos. Durante a gravação, entradas e saída são bloqueadas; falhas mantêm o formulário aberto com erro. A fila de gravações anterior continua sendo utilizada. **Excluir recomposição** exige confirmação e volta a considerar o resultado original, sem alterar atividades.

## Contrato de cálculo

- Original: soma elegível das atividades, calculada pelas mesmas funções já aprovadas (`DiarySnapshot.result` / `SchoolRules.grades`). Não é uma cópia congelada: correções das atividades continuam refletidas no original.
- Vigente com recomposição realizada: `min(10, max(original, recomposição))`. A função pura `RecompositionRules.effective` é compartilhada pelas projeções e prévias.
- Sem recomposição realizada, conserva-se o resultado anterior, inclusive a escala configurada da turma. A nova recomposição tem sempre escala 0–10. Para turmas legadas com outra escala, a tela informa essa diferença e o limite de 10 do resultado recomposto; as notas das atividades permanecem intactas.
- Sem nenhuma nota original elegível lançada, original e vigente permanecem **Pendentes**, inclusive quando houver uma nota de recomposição registrada. Não se presume original zero. O lançamento fica guardado e passa a ser considerado quando houver resultado original. Uma soma parcial conhecida continua parcial e pode aparecer no filtro abaixo de 5; as pendências originais não desaparecem.
- Ex.: 3 e 5 → 5; 4,5 e 3 → 4,5; zero e zero → zero. Editar/excluir parte sempre do original, sem somas acumuladas.
- Recuperação comum continua uma atividade regular, com pontos definidos manualmente. Não há recuperação aditiva automática, limite de 7,5 ou média anual inventada.

O boletim, a ordenação por nota e o resumo anual apresentam o vigente. Quando há recomposição, o boletim acrescenta original e recomposição em colunas próprias; os históricos individuais (Relatórios e cadastro) distinguem os três valores e a data. A recomposição não vira coluna de atividade e não integra sua soma. As regras e relatórios de frequência permanecem inalterados.

## Room e preservação

O esquema v2 foi gerado pelo KSP/Room, com identidade `32104b8df79c150ac61b09fadc450221`; a comparação estrutural confirmou que as oito entidades v1 permanecem idênticas. O JSON v1 conserva SHA-256 `43fe5f77af75d462dff34128582fdeb6cf759b9504fa2bb6ee468a6951ec3ed7`. O esquema passa de **1 para 2** por `MIGRATION_1_2`, que somente cria `recompositions`. As oito tabelas v1, seus valores, índices, IDs e relacionamentos permanecem intactos. A chave da nova tabela é `(studentId, term)`, com FK restritiva para a matrícula; a turma e o ano vêm dessa matrícula, evitando vínculos redundantes. `value` é anulável e `date` guarda a data da avaliação. Existe um lançamento atual por matrícula/bimestre; edição substitui esse lançamento, sem manter um histórico de tentativas. Original das atividades e nota de recomposição continuam separados.

Repository valida matrícula, bimestre, nota finita 0–10 e data. Gravação/exclusão transacionais verificam se o registro ainda coincide com a versão aberta no formulário, recusando sobrescritas de uma edição obsoleta. Room, Flows, aplicação offline, `br.com.diariodeclasse`, `diario.db` e preferências existentes continuam em uso. Não existe fallback destrutivo, limpeza ou recriação do banco em uma atualização.

## Backup e restauração

O novo formato **3 / banco 2** inclui todas as nove tabelas, IDs e preferências. O leitor continua aceitando formatos **1 e 2 / banco 1**, conferindo o checksum do conteúdo original e sua identidade de esquema antes de validar os dados. Backups antigos recebem uma lista vazia de recomposições; nunca se inventam notas.

A restauração continua completa, com prévia e confirmação `RESTAURAR`, controle de alteração após prévia, cópia de segurança validada **antes** de qualquer substituição e transação única com verificação de vínculos e conteúdo. A nova tabela é removida antes das matrículas e inserida depois dos pais. Restaurar um backup antigo substitui integralmente o conjunto, inclusive deixando sem recomposições; as recomposições atuais ficam na cópia de segurança anterior à restauração. Não há fusão silenciosa de arquivos.

A versão 0.5.0 não lê o formato 3. Não fazer downgrade sobre o banco v2 nem desinstalar para contornar erro de atualização. Guarde backup válido antes de atualizar. Os arquivos de backup contêm dados pessoais e devem permanecer sob guarda do professor.

## Assinatura e validação planejada

A assinatura permanente e `.github/workflows/release.yml` permanecem intactos. Não foram criadas/substituídas chaves nem alterados Secrets. O CI mantém os testes debug e o ensaio release que reutiliza a configuração existente do proprietário. A referência agora é o APK **0.5.0 real**, execução `39032589083`, artefato `12101495282`, com hash/certificado públicos em `baseline/noctua-release-0.5.0.json`.

O ensaio instala a referência em emulador novo, cria somente fixture sintética, atualiza por cima, verifica integralmente o legado, restaura backup antigo, cria recomposições, valida novo backup/restauração e reabre o processo. O workflow manual de release continua disponível ao proprietário. Scripts de atualização recusam aparelhos físicos. Nenhuma compatibilidade com APK debug de outra assinatura é presumida.

Testes acrescentados: regras maior/menor/igual, zero/nulo/pendente e limite; projeções dos quatro períodos e isolamento de origem; Room CRUD, conflitos e falhas simuladas, persistência, backup atual/antigo e migração v1 completa por `MigrationTestHelper`; Compose filtros, passagem para 5, edição/exclusão confirmadas, descarte/recriação e atualização dos relatórios. Executar também a regressão anterior de atividades, recálculo proporcional, chamadas e backup.

## Resultados

Ainda em validação. O resultado definitivo, os links de execução/artefatos e os limites observados serão registrados após o CI. Testes em emulador não substituem a conferência deste incremento em celular físico. Exportação, registros de aula e demais incrementos não fazem parte desta entrega.
