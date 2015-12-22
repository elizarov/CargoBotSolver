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
    final Constraints constraints;

    final int[] init; // init board
    final int[] goal; // goal board

    private final Pos initPos;
    private final PosHash visited = new PosHash();
    private int maxColor = A;

    Pos pos; // current position

    private Pos copy; // current copy

    // keeps performed claw actions in this world
    private int[] actions;

    World(int index, int cp, int[] init, int[] goal, Constraints constraints) {
        this.index = index;
        this.constraints = constraints;
        if (init.length != goal.length)
            throw new IllegalArgumentException("Inconsistent lengths");
        // init colors
        int[] initCC = convertAndCountColors(init);
        int[] goalCC = convertAndCountColors(goal);
        if (!Arrays.equals(initCC, goalCC))
            throw new IllegalArgumentException("Inconsistent number of colors");
        this.init = init;
        this.goal = goal;
        initPos = new Pos(init, cp, NONE, 0, 0);
        pos = initPos;
        visited.add(initPos);
        actions = new int[constraints.maxSteps];
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

    Pos save() {
        assert copy == null;
        assert pos.status == Pos.IN_HASH || pos.status == Pos.SAVED;
        return pos;
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

    void rollback(Pos save) {
        undoCopy();
        pos = save;
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

    int stepsMade() {
        return pos.stepsMade;
    }

    int actionsMade() {
        return pos.actionsMade;
    }

    boolean action(int op) {
        int[] ca = constraints.actions;
        if (ca != null && (copy.actionsMade >= ca.length || ca[copy.actionsMade] != op))
            return false;
        actions[copy.actionsMade++] = op;
        return true;
    }

    String actionsToString() {
        StringBuilder sb = new StringBuilder(pos.actionsMade);
        for (int i = 0; i < pos.actionsMade; i++) {
            sb.append(op2str(actions[i]));
        }
        return sb.toString();
    }

    void restore() {
        undoCopy();
        visited.freeUntil(initPos);
        assert initPos.status == Pos.IN_HASH;
        pos = initPos;
    }

    boolean needsBothMoves() {
        // this is a kludge, so that Tutorial Cargo 101 solves
        return IntStream.of(initPos.b).map(Util::stackHeight).sum() > 1;
    }

}
