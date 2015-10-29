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


}
