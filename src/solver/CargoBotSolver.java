package solver;

import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.Phaser;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static solver.Constants.*;
import static solver.Util.*;

/**
 * @author Roman Elizarov.
 */
public class CargoBotSolver extends Solution {
    public static final String DEFAULT_FILE_NAME = "CargoBotSolver.txt";

    // ======================================= main =======================================

    enum Action { SOLVE, VERIFY }

    static volatile CargoBotSolver SOLVER; // currently operating one

    public static void main(String[] args) throws IOException {
        if (args.length == 0 || args.length > 3) {
            help();
            return;
        }
        Action action = Action.valueOf(args[0].toUpperCase(Locale.US));
        boolean all = args.length > 1 && args[1].toUpperCase(Locale.US).equals("ALL");
        int fi = all ? 2 : 1;
        File f = new File(fi < args.length ? args[fi] : DEFAULT_FILE_NAME);
        if (action == Action.SOLVE)
            new Console().start();
        try (FileInputStream in = new FileInputStream(f)) {
            new Parser(action, all).processFile(in);
        }
    }

    private static void help() {
        System.out.println("Usage: java " + CargoBotSolver.class.getName() + " (solve|verify) [all] [<file>]");
    }

    // ======================================= instance fields =======================================

    // max colors & mods combos
    private final int maxColor;
    private final int nMods;

    private final int[] execMods;
    private final int[] skipMods;
    private final int[] executeModMatrix = new int[MAX_MOD];

    private final String name;
    private final Constraints constraints;
    private final World[] worlds; // many worlds in simulation

    // stats
    private long[] cntTotal = new long[MAX_PROC_LEN * MAX_PROCS * 4];
    private long[] cntBumpLeft = new long[MAX_PROC_LEN * MAX_PROCS * 4];
    private long[] cntBumpRight = new long[MAX_PROC_LEN * MAX_PROCS * 4];
    private long[] cntTooHigh = new long[MAX_PROC_LEN * MAX_PROCS * 4];
    private long[] cntInfLoop = new long[MAX_PROC_LEN * MAX_PROCS * 4];
    private long[] cntReturns = new long[MAX_PROC_LEN * MAX_PROCS * 4];
    private long[] cntUnreachable = new long[MAX_PROC_LEN * MAX_PROCS * 4];
    private long[] cntRedundant = new long[MAX_PROC_LEN * MAX_PROCS * 4];
    private long[] cntFutile = new long[MAX_PROC_LEN * MAX_PROCS * 4];
    private long[] cntPoison = new long[MAX_PROC_LEN * MAX_PROCS * 4];
    private long[] cntStackOver = new long[MAX_PROC_LEN * MAX_PROCS * 4];
    private long[] cntTooManyMoves = new long[MAX_PROC_LEN * MAX_PROCS * 4];
    private long totalMovesMade;
    private int maxMovesMade;
    private final Solution maxMovesSolution = new Solution();

    // pruning for unused operations
    private final int[] usedOps = new int[MAX_OP];
    private final int[] shallUseOp = new int[MAX_OP];
    private int shallUseOpSlots;
    private int freeOpSlots;

    // pruning for redundant pairs
    private final boolean redundant[] = new boolean[1 << (2 * BITS_PER_CODE)];

    // progress
    private Phaser progressPhaser;
    private PrintWriter log;

    // ======================================= constructor =======================================

    CargoBotSolver(String name, Constraints constraints, List<World> worlds) {
        this.name = name;
        this.constraints = constraints;
        log("Name: %s%n", name);
        // find max color
        maxColor = worlds.stream().mapToInt(World::maxColor).max().getAsInt();
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
        initExecuteModMatrix();
        // init redundant pairs
        initRedundantMoves();
        initRedundantDowns();
        // init search worlds
        this.worlds = worlds.toArray(new World[worlds.size()]);
    }

    // ======================================= instance methods =======================================

    private void initExecuteModMatrix() {
        for (int ch = 0; ch <= maxColor; ch++) {
            executeModMatrix[ch] = EXECUTE_UNKNOWN_MOD;
            for (int i = 0; i < nMods; i++) {
                int mod = MODS[i];
                if ((mod & constraints.mod) != 0)
                    continue;
                if (checkMod(mod, ch))
                    execMods[ch] |= mod;
                else
                    skipMods[ch] |= mod;
            }
        }
        initExecuteModMatrixRec(0, 0);
    }

