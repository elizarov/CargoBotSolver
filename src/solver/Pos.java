package solver;

import java.util.Arrays;

/**
 * A specific compactly represented position.
 *
 * @author Roman Elizarov
 */
class Pos {
    static final byte NEW = 0;
    static final byte FREE = 1;
    static final byte IN_HASH = 2;
    static final byte SAVED = 3;

    int[] b;      // board
    int cp;       // claw position
    int ch;       // claw holds
    int sp;       // stack pointer
    long cs;      // call stack

    int movesMade;       // no of moves made so far

    byte status;  // NEW, FREE, IN_HASH, or SAVED
    int hIndex;   // index in hash
    Pos next;    // pool in hash/free/saved lists

    Pos(int bs) {
        b = new int[bs];
    }

    Pos(int[] b, int cp, int ch, int sp, long cs) {
        this.b = b;
        this.cp = cp;
        this.ch = ch;
        this.sp = sp;
        this.cs = cs;
    }

    void assign(Pos pos) {
        assert status == NEW;
        System.arraycopy(pos.b, 0, b, 0, pos.b.length);
        this.cp = pos.cp;
        this.ch = pos.ch;
        this.sp = pos.sp;
        this.cs = pos.cs;
        this.movesMade = pos.movesMade;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Pos pos = (Pos) o;
        return cp == pos.cp && ch == pos.ch && sp == pos.sp && cs == pos.cs && Arrays.equals(b, pos.b);
    }

    @Override
    public int hashCode() {
        int result = Arrays.hashCode(b);
        result = 31 * result + cp;
        result = 31 * result + ch;
        result = 31 * result + sp;
        result = 31 * result + Long.hashCode(cs);
        return result;
    }
}
