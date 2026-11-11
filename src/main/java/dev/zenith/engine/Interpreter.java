package dev.zenith.engine;

import dev.zenith.ast.ActionStatementDef;
import dev.zenith.ast.GuardDef;
import dev.zenith.engine.trace.DispatchedEffect;
import dev.zenith.engine.trace.ExecutionTrace;
import dev.zenith.engine.trace.StateDiff;
import dev.zenith.engine.value.*;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Interpreter {
    private static final int MAX_INSTRUCTIONS = 10_000;
    private int instructionCount = 0;

    public void checkStepLimit() {
        instructionCount++;
        if (instructionCount > MAX_INSTRUCTIONS) {
            throw new RuntimeException("EngineExecutionLimitException: Exceeded maximum budget of " + MAX_INSTRUCTIONS + " steps");
        }
    }

    public boolean executeGuards(List<GuardDef> guards, Scope scope, ExecutionTrace trace) {
        if (guards == null) return true;
        for (GuardDef guard : guards) {
            checkStepLimit();
            String expr = guard.getExpression();
            ZenithValue result = Evaluator.evaluate(expr, scope);
            if (!result.isTruthy()) {
                trace.addStep("❌ Guard failed: '" + expr + "' -> " + guard.getErrorMessage());
                return false;
            } else {
                trace.addStep("✔ Guard passed: '" + expr + "'");
            }
        }
        return true;
    }

    public void executeStatement(String rawStmt, Scope scope, ZenithEntityValue entity,
                                 List<DispatchedEffect> effects, ExecutionTrace trace) {
        checkStepLimit();
        if (rawStmt == null) return;
        String stmt = rawStmt.trim();
        if (stmt.isEmpty() || stmt.startsWith("//")) return;

        // Strip trailing semicolon if present
        if (stmt.endsWith(";")) {
            stmt = stmt.substring(0, stmt.length() - 1).trim();
        }

        // Pattern 1: Variable Declaration: 'var x = expr' or 'val x = expr'
        if (stmt.startsWith("var ") || stmt.startsWith("val ")) {
            int eqIdx = stmt.indexOf('=');
            if (eqIdx != -1) {
                String varName = stmt.substring(4, eqIdx).trim();
                String exprStr = stmt.substring(eqIdx + 1).trim();
                ZenithValue val = Evaluator.evaluate(exprStr, scope);
                scope.define(varName, val);
                trace.addStep("Bound var " + varName + " = " + val.asString());
                return;
            }
        }

        // Pattern 2: Email Send Action: 'send MailContract(arg = val, ...)'
        if (stmt.startsWith("send ")) {
            executeSendStatement(stmt, scope, effects, trace);
            return;
        }

        // Pattern 2.5: WebSocket Broadcast Action: 'broadcast "/topic/chat" { ... }'
        if (stmt.startsWith("broadcast ")) {
            executeBroadcastStatement(stmt, scope, effects, trace);
            return;
        }

        // Pattern 3: Assignment: 'target = expr' or 'entity.field = expr'
        int eqIdx = stmt.indexOf('=');
        if (eqIdx != -1 && !stmt.startsWith("==")) {
            String target = stmt.substring(0, eqIdx).trim();
            String exprStr = stmt.substring(eqIdx + 1).trim();

            ZenithValue evaluatedVal = Evaluator.evaluate(exprStr, scope);

            if (target.startsWith("entity.")) {
                String field = target.substring(7).trim();
                entity.setProperty(field, evaluatedVal);
                scope.assign(field, evaluatedVal);
                trace.addStep("Mutated " + entity.getModelName() + "." + field + " = " + evaluatedVal.asString());
            } else if (entity.hasProperty(target)) {
                entity.setProperty(target, evaluatedVal);
                scope.assign(target, evaluatedVal);
                trace.addStep("Mutated " + entity.getModelName() + "." + target + " = " + evaluatedVal.asString());
            } else if (scope.contains(target)) {
                scope.assign(target, evaluatedVal);
                trace.addStep("Updated variable " + target + " = " + evaluatedVal.asString());
            } else {
                entity.setProperty(target, evaluatedVal);
                scope.define(target, evaluatedVal);
                trace.addStep("Mutated " + entity.getModelName() + "." + target + " = " + evaluatedVal.asString());
            }
            return;
        }

        // Generic evaluation of standalone expressions/calls
        ZenithValue evaluated = Evaluator.evaluate(stmt, scope);
        trace.addStep("Evaluated expression: " + stmt + " -> " + evaluated.asString());
    }

    private void executeBroadcastStatement(String stmt, Scope scope, List<DispatchedEffect> effects, ExecutionTrace trace) {
        String rest = stmt.substring(10).trim();
        String topic = "/topic/messages";
        Map<String, Object> payload = new LinkedHashMap<>();

        if (rest.startsWith("\"") || rest.startsWith("'")) {
            char q = rest.charAt(0);
            int closing = rest.indexOf(q, 1);
            if (closing > 0) {
                topic = rest.substring(1, closing);
                rest = rest.substring(closing + 1).trim();
            }
        } else {
            int space = rest.indexOf(' ');
            if (space > 0) {
                topic = rest.substring(0, space).trim();
                rest = rest.substring(space + 1).trim();
            }
        }

        if (rest.startsWith("{") && rest.endsWith("}")) {
            String inner = rest.substring(1, rest.length() - 1).trim();
            if (!inner.isEmpty()) {
                List<String> pairs = Evaluator.splitTopLevel(inner, ',');
                for (String p : pairs) {
                    int eq = p.indexOf('=');
                    if (eq != -1) {
                        String k = p.substring(0, eq).trim();
                        String vExpr = p.substring(eq + 1).trim();
                        ZenithValue vVal = Evaluator.evaluate(vExpr, scope);
                        payload.put(k, vVal != null ? vVal.getRawValue() : null);
                    } else {
                        ZenithValue vVal = Evaluator.evaluate(p.trim(), scope);
                        payload.put(p.trim(), vVal != null ? vVal.getRawValue() : null);
                    }
                }
            }
        } else if (!rest.isEmpty()) {
            ZenithValue evalPayload = Evaluator.evaluate(rest, scope);
            payload.put("data", evalPayload != null ? evalPayload.getRawValue() : null);
        } else {
            ZenithValue entity = scope.resolve("entity");
            payload.put("data", entity != null ? entity.getRawValue() : null);
        }

        DispatchedEffect effect = new DispatchedEffect("WEBSOCKET", topic, payload);
        effects.add(effect);
        trace.addStep("Broadcast to WebSocket topic '" + topic + "' -> " + payload);
    }

    private void executeSendStatement(String stmt, Scope scope, List<DispatchedEffect> effects, ExecutionTrace trace) {
        String rest = stmt.substring(5).trim();
        int parenOpen = rest.indexOf('(');
        int parenClose = rest.lastIndexOf(')');

        String mailName = rest;
        Map<String, Object> args = new LinkedHashMap<>();

        if (parenOpen != -1 && parenClose != -1 && parenClose > parenOpen) {
            mailName = rest.substring(0, parenOpen).trim();
            String argsBody = rest.substring(parenOpen + 1, parenClose).trim();
            if (!argsBody.isEmpty()) {
                List<String> argPairs = Evaluator.splitTopLevel(argsBody, ',');
                for (String pair : argPairs) {
                    int aEq = pair.indexOf('=');
                    if (aEq != -1) {
                        String argKey = pair.substring(0, aEq).trim();
                        String argExpr = pair.substring(aEq + 1).trim();
                        ZenithValue argVal = Evaluator.evaluate(argExpr, scope);
                        args.put(argKey, argVal != null ? argVal.getRawValue() : null);
                    } else {
                        ZenithValue argVal = Evaluator.evaluate(pair.trim(), scope);
                        args.put(pair.trim(), argVal != null ? argVal.getRawValue() : null);
                    }
                }
            }
        }

        DispatchedEffect effect = new DispatchedEffect("MAIL", mailName, args);
        effects.add(effect);
        trace.addStep("✉ Dispatched mail contract '" + mailName + "' with parameters " + args);
    }
}
