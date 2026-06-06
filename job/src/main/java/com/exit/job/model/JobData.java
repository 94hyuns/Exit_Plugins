package com.exit.job.model;

/**
 * 한 직업의 진행도. 가변(level/exp 변동) 이지만 record + 새 인스턴스 패턴으로 immutable 유지.
 */
public record JobData(int level, long exp) {
    public static JobData INITIAL = new JobData(1, 0);

    public JobData withLevel(int newLevel) { return new JobData(newLevel, exp); }
    public JobData withExp(long newExp) { return new JobData(level, newExp); }
    public JobData add(long deltaExp) { return new JobData(level, exp + deltaExp); }
}
