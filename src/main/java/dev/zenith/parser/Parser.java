package dev.zenith.parser;

import dev.zenith.ast.*;
import dev.zenith.lexer.Token;
import dev.zenith.lexer.TokenType;

import java.util.*;

public class Parser {
    private final List<Token> tokens;
    private int cursor = 0;
    private final String sourceFile;

    private static final Set<String> ALLOWED_CONFIG_KEYS = Set.of(
            "app", "package", "basepath", "port", "database", "auth", "swagger", "flyway", "storage", "storagebucket",
            "usermodel", "jwtsecret", "jwtexpiration", "ratelimit", "mail", "mailprovider", "mailfrom", "cache", "cacheprovider", "cachettl"
    );

    public Parser(List<Token> tokens) {
        this(tokens, null);
    }

    public Parser(List<Token> tokens, String sourceFile) {
        this.tokens = tokens != null ? tokens : Collections.emptyList();
        this.sourceFile = sourceFile;
    }

    public ZenithFile parse() {
        AppConfig config = null;
        List<EnumDef> enums = new ArrayList<>();
        List<ModelDef> models = new ArrayList<>();
        List<DtoDef> dtos = new ArrayList<>();
        List<SeedDef> seeds = new ArrayList<>();
        List<ImportDef> imports = new ArrayList<>();
        List<JobDef> jobs = new ArrayList<>();
        List<MailDef> mails = new ArrayList<>();
        while (!isAtEnd()) {
            Token tok = peek();
            if (tok.getType() == TokenType.CONFIG) {
                if (config != null) {
                    throw error(tok, "Duplicate 'config' block. Only one config block is allowed.");
                }
                config = parseConfig();
            } else if (tok.getType() == TokenType.IMPORT) {
                imports.add(parseImport());
            } else if (tok.getType() == TokenType.ENUM) {
                enums.add(parseEnum());
            } else if (tok.getType() == TokenType.MODEL) {
                models.add(parseModel());
            } else if (tok.getType() == TokenType.EOF) {
                break;
            } else if (tok.getType() == TokenType.DTO) {
                dtos.add(parseDto());
            } else if (tok.getType() == TokenType.SEED) {
                seeds.add(parseSeed());
            } else if (tok.getType() == TokenType.JOB) {
                jobs.add(parseJob());
            } else if (tok.getType() == TokenType.MAIL) {
                mails.add(parseMail());
            } else {
                throw error(tok,
                        "Expected 'import', 'config', 'enum', 'model', 'dto', 'seed', 'job', or 'mail' but found '"
                                + tok.getValue()
                                + "'");
            }
        }

        boolean explicitConfig = config != null;
        if (config == null) {
            config = AppConfig.defaults();
        }

        return new ZenithFile(config, enums, models, dtos, seeds, jobs, mails, imports, explicitConfig);
    }

    private JobDef parseJob() {
        consume(TokenType.JOB, "Expected 'job'");
        Token nameTok = consume(TokenType.IDENTIFIER, "Expected job name after 'job'");
        consume(TokenType.LBRACE, "Expected '{' after job name");

        String cron = "";
        String fixedRate = "";
        String description = "";
        boolean enabled = true;
        List<GuardDef> guards = new ArrayList<>();
        List<String> actions = new ArrayList<>();

        while (!isAtEnd() && peek().getType() != TokenType.RBRACE) {
            Token key = peek();
            if (key.getType() == TokenType.CRON) {
                advance();
                consume(TokenType.COLON, "Expected ':' after 'cron'");
                cron = consume(TokenType.STRING_LITERAL, "Expected cron expression string").getValue();
            } else if (key.getType() == TokenType.ACTION) {
                advance();
                match(TokenType.COLON); // optional ':'
                consume(TokenType.LBRACE, "Expected '{' to start action block");
                while (!isAtEnd() && peek().getType() != TokenType.RBRACE) {
                    String a = parseActionStatement();
                    if (!a.isEmpty()) {
                        actions.add(a);
                    }
                    match(TokenType.COMMA);
                }
                consume(TokenType.RBRACE, "Expected '}' closing action block");
            } else if (key.getType() == TokenType.CREATE) {
                actions.add(parseCreateAction());
            } else if (key.getType() == TokenType.UPDATE) {
                actions.add(parseUpdateAction());
            } else if (key.getType() == TokenType.GUARD) {
                advance();
                consume(TokenType.COLON, "Expected ':' after 'guard'");
                int guardLine = key.getLine();
                StringBuilder expr = new StringBuilder();
                int bracketDepth = 0;
                int parenDepth = 0;
                while (!isAtEnd() && peek().getType() != TokenType.RBRACE
                        && peek().getType() != TokenType.GUARD
                        && peek().getType() != TokenType.ACTION
                        && peek().getLine() == guardLine) {
                    if (peek().getType() == TokenType.COMMA && bracketDepth == 0 && parenDepth == 0) {
                        break;
                    }
                    Token t = advance();
                    if (t.getType() == TokenType.LBRACKET)
                        bracketDepth++;
                    else if (t.getType() == TokenType.RBRACKET)
                        bracketDepth--;
                    else if (t.getType() == TokenType.LPAREN)
                        parenDepth++;
                    else if (t.getType() == TokenType.RPAREN)
                        parenDepth--;
                    expr.append(t.getValue()).append(" ");
                }
                String errMessage = "Guard condition failed";
                if (match(TokenType.COMMA)) {
                    if (peek().getType() == TokenType.STRING_LITERAL) {
                        errMessage = advance().getValue();
                    }
                }
                guards.add(new GuardDef(expr.toString().trim(), errMessage));
            } else if (key.getType() == TokenType.IDENTIFIER) {
                Token idTok = advance();
                consume(TokenType.COLON, "Expected ':' after '" + idTok.getValue() + "'");
                String k = idTok.getValue().toLowerCase();
                if ("cron".equalsIgnoreCase(k)) {
                    cron = consume(TokenType.STRING_LITERAL, "Expected cron expression string").getValue();
                } else if ("description".equalsIgnoreCase(k)) {
                    description = consume(TokenType.STRING_LITERAL, "Expected description string").getValue();
                } else if ("fixedrate".equalsIgnoreCase(k) || "rate".equalsIgnoreCase(k)
                        || "interval".equalsIgnoreCase(k)) {
                    Object frVal = parseLiteralOrIdentifier();
                    fixedRate = frVal != null ? String.valueOf(frVal) : null;
                } else if ("enabled".equalsIgnoreCase(k)) {
                    enabled = parseBoolean();
                } else {
                    parseLiteralOrIdentifier(); // skip unknown property
                }
            } else {
                throw error(key, "Unexpected token in job definition: '" + key.getValue() + "'");
            }
            match(TokenType.COMMA);
        }
        consume(TokenType.RBRACE, "Expected '}' closing job block");

        JobDef job = new JobDef(nameTok.getValue(), cron, fixedRate, guards, actions, description, enabled);
        job.setSourceFile(sourceFile);
        job.setSourceLine(nameTok.getLine());
        return job;
    }

