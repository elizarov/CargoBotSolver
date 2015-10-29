package solver;

import static solver.Constants.*;

/**
 * @author Roman Elizarov
 */
class Constraints {
    int mod;
    int initcp = 0;
    int goalcp = -1; // stop possition is irrelevant by default
    boolean ret;
    int[] size = {3}; // by default start from size 3
    int worlds = 1; // only one goal by default
    Solution use = new Solution();
    boolean useFixed; // when procs in "use" must have fixed numbers
    int minProcs = 1; // min number of procs
    int maxProcs = MAX_PROCS; // max number of procs
    int maxMoves = 10000; // default practical number of moves to make
}
