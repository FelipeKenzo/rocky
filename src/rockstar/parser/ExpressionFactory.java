/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package rockstar.parser;

import java.util.LinkedList;
import java.util.List;
import rockstar.expression.ConstantExpression;
import rockstar.expression.ExpressionError;
import rockstar.expression.Expression;
import rockstar.expression.ListExpression;
import rockstar.expression.LogicalExpression;
import rockstar.expression.VariableReference;
import rockstar.runtime.RockNumber;

/**
 *
 * @author Gabor
 */
public class ExpressionFactory {

    /**
     * Parse an expression, throw exception if failed
     *
     * @param tokens the tokens to parse
     * @param line the Line
     * @return
     */
    public static Expression getExpressionFor(List<String> tokens, Line line, Expression... defaultExprs) {
        Expression parsed = new ExpressionParser(tokens, line).parse(defaultExprs);
        if (parsed != null) {
            return parsed;
        }
        return new ExpressionError(tokens, line);
    }

    /**
     * Try a variable reference, returns null if failed
     *
     * @param list
     * @param line
     * @return
     */
    public static VariableReference tryVariableReferenceFor(List<String> list, Line line) {
        ExpressionParser parser = new ExpressionParser(list, line);
        VariableReference varRef = parser.parseVariableReference();
        if (varRef != null && parser.isFullyParsed()) {
            // has valid value and parsed through the list
            return varRef;
        }
        return null;
    }

    /**
     * Try a variable reference, returns null if failed
     *
     * @param list
     * @param line
     * @return
     */
    public static List<VariableReference> tryVariableReferenceListFor(List<String> list, Line line) {
        List<VariableReference> refList = new LinkedList<>();
        ExpressionParser parser = new ExpressionParser(list, line);
        while (!parser.isFullyParsed()) {
            if (!refList.isEmpty()) {

            }
            VariableReference varRef = parser.parseVariableReference();
            if (varRef != null) {
                refList.add(varRef);
            } else {
                return null;
            }

        }

        return refList;
    }

    /**
     * Try to parse a literal, returns null if failed
     *
     * @param list
     * @param line
     * @return
     */
    public static ConstantExpression tryLiteralFor(List<String> list, Line line) {
        ExpressionParser parser = new ExpressionParser(list, line);
        ConstantExpression literal = parser.parseLiteral();
        if (literal != null && parser.isFullyParsed()) {
            // has valid value and parsed through the list
            return literal;
        }
        return null;
    }

    /**
     * Parses a poetic number literal
     *
     * @param list
     * @param line
     * @param orig
     * @return
     */
    public static ConstantExpression getPoeticLiteralFor(List<String> list, Line line, String orig) {
        // if a literal word like "nothing", then use that
        ConstantExpression literal = tryLiteralFor(list, line);
        if (literal != null) {
            return literal;
        }

        // parse the orig String
        int digit = 0;
        StringBuilder sb = new StringBuilder();

        int pos = 0;
        boolean inComment = false;
        while (pos <= orig.length()) {
            char c = (pos < orig.length()) ? orig.charAt(pos) : ' ';
            if (!inComment) {
                if (Character.isLetter(c)) {
                    digit++;
                } else if (c == '.' || c == ' ' || c == '(') {
                    if (digit > 0) {
                        sb.append((char) ('0' + (digit % 10)));
                    }
                    if (c == '.') {
                        sb.append(c);
                    }
                    digit = 0;
                    if (c == '(') {
                        inComment = true;
                    }
                }
            } else {
                // in comment
                if (c == ')') {
                    inComment = false;
                }
            }

            pos++;
        }
        // parse the concatenated string
        return new ConstantExpression(RockNumber.parse(sb.toString()));
    }

    /**
     * Parses a simple expression (literal or varable reference)
     *
     * @param list
     * @param line
     * @return
     */
    public static Expression tryExpressionFor(List<String> list, Line line) {
        ExpressionParser parser = new ExpressionParser(list, line);
        Expression expr = parser.parse();
        if (expr != null && parser.isFullyParsed()) {
            // has valid value and parsed through the list
            return expr;
        }
        return null;
    }

    /**
     * Parses a list expression of variable references (optionally with values)
     *
     * @param list
     * @param line
     * @param valuesAllowed are values allowed?
     * @return
     */
    public static ListExpression tryListExpressionFor(List<String> list, Line line, boolean valuesAllowed) {
        ExpressionParser parser = new ExpressionParser(list, line);
        Expression expr = parser.parse();
        if (expr != null && parser.isFullyParsed()) {
            ListExpression listexpr = new ListExpression();
            List<Expression> stack = new LinkedList<>();
            stack.add(expr);
            while (!stack.isEmpty()) {
                expr = stack.remove(0);
                if (expr instanceof VariableReference) {
                    // variable references are allowed
                    VariableReference vref = (VariableReference) expr;
                    listexpr.addParameter(vref);
                } else if (valuesAllowed && expr instanceof ConstantExpression) {
                    // values are allowed if the parameter allows
                    ConstantExpression valueExpr = (ConstantExpression) expr;
                    listexpr.addParameter(valueExpr);
                } else if (expr instanceof ListExpression) {
                    // list expressions are expanded
                    ListExpression listExpression = (ListExpression) expr;
                    stack.addAll(listExpression.getParameters());
                } else if (expr instanceof LogicalExpression) {
                    // 'and' expressions are expanded, too
                    LogicalExpression logicalExpression = (LogicalExpression) expr;
                    if (logicalExpression.getType() != LogicalExpression.LogicalType.AND) {
                        return null;
                    }
                    stack.addAll(logicalExpression.getParameters());
                } else {
                    // all others are no allowed
                    return null;
                }
            }

            return listexpr;
        }
        return null;
    }
}
