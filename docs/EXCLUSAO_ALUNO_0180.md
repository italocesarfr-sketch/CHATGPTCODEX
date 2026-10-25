# NOcTuA Professor 0.18.0 — exclusão individual de matrícula

Base distribuída: 0.17.3 / código 32. Nova versão: 0.18.0 / código 33. **CI completo, regressão Android e atualização assinada sobre a 0.17.3 original aprovados.**

## Uso

Editar aluno apresenta Salvar aluno à esquerda e Excluir aluno à direita. Em telas estreitas ou fonte ampliada, as ações passam a uma coluna sem diminuir a fonte. Excluir tem contorno, lixeira e cor de erro. Não aparece no cadastro novo nem como situação de matrícula.

Excluir abre uma única confirmação com nome persistido, turma, disciplina e ano. O diagnóstico conta apenas categorias existentes: atividades com notas (incluindo zero), lançamentos pendentes, frequências persistidas P/F/FJ/NR, recomposições com/sem nota, observações e vínculos/auditorias relevantes. Não confunde ausência de linha com NR. Não relaciona atividades ou horários compartilhados como se fossem apagados.

Não, Voltar do Android ou fechar o popup não gravam alterações. Sim, excluir só é habilitado depois da conferência, sem impedimentos. Não exige digitação ou segunda confirmação. Sucesso é informado somente após o commit; erros permanecem visíveis no popup. Se o aluno estava aberto no perfil, o aplicativo retorna à lista da mesma turma.

## O que é removido e o que é preservado

Somente o ID da matrícula selecionada e suas linhas exclusivas de notas, frequência e recomposição são removidos. O nome e o personId nunca são chaves de exclusão. Um homônimo — ou uma matrícula independente da mesma pessoa — em outra turma permanece intacto.

Atividades, pontuações máximas, horários de Chamada, anotações das aulas, Diário, turmas, rascunhos de importação, grade semanal, outras matrículas e seus lançamentos permanecem. Nenhuma presença ou falta é criada.

Quando existe posição personalizada para a matrícula, ela vira linha vazia e isso é informado antes de confirmar. IDs, posições, visibilidade e linhas vazias dos outros alunos não mudam. Sem posição salva, não se cria linha adicional.

## Impedimentos de integridade

A implementação bloqueia a exclusão individual de origens/destinos de remanejamentos confirmados, inclusive externos e vínculos contidos nos históricos de turmas excluídas. Também confere referências dentro das correções administrativas e frequências retiradas com auditoria. A matrícula não é removida se isso apagar/romper a história legítima de movimentação. Nenhum vínculo é dissolvido automaticamente.

Transferido administrativo, sem vínculo de remanejamento ou histórico compartilhado, pode ser excluído mediante a mesma confirmação. Matrícula marcada Remanejado sem vínculo verificável, referências indevidas de outra turma ou posições de exportação em conflito são bloqueadas com motivo específico. Não há fusão, reativação temporária ou exclusão por nome.

## Transação e concorrência

A prévia usa snapshot consistente das quinze tabelas. Confirmação relê o banco na transação Room e exige exatamente a mesma prévia/fingerprint. Inclusive alteração de valor sem mudança de contagem invalida a autorização. Conservadoramente, qualquer alteração do conjunto escolar depois da prévia pede nova conferência.

O resultado esperado é validado pelo codec existente de backup antes das escritas. A exclusão usa comandos restritos ao studentId e, ao remover a matrícula, também ao classId. Não há cascata indiscriminada. Foreign keys são conferidas e todo o conjunto restante é comparado com o esperado antes do commit. Falha intermediária ou divergência desfaz tudo.

Room v9, quinze tabelas, migrações anteriores e backup formato 11 permanecem inalterados. Não há nova migração. Pacote br.com.diariodeclasse, banco diario.db, funcionamento offline e configuração da assinatura permanente são preservados.

## Testes e entrega

