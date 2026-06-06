package com.exit.job.model;

/**
 * 직업 타입. config.yml의 jobs 섹션 키와 일치.
 */
public enum JobType {
    MINER("miner"),
    FISHER("fisher"),
    FARMER("farmer");

    private final String id;

    JobType(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }

    public static JobType fromId(String id) {
        if (id == null) return null;
        for (JobType t : values()) {
            if (t.id.equalsIgnoreCase(id)) return t;
        }
        return null;
    }
}
