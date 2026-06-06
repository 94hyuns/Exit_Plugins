package com.exit.job.model;

import org.bukkit.Material;

import java.util.List;

/**
 * 한 직업의 정적 정의 (config.yml에서 로드된 메타데이터).
 *
 * @param type        식별 enum
 * @param displayName 표시명 ("광부")
 * @param description 한 줄 설명
 * @param icon        GUI 아이콘 Material
 * @param perks       레벨순 정렬된 능력 리스트
 */
public record JobDefinition(JobType type, String displayName, String description,
                            Material icon, List<PerkInfo> perks) {}
