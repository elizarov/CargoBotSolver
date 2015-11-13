package solver;

/**
 * @author Roman Elizarov
 */
interface Constants {
    // General limits
    int MAX_HEIGHT = 7;
    int MAX_COLORS = 4;
    int MAX_PROCS = 4;
    int MAX_PROC_LEN = 8;

    // Bit sizes
    int BITS_PER_HEIGHT = 3;
    int STACK_HEIGHT_MASK = (1 << BITS_PER_HEIGHT) - 1;
    int BITS_PER_COLOR = 3;
    int STACK_COLOR_MASK = (1 << BITS_PER_COLOR) - 1;

    int BITS_PER_SLOT = 3;
    int FRAME_SLOT_MASK = (1 << BITS_PER_SLOT) - 1;
    int BITS_PER_PROC = 2;
    int FRAME_PROC_MASK = (1 << BITS_PER_PROC) - 1;
    int BITS_PER_FRAME = BITS_PER_SLOT + BITS_PER_PROC;
    long FRAME_FULL_MASK = (1L << BITS_PER_FRAME) - 1;

    int MAX_SP = (64 / BITS_PER_FRAME - 1) * BITS_PER_FRAME; // must fit into "long"

    // Colors
    int NONE = 0;
    int A = 1;
    int B = 2;
    int C = 3;
    int D = 4;
    int POISON = 5;

    // Ops
    int UNKNOWN = 0;
    int DOWN = 1;
    int RIGHT = 2;
    int LEFT = 3;
    int CALL_1 = 4;
    int CALL_2 = 5;
    int CALL_3 = 6;
    int CALL_4 = 7;

    int MAX_OP = 8;

    int BITS_PER_OP = 3;
    int OP_MASK = (1 << BITS_PER_OP) - 1;

    // Mods
    int ALWAYS = 0x008;
    int WHEN_NONE = 0x010;
    int WHEN_ANY = 0x020;
    int WHEN_A = 0x040;
    int WHEN_B = 0x080;
    int WHEN_C = 0x100;
    int WHEN_D = 0x200;

    int MIN_MOD = ALWAYS;
    int MAX_MOD = 0x400;

    int BITS_PER_CODE = 10;

    // execute conditions -- the order is important -- fails last
    int EXECUTE_GOAL = 0;
    int EXECUTE_OK = 1;
    int EXECUTE_UNKNOWN0 = 2;
    int EXECUTE_UNKNOWN_MOD = 3;
    int EXECUTE_UNKNOWN_CODE = 4;
    int EXECUTE_FAIL = 5;
    int EXECUTE_FAIL_BUMP_RIGHT = 6;
    int EXECUTE_FAIL_BUMP_LEFT = 7;
    int EXECUTE_FAIL_TOO_HIGH = 8;
    int EXECUTE_FAIL_INF_LOOP = 9;
    int EXECUTE_FAIL_RETURNS = 10;
    int EXECUTE_FAIL_POISON = 11;
    int EXECUTE_FAIL_STACK_OVER = 12;
    int EXECUTE_FAIL_FORBIDDEN = 13;

    // ops list / strings
    int[] OPS = { DOWN, RIGHT, LEFT, CALL_1, CALL_2, CALL_3, CALL_4 };
    String[] OP_STRS = { "v", ">", "<", "1", "2", "3", "4" };

    // mods list / strings, (note: MODS has a sentinel value at the end)
    int[] MODS = { ALWAYS, WHEN_NONE, WHEN_ANY, WHEN_A, WHEN_B, WHEN_C, WHEN_D, MAX_MOD };
    String[] MOD_STRS = { " ", "N", "*", "A", "B", "C", "D" };

    // constrains on stack contents
    int STACK_ANY = 0; // any stack
    int STACK_INIT = 1; // sub-stack of init
    int STACK_GOAL = 2; // sub-stack of goal
    int STACK_TAKE_ONLY = 4; // can only take boxes
    int STACK_PUT_ONLY = 8; // can only put boxes

    int[] STACKS = { STACK_ANY, STACK_INIT, STACK_GOAL, STACK_TAKE_ONLY, STACK_PUT_ONLY };
    String[] STACK_STRS = { "*", "I", "G", "-", "+" };

    // other strings/chars
    char A_CHAR = 'A';
    char POISON_CHAR = 'P';

    char COMMENT_CHAR = '#';
    char NAME_ON_CHAR = '+';
    char NAME_OFF_CHAR = '-';
    char SOLUTION_LINE_CHAR = '=';
    char CONSTRAINT_LINE_CHAR = ':';

    String CONSTRAINT_MOD = "mod=";
    String CONSTRAINT_INITCP = "initcp=";
    String CONSTRAINT_GOALCP = "goalcp=";
    String CONSTRAINT_RET = "ret";
    String CONSTRAINT_SIZE = "size=";
    String CONSTRAINT_WORLDS = "worlds=";
    String CONSTRAINT_USE = "use=";
    String CONSTRAINT_USE_FIXED = "usefixed";
    String CONSTRAINT_MIN_PROCS = "minprocs=";
    String CONSTRAINT_MAX_PROCS = "maxprocs=";
    String CONSTRAINT_MAX_MOVES = "maxmoves=";
    String CONSTRAINT_STACK = "stack=";
}
