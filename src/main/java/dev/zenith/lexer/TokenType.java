package dev.zenith.lexer;

public enum TokenType {
    // Keywords
    CONFIG,
    MODEL,
    ENUM,
    RELATIONS,
    ENDPOINTS,
    CRUD,
    GET,
    POST,
    PUT,
    DELETE,
    PATCH,
    AUTH,
    PUBLIC,
    GUARD,
    ACTION,
    TRUE,
    FALSE,

    // Identifiers & Literals
    IDENTIFIER,
    STRING_LITERAL,
    NUMBER_LITERAL,

    // Punctuation & Delimiters
    LBRACE,       // {
    RBRACE,       // }
    LPAREN,       // (
    RPAREN,       // )
    LBRACKET,     // [
    RBRACKET,     // ]
    COLON,        // :
    COMMA,        // ,
    EQUAL,        // =
    EQUAL_EQUAL,  // ==
    BANG,         // !
    BANG_EQUAL,   // !=
    AMP_AMP,      // &&
    PIPE_PIPE,    // ||
    AT,           // @
    QUESTION,     // ?
    ARROW,        // ->
    DOT,          // .
    SLASH,        // /
    LT,           // <
    GT,           // >
    LT_EQUAL,     // <=
    GT_EQUAL,     // >=

    // Special
    EOF
}
