package calculator;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.ArrayList;
import java.util.List;

/**
 * 计算器核心逻辑模型，与 JavaFX 完全解耦，可单元测试。
 *
 * 状态约定：
 * - nums 与 ops 构成已提交的表达式（ops.size() == nums.size()-1 或 == nums.size()）
 * - currentInput 为正在输入的数字缓冲（可为 "-5"、"0."、".5" 等形态）
 */
public class CalculatorModel {

    public static final int MAX_INPUT_DIGITS = 15;

    private final List<Double> nums = new ArrayList<>();
    private final List<Character> ops = new ArrayList<>();
    private String currentInput = "";
    private boolean justEvaluated = false;
    private boolean error = false;
    private double result = 0;
    private String lastExpression = "";

    public void pressDigit(char d) {
        if (d < '0' || d > '9') return;
        if (error || justEvaluated) {
            reset();
        }
        if (currentInput.isEmpty()) {
            currentInput = String.valueOf(d);
        } else if ("0".equals(currentInput)) {
            if (d != '0') {
                currentInput = String.valueOf(d);
            }
        } else if (digitCount(currentInput) < MAX_INPUT_DIGITS) {
            currentInput += d;
        }
    }

    public void pressDot() {
        if (error || justEvaluated) {
            reset();
        }
        if (currentInput.isEmpty() || "-".equals(currentInput)) {
            currentInput += "0.";
        } else if (!currentInput.contains(".")) {
            currentInput += ".";
        }
    }

    public void pressOperator(char op) {
        if (op != '+' && op != '-' && op != '*' && op != '/') return;
        if (error) {
            reset();
        } else if (justEvaluated) {
            nums.clear();
            ops.clear();
            nums.add(result);
            ops.add(op);
            currentInput = "";
            justEvaluated = false;
            return;
        }
        if (!currentInput.isEmpty()) {
            if (!isValidNumber(currentInput)) return;
            nums.add(parse(currentInput));
            currentInput = "";
        }
        if (nums.isEmpty()) return; // spec 1: 空框点运算符，显示 0
        if (ops.size() == nums.size()) {
            ops.set(ops.size() - 1, op); // spec 3: 运算符覆盖
        } else {
            ops.add(op);
        }
    }

    public void pressEquals() {
        if (error || justEvaluated) return;
        if (nums.isEmpty() || ops.isEmpty() || currentInput.isEmpty()) return; // spec 4
        if (ops.size() != nums.size()) return;
        if (!isValidNumber(currentInput)) return;
        nums.add(parse(currentInput));
        currentInput = "";
        double v = evaluate();
        if (Double.isNaN(v) || Double.isInfinite(v)) {
            error = true;
            return;
        }
        lastExpression = buildExpression();
        result = v;
        justEvaluated = true;
    }

    public void pressClear() {
        reset();
    }

    public void pressBackspace() {
        if (error || justEvaluated || currentInput.isEmpty()) return;
        currentInput = currentInput.substring(0, currentInput.length() - 1);
    }

    public void pressNegate() {
        if (error || justEvaluated || currentInput.isEmpty()) return;
        if ("0".equals(currentInput)) return;
        if (currentInput.startsWith("-")) {
            currentInput = currentInput.substring(1);
        } else {
            currentInput = "-" + currentInput;
        }
    }

    public String getDisplay() {
        if (error) return "错误";
        if (justEvaluated) return lastExpression + "=" + format(result);
        String expr = buildExpression();
        return expr.isEmpty() ? "0" : expr;
    }

    /** 乘除优先、同级左结合 */
    private double evaluate() {
        List<Double> vals = new ArrayList<>();
        List<Character> lowOps = new ArrayList<>();
        vals.add(nums.get(0));
        for (int i = 0; i < ops.size(); i++) {
            char op = ops.get(i);
            double b = nums.get(i + 1);
            if (op == '*' || op == '/') {
                if (op == '/' && b == 0) return Double.NaN;
                double a = vals.remove(vals.size() - 1);
                vals.add(op == '*' ? a * b : a / b);
            } else {
                vals.add(b);
                lowOps.add(op);
            }
        }
        double v = vals.get(0);
        for (int i = 0; i < lowOps.size(); i++) {
            double b = vals.get(i + 1);
            v = lowOps.get(i) == '+' ? v + b : v - b;
        }
        return v;
    }

    private String buildExpression() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < nums.size(); i++) {
            if (i > 0) sb.append(ops.get(i - 1));
            sb.append(format(nums.get(i)));
        }
        if (!ops.isEmpty() && ops.size() == nums.size()) sb.append(ops.get(ops.size() - 1));
        if (!currentInput.isEmpty()) sb.append(currentInput);
        return sb.toString();
    }

    private static int digitCount(String s) {
        int n = 0;
        for (char c : s.toCharArray()) {
            if (c >= '0' && c <= '9') n++;
        }
        return n;
    }

    private static boolean isValidNumber(String s) {
        try {
            Double.parseDouble(s);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private static double parse(String s) {
        return Double.parseDouble(s);
    }

    /** 整数去小数点；小数取 12 位有效数字并去尾零；极端值回退科学计数 */
    private static String format(double v) {
        if (Double.isNaN(v) || Double.isInfinite(v)) return "错误";
        if (v == Math.floor(v) && Math.abs(v) < 1e15) {
            return String.valueOf((long) v);
        }
        String s = BigDecimal.valueOf(v)
                .round(new MathContext(12))
                .stripTrailingZeros()
                .toPlainString();
        if (s.length() > 20) return String.valueOf(v);
        return s;
    }

    private void reset() {
        nums.clear();
        ops.clear();
        currentInput = "";
        justEvaluated = false;
        error = false;
        result = 0;
        lastExpression = "";
    }
}
