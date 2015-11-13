package solver;

import junit.framework.TestCase;

import java.util.Collections;

import static solver.Constants.*;
import static solver.Parser.*;
import static solver.Util.*;

public class CargoBotSolverTest extends TestCase {
    public void testWrong1() {
        int[] init = { 4, 4, 4, 0, 0, 0, 0 };
        int[] goal = { 0, 0, 0, 0, 4, 4, 4 };
        CargoBotSolver t = new CargoBotSolver("", new Constraints(),
                Collections.singletonList(new World(0, 0, init, goal)));
        t.procLen[0] = 4;
        t.procLen[1] = 4;
        t.code[0 * MAX_PROC_LEN + 0] = ALWAYS | DOWN;
        t.code[0 * MAX_PROC_LEN + 1] = ALWAYS | RIGHT;
        t.code[0 * MAX_PROC_LEN + 2] = ALWAYS | RIGHT;
        t.code[0 * MAX_PROC_LEN + 3] = ALWAYS | CALL_2;
        t.code[1 * MAX_PROC_LEN + 0] = WHEN_ANY | RIGHT;
        t.code[1 * MAX_PROC_LEN + 1] = WHEN_ANY | RIGHT;
        t.code[1 * MAX_PROC_LEN + 2] = WHEN_ANY | DOWN;
        t.code[1 * MAX_PROC_LEN + 3] = ALWAYS | LEFT;
        System.out.println(t.code2Str(false));
        assertFalse(t.simulateCodeInit());
    }

    public void testRight1() {
        int[] init = { 4, 0, 0, 0, 0, 0, 0 };
        int[] goal = { 0, 0, 0, 0, 0, 0, 4 };
        CargoBotSolver t = new CargoBotSolver("", new Constraints(),
                Collections.singletonList(new World(0, 0, init, goal)));
        t.procLen[0] = 5;
        t.code[0 * MAX_PROC_LEN + 0] = ALWAYS | DOWN;
        t.code[0 * MAX_PROC_LEN + 1] = ALWAYS | RIGHT;
        t.code[0 * MAX_PROC_LEN + 2] = ALWAYS | DOWN;
        t.code[0 * MAX_PROC_LEN + 3] = WHEN_NONE | LEFT;
        t.code[0 * MAX_PROC_LEN + 4] = ALWAYS | CALL_1;
        System.out.println(t.code2Str(false));
        assertTrue(t.simulateCodeInit());
    }

    public void testRight2() {
        int[] init = { 4, 4, 4, 0, 0, 0, 0 };
        int[] goal = { 0, 0, 0, 0, 4, 4, 4 };
        CargoBotSolver t = new CargoBotSolver("", new Constraints(),
                Collections.singletonList(new World(0, 0, init, goal)));
        t.procLen[0] = 6;
        t.procLen[1] = 2;
        t.code[0 * MAX_PROC_LEN + 0] = ALWAYS | DOWN;
        t.code[0 * MAX_PROC_LEN + 1] = ALWAYS | CALL_2;
        t.code[0 * MAX_PROC_LEN + 2] = ALWAYS | CALL_2;
        t.code[0 * MAX_PROC_LEN + 3] = ALWAYS | CALL_2;
        t.code[0 * MAX_PROC_LEN + 4] = WHEN_ANY | CALL_2;
        t.code[0 * MAX_PROC_LEN + 5] = ALWAYS | CALL_1;
        t.code[1 * MAX_PROC_LEN + 0] = WHEN_ANY | RIGHT;
        t.code[1 * MAX_PROC_LEN + 1] = WHEN_NONE | LEFT;
        System.out.println(t.code2Str(false));
        assertTrue(t.simulateCodeInit());
    }

    public void testSubStack() {
        int s0 = parseStack("0");
        int s1 = parseStack("ABC");
        int s2 = parseStack("ABCD");
        int s3 = parseStack("BAC");

        assertTrue(isSubStack(s0, s0));
        assertTrue(isSubStack(s0, s1));
        assertTrue(isSubStack(s0, s2));
        assertTrue(isSubStack(s1, s1));
        assertTrue(isSubStack(s1, s2));
        assertTrue(isSubStack(s2, s2));

        assertFalse(isSubStack(s2, s1));
        assertFalse(isSubStack(s2, s0));
        assertFalse(isSubStack(s1, s3));
        assertFalse(isSubStack(s3, s1));
    }
}
