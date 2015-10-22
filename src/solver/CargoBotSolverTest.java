package solver;

import junit.framework.TestCase;

import static solver.CargoBotSolver.*;

public class CargoBotSolverTest extends TestCase {
    public void testWrong1() {
        int[] init = { 4, 4, 4, 0, 0, 0, 0 };
        int[] goal = { 0, 0, 0, 0, 4, 4, 4 };
        CargoBotSolver t = new CargoBotSolver("", 0, 0, init, goal);
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
        System.out.println(t.code2Str(t.code, false));
        assertFalse(t.simulateCodeInit());
    }

    public void testRight1() {
        int[] init = { 4, 0, 0, 0, 0, 0, 0 };
        int[] goal = { 0, 0, 0, 0, 0, 0, 4 };
        CargoBotSolver t = new CargoBotSolver("", 0, 0, init, goal);
        t.procLen[0] = 5;
        t.code[0 * MAX_PROC_LEN + 0] = ALWAYS | DOWN;
        t.code[0 * MAX_PROC_LEN + 1] = ALWAYS | RIGHT;
        t.code[0 * MAX_PROC_LEN + 2] = ALWAYS | DOWN;
        t.code[0 * MAX_PROC_LEN + 3] = WHEN_NONE | LEFT;
        t.code[0 * MAX_PROC_LEN + 4] = ALWAYS | CALL_1;
        System.out.println(t.code2Str(t.code, false));
        assertTrue(t.simulateCodeInit());
    }

    public void testRight2() {
        int[] init = { 4, 4, 4, 0, 0, 0, 0 };
        int[] goal = { 0, 0, 0, 0, 4, 4, 4 };
        CargoBotSolver t = new CargoBotSolver("", 0, 0, init, goal);
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
        System.out.println(t.code2Str(t.code, false));
        assertTrue(t.simulateCodeInit());
    }
}
