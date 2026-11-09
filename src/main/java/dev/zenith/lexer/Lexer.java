package dev.zenith.lexer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Lexer {
    private final String source;
    private final int length;
    private int cursor = 0;
    private int line = 1;
    private int column = 1;

    private static final Map<String, TokenType> KEYWORDS = new HashMap<>();

    static {
        KEYWORDS.put("config", TokenType.CONFIG);
        KEYWORDS.put("model", TokenType.MODEL);
        KEYWORDS.put("dto", TokenType.DTO);
        KEYWORDS.put("body", TokenType.BODY);
        KEYWORDS.put("seed", TokenType.SEED);
        KEYWORDS.put("job", TokenType.JOB);
        KEYWORDS.put("import", TokenType.IMPORT);
        KEYWORDS.put("enum", TokenType.ENUM);
        KEYWORDS.put("relations", TokenType.RELATIONS);
        KEYWORDS.put("endpoints", TokenType.ENDPOINTS);
        KEYWORDS.put("crud", TokenType.CRUD);
        KEYWORDS.put("get", TokenType.GET);
        KEYWORDS.put("post", TokenType.POST);
        KEYWORDS.put("put", TokenType.PUT);
        KEYWORDS.put("delete", TokenType.DELETE);
        KEYWORDS.put("patch", TokenType.PATCH);
        KEYWORDS.put("auth", TokenType.AUTH);
        KEYWORDS.put("public", TokenType.PUBLIC);
        KEYWORDS.put("guard", TokenType.GUARD);
        KEYWORDS.put("action", TokenType.ACTION);
        KEYWORDS.put("true", TokenType.TRUE);
        KEYWORDS.put("false", TokenType.FALSE);
        KEYWORDS.put("create", TokenType.CREATE);
        KEYWORDS.put("update", TokenType.UPDATE);
        KEYWORDS.put("var", TokenType.VAR);
        KEYWORDS.put("cron", TokenType.CRON);
        KEYWORDS.put("mail", TokenType.MAIL);
        KEYWORDS.put("send", TokenType.SEND);
    }

    public Lexer(String source) {
        this.source = source != null ? source : "";
        this.length = this.source.length();
    }

    public List<Token> tokenize() {
        List<Token> tokens = new ArrayList<>();
        while (!isAtEnd()) {
            skipWhitespaceAndComments();
            if (isAtEnd())
                break;

            char ch = peek();
            int startLine = line;
            int startCol = column;

            if (ch == '-' && peekNext() == '>') {
                advance(); // -
                advance(); // >
                tokens.add(new Token(TokenType.ARROW, "->", startLine, startCol));
            } else if (ch == '-') {
                advance();
                tokens.add(new Token(TokenType.MINUS, "-", startLine, startCol));
            } else if (ch == '+') {
                advance();
                tokens.add(new Token(TokenType.PLUS, "+", startLine, startCol));
            } else if (ch == '*') {
                advance();
                tokens.add(new Token(TokenType.STAR, "*", startLine, startCol));
            } else if (ch == '{') {
                advance();
                tokens.add(new Token(TokenType.LBRACE, "{", startLine, startCol));
            } else if (ch == '}') {
                advance();
                tokens.add(new Token(TokenType.RBRACE, "}", startLine, startCol));
            } else if (ch == '(') {
                advance();
                tokens.add(new Token(TokenType.LPAREN, "(", startLine, startCol));
            } else if (ch == ')') {
                advance();
                tokens.add(new Token(TokenType.RPAREN, ")", startLine, startCol));
            } else if (ch == '[') {
                advance();
                tokens.add(new Token(TokenType.LBRACKET, "[", startLine, startCol));
            } else if (ch == ']') {
                advance();
                tokens.add(new Token(TokenType.RBRACKET, "]", startLine, startCol));
            } else if (ch == ':') {
                advance();
                tokens.add(new Token(TokenType.COLON, ":", startLine, startCol));
            } else if (ch == ',') {
                advance();
                tokens.add(new Token(TokenType.COMMA, ",", startLine, startCol));
            } else if (ch == '=') {
                advance();
                if (peek() == '=') {
                    advance();
                    tokens.add(new Token(TokenType.EQUAL_EQUAL, "==", startLine, startCol));
                } else {
                    tokens.add(new Token(TokenType.EQUAL, "=", startLine, startCol));
                }
            } else if (ch == '!') {
                advance();
                if (peek() == '=') {
                    advance();
                    tokens.add(new Token(TokenType.BANG_EQUAL, "!=", startLine, startCol));
                } else {
                    tokens.add(new Token(TokenType.BANG, "!", startLine, startCol));
                }
            } else if (ch == '&' && peekNext() == '&') {
                advance();
                advance();
                tokens.add(new Token(TokenType.AMP_AMP, "&&", startLine, startCol));
            } else if (ch == '|' && peekNext() == '|') {
                advance();
                advance();
                tokens.add(new Token(TokenType.PIPE_PIPE, "||", startLine, startCol));
            } else if (ch == '@') {
                advance();
                tokens.add(new Token(TokenType.AT, "@", startLine, startCol));
            } else if (ch == '?') {
                advance();
                tokens.add(new Token(TokenType.QUESTION, "?", startLine, startCol));
            } else if (ch == '.') {
                advance();
                tokens.add(new Token(TokenType.DOT, ".", startLine, startCol));
            } else if (ch == '/') {
                advance();
                tokens.add(new Token(TokenType.SLASH, "/", startLine, startCol));
            } else if (ch == '<') {
                advance();
                if (peek() == '=') {
                    advance();
                    tokens.add(new Token(TokenType.LT_EQUAL, "<=", startLine, startCol));
                } else {
                    tokens.add(new Token(TokenType.LT, "<", startLine, startCol));
                }
            } else if (ch == '>') {
                advance();
                if (peek() == '=') {
                    advance();
                    tokens.add(new Token(TokenType.GT_EQUAL, ">=", startLine, startCol));
                } else {
                    tokens.add(new Token(TokenType.GT, ">", startLine, startCol));
                }
            } else if (ch == '"' || ch == '\'') {
                tokens.add(readString(ch, startLine, startCol));
            } else if (isDigit(ch)) {
                tokens.add(readNumber(startLine, startCol));
            } else if (isAlpha(ch)) {
                tokens.add(readIdentifierOrKeyword(startLine, startCol));
            } else {
                throw new RuntimeException(
                        "Lexical error at " + startLine + ":" + startCol + ": Unexpected character '" + ch + "'");
            }
        }
        tokens.add(new Token(TokenType.EOF, "", line, column));
        return tokens;
    }

    private Token readString(char quoteChar, int startLine, int startCol) {
        advance(); // opening quote
        StringBuilder sb = new StringBuilder();
        while (!isAtEnd() && peek() != quoteChar) {
            char ch = advance();
            if (ch == '\\' && !isAtEnd()) {
                char next = advance();
                switch (next) {
                    case 'n':
                        sb.append('\n');
                        break;
                    case 't':
                        sb.append('\t');
                        break;
                    case 'r':
                        sb.append('\r');
                        break;
                    case '\\':
                        sb.append('\\');
                        break;
                    case '"':
                        sb.append('"');
                        break;
                    case '\'':
                        sb.append('\'');
                        break;
                    default:
                        sb.append(next);
                        break;
                }
            } else {
                sb.append(ch);
            }
        }
        if (isAtEnd()) {
            throw new RuntimeException("Unterminated string literal starting at " + startLine + ":" + startCol);
        }
        advance(); // closing quote
        return new Token(TokenType.STRING_LITERAL, sb.toString(), startLine, startCol);
    }

    private Token readNumber(int startLine, int startCol) {
        StringBuilder sb = new StringBuilder();
        while (!isAtEnd() && isDigit(peek())) {
            sb.append(advance());
        }
        if (!isAtEnd() && peek() == '.' && isDigit(peekNext())) {
            sb.append(advance()); // .
            while (!isAtEnd() && isDigit(peek())) {
                sb.append(advance());
            }
        }
        return new Token(TokenType.NUMBER_LITERAL, sb.toString(), startLine, startCol);
    }

    private Token readIdentifierOrKeyword(int startLine, int startCol) {
        StringBuilder sb = new StringBuilder();
        while (!isAtEnd() && (isAlphaNumeric(peek()) || peek() == '_')) {
            sb.append(advance());
        }
        String text = sb.toString();
        TokenType type = KEYWORDS.getOrDefault(text, TokenType.IDENTIFIER);
        return new Token(type, text, startLine, startCol);
    }

    private void skipWhitespaceAndComments() {
        while (!isAtEnd()) {
            char ch = peek();
            if (ch == ' ' || ch == '\t' || ch == '\r') {
                advance();
            } else if (ch == '\n') {
                advance();
            } else if (ch == '/' && peekNext() == '/') {
                // Single-line comment
                while (!isAtEnd() && peek() != '\n') {
                    advance();
                }
            } else if (ch == '/' && peekNext() == '*') {
                // Block comment
                advance(); // /
                advance(); // *
                while (!isAtEnd() && !(peek() == '*' && peekNext() == '/')) {
                    advance();
                }
                if (!isAtEnd()) {
                    advance(); // *
                    advance(); // /
                }
            } else {
                break;
            }
        }
    }

    private char peek() {
        return cursor < length ? source.charAt(cursor) : '\0';
    }

    private char peekNext() {
        return cursor + 1 < length ? source.charAt(cursor + 1) : '\0';
    }

    private char advance() {
        if (isAtEnd())
            return '\0';
        char ch = source.charAt(cursor++);
        if (ch == '\n') {
            line++;
            column = 1;
        } else {
            column++;
        }
        return ch;
    }

    private boolean isAtEnd() {
        return cursor >= length;
    }

    private boolean isDigit(char c) {
        return c >= '0' && c <= '9';
    }

    private boolean isAlpha(char c) {
        return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || c == '_';
    }

    private boolean isAlphaNumeric(char c) {
        return isAlpha(c) || isDigit(c);
    }
}
