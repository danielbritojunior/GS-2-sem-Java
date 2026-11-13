-- Versao local para a ferramenta manual Java/JDBC.
-- Preserva CREATE, constraints e comentarios; nao contem o bloco de limpeza.

-- 1. CREATE TABLE (colunas, chaves primárias, chaves únicas e checagens)
-- -----------------------------------------------------------------------------

-- Empresa responsável pela operação das linhas (Metrô, CPTM, concessionárias)
CREATE TABLE t_ec_operadora (
    id_operadora   NUMBER(3)      NOT NULL,
    nm_operadora   VARCHAR2(80)   NOT NULL,
    sg_operadora   VARCHAR2(15)   NOT NULL,
    tp_gestao      VARCHAR2(10)   NOT NULL,
    ds_site        VARCHAR2(100),
    CONSTRAINT pk_ec_operadora        PRIMARY KEY (id_operadora),
    CONSTRAINT un_ec_operadora_nome   UNIQUE (nm_operadora),
    CONSTRAINT un_ec_operadora_sigla  UNIQUE (sg_operadora),
    CONSTRAINT ck_ec_operadora_gestao CHECK (tp_gestao IN ('PUBLICA', 'CONCESSAO'))
);

-- Linha de metrô, trem, monotrilho ou BRT
CREATE TABLE t_ec_linha (
    id_linha       NUMBER(3)      NOT NULL,
    id_operadora   NUMBER(3)      NOT NULL,
    nr_linha       NUMBER(2)      NOT NULL,
    nm_linha       VARCHAR2(30)   NOT NULL,
    tp_modal       VARCHAR2(10)   NOT NULL,
    ds_cor_hex     VARCHAR2(7),
    CONSTRAINT pk_ec_linha        PRIMARY KEY (id_linha),
    CONSTRAINT un_ec_linha_numero UNIQUE (nr_linha),
    CONSTRAINT ck_ec_linha_numero CHECK (nr_linha > 0),
    CONSTRAINT ck_ec_linha_modal  CHECK (tp_modal IN ('METRO', 'TREM', 'MONOTRILHO', 'BRT'))
);

-- Estação ou parada; uma estação pode atender várias linhas
CREATE TABLE t_ec_estacao (
    id_estacao        NUMBER(5)      NOT NULL,
    nm_estacao        VARCHAR2(60)   NOT NULL,
    st_acessivel      CHAR(1)        DEFAULT 'S' NOT NULL,
    nr_min_baldeacao  NUMBER(2)      DEFAULT 0   NOT NULL,
    nr_latitude       NUMBER(9,6)    NOT NULL,
    nr_longitude      NUMBER(9,6)    NOT NULL,
    CONSTRAINT pk_ec_estacao           PRIMARY KEY (id_estacao),
    CONSTRAINT un_ec_estacao_nome      UNIQUE (nm_estacao),
    CONSTRAINT ck_ec_estacao_acessivel CHECK (st_acessivel IN ('S', 'N')),
    CONSTRAINT ck_ec_estacao_baldeacao CHECK (nr_min_baldeacao >= 0),
    CONSTRAINT ck_ec_estacao_latitude  CHECK (nr_latitude  BETWEEN -90  AND 90),
    CONSTRAINT ck_ec_estacao_longitude CHECK (nr_longitude BETWEEN -180 AND 180)
);

-- Associativa LINHA x ESTACAO (N:N): ordem da estação na linha e tempo de
-- viagem até a próxima estação. É o grafo da rede usado no cálculo de rotas.
CREATE TABLE t_ec_linha_estacao (
    id_linha        NUMBER(3)   NOT NULL,
    id_estacao      NUMBER(5)   NOT NULL,
    nr_ordem        NUMBER(3)   NOT NULL,
    nr_min_proxima  NUMBER(3),
    CONSTRAINT pk_ec_linha_estacao       PRIMARY KEY (id_linha, id_estacao),
    CONSTRAINT un_ec_linha_estacao_ordem UNIQUE (id_linha, nr_ordem),
    CONSTRAINT ck_ec_linha_estacao_ordem CHECK (nr_ordem > 0),
    CONSTRAINT ck_ec_linha_estacao_tempo CHECK (nr_min_proxima > 0)
);

