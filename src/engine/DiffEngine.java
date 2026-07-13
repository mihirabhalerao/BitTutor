package engine;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class DiffEngine {
    public List<String> computeDiff(List<String> history, List<String> live) {
        int m = history.size();
        int n = live.size();

        // Maximum m deletions and n insertions to reach live state
        int maxEdits = m + n;

        // Represents the maximum x position than can be reached from previous depth neighbours. Offset by max edits
        // as k ranges from -d to d but we can't index negative k.
        int bestX[] = new int[2 * (m + n) + 1];
        bestX[maxEdits + 1] = 0;

        boolean found = false;

        // Represents the state of bestX at every depth, used to retrace the path found.
        List<int[]> historyPath = new ArrayList<>();

        // Iterate through all possible depths, starting from 0, to find least edits path.
        for (int d = 0; d <= maxEdits; d++) {
            int copy[] = new int[bestX.length];
            System.arraycopy(bestX, 0, copy, 0, copy.length);
            historyPath.add(copy);

            // Iterate through all values of (x - y) in jumps of 2, since converting a deletion to an insertion,
            // requires a -1 to +1 jump, incrementing k by a net sum of 2.
            for (int k = -d; k <= d; k += 2) {
                int x = 0;
                int index = k + maxEdits;

                // Determining if we moved down (insertion) or moved right (deletion).
                if (k == -d || (k != d && bestX[index + 1] > bestX[index - 1])) {
                    // Moved down (insertion), hence x remains the same.
                    x = bestX[index + 1];
                } 
                else {
                    // Move right (deletion), hence x increases.
                    // Deletion prefered over insertion, hence we try to maximise x value
                    // when bestX[index + 1] == bestX[index - 1]
                    x = bestX[index - 1] + 1;
                }

                int y = x - k;

                // Greedy Step: Slide down matching diagonals automatically at 0 cost
                while (x < m && y < n && history.get(x).equals(live.get(y))) {
                    x++;
                    y++;
                }

                bestX[index] = x;

                // Target Check: Have we reached the bottom-right corner (M, N)?
                if (x >= m && y >= n) {
                    found = true;
                    break;
                }
            }

            if (found) break;
        }

        return backtrackPath(history, live, historyPath, m, n);
    }

    /**
     * Backtracks through the captured bestX-arrays from the end to reconstruct 
     * the exact edit sequence instructions chronologically.
     */
    private List<String> backtrackPath(List<String> h, List<String> l, List<int[]> path, int m, int n) {
        List<String> report = new ArrayList<>();
        int x = m, y = n;
        int maxEdits = m + n;

        // Traverses backward through the path list.
        for (int d = path.size() - 1; d >= 0; d--) {
            int bestX[] = path.get(d);
            int k = x - y;
            int index = k + maxEdits;

            // Determines which diagonal we came from.
            boolean insertionAncestor = (k == -d || (k != d && bestX[index - 1] < bestX[index + 1]));

            int prevK = insertionAncestor ? k + 1 : k - 1;
            int prevX = bestX[prevK + maxEdits];
            int prevY = prevX - prevK;

            // Match up diagonal extractions.   
            while (prevX < x && prevY < y) {
                report.add("   " + h.get(x - 1));
                x--;
                y--;
            }

            if (d > 0) {
                if (insertionAncestor) {
                    // It was an insertion (+).
                    report.add("\u001B[32m+  " + l.get(y - 1) + "\u001B[0m");
                    y--;
                } else {
                    // It was a deletion (-).
                    report.add("\u001B[31m-  " + h.get(x - 1) + "\u001B[0m");
                    x--;
                }
            }
        }

        Collections.reverse(report);
        return report;
    }
}