    private MailDef parseMail() {
        Token mailTok = consume(TokenType.MAIL, "Expected 'mail'");
        Token nameTok = consume(TokenType.IDENTIFIER, "Expected mail contract name after 'mail'");
        List<FieldDef> parameters = new ArrayList<>();
        if (match(TokenType.LPAREN)) {
            while (!isAtEnd() && peek().getType() != TokenType.RBRACE && peek().getType() != TokenType.RPAREN) {
                Token pName = consume(TokenType.IDENTIFIER, "Expected parameter name in mail contract");
                consume(TokenType.COLON, "Expected ':' after parameter name");
                TypeRef pType = parseTypeRef();
                parameters.add(new FieldDef(pName.getValue(), pType, false, Collections.emptyList(), null));
                match(TokenType.COMMA);
            }
            consume(TokenType.RPAREN, "Expected ')' closing mail parameters");
        }

        consume(TokenType.LBRACE, "Expected '{' to start mail block");
        String toExpr = "";
        String subjectExpr = "";
        String templatePath = "";
        String fromOverride = null;

        while (!isAtEnd() && peek().getType() != TokenType.RBRACE) {
            Token keyTok = consume(TokenType.IDENTIFIER, "Expected mail property ('to', 'subject', 'template', 'from')");
            consume(TokenType.COLON, "Expected ':' after mail property");
            String k = keyTok.getValue().toLowerCase();
            int line = keyTok.getLine();
            if ("to".equals(k)) {
                StringBuilder sb = new StringBuilder();
                while (!isAtEnd() && peek().getType() != TokenType.COMMA && peek().getType() != TokenType.RBRACE && peek().getLine() == line) {
                    sb.append(advance().getValue());
                }
                toExpr = sb.toString().trim();
            } else if ("subject".equals(k)) {
                StringBuilder sb = new StringBuilder();
                while (!isAtEnd() && peek().getType() != TokenType.COMMA && peek().getType() != TokenType.RBRACE && peek().getLine() == line) {
                    Token t = advance();
                    if (t.getType() == TokenType.STRING_LITERAL) {
                        sb.append("\"").append(t.getValue()).append("\"");
                    } else {
                        sb.append(t.getValue());
                    }
                }
                subjectExpr = sb.toString().trim();
            } else if ("template".equals(k)) {
                templatePath = consume(TokenType.STRING_LITERAL, "Expected template file path string").getValue();
            } else if ("from".equals(k)) {
                fromOverride = consume(TokenType.STRING_LITERAL, "Expected sender email string").getValue();
            } else {
                parseLiteralOrIdentifier();
            }
            match(TokenType.COMMA);
        }
        consume(TokenType.RBRACE, "Expected '}' closing mail block");

        MailDef mailDef = new MailDef(nameTok.getValue(), parameters, toExpr, subjectExpr, templatePath, fromOverride);
        mailDef.setSourceFile(sourceFile);
        mailDef.setSourceLine(mailTok.getLine());
        return mailDef;
    }

    private ImportDef parseImport() {
        Token importTok = consume(TokenType.IMPORT, "Expected 'import'");
        Token pathTok = consume(TokenType.STRING_LITERAL, "Expected file path string after 'import'");
        return new ImportDef(pathTok.getValue(), importTok.getLine(), importTok.getColumn());
    }

    private SeedDef parseSeed() {
        consume(TokenType.SEED, "Expected 'seed'");
        Token nameToken = consume(TokenType.IDENTIFIER, "Expected model name after 'seed'");
        boolean hasOuterBrace = match(TokenType.LBRACE);
        consume(TokenType.LBRACKET, "Expected '[' to start seed rows");

        List<Map<String, Object>> rows = new ArrayList<>();

        while (!isAtEnd() && peek().getType() != TokenType.RBRACKET) {
            rows.add(parseSeedRow());
            match(TokenType.COMMA); // optional comma between row objects
        }

        consume(TokenType.RBRACKET, "Expected ']' closing seed rows");
        if (hasOuterBrace) {
            consume(TokenType.RBRACE, "Expected '}' closing seed block");
        }

        return new SeedDef(nameToken.getValue(), rows);
    }

    private Map<String, Object> parseSeedRow() {
        consume(TokenType.LBRACE, "Expected '{' to start seed object");
        Map<String, Object> row = new LinkedHashMap<>();

        while (!isAtEnd() && peek().getType() != TokenType.RBRACE) {
            Token keyToken = consumeKeyName();
            consume(TokenType.COLON, "Expected ':' after field name '" + keyToken.getValue() + "'");
            Object value = parseSeedValue();
            row.put(keyToken.getValue(), value);
            match(TokenType.COMMA); // optional comma between fields
        }

        consume(TokenType.RBRACE, "Expected '}' closing seed object");
        return row;
    }

    private Object parseSeedValue() {
        Token tok = peek();
        if (tok.getType() == TokenType.STRING_LITERAL) {
            advance();
            return tok.getValue();
        } else if (tok.getType() == TokenType.NUMBER_LITERAL) {
            advance();
            try {
                if (tok.getValue().contains(".")) {
                    return Double.parseDouble(tok.getValue());
                } else {
                    return Long.parseLong(tok.getValue());
                }
            } catch (NumberFormatException e) {
                return tok.getValue();
            }
        } else if (tok.getType() == TokenType.TRUE) {
            advance();
            return true;
        } else if (tok.getType() == TokenType.FALSE) {
            advance();
            return false;
        } else if (tok.getType() == TokenType.SEED) {
            advance(); // consume 'seed'
            Token modelTok = consume(TokenType.IDENTIFIER, "Expected model name after 'seed'");
            consume(TokenType.LBRACKET, "Expected '[' after model name in seed reference");
            Token idxTok = consume(TokenType.NUMBER_LITERAL, "Expected integer index inside '['");
            consume(TokenType.RBRACKET, "Expected ']' closing seed reference index");
            return new SeedRef(modelTok.getValue(), Integer.parseInt(idxTok.getValue()));
        } else if (tok.getType() == TokenType.IDENTIFIER) {
            Token idTok = advance();
            if (match(TokenType.LBRACKET)) {
                Token idxTok = consume(TokenType.NUMBER_LITERAL, "Expected integer index inside '['");
                consume(TokenType.RBRACKET, "Expected ']' closing index");
                return new SeedRef(idTok.getValue(), Integer.parseInt(idxTok.getValue()));
            }
            if (match(TokenType.DOT)) {
                Token subTok = consume(TokenType.IDENTIFIER, "Expected identifier after '.'");
                return idTok.getValue() + "." + subTok.getValue();
            }
            return idTok.getValue();
        } else {
            throw error(tok,
                    "Expected seed value (string, number, boolean, or identifier), but found '" + tok.getValue() + "'");
        }
    }

    private DtoDef parseDto() {
        consume(TokenType.DTO, "Expected 'dto'");
        Token nameToken = consume(TokenType.IDENTIFIER, "Expected dto name");
        consume(TokenType.LBRACE, "Expected '{' after dto name");

        List<FieldDef> fields = new ArrayList<>();

        while (!isAtEnd() && peek().getType() != TokenType.RBRACE) {
            Token cur = peek();
            try {
                FieldDef field = parseField();
                fields.add(field);
            } catch (Exception e) {
                throw error(cur, "Unexpected token in dto body: '" + cur.getValue() + "'");
            }
        }

        consume(TokenType.RBRACE, "Expected '}' closing dto block");

        DtoDef dto = new DtoDef(nameToken.getValue(), fields);
        dto.setSourceFile(sourceFile);
        dto.setSourceLine(nameToken.getLine());
        return dto;
    }

