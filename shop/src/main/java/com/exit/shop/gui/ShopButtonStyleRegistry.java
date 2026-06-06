package com.exit.shop.gui;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.EnumMap;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Shop GUI 버튼의 시각적 스타일 (Material + CustomModelData) 을 yml에서 로드.
 *
 * <p>config.yml 의 gui 섹션 예시:
 * <pre>
 * gui:
 *   buy-button:
 *     material: LIME_STAINED_GLASS_PANE
 *     custom-model-data: 0       # 0 또는 미지정 = CMD 안 씀
 *   sell-button:
 *     material: RED_STAINED_GLASS_PANE
 *   sell-all-button:
 *     material: ORANGE_STAINED_GLASS_PANE
 *   sell-disabled:
 *     material: GRAY_STAINED_GLASS_PANE
 * </pre>
 *
 * <p>섹션 누락 시 디폴트 (기존 글래스 페인) 사용.
 */
public class ShopButtonStyleRegistry {

    public enum Slot {
        BUY, SELL, SELL_ALL,
        SELL_DISABLED, BUY_DISABLED,
        NAV_PREV, NAV_NEXT, NAV_CLOSE
    }

    /** Material + 선택적 CustomModelData. */
    public record Style(Material material, int customModelData) {
        public boolean hasCustomModelData() { return customModelData > 0; }
    }

    private final EnumMap<Slot, Style> styles = new EnumMap<>(Slot.class);

    public ShopButtonStyleRegistry() {
        applyDefaults();
    }

    private void applyDefaults() {
        styles.put(Slot.BUY, new Style(Material.LIME_STAINED_GLASS_PANE, 0));
        styles.put(Slot.SELL, new Style(Material.RED_STAINED_GLASS_PANE, 0));
        styles.put(Slot.SELL_ALL, new Style(Material.ORANGE_STAINED_GLASS_PANE, 0));
        styles.put(Slot.SELL_DISABLED, new Style(Material.GRAY_STAINED_GLASS_PANE, 0));
        styles.put(Slot.BUY_DISABLED, new Style(Material.GRAY_STAINED_GLASS_PANE, 0));
        styles.put(Slot.NAV_PREV, new Style(Material.ARROW, 0));
        styles.put(Slot.NAV_NEXT, new Style(Material.ARROW, 0));
        styles.put(Slot.NAV_CLOSE, new Style(Material.BARRIER, 0));
    }

    public void loadFromConfig(File configFile, Logger logger) {
        applyDefaults();
        if (!configFile.exists()) return;

        FileConfiguration config = YamlConfiguration.loadConfiguration(configFile);
        ConfigurationSection guiSection = config.getConfigurationSection("gui");
        if (guiSection == null) return;

        applyEntry(guiSection, "buy-button", Slot.BUY, logger);
        applyEntry(guiSection, "sell-button", Slot.SELL, logger);
        applyEntry(guiSection, "sell-all-button", Slot.SELL_ALL, logger);
        applyEntry(guiSection, "sell-disabled", Slot.SELL_DISABLED, logger);
        applyEntry(guiSection, "buy-disabled", Slot.BUY_DISABLED, logger);
        applyEntry(guiSection, "nav-prev", Slot.NAV_PREV, logger);
        applyEntry(guiSection, "nav-next", Slot.NAV_NEXT, logger);
        applyEntry(guiSection, "nav-close", Slot.NAV_CLOSE, logger);
    }

    public void reload(File configFile, Logger logger) {
        loadFromConfig(configFile, logger);
    }

    private void applyEntry(ConfigurationSection root, String key, Slot slot, Logger logger) {
        ConfigurationSection sec = root.getConfigurationSection(key);
        if (sec == null) return;

        String matStr = sec.getString("material");
        int cmd = sec.getInt("custom-model-data", 0);

        Material mat = null;
        if (matStr != null) {
            mat = Material.matchMaterial(matStr);
            if (mat == null) {
                logger.warning("[Shop] gui." + key + ".material 알 수 없는 값: '" + matStr
                        + "' — 기본값 유지");
            }
        }
        if (mat == null) return;  // 디폴트 유지

        styles.put(slot, new Style(mat, Math.max(0, cmd)));
    }

    public Style get(Slot slot) {
        return styles.get(slot);
    }

    public Map<Slot, Style> all() {
        return styles;
    }
}
