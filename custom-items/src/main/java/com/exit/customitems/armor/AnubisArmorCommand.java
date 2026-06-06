package com.exit.customitems.armor;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * /anubisarmor give &lt;player&gt; &lt;helmet|leggings|boots|set&gt; [buff] [amount]
 *
 * <p>buff 는 SPEED / SATURATION / HASTE / RANDOM (기본 RANDOM).
 * set 은 3부위 1세트 한 번에 지급 — buff 지정 시 3부위 모두 동일 buff, 미지정 시 각자 랜덤.
 */
public class AnubisArmorCommand implements CommandExecutor, TabCompleter {

    private static final String PERMISSION = "customitems.anubisarmor.admin";
    private static final List<String> PARTS = List.of("helmet", "leggings", "boots", "set");
    private static final List<String> BUFFS = List.of("random", "speed", "saturation", "haste");

    private final AnubisArmorItem anubisArmorItem;

    public AnubisArmorCommand(AnubisArmorItem anubisArmorItem) {
        this.anubisArmorItem = anubisArmorItem;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission(PERMISSION)) {
            sender.sendMessage(Component.text("권한이 없습니다.").color(NamedTextColor.RED));
            return true;
        }

        if (args.length < 3 || !args[0].equalsIgnoreCase("give")) {
            sender.sendMessage(Component.text(
                    "사용법: /anubisarmor give <player> <helmet|leggings|boots|set> [speed|saturation|haste|random] [amount]")
                    .color(NamedTextColor.YELLOW));
            return true;
        }

        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            sender.sendMessage(Component.text("플레이어를 찾을 수 없습니다: " + args[1])
                    .color(NamedTextColor.RED));
            return true;
        }

        String part = args[2].toLowerCase();
        if (!PARTS.contains(part)) {
            sender.sendMessage(Component.text("부위는 helmet, leggings, boots, set 중 하나여야 합니다.")
                    .color(NamedTextColor.RED));
            return true;
        }

        // 선택적 buff (args[3]) — 숫자면 amount 로 해석, 문자열이면 buff 로
        AnubisArmorItem.Buff fixedBuff = null;
        int amountIdx = 3;
        if (args.length >= 4) {
            String maybeBuff = args[3].toLowerCase();
            if (BUFFS.contains(maybeBuff)) {
                if (!"random".equals(maybeBuff)) {
                    fixedBuff = AnubisArmorItem.Buff.fromString(maybeBuff);
                }
                amountIdx = 4;
            }
        }

        int amount = 1;
        if (args.length > amountIdx) {
            try {
                amount = Integer.parseInt(args[amountIdx]);
            } catch (NumberFormatException e) {
                sender.sendMessage(Component.text("개수는 숫자여야 합니다: " + args[amountIdx])
                        .color(NamedTextColor.RED));
                return true;
            }
            if (amount <= 0 || amount > 64) {
                sender.sendMessage(Component.text("개수는 1~64 사이여야 합니다.")
                        .color(NamedTextColor.RED));
                return true;
            }
        }

        List<ItemStack> stacks = new ArrayList<>();
        switch (part) {
            case "helmet"   -> stacks.add(fixedBuff == null
                    ? anubisArmorItem.createHelmet(amount)
                    : anubisArmorItem.createHelmet(amount, fixedBuff));
            case "leggings" -> stacks.add(fixedBuff == null
                    ? anubisArmorItem.createLeggings(amount)
                    : anubisArmorItem.createLeggings(amount, fixedBuff));
            case "boots"    -> stacks.add(fixedBuff == null
                    ? anubisArmorItem.createBoots(amount)
                    : anubisArmorItem.createBoots(amount, fixedBuff));
            case "set" -> {
                if (fixedBuff == null) {
                    stacks.add(anubisArmorItem.createHelmet(amount));
                    stacks.add(anubisArmorItem.createLeggings(amount));
                    stacks.add(anubisArmorItem.createBoots(amount));
                } else {
                    stacks.add(anubisArmorItem.createHelmet(amount, fixedBuff));
                    stacks.add(anubisArmorItem.createLeggings(amount, fixedBuff));
                    stacks.add(anubisArmorItem.createBoots(amount, fixedBuff));
                }
            }
        }

        for (ItemStack stack : stacks) {
            var overflow = target.getInventory().addItem(stack);
            for (ItemStack leftover : overflow.values()) {
                target.getWorld().dropItemNaturally(target.getLocation(), leftover);
            }
        }

        String label2 = "set".equals(part) ? "세트 (3부위)" : part;
        String buffLabel = fixedBuff == null ? "랜덤" : fixedBuff.koName;
        sender.sendMessage(Component.text(
                target.getName() + " 에게 아누비스 " + label2 + " " + amount + "개 [버프: " + buffLabel + "] 지급.")
                .color(NamedTextColor.GOLD));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission(PERMISSION)) return Collections.emptyList();

        if (args.length == 1) return filter(List.of("give"), args[0]);
        if (args.length == 2 && args[0].equalsIgnoreCase("give")) {
            List<String> names = new ArrayList<>();
            for (Player p : Bukkit.getOnlinePlayers()) names.add(p.getName());
            return filter(names, args[1]);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("give")) {
            return filter(PARTS, args[2]);
        }
        if (args.length == 4 && args[0].equalsIgnoreCase("give")) {
            // 4번째 인자는 buff 또는 amount 둘 다 가능 — 둘 다 제안
            List<String> opts = new ArrayList<>(BUFFS);
            opts.addAll(List.of("1", "8", "16", "64"));
            return filter(opts, args[3]);
        }
        if (args.length == 5 && args[0].equalsIgnoreCase("give")) {
            return filter(List.of("1", "8", "16", "64"), args[4]);
        }
        return Collections.emptyList();
    }

    private List<String> filter(List<String> src, String prefix) {
        String lower = prefix.toLowerCase();
        List<String> out = new ArrayList<>();
        for (String s : src) if (s.toLowerCase().startsWith(lower)) out.add(s);
        return out;
    }
}