    private AppConfig parseConfig() {
        consume(TokenType.CONFIG, "Expected 'config'");
        consume(TokenType.LBRACE, "Expected '{' after 'config'");

        Map<String, Object> props = new LinkedHashMap<>();

        while (!isAtEnd() && peek().getType() != TokenType.RBRACE) {
            Token keyToken = consumeKeyName();
            String key = keyToken.getValue();
            String keyLower = key.toLowerCase();

            if (!ALLOWED_CONFIG_KEYS.contains(keyLower)) {
                throw error(keyToken, "Invalid configuration key '" + key + "'. Supported configuration keys are: "
                        + "app, package, basePath, port, database, auth, swagger, flyway, storage, storageBucket, userModel, jwtSecret, jwtExpiration, rateLimit, mail, mailProvider, mailFrom, cache, cacheProvider, cacheTtl.");
            }

            boolean isDuplicate = props.keySet().stream().anyMatch(existing -> existing.equalsIgnoreCase(key));
            if (isDuplicate) {
                throw error(keyToken, "Duplicate configuration key '" + key
                        + "' in config block. Each configuration key can only be declared once.");
            }

            consume(TokenType.COLON, "Expected ':' after key '" + key + "'");
            Object val;
            if ("jwtexpiration".equals(keyLower) && peek().getType() == TokenType.NUMBER_LITERAL
                    && peekNext().getType() == TokenType.IDENTIFIER && peekNext().getLine() == peek().getLine()) {
                // Support unquoted duration literal like 24h, 30m, 7d, 30s
                Token numTok = advance();
                Token unitTok = advance();
                val = numTok.getValue() + unitTok.getValue();
            } else {
                val = parseLiteralOrIdentifier();
            }

            if (val != null) {
                String valStr = val.toString().trim();
                if ("database".equals(keyLower)) {
                    String d = valStr.toLowerCase();
                    if (!d.equals("postgres") && !d.equals("postgresql") && !d.equals("mysql") && !d.equals("h2")) {
                        throw error(keyToken, "Invalid database dialect '" + valStr
                                + "'. Supported database options are: postgres, mysql, h2.");
                    }
                } else if ("auth".equals(keyLower)) {
                    String a = valStr.toLowerCase();
                    if (!a.equals("jwt") && !a.equals("none") && !a.equals("false") && !a.equals("true")) {
                        throw error(keyToken, "Invalid auth mode '" + valStr + "'. Supported options are: jwt, none.");
                    }
                } else if ("port".equals(keyLower)) {
                    try {
                        int p = Integer.parseInt(valStr);
                        if (p < 1 || p > 65535) {
                            throw error(keyToken, "Port number must be between 1 and 65535, found: " + valStr);
                        }
                    } catch (NumberFormatException nfe) {
                        throw error(keyToken, "Port must be an integer, found: '" + valStr + "'");
                    }
                } else if ("storage".equals(keyLower)) {
                    String s = valStr.toLowerCase();
                    if (!s.equals("minio") && !s.equals("s3") && !s.equals("none")) {
                        throw error(keyToken,
                                "Invalid storage provider '" + valStr + "'. Supported options are: minio, s3.");
                    }
                } else if ("swagger".equals(keyLower) || "flyway".equals(keyLower)) {
                    String b = valStr.toLowerCase();
                    if (!b.equals("true") && !b.equals("false")) {
                        throw error(keyToken,
                                "Property '" + key + "' must be a boolean (true or false), found: '" + valStr + "'");
                    }
                } else if ("jwtexpiration".equals(keyLower)) {
                    long exp = AppConfig.parseDurationMs(valStr, -1L);
                    if (exp <= 0) {
                        throw error(keyToken, "jwtExpiration must be a positive duration (e.g. 24h, 30m, 7d, 60s) or milliseconds (e.g. 86400000), found: '" + valStr + "'");
                    }
                } else if ("jwtsecret".equals(keyLower)) {
                    if (valStr.isEmpty()) {
                        throw error(keyToken, "jwtSecret cannot be empty");
                    }
                } else if ("ratelimit".equals(keyLower)) {
                    try {
                        RateLimitSpec.parse(valStr);
                    } catch (IllegalArgumentException iae) {
                        throw error(keyToken, iae.getMessage());
                    }
                } else if ("mail".equals(keyLower) || "mailprovider".equals(keyLower)) {
                    String p = valStr.toLowerCase();
                    if (!p.equals("smtp") && !p.equals("sendgrid") && !p.equals("ses") && !p.equals("console")) {
                        throw error(keyToken, "Invalid mail provider '" + valStr + "'. Supported options: smtp, sendgrid, ses, console.");
                    }
                } else if ("cache".equals(keyLower) || "cacheprovider".equals(keyLower)) {
                    String c = valStr.toLowerCase();
                    if (!c.equals("redis") && !c.equals("caffeine") && !c.equals("memory") && !c.equals("none") && !c.equals("true") && !c.equals("false")) {
                        throw error(keyToken, "Invalid cache provider '" + valStr + "'. Supported options: redis, caffeine, memory, none.");
                    }
                }
            }

            props.put(key, val);

            // optional comma
            match(TokenType.COMMA);
        }

        consume(TokenType.RBRACE, "Expected '}' closing config block");
        return new AppConfig(props);
    }

    private EnumDef parseEnum() {
        consume(TokenType.ENUM, "Expected 'enum'");
        Token nameToken = consume(TokenType.IDENTIFIER, "Expected enum name");
        consume(TokenType.LBRACE, "Expected '{' after enum name");

        List<String> values = new ArrayList<>();
        while (!isAtEnd() && peek().getType() != TokenType.RBRACE) {
            Token valToken = consume(TokenType.IDENTIFIER, "Expected enum value identifier");
            values.add(valToken.getValue());

            match(TokenType.COMMA);
        }

        consume(TokenType.RBRACE, "Expected '}' closing enum block");
        EnumDef enumDef = new EnumDef(nameToken.getValue(), values);
        enumDef.setSourceFile(sourceFile);
        enumDef.setSourceLine(nameToken.getLine());
        return enumDef;
    }

    private ModelDef parseModel() {
        consume(TokenType.MODEL, "Expected 'model'");
        Token nameToken = consume(TokenType.IDENTIFIER, "Expected model name");
        consume(TokenType.LBRACE, "Expected '{' after model name");

        List<FieldDef> fields = new ArrayList<>();
        List<RelationDef> relations = new ArrayList<>();
        CrudConfig crudConfig = null;
        List<CustomEndpointDef> customEndpoints = new ArrayList<>();
        List<IndexDef> indexes = new ArrayList<>();

        while (!isAtEnd() && peek().getType() != TokenType.RBRACE) {
            Token cur = peek();
            if (cur.getType() == TokenType.AT) {
                indexes.add(parseModelIndexDeclaration());
            } else if (cur.getType() == TokenType.RELATIONS) {
                relations.addAll(parseRelationsBlock());
            } else if (cur.getType() == TokenType.ENDPOINTS) {
                CrudConfig insideCrud = parseEndpointsBlock(customEndpoints);
                if (insideCrud != null) {
                    crudConfig = insideCrud;
                }
            } else if (cur.getType() == TokenType.CRUD) {
                crudConfig = parseCrudBlock();
            } else if (cur.getType() == TokenType.IDENTIFIER || cur.getType() == TokenType.ACTION
                    || cur.getType() == TokenType.PUBLIC || cur.getType() == TokenType.AUTH
                    || cur.getType() == TokenType.GUARD || cur.getType() == TokenType.VAR
                    || cur.getType() == TokenType.BODY || cur.getType() == TokenType.CONFIG) {
                // Could be a field definition or inline relation
                FieldDef field = parseField();
                fields.add(field);
            } else {
                throw error(cur, "Unexpected token in model body: '" + cur.getValue() + "'");
            }
        }

        consume(TokenType.RBRACE, "Expected '}' closing model block");

        // Scan fields for any inline @relation annotations and move them to relations
        // list
        Iterator<FieldDef> iter = fields.iterator();
        while (iter.hasNext()) {
            FieldDef f = iter.next();
            if (f.hasAnnotation("relation")) {
                AnnotationDef ann = f.getAnnotation("relation");
                String relTypeStr = ann.getArgument("type") != null ? ann.getArgument("type").toString()
                        : (ann.getArguments().containsKey("0") ? ann.getArguments().get("0").toString() : "manyToOne");
                String mappedBy = ann.getArgument("mappedBy") != null ? ann.getArgument("mappedBy").toString() : null;

                String target = f.getType().isList() ? f.getType().getGenericType() : f.getType().getName();
                relations.add(new RelationDef(f.getName(), target, RelationType.fromString(relTypeStr), mappedBy,
                        f.isOptional()));
            }
        }

        ModelDef model = new ModelDef(nameToken.getValue(), fields, relations, crudConfig, customEndpoints, indexes);
        model.setSourceFile(sourceFile);
        model.setSourceLine(nameToken.getLine());
        return model;
    }

    /**
     * Parses a model-level composite index declaration:
     * {@code @index([fieldA, fieldB])}, {@code @index([a, b], name = "idx_custom")},
     * {@code @index(fields = [a, b], unique = true)} or {@code @uniqueIndex([a, b])}.
     */
    private IndexDef parseModelIndexDeclaration() {
        Token atTok = peek();
        Token nameTok = peekNext();
        String annName = nameTok.getValue();
        boolean isIndex = "index".equalsIgnoreCase(annName);
        boolean isUniqueIndex = "uniqueIndex".equalsIgnoreCase(annName);
        if (!isIndex && !isUniqueIndex) {
            throw error(nameTok, "Unexpected model-level annotation '@" + annName
                    + "'. Only '@index([...])' and '@uniqueIndex([...])' are allowed at model scope");
        }
        AnnotationDef ann = parseAnnotation();

        Object fieldsArg = ann.getArgument("fields");
        if (fieldsArg == null) fieldsArg = ann.getArgument("0");
        if (fieldsArg == null) {
            throw error(nameTok, "Model-level '@" + annName + "' requires a field list, e.g. '@" + annName + "([fieldA, fieldB])'");
        }
        List<String> fieldNames = new ArrayList<>();
        if (fieldsArg instanceof List) {
            for (Object o : (List<?>) fieldsArg) {
                fieldNames.add(String.valueOf(o));
            }
        } else {
            fieldNames.add(String.valueOf(fieldsArg));
        }
        if (fieldNames.isEmpty()) {
            throw error(nameTok, "'@" + annName + "' field list must not be empty");
        }

        Object nameArg = ann.getArgument("name");
        if (nameArg == null && ann.getArgument("1") instanceof String) {
            nameArg = ann.getArgument("1");
        }
        Object uniqueArg = ann.getArgument("unique");
        boolean unique = isUniqueIndex || Boolean.TRUE.equals(uniqueArg) || "true".equals(String.valueOf(uniqueArg));

        IndexDef idx = new IndexDef(nameArg != null ? nameArg.toString() : null, fieldNames, unique, false);
        idx.setSourceFile(sourceFile);
        idx.setSourceLine(atTok.getLine());
        return idx;
    }

