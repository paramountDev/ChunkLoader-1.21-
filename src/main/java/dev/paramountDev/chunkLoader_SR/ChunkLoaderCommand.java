package dev.paramountDev.chunkLoader_SR;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class ChunkLoaderCommand implements CommandExecutor, TabCompleter {

    private final ChunkLoader_SR plugin;

    public ChunkLoaderCommand(ChunkLoader_SR plugin) {
        this.plugin = plugin;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender,
                                                @NotNull Command command,
                                                @NotNull String label,
                                                @NotNull String[] args) {
        List<String> list = new ArrayList<>();
        if (args.length == 1) {
            list.add("get");
        }
        return list;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "Только игрок может использовать эту команду.");
            return true;
        }
        if (args.length == 0 || !args[0].equalsIgnoreCase("get")) {
            player.sendMessage(ChatColor.YELLOW + "Используй: /chunkloader get");
            return true;
        }

        player.getInventory().addItem(ChunkLoader_SR.getInstance().createChunkLoaderItem());
        return true;
    }
}
