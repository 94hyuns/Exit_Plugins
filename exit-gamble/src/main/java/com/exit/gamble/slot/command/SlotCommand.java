package com.exit.gamble.slot.command;

import com.exit.gamble.slot.config.SlotConfig;
import com.exit.gamble.slot.gui.SlotGui;
import com.exit.gamble.slot.gui.SlotHolder;
import com.exit.gamble.slot.world.SlotMachine;
import com.exit.gamble.slot.world.SlotMachineManager;
import com.exit.gamble.slot.world.SlotMachineMirror;
import com.exit.gamble.slot.world.SlotMachineMirror.StatusKind;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

public class SlotCommand implements CommandExecutor {

    private static final double USE_RADIUS = 5.0;

    private final Plugin plugin;
    private final SlotConfig config;
    private final SlotGui gui;
    private final SlotMachineManager machineManager;
    private final SlotMachineMirror mirror;

    public SlotCommand(Plugin plugin, SlotConfig config, SlotGui gui,
                       SlotMachineManager machineManager, SlotMachineMirror mirror) {
        this.plugin = plugin;
        this.config = config;
        this.gui = gui;
        this.machineManager = machineManager;
        this.mirror = mirror;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        String name = command.getName();

        switch (name) {
            case "슬롯머신" -> handleEnter(sender, args, false);
            case "슬롯머신스폰" -> handleSpawn(sender, args);
            case "슬롯머신제거" -> handleRemove(sender, args);
            case "슬롯머신목록" -> handleList(sender);
            case "슬롯머신NPC스폰" -> handleNpcSpawn(sender, args);
            case "슬롯머신NPC제거" -> handleNpcRemove(sender, args);
            case "슬롯리로드" -> {
                config.load();
                sender.sendMessage(Component.text("[ExitGamble] config 리로드 완료", NamedTextColor.GREEN));
            }
            case "슬롯테스트" -> handleEnter(sender, args, true);
            default -> { return false; }
        }
        return true;
    }

    /** NPC 클릭에서 호출되는 입장 진입점. 거리 체크 생략 (NPC 가까이 있어야 클릭 가능하므로). */
    public void enterFromNpc(Player player, SlotMachine machine) {
        enterMachine(player, machine, false, true);
    }