-- Passageiro cadastrado no aplicativo (dados fictícios)
CREATE TABLE t_ec_usuario (
    id_usuario              NUMBER(8)      NOT NULL,
    nm_usuario              VARCHAR2(80)   NOT NULL,
    ds_email                VARCHAR2(120)  NOT NULL,
    st_mobilidade_reduzida  CHAR(1)        DEFAULT 'N' NOT NULL,
    nr_reputacao            NUMBER(3)      DEFAULT 50  NOT NULL,
    dt_cadastro             DATE           DEFAULT SYSDATE NOT NULL,
    CONSTRAINT pk_ec_usuario            PRIMARY KEY (id_usuario),
    CONSTRAINT un_ec_usuario_email      UNIQUE (ds_email),
    CONSTRAINT ck_ec_usuario_email      CHECK (ds_email LIKE '%_@_%._%'),
    CONSTRAINT ck_ec_usuario_mobilidade CHECK (st_mobilidade_reduzida IN ('S', 'N')),
    CONSTRAINT ck_ec_usuario_reputacao  CHECK (nr_reputacao BETWEEN 0 AND 100)
);

-- Tipos de ocorrência (atraso, trem parado, superlotação, elevador...)
CREATE TABLE t_ec_tipo_ocorrencia (
    id_tipo_ocorrencia       NUMBER(3)      NOT NULL,
    nm_tipo                  VARCHAR2(50)   NOT NULL,
    ds_tipo                  VARCHAR2(200),
    st_afeta_acessibilidade  CHAR(1)        DEFAULT 'N' NOT NULL,
    CONSTRAINT pk_ec_tipo_ocorrencia       PRIMARY KEY (id_tipo_ocorrencia),
    CONSTRAINT un_ec_tipo_ocorrencia_nome  UNIQUE (nm_tipo),
    CONSTRAINT ck_ec_tipo_ocorrencia_acess CHECK (st_afeta_acessibilidade IN ('S', 'N'))
);

-- Problema relatado por um passageiro (tp_fonte = USUARIO) ou divulgado pela
-- operadora (tp_fonte = OPERADORA). A estação é opcional: NULL = linha inteira.
CREATE TABLE t_ec_ocorrencia (
    id_ocorrencia       NUMBER(10)     NOT NULL,
    id_tipo_ocorrencia  NUMBER(3)      NOT NULL,
    id_linha            NUMBER(3)      NOT NULL,
    id_estacao          NUMBER(5),
    id_usuario          NUMBER(8),
    tp_fonte            VARCHAR2(10)   NOT NULL,
    ds_ocorrencia       VARCHAR2(300)  NOT NULL,
    nr_atraso_min       NUMBER(3)      DEFAULT 0 NOT NULL,
    st_ocorrencia       VARCHAR2(10)   DEFAULT 'ABERTA' NOT NULL,
    dt_inicio           DATE           DEFAULT SYSDATE NOT NULL,
    dt_fim              DATE,
    CONSTRAINT pk_ec_ocorrencia        PRIMARY KEY (id_ocorrencia),
    CONSTRAINT ck_ec_ocorrencia_fonte  CHECK (tp_fonte IN ('USUARIO', 'OPERADORA')),
    -- relato de passageiro precisa de autor; aviso oficial não tem autor
    CONSTRAINT ck_ec_ocorrencia_autor  CHECK (
           (tp_fonte = 'USUARIO'   AND id_usuario IS NOT NULL)
        OR (tp_fonte = 'OPERADORA' AND id_usuario IS NULL)),
    CONSTRAINT ck_ec_ocorrencia_atraso CHECK (nr_atraso_min BETWEEN 0 AND 600),
    CONSTRAINT ck_ec_ocorrencia_status CHECK (
           st_ocorrencia IN ('ABERTA', 'CONFIRMADA', 'ENCERRADA', 'DESCARTADA')),
    -- ocorrência ativa não tem fim; encerrada ou descartada tem fim >= início
    CONSTRAINT ck_ec_ocorrencia_fim    CHECK (
           (st_ocorrencia IN ('ABERTA', 'CONFIRMADA') AND dt_fim IS NULL)
        OR (st_ocorrencia IN ('ENCERRADA', 'DESCARTADA')
            AND dt_fim IS NOT NULL AND dt_fim >= dt_inicio))
);

-- Associativa USUARIO x OCORRENCIA (N:N): outros passageiros confirmam
-- (st_confirma = S) ou desmentem (st_confirma = N) um relato.
CREATE TABLE t_ec_confirmacao (
    id_ocorrencia   NUMBER(10)  NOT NULL,
    id_usuario      NUMBER(8)   NOT NULL,
    st_confirma     CHAR(1)     DEFAULT 'S' NOT NULL,
    dt_confirmacao  DATE        DEFAULT SYSDATE NOT NULL,
    CONSTRAINT pk_ec_confirmacao        PRIMARY KEY (id_ocorrencia, id_usuario),
    CONSTRAINT ck_ec_confirmacao_status CHECK (st_confirma IN ('S', 'N'))
);

