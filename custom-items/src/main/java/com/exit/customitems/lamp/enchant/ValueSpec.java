package com.exit.customitems.lamp.enchant;

/**
 * 램프 롤 시 결정되는 단일 수치값의 범위 정의.
 *
 * <p>모든 값은 정수(×100) 스케일. 예를 들어 "0.5 ~ 5, 0.1 단위"는
 * {@code new ValueSpec(50, 500, 10)}.
 *
 * <p>인챈트 하나가 여러 수치를 가지면 ValueSpec을 여러 개 갖는다.
 * 예: "체력이 X% 이하일 때 공격력 Y 증가" → ValueSpec 2개.
 *
 * <p>분포 결정 우선순위 (EnchantRoller.rollValue):
 * <ol>
 *   <li>{@code explicitWeights} 명시 시 — 후보별 가중치 직접 사용</li>
 *   <li>{@code bias} 명시 시 — {@code weight(i) = (N-i)^bias}</li>
 *   <li>기본값 — LampConfig.levelBias 사용</li>
 * </ol>
 */
public record ValueSpec(int min, int max, int step, double bias, double[] explicitWeights) {

    public ValueSpec {
        if (min < 0 || max < min || step <= 0) {
            throw new IllegalArgumentException(
                "Invalid ValueSpec: min=" + min + " max=" + max + " step=" + step);
        }
        if ((max - min) % step != 0) {
            throw new IllegalArgumentException(
                "(max - min) must be divisible by step: min=" + min + " max=" + max + " step=" + step);
        }
        if (explicitWeights != null) {
            int expectedCount = (max - min) / step + 1;
            if (explicitWeights.length != expectedCount) {
                throw new IllegalArgumentException(
                    "explicitWeights length " + explicitWeights.length
                        + " mismatches candidate count " + expectedCount);
            }
        }
    }

    /** bias 미지정 — global default 사용. */
    public ValueSpec(int min, int max, int step) {
        this(min, max, step, Double.NaN, null);
    }

    /** bias 명시 — explicit weights 없음. */
    public ValueSpec(int min, int max, int step, double bias) {
        this(min, max, step, bias, null);
    }

    /** explicit weights 명시 — bias 무시. */
    public ValueSpec(int min, int max, int step, double[] explicitWeights) {
        this(min, max, step, Double.NaN, explicitWeights);
    }

    public boolean hasExplicitBias() {
        return !Double.isNaN(bias);
    }

    public boolean hasExplicitWeights() {
        return explicitWeights != null;
    }

    /** 이 스펙이 가질 수 있는 이산 값의 개수. */
    public int candidateCount() {
        return (max - min) / step + 1;
    }

    /** i번째 후보값 (0-indexed, 낮은 값부터). */
    public int candidateAt(int index) {
        return min + step * index;
    }
}
