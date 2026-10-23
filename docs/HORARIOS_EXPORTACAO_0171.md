# NOcTuA Professor 0.17.1 — alcance dos horários e período da exportação

Versão proposta: **0.17.1 / código 30**, sobre a 0.17.0 original / 29. Validação Android e release ainda pendentes; os resultados definitivos serão registrados após o CI.

## Horários

O primeiro cadastro de cada ano começa automaticamente em 1º de janeiro. O formulário não solicita data ou alcance e Salvar grava diretamente, sem criar aulas ou conteúdos. Os períodos continuam ligados aos IDs de turmas daquele ano; não são transportados para anos seguintes.

Nas edições, os dois alcances são **A partir de hoje** e **Para o ano todo**. A data é mostrada antes de salvar. Hoje só é aceito no ano atual; nos demais anos, é necessário escolher a opção anual. Novos períodos da semana usam inicialmente o alcance anual; editar períodos existentes no ano atual sugere Hoje. Hoje salva diretamente, sem segunda etapa administrativa. A conferência anual mostra o intervalo, as datas com registros cuja previsão muda e, no alcance anual, quantas vigências posteriores serão substituídas. Não altera os lançamentos efetivos.

### Revisões imutáveis, sem mudança de esquema

A consulta continua usando a maior data efetiva e, nela, a maior revisão. Uma substituição anual insere uma nova revisão em 1º de janeiro **e em cada fronteira de vigência já existente naquele ano**, todas contendo o mesmo retrato semanal revisado. Assim, nem uma revisão futura antiga pode voltar a prevalecer silenciosamente. Não há UPDATE nem DELETE das revisões anteriores: elas seguem consultáveis em Vigências e incluídas no backup.

A edição parte do retrato semanal consultado, preservando os demais períodos desse retrato. A prévia anual informa que ele substituirá também previsões posteriores; não tenta mesclar duas rotinas potencialmente conflitantes. Edições posteriores ainda podem estabelecer novas vigências normalmente. Uma grade antiga iniciada em outubro só se torna anual mediante ação explícita do professor.

A confirmação revalida o conjunto completo de revisões do ano, conflitos de períodos/turnos, turmas, datas acadêmicas afetadas e cada revisão proposta. Todas as inserções pertencem à mesma transação Room. Uma falha na segunda inserção também desfaz a primeira. Nenhuma chamada, frequência, nota ou descrição do Diário é gravada por essa operação.

## Exportação

Ajustes → Abrangência oferece **1º, 2º, 3º, 4º bimestre e Quatro bimestres**. Chamada e Planos também conservam Intervalo de datas. Resumo anual mantém seu período anual fixo, sem seletor sem efeito.

Cada entrada em Exportar herda o bimestre consultado em Relatórios. Alterações posteriores pertencem apenas à visita à exportação e ao modelo escolhido; rotação conserva esses ajustes. Voltar não modifica Relatórios. Reentrar inicia novamente pelo bimestre de Relatórios, mantendo preferências de conteúdo/formato compatíveis e zerando a abrangência da visita anterior.

O mesmo parâmetro efetivo alimenta resumo, nome do arquivo, prévia, XLSX, CSV, TSV e cópia, inclusive somente notas. Os exportadores e cálculos acadêmicos existentes são reutilizados. Quatro bimestres em Chamada/Planos usa o início mínimo e término máximo dos quatro períodos configurados; sem os quatro períodos, solicita configuração ou intervalo explícito. Não inventa datas acadêmicas. Quatro bimestres não troca o modelo por Resumo anual.

## Integridade e compatibilidade

- Mesmo applicationId `br.com.diariodeclasse` e banco `diario.db`.
- **Room v9 e backup formato 11 inalterados**; nenhuma migração nova ou transformação automática das grades antigas.
- Nenhuma alteração em notas, matrículas, remanejamentos, frequência, recomposição, diários, identidade visual, login demonstrativo ou certificados.
- O CI compara a assinatura e testa `adb install -r` sobre o APK original **0.17.0**, identificado por hash em `docs/baseline/noctua-release-0.17.0.json`. A semente inclui grade antiga de outubro e revisão futura, registros acadêmicos, auditoria de remanejamentos, preferências e backups.

## Testes acrescentados

- JVM: cadastro anual em outubro/dezembro; todas as fronteiras futuras; revisões sucessivas; Hoje/ano diferente; períodos acadêmicos afetados; conflitos; restauração; seleção efetiva de todos os bimestres/seis modelos; quatro períodos e intervalo; equivalência da matriz de saída e cópia.
- Room: preservação integral dos registros com P/F/FJ/NR e Diário; reinício e restauração de múltiplas revisões; falha após a primeira inserção com rollback; concorrência, novas datas e turma arquivada.
- UI: primeiro cadastro sem data ou alcance; dois alcances; substituição anual confirmada; bimestres diretos, prévia/cópia, rotação, retorno/reentrada e separação entre modelos; telas compactas com fontes 1,3 e 1,6.
- Leitor independente openpyxl/Python csv: nova matriz de 48 XLSX, 48 CSV e 144 TSV, além das massas anteriores. Datas Excel reais são conferidas como datas formatadas e seriais equivalentes; nenhum escritor é usado para reler o arquivo.

## Limitações

A validação física final permanece com o proprietário. Leitura independente de XLSX não substitui abrir os arquivos na interface do Microsoft Excel. Uma substituição anual aplica o retrato completo revisado e pode substituir previsões posteriores de outras turmas; a confirmação informa esse alcance. Os lançamentos reais permanecem intocados.