    private FieldDef parseField() {
        Token nameTok = consumeFieldName();
        consume(TokenType.COLON, "Expected ':' after field name");

        TypeRef type = parseTypeRef();

        boolean optional = match(TokenType.QUESTION);

        List<AnnotationDef> annotations = new ArrayList<>();
        Object defaultValue = null;

        while (peek().getType() == TokenType.AT || peek().getType() == TokenType.EQUAL) {
            if (match(TokenType.EQUAL)) {
                defaultValue = parseLiteralOrIdentifier();
            } else if (peek().getType() == TokenType.AT) {
                if (isModelLevelIndexAhead(nameTok.getLine())) {
                    break;
                }
                annotations.add(parseAnnotation());
            }
        }

        match(TokenType.COMMA);

        return new FieldDef(nameTok.getValue(), type, optional, annotations, defaultValue);
    }

    /**
     * Returns true when the upcoming {@code @} token begins a model-level composite index
     * declaration (e.g. {@code @index([a, b])} or {@code @index(fields = [a, b])}) on a new line,
     * so that {@link #parseField()} does not greedily attach it to the preceding field.
     */
    private boolean isModelLevelIndexAhead(int fieldLine) {
        Token atTok = peek();
        if (atTok.getType() != TokenType.AT || atTok.getLine() <= fieldLine) {
            return false;
        }
        Token nameTok = peekNext();
        String annName = nameTok.getValue();
        if (!"index".equalsIgnoreCase(annName) && !"uniqueIndex".equalsIgnoreCase(annName)) {
            return false;
        }
        // Check if followed by '([' or '(fields'
        if (cursor + 2 < tokens.size() && tokens.get(cursor + 2).getType() == TokenType.LPAREN) {
            if (cursor + 3 < tokens.size()) {
                Token firstArg = tokens.get(cursor + 3);
                if (firstArg.getType() == TokenType.LBRACKET
                        || (firstArg.getType() == TokenType.IDENTIFIER && "fields".equalsIgnoreCase(firstArg.getValue()))) {
                    return true;
                }
            }
        }
        return false;
    }

    private TypeRef parseTypeRef() {
        Token typeNameTok = consume(TokenType.IDENTIFIER, "Expected type name");
        String name = typeNameTok.getValue();

        if ("List".equalsIgnoreCase(name) && match(TokenType.LT)) {
            Token genericTok = consume(TokenType.IDENTIFIER, "Expected generic type inside List<...>");
            consume(TokenType.GT, "Expected '>' after generic type");
            return TypeRef.list(genericTok.getValue());
        }

        Map<String, Object> params = new HashMap<>();

        if (match(TokenType.LPAREN)) {
            // Parameterized type, e.g. String(min = 2, max = 50) or Decimal(10, 2)
            int argIdx = 0;
            while (!isAtEnd() && peek().getType() != TokenType.RPAREN) {
                if (peek().getType() == TokenType.IDENTIFIER && peekNext().getType() == TokenType.EQUAL) {
                    Token key = advance();
                    advance(); // =
                    Object val = parseLiteralOrIdentifier();
                    params.put(key.getValue(), val);
                } else {
                    Object val = parseLiteralOrIdentifier();
                    params.put(String.valueOf(argIdx++), val);
                }
                match(TokenType.COMMA);
            }
            consume(TokenType.RPAREN, "Expected ')' after type arguments");
        }

        return TypeRef.simpleWithParams(name, params);
    }

    private AnnotationDef parseAnnotation() {
        Token atTok = consume(TokenType.AT, "Expected '@'");
        Token nameTok;
        if (peek().getType() == TokenType.IDENTIFIER || peek().getType() == TokenType.VAR
                || peek().getType() == TokenType.ACTION || peek().getType() == TokenType.AUTH
                || peek().getType() == TokenType.PUBLIC || peek().getType() == TokenType.GUARD
                || peek().getType() == TokenType.BODY || peek().getType() == TokenType.CONFIG) {
            nameTok = advance();
        } else {
            nameTok = consume(TokenType.IDENTIFIER, "Expected annotation name");
        }

        Map<String, Object> args = new HashMap<>();
        if (match(TokenType.LPAREN)) {
            int argIdx = 0;
            while (!isAtEnd() && peek().getType() != TokenType.RPAREN) {
                if ((peek().getType() == TokenType.IDENTIFIER || peek().getType() == TokenType.VAR)
                        && peekNext().getType() == TokenType.EQUAL) {
                    Token key = advance();
                    advance(); // =
                    Object val = parseLiteralOrIdentifier();
                    args.put(key.getValue(), val);
                } else {
                    Object val = parseLiteralOrIdentifier();
                    args.put(String.valueOf(argIdx++), val);
                }
                match(TokenType.COMMA);
            }
            consume(TokenType.RPAREN, "Expected ')' after annotation arguments");
        }

        return new AnnotationDef(nameTok.getValue(), args, atTok.getLine());
    }

    private List<RelationDef> parseRelationsBlock() {
        consume(TokenType.RELATIONS, "Expected 'relations'");
        consume(TokenType.LBRACE, "Expected '{' after 'relations'");

        List<RelationDef> rels = new ArrayList<>();
        while (!isAtEnd() && peek().getType() != TokenType.RBRACE) {
            Token fieldNameTok = consume(TokenType.IDENTIFIER, "Expected relation field name");
            consume(TokenType.COLON, "Expected ':' after relation field name");

            Token targetModelTok = consume(TokenType.IDENTIFIER, "Expected target model name");
            String target = targetModelTok.getValue();
            if ("List".equalsIgnoreCase(target) && match(TokenType.LT)) {
                Token gen = consume(TokenType.IDENTIFIER, "Expected model name in List<...>");
                consume(TokenType.GT, "Expected '>' after List<...>");
                target = gen.getValue();
            }
            boolean optional = match(TokenType.QUESTION);

            AnnotationDef ann = parseAnnotation();
            String relTypeStr = "manyToOne";
            if ("relation".equalsIgnoreCase(ann.getName())) {
                if (ann.getArguments().containsKey("0")) {
                    relTypeStr = ann.getArguments().get("0").toString();
                } else if (ann.getArgument("type") != null) {
                    relTypeStr = ann.getArgument("type").toString();
                }
            } else if ("manyToOne".equalsIgnoreCase(ann.getName())
                    || "oneToMany".equalsIgnoreCase(ann.getName())
                    || "oneToOne".equalsIgnoreCase(ann.getName())
                    || "manyToMany".equalsIgnoreCase(ann.getName())) {
                relTypeStr = ann.getName();
            } else {
                throw new ParseException("Unknown relation annotation '@" + ann.getName()
                        + "' on relation '" + fieldNameTok.getValue()
                        + "'. Expected '@relation(manyToOne)', '@relation(oneToMany)', '@relation(oneToOne)', or '@relation(manyToMany)'.",
                        ann.getSourceLine() > 0 ? ann.getSourceLine() : fieldNameTok.getLine(), 1, sourceFile);
            }

            String mappedBy = ann.getArgument("mappedBy") != null ? ann.getArgument("mappedBy").toString() : null;

            rels.add(new RelationDef(fieldNameTok.getValue(), target, RelationType.fromString(relTypeStr), mappedBy,
                    optional));

            match(TokenType.COMMA);
        }

        consume(TokenType.RBRACE, "Expected '}' closing relations block");
        return rels;
    }

