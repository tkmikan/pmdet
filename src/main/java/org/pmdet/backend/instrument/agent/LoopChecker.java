package org.pmdet.backend.instrument.agent;

import org.pmdet.backend.exception.LargeLoopException;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class LoopChecker {

    //    private static final Map<Integer, ArrayList<Integer>> record = new HashMap<>();
    private static final Map<Integer, Pair> prev = new HashMap<>();
    private static final Set<Integer> checked = new HashSet<>();

    private static class Pair {
        final int a;
        final int b;

        public Pair(int a, int b) {
            this.a = a;
            this.b = b;
        }
    }

    public static void reset() {
        checked.clear();
        prev.clear();
    }

    public static void recordCmp(int a, int b, int label) {
        if (checked.contains(label)) {
            return;
        }
        if (prev.containsKey(label) ) {
            Pair p = prev.get(label);
            int total = Math.abs(p.a - p.b);
            int cur = Math.abs(a - b);
            if (total > cur) {    // potential loop
                if (a == p.a || b == p.b) {

                } else {
                    checked.add(label);
                    return;
                }
                int step = total - cur;
                int count = total / step;
                if (count > 0x1000) {
//                    System.out.println("LoopChecker: detect large loop");
                    throw new LargeLoopException("LoopChecker: detect large loop");
                }
            }
            checked.add(label);
        }
        prev.put(label, new Pair(a, b));
    }
}
