package solver;

import java.io.*;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.Phaser;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * @author Roman Elizarov.
 */
public class CargoBotSolver {

    // ======================================= main =======================================

    public static void main(String[] args) throws IOException {
        File f = new File(args.length == 0 ? "CargoBotSolver.txt" : args[0]);
        try (FileInputStream in = new FileInputStream(f)) {
            solveFile(in);
        }
    }

    // ======================================= file parser =======================================

    private static void solveFile(InputStream in) throws IOException {
        solveFile(new BufferedReader(new InputStreamReader(in)));
    }

    private static void solveFile(BufferedReader in) throws IOException {
        while (true) {
            String name = nextLine(in);
            if (name == null)
                break;
            boolean on = false;
            switch (name.charAt(0)) {
                case '+':
                    on = true;
                    // falls through
                case '-':
                    break;
                default:
                    throw new IllegalArgumentException("Unexpected line: " + name);
            }
            name = name.substring(1);
            String line = nextLine(in);
            int restrict = 0;
            if (line.startsWith(":")) {
                restrict = parseRestrict(line.substring(1));
                line = nextLine(in);
            }
            int hp = Integer.parseInt(line);
            int[] init = parseBoard(nextLine(in));
            int[] goal = parseBoard(nextLine(in));
            if (!on)
                continue; // turned off
            new CargoBotSolver(name, restrict, hp, init, goal).go();
        }
    }

