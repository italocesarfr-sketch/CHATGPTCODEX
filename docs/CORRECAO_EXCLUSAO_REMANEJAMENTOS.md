# Exclusão definitiva de turmas com remanejamentos

Base conferida: `9b4247c` (Noctua 0.11.1 / 17), incluindo a limpeza pontual de Relatórios. Candidato: **0.11.2 / 18**. Esta correção não reformula telas nem regras pedagógicas.

## Causa e solução

Até a 0.11.1, a consolidação dependia de `students`, `activities`, `lessons` e `relocations` da turma original. As chaves estrangeiras restritivas impediam sua remoção, e a exclusão era bloqueada. Apenas retirar o bloqueio perderia o histórico ou produziria referências inválidas.

Room **v7** acrescenta somente `preserved_histories`, com `classId` original e conteúdo JSON tipado/versionado pelo esquema e pelo backup. Cada origem excluída guarda o contexto original da turma (ano, disciplina, escala, critérios de FJ), seus bimestres e apenas as matrículas/registros necessários a pessoas que continuam vinculadas a outra turma cadastrada. Os 13 esquemas de tabelas v6 e os JSONs v1–v6 permanecem intactos. Migração **6→7 aditiva**, inicialmente sem históricos; não apaga nem transforma registros.

O histórico preserva IDs de pessoa, matrícula, atividade e aula, valores de notas (incluindo zero e pendente), recomposições, datas, horários, máximos, cancelamentos, P/F/FJ/NR e vínculos originais. Metadados de atividades elegíveis ainda sem nota e aulas elegíveis sem chamada também permanecem, para manter pendências e oportunidades reais. Registros já lançados fora dos limites atuais da matrícula são preservados, sem passarem a contar indevidamente.

`DiarySnapshot.academic()` une os registros atuais e históricos somente para leitura acadêmica. `SchoolReports`, notas/frequência e exportação continuam usando as regras existentes. Seletores, cadastro, chamada e lançamento de novas atividades usam somente turmas/matrículas cadastradas: uma turma excluída não reaparece como arquivada nem vira turma fictícia no destino. O perfil identifica a origem com **“turma excluída”**. Notas/chamadas originais continuam somente leitura; a correção/exclusão explícita da recomposição herdada permanece disponível, no mesmo ID histórico.

Cada registro tem um único local persistente depois do commit. Não são copiadas notas para atividades do destino nem convertidos horários/calendários. Os vínculos envolvendo matrículas removidas passam ao histórico; os demais vínculos e registros de outras turmas não mudam. A validação integral recusa IDs duplicados, referências inválidas, ciclos, calendários/escalas/políticas de FJ incompatíveis e recomposições conflitantes.

## Remanejamentos sucessivos e exclusão

Para X→G→H, excluir X e depois G, ou G antes de X, preserva as três matrículas na consulta do aluno em H, cada lançamento com sua origem. O cálculo mantém a soma original das atividades elegíveis e a recomposição substitutiva, sem duplicar pontos ou oportunidades de frequência.

O conjunto preservado é obtido pelos componentes dos vínculos explícitos que ainda alcançam alguma matrícula cadastrada. Não se infere continuidade pelo nome ou apenas pelo `personId`. Se o destino for excluído antes da origem, os vínculos reais permanecem consultáveis e a origem não é reativada. Quando a última turma cadastrada de um componente é excluída, os históricos que se tornaram exclusivos desse componente são removidos; dados de outras pessoas/turmas permanecem intactos. Um destino externo sem outra matrícula cadastrada não impede excluir os dados exclusivos da origem.

Identidades legadas compartilhadas **sem vínculo comprovado**, ou registros ilegítimos cruzando turmas, continuam exigindo revisão: associá-los automaticamente inventaria um histórico. O bloqueio genérico por remanejamento foi removido. Arquivar não é obrigatório para vínculos válidos.

## Confirmação e atomicidade

A prévia pergunta **“Tem certeza de que deseja excluir permanentemente esta turma?”**, identifica turma/ano e apresenta os registros afetados e quantidades de históricos, notas, frequências e recomposições preservados. A operação é irreversível, recomenda backup e exige digitar `EXCLUIR Nome (Ano)`. Cancelar ou apenas abrir a prévia não grava nada. Se os dados mudarem depois da prévia, a confirmação é recusada.

Uma única transação Room valida o plano, grava os históricos necessários, retira vínculos físicos envolvidos e remove explicitamente os dados da turma. Não desativa FKs nem usa cascata como solução. Históricos de outros componentes não são regravados. Antes do commit, compara o snapshot esperado, executa `foreign_key_check` e confere, pelas mesmas projeções aprovadas, os quatro resultados bimestrais e a frequência/histórico dos alunos afetados. Qualquer erro, inclusive na preservação ou conferência final, desfaz a operação inteira.

## Backup e assinatura

Backup **formato 8 / esquema 7** inclui todos os históricos, com ordenação canônica, checksum e validação do grafo lógico além das FKs físicas. O leitor aceita formatos **1–7** anteriores; ausência de históricos antigos significa lista vazia, sem inventar vínculos. A restauração continua confirmada, com cópia de segurança anterior, uma transação e verificação posterior. Versões antigas não conseguem ler o formato novo; não se recomenda retroceder o aplicativo.