Foram acrescentados testes JVM, Room, interface, telas compactas e fontes ampliadas. O teste de atualização usa o APK original assinado 0.17.3: semeia homônimos e registros antes de atualizar, preserva toda a base e só depois testa a exclusão direcionada, restauração e novo reinício.

O Impeccable 4.7.0 local foi consultado: contexto, hardening, craft e acessibilidade Android. A implementação usa componentes Compose/Material 3 existentes, sem dependências novas. As capturas reais do emulador foram inspecionadas, incluindo fontes ampliadas e a confirmação rolável.

Arquivos principais: data/StudentDeletion.kt, data/DiaryRepository.kt, ui/StudentDeletionDialog.kt, ui/Forms.kt, ui/RosterScreen.kt, ui/DiaryApp.kt, drawable/ic_delete.xml e app/build.gradle.kts. Testes: StudentDeletionTest, StudentDeletionInstrumentedTest, StudentDeletionUiInstrumentedTest, StudentDeletionLayoutInstrumentedTest, StableUpgradeProbeTest e TimetableUiInstrumentedTest (sincronização do Flow na regressão existente). CI: validate-android.sh, test-stable-upgrade.sh, android.yml e baseline/noctua-release-0.17.3.json. Caminhos Kotlin relativos a app/src/main/java/br/com/diariodeclasse ou aos diretórios de teste correspondentes.


## Resultado da regressão

- **175 testes JVM aprovados**, sem falhas ou ignorados.
- **289 casos Android nos XML: 285 aprovados e quatro sondas condicionais**, sem falhas ou erros. FrequencyProcessProbe, UpgradeProbe e StableUpgradeProbe têm execuções específicas; ReleaseContinuityProbe é legada. Os quatro não são contados como aprovados na suíte geral.
- **14 testes focados de exclusão aprovados**, cobrindo oito cenários Room, quatro fluxos reais de navegação e dois testes de layout. Sobrepõem-se à suíte completa e não são casos únicos adicionais.
- O popup foi testado também com as escalas **reais do Android 1,3 e 1,6**, conferindo a densidade do texto da própria janela, rolagem e ações de 48 dp. Botões foram medidos em larguras úteis de 248/288/320 dp e fontes 1,0/1,3/1,6.
- A regressão anterior passou: 23 casos de horários, quatro de layout anterior, 35 de recursos anteriores e 27 de relatórios/exportação, além da suíte completa.
- Leitura independente preservada: 91 XLSX, 89 CSV e 267 TSV, incluindo modelos, históricos, zeros, vazios e planos de aula.
- Foram comprovados rollback após falha SQLite no DELETE final, bloqueio de prévia obsoleta com a mesma contagem, persistência após reabrir o banco e restauração do estado após excluir. A comparação integral do snapshot comprova que os demais dados não foram modificados.

A primeira rodada encontrou três problemas de teste, corrigidos sem alterar a execução de exclusão: beans Java de relatório precisam de comparação de todos os valores, não identidade de objeto; trocar turma retorna à gestão e exige abrir sua lista; a leitura dos glifos usa os nós não mesclados do Compose. As comparações de banco, boletim, frequência e exportação foram mantidas.

## Capturas reais

| Captura | Conteúdo |
| --- | --- |
| [Editar aluno](images/exclusao-aluno-0180/editar-aluno.png) | Ações no formulário existente, com adaptação à largura |
| [Salvar à esquerda / Excluir à direita](images/exclusao-aluno-0180/acoes-lado-a-lado.png) | Componente em largura útil de 288 dp |
| [Fonte ampliada 1,6](images/exclusao-aluno-0180/acoes-fonte-1.6.png) | Ações empilhadas, sem cortes |
| [Popup com diagnóstico real](images/exclusao-aluno-0180/confirmacao.png) | Dados da matrícula fictícia consultados no banco |
| [Popup com fonte real 1,6](images/exclusao-aluno-0180/popup-fonte-1.6.png) | Nome longo, diagnóstico rolável e ações acessíveis |