    private static int parseRestrict(String line) {
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

    private static int[] parseBoard(String line) {
        String[] s = line.split("[ ]+");
        int[] b = new int[s.length];
        for (int i = 0; i < s.length; i++) {
            try {
                b[i] = Integer.parseInt(s[i]);
            } catch (NumberFormatException e) {
                b[i] = parseStack(s[i]);
            }
        }
        return b;
    }

    private static int parseStack(String ss) {
        int s = 0;
        for (int i = 0; i < ss.length(); i++) {
            int b = ss.charAt(i) - 'A' + A;
            if (b < A || b > MAX_COLORS)
                throw new IllegalArgumentException("Malformed stack: " + ss);
            s = stackPush(s, b);
        }
        return s;
    }

    private static String nextLine(BufferedReader in) throws IOException {
        while (true) {
            String line = in.readLine();
            if (line == null)
                return null;
            int j = line.indexOf('#');
            if (j >= 0)
                line = line.substring(0, j);
            line = line.trim();
            if (!line.isEmpty())
                return line;
        }
    }

    // ======================================= constants =======================================

    // General limits
    static final int MAX_HEIGHT = 7;
    static final int MAX_COLORS = 4;
    static final int MAX_PROCS = 4;
    static final int MAX_PROC_LEN = 8;

    // Bit sizes
    static final int BITS_PER_HEIGHT = 3;
    static final int STACK_HEIGHT_MASK = (1 << BITS_PER_HEIGHT) - 1;
    static final int BITS_PER_COLOR = 3;
    static final int STACK_COLOR_MASK = (1 << BITS_PER_COLOR) - 1;

    static final int BITS_PER_SLOT = 3;
    static final int FRAME_SLOT_MASK = (1 << BITS_PER_SLOT) - 1;
    static final int BITS_PER_PROC = 2;
    static final int FRAME_PROC_MASK = (1 << BITS_PER_PROC) - 1;
    static final int BITS_PER_FRAME = BITS_PER_SLOT + BITS_PER_PROC;
    static final long FRAME_FULL_MASK = (1L << BITS_PER_FRAME) - 1;

    static final int MAX_SP = (64 / BITS_PER_FRAME - 1) * BITS_PER_FRAME; // must fit into "long"

    // Colors
    static final int NONE = 0;
    static final int A = 1;
    static final int B = 2;
    static final int C = 3;
    static final int D = 4;

    // Ops
    static final int UNKNOWN = 0;
    static final int DOWN = 1;
    static final int RIGHT = 2;
    static final int LEFT = 3;
    static final int CALL_1 = 4;
    static final int CALL_2 = 5;
    static final int CALL_3 = 6;
    static final int CALL_4 = 7;

    static final int MAX_OP = 8;

    static final int BITS_PER_OP = 3;
    static final int OP_MASK = (1 << BITS_PER_OP) - 1;

    // Mods
    static final int ALWAYS = 0x008;
    static final int WHEN_NONE = 0x010;
    static final int WHEN_ANY = 0x020;
    static final int WHEN_A = 0x040;
    static final int WHEN_B = 0x080;
    static final int WHEN_C = 0x100;
    static final int WHEN_D = 0x200;

    static final int MIN_MOD = ALWAYS;
    static final int MAX_MOD = 0x400;

    static final int BITS_PER_CODE = 10;

    // execute conditions
    static final int EXECUTE_OK = 1;
    static final int EXECUTE_FAIL = 2;
    static final int EXECUTE_UNKNOWN_MOD = 3;
    static final int EXECUTE_UNKNOWN_CODE = 4;

    // ops list / strings
    private static final int[] OPS = { DOWN, RIGHT, LEFT, CALL_1, CALL_2, CALL_3, CALL_4 };
    private static final String[] OP_STRS = { "v", ">", "<", "1", "2", "3", "4" };

    // mods list / strings, (note: MODS has a sentinel value at the end)
    private static final int[] MODS = { ALWAYS, WHEN_NONE, WHEN_ANY, WHEN_A, WHEN_B, WHEN_C, WHEN_D, MAX_MOD };
    private static final String[] MOD_STRS = { " ", "N", "*", "A", "B", "C", "D" };

    // ======================================= instance fields =======================================

    // max colors & mods combos
    private int maxColor = A;
    private int nMods;

    private final int[] execMods;
    private final int[] skipMods;
    private final int[] executeModMatrix = new int[MAX_MOD];

    private final String name;
    private final Pos initPos;
    private final int[] goal;

    final int[] procLen = new int[MAX_PROCS + 1];
    final int[] code = new int[MAX_PROCS * MAX_PROC_LEN];
    
    private final Hash visited = new Hash();

    // stats
    private long[] cntTotal = new long[MAX_PROC_LEN * MAX_PROCS * 4];
    private long[] cntBumpLeft = new long[MAX_PROC_LEN * MAX_PROCS * 4];
    private long[] cntBumpRight = new long[MAX_PROC_LEN * MAX_PROCS * 4];
    private long[] cntTooHigh = new long[MAX_PROC_LEN * MAX_PROCS * 4];
    private long[] cntInfLoop = new long[MAX_PROC_LEN * MAX_PROCS * 4];
    private long[] cntFinished = new long[MAX_PROC_LEN * MAX_PROCS * 4];
    private long[] cntUnreachable = new long[MAX_PROC_LEN * MAX_PROCS * 4];
    private long[] cntRedundant = new long[MAX_PROC_LEN * MAX_PROCS * 4];
    private long[] cntFutile = new long[MAX_PROC_LEN * MAX_PROCS * 4];
    private int maxMovesMade;
    private long totalMovesMade;

    // pruning for unused operations
    private final int[] usedOps = new int[MAX_OP];
    private final int[] shallUseOp = new int[MAX_OP];
    private int shallUseOpSlots;
    private int freeOpSlots;

    // prunning for redundant pairs
    private final boolean redundant[] = new boolean[1 << (2 * BITS_PER_CODE)];

    // progress
    private Phaser progressPhaser;
    private PrintWriter log;

    // ======================================= constructor =======================================

    CargoBotSolver(String name, int restrict, int hp, int[] init, int[] goal) {
        this.name = name;
        log("Solving: %s%n", name);
        if (init.length != goal.length)
            throw new IllegalArgumentException("Inconsistent lengths");
        // init colors
        int[] initCC = convertAndCountColors(init);
        int[] goalCC = convertAndCountColors(goal);
        if (!Arrays.equals(initCC, goalCC))
            throw new IllegalArgumentException("Inconsistent number of colors");
        switch (maxColor) {
            case A: nMods = 3; break;
            case B: nMods = 5; break;
            case C: nMods = 6; break;
            case D: nMods = 7; break;
            default: throw new AssertionError();
        }
        // init execute mod matrix
        execMods = new int[maxColor + 1];
        skipMods = new int[maxColor + 1];
        for (int hh = 0; hh <= maxColor; hh++) {
            for (int i = 0; i < nMods; i++) {
                int mod = MODS[i];
                if ((mod & restrict) != 0)
                    continue;
                if (checkMod(mod, hh))
                    execMods[hh] |= mod;
                else
                    skipMods[hh] |= mod;
            }
        }
        initExecuteModMatrixRec(0, 0);
        // init redundant pairs
        initRedundantMoves();
        initRedundantDowns();
        // init search
        this.goal = goal;
        initPos = new Pos(init, hp, NONE, 0, packFrame(0, 0, 0));
        visited.add(initPos);
    }

    // ======================================= instance methods =======================================

    private int[] convertAndCountColors(int[] ss) {
        int[] cc = new int[MAX_COLORS + 1];
        for (int i = 0; i < ss.length; i++) {
            int s = ss[i];
            int h = stackHeight(s);
            if (h == 0)
                continue;
            if (s == h) {
                // convert to stack of A's
                s = 0;
                for (int k = 0; k < h; k++) {
                    s = stackPush(s, A);
                }
                ss[i] = s;
                cc[A] += h;
            } else {
                // see what colors are used
                for (int k = 0; k < h; k++) {
                    int b = stackTop(s);
                    assert b >= A && b <= MAX_COLORS;
                    maxColor = Math.max(maxColor, b);
                    s = stackPop(s);
                    cc[b]++;
                }
            }
        }
        return cc;
    }

    private void initExecuteModMatrixRec(int i, int mod) {
        if (i < nMods) {
            // exhaustive search for all mod bit combos
            initExecuteModMatrixRec(i + 1, mod);
            initExecuteModMatrixRec(i + 1, mod | MODS[i]);
            return;
        }
        // check all "head holds" states
        for (int hh = 0; hh <= maxColor; hh++) {
            boolean execute = (execMods[hh] & mod) != 0;
            boolean skip = (skipMods[hh] & mod) != 0;
            if (execute && skip)
                executeModMatrix[mod | hh] = EXECUTE_UNKNOWN_MOD;
            else if (execute)
                executeModMatrix[mod | hh] = EXECUTE_OK;
            else if (skip)
                executeModMatrix[mod | hh] = EXECUTE_FAIL;
        }
    }

    private void initRedundantMoves() {
        // will record possible profiles of the pair of conditional moves (3 bits per hand contents)
        int hp0 = 2;
        int bitsPerProfile = 3;
        boolean[] seenProfile = new boolean[1 << (bitsPerProfile * (maxColor + 1))];
        int[] moves = new int[]{RIGHT, LEFT};
        // mark "empty profile" (never does anything) as redundant
        int profile = 0;
        for (int hh = 0; hh <= maxColor; hh++) {
            profile = (profile << bitsPerProfile) + hp0;
        }
        // mark as "seen" all profiles of single cond moves -- all their double moves reproductions are redundant
        for (int modI = 0; modI < nMods; modI++) {
            int mod = MODS[modI];
            for (int move : moves) {
                profile = 0;
                for (int hh = 0; hh <= maxColor; hh++) {
                    int hp = hp0;
                    if (checkMod(mod, hh))
                        hp += move == RIGHT ? 1 : -1;
                    profile = (profile << bitsPerProfile) + hp;
                }
                seenProfile[profile] = true;
            }

        }
        // compute effect of each pairs of mod|move on each possible content of the hand
        for (int mod1i = 0; mod1i < nMods; mod1i++) {
            int mod1 = MODS[mod1i];
            for (int move1 : moves) {
                for (int mod2i = 0; mod2i < nMods; mod2i++) {
                    int mod2 = MODS[mod2i];
                    for (int move2 : moves) {
                        int minEnvelope = hp0;
                        int maxEnvelope = hp0;
                        int minFinish = hp0;
                        int maxFinish = hp0;
                        profile = 0;
                        for (int hh = 0; hh <= maxColor; hh++) {
                            int hp = hp0;
                            if (checkMod(mod1, hh))
                                hp += move1 == RIGHT ? 1 : -1;
                            minEnvelope = Math.min(minEnvelope, hp);
                            maxEnvelope = Math.max(maxEnvelope, hp);
                            if (checkMod(mod2, hh))
                                hp += move2 == RIGHT ? 1 : -1;
                            minEnvelope = Math.min(minEnvelope, hp);
                            maxEnvelope = Math.max(maxEnvelope, hp);
                            minFinish = Math.min(minFinish, hp);
                            maxFinish = Math.max(maxFinish, hp);
                            profile = (profile << bitsPerProfile) + hp;
                        }
                        // record
                        int code1 = mod1 | move1;
                        int code2 = mod2 | move2;
                        int pair = (code1 << BITS_PER_CODE) + code2;
                        // the pair with redundant envelope or the pair whose profile was seen is redundant
                        if (minEnvelope < minFinish || maxEnvelope > maxFinish || seenProfile[profile]) {
                            redundant[pair] = true;
                        } else {
                            // non redundant -- but make others with the same profile as redundant
                            seenProfile[profile] = true;
                        }
                    }
                }
            }
        }
        // sanity checks
        // [ <  >] is redundant, because the same as []
        assert redundant[((ALWAYS | LEFT) << BITS_PER_CODE) + (ALWAYS | RIGHT)];
        // [*> N>] is redundant, because the same as [ >]
        assert redundant[((WHEN_ANY | RIGHT) << BITS_PER_CODE) + (WHEN_NONE | RIGHT)];
        // [ >  >] is not redundant
        assert !redundant[((ALWAYS | RIGHT) << BITS_PER_CODE) + (ALWAYS | RIGHT)];
        // now check all moves with yet undecided conditions --
        // they are redundant when any actual pair that can be decided is redundant
        initRedundantUndecided(moves);
    }

    void initRedundantDowns() {
        // will record "action taken" bit all all pairs of color in hand/color on board
        boolean[] seenProfile = new boolean[1 << ((MAX_COLORS + 1) * (MAX_COLORS + 1))];
        seenProfile[0] = true; // no action taken any time
        // mark as "seen" all profiles of single down moves -- all their double reproductions are redundant
        for (int modI = 0; modI < nMods; modI++) {
            int mod = MODS[modI];
            int profile = 0;
            for (int hh = 0; hh <= maxColor; hh++) {
                if (checkMod(mod, hh)) {
                    if (hh == 0) {
                        // took whatever color on board (if non-empty)
                        for (int b = A; b <= maxColor; b++) {
                            profile |= 1 << (hh * (MAX_COLORS + 1) + b);
                        }
                    } else {
                        // put it on whatever color on top of the stack (even on empty)
                        for (int b = 0; b <= maxColor; b++) {
                            profile |= 1 << (hh * (MAX_COLORS + 1) + b);
                        }
                    }
                }
            }
            seenProfile[profile] = true;
        }
        // compute effect of each pairs of mod|down on each possible content of the hand/board
        for (int mod1i = 0; mod1i < nMods; mod1i++) {
            int mod1 = MODS[mod1i];
            for (int mod2i = 0; mod2i < nMods; mod2i++) {
                int mod2 = MODS[mod2i];
                int profile = 0;
                for (int hh = 0; hh <= maxColor; hh++) {
                    for (int b = 0; b <= maxColor; b++) {
                        int hh1 = hh;
                        int b1 = b;
                        int sSize = 0;
                        // first DOWN op
                        if (checkMod(mod1, hh1)) {
                            if (hh1 == 0) {
                                hh1 = b1;
                                if (b1 != 0)
                                    sSize -= 1;
                            } else {
                                b1 = hh1;
                                hh1 = 0;
                                sSize += 1;
                            }
                        }
                        // second DOWN op
                        int hh2 = hh1;
                        int b2 = b1;
                        if (checkMod(mod2, hh2)) {
                            if (hh2 == 0) {
                                //hh2 = b2;
                                if (b2 != 0)
                                    sSize -= 1;
                            } else {
                                //b2 = hh2;
                                //hh2 = 0;
                                sSize += 1;
                            }
                        }
                        if (sSize != 0) {
                            profile |= 1 << (hh * (MAX_COLORS + 1) + b);
                        }
                    }
                }
                int code1 = mod1 | DOWN;
                int code2 = mod2 | DOWN;
                int pair = (code1 << BITS_PER_CODE) + code2;
                if (seenProfile[profile]) {
                    redundant[pair] = true;
                } else {
                    seenProfile[profile] = true;
                }
            }
        }
        // sanity checks
        //      [ v Nv] is equal to [Nv]
        assert redundant[((ALWAYS | DOWN) << BITS_PER_CODE) + (WHEN_NONE | DOWN)];
        //      [*v Nv] is equal to [Nv]
        assert redundant[((WHEN_ANY | DOWN) << BITS_PER_CODE) + (WHEN_NONE | DOWN)];
        //      [*v *v] is equal to [*v]
        assert redundant[((WHEN_ANY | DOWN) << BITS_PER_CODE) + (WHEN_ANY | DOWN)];
        //      [Nv *v] is equal to [*v]
        assert redundant[((WHEN_NONE | DOWN) << BITS_PER_CODE) + (WHEN_ANY | DOWN)];
        //      [Nv  v] is equal to [*v]
        assert redundant[((WHEN_NONE | DOWN) << BITS_PER_CODE) + (ALWAYS | DOWN)];
        // now check all pairs of DOWNs with yet undecided conditions --
        // they are redundant when any actual pair that can be decided is redundant
        initRedundantUndecided(new int[] { DOWN });
    }

    private void initRedundantUndecided(int[] ops) {
        for (int mod1mix = MIN_MOD; mod1mix < MODS[nMods]; mod1mix += MIN_MOD) {
            for (int op1 : ops) {
                for (int mod2mix = MIN_MOD; mod2mix < MODS[nMods]; mod2mix += MIN_MOD) {
                    for (int op2 : ops) {
                        boolean allRedundant = true;
                    check_loop:
                        for (int mod1i = 0; mod1i < nMods; mod1i++) {
                            int mod1 = MODS[mod1i];
                            if ((mod1 & mod1mix) != 0) {
                                for (int mod2i = 0; mod2i < nMods; mod2i++) {
                                    int mod2 = MODS[mod2i];
                                    if ((mod2 & mod2mix) != 0) {
                                        int code1 = mod1 | op1;
                                        int code2 = mod2 | op2;
                                        int pair = (code1 << BITS_PER_CODE) + code2;
                                        if (!redundant[pair]) {
                                            allRedundant = false;
                                            break check_loop;
                                        }
                                    }
                                }
                            }
                        }
                        // finished checking all concrete mods in there
                        if (allRedundant) {
                            int code1 = mod1mix | op1;
                            int code2 = mod2mix | op2;
                            int pair = (code1 << BITS_PER_CODE) + code2;
                            redundant[pair] = true;
                        }
                    }
                }
            }
        }
    }

    boolean simulateCodeInit() {
        initPruning();
        if (progressPhaser != null)
            progressPhaser.arriveAndAwaitAdvance();
        boolean result = simulateCode0(0, initPos);
        if (progressPhaser != null)
            progressPhaser.arriveAndAwaitAdvance();
        return result;
    }

    private void initPruning() {
        Arrays.fill(usedOps, 0);
        Arrays.fill(shallUseOp, 0);
        if (IntStream.of(initPos.b).map(CargoBotSolver::stackHeight).sum() > 1) {
            shallUseOp[LEFT] = 1;
            shallUseOp[RIGHT] = 1;
        }
        shallUseOp[DOWN] = 1;
        for (int i = 1; i < MAX_PROCS; i++) {
            if (procLen[i] != 0)
                shallUseOp[CALL_1 + i] = 1;
        }
        shallUseOpSlots = IntStream.of(shallUseOp).sum();
        freeOpSlots = 0;
        for (int pi = 0; pi < MAX_PROCS; pi++) {
            for (int ii = 0; ii < procLen[pi]; ii++) {
                if (code[pi * MAX_PROC_LEN + ii] == UNKNOWN)
                    freeOpSlots++;
            }
        }
    }

    // invariant -- pos is added to visited (needs to be copied before modification)
    private boolean simulateCode0(int depth, Pos start) {
        depth++;
        cntTotal[depth]++; // track attempts at each depth
        Pos pos = start;
        sim_loop:
        while (true) {
            // here pos was added to hash -- no longer can no longer release it -- allocate a copy
            int pi = unpackProc(pos.sp, pos.cs); // proc index
            int ii = unpackSlot(pos.sp, pos.cs); // instr index
            int c = code[pi * MAX_PROC_LEN + ii];
            if (c == UNKNOWN) {
                if (searchCode0(depth, pos, pi, ii))
                    return true;
                break;
            }
            // now actually (try to) execute code
            Pos copy = visited.allocCopy(pos); // <-- ALLOCATED HERE
            switch (executeOneStep(depth, copy, pi, ii, c)) {
                case EXECUTE_FAIL:
                    visited.release(copy);
                    break sim_loop;
                case EXECUTE_OK:
                    break; // continue execution in loop
                case EXECUTE_UNKNOWN_MOD:
                    visited.release(copy);
                    if (searchMod(depth, pos, pi, ii))
                        return true;
                    break sim_loop;
                case EXECUTE_UNKNOWN_CODE:
                    visited.release(copy);
                    if (searchOp(depth, pos, pi, ii, (c & ~OP_MASK)))
                        return true;
                    break sim_loop;
            }
            totalMovesMade++;
            maxMovesMade = Math.max(maxMovesMade, visited.size);
            if (Arrays.equals(copy.b, goal))
                return true; // found solution
            if (copy.sp < 0) {
                cntFinished[depth]++;
                visited.release(copy);
                break; // return from top proc
            }
            if (!visited.add(copy)) {
                cntInfLoop[depth]++;
                visited.release(copy);
                break; // repeated
            }
            pos = copy;  // <-- ADDED TO VISITED
        }
        visited.removeUntil(start);
        return false;
    }

    // pos is a fresh copy here (can modify)
    private int executeOneStep(int depth, Pos pos, int pi, int ii, int c) {
        int mod = c & ~OP_MASK;
        switch (executeModMatrix[mod | pos.hh]) {
            case EXECUTE_OK:
                // execute this op (condition is true)
                break;
            case EXECUTE_FAIL:
                // skip this op (condition is false)
                next(pos, pi, ii);
                return EXECUTE_OK;
            case EXECUTE_UNKNOWN_MOD:
                return EXECUTE_UNKNOWN_MOD;
            default:
                throw new AssertionError();
        }
        int op = c & OP_MASK;
        switch (op) {
            case DOWN:
                int s = pos.b[pos.hp];
                if (pos.hh != NONE) {
                    pos.b[pos.hp] = stackPush(s, pos.hh);
                    pos.hh = NONE;
                } else if (stackHeight(s) > 0) {
                    pos.hh = stackTop(s);
                    pos.b[pos.hp] = stackPop(s);
                }
                next(pos, pi, ii);
                return EXECUTE_OK;
            case RIGHT:
                if (pos.hp >= pos.b.length - 1) {
                    cntBumpRight[depth]++;
                    return EXECUTE_FAIL;
                }
                if (stackHeight(pos.b[pos.hp]) >= MAX_HEIGHT) {
                    cntTooHigh[depth]++;
                    return EXECUTE_FAIL; // cannot move when stack at the max height
                }
                pos.hp++;
                next(pos, pi, ii);
                return EXECUTE_OK;
            case LEFT:
                if (pos.hp <= 0) {
                    cntBumpLeft[depth]++;
                    return EXECUTE_FAIL;
                }
                if (stackHeight(pos.b[pos.hp]) >= MAX_HEIGHT) {
                    cntTooHigh[depth]++;
                    return EXECUTE_FAIL; // cannot move when stack at the max height
                }
                pos.hp--;
                next(pos, pi, ii);
                return EXECUTE_OK;
            case UNKNOWN:
                return EXECUTE_UNKNOWN_CODE;
            // bail out for all the calls
        }
        // calls
        int ci = op - CALL_1;
        assert ci >= 0;
        if (ii == procLen[pi] - 1) {
            // any tail call is done "in-place"
        } else {
            // other call -- will return to next instruction (note -- it was not a tail call!)
            pos.cs = clearFrame(pos.sp, pos.cs) | packFrame(pos.sp, pi, ii + 1);
            if (pos.sp < MAX_SP) {
                pos.sp += BITS_PER_FRAME;
            } else {
                // shift stack on overflow (forget oldest)
                pos.cs = pos.cs >>> BITS_PER_FRAME;
            }
        }
        // record new proc & instruction in the current activation record
        pos.cs = clearFrame(pos.sp, pos.cs) | packFrame(pos.sp, ci, 0);
        return EXECUTE_OK;
    }

    private void next(Pos pos, int pi, int ii) {
        // instr index++
        if (++ii >= procLen[pi]) {
            // return
            pos.cs = clearFrame(pos.sp, pos.cs);
            pos.sp -= BITS_PER_FRAME;
        } else {
            // update frame
            pos.cs = clearFrame(pos.sp, pos.cs) | packFrame(pos.sp, pi, ii);
        }
    }

    // invariant -- pos is added to visited (needs to be copied before modification)
    private boolean searchCode0(int depth, Pos pos, int pi, int ii) {
        assert code[pi * MAX_PROC_LEN + ii] == UNKNOWN;
        // try to execute something (don't commit to exact condition)
        if (searchOp(depth, pos, pi, ii, execMods[pos.hh]))
            return true;
        // try to skip instruction (don't commit what instruction yet)
        int mod = skipMods[pos.hh];
        if (mod != 0) { // if conds can be used
            code[pi * MAX_PROC_LEN + ii] = mod | UNKNOWN;
            if (simulateCode0(depth, pos))
                return true;
        }
        code[pi * MAX_PROC_LEN + ii] = UNKNOWN;
        return false;
    }

    private boolean searchOp(int depth, Pos pos, int pi, int ii, int mod) {
        for (int op : OPS) {
            int ci = op - CALL_1; // "calls to" index
            if (ci > 0 && procLen[ci] == 0)
                break; // call to absent proc -- don't try & break loop
            if (ci > 1 && usedOps[ci - 1] == 0)
                break; // must first call to PROC_2, then PROC_3, etc
            if (usedOps[op]++ < shallUseOp[op])
                shallUseOpSlots--;
            freeOpSlots--;
            if (freeOpSlots >= shallUseOpSlots) {
                if (verifyAndSimulateCode(depth, pos, pi, ii, mod, op))
                    return true;
            } else {
                cntFutile[depth + 1]++;
            }
            if (--usedOps[op] < shallUseOp[op])
                shallUseOpSlots++;
            freeOpSlots++;
        }
        code[pi * MAX_PROC_LEN + ii] = mod | UNKNOWN;
        return false;
    }

    // invariant -- pos is added to visited (needs to be copied before modification)
    private boolean searchMod(int depth, Pos pos, int pi, int ii) {
        int c = code[pi * MAX_PROC_LEN + ii];
        int mod = c & ~OP_MASK;
        int op = c & OP_MASK;
        // assume executed
        if (verifyAndSimulateCode(depth, pos, pi, ii, mod & execMods[pos.hh], op))
            return true;
        // assume skipped
        if (verifyAndSimulateCode(depth, pos, pi, ii, mod & skipMods[pos.hh], op))
            return true;
        code[pi * MAX_PROC_LEN + ii] = c;
        return false;
    }

    private boolean verifyAndSimulateCode(int depth, Pos pos, int pi, int ii, int mod, int op) {
        if (mod == ALWAYS && op == CALL_1 + pi && ii < procLen[pi] - 1) {
            cntUnreachable[depth + 1]++;
            return false; // unconditional self call -- code after this one is unreachable -- don't try
        }
        int c = mod | op;
        if (ii > 0 && redundant[(code[pi * MAX_PROC_LEN + ii - 1] << BITS_PER_CODE) + c]) {
            cntRedundant[depth + 1]++;
            return false; // redundant pair
        }
        code[pi * MAX_PROC_LEN + ii] = c;
        return simulateCode0(depth, pos);
    }

    private String shape2Str() {
        return String.format(Locale.US, "%d = [%s]",
                IntStream.of(procLen).sum(),
                IntStream.of(procLen).
                        filter(len -> len != 0)
                        .mapToObj(cl -> "" + cl)
                        .collect(Collectors.joining(", ")));
    }

    private boolean searchShape(int i, int slots) {
        if (slots == 0) {
            log("%s - Searching for solutions of size %s%n", time(), shape2Str());
            return simulateCodeInit();
        }
        if (i >= MAX_PROCS)
            return false;
        int limit = Math.min(slots, MAX_PROC_LEN);
        for (int cl = limit; cl >= 2; cl--) {
            procLen[i] = cl;
            if (searchShape(i + 1, slots - cl))
                return true;
            procLen[i] = 0;
        }
        return false;
    }

    private String time() {
        return new SimpleDateFormat("yyyyMMdd-HHmmss.sss").format(new Date());
    }

    String code2Str(int[] code, boolean pickOne) {
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
                sb.append(mod2Str(c, pickOne)).append(op2Str(c));
            }
            sb.append("]");
        }
        return sb.toString();
    }

    private void go() throws IOException {
        progressPhaser = new Phaser(2);
        ProgressPrinter progressPrinter = new ProgressPrinter();
        progressPrinter.start();
        openLog();
        log("%s ===================================================%n", time());
        long time = System.currentTimeMillis();
        for (int slots = 3;; slots++) {
            if (searchShape(0, slots)) {
                long timeTotal = System.currentTimeMillis() - time;
                log("%s ---------------------------------------------------%n", time());
                log("Found solution for %s in %,d ms%n", name, timeTotal);
                log("Solution of size %s%n", shape2Str());
                log("%s%n", code2Str(code, true));
                log("This solution makes %,d moves%n", visited.size);
                log("Made max %,d moves while searching, total %,d moves%n", maxMovesMade, totalMovesMade);
                log("Analyzed combinations and encountered backtracking reasons:%n");
                logStats("Total      ", cntTotal);
                logStats("Bump Left  ", cntBumpLeft);
                logStats("Bump Right ", cntBumpRight);
                logStats("Too High   ", cntTooHigh);
                logStats("Inf Loop   ", cntInfLoop);
                logStats("Finished   ", cntFinished);
                logStats("Unreachable", cntUnreachable);
                logStats("Redundant  ", cntRedundant);
                logStats("Futile     ", cntFutile);
                break;
            } else {
                long timeTotal = System.currentTimeMillis() - time;
                log("%s - Analyzed solutions up to size %d in %,d ms%n", time(), slots, timeTotal);
            }
        }
        closeLog();
        progressPrinter.interrupt();
    }

    private void logStats(String stat, long[] cnt) {
        int maxD = 0;
        long maxDCnt = 0;
        int maxDIndex = 0;
        long sum = 0;
        StringBuilder sb = new StringBuilder();
        for (int depth = 1; depth < cnt.length; depth++) {
            long t = cnt[depth];
            sum += t;
            if (t > maxDCnt) {
                maxDCnt = t;
                maxDIndex = depth;
            }
            if (t != 0) {
                maxD = depth;
            }
            if (depth <= 20) {
                sb.append(String.format(Locale.US, " %,d", t));
            }
        }
        log("%s : %,13d at depth up to %2d, max %,13d at depth %2d, at first depths:%s%n",
                stat, sum, maxD, maxDCnt, maxDIndex, sb);
    }

    private void log(String fmt, Object... args) {
        System.out.printf(Locale.US, fmt, args);
        if (log != null)
            log.printf(Locale.US, fmt, args);
    }

    private void openLog() throws IOException {
        File dir = new File("logs");
        dir.mkdirs();
        File file = new File(dir, name + ".log");
        log = new PrintWriter(new FileWriter(file, true));
    }

    private void closeLog() {
        log.close();
        log = null;
    }

    // ======================================= static methods =======================================

    static boolean checkMod(int mod, int hh) {
        switch (mod) {
            case ALWAYS: return true;
            case WHEN_NONE: return hh == NONE;
            case WHEN_A: return hh == A;
            case WHEN_B: return hh == B;
            case WHEN_C: return hh == C;
            case WHEN_D: return hh == D;
            case WHEN_ANY: return hh != NONE;
            default: throw new AssertionError();
        }
    }

    static String mod2Str(int c, boolean pickOne) {
        int mod = c & ~OP_MASK;
        if (mod == 0)
            return "_";
        for (int i = 0; i < MODS.length; i++) {
            if (mod == MODS[i] || pickOne && (mod & MODS[i]) != 0) {
                return MOD_STRS[i];
            }
        }
        return "?";
    }

    static String op2Str(int c) {
        int op = c & OP_MASK;
        if (op == UNKNOWN)
            return "_";
        for (int i = 0; i < OPS.length; i++) {
            if (op == OPS[i]) {
                return OP_STRS[i];
            }
        }
        return "?";
    }

    static int stackHeight(int s) {
        return s & STACK_HEIGHT_MASK;
    }

    static int stackTop(int s) {
        return (s >> BITS_PER_HEIGHT) & STACK_COLOR_MASK;
    }

    static int stackPush(int s, int b) {
        int h = stackHeight(s);
        assert h < MAX_HEIGHT;
        return (h + 1) | ((s & ~STACK_HEIGHT_MASK) << BITS_PER_COLOR) | (b << BITS_PER_HEIGHT);
    }

    static int stackPop(int s) {
        int h = stackHeight(s);
        assert h > 0;
        return (h - 1) | ((s >> BITS_PER_COLOR) & ~STACK_HEIGHT_MASK);
    }

    static long packFrame(int sp, int proc, int slot) {
        return (((long)proc << BITS_PER_SLOT) | slot) << sp;
    }

    static long clearFrame(int sp, long frame) {
        return frame & ~(FRAME_FULL_MASK << sp);
    }

    static int unpackProc(int sp, long frame) {
        return (int)((frame >>> (sp + BITS_PER_SLOT)) & FRAME_PROC_MASK);
    }

    static int unpackSlot(int sp, long frame) {
        return (int)((frame >>> sp) & FRAME_SLOT_MASK);
    }

    // ======================================= helper classes =======================================

    static class Pos {
        int[] b;      // board
        int hp;       // hand pointer
        int hh;       // hand holds
        int sp;       // stack pointer
        long cs;      // call stack

        int hIndex;   // index in hash
        Pos hNext;    // pool in hash

        Pos(int bs) {
            b = new int[bs];
        }

        Pos(int[] b, int hp, int hh, int sp, long cs) {
            this.b = b;
            this.hp = hp;
            this.hh = hh;
            this.sp = sp;
            this.cs = cs;
        }

        void assign(Pos pos) {
            System.arraycopy(pos.b, 0, b, 0, pos.b.length);
            this.hp = pos.hp;
            this.hh = pos.hh;
            this.sp = pos.sp;
            this.cs = pos.cs;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Pos pos = (Pos) o;
            return hp == pos.hp && hh == pos.hh && sp == pos.sp && cs == pos.cs && Arrays.equals(b, pos.b);
        }

        @Override
        public int hashCode() {
            int result = Arrays.hashCode(b);
            result = 31 * result + hp;
            result = 31 * result + hh;
            result = 31 * result + sp;
            result = 31 * result + Long.hashCode(cs);
            return result;
        }
    }

    static class Hash {
        static final int MAGIC = 0x9E3779B9;
        static final int START_BITS = 8;

        Pos[] a = new Pos[1 << START_BITS];
        int shift = 32 - START_BITS;
        int size;
        int limit = limit(1 << START_BITS);

        Pos free;
        Pos last;

        Pos allocCopy(Pos pos) {
            Pos res;
            if (free != null) {
                res = free;
                free = res.hNext;
                res.hNext = null;
            } else
                res = new Pos(pos.b.length);
            res.assign(pos);
            return res;
        }

        void release(Pos pos) {
            pos.hNext = free;
            free = pos;
        }

        void removeUntil(Pos stop) {
            Pos pos = last;
            while (pos != stop) {
                Pos next = pos.hNext;
                a[pos.hIndex] = null;
                release(pos);
                pos = next;
                size--;
            }
            last = stop;
        }

        boolean add(Pos pos) {
            if (size >= limit)
                resize();
            if (!addImpl(pos))
                return false;
            size++;
            pos.hNext = last;
            last = pos;
            return true;
        }

        boolean addImpl(Pos pos) {
            int h = (pos.hashCode() * MAGIC) >>> shift;
            while (true) {
                Pos q = a[h];
                if (q == null) {
                    pos.hIndex = h;
                    a[h] = pos;
                    return true;
                }
                if (q.equals(pos))
                    return false;
                if (h == 0)
                    h = a.length;
                h--;
            }
        }

        private int limit(int len) {
            return len * 2 / 3;
        }

        private void resize() {
            Pos[] old = a;
            int len = old.length * 2;
            a = new Pos[len];
            limit = limit(len);
            shift--;
            for (Pos pos : old) {
                if (pos != null) {
                    boolean success = addImpl(pos);
                    assert success;
                }
            }
        }
    }

    class ProgressPrinter extends Thread {
        private int[] codeCopy = new int[MAX_PROCS * MAX_PROC_LEN];

        public ProgressPrinter() {
            setDaemon(true);
        }

        @Override
        public void run() {
            try {
                while (true) {
                    progressPhaser.arriveAndAwaitAdvance();
                    showProgress(progressPhaser.arrive());
                }
            } catch (InterruptedException e) {
                // exit
            }
        }

        private void showProgress(int phase) throws InterruptedException {
            while (true) {
                try {
                    progressPhaser.awaitAdvanceInterruptibly(phase, 10, TimeUnit.SECONDS);
                    return; // phase done
                } catch (TimeoutException e) {
                    // print & continue
                }
                System.arraycopy(code, 0, codeCopy, 0, codeCopy.length);
                log("%s - working on %s%n", time(), code2Str(codeCopy, false));
            }
        }
    }
}
