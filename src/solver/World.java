package solver;

import java.util.Arrays;
import java.util.stream.IntStream;

import static solver.Constants.*;
import static solver.Constants.A;
import static solver.Constants.MAX_COLORS;
import static solver.Util.*;

/**
 * One world that the code shall solve (mapping input to output).
 *
 * @author Roman Elizarov
 */
class World {
    final int index; // index of this world in worlds array
    final int[] goal; // goal board

    private final Pos initPos;
    private final PosHash visited = new PosHash();
    private int maxColor = A;

    Pos pos; // current position

    // saved position for each depth
    private final Pos[] savePos = new Pos[MAX_PROC_LEN * MAX_PROCS * 4];
    private Pos copy; // current copy

    World(int index, int cp, int[] init, int[] goal) {
        this.index = index;
        if (init.length != goal.length)
            throw new IllegalArgumentException("Inconsistent lengths");
        // init colors
        int[] initCC = convertAndCountColors(init);
        int[] goalCC = convertAndCountColors(goal);
        if (!Arrays.equals(initCC, goalCC))
            throw new IllegalArgumentException("Inconsistent number of colors");
        this.goal = goal;
        initPos = new Pos(init, cp, NONE, 0, 0);
        pos = initPos;
        visited.add(initPos);
    }

    private int[] convertAndCountColors(int[] ss) {
        int[] cc = new int[POISON + 1];
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
                    if (b != POISON) {
                        assert b >= A && b <= MAX_COLORS;
                        maxColor = Math.max(maxColor, b);
                    }
                    cc[b]++;
                    s = stackPop(s);
                }
            }
        }
        return cc;
    }

    int maxColor() {
        return maxColor;
    }

    void save(int depth) {
        assert copy == null;
        assert pos.status == Pos.IN_HASH || pos.status == Pos.SAVED;
        assert savePos[depth] == null;
        savePos[depth] = pos;
    }

    Pos copyPos() {
        if (copy == null)
            copy = visited.allocCopy(pos);
        return copy;
    }

    void undoCopy() {
        if (copy != null) {
            visited.free(copy);
            copy = null;
        }
    }

    void rollback(int depth) {
        undoCopy();
        pos = savePos[depth];
        savePos[depth] = null;
        visited.freeUntil(pos);
    }

    void commitPos() {
        assert copy != null;
        visited.freeIfNew(pos);
        pos = copy;
        copy = null;
        if (pos.status == Pos.NEW)
            visited.save(pos);
    }

    boolean checkVisitedAndCommit() {
        assert copy != null;
        if (visited.add(copy)) {
            pos = copy;
            copy = null;
            return true;
        }
        visited.free(copy);
        copy = null;
        return false;
    }

    int movesMade() {
        return pos.movesMade;
    }

    void restore() {
        undoCopy();
        visited.freeUntil(initPos);
        Arrays.fill(savePos, null);
        assert initPos.status == Pos.IN_HASH;
        pos = initPos;
    }

    boolean needsBothMoves() {
        // this is a kludge, so that Tutorial Cargo 101 solves
        return IntStream.of(initPos.b).map(Util::stackHeight).sum() > 1;
    }
}