    private CrudConfig parseEndpointsBlock(List<CustomEndpointDef> customEndpoints) {
        consume(TokenType.ENDPOINTS, "Expected 'endpoints'");
        consume(TokenType.LBRACE, "Expected '{' after 'endpoints'");

        CrudConfig crudConfig = null;
        while (!isAtEnd() && peek().getType() != TokenType.RBRACE) {
            Token cur = peek();
            RateLimitSpec endpointDecoratorLimit = null;
            if (cur.getType() == TokenType.AT && peekNext().getType() == TokenType.IDENTIFIER
                    && ("ratelimit".equalsIgnoreCase(peekNext().getValue()) || "rate_limit".equalsIgnoreCase(peekNext().getValue()))) {
                AnnotationDef ann = parseAnnotation();
                endpointDecoratorLimit = parseRateLimitAnnotation(ann);
                cur = peek();
            }

            if (cur.getType() == TokenType.CRUD) {
                crudConfig = parseCrudBlock();
                if (endpointDecoratorLimit != null && crudConfig != null && crudConfig.getRateLimit() == null) {
                    crudConfig.setRateLimit(endpointDecoratorLimit);
                }
            } else if (isHttpMethod(cur.getType())) {
                CustomEndpointDef cep = parseCustomEndpoint();
                if (endpointDecoratorLimit != null && cep.getRateLimit() == null) {
                    cep.setRateLimit(endpointDecoratorLimit);
                }
                customEndpoints.add(cep);
            } else {
                throw error(cur,
                        "Expected HTTP method ('get', 'post', 'put', 'delete', 'patch') or 'crud' in endpoints");
            }
        }

        consume(TokenType.RBRACE, "Expected '}' closing endpoints block");
        return crudConfig;
    }

    private CrudConfig parseCrudBlock() {
        Token crudTok = consume(TokenType.CRUD, "Expected 'crud'");
        boolean enabled = true;
        boolean paginate = true;
        List<String> search = new ArrayList<>();
        List<String> filter = new ArrayList<>();
        List<String> sort = new ArrayList<>();
        Boolean requiresAuth = null;
        List<String> defaultRoles = new ArrayList<>();
        Map<String, List<String>> operationRoles = new LinkedHashMap<>();
        RateLimitSpec rateLimit = null;
        String cacheDuration = null;

        if (match(TokenType.LBRACE)) {
            while (!isAtEnd() && peek().getType() != TokenType.RBRACE) {
                Token key;
                if (peek().getType() == TokenType.IDENTIFIER || peek().getType() == TokenType.AUTH
                        || peek().getType() == TokenType.PUBLIC || peek().getType() == TokenType.GUARD
                        || isHttpMethod(peek().getType())) {
                    key = advance();
                } else {
                    key = consume(TokenType.IDENTIFIER, "Expected crud property");
                }
                consume(TokenType.COLON, "Expected ':' after crud property");

                String k = key.getValue().toLowerCase();
                if ("enabled".equals(k)) {
                    enabled = parseBoolean();
                } else if ("paginate".equals(k)) {
                    paginate = parseBoolean();
                } else if ("auth".equals(k)) {
                    requiresAuth = parseBoolean();
                } else if ("public".equals(k)) {
                    requiresAuth = !parseBoolean();
                } else if ("search".equals(k) || "searchable".equals(k)) {
                    search = parseIdentifierList();
                } else if ("filter".equals(k) || "filterable".equals(k)) {
                    filter = parseIdentifierList();
                } else if ("sort".equals(k) || "sortable".equals(k)) {
                    sort = parseIdentifierList();
                } else if ("role".equals(k) || "roles".equals(k)) {
                    defaultRoles = parseRoleListOrSingle();
                } else if ("guard".equals(k)) {
                    int line = key.getLine();
                    StringBuilder expr = new StringBuilder();
                    while (!isAtEnd() && peek().getType() != TokenType.COMMA
                            && peek().getType() != TokenType.RBRACE
                            && peek().getLine() == line) {
                        expr.append(advance().getValue()).append(" ");
                    }
                    GuardDef g = new GuardDef(expr.toString().trim(), "");
                    if (g.isRoleGuard()) {
                        String role = g.extractRole();
                        if (role != null && !role.isEmpty()) {
                            defaultRoles.add(role);
                        }
                    }
                } else if ("getall".equals(k) || "list".equals(k) || "findall".equals(k)) {
                    operationRoles.put("getall", parseRoleListOrSingle());
                } else if ("getbyid".equals(k) || "get".equals(k) || "findbyid".equals(k)) {
                    operationRoles.put("getbyid", parseRoleListOrSingle());
                } else if ("create".equals(k) || "post".equals(k)) {
                    operationRoles.put("create", parseRoleListOrSingle());
                } else if ("update".equals(k) || "put".equals(k) || "patch".equals(k)) {
                    operationRoles.put("update", parseRoleListOrSingle());
                } else if ("delete".equals(k)) {
                    operationRoles.put("delete", parseRoleListOrSingle());
                } else if ("ratelimit".equals(k)) {
                    String specStr = parseRateLimitString(key);
                    try {
                        rateLimit = RateLimitSpec.parse(specStr);
                    } catch (IllegalArgumentException iae) {
                        throw error(key, iae.getMessage());
                    }
                } else if ("cache".equals(k)) {
                    cacheDuration = parseCacheDurationToken(key);
                } else {
                    parseLiteralOrIdentifier(); // skip unknown
                }

                match(TokenType.COMMA);
            }
            consume(TokenType.RBRACE, "Expected '}' closing crud block");
        }

        CrudConfig crud = new CrudConfig(enabled, paginate, search, filter, sort, requiresAuth, defaultRoles,
                operationRoles, rateLimit);
        crud.setCacheDuration(cacheDuration);
        crud.setSourceFile(sourceFile);
        crud.setSourceLine(crudTok.getLine());
        return crud;
    }

    private String parseRateLimitString(Token keyToken) {
        if (peek().getType() == TokenType.STRING_LITERAL) {
            return advance().getValue();
        }
        // Handle unquoted e.g. 10/minute or 5 / second
        int keyLine = keyToken.getLine();
        StringBuilder sb = new StringBuilder();
        while (!isAtEnd() && peek().getType() != TokenType.COMMA && peek().getType() != TokenType.RBRACE && peek().getLine() == keyLine) {
            sb.append(advance().getValue());
        }
        return sb.toString().trim();
    }

    private String parseCacheDurationToken(Token keyToken) {
        if (peek().getType() == TokenType.STRING_LITERAL) {
            String val = advance().getValue().trim();
            validateCacheDuration(keyToken, val);
            return val;
        } else if (peek().getType() == TokenType.TRUE) {
            advance();
            return "10m";
        } else if (peek().getType() == TokenType.FALSE) {
            advance();
            return "0s";
        }
        // Check if raw token like 15m, 1h, 30s
        int keyLine = keyToken.getLine();
        StringBuilder sb = new StringBuilder();
        while (!isAtEnd() && peek().getType() != TokenType.COMMA && peek().getType() != TokenType.RBRACE && peek().getLine() == keyLine) {
            sb.append(advance().getValue());
        }
        String val = sb.toString().trim();
        validateCacheDuration(keyToken, val);
        return val;
    }

    public static void validateCacheDuration(Token token, String durationStr) {
        if (durationStr == null || durationStr.isEmpty()) return;
        String s = durationStr.trim().toLowerCase();
        if ("true".equals(s) || "false".equals(s)) return;
        if (!s.matches("^\\d+(s|m|h|d|ms)$")) {
            throw new ParseException("Invalid cache duration format: '" + durationStr + "'. Expected format e.g. '15m', '1h', '300s', '30s'.",
                    token.getLine(), token.getColumn());
        }
    }

