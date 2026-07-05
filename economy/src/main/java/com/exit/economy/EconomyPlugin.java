package com.exit.economy;

import com.exit.core.CorePlugin;
import com.exit.core.api.EconomyProvider;
import com.exit.core.registry.ServiceRegistry;
import com.exit.economy.commands.DepositCommand;
import com.exit.economy.commands.TransferCommand;
import com.exit.economy.commands.WithdrawCommand;
import com.exit.economy.display.BalanceScoreboard;
import com.exit.economy.items.CashNoteListener;
import org.bukkit.plugin.java.JavaPlugin;

public class EconomyPlugin extends JavaPlugin {

    private static EconomyPlugin instance;
    private BalanceScoreboard balanceScoreboard;

    @Override
    public void onEnable() {
        instance = this;

        EconomyProviderImpl provider = new EconomyProviderImpl(
            CorePlugin.getInstance().getPlayerDataManager()
        );
        ServiceRegistry.register(EconomyProvider.class, provider);

        balanceScoreboard = new BalanceScoreboard(this);

        getCommand("출금").setExecutor(new WithdrawCommand(this));
        getCommand("입금").setExecutor(new DepositCommand(this));
        getCommand("송금").setExecutor(new TransferCommand(this));

        getServer().getPluginManager().registerEvents(new CashNoteListener(this), this);
        getServer().getPluginManager().registerEvents(balanceScoreboard, this);

        getLogger().info("Economy 플러그인이 활성화되었습니다. 단위: 울캐쉬(w)");
    }

    @Override
    public void onDisable() {
        ServiceRegistry.unregister(EconomyProvider.class);
        if (balanceScoreboard != null) balanceScoreboard.cleanup();
        getLogger().info("Economy 플러그인이 비활성화되었습니다.");
    }

    public static EconomyPlugin getInstance() {
        return instance;
    }

    public EconomyProvider getEconomy() {
        return ServiceRegistry.get(EconomyProvider.class).orElseThrow();
    }

    public BalanceScoreboard getBalanceScoreboard() {
        return balanceScoreboard;
    }
}
