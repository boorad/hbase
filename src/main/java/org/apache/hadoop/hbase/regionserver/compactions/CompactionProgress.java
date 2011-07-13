package org.apache.hadoop.hbase.regionserver.compactions;



public class CompactionProgress {

  public int totalCompactingKVs;
  public int currentCompactedKVs = 0;

  public CompactionProgress(int totalCompactingKVs) {
    this.totalCompactingKVs = totalCompactingKVs;
  }

  public float getProgressPct() {
    return currentCompactedKVs / totalCompactingKVs;
  }

}
