package me.justindevb.replay;

import me.justindevb.replay.api.ReplayManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;
import java.util.Locale;

public class ReplayCommand implements CommandExecutor, TabCompleter {
    private final ReplayManager replayManager;

    public ReplayCommand(ReplayManager replayManager) {
        this.replayManager = replayManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        Player player = null;
        if (sender instanceof Player p) {
            player = p;
        }

        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        String subCommand = args[0].toLowerCase(Locale.ROOT);

            switch (subCommand) {
                case "start" -> {
                    String sessionName = args.length > 1 ? args[1] : "recording_" + System.currentTimeMillis();
                    int duration = 60;

                    if (args.length >= 4) {
                        try {
                            duration = Integer.parseInt(args[args.length - 1]);
                        } catch (NumberFormatException ignored) {}
                    }

                    List<Player> targets = new ArrayList<>();
                    int endIdx = args.length;
                    if (args.length >= 4 && args[args.length-1].matches("\\d+")) {
                        endIdx = args.length - 1;
                    }

                    for (int i = 2; i < endIdx; i++) {
                        Player target = Bukkit.getPlayerExact(args[i]);
                        if (target != null) {
                            targets.add(target);
                        } else {
                            sender.sendMessage("§cPlayer not found: " + args[i]);
                        }
                    }

                    if (targets.isEmpty()) {
                        sender.sendMessage("§cNo valid players to record.");
                        return true;
                    }

                    if (replayManager.startRecording(sessionName, targets, duration)) {
                        sender.sendMessage("§aStarted recording session: " + sessionName + " (" + duration + "s)");
                    } else {
                        sender.sendMessage("§cSession with that name already exists!");
                    }
                }

                case "stop" -> {
                    if (args.length < 2) {
                        sender.sendMessage("§cUsage: /replay stop <name>");
                        return true;
                    }
                    String sessionName = joinArgs(args, 1);
                    if (replayManager.stopRecording(sessionName, true)) {
                        sender.sendMessage("§aStopped recording session: " + sessionName);
                    } else {
                        sender.sendMessage("§cNo active session with that name!");
                    }
                }

                case "play" -> {
                    if (!(sender instanceof Player p)) {
                        sender.sendMessage("§c/play command can only be executed by a player!");
                        return true;
                    }
                    if (args.length < 2) {
                        sender.sendMessage("§cUsage: /replay play <name>");
                        return true;
                    }
                    String replayName = joinArgs(args, 1);
                    replayManager.startReplay(replayName, p);
                }

                case "list" -> {
                    int parsedPage = 1;
                    if (args.length >= 2) {
                        try {
                            parsedPage = Math.max(1, Integer.parseInt(args[1]));
                        } catch (NumberFormatException ignored) {
                            sender.sendMessage("§cUsage: /replay list [page]");
                            return true;
                        }
                    }

                    final int page = parsedPage;

                    replayManager.listSavedReplays()
                            .thenAccept(replays -> {
                                Replay.getInstance().getServer().getGlobalRegionScheduler().execute(Replay.getInstance(), () -> {
                                    if (replays.isEmpty()) {
                                        sender.sendMessage("§cNo replays found.");
                                        return;
                                    }

                                    int perPage = Replay.getInstance().getConfig().getInt("list-page-size", 10);
                                    int totalPages = (int) Math.ceil((double) replays.size() / perPage);

                                    if (page > totalPages) {
                                        sender.sendMessage("§cPage out of range. Max page: " + totalPages);
                                        return;
                                    }

                                    int from = (page - 1) * perPage;
                                    int to = Math.min(from + perPage, replays.size());

                                    sender.sendMessage("§6Replays §7(Page " + page + "/" + totalPages + ")");
                                    for (int i = from; i < to; i++) {
                                        sender.sendMessage("§e- §f" + replays.get(i));
                                    }
                                });
                            });
                    return true;
                }

                case "delete" -> {
                    if (args.length < 2) {
                        sender.sendMessage("§cUsage: /replay delete <name>");
                        return true;
                    }
                    String name = joinArgs(args, 1);
                    replayManager.deleteSavedReplay(name)
                            .thenAccept(success -> {
                                Replay.getInstance().getFoliaLib().getScheduler().runNextTick(task -> {
                                    if (success) {
                                        sender.sendMessage("§aDeleted replay: " + name);
                                    } else {
                                        sender.sendMessage("§cReplay not found: " + name);
                                    }
                                });
                            });
                    return true;
                }

                default -> {
                    sender.sendMessage("§cUnknown subcommand: §f" + args[0]);
                    sendHelp(sender);
                }
            }
            return true;
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage("§6§lBetterReplay Commands:");

        if (sender.hasPermission("replay.start"))
            sender.sendMessage("§e/replay start <name> <player1 player2 ...> [seconds] §7- Start recording");

        if (sender.hasPermission("replay.stop")) {
            sender.sendMessage("§e/replay stop <name> §7- Stop an active recording");

            var sessions = replayManager.getActiveRecordings();
            if (!sessions.isEmpty()) {
                sender.sendMessage("§7  Active: §f" + String.join("§7, §f", sessions));
            }
        }

        if (sender.hasPermission("replay.play"))
            sender.sendMessage("§e/replay play <name> §7- Play a saved replay");

        if (sender.hasPermission("replay.list"))
            sender.sendMessage("§e/replay list [page] §7- List saved replays");

        if (sender.hasPermission("replay.delete"))
            sender.sendMessage("§e/replay delete <name> §7- Delete a saved replay");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {

        if (args.length == 1) {
            List<String> completions = new ArrayList<>();

            if (sender.hasPermission("replay.start")) completions.add("start");
            if (sender.hasPermission("replay.stop")) completions.add("stop");
            if (sender.hasPermission("replay.play")) completions.add("play");
            if (sender.hasPermission("replay.delete")) completions.add("delete");
            if (sender.hasPermission("replay.list")) completions.add("list");

            return completions.stream()
                    .filter(s -> s.startsWith(args[0].toLowerCase()))
                    .toList();
        }

        if (args.length >= 2 && (args[0].equalsIgnoreCase("delete") || args[0].equalsIgnoreCase("play"))) {
            if (!sender.hasPermission("replay." + args[0].toLowerCase()))
                return Collections.emptyList();

            List<String> cachedReplays = replayManager.getCachedReplayNames();

            String prefix = joinArgs(args, 1).toLowerCase();

            List<String> matches = cachedReplays.stream()
                    .filter(name -> name.toLowerCase().startsWith(prefix))
                    .toList();
            if (matches.isEmpty() && args.length == 2) {
                return List.of("<name>");
            }
            return matches;
        }

        if (args.length >= 2 && args[0].equalsIgnoreCase("stop")) {
            if (!sender.hasPermission("replay.stop"))
                return Collections.emptyList();

            String prefix = joinArgs(args, 1).toLowerCase();

            List<String> matches = replayManager.getActiveRecordings()
                    .stream()
                    .filter(name -> name.toLowerCase().startsWith(prefix))
                    .toList();
            if (matches.isEmpty() && args.length == 2) {
                return List.of("<name>");
            }
            return matches;
        }


        if (args.length == 2 && args[0].equalsIgnoreCase("start")) {
            if (!sender.hasPermission("replay.start"))
                return Collections.emptyList();
            return List.of("<name>");
        }

        if (args.length == 3 && args[0].equalsIgnoreCase("start")) {
            if (!sender.hasPermission("replay.start"))
                return Collections.emptyList();

            // First player slot — only suggest player names, no duration yet
            String currentArg = args[2].toLowerCase();

            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(name -> name.toLowerCase().startsWith(currentArg))
                    .toList();
        }

        if (args.length >= 4 && args[0].equalsIgnoreCase("start")) {
            if (!sender.hasPermission("replay.start"))
                return Collections.emptyList();

            // Collect already-selected player names so we don't suggest them again
            java.util.Set<String> alreadySelected = new java.util.HashSet<>();
            for (int i = 2; i < args.length - 1; i++) {
                alreadySelected.add(args[i].toLowerCase());
            }

            String currentArg = args[args.length - 1].toLowerCase();

            List<String> suggestions = Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(name -> !alreadySelected.contains(name.toLowerCase()))
                    .filter(name -> name.toLowerCase().startsWith(currentArg))
                    .collect(java.util.stream.Collectors.toCollection(ArrayList::new));

            // Show duration hint now that at least one player is selected
            if (currentArg.isEmpty() || "[seconds]".startsWith(currentArg)) {
                suggestions.add("[seconds]");
            }

            return suggestions;
        }

        return Collections.emptyList();
    }

    private String joinArgs(String[] args, int fromIndex) {
        if (fromIndex >= args.length) {
            return "";
        }
        return String.join(" ", Arrays.copyOfRange(args, fromIndex, args.length)).trim();
    }

}