    public static java.time.Duration parseCacheDuration(String durationStr) {
        if (durationStr == null || durationStr.trim().isEmpty() || "false".equalsIgnoreCase(durationStr) || "0s".equalsIgnoreCase(durationStr)) {
            return java.time.Duration.ZERO;
        }
        String s = durationStr.trim().toLowerCase();
        if ("true".equals(s)) return java.time.Duration.ofMinutes(10);
        if (s.endsWith("ms")) {
            long ms = Long.parseLong(s.substring(0, s.length() - 2));
            return java.time.Duration.ofMillis(ms);
        } else if (s.endsWith("s")) {
            long sec = Long.parseLong(s.substring(0, s.length() - 1));
            return java.time.Duration.ofSeconds(sec);
        } else if (s.endsWith("m")) {
            long min = Long.parseLong(s.substring(0, s.length() - 1));
            return java.time.Duration.ofMinutes(min);
        } else if (s.endsWith("h")) {
            long hrs = Long.parseLong(s.substring(0, s.length() - 1));
            return java.time.Duration.ofHours(hrs);
        } else if (s.endsWith("d")) {
            long days = Long.parseLong(s.substring(0, s.length() - 1));
            return java.time.Duration.ofDays(days);
        }
        long sec = Long.parseLong(s);
        return java.time.Duration.ofSeconds(sec);
    }

    private RateLimitSpec parseRateLimitAnnotation(AnnotationDef ann) {
        // Formats:
        // @rateLimit("10/minute") or @rateLimit(requests = 10, per = "1m") or @rateLimit(10, "1m")
        Map<String, Object> args = ann.getArguments();
        if (args.isEmpty()) {
            throw new ParseException("@rateLimit decorator requires arguments, e.g. @rateLimit(\"10/minute\") or @rateLimit(10, \"1m\")",
                    ann.getSourceLine(), 1, sourceFile);
        }
        if (args.containsKey("0") && args.get("0") instanceof String && ((String) args.get("0")).contains("/")) {
            return RateLimitSpec.parse(args.get("0").toString());
        }
        Object reqObj = args.get("requests") != null ? args.get("requests")
                : (args.get("capacity") != null ? args.get("capacity") : args.get("0"));
        Object perObj = args.get("per") != null ? args.get("per")
                : (args.get("duration") != null ? args.get("duration") : args.get("1"));
        if (reqObj != null && perObj != null) {
            int cap;
            try {
                cap = Integer.parseInt(reqObj.toString().trim());
            } catch (NumberFormatException nfe) {
                throw new ParseException("Rate limit requests/capacity must be an integer, found: '" + reqObj + "'",
                        ann.getSourceLine(), 1, sourceFile);
            }
            if (cap <= 0) {
                throw new ParseException("Rate limit requests/capacity must be greater than zero, found: " + cap,
                        ann.getSourceLine(), 1, sourceFile);
            }
            String pStr = perObj.toString().trim().replace("\"", "").replace("'", "");
            return RateLimitSpec.of(cap, pStr);
        }
        if (args.containsKey("0")) {
            return RateLimitSpec.parse(args.get("0").toString());
        }
        throw new ParseException("Invalid @rateLimit declaration. Expected format: @rateLimit(\"10/minute\") or @rateLimit(requests = 10, per = \"1m\")",
                ann.getSourceLine(), 1, sourceFile);
    }

    private List<String> parseRoleListOrSingle() {
        List<String> list = new ArrayList<>();
        if (match(TokenType.LBRACKET)) {
            while (!isAtEnd() && peek().getType() != TokenType.RBRACKET) {
                String val = parseRoleToken();
                if (val != null && !val.isEmpty()) {
                    list.add(val);
                }
                match(TokenType.COMMA);
            }
            consume(TokenType.RBRACKET, "Expected ']'");
        } else {
            String val = parseRoleToken();
            if (val != null && !val.isEmpty()) {
                list.add(val);
            }
        }
        return list;
    }

    private String parseRoleToken() {
        Token t = advance();
        String val = t.getValue();
        if (match(TokenType.DOT)) {
            Token sub = advance();
            val = sub.getValue();
        }
        return val.replace("\"", "").replace("'", "").trim();
    }

