package dev.anhcraft.timedmmoitems;

import net.Indyuce.mmoitems.MMOItems;
import net.Indyuce.mmoitems.api.item.mmoitem.LiveMMOItem;
import net.Indyuce.mmoitems.stat.data.DoubleData;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.text.SimpleDateFormat;
import java.util.*;

public final class TimedMMOItems extends JavaPlugin {
    public static final ExpiryPeriod EXPIRY_PERIOD = new ExpiryPeriod();
    public static final ExpiryDate EXPIRY_DATE = new ExpiryDate();
    public static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("dd/MM/yyyy");
    private static final long DAY = 60 * 60 * 24;
    private static final long HOUR = 60 * 60;
    private static final long MINUTE = 60;
    public static TimedMMOItems plugin;
    private final Queue<Player> playerQueue = new LinkedList<>();

    public static boolean isMMOItem(ItemStack vanilla) {
        return io.lumine.mythic.lib.api.item.NBTItem.get(vanilla).hasType();
    }

    @Override
    public void onEnable() {
        plugin = this;
        saveDefaultConfig();
        reloadConfig();

        MMOItems.plugin.getStats().register(EXPIRY_PERIOD);
        MMOItems.plugin.getStats().register(EXPIRY_DATE);

        new BukkitRunnable() {
            @Override
            public void run() {
                if (playerQueue.isEmpty()) {
                    playerQueue.addAll(Bukkit.getOnlinePlayers());
                }
                Player player = playerQueue.poll();
                if (player == null || !player.isOnline()) return;
                if (player.hasPermission("timeditems.bypass")) return;
                boolean needUpdate = false;
                boolean hasExpired = false;
                int rmvCounter = 0;
                List<ItemStack> newItems = new LinkedList<>();
                for (ItemStack item : player.getInventory().getContents()) {
                    if (item != null && !item.getType().isAir() && isMMOItem(item)) {
                        LiveMMOItem mmo = new LiveMMOItem(item);
                        if (mmo.hasData(EXPIRY_PERIOD) && !mmo.hasData(EXPIRY_DATE)) {
                            mmo.setData(EXPIRY_DATE, new DoubleData(System.currentTimeMillis() + ((DoubleData) mmo.getData(EXPIRY_PERIOD)).getValue() * 1000));
                            newItems.add(mmo.newBuilder().build());
                            needUpdate = true;
                            continue;
                        }
                        if (getConfig().getBoolean("remove-expired-item") && mmo.hasData(EXPIRY_DATE) && ((DoubleData) mmo.getData(EXPIRY_DATE)).getValue() < System.currentTimeMillis()) {
                            rmvCounter += item.getAmount();
                            needUpdate = true;
                            hasExpired = true;
                            continue;
                        }
                    }
                    newItems.add(item);
                }
                if (!needUpdate) return;

                int rm = rmvCounter;
                boolean u2 = hasExpired;
                player.getInventory().setContents(newItems.toArray(new ItemStack[0]));
                player.updateInventory();
                if (u2) {
                    String msg = Objects.requireNonNull(getConfig().getString("expired-item-removed", ""))
                            .replace("%amount%", Integer.toString(rm));
                    player.sendMessage(ChatColor.translateAlternateColorCodes('&', msg));
                    player.playSound(player.getLocation(), Sound.ENTITY_ITEM_BREAK, 1.0f, 1.0f);
                }
            }
        }.runTaskTimer(this, 0, 20L * getConfig().getInt("item-check-interval"));

        getServer().dispatchCommand(getServer().getConsoleSender(), "mi reload"); // force reload MMOItems
    }

    @Override
    public void onDisable() {
        Bukkit.getScheduler().cancelTasks(this);
    }

    public String formatDuration(long seconds) {
        long days = seconds / DAY;
        seconds = Math.max(0, seconds - days * DAY);
        long hours = seconds / HOUR;
        seconds = Math.max(0, seconds - hours * HOUR);
        long minutes = seconds / MINUTE;
        seconds = Math.max(0, seconds - minutes * MINUTE);
        List<String> args = new ArrayList<>();
        if (days > 0)
            args.add(String.format(Objects.requireNonNull(getConfig().getString("unit-format.day", "%d ngày")), days));
        if (hours > 0)
            args.add(String.format(Objects.requireNonNull(getConfig().getString("unit-format.hour", "%d giờ")), hours));
        if (minutes > 0)
            args.add(String.format(Objects.requireNonNull(getConfig().getString("unit-format.minute", "%d phút")), minutes));
        if (seconds > 0)
            args.add(String.format(Objects.requireNonNull(getConfig().getString("unit-format.second", "%d giây")), seconds));
        return String.join(" ", args);
    }

    public String formatDate(long time) {
        return DATE_FORMAT.format(new Date(time));
    }
}
