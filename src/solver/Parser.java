package solver;

import java.io.*;
import java.util.ArrayList;
import java.util.List;

import static java.lang.Integer.parseInt;
import static solver.Constants.*;
import static solver.Util.*;

class Parser {
    private final CargoBotSolver.Action action;
    private final boolean all;
    private String line;
    private int lineNo;

    Parser(CargoBotSolver.Action action, boolean all) {
        this.action = action;
        this.all = all;
    }

    void processFile(InputStream in) throws IOException {
        LineNumberReader reader = new LineNumberReader(new InputStreamReader(new BufferedInputStream(in, 65536)));
        try {
            processFile(reader);
        } catch (Exception e) {
            System.out.println("Error on line " + lineNo + ": " + e);
            e.printStackTrace(System.out);
        }
    }

    private void processFile(LineNumberReader in) throws IOException {
        nextLine(in);
        List<Solution> solutions = new ArrayList<>();
        int failedCnt = 0;
        int noSolutionCnt = 0;
        while (true) {
            if (line == null)
                break;
            boolean on = false;
            switch (line.charAt(0)) {
                case NAME_ON_CHAR:
                    on = true;
                    // falls through
                case NAME_OFF_CHAR:
                    break;
                case SOLUTION_LINE_CHAR:
                    solutions.add(new Solution(line.substring(1)));
                    nextLine(in);
                    continue;
                default:
                    throw new IllegalArgumentException("Unexpected line: " + line);
            }
            String name = line.substring(1);
            nextLine(in);
            if (line == null)
                throw new IllegalArgumentException("Next line expected");
            Constraints constraints = new Constraints();
            while (line.charAt(0) == CONSTRAINT_LINE_CHAR) {
                line = line.substring(1);
                if (startsWithIgnoreCase(CONSTRAINT_MOD)) {
                    constraints.mod = parseConstraintMod(line.substring(CONSTRAINT_MOD.length()));
                } else if (startsWithIgnoreCase(CONSTRAINT_INITCP)) {
                    constraints.initcp = parseInt(line.substring(CONSTRAINT_INITCP.length()));
                } else if (startsWithIgnoreCase(CONSTRAINT_GOALCP)) {
                    constraints.goalcp = parseInt(line.substring(CONSTRAINT_GOALCP.length()));
                } else if (equalsIgnoreCase(CONSTRAINT_RET)) {
                    constraints.ret = true;
                } else if (startsWithIgnoreCase(CONSTRAINT_SIZE)) {
                    constraints.size = parseIntList(line.substring(CONSTRAINT_SIZE.length()));
                } else if (startsWithIgnoreCase(CONSTRAINT_WORLDS)) {
                    constraints.worlds = parseInt(line.substring(CONSTRAINT_WORLDS.length()));
                } else if (startsWithIgnoreCase(CONSTRAINT_USE)) {
                    constraints.use = new Solution(line.substring(CONSTRAINT_USE.length()));
                } else if (equalsIgnoreCase(CONSTRAINT_USE_FIXED)) {
                    constraints.useFixed = true;
                } else if (startsWithIgnoreCase(CONSTRAINT_MIN_PROCS)) {
                    constraints.minProcs = parseInt(line.substring(CONSTRAINT_MIN_PROCS.length()));
                } else if (startsWithIgnoreCase(CONSTRAINT_MAX_PROCS)) {
                    constraints.maxProcs = parseInt(line.substring(CONSTRAINT_MAX_PROCS.length()));
                } else if (startsWithIgnoreCase(CONSTRAINT_MAX_MOVES)) {
                    constraints.maxMoves = parseInt(line.substring(CONSTRAINT_MAX_MOVES.length()));
                } else
                    throw new IllegalArgumentException("Invalid constraints line: " + line);
                nextLine(in);
                if (line == null)
                    throw new IllegalArgumentException("Next line expected");
            }
            List<World> worlds = new ArrayList<>();
            for (int i = 0; i < constraints.worlds; i++) {
                String line1 = line;
                nextLine(in);
                String line2 = line;
                nextLine(in);
                worlds.add(new World(i, constraints.initcp, parseBoard(line1), parseBoard(line2)));
            }
            if (on || all) {
                CargoBotSolver.SOLVER = new CargoBotSolver(name, constraints, worlds);
                switch (action) {
                    case SOLVE:
                        CargoBotSolver.SOLVER.solve();
                        break;
                    case VERIFY:
                        if (solutions.isEmpty()) {
                            System.out.println("!!! NO SOLUTIONS TO VERIFY !!!");
                            noSolutionCnt++;
                        }
                        for (Solution solution : solutions) {
                            if (!CargoBotSolver.SOLVER.verify(solution))
                                failedCnt++;
                        }
                        break;
                }
            }
            solutions.clear();
        }
        System.out.println("===========================================");
        if (failedCnt > 0)
            System.out.println("!!! FAILED TO VERIFY SOLUTIONS: " + failedCnt);
        if (noSolutionCnt > 0)
            System.out.println("!!! NO SOLUTIONS TO VERIFY    : " + noSolutionCnt);
    }

    private boolean equalsIgnoreCase(String s) {
        return line.equalsIgnoreCase(s);
    }

    private boolean startsWithIgnoreCase(String s) {
        return line.length() >= s.length() && line.substring(0, s.length()).equalsIgnoreCase(s);
    }

    private void nextLine(LineNumberReader in) throws IOException {
        while (true) {
            line = in.readLine();
            if (line == null)
                break;
            int j = line.indexOf(COMMENT_CHAR);
            if (j >= 0)
                line = line.substring(0, j);
            line = line.trim();
            if (!line.isEmpty())
                break;
        }
        lineNo = in.getLineNumber();
    }

    private static int parseConstraintMod(String line) {
        int restrict = 0;
        for (int i = 0; i < MOD_STRS.length; i++) {
            int mod = MODS[i];
            if (mod != ALWAYS)
                restrict |= mod;
        }
        loop:
        for (int i = 0; i < line.length(); i++) {
            String c = "" + line.charAt(i);
            for (int j = 0; j < MOD_STRS.length; j++) {
                if (c.equals(MOD_STRS[j])) {
                    restrict &= ~MODS[j];
                    continue loop;
                }
            }
            throw new IllegalArgumentException("Invalid restrictions: " + line);
        }
        return restrict;
    }

    private static int[] parseIntList(String str) {
        String[] ss = str.split("\\s*,\\s*");
        int n = ss.length;
        int[] ints = new int[n];
        for (int i = 0; i < n; i++) {
            ints[i] = parseInt(ss[i]);
        }
        return ints;
    }

    private static int[] parseBoard(String line) {
        String[] s = line.split("\\s+");
        int[] b = new int[s.length];
        for (int i = 0; i < s.length; i++) {
            try {
                b[i] = parseInt(s[i]);
            } catch (NumberFormatException e) {
                b[i] = parseStack(s[i]);
            }
        }
        return b;
    }

    private static int parseStack(String ss) {
        int s = 0;
        for (int i = 0; i < ss.length(); i++) {
            char sc = ss.charAt(i);
            int b;
            if (sc == POISON_CHAR) {
                b = POISON;
            } else {
                b = sc - A_CHAR + A;
                if (b < A || b > MAX_COLORS)
                    throw new IllegalArgumentException("Malformed stack: " + ss);
            }
            s = stackPush(s, b);
        }
        return s;
    }
}