    private CustomEndpointDef parseCustomEndpoint() {
        Token methodTok = advance();
        String path = "/";
        if (peek().getType() == TokenType.STRING_LITERAL) {
            path = advance().getValue();
        } else if (peek().getType() == TokenType.SLASH) {
            // raw path like /{id}/publish
            StringBuilder sb = new StringBuilder();
            while (!isAtEnd() && peek().getType() != TokenType.ARROW && peek().getType() != TokenType.LBRACE) {
                sb.append(advance().getValue());
            }
            path = sb.toString();
        }
        String bodyType = "";

        if (match(TokenType.LPAREN)) {
            consume(TokenType.BODY, "Expected 'body'");
            consume(TokenType.COLON, "Expected ':' after body");

            bodyType = consume(TokenType.IDENTIFIER, "Expected body type").getValue();

            consume(TokenType.RPAREN, "Expected ')'");
        }

        String returnType = "Void";
        if (match(TokenType.ARROW)) {
            Token retTok = consume(TokenType.IDENTIFIER, "Expected return type after '->'");
            StringBuilder retSb = new StringBuilder(retTok.getValue());
            if (match(TokenType.LT)) {
                retSb.append("<");
                int depth = 1;
                while (!isAtEnd() && depth > 0) {
                    Token cur = advance();
                    if (cur.getType() == TokenType.LT) {
                        depth++;
                        retSb.append("<");
                    } else if (cur.getType() == TokenType.GT) {
                        depth--;
                        retSb.append(">");
                    } else {
                        retSb.append(cur.getValue());
                    }
                }
                returnType = retSb.toString();
            } else {
                returnType = retTok.getValue();
            }
        }

        String description = "";
        Boolean requiresAuth = null;
        Boolean userScoped = null;
        String query = null;
        RateLimitSpec rateLimit = null;
        String cacheDuration = null;
        Map<String, String> paramTypes = new LinkedHashMap<>();
        List<GuardDef> guards = new ArrayList<>();
        List<String> actions = new ArrayList<>();
        List<String> roles = new ArrayList<>();

        if (match(TokenType.LBRACE)) {
            while (!isAtEnd() && peek().getType() != TokenType.RBRACE) {
                Token key = peek();
                if (key.getType() == TokenType.GUARD) {
                    advance(); // guard
                    consume(TokenType.COLON, "Expected ':' after 'guard'");
                    int guardLine = key.getLine();
                    StringBuilder expr = new StringBuilder();
                    int bracketDepth = 0;
                    int parenDepth = 0;
                    while (!isAtEnd() && peek().getType() != TokenType.RBRACE
                            && peek().getType() != TokenType.GUARD
                            && peek().getType() != TokenType.ACTION
                            && peek().getLine() == guardLine) {
                        if (peek().getType() == TokenType.COMMA && bracketDepth == 0 && parenDepth == 0) {
                            break;
                        }
                        Token t = advance();
                        if (t.getType() == TokenType.LBRACKET)
                            bracketDepth++;
                        else if (t.getType() == TokenType.RBRACKET)
                            bracketDepth--;
                        else if (t.getType() == TokenType.LPAREN)
                            parenDepth++;
                        else if (t.getType() == TokenType.RPAREN)
                            parenDepth--;
                        expr.append(t.getValue()).append(" ");
                    }
                    String errMessage = "Guard condition failed";
                    if (match(TokenType.COMMA)) {
                        if (peek().getType() == TokenType.STRING_LITERAL) {
                            errMessage = advance().getValue();
                        }
                    }
                    guards.add(new GuardDef(expr.toString().trim(), errMessage));
                } else if (key.getType() == TokenType.ACTION) {
                    advance(); // action
                    match(TokenType.COLON); // ':' is optional: 'action {' or 'action: {'
                    consume(TokenType.LBRACE, "Expected '{' to start action block");
                    while (!isAtEnd() && peek().getType() != TokenType.RBRACE) {
                        String a = parseActionStatement();
                        if (!a.isEmpty()) {
                            actions.add(a);
                        }
                        match(TokenType.COMMA);
                    }
                    consume(TokenType.RBRACE, "Expected '}' closing action block");
                } else if (key.getType() == TokenType.CREATE) {
                    actions.add(parseCreateAction());
                } else if (key.getType() == TokenType.UPDATE) {
                    actions.add(parseUpdateAction());
                } else if (key.getType() == TokenType.IDENTIFIER || key.getType() == TokenType.AUTH
                        || key.getType() == TokenType.PUBLIC) {
                    Token idTok = advance();
                    consume(TokenType.COLON, "Expected ':' after '" + idTok.getValue() + "'");
                    String k = idTok.getValue().toLowerCase();
                    if ("description".equalsIgnoreCase(k)) {
                        description = consume(TokenType.STRING_LITERAL, "Expected string for description").getValue();
                    } else if ("auth".equalsIgnoreCase(k)) {
                        if (peek().getType() == TokenType.LBRACKET) {
                            List<String> rList = parseRoleListOrSingle();
                            roles.addAll(rList);
                            String joined = String.join(", ", rList);
                            guards.add(new GuardDef("role in [" + joined + "]",
                                    "Access denied: required role in [" + joined + "]"));
                            requiresAuth = true;
                        } else {
                            requiresAuth = parseBoolean();
                        }
                    } else if ("public".equalsIgnoreCase(k)) {
                        requiresAuth = !parseBoolean();
                    } else if ("role".equalsIgnoreCase(k) || "roles".equalsIgnoreCase(k)) {
                        List<String> rList = parseRoleListOrSingle();
                        for (String rItem : rList) {
                            if ("public".equalsIgnoreCase(rItem)) {
                                throw error(idTok, "Invalid role 'public' in endpoint 'roles'. For unauthenticated public access, use 'auth: false'.");
                            }
                            if ("authenticated".equalsIgnoreCase(rItem) || "auth".equalsIgnoreCase(rItem)) {
                                throw error(idTok, "Invalid role '" + rItem + "' in endpoint 'roles'. To require an authenticated user, use 'auth: true'.");
                            }
                        }
                        roles.addAll(rList);
                        String joined = String.join(", ", rList);
                        guards.add(new GuardDef("role in [" + joined + "]",
                                "Access denied: required role in [" + joined + "]"));
                        requiresAuth = true;
                    } else if ("user".equalsIgnoreCase(k) || "currentuser".equalsIgnoreCase(k)
                            || "me".equalsIgnoreCase(k)) {
                        userScoped = parseBoolean();
                    } else if ("params".equalsIgnoreCase(k) || "param".equalsIgnoreCase(k)
                            || "types".equalsIgnoreCase(k)) {
                        if (match(TokenType.LBRACE)) {
                            while (!isAtEnd() && peek().getType() != TokenType.RBRACE) {
                                Token pName = advance();
                                if (match(TokenType.COLON)) {
                                    Token pType = advance();
                                    paramTypes.put(pName.getValue(), pType.getValue());
                                }
                                match(TokenType.COMMA);
                            }
                            consume(TokenType.RBRACE, "Expected '}' closing params block");
                        } else {
                            Token pName = advance();
                            if (match(TokenType.COLON)) {
                                Token pType = advance();
                                paramTypes.put(pName.getValue(), pType.getValue());
                            }
                        }
                    } else if ("query".equalsIgnoreCase(k) || "where".equalsIgnoreCase(k)
                            || "find".equalsIgnoreCase(k)) {
                        int currentLine = idTok.getLine();
                        StringBuilder qSb = new StringBuilder();
                        while (!isAtEnd() && peek().getType() != TokenType.RBRACE
                                && peek().getType() != TokenType.COMMA
                                && peek().getType() != TokenType.GUARD
                                && peek().getType() != TokenType.ACTION
                                && peek().getLine() == currentLine) {
                            qSb.append(advance().getValue()).append(" ");
                        }
                        query = qSb.toString().trim();
                    } else if ("ratelimit".equalsIgnoreCase(k)) {
                        String specStr = parseRateLimitString(idTok);
                        try {
                            rateLimit = RateLimitSpec.parse(specStr);
                        } catch (IllegalArgumentException iae) {
                            throw error(idTok, iae.getMessage());
                        }
                    } else if ("cache".equalsIgnoreCase(k)) {
                        cacheDuration = parseCacheDurationToken(idTok);
                    } else {
                        parseLiteralOrIdentifier();
                    }
                } else {
                    advance();
                }
                match(TokenType.COMMA);
            }
            consume(TokenType.RBRACE, "Expected '}' closing custom endpoint block");
        }

        CustomEndpointDef cep = new CustomEndpointDef(methodTok.getValue(), path, returnType, description, guards,
                actions, requiresAuth,
                userScoped, query, paramTypes, bodyType, rateLimit, roles);
        cep.setCacheDuration(cacheDuration);
        cep.setSourceFile(sourceFile);
        cep.setSourceLine(methodTok.getLine());
        return cep;
    }

    private String parseActionStatement() {
        if (peek().getType() == TokenType.VAR) {
            advance(); // consume 'var'
            Token varName = consumeFieldName();
            consume(TokenType.EQUAL, "Expected '=' after variable identifier");
            String rightExpr;
            if (peek().getType() == TokenType.CREATE) {
                rightExpr = parseCreateAction();
            } else if (peek().getType() == TokenType.UPDATE) {
                rightExpr = parseUpdateAction();
            } else {
                throw error(peek(), "Expected 'create' or 'update' expression after 'var " + varName.getValue() + " ='");
            }
            return "var " + varName.getValue() + " = " + rightExpr;
        } else if (peek().getType() == TokenType.CREATE) {
            return parseCreateAction();
        } else if (peek().getType() == TokenType.UPDATE) {
            return parseUpdateAction();
        } else {
            StringBuilder stmt = new StringBuilder();
            int parenDepth = 0;
            int braceDepth = 0;
            Token lastToken = null;
            while (!isAtEnd()) {
                Token p = peek();
                if (parenDepth == 0 && braceDepth == 0) {
                    if (p.getType() == TokenType.RBRACE || p.getType() == TokenType.COMMA) {
                        break;
                    }
                    if (stmt.length() > 0 && (p.getType() == TokenType.CREATE || p.getType() == TokenType.UPDATE || p.getType() == TokenType.VAR)) {
                        break;
                    }
                    if (stmt.length() > 0 && lastToken != null && p.getLine() > lastToken.getLine()
                            && !isContinuationToken(lastToken) && !isBinaryOperator(p)) {
                        break;
                    }
                }
                Token t = advance();
                lastToken = t;
                if (t.getType() == TokenType.LPAREN)
                    parenDepth++;
                else if (t.getType() == TokenType.RPAREN)
                    parenDepth--;
                else if (t.getType() == TokenType.LBRACE)
                    braceDepth++;
                else if (t.getType() == TokenType.RBRACE)
                    braceDepth--;

                if (t.getType() == TokenType.STRING_LITERAL) {
                    stmt.append("\"").append(t.getValue().replace("\"", "\\\"")).append("\" ");
                } else {
                    stmt.append(t.getValue()).append(" ");
                }
            }
            return stmt.toString().trim();
        }
    }

    private boolean isContinuationToken(Token t) {
        if (t == null)
            return false;
        TokenType type = t.getType();
        return type == TokenType.PLUS || type == TokenType.MINUS || type == TokenType.STAR
                || type == TokenType.EQUAL || type == TokenType.COMMA || type == TokenType.DOT
                || type == TokenType.COLON || type == TokenType.LPAREN || type == TokenType.LBRACE
                || type == TokenType.LBRACKET;
    }

    private boolean isBinaryOperator(Token t) {
        if (t == null)
            return false;
        TokenType type = t.getType();
        return type == TokenType.PLUS || type == TokenType.MINUS || type == TokenType.STAR
                || type == TokenType.DOT || type == TokenType.EQUAL;
    }

