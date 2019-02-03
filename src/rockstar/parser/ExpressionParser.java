/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package rockstar.parser;

import java.util.Arrays;
import java.util.List;
import java.util.Stack;
import rockstar.expression.ComparisonExpression;
import rockstar.expression.ComparisonExpression.ComparisonType;
import rockstar.expression.CompoundExpression;
import rockstar.expression.ConstantExpression;
import rockstar.expression.DivideExpression;
import rockstar.expression.DummyExpression;
import rockstar.expression.Expression;
import rockstar.expression.FunctionCall;
import rockstar.expression.LogicalExpression;
import rockstar.expression.LogicalExpression.LogicalType;
import rockstar.expression.MinusExpression;
import rockstar.expression.MultiplyExpression;
import rockstar.expression.NotExpression;
import rockstar.expression.PlusExpression;
import rockstar.expression.SimpleExpression;
import rockstar.expression.VariableReference;
import rockstar.runtime.BlockContext;
import rockstar.runtime.NumericValue;
import rockstar.runtime.Value;

/**
 *
 * @author Gabor
 */
public class ExpressionParser {

    // tokens of the whole expression
    private final List<String> list;
    // next position in the list
    private int idx;
    // saved position
    private int savedIdx;
    private final Line line;

    ExpressionParser(List<String> list, Line line) {
        this.list = list;
        this.line = line;
        idx = 0;
    }

    boolean isFullyParsed() {
        return idx == list.size();
    }

    private boolean containsAtLeast(int count) {
        return list.size() >= idx + count;
    }

    private String peekCurrent() {
        return list.get(idx);
    }

    private void next() {
        idx++;
    }

    private void next(int count) {
        idx += count;
    }

    private String peekNext() {
        String next = list.get(idx + 1);
        return next;
    }

    private String peekNext(int offset) {
        String next = list.get(idx + offset);
        return next;
    }
    
    private void savePos() {
        savedIdx = idx;
    }
    
    private void restorePos() {
        idx = savedIdx;
    }
    
    public static final List<String> MYSTERIOUS_KEYWORDS = Arrays.asList(new String[]{"mysterious"});
    public static final List<String> NULL_KEYWORDS = Arrays.asList(new String[]{"null", "nothing", "nowhere", "nobody", "empty", "gone"});
    public static final List<String> BOOLEAN_TRUE_KEYWORDS = Arrays.asList(new String[]{"true", "right", "yes", "ok"});
    public static final List<String> BOOLEAN_FALSE_KEYWORDS = Arrays.asList(new String[]{"false", "wrong", "no", "lies"});
    public static final List<String> RESERVED_KEYWORDS = Arrays.asList(new String[]{"definitely", "maybe"});

    ConstantExpression parseLiteral() {
        if (!isFullyParsed()) {
            String token = peekCurrent();
            if (token.startsWith("\"") && token.endsWith("\"") && token.length() >= 2) {
                // string literal> strip quotes
                next();
                String literal = token.substring(1, token.length() - 1);
                return new ConstantExpression(literal);
            }
            token = token.toLowerCase();
            if (MYSTERIOUS_KEYWORDS.contains(token)) {
                next();
                return ConstantExpression.CONST_MYSTERIOUS;
            }
            if (NULL_KEYWORDS.contains(token)) {
                next();
                return ConstantExpression.CONST_NULL;
            }
            if (BOOLEAN_TRUE_KEYWORDS.contains(token)) {
                next();
                return ConstantExpression.CONST_TRUE;
            }
            if (BOOLEAN_FALSE_KEYWORDS.contains(token)) {
                next();
                return ConstantExpression.CONST_FALSE;
            }
            NumericValue nv = NumericValue.parse(token);
            if (nv != null) {
                next();
                return new ConstantExpression(nv);
            }
            if (RESERVED_KEYWORDS.contains(token)) {
                // reserved keywords are skipped
                next();
                return null;
            }
        }
        return null;
    }
    private static final List<String> COMMON_VARIABLE_KEYWORDS = Arrays.asList(new String[]{"a", "an", "the", "my", "your", "A", "An", "The", "My", "Your"});
    private static final List<String> LAST_NAMED_VARIABLE_REFERENCE_KEYWORDS = Arrays.asList(new String[]{"it", "he", "she", "him", "her", "they", "them", "ze", "hir", "zie", "zir", "xe", "xem", "ve", "ver"});

