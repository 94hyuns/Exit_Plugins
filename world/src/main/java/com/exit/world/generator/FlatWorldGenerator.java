package com.exit.world.generator;

import org.bukkit.block.Biome;
import org.bukkit.generator.BiomeProvider;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.generator.WorldInfo;

import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * 평지 + 바이옴 고정 청크 생성기.
 * 마을(FOREST), 던전(DESERT) 월드에 사용.
 *
 * 레이어 구조:
 *   Y 0~4   : 베드락
 *   Y 5~62  : 스톤
 *   Y 63    : 더트
 *   Y 64    : 그라스/샌드 (바이옴에 따라)
 */
public class FlatWorldGenerator extends ChunkGenerator {

    private final Biome biome;

    public FlatWorldGenerator(Biome biome) {
        this.biome = biome;
    }

    @Override
    public void generateSurface(WorldInfo worldInfo, Random random, int chunkX, int chunkZ, ChunkData chunk) {
        boolean isDesert = (biome == Biome.DESERT);

        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                // 베드락 (Y 0~4)
                for (int y = 0; y <= 4; y++) {
                    chunk.setBlock(x, y, z, org.bukkit.Material.BEDROCK);
                }
                // 스톤 (Y 5~62)
                for (int y = 5; y <= 62; y++) {
                    chunk.setBlock(x, y, z, org.bukkit.Material.STONE);
                }
                // 더트 (Y 63) — 사막은 샌드스톤
                chunk.setBlock(x, 63, z, isDesert ? org.bukkit.Material.SANDSTONE : org.bukkit.Material.DIRT);
                // 표면 (Y 64) — 사막은 모래, 숲은 잔디
                chunk.setBlock(x, 64, z, isDesert ? org.bukkit.Material.SAND : org.bukkit.Material.GRASS_BLOCK);
            }
        }
    }

    @Override
    public BiomeProvider getDefaultBiomeProvider(WorldInfo worldInfo) {
        return new FixedBiomeProvider(biome);
    }

    /**
     * 전 구역을 하나의 바이옴으로 고정하는 BiomeProvider.
     */
    public static class FixedBiomeProvider extends BiomeProvider {

        private final Biome biome;

        public FixedBiomeProvider(Biome biome) {
            this.biome = biome;
        }

        @Override
        public Biome getBiome(WorldInfo worldInfo, int x, int y, int z) {
            return biome;
        }

        @Override
        public List<Biome> getBiomes(WorldInfo worldInfo) {
            return Collections.singletonList(biome);
        }
    }
}
