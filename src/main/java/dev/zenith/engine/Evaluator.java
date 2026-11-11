package dev.zenith.engine;

import dev.zenith.engine.value.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Evaluator {

    public static ZenithValue evaluate(String expr, Scope scope) {
        if (expr == null) return ZenithNullValue.INSTANCE;
        expr = expr.trim();
        if (expr.isEmpty()) return ZenithNullValue.INSTANCE;

        // Strip outer parenthesis if fully balanced
        if (expr.startsWith("(") && expr.endsWith(")") && isBalancedParens(expr.substring(1, expr.length() - 1))) {
            return evaluate(expr.substring(1, expr.length() - 1), scope);
        }

        // Logical OR: '||'
        int orIdx = findTopLevelOp(expr, "||");
        if (orIdx != -1) {
            ZenithValue left = evaluate(expr.substring(0, orIdx), scope);
            if (left.isTruthy()) return ZenithBooleanValue.TRUE;
            ZenithValue right = evaluate(expr.substring(orIdx + 2), scope);
            return ZenithBooleanValue.of(right.isTruthy());
        }

        // Logical AND: '&&'
        int andIdx = findTopLevelOp(expr, "&&");
        if (andIdx != -1) {
            ZenithValue left = evaluate(expr.substring(0, andIdx), scope);
            if (!left.isTruthy()) return ZenithBooleanValue.FALSE;
            ZenithValue right = evaluate(expr.substring(andIdx + 2), scope);
            return ZenithBooleanValue.of(right.isTruthy());
        }

        // Logical NOT: '!'
        if (expr.startsWith("!") && !expr.startsWith("!=")) {
            ZenithValue val = evaluate(expr.substring(1), scope);
            return ZenithBooleanValue.of(!val.isTruthy());
        }

        // Comparison: '==', '!=', '>=', '<=', '>', '<'
        int eqIdx = findTopLevelOp(expr, "==");
        if (eqIdx != -1) {
            ZenithValue left = evaluate(expr.substring(0, eqIdx), scope);
            ZenithValue right = evaluate(expr.substring(eqIdx + 2), scope);
            return ZenithBooleanValue.of(areValuesEqual(left, right));
        }

        int neqIdx = findTopLevelOp(expr, "!=");
        if (neqIdx != -1) {
            ZenithValue left = evaluate(expr.substring(0, neqIdx), scope);
            ZenithValue right = evaluate(expr.substring(neqIdx + 2), scope);
            return ZenithBooleanValue.of(!areValuesEqual(left, right));
        }

        int gteIdx = findTopLevelOp(expr, ">=");
        if (gteIdx != -1) {
            int cmp = compareValues(evaluate(expr.substring(0, gteIdx), scope), evaluate(expr.substring(gteIdx + 2), scope));
            return ZenithBooleanValue.of(cmp >= 0);
        }

        int lteIdx = findTopLevelOp(expr, "<=");
        if (lteIdx != -1) {
            int cmp = compareValues(evaluate(expr.substring(0, lteIdx), scope), evaluate(expr.substring(lteIdx + 2), scope));
            return ZenithBooleanValue.of(cmp <= 0);
        }

        int gtIdx = findTopLevelOp(expr, ">");
        if (gtIdx != -1) {
            int cmp = compareValues(evaluate(expr.substring(0, gtIdx), scope), evaluate(expr.substring(gtIdx + 1), scope));
            return ZenithBooleanValue.of(cmp > 0);
        }

        int ltIdx = findTopLevelOp(expr, "<");
        if (ltIdx != -1) {
            int cmp = compareValues(evaluate(expr.substring(0, ltIdx), scope), evaluate(expr.substring(ltIdx + 1), scope));
            return ZenithBooleanValue.of(cmp < 0);
        }

        // 'in' operator e.g. "role in [ADMIN, MANAGER]"
        int inIdx = findTopLevelWordOp(expr, "in");
        if (inIdx != -1) {
            ZenithValue left = evaluate(expr.substring(0, inIdx), scope);
            ZenithValue right = evaluate(expr.substring(inIdx + 2), scope);
            if (right instanceof ZenithListValue) {
                boolean contains = false;
                for (ZenithValue item : ((ZenithListValue) right).getElements()) {
                    if (areValuesEqual(left, item)) {
                        contains = true;
                        break;
                    }
                }
                return ZenithBooleanValue.of(contains);
            }
            return ZenithBooleanValue.FALSE;
        }

        // Additive: '+' and '-' (only when '-' is binary not unary)
        int addIdx = findTopLevelOp(expr, "+");
        if (addIdx != -1) {
            ZenithValue left = evaluate(expr.substring(0, addIdx), scope);
            ZenithValue right = evaluate(expr.substring(addIdx + 1), scope);
            if (left instanceof ZenithStringValue || right instanceof ZenithStringValue) {
                return new ZenithStringValue(left.asString() + right.asString());
            }
            if (left instanceof ZenithNumberValue && right instanceof ZenithNumberValue) {
                BigDecimal res = ((ZenithNumberValue) left).getDecimalValue().add(((ZenithNumberValue) right).getDecimalValue());
                return new ZenithNumberValue(res);
            }
            return new ZenithStringValue(left.asString() + right.asString());
        }

        int subIdx = findTopLevelBinaryMinus(expr);
        if (subIdx != -1) {
            ZenithValue left = evaluate(expr.substring(0, subIdx), scope);
            ZenithValue right = evaluate(expr.substring(subIdx + 1), scope);
            if (left instanceof ZenithNumberValue && right instanceof ZenithNumberValue) {
                BigDecimal res = ((ZenithNumberValue) left).getDecimalValue().subtract(((ZenithNumberValue) right).getDecimalValue());
                return new ZenithNumberValue(res);
            }
            return new ZenithNumberValue(0);
        }

        // Multiplicative: '*', '/', '%'
        int mulIdx = findTopLevelOp(expr, "*");
        if (mulIdx != -1) {
            ZenithValue left = evaluate(expr.substring(0, mulIdx), scope);
            ZenithValue right = evaluate(expr.substring(mulIdx + 1), scope);
            if (left instanceof ZenithNumberValue && right instanceof ZenithNumberValue) {
                BigDecimal res = ((ZenithNumberValue) left).getDecimalValue().multiply(((ZenithNumberValue) right).getDecimalValue());
                return new ZenithNumberValue(res);
            }
            return new ZenithNumberValue(0);
        }

        int divIdx = findTopLevelOp(expr, "/");
        if (divIdx != -1) {
            ZenithValue left = evaluate(expr.substring(0, divIdx), scope);
            ZenithValue right = evaluate(expr.substring(divIdx + 1), scope);
            if (left instanceof ZenithNumberValue && right instanceof ZenithNumberValue) {
                BigDecimal rVal = ((ZenithNumberValue) right).getDecimalValue();
                if (rVal.compareTo(BigDecimal.ZERO) == 0) {
                    throw new RuntimeException("Division by zero in simulation");
                }
                BigDecimal res = ((ZenithNumberValue) left).getDecimalValue().divide(rVal, 4, RoundingMode.HALF_UP);
                return new ZenithNumberValue(res);
            }
            return new ZenithNumberValue(0);
        }

        // Literals and atomic references
        return evaluatePrimary(expr, scope);
    }

    private static ZenithValue evaluatePrimary(String expr, Scope scope) {
        expr = expr.trim();
        if (expr.isEmpty()) return ZenithNullValue.INSTANCE;

        // String literals: "..." or '...'
        if ((expr.startsWith("\"") && expr.endsWith("\"") && expr.length() >= 2) ||
            (expr.startsWith("'") && expr.endsWith("'") && expr.length() >= 2)) {
            return new ZenithStringValue(expr.substring(1, expr.length() - 1));
        }

        // Boolean literals
        if ("true".equalsIgnoreCase(expr)) return ZenithBooleanValue.TRUE;
        if ("false".equalsIgnoreCase(expr)) return ZenithBooleanValue.FALSE;
        if ("null".equalsIgnoreCase(expr)) return ZenithNullValue.INSTANCE;

        // Number literals
        if (expr.matches("^-?\\d+(\\.\\d+)?$")) {
            return new ZenithNumberValue(new BigDecimal(expr));
        }

        // List literal: [...]
        if (expr.startsWith("[") && expr.endsWith("]")) {
            String content = expr.substring(1, expr.length() - 1).trim();
            ZenithListValue list = new ZenithListValue();
            if (!content.isEmpty()) {
                List<String> items = splitTopLevel(content, ',');
                for (String item : items) {
                    list.add(evaluate(item.trim(), scope));
                }
            }
            return list;
        }

        // Member dereference / dotted access: e.g. "entity.customer.email" or "OrderStatus.CONFIRMED"
        if (expr.contains(".")) {
            String[] parts = expr.split("\\.");
            // Check if parts[0] is an Enum like OrderStatus.CONFIRMED
            if (parts.length == 2 && Character.isUpperCase(parts[0].charAt(0))) {
                // Returns string representation of enum value e.g. "CONFIRMED"
                return new ZenithStringValue(parts[1]);
            }

            ZenithValue current = scope.resolve(parts[0]);
            // If parts[0] is not bound directly in scope, check if it's a property on 'entity'
            if (current instanceof ZenithNullValue && scope.contains("entity")) {
                ZenithValue entityVal = scope.resolve("entity");
                if (entityVal instanceof ZenithEntityValue) {
                    current = ((ZenithEntityValue) entityVal).getProperty(parts[0]);
                }
            }

            for (int i = 1; i < parts.length; i++) {
                String member = parts[i];
                if (current instanceof ZenithEntityValue) {
                    current = ((ZenithEntityValue) current).getProperty(member);
                } else {
                    return ZenithNullValue.INSTANCE;
                }
            }
            return current;
        }

        // Identifier lookup in scope
        if (scope.contains(expr)) {
            return scope.resolve(expr);
        }

        // If not in scope directly, check if entity has this property (e.g. status == 'PENDING')
        ZenithValue entityVal = scope.resolve("entity");
        if (entityVal instanceof ZenithEntityValue) {
            ZenithEntityValue ent = (ZenithEntityValue) entityVal;
            if (ent.hasProperty(expr)) {
                return ent.getProperty(expr);
            }
        }

        // Fallback: enum identifier shorthand (e.g. PENDING, CONFIRMED)
        if (expr.matches("^[A-Z][A-Z0-9_]*$")) {
            return new ZenithStringValue(expr);
        }

        return new ZenithStringValue(expr);
    }

    private static boolean areValuesEqual(ZenithValue v1, ZenithValue v2) {
        if (v1 == null && v2 == null) return true;
        if (v1 == null || v2 == null) return false;
        if (v1 instanceof ZenithNullValue && v2 instanceof ZenithNullValue) return true;
        if (v1 instanceof ZenithNullValue || v2 instanceof ZenithNullValue) return false;

        // Number comparison
        if (v1 instanceof ZenithNumberValue && v2 instanceof ZenithNumberValue) {
            return ((ZenithNumberValue) v1).getDecimalValue().compareTo(((ZenithNumberValue) v2).getDecimalValue()) == 0;
        }

        // String comparison (handles enums e.g. "CONFIRMED" == "CONFIRMED")
        return v1.asString().equals(v2.asString());
    }

    private static int compareValues(ZenithValue v1, ZenithValue v2) {
        if (v1 instanceof ZenithNumberValue && v2 instanceof ZenithNumberValue) {
            return ((ZenithNumberValue) v1).getDecimalValue().compareTo(((ZenithNumberValue) v2).getDecimalValue());
        }
        return v1.asString().compareTo(v2.asString());
    }

    private static int findTopLevelOp(String expr, String op) {
        int depthParen = 0;
        int depthBracket = 0;
        boolean inQuote = false;
        char quoteChar = 0;

        for (int i = 0; i <= expr.length() - op.length(); i++) {
            char c = expr.charAt(i);
            if ((c == '"' || c == '\'') && (i == 0 || expr.charAt(i - 1) != '\\')) {
                if (!inQuote) {
                    inQuote = true;
                    quoteChar = c;
                } else if (quoteChar == c) {
                    inQuote = false;
                }
            } else if (!inQuote) {
                if (c == '(') depthParen++;
                else if (c == ')') depthParen--;
                else if (c == '[') depthBracket++;
                else if (c == ']') depthBracket--;
                else if (depthParen == 0 && depthBracket == 0) {
                    if (expr.substring(i, i + op.length()).equals(op)) {
                        // Ensure '==' does not match '=' and '>' does not match '>='
                        if (op.equals(">") && (i + 1 < expr.length() && expr.charAt(i + 1) == '=')) continue;
                        if (op.equals("<") && (i + 1 < expr.length() && expr.charAt(i + 1) == '=')) continue;
                        if (op.equals("=") && (i + 1 < expr.length() && expr.charAt(i + 1) == '=')) continue;
                        return i;
                    }
                }
            }
        }
        return -1;
    }

    private static int findTopLevelWordOp(String expr, String word) {
        Pattern pattern = Pattern.compile("\\b" + Pattern.quote(word) + "\\b");
        Matcher m = pattern.matcher(expr);
        while (m.find()) {
            int idx = m.start();
            // check depth
            if (isAtTopLevel(expr, idx)) {
                return idx;
            }
        }
        return -1;
    }

    private static boolean isAtTopLevel(String expr, int targetIdx) {
        int depthParen = 0;
        int depthBracket = 0;
        boolean inQuote = false;
        char quoteChar = 0;

        for (int i = 0; i < targetIdx; i++) {
            char c = expr.charAt(i);
            if ((c == '"' || c == '\'') && (i == 0 || expr.charAt(i - 1) != '\\')) {
                if (!inQuote) {
                    inQuote = true;
                    quoteChar = c;
                } else if (quoteChar == c) {
                    inQuote = false;
                }
            } else if (!inQuote) {
                if (c == '(') depthParen++;
                else if (c == ')') depthParen--;
                else if (c == '[') depthBracket++;
                else if (c == ']') depthBracket--;
            }
        }
        return !inQuote && depthParen == 0 && depthBracket == 0;
    }

    private static int findTopLevelBinaryMinus(String expr) {
        int depthParen = 0;
        int depthBracket = 0;
        boolean inQuote = false;
        char quoteChar = 0;

        for (int i = 0; i < expr.length(); i++) {
            char c = expr.charAt(i);
            if ((c == '"' || c == '\'') && (i == 0 || expr.charAt(i - 1) != '\\')) {
                if (!inQuote) {
                    inQuote = true;
                    quoteChar = c;
                } else if (quoteChar == c) {
                    inQuote = false;
                }
            } else if (!inQuote) {
                if (c == '(') depthParen++;
                else if (c == ')') depthParen--;
                else if (c == '[') depthBracket++;
                else if (c == ']') depthBracket--;
                else if (depthParen == 0 && depthBracket == 0 && c == '-') {
                    // Check if binary: must have non-operator non-space before it
                    String before = expr.substring(0, i).trim();
                    if (!before.isEmpty()) {
                        char last = before.charAt(before.length() - 1);
                        if (Character.isLetterOrDigit(last) || last == ')' || last == ']' || last == '\'' || last == '"') {
                            return i;
                        }
                    }
                }
            }
        }
        return -1;
    }

    private static boolean isBalancedParens(String s) {
        int d = 0;
        for (char c : s.toCharArray()) {
            if (c == '(') d++;
            else if (c == ')') {
                d--;
                if (d < 0) return false;
            }
        }
        return d == 0;
    }

    public static List<String> splitTopLevel(String str, char delimiter) {
        List<String> list = new ArrayList<>();
        int depthParen = 0;
        int depthBracket = 0;
        boolean inQuote = false;
        char quoteChar = 0;
        StringBuilder current = new StringBuilder();

        for (int i = 0; i < str.length(); i++) {
            char c = str.charAt(i);
            if ((c == '"' || c == '\'') && (i == 0 || str.charAt(i - 1) != '\\')) {
                if (!inQuote) {
                    inQuote = true;
                    quoteChar = c;
                } else if (quoteChar == c) {
                    inQuote = false;
                }
                current.append(c);
            } else if (!inQuote) {
                if (c == '(') depthParen++;
                else if (c == ')') depthParen--;
                else if (c == '[') depthBracket++;
                else if (c == ']') depthBracket--;

                if (c == delimiter && depthParen == 0 && depthBracket == 0) {
                    list.add(current.toString().trim());
                    current.setLength(0);
                } else {
                    current.append(c);
                }
            } else {
                current.append(c);
            }
        }
        if (current.length() > 0) {
            list.add(current.toString().trim());
        }
        return list;
    }
}

