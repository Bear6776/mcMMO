package com.gmail.nossr50.runnables.player;

import java.util.concurrent.locks.ReentrantLock;

import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import com.gmail.nossr50.mcMMO;
import com.gmail.nossr50.config.Config;
import com.gmail.nossr50.datatypes.player.McMMOPlayer;
import com.gmail.nossr50.datatypes.player.PlayerProfile;
import com.gmail.nossr50.locale.LocaleLoader;
import com.gmail.nossr50.runnables.commands.McScoreboardKeepTask;
import com.gmail.nossr50.util.Misc;
import com.gmail.nossr50.util.player.UserManager;
import com.gmail.nossr50.util.scoreboards.ScoreboardManager;

public class PlayerProfileLoadingTask extends BukkitRunnable {
    private static final int MAX_TRIES = 5;
    private final Player player;
    private int attempt = 0;
    private ReentrantLock lock = new ReentrantLock();
    private boolean cancelled = false;

    public PlayerProfileLoadingTask(Player player) {
        this.player = player;
    }

    // WARNING: ASYNC TASK
    // DO NOT MODIFY THE McMMOPLAYER FROM THIS CODE
    @Override
    public void run() {
        lock.lock();

        if (this.cancelled) {
            return;
        }

        // Quit if they logged out
        if (!player.isOnline()) {
            mcMMO.p.getLogger().info("Aborting profile loading recovery for " + player.getName() + " - player logged out");
            this.cancel();
            cancelled = true;
            lock.unlock();
            return;
        }

        // Send the message that we're doing the recovery
        if (attempt > 0) {
            player.sendMessage(LocaleLoader.getString("Profile.Loading.Start"));
        }

        PlayerProfile profile = mcMMO.getDatabaseManager().loadPlayerProfile(player.getName(), player.getUniqueId(), true);
        // If successful, schedule the apply
        if (profile.isLoaded()) {
            new ApplySuccessfulProfile(profile).runTask(mcMMO.p);
            this.cancel();
            cancelled = true;
            lock.unlock();
            return;
        }

        // Increment attempt counter and try
        attempt++;

        // If we've failed five times, give up
        if (attempt >= MAX_TRIES) {
            mcMMO.p.getLogger().severe("Giving up on attempting to load the PlayerProfile for " + player.getName());
            mcMMO.p.getServer().broadcast(LocaleLoader.getString("Profile.Loading.AdminFailureNotice", player.getName()), Server.BROADCAST_CHANNEL_ADMINISTRATIVE);
            player.sendMessage(LocaleLoader.getString("Profile.Loading.Failure").split("\n"));
            this.cancel();
            cancelled = true;
            lock.unlock();
            return;
        }
        lock.unlock();
    }

    private class ApplySuccessfulProfile extends BukkitRunnable {
        private final PlayerProfile profile;

        private ApplySuccessfulProfile(PlayerProfile profile) {
            this.profile = profile;
        }

        // Synchronized task
        // No database access permitted
        @Override
        public void run() {
            if (!player.isOnline()) {
                mcMMO.p.getLogger().info("Aborting profile loading recovery for " + player.getName() + " - player logged out");
                return;
            }

            McMMOPlayer mcMMOPlayer = new McMMOPlayer(player, profile);
            UserManager.track(mcMMOPlayer);
            mcMMOPlayer.actualizeRespawnATS();
            ScoreboardManager.setupPlayer(player);

            if (attempt > 0) {
                player.sendMessage(LocaleLoader.getString("Profile.Loading.Success"));
            }

            if (Config.getInstance().getShowStatsAfterLogin()) {
                ScoreboardManager.enablePlayerStatsScoreboard(player);
                new McScoreboardKeepTask(player).runTaskLater(mcMMO.p, 1 * Misc.TICK_CONVERSION_FACTOR);
            }
        }
    }
}