    VariableReference parseVariableReference() {
        String name = null;
        boolean isFunctionName = false;
        if (isFullyParsed()) {
            return null;
        }
        String token0 = peekCurrent();
        if (COMMON_VARIABLE_KEYWORDS.contains(token0) && containsAtLeast(2)) {
            // common variable
            String token1 = peekNext();
            if (token1.toLowerCase().equals(token1)) {
                name = token0.toLowerCase() + " " + token1;
                next(2);
            }
        }
        if (name == null && token0.length() > 0 && Character.isUpperCase(token0.charAt(0))) {
            // proper variable
            next(); // first part processed
            StringBuilder sb = new StringBuilder(token0);
            while (!isFullyParsed()) {
                String token = peekCurrent();
                // all parts of a Proper Name must start with capital letter
                if (token.length() > 0 && Character.isUpperCase(token.charAt(0))) {
                    next(); // next part processed
                    sb.append(" ").append(token);
                } else {
                    break;
                }
            }
            // if a Proper Name is followed by "taking", it is a function call
            if (! isFullyParsed() && peekCurrent().equals("taking")) {
                isFunctionName = true;
            }
            name = sb.toString();
        }
        if (name == null && containsAtLeast(1)) {
            // Variable backreference
            // TODO start of the line capitalization
            if (LAST_NAMED_VARIABLE_REFERENCE_KEYWORDS.contains(token0)) {
                next();
                return ExpressionFactory.lastVariable;
            }
        }
        if (name != null) {
            VariableReference varRef = new VariableReference(name, isFunctionName);
            if (! isFunctionName) {
                ExpressionFactory.lastVariable = varRef;
            } 
            return varRef;
        }
        return null;
    }

    public SimpleExpression parseSimpleExpression() {
        SimpleExpression expr = parseLiteral();
        if (expr == null) {
            expr = parseVariableReference();
        }
        return expr;
    }

    Stack<CompoundExpression> operatorStack;
    Stack<Expression> valueStack;

    public Expression parse() {
        operatorStack = new Stack<>();
        valueStack = new Stack<>();
        boolean operatorRequired = false;
        while (!isFullyParsed()) {
            CompoundExpression operator = getOperator();
            if (operator != null) {
                // operator found
                pushOperator(operator);
                // after operators a value us requires, except FunctionCall that consumers values, too
                operatorRequired = (operator instanceof FunctionCall);
            } else if (operatorRequired) {
                // operator not found, but required
                return new DummyExpression(list, idx, "Operator required");
            } else {
                Expression value = parseSimpleExpression();
                if (value != null) {
                    // value found
                    valueStack.push(value);
                } else {
                    // neither operator nor value found
                    return new DummyExpression(list, idx, "Operator or value required");
                }
                operatorRequired = true;
            }
        }
        // compact operators
        pushOperator(new EndOfExpression());
        return valueStack.isEmpty() ? null : valueStack.get(0);
    }

    private void pushOperator(CompoundExpression operator) {

        // interpret 
        while (!operatorStack.isEmpty()
                && (operatorStack.peek().getPrecedence() <= operator.getPrecedence())) {

            // take the operator from the top of the operator stack
            CompoundExpression op = operatorStack.pop();

            // process the operator
            int paramCount = op.getParameterCount();
            if (valueStack.size() < paramCount) {
                paramCount = valueStack.size();
            }
            // add paramcount parameters to the operator, preserving declaraton order
            for (int i = 0; i < paramCount; i++) {
                op.addParameterReverse(valueStack.pop());
            }
            // all parameters set: time to check the types
            op.setupFinished();
            // the result of the operator is a value now
            valueStack.push(op);
        }

        operatorStack.push(operator);
    }