    private void initExecuteModMatrixRec(int i, int mod) {
        if (i < nMods) {
            // exhaustive search for all mod bit combos
            initExecuteModMatrixRec(i + 1, mod);
            initExecuteModMatrixRec(i + 1, mod | MODS[i]);
            return;
        }
        // check all "head holds" states
        for (int ch = 0; ch <= maxColor; ch++) {
            boolean execute = (execMods[ch] & mod) != 0;
            boolean skip = (skipMods[ch] & mod) != 0;
            if (execute && skip)
                executeModMatrix[mod | ch] = EXECUTE_UNKNOWN_MOD;
            else if (execute)
                executeModMatrix[mod | ch] = EXECUTE_OK;
            else if (skip)
                executeModMatrix[mod | ch] = EXECUTE_FAIL;
        }
    }

    private void initRedundantMoves() {
        // will record possible profiles of the pair of conditional moves (3 bits per hand contents)
        int cp0 = 2;
        int bitsPerProfile = 3;
        boolean[] seenProfile = new boolean[1 << (bitsPerProfile * (maxColor + 1))];
        int[] moves = new int[]{RIGHT, LEFT};
        // mark "empty profile" (never does anything) as redundant
        int profile = 0;
        for (int ch = 0; ch <= maxColor; ch++) {
            profile = (profile << bitsPerProfile) + cp0;
        }
        // mark as "seen" all profiles of single cond moves -- all their double moves reproductions are redundant
        for (int modI = 0; modI < nMods; modI++) {
            int mod = MODS[modI];
            for (int move : moves) {
                profile = 0;
                for (int ch = 0; ch <= maxColor; ch++) {
                    int cp = cp0;
                    if (checkMod(mod, ch))
                        cp += move == RIGHT ? 1 : -1;
                    profile = (profile << bitsPerProfile) + cp;
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
                        int minEnvelope = cp0;
                        int maxEnvelope = cp0;
                        int minFinish = cp0;
                        int maxFinish = cp0;
                        profile = 0;
                        for (int ch = 0; ch <= maxColor; ch++) {
                            int cp = cp0;
                            if (checkMod(mod1, ch))
                                cp += move1 == RIGHT ? 1 : -1;
                            minEnvelope = Math.min(minEnvelope, cp);
                            maxEnvelope = Math.max(maxEnvelope, cp);
                            if (checkMod(mod2, ch))
                                cp += move2 == RIGHT ? 1 : -1;
                            minEnvelope = Math.min(minEnvelope, cp);
                            maxEnvelope = Math.max(maxEnvelope, cp);
                            minFinish = Math.min(minFinish, cp);
                            maxFinish = Math.max(maxFinish, cp);
                            profile = (profile << bitsPerProfile) + cp;
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
            for (int ch = 0; ch <= maxColor; ch++) {
                if (checkMod(mod, ch)) {
                    if (ch == 0) {
                        // took whatever color on board (if non-empty)
                        for (int b = A; b <= maxColor; b++) {
                            profile |= 1 << (ch * (MAX_COLORS + 1) + b);
                        }
                    } else {
                        // put it on whatever color on top of the stack (even on empty)
                        for (int b = 0; b <= maxColor; b++) {
                            profile |= 1 << (ch * (MAX_COLORS + 1) + b);
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
                for (int ch = 0; ch <= maxColor; ch++) {
                    for (int b = 0; b <= maxColor; b++) {
                        int hh1 = ch;
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
                            profile |= 1 << (ch * (MAX_COLORS + 1) + b);
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
        boolean result = simulateCode0(0, worlds[0]);
        if (progressPhaser != null)
            progressPhaser.arriveAndAwaitAdvance();
        return result;
    }

    private void initPruning() {
        Arrays.fill(usedOps, 0);
        Arrays.fill(shallUseOp, 0);
        if (Stream.of(worlds).anyMatch(World::needsBothMoves)) {
            shallUseOp[LEFT] = 1;
            shallUseOp[RIGHT] = 1;
        }
        shallUseOp[DOWN] = 1;
        for (int i = 1; i < MAX_PROCS; i++) {
            if (procLen[i] != 0)
                shallUseOp[CALL_1 + i] = 1;
        }
        freeOpSlots = 0;
        for (int pi = 0; pi < MAX_PROCS; pi++) {
            for (int ii = 0; ii < procLen[pi]; ii++) {
                int op = code[pi * MAX_PROC_LEN + ii] & OP_MASK;
                if (op == UNKNOWN)
                    freeOpSlots++;
                else
                    usedOps[op]++;
            }
        }
        shallUseOpSlots = 0;
        for (int i = 0; i < MAX_PROC_LEN; i++) {
            if (shallUseOp[i] > usedOps[i])
                shallUseOpSlots += shallUseOp[i] - usedOps[i];
        }
    }

    private boolean simulateCode0(int depth, World world) {
        cntTotal[depth]++; // track attempts at each depth
        world.save(depth);
    sim_loop:
        while (true) {
            Pos pos = world.pos;
            int pi = unpackProc(pos.sp, pos.cs); // proc index
            int ii = unpackSlot(pos.sp, pos.cs); // instr index
            int c = code[pi * MAX_PROC_LEN + ii];
            switch (executeOneStep(world, pos, pi, ii, c)) {
                case EXECUTE_GOAL:
                    // goal in this world -- check next world (if any)
                    if (world.index >= worlds.length - 1)
                        return true; // GOAL IN ALL WORLDS - we are done!
                    if (simulateCode0(depth, worlds[world.index + 1]))
                        return true; // GOAL IN ALL WORLDS - we are done!
                    break sim_loop; // oops, not -- cleanup & continue looking
                case EXECUTE_OK:
                    break; // continue execution in loop
                case EXECUTE_UNKNOWN0:
                    if (searchCode0(depth, world, pos, pi, ii, c))
                        return true;
                    break sim_loop;
                case EXECUTE_UNKNOWN_MOD:
                    if (searchMod(depth, world, pos, pi, ii, c))
                        return true;
                    break sim_loop;
                case EXECUTE_UNKNOWN_CODE:
                    if (searchOp(depth, world, 0, pi, ii, c))
                        return true;
                    break sim_loop;
                case EXECUTE_FAIL_BUMP_LEFT:
                    cntBumpLeft[depth]++;
                    break sim_loop;
                case EXECUTE_FAIL_BUMP_RIGHT:
                    cntBumpRight[depth]++;
                    break sim_loop;
                case EXECUTE_FAIL_TOO_HIGH:
                    cntTooHigh[depth]++;
                    break sim_loop;
                case EXECUTE_FAIL_INF_LOOP:
                    cntInfLoop[depth]++;
                    break sim_loop;
                case EXECUTE_FAIL_RETURNS:
                    cntReturns[depth]++;
                    break sim_loop;
                case EXECUTE_FAIL_POISON:
                    cntPoison[depth]++;
                    break sim_loop;
                case EXECUTE_FAIL_STACK_OVER:
                    cntStackOver[depth]++;
                    break sim_loop;
                default:
                    throw new AssertionError();
            }
            totalMovesMade++;
            if (world.movesMade() > constraints.maxMoves) {
                cntTooManyMoves[depth]++;
                break;
            }
        }
        if (world.movesMade() > maxMovesMade) {
            maxMovesMade = world.movesMade();
            maxMovesSolution.assign(this);
        }
        world.rollback(depth);
        return false;
    }

    private int executeOneStep(World world, Pos pos, int pi, int ii, int c) {
        if (c == UNKNOWN)
            return EXECUTE_UNKNOWN0;
        int mod = c & ~OP_MASK;
        switch (executeModMatrix[mod | pos.ch]) {
            case EXECUTE_OK:
                // execute this op (condition is true)
                break;
            case EXECUTE_FAIL:
                // skip this op (condition is false)
                return next(world, pi, ii);
            case EXECUTE_UNKNOWN_MOD:
                return EXECUTE_UNKNOWN_MOD;
            default:
                throw new AssertionError();
        }
        int op = c & OP_MASK;
        switch (op) {
            case DOWN:
                int s = pos.b[pos.cp];
                if (pos.ch != NONE) {
                    pos = world.copyPos();
                    pos.b[pos.cp] = stackPush(s, pos.ch);
                    pos.ch = NONE;
                } else if (stackHeight(s) > 0) {
                    int ch = stackTop(s);
                    if (ch == POISON) {
                        return EXECUTE_FAIL_POISON;
                    }
                    pos = world.copyPos();
                    pos.ch = ch;
                    pos.b[pos.cp] = stackPop(s);
                }
                return next(world, pi, ii);
            case RIGHT:
                if (pos.cp >= pos.b.length - 1) {
                    return EXECUTE_FAIL_BUMP_RIGHT;
                }
                if (stackHeight(pos.b[pos.cp]) >= MAX_HEIGHT) {
                    return EXECUTE_FAIL_TOO_HIGH; // cannot move when stack at the max height
                }
                pos = world.copyPos();
                pos.cp++;
                return next(world, pi, ii);
            case LEFT:
                if (pos.cp <= 0) {
                    return EXECUTE_FAIL_BUMP_LEFT;
                }
                if (stackHeight(pos.b[pos.cp]) >= MAX_HEIGHT) {
                    return EXECUTE_FAIL_TOO_HIGH; // cannot move when stack at the max height
                }
                pos = world.copyPos();
                pos.cp--;
                return next(world, pi, ii);
            case UNKNOWN:
                return EXECUTE_UNKNOWN_CODE;
            // bail out for all the calls
        }
        // calls
        int ci = op - CALL_1;
        assert ci >= 0;
        pos = world.copyPos();
        // any tail call is done "in-place"
        if (ii < procLen[pi] - 1) {
            // other call -- will return to next instruction (note -- it was not a tail call!)
            pos.cs = clearFrame(pos.sp, pos.cs) | packFrame(pos.sp, pi, ii + 1);
            if (pos.sp < MAX_SP) {
                pos.sp += BITS_PER_FRAME;
            } else {
                // stack overflow
                if (constraints.ret) {
                    // it is an error only when we are required to return for solution
                    world.undoCopy();
                    return EXECUTE_FAIL_STACK_OVER;
                }
                // shift stack on overflow (forget oldest)
                pos.cs = pos.cs >>> BITS_PER_FRAME;
            }
        }
        // record new proc & instruction in the current activation record
        pos.cs = clearFrame(pos.sp, pos.cs) | packFrame(pos.sp, ci, 0);
        pos.movesMade++;
        // check for infinite loop on call (and on call only!)
        if (!world.checkVisitedAndCommit()) {
            world.undoCopy();
            return EXECUTE_FAIL_INF_LOOP;
        }
        return EXECUTE_OK;
    }

    private int next(World world, int pi, int ii) {
        Pos pos = world.copyPos();
        // instr index++
        if (++ii >= procLen[pi]) {
            // return
            pos.cs = clearFrame(pos.sp, pos.cs);
            pos.sp -= BITS_PER_FRAME;
        } else {
            // update frame
            pos.cs = clearFrame(pos.sp, pos.cs) | packFrame(pos.sp, pi, ii);
        }
        pos.movesMade++;
        if (Arrays.equals(world.goal, pos.b)
                && (!constraints.ret || pos.sp < 0)
                && (constraints.goalcp < 0 || pos.cp == constraints.goalcp))
        {
            world.commitPos();
            return EXECUTE_GOAL;
        }
        if (pos.sp < 0) {
            world.undoCopy();
            return EXECUTE_FAIL_RETURNS;
        }
        world.commitPos();
        return EXECUTE_OK;
    }

    private boolean searchCode0(int depth, World world, Pos pos, int pi, int ii, int c) {
        assert code[pi * MAX_PROC_LEN + ii] == UNKNOWN;
        // try to execute something (don't commit to exact condition)
        if (searchOp(depth, world, execMods[pos.ch], pi, ii, c))
            return true;
        // try to skip instruction (don't commit what instruction yet)
        int mod = skipMods[pos.ch];
        if (mod != 0) { // if mods can be used
            code[pi * MAX_PROC_LEN + ii] = mod;
            if (simulateCode0(depth + 1, world))
                return true;
        }
        code[pi * MAX_PROC_LEN + ii] = UNKNOWN;
        return false;
    }

    private boolean searchMod(int depth, World world, Pos pos, int pi, int ii, int c) {
        assert code[pi * MAX_PROC_LEN + ii] == c;
        int mod = c & ~OP_MASK;
        int op = c & OP_MASK;
        // assume executed
        if (verifyAndSimulateCode(depth, world, pi, ii, mod & execMods[pos.ch], op))
            return true;
        // assume skipped
        if (verifyAndSimulateCode(depth, world, pi, ii, mod & skipMods[pos.ch], op))
            return true;
        code[pi * MAX_PROC_LEN + ii] = c;
        return false;
    }

    private boolean searchOp(int depth, World world, int useMod, int pi, int ii, int c) {
        int mod = c & ~OP_MASK;
        assert code[pi * MAX_PROC_LEN + ii] == mod;
        for (int op : OPS) {
            int ci = op - CALL_1; // "calls to" index
            if (ci > 0 && procLen[ci] == 0)
                break; // call to absent proc -- don't try & break loop
            if (ci > 1 && usedOps[op - 1] == 0)
                break; // symmetry breaking -- must first call to PROC_2, then PROC_3, etc
            if (usedOps[op]++ < shallUseOp[op])
                shallUseOpSlots--;
            freeOpSlots--;
            if (freeOpSlots >= shallUseOpSlots) {
                if (verifyAndSimulateCode(depth, world, pi, ii, mod | useMod, op))
                    return true;
            } else {
                cntFutile[depth + 1]++;
            }
            if (--usedOps[op] < shallUseOp[op])
                shallUseOpSlots++;
            freeOpSlots++;
        }
        code[pi * MAX_PROC_LEN + ii] = mod;
        return false;
    }

    private boolean verifyAndSimulateCode(int depth, World world, int pi, int ii, int mod, int op) {
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
        return simulateCode0(depth + 1, world);
    }

    private boolean searchShape(int i, int slots, int[] startShape, int shallUse) {
        if (slots == 0) {
            if (shallUse != 0)
                return false; // have not used all required procs
            if (i < constraints.minProcs || i > constraints.maxProcs)
                return false; // proc count constraints violated
            log("%s - Searching for solutions of size %s%n", time(), shape2Str());
            return simulateCodeInit();
        }
        if (i >= MAX_PROCS)
            return false;
        int limit = startShape != null ? startShape[i] : Math.min(slots, MAX_PROC_LEN);
        for (int cl = limit; cl >= 2; cl--) {
            procLen[i] = cl;
            // see if some required to use proc can be used here (length fits)
            if (constraints.useFixed) {
                if (searchShapeUse(i, i, slots, startShape, shallUse))
                    return true;
            } else for (int j = 0; j < MAX_PROCS; j++) {
                if (searchShapeUse(i, j, slots, startShape, shallUse))
                    return true;
            }
            // try arbitrary proc here (if allowed)
            if (!constraints.useFixed || constraints.use.procLen[i] == 0) {
                if (searchShape(i + 1, slots - cl, startShape, shallUse))
                    return true;
            }
            procLen[i] = 0;
            startShape = null;
        }
        return false;
    }

    private boolean searchShapeUse(int i, int j, int slots, int[] startShape, int shallUse) {
        int cl = procLen[i];
        int shallUseBit = 1 << j;
        if ((shallUse & shallUseBit) != 0 && constraints.use.procLen[j] == cl) {
            System.arraycopy(constraints.use.code, MAX_PROC_LEN * j, code, MAX_PROC_LEN * i, MAX_PROC_LEN);
            if (searchShape(i + 1, slots - cl, startShape, shallUse & ~shallUseBit))
                return true;
            Arrays.fill(code, MAX_PROC_LEN * i, MAX_PROC_LEN * i + MAX_PROC_LEN, 0);
        }
        return false;
    }

    private String time() {
        return new SimpleDateFormat("yyyyMMdd-HHmmss.sss").format(new Date());
    }

    boolean verify(Solution solution) {
        assign(solution);
        log("Verifying solution of size %s%n", shape2Str());
        log("%s%n", code2Str(true));
        boolean ok = simulateCodeInit();
        if (ok) {
            logMovesMade();
        } else {
            log("!!! VERIFICATION FAILED !!!%n");
        }
        Stream.of(worlds).forEach(World::restore);
        return ok;
    }

    private void logMovesMade() {
        log("This solution makes %s moves%n", Stream.of(worlds)
                .map(world -> String.format(Locale.US, "%,d", world.movesMade()))
                .collect(Collectors.joining(", ")));
    }

    void solve() throws IOException {
        progressPhaser = new Phaser(2);
        ProgressPrinter progressPrinter = new ProgressPrinter();
        progressPrinter.start();
        openLog();
        log("%s ===================================================%n", time());
        long time = System.currentTimeMillis();
        int startSize = IntStream.of(constraints.size).sum();
        int[] startShape = constraints.size;
        int shallUse = 0;
        for (int i = 0; i < MAX_PROCS; i++) {
            if (constraints.use.procLen[i] != 0)
                shallUse |= (1 << i);
        }
        Arrays.fill(code, 0);
        for (int slots = startSize;; slots++) {
            if (searchShape(0, slots, startShape, shallUse)) {
                long timeTotal = System.currentTimeMillis() - time;
                log("%s ---------------------------------------------------%n", time());
                log("Found solution for %s in %,d ms%n", name, timeTotal);
                log("Solution of size %s%n", shape2Str());
                log("%s%n", code2Str(true));
                logMovesMade();
                logAllStats();
                break;
            } else {
                long timeTotal = System.currentTimeMillis() - time;
                log("%s - Analyzed solutions up to size %d in %,d ms%n", time(), slots, timeTotal);
            }
            startShape = null;
        }
        closeLog();
        progressPrinter.interrupt();
        Stream.of(worlds).forEach(World::restore);
    }

    private void logAllStats() {
        log("Made total of %,d moves while searching%n", totalMovesMade);
        log("Made max of %,d moves with %s%n", maxMovesMade, maxMovesSolution);
        log("Analyzed combinations and encountered backtracking reasons:%n");
        logStats("Total         ", cntTotal);
        logStats("Bump Left     ", cntBumpLeft);
        logStats("Bump Right    ", cntBumpRight);
        logStats("Too High      ", cntTooHigh);
        logStats("Inf Loop      ", cntInfLoop);
        logStats("Returns       ", cntReturns);
        logStats("Unreachable   ", cntUnreachable);
        logStats("Redundant     ", cntRedundant);
        logStats("Futile        ", cntFutile);
        logStats("Poison        ", cntPoison);
        logStats("Stack Over    ", cntStackOver);
        logStats("Too Many Moves", cntTooManyMoves);
    }

    private void logStats(String stat, long[] cnt) {
        int maxD = 0;
        long maxDCnt = 0;
        int maxDIndex = 0;
        long sum = 0;
        StringBuilder sb = new StringBuilder();
        for (int depth = 0; depth < cnt.length; depth++) {
            long t = cnt[depth];
            sum += t;
            if (t > maxDCnt) {
                maxDCnt = t;
                maxDIndex = depth;
            }
            if (t != 0) {
                maxD = depth;
            }
            if (depth < 20) {
                sb.append(String.format(Locale.US, " %,d", t));
            }
        }
        if (sum != 0)
            log("%s : %,14d at depth up to %2d, max %,14d at depth %2d, at first depths:%s%n",
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
        log = new PrintWriter(new FileWriter(file, true), true);
    }

    private void closeLog() {
        log.close();
        log = null;
    }

    // ======================================= helper classes =======================================

    static class Console extends Thread {
        Console() {
            super("Console");
            setDaemon(true);
        }

        @Override
        public void run() {
            BufferedReader in = new BufferedReader(new InputStreamReader(System.in));
            try {
                while (true) {
                    String line = in.readLine();
                    if (line == null)
                        break;
                    line = line.trim();
                    if (line.isEmpty())
                        continue;
                    CargoBotSolver solver = SOLVER;
                    switch (Character.toUpperCase(line.charAt(0))) {
                        case 'S':
                            if (solver != null)
                                solver.logAllStats();
                            break;
                        default:
                            consoleHelp();
                    }

                }
            } catch (IOException e) {
                e.printStackTrace(System.out);
            }
        }

        private void consoleHelp() {
            System.out.println("Type 's' on the console to get current stats");
        }
    }

    class ProgressPrinter extends Thread {
        private final Solution copy = new Solution();

        ProgressPrinter() {
            super("ProgressPrinter");
            setDaemon(true);
        }

        @Override
        public void run() {
            try {
                while (!Thread.currentThread().isInterrupted()) {
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
                // quick racy read here, but it does not matter, because it is for information purposes only
                copy.assign(CargoBotSolver.this);
                log("%s - working on %s%n", time(), copy);
            }
        }
    }

}
