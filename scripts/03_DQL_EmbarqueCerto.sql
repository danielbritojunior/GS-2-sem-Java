-- =============================================================================
-- FIAP · Engenharia de Software · 2º ano · Global Solution 2026.2 (CitizenTech)
-- Disciplina : Database Design
-- Solução    : Embarque Certo
-- Integrantes: Daniel Brito          RM 566236
--              Gustavo Borsato       RM 564621
--              Vitor Rampazzi Franco RM 562270
--
-- Script 03 : DQL (consultas)
-- Pré-requisito: executar antes o 01_DDL e o 02_DML
-- =============================================================================


-- -----------------------------------------------------------------------------
-- CONSULTA 1 · INNER JOIN entre duas tabelas
--   Tabelas : T_EC_OCORRENCIA e T_EC_ESTACAO
--   Pergunta: o que aconteceu em cada estação, da ocorrência mais recente para
--             a mais antiga?
--   Uso     : tela "O que está acontecendo nas estações" do aplicativo.
--   Detalhe : as ocorrências que afetam a linha inteira (id_estacao NULL) não
--             aparecem, porque o INNER JOIN só devolve as linhas que têm
--             correspondência nas duas tabelas.
-- -----------------------------------------------------------------------------
SELECT e.nm_estacao                                AS estacao,
       o.id_ocorrencia                             AS ocorrencia,
       o.ds_ocorrencia                             AS descricao,
       o.st_ocorrencia                             AS situacao,
       o.nr_atraso_min                             AS atraso_min,
       TO_CHAR(o.dt_inicio, 'DD/MM/YYYY HH24:MI')  AS inicio
  FROM t_ec_ocorrencia o
 INNER JOIN t_ec_estacao e
    ON e.id_estacao = o.id_estacao
 ORDER BY o.dt_inicio DESC;


-- -----------------------------------------------------------------------------
-- CONSULTA 2 · INNER JOIN entre duas tabelas com funções de grupo e GROUP BY
--   Tabelas : T_EC_ESTACAO e T_EC_REGISTRO_LOTACAO
--   Pergunta: quais estações ficam mais lotadas no pico da manhã
--             (registros feitos entre 06h00 e 09h59)?
--   Uso     : ranking de lotação para o passageiro escolher horário ou rota
--             e para a operadora reforçar a frota onde mais precisa.
--   Funções : COUNT (quantos registros), AVG (nível médio), MAX (pior nível)
-- -----------------------------------------------------------------------------
SELECT e.nm_estacao                 AS estacao,
       COUNT(r.id_registro)         AS qt_registros,
       ROUND(AVG(r.nr_nivel), 2)    AS nivel_medio,
       MAX(r.nr_nivel)              AS nivel_maximo
  FROM t_ec_estacao e
 INNER JOIN t_ec_registro_lotacao r
    ON r.id_estacao = e.id_estacao
 WHERE TO_NUMBER(TO_CHAR(r.dt_registro, 'HH24')) BETWEEN 6 AND 9
 GROUP BY e.nm_estacao
 ORDER BY nivel_medio DESC, qt_registros DESC, estacao;


-- -----------------------------------------------------------------------------
-- CONSULTA 3 · LEFT OUTER JOIN entre duas tabelas com WHERE e ORDER BY
--   Tabelas : T_EC_LINHA e T_EC_OCORRENCIA
--   Pergunta: qual é a situação agora de cada linha de metrô?
--   Uso     : painel "Situação das linhas" do aplicativo.
--   Detalhe : o LEFT OUTER JOIN mantém as linhas SEM ocorrência ativa, que
--             aparecem como "Operação normal" (colunas da ocorrência em NULL,
--             tratadas com NVL).
--             - O filtro de situação (ABERTA/CONFIRMADA) fica no ON para não
--               eliminar as linhas sem ocorrência.
--             - O WHERE filtra as linhas do modal METRO (exclui os trens).
--             - O ORDER BY classifica pelo número da linha e, dentro dela,
--               pelo maior atraso.
-- -----------------------------------------------------------------------------
SELECT l.nr_linha                               AS linha,
       l.nm_linha                               AS nome,
       NVL(o.st_ocorrencia, 'NORMAL')           AS situacao,
       NVL(o.ds_ocorrencia, 'Operação normal')  AS detalhe,
       NVL(o.nr_atraso_min, 0)                  AS atraso_min
  FROM t_ec_linha l
  LEFT OUTER JOIN t_ec_ocorrencia o
    ON o.id_linha = l.id_linha
   AND o.st_ocorrencia IN ('ABERTA', 'CONFIRMADA')
 WHERE l.tp_modal = 'METRO'
 ORDER BY l.nr_linha, atraso_min DESC;
