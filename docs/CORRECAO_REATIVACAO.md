# Correção 0.7.1 — Reativação e remanejamento

Base: **Noctua 0.7.0 / código 11**. Candidata: **0.7.1 / código 12**. Escopo exclusivo: consistência da reativação e validações diretamente relacionadas. Nenhuma Etapa 5, regra pedagógica, reformulação visual ou exportação.

## Reprodução e causa confirmada antes da correção

O teste Room `transferredThenReactivatedEnrollmentCanRelocateAfterReopenWithoutLosingHistory` foi publicado e executado antes de modificar produção. Na [execução 39065254583](https://github.com/italocesarfr-sketch/CHATGPTCODEX/actions/runs/39065254583), a sequência Transferido → Ativo → fechar/reabrir banco → conferir remanejamento em 17/10/2026 falhou com a mensagem exata: **“Esta matrícula já foi encerrada/remanejada. Consulte o histórico.”** Os demais testes da suíte Android não falharam.

No `StudentForm`, o seletor alterava somente `status`. A data `left` continuava preenchida e era reenviada no salvamento. O ViewModel encaminhava esses valores pela fila existente, sem convertê-los. `saveStudent` aceitava e persistia `Ativo` com a saída antiga. Não era só um texto desatualizado na tela: após reabrir Room, o status era Ativo e o intervalo permanecia encerrado. `previewRelocation` reunia status, saída e vínculo em uma única condição, produzindo a mensagem genérica.

Uma transferência administrativa simples é uma edição de situação/saída. Um remanejamento consolidado possui vínculo em `relocations`, que não pode ser apagado ou reaberto pelo formulário. Transferências administrativas legadas também podem possuir outra matrícula da mesma pessoa sem vínculo explícito; essa condição exige proteção contra sobreposição.

A publicação inicial do teste ficou com uma árvore incompleta; foi imediatamente recomposta a partir da árvore integral da 0.7.0. A reprodução só foi acionada após conferir os 147 arquivos remotos e confirmar que somente o teste diferia da versão de referência. Nenhum arquivo local ou dado escolar foi apagado.

## Comportamento corrigido

- Ao salvar uma alteração explícita de **Transferido para Ativo**, a saída anterior é retirada na mesma transação. O formulário informa esse efeito antes de salvar. Ingresso, saída alternativa e reativação devem ser confirmados separadamente.
- Uma edição de nome, número ou observação de um aluno já Ativo **não** retira sua saída. Datas de saída legítimas continuam válidas. Abrir o aplicativo, navegar, atualizar ou restaurar backup nunca reativa matrículas automaticamente.
- O cadastro Ativo com saída oferece **Confirmar reativação**. A confirmação identifica a matrícula/data e só retira a saída se o registro ainda for exatamente o conferido. Cancelar não grava nada. O formulário fecha após o reparo, para que qualquer remanejamento use o estado confirmado.
- IDs de matrícula e pessoa, ingresso, observações, notas, chamadas e recomposições permanecem preservados. Nenhuma presença, falta, identidade, matrícula ou vínculo é criado pela reativação.
- Matrículas com saída de remanejamento consolidado, com entrada vinculada cujos limites seriam alterados, ou com outra matrícula da mesma pessoa sobreposta no mesmo ano são recusadas. Não há fusão por nome nem inferência de vínculo legado.
- Mensagens distinguem matrícula encerrada, já remanejada, destino igual à origem, data inválida e estado Ativo com saída que exige conferência.

## Teste final no mesmo celular

1. Antes da atualização, salve um backup externo válido pelo aplicativo. Instale o **APK release** 0.7.1, sem desinstalar nem limpar os dados.
2. Em **Cadastrar Turma → Gerenciar alunos**, edite a mesma aluna fictícia do 1º X.
3. Se ela está Ativa com saída, toque **Confirmar reativação**, confira a data antiga e confirme somente se a matrícula foi legitimamente reativada. Se existem outras edições no formulário, reabra o cadastro antes de confirmar o reparo.
4. Reabra a edição da mesma matrícula, selecione Remanejado, destino 1º G e data 17/10/2026. Confira a prévia e confirme.
5. Consulte o histórico na origem e os registros consolidados no destino. Não exclua nem recadastre a aluna.

Uma transferência ainda válida deve permanecer encerrada. Se houver movimento consolidado, outra matrícula da mesma pessoa, lançamento na origem após a data efetiva ou destino incompatível, a operação será recusada com motivo específico; os registros não serão modificados.

## Compatibilidade e assinatura

Sem alteração de entidades, DAO, consultas, esquema Room **v3**, migrações ou backup **formato 4**. Permanecem `br.com.diariodeclasse`, `diario.db`, dez tabelas, IDs, preferências, leitores dos backups anteriores e funcionamento offline. Não há migração de dados nem correção em massa; o reparo é explícito e transacional.

O workflow manual `release.yml` e a configuração de assinatura permanecem intactos. A referência do teste de atualização é a release real **0.7.0**, execução **39055280256**, artefato **12109269070**, fixada por checksum e certificado em [baseline/noctua-release-0.7.0.json](baseline/noctua-release-0.7.0.json). O CI reutiliza exclusivamente a chave já configurada pelo proprietário, sem criar/substituir chaves ou modificar Secrets. O ensaio é restrito a emulador descartável e dados fictícios; nunca executá-lo em aparelho físico.

A semente é criada pelo próprio APK 0.7.0: todas as dez tabelas, remanejamento consolidado anterior, matrícula legitimamente encerrada e a sequência que produziu Ativo com saída residual, incluindo notas zero, recomposição e P/F/FJ. A atualização deve preservar tudo antes de qualquer reparo. Depois, testa-se reparo confirmado, novo remanejamento, ausência de duplicação, backup/restauração e reabertura.

## Validação

Resultados finais, APK e limitações serão registrados após conclusão dos testes e conferência dos artefatos. A falha inicial acima é a prova de reprodução, não aprovação da candidata. O teste físico da correção na matrícula do proprietário permanece pendente.
