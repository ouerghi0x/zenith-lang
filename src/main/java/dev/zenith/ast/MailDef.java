package dev.zenith.ast;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Represents a top-level transactional email specification:
 * mail OrderConfirmation(order: Order, customer: Customer) {
 *     to: customer.email
 *     subject: "Your order #" + order.orderNumber + " is confirmed!"
 *     template: "emails/order-confirmation.html"
 * }
 */
public class MailDef {
    private final String name;
    private final List<FieldDef> parameters;
    private final String toExpression;
    private final String subjectExpression;
    private final String templatePath;
    private final String contentExpression;
    private final String fromOverride;
    private String sourceFile;
    private int sourceLine;

    public MailDef(String name, List<FieldDef> parameters, String toExpression,
                   String subjectExpression, String templatePath, String contentExpression, String fromOverride) {
        this.name = name;
        this.parameters = parameters != null ? parameters : new ArrayList<>();
        this.toExpression = toExpression != null ? toExpression : "";
        this.subjectExpression = subjectExpression != null ? subjectExpression : "";
        this.templatePath = templatePath != null ? templatePath : "";
        this.contentExpression = contentExpression != null ? contentExpression : "";
        this.fromOverride = fromOverride;
    }

    public MailDef(String name, List<FieldDef> parameters, String toExpression,
                   String subjectExpression, String templatePath, String fromOverride) {
        this(name, parameters, toExpression, subjectExpression, templatePath, "", fromOverride);
    }

    public String getName() {
        return name;
    }

    public List<FieldDef> getParameters() {
        return Collections.unmodifiableList(parameters);
    }

    public FieldDef findParameter(String paramName) {
        if (paramName == null) return null;
        for (FieldDef p : parameters) {
            if (p.getName().equalsIgnoreCase(paramName)) return p;
        }
        return null;
    }

    public String getToExpression() {
        return toExpression;
    }

    public String getSubjectExpression() {
        return subjectExpression;
    }

    public String getTemplatePath() {
        return templatePath;
    }

    public String getContentExpression() {
        return contentExpression;
    }

    public boolean hasContent() {
        return contentExpression != null && !contentExpression.trim().isEmpty();
    }

    public boolean hasTemplate() {
        return templatePath != null && !templatePath.trim().isEmpty();
    }

    public String getFromOverride() {
        return fromOverride;
    }

    public String getSourceFile() {
        return sourceFile;
    }

    public void setSourceFile(String sourceFile) {
        this.sourceFile = sourceFile;
    }

    public int getSourceLine() {
        return sourceLine;
    }

    public void setSourceLine(int sourceLine) {
        this.sourceLine = sourceLine;
    }
}

