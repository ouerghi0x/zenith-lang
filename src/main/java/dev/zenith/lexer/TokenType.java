package dev.zenith.lexer;

public enum TokenType {
    // Keywords
    CONFIG,
    MODEL,
    ENUM,
    RELATIONS,
    ENDPOINTS,
    CRUD,
    DTO,
    GET,
    POST,

    PUT,
    DELETE,
    PATCH,
    AUTH,
    PUBLIC,
    GUARD,
    ACTION,
    // ACTIONS KEYWORDS
    CREATE,
    UPDATE,
    VAR,
    TRUE,
    FALSE,
    SEED,
    IMPORT,
    // Identifiers & Literals
    IDENTIFIER,
    STRING_LITERAL,
    NUMBER_LITERAL,

    // Punctuation & Delimiters
    LBRACE, // {
    RBRACE, // }
    LPAREN, // (
    RPAREN, // )
    LBRACKET, // [
    RBRACKET, // ]
    COLON, // :
    COMMA, // ,
    EQUAL, // =
    EQUAL_EQUAL, // ==
    BANG, // !
    BANG_EQUAL, // !=
    AMP_AMP, // &&
    PIPE_PIPE, // ||
    AT, // @
    QUESTION, // ?
    ARROW, // ->
    DOT, // .
    SLASH, // /
    LT, // <
    GT, // >
    LT_EQUAL, // <=
    GT_EQUAL, // >=
    PLUS, // +
    MINUS, // -
    STAR, // *

    // Special
    EOF, BODY, JOB, CRON
}