-- Nível de lotação informado pelo passageiro na plataforma de uma linha
-- (1 = vazia, 2 = tranquila, 3 = moderada, 4 = cheia, 5 = superlotada)
CREATE TABLE t_ec_registro_lotacao (
    id_registro   NUMBER(10)    NOT NULL,
    id_usuario    NUMBER(8)     NOT NULL,
    id_linha      NUMBER(3)     NOT NULL,
    id_estacao    NUMBER(5)     NOT NULL,
    nr_nivel      NUMBER(1)     NOT NULL,
    ds_sentido    VARCHAR2(40)  NOT NULL,
    dt_registro   DATE          DEFAULT SYSDATE NOT NULL,
    CONSTRAINT pk_ec_registro_lotacao PRIMARY KEY (id_registro),
    CONSTRAINT ck_ec_lotacao_nivel    CHECK (nr_nivel BETWEEN 1 AND 5)
);

-- Trajeto frequente salvo pelo passageiro; recebe alertas quando algo afeta
CREATE TABLE t_ec_trajeto (
    id_trajeto          NUMBER(10)    NOT NULL,
    id_usuario          NUMBER(8)     NOT NULL,
    id_estacao_origem   NUMBER(5)     NOT NULL,
    id_estacao_destino  NUMBER(5)     NOT NULL,
    nm_trajeto          VARCHAR2(40)  NOT NULL,
    hr_partida          VARCHAR2(5)   NOT NULL,
    st_alerta_ativo     CHAR(1)       DEFAULT 'S' NOT NULL,
    CONSTRAINT pk_ec_trajeto          PRIMARY KEY (id_trajeto),
    CONSTRAINT un_ec_trajeto_nome     UNIQUE (id_usuario, nm_trajeto),
    CONSTRAINT ck_ec_trajeto_horario  CHECK (REGEXP_LIKE(hr_partida, '^([01][0-9]|2[0-3]):[0-5][0-9]$')),
    CONSTRAINT ck_ec_trajeto_alerta   CHECK (st_alerta_ativo IN ('S', 'N')),
    CONSTRAINT ck_ec_trajeto_estacoes CHECK (id_estacao_origem <> id_estacao_destino)
);

-- Associativa TRAJETO x OCORRENCIA (N:N): alerta enviado ao passageiro
CREATE TABLE t_ec_alerta (
    id_trajeto     NUMBER(10)    NOT NULL,
    id_ocorrencia  NUMBER(10)    NOT NULL,
    tp_canal       VARCHAR2(5)   DEFAULT 'PUSH' NOT NULL,
    st_lido        CHAR(1)       DEFAULT 'N' NOT NULL,
    dt_envio       DATE          DEFAULT SYSDATE NOT NULL,
    CONSTRAINT pk_ec_alerta       PRIMARY KEY (id_trajeto, id_ocorrencia),
    CONSTRAINT ck_ec_alerta_canal CHECK (tp_canal IN ('PUSH', 'SMS', 'EMAIL')),
    CONSTRAINT ck_ec_alerta_lido  CHECK (st_lido IN ('S', 'N'))
);


-- -----------------------------------------------------------------------------
-- 2. ALTER TABLE: chaves estrangeiras (relacionamentos)
--    Criadas depois de todas as tabelas para não depender da ordem de criação.
-- -----------------------------------------------------------------------------

-- OPERADORA 1 : N LINHA
ALTER TABLE t_ec_linha ADD CONSTRAINT fk_ec_linha_operadora
    FOREIGN KEY (id_operadora) REFERENCES t_ec_operadora (id_operadora);

-- LINHA N : N ESTACAO (resolvido pela associativa LINHA_ESTACAO)
ALTER TABLE t_ec_linha_estacao ADD CONSTRAINT fk_ec_linha_estacao_linha
    FOREIGN KEY (id_linha) REFERENCES t_ec_linha (id_linha);
ALTER TABLE t_ec_linha_estacao ADD CONSTRAINT fk_ec_linha_estacao_estacao
    FOREIGN KEY (id_estacao) REFERENCES t_ec_estacao (id_estacao);

-- TIPO_OCORRENCIA 1 : N OCORRENCIA
ALTER TABLE t_ec_ocorrencia ADD CONSTRAINT fk_ec_ocorrencia_tipo
    FOREIGN KEY (id_tipo_ocorrencia) REFERENCES t_ec_tipo_ocorrencia (id_tipo_ocorrencia);
-- LINHA 1 : N OCORRENCIA
ALTER TABLE t_ec_ocorrencia ADD CONSTRAINT fk_ec_ocorrencia_linha
    FOREIGN KEY (id_linha) REFERENCES t_ec_linha (id_linha);
