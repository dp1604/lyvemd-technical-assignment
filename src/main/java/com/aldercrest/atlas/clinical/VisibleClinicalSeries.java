package com.aldercrest.atlas.clinical;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import static com.aldercrest.atlas.clinical.ClinicalModels.*;

/** A view boundary, not an authorization implementation. The service supplies its stored-rights predicate. */
public final class VisibleClinicalSeries {
    private final List<Observation> rows;
    private VisibleClinicalSeries(List<Observation> rows) { this.rows = List.copyOf(rows); }

    public static VisibleClinicalSeries filter(String patientId, List<Observation> candidates,
                                                Predicate<Observation> authorizedAndVisible) {
        Map<String, Observation> visible = new HashMap<>();
        for (Observation row : candidates) {
            if (!patientId.equals(row.patientId()) || !authorizedAndVisible.test(row)) { continue; }
            if (row.id() == null || row.effective() == null || row.analyte() == null
                    || visible.putIfAbsent(row.id(), row) != null) {
                throw new IllegalArgumentException("Invalid clinical view input.");
            }
        }
        List<Observation> complete = new ArrayList<>();
        Map<String, Boolean> completeChains = new HashMap<>();
        for (Observation row : visible.values()) {
            Set<String> visited = new HashSet<>();
            List<String> path = new ArrayList<>();
            Observation cursor = row;
            boolean completeChain = true;
            while (true) {
                if (completeChains.containsKey(cursor.id())) { completeChain = completeChains.get(cursor.id()); break; }
                if (!visited.add(cursor.id())) { completeChain = false; break; }
                path.add(cursor.id());
                if (cursor.correctionOf() == null) { break; }
                cursor = visible.get(cursor.correctionOf());
                if (cursor == null) { completeChain = false; break; }
            }
            for (String id : path) { completeChains.put(id, completeChain); }
            if (completeChain) { complete.add(row); }
        }
        return new VisibleClinicalSeries(complete);
    }

    public List<Observation> observations() { return rows; }

    /** Resolve visible heads before eligibility; never revive an original behind an ineligible visible correction. */
    public List<Observation> latestEligibleHeads() {
        Set<String> superseded = new HashSet<>();
        rows.forEach(row -> { if (row.correctionOf() != null) { superseded.add(row.correctionOf()); } });
        return rows.stream().filter(row -> !superseded.contains(row.id())).filter(Observation::eligibleForDerivation).toList();
    }
}
