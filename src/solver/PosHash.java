package solver;

/**
 * A hash set of visited positions.
 *
 * @author Roman Elizarov
 */
class PosHash {
    static final int MAGIC = 0x9E3779B9;
    static final int START_BITS = 8;

    private Pos[] a = new Pos[1 << START_BITS];
    private int shift = 32 - START_BITS;
    private int size;
    private int limit = limit(1 << START_BITS);

    private Pos free;
    private Pos last;

    Pos allocCopy(Pos pos) {
        Pos res;
        if (free != null) {
            res = free;
            free = res.next;
            res.next = null;
            assert res.status == Pos.FREE;
            res.status = Pos.NEW;
        } else
            res = new Pos(pos.b.length);
        res.assign(pos);
        return res;
    }

    void freeIfNew(Pos pos) {
        if (pos.status == Pos.NEW)
            free(pos);
    }

    void free(Pos pos) {
        assert pos.status == Pos.NEW;
        pos.status = Pos.FREE;
        pos.next = free;
        free = pos;
    }

    void freeUntil(Pos stop) {
        Pos pos = last;
        while (pos != stop) {
            Pos next = pos.next;
            if (pos.status == Pos.IN_HASH) {
                a[pos.hIndex] = null;
                size--;
            } else {
                assert pos.status == Pos.SAVED;
            }
            pos.status = Pos.NEW;
            free(pos);
            pos = next;
        }
        last = stop;
    }

    void save(Pos pos) {
        assert pos.status == Pos.NEW;
        pos.status = Pos.SAVED;
        pos.next = last;
        last = pos;
    }

    boolean add(Pos pos) {
        assert pos.status == Pos.NEW;
        if (size >= limit)
            resize();
        if (!addImpl(pos))
            return false;
        size++;
        pos.status = Pos.IN_HASH;
        pos.next = last;
        last = pos;
        return true;
    }

    private boolean addImpl(Pos pos) {
        int h = (pos.hashCode() * MAGIC) >>> shift;
        while (true) {
            Pos q = a[h];
            if (q == null) {
                pos.hIndex = h;
                a[h] = pos;
                return true;
            }
            if (q.equals(pos))
                return false;
            if (h == 0)
                h = a.length;
            h--;
        }
    }

    private int limit(int len) {
        return len * 2 / 3;
    }

    private void resize() {
        Pos[] old = a;
        int len = old.length * 2;
        a = new Pos[len];
        limit = limit(len);
        shift--;
        for (Pos pos : old) {
            if (pos != null) {
                boolean success = addImpl(pos);
                assert success;
            }
        }
    }

}