    public CompoundExpression getOperator() {
        String token = this.peekCurrent();
        // logical operators
        if ("not".equals(token)) {
            next();
            return new NotExpression();
        }
        if ("and".equals(token)) {
            next();
            return new LogicalExpression(LogicalType.AND);
        }
        if ("or".equals(token)) {
            next();
            return new LogicalExpression(LogicalType.OR);
        }
        if ("nor".equals(token)) {
            next();
            return new LogicalExpression(LogicalType.NOR);
        }
        if ("is".equals(token)) {
            next();
            if (containsAtLeast(3)) {
                if ("than".equals(peekNext())) {
                    String comparator = this.peekCurrent();
                    ComparisonType type = null;
                    switch (comparator) {
                        case "higher":
                        case "greater":
                        case "bigger":
                        case "stronger":
                            type = ComparisonType.GREATER_THAN;
                            break;
                        case "lower":
                        case "less":
                        case "smaller":
                        case "weaker":
                            type = ComparisonType.LESS_THAN;
                            break;
                    }
                    if (type != null) {
                        next(2);
                        return new ComparisonExpression(type);
                    }
                }
            }
            if (containsAtLeast(4)) {
                if ("as".equals(peekCurrent()) && "as".equals(peekNext(2))) {
                    String comparator = this.peekNext(1);
                    ComparisonType type = null;
                    switch (comparator) {
                        case "high":
                        case "great":
                        case "big":
                        case "strong":
                            type = ComparisonType.GREATER_OR_EQUALS;
                            break;
                        case "low":
                        case "little":
                        case "small":
                        case "weak":
                            type = ComparisonType.LESS_OR_EQUALS;
                            break;
                    }
                    if (type != null) {
                        next(3);
                        return new ComparisonExpression(type);
                    }
                }
            }
            // simple "is"
            return new ComparisonExpression(ComparisonType.EQUALS);
        }
        if ("isnt".equals(token) || "aint".equals(token)) {
            next();
            return new ComparisonExpression(ComparisonType.NOT_EQUALS);
        }

        // arithmetical operators
        if ("plus".equals(token) || "with".equals(token)) {
            next();
            return new PlusExpression();
        }
        if ("minus".equals(token) || "without".equals(token)) {
            next();
            return new MinusExpression();
        }
        if ("times".equals(token) || "of".equals(token)) {
            next();
            return new MultiplyExpression();
        }
        if ("over".equals(token)) {
            next();
            return new DivideExpression();
        }

        // function call
        if ("taking".equals(token)) {
            next();
            FunctionCall functionCall = new FunctionCall();
            SimpleExpression funcParam;
            savePos();

            while (!isFullyParsed()) {
                funcParam = parseSimpleExpression();
                if (funcParam != null) {
                    // if a function call is found in the parameters, we need to rewind
                    // Example: say TrueFunc taking nothing and TrueFunc taking nothing
                    if(funcParam instanceof VariableReference && 
                            ((VariableReference)funcParam).isFunctionName()) {
                        restorePos();
                        break;
                    }
                    // otherwise save the parameter and the position
                    functionCall.addParameter(funcParam);
                    savePos();
                } else {
                    // ERROR: invalid parameter
                    // TODO some better method to sign expression parse error
                    valueStack.push(new DummyExpression(list, idx, "Invalid function parameter"));
                    return null;
                }
                // end of expression or no "and" found: end of parameters
                if (isFullyParsed() || !("and".equals(peekCurrent()))) {
                    break;
                }
                next();
            }
            return functionCall;
        }
        return null;
    }

    private static class EndOfExpression extends CompoundExpression {

        @Override
        public int getPrecedence() {
            return 999;
        }

        @Override
        public String getFormat() {
            return "$";
        }

        @Override
        public int getParameterCount() {
            return 0;
        }

        @Override
        public Value evaluate(BlockContext ctx) {
            return null;
        }
        
        

    }

}

/*
  1 + 2 * 3^4           =>  1+(2*(3^4))

ValueStack                  OperatorStack
1                           
1                           +
1 2                         +
1 2                         + *
1 2 3                       + *
1 2 3                       + * ^
1 2 3 4                     + * ^
1 2 3 4                     + * ^ $ REDUCE
1 2 (3^4)                   + * $   REDUCE
1 (2*(3^4))                 + $     REDUCE
(1+(2*(3^4)))               $       FINISH


$       = 999
+ -     500
* /     400
^       300

  1*2 + 3*4             => (1*2) + (3*4)
ValueStack                  OperatorStack
1
1                           *
1 2                         *
1 2                         * +     REDUCE
(1*2)                       +
(1*2) 3                     +
(1*2) 3                     + *
(1*2) 3 4                   + *
(1*2) 3 4                   + * $   REDUCE
(1*2) (3*4)                 + $     REDUCE
((1*2)+(3*4))               $       FINISH


 not not a and b        => (!(!a)) & b

$
& | nor 
!

ValueStack                  OperatorStack
                            !
                            ! !
a                           ! !
a                           ! ! &   REDUCE
(!a)                        ! &     REDUDE
(!(!a))                     &
(!(!a)) b                   &
(!(!a)) b                   & $     REDUDE
((!(!a)) & b)               $       FINISH


  fibo taking 1 and 2 plus 3    => (fibo(1, 2) + 3)

fibo
fibo                        funccall(1,2)
fibo                        funccall +      REDUCE
fibo(1,2)                   +
fibo(1,2) 3                 +
fibo(1,2) 3                 + $             REDUCE
(fibo(1,2)+3)               $               FINISH



Midnight taking my world, Fire is nothing and Midnight taking my world, Hate is nothing


 */