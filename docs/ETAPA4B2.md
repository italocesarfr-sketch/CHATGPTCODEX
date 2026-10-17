# Etapa 4B.2 — Recomposição de Notas

Versão validada **Noctua 0.6.0 / código 9**, baseado na 0.5.0 / código 8. Implementação restrita à recomposição e sua integração aos relatórios. Resultados de compilação, testes e atualização registrados ao final.

## Uso

Em **Atividades e Notas → Recomposição de Notas**, escolha o ano/turma nos seletores globais e o **bimestre de origem** na tela. A data da avaliação é independente desse bimestre: uma recomposição feita em novembro pode alterar somente o 3º bimestre.

O filtro inicial mostra somente resultados vigentes conhecidos abaixo de **5,00**. **Todos os alunos** permite consultar pendências, alunos que já atingiram 5 e corrigir qualquer lançamento, incluindo matrículas transferidas com histórico. Atingir 5 após salvar remove o aluno do filtro inicial, sem apagar a matrícula ou o registro.

Toque em **Lançar recomposição / Editar recomposição**. O formulário apresenta original, nota de 0 a 10, data e prévia do resultado. Confirme em **Salvar recomposição**. O resultado da gravação aparece em uma mensagem fixa da própria tela, sem cobrir os botões da lista. Vazio significa não realizada; `0` é nota zero. O formulário bloqueia a interação com filtros, turma, ano e navegação de fundo; sair com alterações requer confirmação de descarte. Voltar e cancelar a confirmação conservam o rascunho. A recriação da Activity conserva os campos. Durante a gravação, entradas e saída são bloqueadas; falhas mantêm o formulário aberto com erro. A fila de gravações anterior continua sendo utilizada. **Excluir recomposição** exige confirmação e volta a considerar o resultado original, sem alterar atividades.

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

## Assinatura e atualização

A assinatura permanente e `.github/workflows/release.yml` permanecem intactos. Não foram criadas/substituídas chaves nem alterados Secrets. O CI mantém os testes debug e o ensaio release que reutiliza a configuração existente do proprietário. A referência agora é o APK **0.5.0 real**, execução `39032589083`, artefato `12101495282`, com hash/certificado públicos em `baseline/noctua-release-0.5.0.json`.

O ensaio instala a referência em emulador novo, cria somente fixture sintética, atualiza por cima, verifica integralmente o legado, restaura backup antigo, cria recomposições, valida novo backup/restauração e reabre o processo. O workflow manual de release continua disponível ao proprietário. Scripts de atualização recusam aparelhos físicos. Nenhuma compatibilidade com APK debug de outra assinatura é presumida.

Testes acrescentados: regras maior/menor/igual, zero/nulo/pendente e limite; projeções dos quatro períodos e isolamento de origem; Room CRUD, conflitos e falhas simuladas, persistência, backup atual/antigo e migração v1 completa por `MigrationTestHelper`; Compose filtros, passagem para 5, edição/exclusão confirmadas, descarte/recriação e atualização dos relatórios. A regressão anterior de atividades, recálculo proporcional, chamadas e backup também foi executada.

## Resultados verificados — 17/10/2026

