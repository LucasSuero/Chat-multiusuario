-- Script de criacao do banco (SQLite). Executado automaticamente por Database.inicializar().
-- Conversas particulares e grupos compartilham a tabela conversa (coluna tipo).

CREATE TABLE IF NOT EXISTS usuario (
    id         INTEGER PRIMARY KEY AUTOINCREMENT,
    username   TEXT    NOT NULL UNIQUE COLLATE NOCASE,
    senha_hash TEXT    NOT NULL,
    avatar     TEXT    NOT NULL DEFAULT 'avatar01',
    criado_em  TEXT    NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%SZ', 'now'))
);

CREATE TABLE IF NOT EXISTS conversa (
    id         INTEGER PRIMARY KEY AUTOINCREMENT,
    tipo       TEXT    NOT NULL CHECK (tipo IN ('PRIVADA', 'GRUPO')),
    nome       TEXT,
    imagem     TEXT,
    criador_id INTEGER REFERENCES usuario(id),
    criada_em  TEXT    NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%SZ', 'now'))
);

CREATE TABLE IF NOT EXISTS conversa_participante (
    conversa_id INTEGER NOT NULL REFERENCES conversa(id) ON DELETE CASCADE,
    usuario_id  INTEGER NOT NULL REFERENCES usuario(id)  ON DELETE CASCADE,
    entrou_em   TEXT    NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%SZ', 'now')),
    PRIMARY KEY (conversa_id, usuario_id)
);

CREATE TABLE IF NOT EXISTS mensagem (
    id           INTEGER PRIMARY KEY AUTOINCREMENT,
    conversa_id  INTEGER NOT NULL REFERENCES conversa(id) ON DELETE CASCADE,
    remetente_id INTEGER NOT NULL REFERENCES usuario(id),
    conteudo     TEXT    NOT NULL,
    enviada_em   TEXT    NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_mensagem_conversa ON mensagem(conversa_id, id);
CREATE INDEX IF NOT EXISTS idx_participante_usuario ON conversa_participante(usuario_id);

-- Sala Geral: conversa publica da qual todo usuario participa (e o "broadcast" exigido pela atividade).
-- Identificada por tipo GRUPO + criador_id NULL + nome 'Geral' (grupos de usuarios sempre tem criador).
INSERT INTO conversa (tipo, nome, imagem)
SELECT 'GRUPO', 'Geral', 'grupo01'
WHERE NOT EXISTS (SELECT 1 FROM conversa WHERE tipo = 'GRUPO' AND criador_id IS NULL AND nome = 'Geral');
