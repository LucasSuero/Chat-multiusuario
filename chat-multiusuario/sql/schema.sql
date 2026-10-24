-- Script de criação do banco e da tabela de mensagens (MySQL 8+)
CREATE DATABASE IF NOT EXISTS chat_db
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

USE chat_db;

CREATE TABLE IF NOT EXISTS mensagens (
    id        BIGINT       NOT NULL AUTO_INCREMENT,
    remetente VARCHAR(20)  NOT NULL,
    conteudo  VARCHAR(500) NOT NULL,
    horario   DATETIME(3)  NOT NULL,
    PRIMARY KEY (id),
    INDEX idx_mensagens_horario (horario)
) ENGINE = InnoDB;