[Baixar APK de teste com assinatura release existente](https://github.com/italocesarfr-sketch/CHATGPTCODEX/actions/runs/39045605081/artifacts/12105803073) • [GitHub Actions aprovado](https://github.com/italocesarfr-sketch/CHATGPTCODEX/actions/runs/39045605081) • [Testes e capturas](https://github.com/italocesarfr-sketch/CHATGPTCODEX/actions/runs/39045605081/artifacts/12105532335) • [Provas de atualização](https://github.com/italocesarfr-sketch/CHATGPTCODEX/actions/runs/39045605081/artifacts/12105773296). O ZIP contém `app-release.apk`, SHA-256, identidade pública e comparação com a versão anterior. Código testado: [`e1dcf70`](https://github.com/italocesarfr-sketch/CHATGPTCODEX/commit/e1dcf70176f9be6d764d1f5548020dc20de8be37). O commit documental posterior mantém o mesmo código.

| Verificação | Resultado |
| --- | --- |
| JVM, incluindo regras e codecs | 39 aprovados, zero falhas, no CI e localmente |
| Android / emulador API 30 | 80 aprovados, zero falhas |
| Sondas de reinício de chamadas/notas | 2 fases aprovadas |
| Atualização da referência legada Room v1 | 2 fases aprovadas |
| Release real 0.5.0 → 0.6.0 | 4 fases aprovadas: antes, depois, backup/restauração e nova abertura |
| Migração Room 1 → 2 | Validada com todas as entidades/IDs da fixture; tabela nova inicialmente vazia |
| Backup | Formato 3 com nove tabelas; leitura/restauração dos formatos 1 e 2 aprovadas |
| Compilação | `assembleDebug`, `assembleDebugAndroidTest` e `assembleRelease` aprovados |
| Assinatura | Verificação criptográfica aprovada, mesmo certificado da release 0.5.0 |
| Domínio Java local | 28 cenários aprovados |

Os quatro testes exclusivos de sondas ficam ignorados na suíte Android normal e são acionados separadamente. Foram conferidos os XMLs JUnit e as saídas das oito fases atuais. O script histórico que criava chaves de teste não foi acionado. Somente dados fictícios foram usados. Não foram criadas/substituídas chaves de assinatura permanentes nem alterados Secrets.

As primeiras execuções identificaram problemas no teste de captura com duas janelas e na rolagem antes de interagir com controles. Uma execução também registrou SIGSEGV do `surfaceflinger` do emulador; o CI passou a desativar Vulkan. A verificação de recriação foi ajustada para alcançar o cabeçalho após a restauração da posição da lista. A confirmação de salvamento da recomposição passou a aparecer na própria tela, sem sobrepor botões. A suíte completa foi repetida e aprovada após os ajustes; nenhuma fórmula foi modificada para contornar testes.

O ambiente local recebeu SDK 35 e JDK 17 completo; o Java originalmente disponível não tinha `jlink`. Testes JVM e APKs debug/instrumentação foram compilados localmente com Gradle. A validação Android e a atualização assinada foram executadas no CI. O APK assinado do CI foi baixado e verificado novamente com `apksigner`, manifesto binário e checksum.

- APK release: **7,114,194 bytes**, `br.com.diariodeclasse`, versão **0.6.0 / código 9**, Android 8.0+.
- Caminho local verificado: `app/build/outputs/apk/release/app-release.apk` (absoluto: `/workspace/diario-de-classe/app/build/outputs/apk/release/app-release.apk`).
- APK release SHA-256: `8ca266790cb32a1e9572bc9cdefec0a737533291c0f973e5dcf15c71e5282369`.
- Certificado público SHA-256: `6dbbba2d930eb7f7fbeff1ee82512f656251bbec9f4b31b6c5614528d29f1aae`.
- [Metadados completos](baseline/etapa4b2-validacao.json).
- [APK debug interno](https://github.com/italocesarfr-sketch/CHATGPTCODEX/actions/runs/39045605081/artifacts/12106085605): certificado diferente da release, sem promessa de instalação sobre ela. Caminho local verificado: `app/build/outputs/apk/debug/app-debug.apk`.

**Compatibilidade comprovada:** instalação por cima do APK release 0.5.0 de referência com a mesma chave, preservando integralmente as oito tabelas anteriores, IDs e preferências, com migração aditiva. Depois da atualização, recomposições zero, não realizada e positiva foram criadas, incluídas no backup/restauração e verificadas após encerramento/reabertura do processo. As notas originais permaneceram intactas.

O workflow manual `release.yml` continua intacto para o proprietário gerar outra compilação com a mesma chave. Nenhuma chave ou senha foi publicada. Os APKs de teste têm retenção de 30 dias no Actions; a referência 0.5.0 também depende da retenção do artefato anterior. Quando expirar, o mantenedor deverá fixar uma nova referência legítima com seus metadados públicos, sem desativar a comparação de certificados.

**Limites:** este incremento ainda precisa de conferência em celular físico. A prova é com a release 0.5.0 de referência em emulador API 30, sem inspeção do certificado do aparelho do professor. APKs debug antigos de outra assinatura continuam incompatíveis. Faça backup externo válido antes de atualizar; não desinstale nem limpe dados para contornar divergência de assinatura. Não fazer downgrade para 0.5.0 sobre o banco v2. Não foram feitos novos ensaios em API 26, tablets, TalkBack ou fonte ampliada nesta entrega.

Somente a Etapa 4B.2 está concluída. Exportação, registros de aula, outras políticas de recuperação e os próximos incrementos aguardam autorização.
