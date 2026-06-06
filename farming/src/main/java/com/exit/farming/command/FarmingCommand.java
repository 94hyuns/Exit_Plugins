package com.exit.farming.command;

import com.exit.farming.FarmingPlugin;
import com.exit.farming.crop.Crop;
import com.exit.farming.farmland.FarmlandClaimManager;
import com.exit.farming.farmland.FarmlandPolicy;
import com.exit.farming.farmland.WorldPolicyManager;
import com.exit.farming.item.CropItem;
import com.exit.farming.water.SprinklerItem;
import com.exit.farming.water.WaterTier;
import com.exit.farming.water.WateringCanItem;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.UUID;

/**
 * - /씨앗모음  : 13종 씨앗 각 2개씩 지급 (OP)
 * - /과일모음  : 13종 열매 각 2개씩 지급 (OP)
 * - /경작지정보 : 바라보는 블록 또는 발밑 경작지 클레임 정보 조회 (일반)
 */
public class FarmingCommand implements CommandExecutor {

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("콘솔에서는 사용할 수 없습니다.", NamedTextColor.RED));
            return true;
        }

        return switch (command.getName()) {
            case "씨앗모음" -> handleGive(player, true);
            case "과일모음" -> handleGive(player, false);
            case "경작지정보" -> handleClaimInfo(player);
            case "물뿌리개" -> handleWaterTool(player, args, false);
            case "스프링쿨러" -> handleWaterTool(player, args, true);
            default -> false;
        };
    }

    /**
     * /물뿌리개 [구리|철|다이아]   /스프링쿨러 [구리|철|다이아]
     * 인자 생략 시 구리 기본.
     */
    private boolean handleWaterTool(Player player, String[] args, boolean sprinkler) {
        if (!player.isOp()) {
            player.sendMessage(Component.text("관리자만 사용할 수 있습니다.", NamedTextColor.RED));
            return true;
        }
        WaterTier tier = WaterTier.COPPER;
        if (args.length >= 1) {
            String a = args[0].toLowerCase();
            tier = switch (a) {
                case "구리", "copper" -> WaterTier.COPPER;
                case "철", "iron" -> WaterTier.IRON;
                case "다이아", "다이아몬드", "diamond" -> WaterTier.DIAMOND;
                default -> null;
            };
            if (tier == null) {
                player.sendMessage(Component.text("등급 인자: 구리 / 철 / 다이아", NamedTextColor.YELLOW));
                return true;
            }
        }
        var item = sprinkler ? SprinklerItem.create(tier) : WateringCanItem.create(tier);
        var leftover = player.getInventory().addItem(item);
        leftover.values().forEach(drop -> player.getWorld().dropItem(player.getLocation(), drop));
        player.sendMessage(Component.text(
                tier.displayKor() + " " + (sprinkler ? "스프링쿨러" : "물뿌리개") + " 지급.",
                NamedTextColor.GREEN));
        return true;
    }

    private boolean handleGive(Player player, boolean isSeed) {
        if (!player.isOp()) {
            player.sendMessage(Component.text("관리자만 사용할 수 있습니다.", NamedTextColor.RED));
            return true;
        }
        int given = 0;
        for (Crop c : Crop.values()) {
            var item = isSeed ? CropItem.createSeed(c, 2) : CropItem.createFruit(c, 2, false);
            var leftover = player.getInventory().addItem(item);
            leftover.values().forEach(drop ->
                    player.getWorld().dropItem(player.getLocation(), drop));
            given++;
        }
        String label2 = isSeed ? "씨앗" : "열매";
        player.sendMessage(Component.text(label2 + " 모음 " + given + "종 지급 완료.",
                NamedTextColor.GREEN));
        return true;
    }

    private boolean handleClaimInfo(Player player) {
        FarmingPlugin fp = FarmingPlugin.getInstance();
        WorldPolicyManager policyMgr = fp.getPolicyManager();
        FarmlandClaimManager claimMgr = fp.getClaimManager();

        var block = player.getTargetBlockExact(6);
        if (block == null) {
            player.sendMessage(Component.text("바라보는 블록이 없습니다 (6블록 이내).",
                    NamedTextColor.YELLOW));
            return true;
        }

        FarmlandPolicy policy = policyMgr.policyOf(block.getWorld());
        player.sendMessage(Component.text("월드: " + block.getWorld().getName()
                + " / 정책: " + policy, NamedTextColor.GRAY));

        if (claimMgr.isClaimed(block)) {
            UUID owner = claimMgr.getOwner(block);
            String name = Bukkit.getOfflinePlayer(owner).getName();
            player.sendMessage(Component.text("이 경작지 소유자: "
                    + (name == null ? owner.toString() : name), NamedTextColor.GREEN));
        } else {
            player.sendMessage(Component.text("이 블록은 클레임되지 않았습니다.",
                    NamedTextColor.GRAY));
        }
        return true;
    }
}
