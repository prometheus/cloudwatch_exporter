package io.prometheus.cloudwatch;

import static org.junit.Assert.assertTrue;

import java.util.Collections;
import java.util.List;

import org.junit.Test;

public class GetMetricDataGetterTest {
    @Test
    public void testPartition() {
        List<Integer> originalList = List.copyOf(Collections.nCopies(28, 0));
        List<List<Integer>> partitions = GetMetricDataDataGetter.partitionByMaxSize(originalList, 40);
        for (List<Integer> p : partitions) {
            assertTrue("partition must be smaller than 40", p.size() <= 40);
            assertTrue("partition should not be empty", !p.isEmpty());
        }
    }
}
