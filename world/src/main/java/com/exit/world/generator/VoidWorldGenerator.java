package com.exit.world.generator;

import org.bukkit.block.Biome;
import org.bukkit.generator.BiomeProvider;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.generator.WorldInfo;

import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * 빈 청크만 생성하는 보이드 제너레이터.
 * 프리빌드 월드(마을, 던전)에서 보더 밖 미생성 청크가
 * 플랫 지형으로 채워지는 것을 방지한다.
 */
public class VoidWorldGenerator extends ChunkGenerator {

    @Override
    public void generateSurface(WorldInfo worldInfo, Random random,
                                int chunkX, int chunkZ, ChunkData chunk) {
        // 아무것도 생성하지 않음 → 전체 에어
    }

    // shouldGenerate* 디폴트가 true 라서 안 끄면 vanilla noise/surface/caves/decoration 이 그대로 돔.
    // 진짜 빈 보이드를 위해 모든 phase 차단.
    @Override public boolean shouldGenerateNoise() { return false; }
    @Override public boolean shouldGenerateSurface() { return false; }
    @Override public boolean shouldGenerateBedrock() { return false; }
    @Override public boolean shouldGenerateCaves() { return false; }
    @Override public boolean shouldGenerateDecorations() { return false; }
    @Override public boolean shouldGenerateMobs() { return false; }
    @Override public boolean shouldGenerateStructures() { return false; }

    @Override
    public BiomeProvider getDefaultBiomeProvider(WorldInfo worldInfo) {
        return new FlatWorldGenerator.FixedBiomeProvider(Biome.THE_VOID);
    }
}
