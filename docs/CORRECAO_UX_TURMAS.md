# Edição, arquivamento e exclusão de turmas — Noctua 0.11.4 / 20

Base inspecionada: `09d8e10`, Noctua 0.11.3 / 19. Git limpo e marca oficial/limpeza de Relatórios preservadas.

## Fluxo

Cadastrar Turma apresenta **+ Nova Turma**, **Editar Turma** e **Turmas Arquivadas**, mantendo Gerenciar alunos. A página administrativa redundante foi eliminada. O formulário original conserva todos os campos, configurações adicionais e salvamento, com **Arquivar** e **Excluir** ao final da área rolável.

Arquivar pede uma confirmação simples e reutiliza `archiveClass`. Turmas Arquivadas abre o mesmo formulário, permitindo reativar e acessar relatórios, registros de aula e exportação sem reativação automática. Salvar a edição de uma turma arquivada não a seleciona como ativa.

Excluir abre diretamente **Excluir turma?**, identifica o nome da turma persistida e informa a irreversibilidade. **Não** cancela sem escrita; **Sim, excluir** usa a cor de erro e autoriza a transação. Não há frase digitada, contagens ou etapas adicionais. O nome e ID usados pertencem à turma que abriu o formulário; não são obtidos novamente do seletor global ao confirmar. Edições dos campos continuam sendo gravadas exclusivamente por **Salvar turma**; arquivar/excluir não salva os rascunhos dos campos.

Durante a conferência/gravação, os botões de confirmação e fechamento ficam indisponíveis para impedir duplicação ou fechamento prematuro. Falhas exibem mensagem curta, mantêm o diálogo e não apresentam sucesso. Fechar e tentar novamente obtém uma nova prévia. Após sucesso, volta a Cadastrar Turma, resolve e memoriza uma seleção ativa válida a partir do banco confirmado e mostra uma confirmação discreta.

## Integridade intocada

Nenhum arquivo de persistência, regras, relatórios, exportação, backup ou assinatura foi alterado. Room **v7**, as 14 tabelas e backup **8** permanecem idênticos; nenhuma migração nova. Pacote `br.com.diariodeclasse`, banco `diario.db` e marca oficial preservados.

O diálogo obtém internamente o mesmo `ClassDeletionPreview`. Somente após **Sim, excluir**, passa a autorização à API existente `deleteClass(plan, plan.confirmation)`. O Repository continua conferindo o fingerprint, vínculos e estado atual dentro da mesma transação, preservando históricos por IDs, origem, notas, recomposições e frequência. Não há DELETE direto na interface nem alteração de cascatas. Prévia obsoleta, falha de preservação ou de exclusão continua provocando rollback integral. A arquitetura detalhada permanece em [CORRECAO_EXCLUSAO_REMANEJAMENTOS.md](CORRECAO_EXCLUSAO_REMANEJAMENTOS.md); sua descrição de confirmação digitada documenta a interface anterior e é substituída somente neste aspecto.

## Validação

Testes Compose atualizados para o fluxo unificado: ausência de Gerenciar Turma, campos de edição, ações inferiores, nome correto no diálogo, ausência de digitação, cancelamento sem escrita, exclusão confirmada, seleção persistida, arquivamento/cancelamento/consulta/reativação e recriação da Activity. Incluído erro por trigger fictício com rollback, ausência de mensagem de sucesso e tentativa posterior bem-sucedida. Mantida a regressão Room de origens/intermediárias sucessivas, recomposições, relatórios/exportações, falhas transacionais, backup/restauração e reabertura.

CI atualiza a referência assinada para a release real 0.11.3, fixada por hash e certificado público. Mantém as sondas de atualização com a chave permanente já configurada, sem criar chaves ou modificar Secrets. Resultados finais e capturas serão registrados após a execução; compilação local não equivale a teste em aparelho físico.