-- ESTACAO 0..1 : N OCORRENCIA (opcional: NULL = linha inteira)
ALTER TABLE t_ec_ocorrencia ADD CONSTRAINT fk_ec_ocorrencia_estacao
    FOREIGN KEY (id_estacao) REFERENCES t_ec_estacao (id_estacao);
-- USUARIO 0..1 : N OCORRENCIA (opcional: NULL = aviso oficial da operadora)
ALTER TABLE t_ec_ocorrencia ADD CONSTRAINT fk_ec_ocorrencia_usuario
    FOREIGN KEY (id_usuario) REFERENCES t_ec_usuario (id_usuario);

-- USUARIO N : N OCORRENCIA (resolvido pela associativa CONFIRMACAO)
ALTER TABLE t_ec_confirmacao ADD CONSTRAINT fk_ec_confirmacao_ocorrencia
    FOREIGN KEY (id_ocorrencia) REFERENCES t_ec_ocorrencia (id_ocorrencia);
ALTER TABLE t_ec_confirmacao ADD CONSTRAINT fk_ec_confirmacao_usuario
    FOREIGN KEY (id_usuario) REFERENCES t_ec_usuario (id_usuario);

-- USUARIO, LINHA e ESTACAO 1 : N REGISTRO_LOTACAO
ALTER TABLE t_ec_registro_lotacao ADD CONSTRAINT fk_ec_lotacao_usuario
    FOREIGN KEY (id_usuario) REFERENCES t_ec_usuario (id_usuario);
ALTER TABLE t_ec_registro_lotacao ADD CONSTRAINT fk_ec_lotacao_linha
    FOREIGN KEY (id_linha) REFERENCES t_ec_linha (id_linha);
ALTER TABLE t_ec_registro_lotacao ADD CONSTRAINT fk_ec_lotacao_estacao
    FOREIGN KEY (id_estacao) REFERENCES t_ec_estacao (id_estacao);

-- USUARIO 1 : N TRAJETO; ESTACAO 1 : N TRAJETO (duas vezes: origem e destino)
ALTER TABLE t_ec_trajeto ADD CONSTRAINT fk_ec_trajeto_usuario
    FOREIGN KEY (id_usuario) REFERENCES t_ec_usuario (id_usuario);
ALTER TABLE t_ec_trajeto ADD CONSTRAINT fk_ec_trajeto_origem
    FOREIGN KEY (id_estacao_origem) REFERENCES t_ec_estacao (id_estacao);
ALTER TABLE t_ec_trajeto ADD CONSTRAINT fk_ec_trajeto_destino
    FOREIGN KEY (id_estacao_destino) REFERENCES t_ec_estacao (id_estacao);

-- TRAJETO N : N OCORRENCIA (resolvido pela associativa ALERTA)
ALTER TABLE t_ec_alerta ADD CONSTRAINT fk_ec_alerta_trajeto
    FOREIGN KEY (id_trajeto) REFERENCES t_ec_trajeto (id_trajeto);
ALTER TABLE t_ec_alerta ADD CONSTRAINT fk_ec_alerta_ocorrencia
    FOREIGN KEY (id_ocorrencia) REFERENCES t_ec_ocorrencia (id_ocorrencia);


-- -----------------------------------------------------------------------------
-- 3. Comentários (documentação gravada no próprio dicionário de dados)
-- -----------------------------------------------------------------------------
COMMENT ON TABLE t_ec_operadora        IS 'Empresa que opera linhas de metrô, trem ou BRT';
COMMENT ON TABLE t_ec_linha            IS 'Linha de transporte sobre trilhos ou BRT';
COMMENT ON TABLE t_ec_estacao          IS 'Estação ou parada; pode atender mais de uma linha';
COMMENT ON TABLE t_ec_linha_estacao    IS 'Estações de cada linha, em ordem, com o tempo até a próxima (grafo da rede)';
COMMENT ON TABLE t_ec_usuario          IS 'Passageiro cadastrado no aplicativo (dados fictícios)';
COMMENT ON TABLE t_ec_tipo_ocorrencia  IS 'Classificação das ocorrências (atraso, superlotação, elevador...)';
COMMENT ON TABLE t_ec_ocorrencia       IS 'Problema relatado por passageiro ou divulgado pela operadora';
COMMENT ON TABLE t_ec_confirmacao      IS 'Confirmação ou contestação de uma ocorrência por outro passageiro';
COMMENT ON TABLE t_ec_registro_lotacao IS 'Nível de lotação (1 a 5) informado na plataforma de uma linha';
COMMENT ON TABLE t_ec_trajeto          IS 'Trajeto frequente salvo pelo passageiro para receber alertas';
COMMENT ON TABLE t_ec_alerta           IS 'Alerta enviado a um trajeto afetado por uma ocorrência';
