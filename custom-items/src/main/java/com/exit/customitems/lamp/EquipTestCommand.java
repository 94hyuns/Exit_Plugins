package com.exit.customitems.lamp;

import com.exit.customitems.lamp.enchant.CustomEnchant;
import com.exit.customitems.lamp.enchant.EnchantRegistry;
import com.exit.customitems.lamp.enchant.EnchantStorage;
import com.exit.customitems.lamp.enchant.EnchantTier;
import com.exit.customitems.lamp.enchant.LoreRenderer;
import com.exit.customitems.lamp.enchant.RolledEnchant;
import com.exit.customitems.lamp.enchant.ValueSpec;
import com.exit.customitems.lamp.enchant.impl.combat.AttackPowerEnchant;
import com.exit.customitems.lamp.enchant.impl.combat.BowAttackPowerEnchant;
import com.exit.customitems.lamp.enchant.impl.combat.VitalityEnchant;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * <b>OP 전용 테스트 명령어.</b>
 * <p>{@code /장비테스트 <장비> <인챈트명[레벨]> <플레이어>}
 *
 * <p>예시:
 * <ul>
 *   <li>{@code /장비테스트 방어구 공복의저주 Rokai} — 활력 max + 공복의저주 SET 4부위 다이아 방어구 1세트 지급</li>
 *   <li>{@code /장비테스트 검 약자멸시1 Rokai} — 공격력 max + 약자멸시 L1 다이아검 1개 지급</li>
 *   <li>{@code /장비테스트 검 약자멸시2 Rokai} — 공격력 max + 약자멸시 L2</li>
 *   <li>{@code /장비테스트 활 이연사 Rokai} — 활공격력 max + 이연사 L1</li>
 *   <li>{@code /장비테스트 검 공격력 Rokai} — 공격력 max BASIC 만 (UNIQUE 없음)</li>
 * </ul>
 *
 * <p>인챈트명은 표시명(displayName) 기준. 활성화된 인챈트만 사용 가능 (비활성 인챈트는 표시명 매칭 X).
 * 무기 UNIQUE 의 경우 인챈트명 뒤에 1 또는 2 (레벨) 붙일 수 있음, 없으면 L1.
 */
public class EquipTestCommand implements CommandExecutor, TabCompleter {

    private static final String PERMISSION = "customitems.testgear.admin";

    private final Plugin plugin;
    private final EnchantRegistry registry;
    private final EnchantStorage storage;
    private final LoreRenderer loreRenderer;

    public EquipTestCommand(Plugin plugin, EnchantRegistry registry,
                            EnchantStorage storage, LoreRenderer loreRenderer) {
        this.plugin = plugin;
        this.registry = registry;
        this.storage = storage;
        this.loreRenderer = loreRenderer;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission(PERMISSION)) {
            sender.sendMessage(Component.text("권한이 없습니다.").color(NamedTextColor.RED));
            return true;
        }
        if (args.length < 3) {
            sender.sendMessage(Component.text(
                    "사용법: /장비테스트 <방어구|검|창|삼지창|철퇴|활> <인챈트명[레벨]> <플레이어>")
                    .color(NamedTextColor.YELLOW));
            return true;
        }

        String gearArg = args[0];
        String enchantArg = args[1];
        String targetName = args[2];

        Player target = Bukkit.getPlayerExact(targetName);
        if (target == null) {
            sender.sendMessage(Component.text("플레이어를 찾을 수 없습니다: " + targetName)
                    .color(NamedTextColor.RED));
            return true;
        }

        // 인챈트명 + 레벨 파싱
        ParsedEnchant parsed = parseEnchant(enchantArg);
        if (parsed == null) {
            sender.sendMessage(Component.text("알 수 없는 인챈트: " + enchantArg
                    + " (활성화된 인챈트의 표시명만 사용 가능)").color(NamedTextColor.RED));
            return true;
        }

        // 장비 타입에 따라 아이템 생성
        List<ItemStack> items = createItems(gearArg, parsed);
        if (items == null) {
            sender.sendMessage(Component.text("알 수 없는 장비: " + gearArg
                    + " (방어구|검|창|삼지창|철퇴|활)").color(NamedTextColor.RED));
            return true;
        }

        // 인벤토리에 지급, 가득 차면 바닥에 드롭
        var overflow = target.getInventory().addItem(items.toArray(new ItemStack[0]));
        for (ItemStack leftover : overflow.values()) {
            target.getWorld().dropItemNaturally(target.getLocation(), leftover);
        }