    private String parseCreateAction() {
        consume(TokenType.CREATE, "Expected 'create'");
        Token modelTok = consume(TokenType.IDENTIFIER, "Expected model name after 'create'");
        consume(TokenType.LBRACE, "Expected '{' after model name");
        StringBuilder sb = new StringBuilder();
        sb.append("create ").append(modelTok.getValue()).append(" { ");
        boolean first = true;
        while (!isAtEnd() && peek().getType() != TokenType.RBRACE) {
            if (!first)
                sb.append(", ");
            Token fieldTok = consumeFieldName();
            if (match(TokenType.EQUAL) || match(TokenType.COLON)) {
                StringBuilder expr = new StringBuilder();
                int parenDepth = 0;
                int braceDepth = 0;
                Token lastToken = null;
                while (!isAtEnd()) {
                    Token p = peek();
                    if (parenDepth == 0 && braceDepth == 0) {
                        if (p.getType() == TokenType.COMMA || p.getType() == TokenType.RBRACE) {
                            break;
                        }
                        if (expr.length() > 0 && lastToken != null && p.getLine() > lastToken.getLine()
                                && !isContinuationToken(lastToken) && !isBinaryOperator(p)) {
                            break;
                        }
                    }
                    Token t = advance();
                    lastToken = t;
                    if (t.getType() == TokenType.LPAREN)
                        parenDepth++;
                    else if (t.getType() == TokenType.RPAREN)
                        parenDepth--;
                    else if (t.getType() == TokenType.LBRACE)
                        braceDepth++;
                    else if (t.getType() == TokenType.RBRACE)
                        braceDepth--;

                    if (t.getType() == TokenType.STRING_LITERAL) {
                        expr.append("\"").append(t.getValue().replace("\"", "\\\"")).append("\" ");
                    } else {
                        expr.append(t.getValue()).append(" ");
                    }
                }
                sb.append(fieldTok.getValue()).append(" = ").append(expr.toString().trim());
            } else {
                sb.append(fieldTok.getValue());
            }
            first = false;
            match(TokenType.COMMA);
        }
        consume(TokenType.RBRACE, "Expected '}' closing create block");
        sb.append(" }");
        return sb.toString();
    }

    private String parseUpdateAction() {
        consume(TokenType.UPDATE, "Expected 'update'");
        Token modelTok = consume(TokenType.IDENTIFIER, "Expected model name after 'update'");
        String onCollection = null;
        if (peek().getType() == TokenType.IDENTIFIER && "on".equalsIgnoreCase(peek().getValue())) {
            advance(); // on
            if (peek().getType() == TokenType.STRING_LITERAL || peek().getType() == TokenType.IDENTIFIER) {
                onCollection = advance().getValue();
            }
        }
        consume(TokenType.LBRACE, "Expected '{' after update target");
        StringBuilder sb = new StringBuilder();
        sb.append("update ").append(modelTok.getValue());
        if (onCollection != null) {
            sb.append(" on \"").append(onCollection).append("\"");
        }
        sb.append(" { ");
        boolean first = true;
        while (!isAtEnd() && peek().getType() != TokenType.RBRACE) {
            if (!first)
                sb.append(", ");
            StringBuilder item = new StringBuilder();
            int braceDepth = 0;
            int parenDepth = 0;
            Token lastToken = null;
            while (!isAtEnd()) {
                Token p = peek();
                if (braceDepth == 0 && parenDepth == 0) {
                    if (p.getType() == TokenType.COMMA || p.getType() == TokenType.RBRACE) {
                        break;
                    }
                    if (item.length() > 0 && lastToken != null && p.getLine() > lastToken.getLine()
                            && !isContinuationToken(lastToken) && !isBinaryOperator(p)) {
                        break;
                    }
                }
                Token t = advance();
                lastToken = t;
                if (t.getType() == TokenType.LBRACE)
                    braceDepth++;
                else if (t.getType() == TokenType.RBRACE)
                    braceDepth--;
                else if (t.getType() == TokenType.LPAREN)
                    parenDepth++;
                else if (t.getType() == TokenType.RPAREN)
                    parenDepth--;

                if (t.getType() == TokenType.STRING_LITERAL) {
                    item.append("\"").append(t.getValue().replace("\"", "\\\"")).append("\" ");
                } else {
                    item.append(t.getValue()).append(" ");
                }
            }
            sb.append(item.toString().trim());
            first = false;
            match(TokenType.COMMA);
        }
        consume(TokenType.RBRACE, "Expected '}' closing update block");
        sb.append(" }");
        return sb.toString();
    }

    private List<String> parseIdentifierList() {
        List<String> list = new ArrayList<>();
        consume(TokenType.LBRACKET, "Expected '['");
        while (!isAtEnd() && peek().getType() != TokenType.RBRACKET) {
            Token t = advance();
            if (t.getValue() == null || t.getValue().trim().isEmpty()) {
                throw error(t, "Expected identifier in list");
            }
            list.add(t.getValue());
            match(TokenType.COMMA);
        }
        consume(TokenType.RBRACKET, "Expected ']'");
        return list;
    }

    private boolean parseBoolean() {
        if (match(TokenType.TRUE))
            return true;
        if (match(TokenType.FALSE))
            return false;
        Token tok = advance();
        return Boolean.parseBoolean(tok.getValue());
    }

    private Object parseLiteralOrIdentifier() {
        Token tok = peek();
        if (tok.getType() == TokenType.STRING_LITERAL) {
            advance();
            return tok.getValue();
        } else if (tok.getType() == TokenType.NUMBER_LITERAL) {
            advance();
            try {
                if (tok.getValue().contains(".")) {
                    return Double.parseDouble(tok.getValue());
                } else {
                    return Long.parseLong(tok.getValue());
                }
            } catch (NumberFormatException e) {
                return tok.getValue();
            }
        } else if (tok.getType() == TokenType.TRUE) {
            advance();
            return true;
        } else if (tok.getType() == TokenType.FALSE) {
            advance();
            return false;
        } else if (tok.getType() == TokenType.IDENTIFIER || tok.getType() == TokenType.AUTH
                || tok.getType() == TokenType.PUBLIC || tok.getType() == TokenType.CRUD
                || tok.getType() == TokenType.MAIL || tok.getType() == TokenType.SEND) {
            advance();
            return tok.getValue();
        } else if (tok.getType() == TokenType.LBRACKET) {
            advance();
            List<Object> list = new ArrayList<>();
            while (!isAtEnd() && peek().getType() != TokenType.RBRACKET) {
                list.add(parseLiteralOrIdentifier());
                match(TokenType.COMMA);
            }
            consume(TokenType.RBRACKET, "Expected ']' closing list literal");
            return list;
        } else {
            throw error(tok, "Expected literal value or identifier, found " + tok.getType());
        }
    }

    private Token consumeFieldName() {
        Token t = peek();
        if (t.getType() == TokenType.IDENTIFIER || t.getType() == TokenType.ACTION
                || t.getType() == TokenType.AUTH || t.getType() == TokenType.PUBLIC
                || t.getType() == TokenType.CONFIG || t.getType() == TokenType.GUARD
                || t.getType() == TokenType.VAR || t.getType() == TokenType.BODY) {
            return advance();
        }
        throw error(t, "Expected field name, but got '" + t.getValue() + "' (" + t.getType() + ")");
    }

    private Token consumeKeyName() {
        Token t = peek();
        if (t.getType() == TokenType.IDENTIFIER || t.getType() == TokenType.AUTH || t.getType() == TokenType.PUBLIC
                || t.getType() == TokenType.CRUD || t.getType() == TokenType.MAIL || t.getType() == TokenType.SEND) {
            return advance();
        }
        throw error(t, "Expected key name, but got '" + t.getValue() + "' (" + t.getType() + ")");
    }

    private boolean isHttpMethod(TokenType type) {
        return type == TokenType.GET || type == TokenType.POST ||
                type == TokenType.PUT || type == TokenType.DELETE ||
                type == TokenType.PATCH;
    }

    private Token peek() {
        if (cursor < tokens.size()) {
            return tokens.get(cursor);
        }
        return tokens.get(tokens.size() - 1);
    }

    private Token peekNext() {
        if (cursor + 1 < tokens.size()) {
            return tokens.get(cursor + 1);
        }
        return tokens.get(tokens.size() - 1);
    }

    private Token advance() {
        if (!isAtEnd())
            cursor++;
        return tokens.get(cursor - 1);
    }

    private boolean isAtEnd() {
        return cursor >= tokens.size() || peek().getType() == TokenType.EOF;
    }

    private boolean match(TokenType type) {
        if (peek().getType() == type) {
            advance();
            return true;
        }
        return false;
    }

    private Token consume(TokenType expected, String errorMsg) {
        if (peek().getType() == expected) {
            return advance();
        }
        throw error(peek(), errorMsg + ", but got '" + peek().getValue() + "' (" + peek().getType() + ")");
    }

    private ParseException error(Token token, String message) {
        return new ParseException(message, token.getLine(), token.getColumn(), sourceFile);
    }
}
