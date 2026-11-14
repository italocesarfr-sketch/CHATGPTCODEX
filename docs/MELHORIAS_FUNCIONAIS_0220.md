# Melhorias funcionais — chamada, atividades e notas

Branch: `sync/academic-complete-0220`. Continuidade da base funcional H13 preservada; os candidatos H13.1/H13.2 não foram integrados. Somente dados sintéticos e infraestrutura descartável são autorizados nesta rodada.

## Etapa 1 — chamada

- `08ffc24`: segundo toque em P/F consulta o estado real dentro de `Room.withTransaction` e exclui a ocorrência. Ausência não é NR.
- `mark` continua atribuindo estados diretamente para menus, correções e importações. NR explícito e registros anteriores não são convertidos. FJ e seus motivos mantêm as validações por ocorrência.
- O DELETE existente dispara a captura transacional da sincronização. A outbox transmite `payload: null`, com revisão conhecida; o recebimento exclui a mesma chave composta aula/matrícula. Nenhum novo formato, migração ou protocolo.
- `478894c`: prova HTTP real em dois AVDs novos; `836d06c`: correção de expectativas antigas de automação; `358a272`: inicialização PostgREST espera o fim do HTTP 503 de carga do cache.
- Não há autorização para corrigir chamadas de matrículas remanejadas ou romper históricos. Os bloqueios anteriores permanecem aplicados antes de excluir.

## Etapa 2 — atividades e notas (em validação)

Busca normalizada por nome sem acentos/caixa e número exato da chamada. Ordenação por número (padrão) ou nome com desempate estável. Nenhuma renumeração, matrícula ou nota muda ao ordenar. Texto inválido não salvo permanece identificado mesmo ao filtrar o aluno e durante recriação da tela.

Criação: turma atual por padrão, todas as compatíveis do mesmo ano letivo ou seleção manual. A seleção manual permite todas/nenhuma/individual, agrupadas por ano letivo e etapa. Turmas de outro ano letivo exigem data própria explícita; nenhuma data é deslocada automaticamente. O cadastro não possui campo estruturado de série: não se infere série a partir do nome livre da turma. A elegibilidade utiliza etapa, disciplina, arquivamento, ano da data e período real; todas as turmas envolvidas aparecem no resumo.

`ActivityCopies.plan` é uma prévia sem escrita, revalidada pelo repositório dentro de uma única transação. Se uma turma, período ou elegibilidade mudou, ou existir duplicata, nada é inserido. As cópias possuem IDs independentes determinísticos por pedido/turma; repetir o mesmo pedido não sobrescreve atividades ou notas. As notas não são copiadas. Um erro de inserção reverte o lote e a captura da fila. A sincronização incremental existente transporta as atividades e depois suas notas, respeitando dependências.

Não houve alteração de Room 14, backup 12, SQL acadêmico, assinatura, applicationId, regras de remanejamento ou configurações hospedadas. Os limites de lote C/D/E continuam válidos; ultrapassá-los bloqueia o envio com os dados locais preservados.

## Evidências e limites

Comando local: `./gradlew --offline --no-daemon testDebugUnitTest assembleDebugAndroidTest`, JDK 17/SDK local. Resultado mais recente: BUILD SUCCESSFUL; 306 casos JVM, 301 aprovados, cinco condicionais não executados (quatro H01–H04 hospedados e um SupabaseLiveAuthTest), zero falhas. Esses condicionais não representam homologação.

A rodada `43546621371` aprovou 28/30 testes instrumentados: expectativa antiga de P no segundo toque e procura de título lazy fora da área composta falharam. Ambos foram corrigidos preservando asserções. A rodada `43547135005` interrompeu antes do Android: o health check antigo tratava HTTP 503 de inicialização como pronto. Correção: aguardar HTTP de sucesso; nenhum teste de permissão foi flexibilizado.

Resultados finais Android/HTTP e capturas serão consolidados após a execução em andamento. A compilação local não comprova execução instrumentada.

Nenhuma execução hospedada, instalação em dispositivo existente ou release oficial. O fluxo novo deverá ser homologado fisicamente em variante isolada antes de distribuição.
