package br.unicesumar.chat.service;

/** Códigos de erro enviados aos clientes pelo protocolo. */
public enum Erro {
    COMANDO_INVALIDO,
    NAO_AUTENTICADO,
    CREDENCIAIS_INVALIDAS,
    USUARIO_EXISTE,
    VALIDACAO,
    NAO_ENCONTRADO,
    CONVERSA_INACESSIVEL,
    TEMPO_ESGOTADO,
    SERVIDOR_CHEIO,
    ERRO_INTERNO
}