Mantidos pacote `br.com.diariodeclasse`, banco `diario.db`, armazenamento privado e operação offline. A assinatura e os Secrets não foram alterados. O CI passa a testar a atualização real da release **0.11.1 (17)**, fixada por SHA-256 e certificado público, para **0.11.2 (18)** usando a mesma chave já configurada. As sondas adicionais criam e excluem origens fictícias depois da atualização, restauram o backup novo e verificam novamente após encerrar o processo.

## Testes e resultados

Incluídos testes JVM, Room e de interface com dados exclusivamente fictícios: sem remanejamento, origem, intermediária, cadeia sucessiva, destino, notas zero/pendentes, recomposição, P/F/FJ/NR, aulas duplas/triplas/canceladas, quatro bimestres, exportações, confirmação/cancelamento, prévia obsoleta, falhas por triggers com rollback, migração v6, backup/restauração e reabertura. As validações anteriores de chamadas, recálculo proporcional, recomposição, relatórios e exportações permanecem na regressão.

**CI aprovado na primeira execução publicada:** [Actions 39153312998](https://github.com/italocesarfr-sketch/CHATGPTCODEX/actions/runs/39153312998), commit de código [`879e08b`](https://github.com/italocesarfr-sketch/CHATGPTCODEX/commit/879e08b6dd3577f381ca38b9e0b6c73c93cc2134).

- **102 testes JVM e 168 Android aprovados, zero falhas.** Dez cenários Android novos: nove Room e um fluxo de interface que cancela e confirma a exclusão da origem, consulta os resultados no destino e recria a Activity.
- Quatro sondas condicionais ignoradas na suíte normal. As sondas legadas, de frequência entre processos e de atualização assinada foram executadas separadamente (duas + duas + seis fases aprovadas). O teste histórico `ReleaseContinuity` permanece ignorado e é substituído pelo ensaio da release real 0.11.1 com a assinatura permanente, sem chave nova.
- **Migração 6→7 aprovada** com as 13 tabelas preenchidas por dados fictícios e estrutura anterior idêntica. Esquema v7: identidade `0c0a542f53b49d5f6d895515d1b9ec49`, SHA-256 `45e14573bede8c237e55bd2bf21dea38d256e650686bb9b2226f0269ad1f1510`. JSONs v1–v6 conferidos byte a byte contra a base 0.11.1.
- **Rollback comprovado** ao falhar a escrita do histórico, ao falhar a exclusão após sua escrita e ao falhar a restauração do histórico. Cancelamento e prévia obsoleta mantêm os dados intactos.
- Atualização real **0.11.1 (17) → 0.11.2 (18)** por `adb install -r`, mesma assinatura existente, registros antigos/IDs/preferências preservados. Após a atualização, as sondas adicionais excluem X e G, verificam H, alteram/restauram o backup com históricos e conferem novamente após `force-stop` e reabertura do processo.
- **39 XLSX, 39 CSV e 117 TSV validados** com leitores independentes: 30 conjuntos JVM anteriores, seis novos conjuntos pós-exclusão e três conjuntos Android. Projeções de todos os modelos/bimestres antes e depois da exclusão são iguais. A interface e a ajuda discreta de Relatórios continuam na regressão.
- O primeiro teste JVM local identificou quatro asserções antigas que esperavam o cabeçalho de backup 7/v6; foram atualizadas para 8/v7, mantendo testes de leitura das versões antigas. Não houve falha na suíte final de CI.

[Baixar APK release assinado — ZIP](https://github.com/italocesarfr-sketch/CHATGPTCODEX/actions/runs/39153312998/artifacts/12146541370). Extrair `app-release.apk`; o artefato mantém o nome histórico `noctua-etapa6-release`, mas a versão interna conferida é **0.11.2 / 18**. [Resultados dos testes](https://github.com/italocesarfr-sketch/CHATGPTCODEX/actions/runs/39153312998/artifacts/12146008176) · [Provas da atualização e reinício](https://github.com/italocesarfr-sketch/CHATGPTCODEX/actions/runs/39153312998/artifacts/12146402004) · [Metadados](CORRECAO_EXCLUSAO_RESULTADOS.json).

APK baixado e existente em `app/build/outputs/apk/release/app-release.apk`, 7.327.710 bytes. Assinatura APK v2 verificada por `apksigner`, pacote preservado, API mínima 26 e sem permissão INTERNET. SHA-256 do APK: `c7a5f13c30c8101073e2aada15adf4add3c0ce0cf44e0a766ed1d7f0a4abe069`. Certificado público SHA-256: `6dbbba2d930eb7f7fbeff1ee82512f656251bbec9f4b31b6c5614528d29f1aae`, idêntico à referência 0.11.1. Hashes dos ZIPs também conferidos. Workflow release manual e bloco de configuração da assinatura preservados.

**Limitações:** ensaios Android em emulador API 30, sem novo teste físico desta correção nem abertura manual no Microsoft Excel. As planilhas foram lidas por openpyxl e parser CSV/TSV. O limite de backup continua em 32 MB; históricos preservados também contam nesse limite. Identidades legadas sem vínculo comprovado ou dados cruzados inválidos continuam exigindo revisão, sem associação inventada. Faça um backup antes de atualizar e antes de excluir; use o APK release compatível sem desinstalar. Guarde o arquivo: o artefato de APK do Actions expira após 30 dias.
