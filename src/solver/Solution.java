package solver;

import java.util.Locale;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static solver.Constants.*;

/**
 * Represents a single solution with code for solution's procedures.
 *
 * @author Roman Elizarov
 */
class Solution {
    final int[] procLen = new int[MAX_PROCS];
    final int[] code = new int[MAX_PROCS * MAX_PROC_LEN];

    Solution() {}

    Solution(String str) {
        String s = str;
        while (true) {
            s = s.trim();
            if (s.isEmpty())
                break;
            if (!s.startsWith("P") || s.length() < 4)
                throw new IllegalArgumentException("Not a solution string, expects 'P': " + str);
            s = s.substring(1);
            int pi = s.charAt(0) - '1';
            if (pi < 0 || pi >= MAX_PROCS)
                throw new IllegalArgumentException("Not a solution string, invalid proc id: " + str);
            s = s.substring(1).trim();
            if (!s.startsWith("["))
                throw new IllegalArgumentException("Not a solution string, expects '[': " + str);
            s = s.substring(1).trim();
            int i = s.indexOf(']');
            if (i < 0)
                throw new IllegalArgumentException("Not a solution string, expects ']': " + str);
            String[] cs = s.substring(0, i).split("\\s+");
            s = s.substring(i + 1);
            if (cs.length > MAX_PROC_LEN)
                throw new IllegalArgumentException("Not a solution string, proc len exceeded: " + str);
            procLen[pi] = cs.length;
            for (int ii = 0; ii < cs.length; ii++) {
                String c = cs[ii];
                if (c.length() > 2)
                    throw new IllegalArgumentException("Not a solution string, invalid code: " + str);
                String opS = c.substring(c.length() - 1);
                int op = str2op(opS);
                if (op < 0)
                    throw new IllegalArgumentException("Not a solution string, invalid op '" + opS + "': " + str);
                int mod;
                if (c.length() == 2) {
                    String modS = c.substring(0, 1);
                    mod = str2mod(modS);
                    if (mod < 0)
                        throw new IllegalArgumentException("Not a solution string, invalid mod '" + modS + "': " + str);
                } else
                    mod = ALWAYS;
                code[pi * MAX_PROC_LEN + ii] = mod | op;
            }
        }
    }

    void assign(Solution s) {
        System.arraycopy(s.procLen, 0, procLen, 0, procLen.length);
        System.arraycopy(s.code, 0, code, 0, code.length);
    }

    String shape2Str() {
        return String.format(Locale.US, "%d = [%s]",
                IntStream.of(procLen).sum(),
                IntStream.of(procLen).
                        filter(len -> len != 0)
                        .mapToObj(cl -> "" + cl)
                        .collect(Collectors.joining(", ")));
    }

    String code2Str(boolean pickOne) {
        StringBuilder sb = new StringBuilder();
        for (int pi = 0; pi < MAX_PROCS; pi++) {
            int len = procLen[pi];
            if (len == 0)
                continue;
            if (sb.length() > 0)
                sb.append(' ');
            sb.append("P").append(pi + 1).append(" [");
            for (int ii = 0; ii < len; ii++) {
                if (ii > 0)
                    sb.append(' ');
                int c = code[pi * MAX_PROC_LEN + ii];
                sb.append(mod2str(c, pickOne)).append(op2str(c));
            }
            sb.append("]");
        }
        return sb.toString();
    }

    @Override
    public String toString() {
        return code2Str(false);
    }

    private static String mod2str(int c, boolean pickOne) {
        int mod = c & ~OP_MASK;
        if (mod == 0)
            return "_";
        for (int i = 0; i < MOD_STRS.length; i++) {
            if (mod == MODS[i] || pickOne && (mod & MODS[i]) != 0) {
                return MOD_STRS[i];
            }
        }
        return "?";
    }

    private static String op2str(int c) {
        int op = c & OP_MASK;
        if (op == UNKNOWN)
            return "_";
        for (int i = 0; i < OP_STRS.length; i++) {
            if (op == OPS[i]) {
                return OP_STRS[i];
            }
        }
        return "?";
    }

    private static int str2mod(String s) {
        if (s.equals("_")) {
            return 0;
        }
        for (int i = 0; i < MOD_STRS.length; i++) {
            if (s.equals(MOD_STRS[i])) {
                return MODS[i];
            }
        }
        return -1;
    }

    private static int str2op(String s) {
        if (s.equals("_")) {
            return UNKNOWN;
        }
        for (int i = 0; i < OP_STRS.length; i++) {
            if (s.equals(OP_STRS[i])) {
                return OPS[i];
            }
        }
        return -1;
    }
}