Todos os exemplos contêm dados fictícios. Em fonte ampliada, o diagnóstico e o aviso exigem rolagem; os testes conferem que o final do texto permanece acessível. Conferência final em aparelho físico permanece com o proprietário.

- [Código do aplicativo](https://github.com/italocesarfr-sketch/CHATGPTCODEX/commit/aaf8e57312cec007d40463a8bb059ad57f045d6a).
- [Commit de testes validado](https://github.com/italocesarfr-sketch/CHATGPTCODEX/commit/4d8cde571108dcf1c111f0d00c0e927399e5895d).
- [GitHub Actions](https://github.com/italocesarfr-sketch/CHATGPTCODEX/actions/runs/40195629751).
- [Resultados, XML e capturas completos](https://github.com/italocesarfr-sketch/CHATGPTCODEX/actions/runs/40195629751/artifacts/12599598181).


## Correção identificada no teste de atualização

A primeira atualização controlada passou pela instalação, restauração, reinício e preservação dos remanejamentos, mas identificou um bloqueio indevido ao excluir um cadastro independente quando existiam históricos de outras turmas. A conferência agora acessa explicitamente os registros de cada histórico, sem confundi-los com os lançamentos locais do aluno selecionado. Foram acrescentados testes JVM e Room com históricos sem relação com a matrícula. Esse bloqueio não apagou dados. A nova rodada completa e as oito fases da atualização assinada passaram depois da correção.


A regressão de horários também passou a aguardar a publicação das revisões do Room no Flow antes de conferir o resultado anual. O teste mantém a exigência de três aulas e as comparações dos registros acadêmicos; nenhuma regra de horários foi alterada. O teste focado, as escalas de fonte e a suíte Android completa passaram na execução final de código.


## Atualização e APK release

A release 0.18.0 / 33 foi testada sobre o **APK original 0.17.3 / 32**, sem desinstalar. Passaram as oito fases: antes da instalação, depois de `adb install -r`, backup/restauração, reinício, exclusão de turmas com histórico preservado, novo reinício, exclusão individual e reinício após essa exclusão. Cada arquivo de resultado terminou em `OK (1 test)`.

Os homônimos e seus lançamentos foram criados na release anterior, antes da atualização. A confirmação posterior removeu somente o cadastro errado; notas zero, FJ, recomposição e identidade do outro cadastro permaneceram. Os históricos das duas turmas excluídas também permaneceram idênticos. A restauração e a reabertura confirmaram esse resultado.

O APK publicado foi baixado e novamente verificado localmente: mesmo pacote e certificado da 0.17.3, assinatura válida e versão maior. Seu hash coincide com o APK testado antes do upload. Não houve criação ou alteração de chaves, senhas ou Secrets.

- **APK SHA-256:** `3d90feff487e0ef7edbcb61df769c2baf8bf7d2942a6e564067877b7030cfb99`.
- **Certificado SHA-256:** `6dbbba2d930eb7f7fbeff1ee82512f656251bbec9f4b31b6c5614528d29f1aae` (identificação pública).
- [APK release assinado — baixar ZIP e instalar app-release.apk sobre a instalação anterior](https://github.com/italocesarfr-sketch/CHATGPTCODEX/actions/runs/40195629751/artifacts/12600252554).
- [Evidências das oito fases da atualização](https://github.com/italocesarfr-sketch/CHATGPTCODEX/actions/runs/40195629751/artifacts/12600367335).
- [Resultados estruturados e lista integral dos arquivos alterados](EXCLUSAO_ALUNO_0180_RESULTADOS.json).

O artefato conserva o nome histórico `noctua-etapa6-release`, mas o conteúdo verificado é 0.18.0 / 33. Os downloads exigem acesso ao repositório e estão sujeitos à retenção do Actions. Os hashes de ZIP registrados no JSON são diferentes do hash do APK interno.

A finalização posterior ao commit validado altera somente documentação e capturas. Permanecem como limites a proteção das matrículas com movimentação confirmada descrita acima e a conferência manual final no aparelho físico do proprietário; não há falhas de teste pendentes.