        sender.sendMessage(Component.text(
                target.getName() + "에게 " + gearArg + " (" + parsed.enchant.getDisplayName()
                + (parsed.level > 1 ? " L" + parsed.level : "") + ") "
                + items.size() + "개 지급.").color(NamedTextColor.GREEN));
        target.sendMessage(Component.text(
                "[테스트 지급] " + gearArg + " — " + parsed.enchant.getDisplayName()
                + (parsed.level > 1 ? " L" + parsed.level : "")).color(NamedTextColor.GOLD));
        return true;
    }

    private record ParsedEnchant(CustomEnchant enchant, int level) {}

    private ParsedEnchant parseEnchant(String input) {
        // 끝에 숫자가 붙으면 레벨로 분리
        int level = 1;
        String name = input;
        if (input.length() > 1 && Character.isDigit(input.charAt(input.length() - 1))) {
            level = Character.getNumericValue(input.charAt(input.length() - 1));
            name = input.substring(0, input.length() - 1);
        }
        // 등록된 인챈트 중 displayName 일치 검색
        for (CustomEnchant e : registry.allEnchants()) {
            if (e.getDisplayName().replace(" ", "").equalsIgnoreCase(name.replace(" ", ""))) {
                return new ParsedEnchant(e, Math.max(1, level));
            }
        }
        return null;
    }

    private List<ItemStack> createItems(String gearArg, ParsedEnchant parsed) {
        switch (gearArg) {
            case "방어구": return buildArmorSet(parsed);
            case "검":   return buildWeapon(Material.DIAMOND_SWORD, parsed);
            case "삼지창": return buildWeapon(Material.TRIDENT, parsed);
            case "철퇴": return buildWeapon(Material.MACE, parsed);
            case "활":   return buildWeapon(Material.BOW, parsed);
            case "창": {
                Material spear = Material.matchMaterial("diamond_spear");
                if (spear == null) {
                    // 폴백: 네더라이트 → 아이언 → 트라이던트
                    for (String n : new String[]{"netherite_spear", "iron_spear", "trident"}) {
                        spear = Material.matchMaterial(n);
                        if (spear != null) break;
                    }
                }
                return buildWeapon(spear, parsed);
            }
            default: return null;
        }
    }

    private List<ItemStack> buildArmorSet(ParsedEnchant parsed) {
        List<ItemStack> result = new ArrayList<>();
        Material[] pieces = {
                Material.DIAMOND_HELMET, Material.DIAMOND_CHESTPLATE,
                Material.DIAMOND_LEGGINGS, Material.DIAMOND_BOOTS
        };
        boolean isSet = parsed.enchant.getTier() == EnchantTier.SET;
        for (Material m : pieces) {
            ItemStack item = new ItemStack(m);
            List<RolledEnchant> rolled = new ArrayList<>();
            if (isSet) {
                // 방어구 SET 지급: 활력 max(BASIC) + 해당 SET
                rolled.add(maxBasic(VitalityEnchant.keyOf(plugin)));
                rolled.add(new RolledEnchant(parsed.enchant, new int[0]));
            } else {
                // 방어구 BASIC 지급: 해당 BASIC max 만
                rolled.add(maxRoll(parsed.enchant));
            }
            applyEnchants(item, rolled);
            result.add(item);
        }
        return result;
    }

    private List<ItemStack> buildWeapon(Material material, ParsedEnchant parsed) {
        if (material == null) return null;
        ItemStack item = new ItemStack(material);
        List<RolledEnchant> rolled = new ArrayList<>();
        boolean isUnique = parsed.enchant.getTier() == EnchantTier.SET;
        if (isUnique) {
            // 무기 UNIQUE 지급: BASIC(공격력 또는 활공격력) max + UNIQUE 지정 레벨
            NamespacedKey basicKey = material == Material.BOW
                    ? BowAttackPowerEnchant.keyOf(plugin)
                    : AttackPowerEnchant.keyOf(plugin);
            rolled.add(maxBasic(basicKey));
            rolled.add(rollWithLevel(parsed.enchant, parsed.level));
        } else {
            // 무기 BASIC 지급: 해당 BASIC max 만
            rolled.add(maxRoll(parsed.enchant));
        }
        applyEnchants(item, rolled);
        return List.of(item);
    }

    /** 등록된 enchant 의 max 값으로 RolledEnchant 생성. */
    private RolledEnchant maxRoll(CustomEnchant enchant) {
        List<ValueSpec> specs = enchant.getValueSpecs();
        int[] values = new int[specs.size()];
        for (int i = 0; i < specs.size(); i++) {
            values[i] = specs.get(i).max();
        }
        return new RolledEnchant(enchant, values);
    }

    /** UNIQUE 무기 인챈트 (1~2 level) — 지정 레벨로 stored value 설정. */
    private RolledEnchant rollWithLevel(CustomEnchant enchant, int level) {
        List<ValueSpec> specs = enchant.getValueSpecs();
        if (specs.isEmpty()) return new RolledEnchant(enchant, new int[0]);
        int[] values = new int[specs.size()];
        ValueSpec spec0 = specs.get(0);
        int storedLevel = com.exit.customitems.util.NumUtil.toStored(level);
        // spec 범위로 클램프
        values[0] = Math.max(spec0.min(), Math.min(spec0.max(), storedLevel));
        // 나머지 ValueSpec 은 max
        for (int i = 1; i < specs.size(); i++) values[i] = specs.get(i).max();
        return new RolledEnchant(enchant, values);
    }

    /** key 로 enchant 검색 후 maxRoll. */
    private RolledEnchant maxBasic(NamespacedKey key) {
        Optional<CustomEnchant> e = registry.get(key);
        if (e.isEmpty()) {
            throw new IllegalStateException("BASIC enchant 등록 안 됨: " + key);
        }
        return maxRoll(e.get());
    }

    private void applyEnchants(ItemStack item, List<RolledEnchant> rolled) {
        storage.save(item, rolled);
        loreRenderer.render(item, rolled);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission(PERMISSION)) return List.of();
        if (args.length == 1) {
            return filter(Arrays.asList("방어구", "검", "창", "삼지창", "철퇴", "활"), args[0]);
        }
        if (args.length == 2) {
            List<String> names = new ArrayList<>();
            for (CustomEnchant e : registry.allEnchants()) {
                names.add(e.getDisplayName().replace(" ", ""));
            }
            return filter(names, args[1]);
        }
        if (args.length == 3) {
            List<String> ps = new ArrayList<>();
            for (Player p : Bukkit.getOnlinePlayers()) ps.add(p.getName());
            return filter(ps, args[2]);
        }
        return List.of();
    }

    private List<String> filter(List<String> src, String prefix) {
        String lower = prefix.toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>();
        for (String s : src) if (s.toLowerCase(Locale.ROOT).startsWith(lower)) out.add(s);
        return out;
    }
}
