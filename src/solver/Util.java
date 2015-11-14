package solver;

import static solver.Constants.*;

/**
 * @author Roman Elizarov
 */
class Util {
    private Util() {} // do not create

    static boolean checkMod(int mod, int ch) {
        switch (mod) {
            case ALWAYS: return true;
            case WHEN_NONE: return ch == NONE;
            case WHEN_A: return ch == A;
            case WHEN_B: return ch == B;
            case WHEN_C: return ch == C;
            case WHEN_D: return ch == D;
            case WHEN_ANY: return ch != NONE;
            default: throw new AssertionError();
        }
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

    static boolean isSubStack(int s1, int s2) {
        int h1 = stackHeight(s1);
        int h2 = stackHeight(s2);
        if (h1 > h2)
            return false;
        // assert h1 <= h2
        int mask = ((1 << (h1 * BITS_PER_COLOR)) - 1) << BITS_PER_HEIGHT;
        return (s1 & mask) == ((s2 >> ((h2 - h1) * BITS_PER_COLOR)) & mask);
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


    static String mod2str(int c, boolean pickOne) {
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

    static String op2str(int c) {
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

    static int str2mod(String s) {
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

    static int str2op(String s) {
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