    private void handleEnter(CommandSender sender, String[] args, boolean testMode) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("플레이어만 사용 가능", NamedTextColor.RED));
            return;
        }

        SlotMachine machine;
        if (args.length >= 1) {
            machine = machineManager.get(args[0]).orElse(null);
            if (machine == null) {
                player.sendMessage(Component.text("머신 '" + args[0] + "' 없음", NamedTextColor.RED));
                return;
            }
        } else {
            machine = machineManager.findNearest(player.getLocation(), USE_RADIUS).orElse(null);
        }

        enterMachine(player, machine, testMode, false);
    }

    private void enterMachine(Player player, SlotMachine machine, boolean testMode, boolean skipDistanceCheck) {
        if (machine != null) {
            if (!machine.anchor().getWorld().equals(player.getWorld())) {
                player.sendMessage(Component.text(
                        "머신은 " + machine.anchor().getWorld().getName() + " 월드에 있음", NamedTextColor.RED));
                return;
            }
            if (!skipDistanceCheck && machine.anchor().distance(player.getLocation()) > USE_RADIUS) {
                player.sendMessage(Component.text(
                        "머신과 너무 멉니다 (최대 " + USE_RADIUS + "블록)", NamedTextColor.RED));
                return;
            }
            if (!machineManager.tryAcquire(machine, player)) {
                String userName = machine.currentUser() == null ? "?"
                        : (Bukkit.getOfflinePlayer(machine.currentUser()).getName());
                player.sendMessage(Component.text(userName + " 님이 사용 중입니다", NamedTextColor.YELLOW));
                return;
            }
            mirror.updateStatus(machine, StatusKind.OCCUPIED, player.getName(), config.bets().get(0));
        }

        gui.open(player, machine);
        if (testMode) {
            if (player.getOpenInventory().getTopInventory().getHolder() instanceof SlotHolder holder) {
                holder.setTestMode(true);
                player.sendMessage(Component.text("[ExitGamble] 테스트 모드 — 결제 없음", NamedTextColor.YELLOW));
            }
        }
    }

    private void handleSpawn(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("플레이어만 사용 가능", NamedTextColor.RED));
            return;
        }
        if (args.length < 1) {
            player.sendMessage(Component.text("사용법: /슬롯머신스폰 <id>", NamedTextColor.RED));
            return;
        }
        String id = args[0];
        if (machineManager.get(id).isPresent()) {
            player.sendMessage(Component.text("이미 존재하는 id: " + id, NamedTextColor.RED));
            return;
        }
        SlotMachine machine = machineManager.spawn(id, player);
        if (machine == null) return; // spawn 내부에서 에러 메시지 전송됨
        mirror.resetReels(machine, config.symbols().get(0));
        mirror.updateStatus(machine, StatusKind.IDLE, "", 0);
        Location a = machine.anchor();
        player.sendMessage(Component.text(
                "[ExitGamble] 머신 '" + id + "' 등록: "
                        + a.getWorld().getName() + " (" + (int)a.getX() + "," + (int)a.getY() + "," + (int)a.getZ() + ")",
                NamedTextColor.GREEN));
    }

    private void handleRemove(CommandSender sender, String[] args) {
        if (args.length < 1) {
            sender.sendMessage(Component.text("사용법: /슬롯머신제거 <id>", NamedTextColor.RED));
            return;
        }
        String id = args[0];
        boolean removed = machineManager.remove(id);
        if (removed) {
            sender.sendMessage(Component.text("[ExitGamble] 머신 '" + id + "' 제거", NamedTextColor.GREEN));
        } else {
            sender.sendMessage(Component.text("머신 '" + id + "' 없음", NamedTextColor.RED));
        }
    }

    private void handleNpcSpawn(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("플레이어만 사용 가능", NamedTextColor.RED));
            return;
        }
        if (args.length < 1) {
            player.sendMessage(Component.text(
                    "사용법: /슬롯머신NPC스폰 <머신id> [스킨플레이어이름]", NamedTextColor.RED));
            return;
        }
        String machineId = args[0];
        SlotMachine machine = machineManager.get(machineId).orElse(null);
        if (machine == null) {
            player.sendMessage(Component.text(
                    "머신 '" + machineId + "' 없음. 먼저 /슬롯머신스폰 으로 등록하세요.", NamedTextColor.RED));
            return;
        }
        if (machine.hasNpc()) {
            player.sendMessage(Component.text(
                    "이미 NPC 가 붙어있음 (npcId: " + machine.npcId() + "). 먼저 /슬롯머신NPC제거 " + machineId,
                    NamedTextColor.RED));
            return;
        }
        String skin = args.length >= 2 ? args[1] : player.getName();
        String npcId = "slot_" + machineId;
        boolean ok = machineManager.attachNpc(machine, npcId, skin, player.getLocation());
        if (ok) {
            player.sendMessage(Component.text(
                    "[ExitGamble] 머신 '" + machineId + "' 에 NPC 부착 (스킨: " + skin + ")", NamedTextColor.GREEN));
        } else {
            player.sendMessage(Component.text(
                    "[ExitGamble] NPC 부착 실패 (Core 1.6.0+ 필요 또는 npcId 중복)", NamedTextColor.RED));
        }
    }

    private void handleNpcRemove(CommandSender sender, String[] args) {
        if (args.length < 1) {
            sender.sendMessage(Component.text("사용법: /슬롯머신NPC제거 <머신id>", NamedTextColor.RED));
            return;
        }
        SlotMachine machine = machineManager.get(args[0]).orElse(null);
        if (machine == null) {
            sender.sendMessage(Component.text("머신 '" + args[0] + "' 없음", NamedTextColor.RED));
            return;
        }
        boolean ok = machineManager.detachNpc(machine);
        sender.sendMessage(Component.text(
                ok ? "[ExitGamble] NPC 제거" : "NPC 가 붙어있지 않음",
                ok ? NamedTextColor.GREEN : NamedTextColor.YELLOW));
    }

    private void handleList(CommandSender sender) {
        var all = machineManager.all();
        if (all.isEmpty()) {
            sender.sendMessage(Component.text("등록된 머신 없음", NamedTextColor.GRAY));
            return;
        }
        sender.sendMessage(Component.text("[ExitGamble] 등록된 머신:", NamedTextColor.YELLOW));
        for (SlotMachine m : all) {
            Location a = m.anchor();
            String status = m.isOccupied() ? "사용 중" : "비어있음";
            sender.sendMessage(Component.text(
                    " - " + m.id() + " (" + a.getWorld().getName() + ", "
                            + (int)a.getX() + "," + (int)a.getY() + "," + (int)a.getZ() + ") [" + status + "]",
                    NamedTextColor.GRAY));
        }
    }
}
