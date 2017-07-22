package com.deadmandungeons.audioconnect.command;

import com.deadmandungeons.audioconnect.AudioConnect;
import com.deadmandungeons.audioconnect.AudioConnectConfig;
import com.deadmandungeons.audioconnect.flags.AudioTrack;
import com.deadmandungeons.audioconnect.messages.AudioMessage;
import com.deadmandungeons.deadmanplugin.Messenger;
import com.deadmandungeons.deadmanplugin.command.ArgumentInfo;
import com.deadmandungeons.deadmanplugin.command.Arguments;
import com.deadmandungeons.deadmanplugin.command.Command;
import com.deadmandungeons.deadmanplugin.command.CommandInfo;
import com.deadmandungeons.deadmanplugin.command.SubCommandInfo;
import com.google.common.collect.ImmutableSet;
import com.google.common.io.BaseEncoding;
import com.google.common.net.HttpHeaders;
import com.sk89q.worldguard.bukkit.WorldGuardPlugin;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.managers.storage.StorageException;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import org.apache.commons.lang.StringUtils;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStreamReader;
import java.io.ObjectInputStream;
import java.net.CookieManager;
import java.net.CookieStore;
import java.net.HttpCookie;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

//@formatter:off
@CommandInfo(
    name = "Import",
    permissions = {"audioconnect.admin.import"},
    subCommands = {
        @SubCommandInfo(
            arguments = {
                @ArgumentInfo(argName = "mcjukebox", argType = ArgumentInfo.ArgType.NON_VARIABLE)
            },
            description = "Import the regions and audio files from the current MCJukebox plugin installation. "
                + "Use this for easy conversion from MCJukebox (if installed)."
        ),
        @SubCommandInfo(
            arguments = {
                @ArgumentInfo(argName = "stop", argType = ArgumentInfo.ArgType.NON_VARIABLE)
            },
            description = "Stop the currently running import process"
        )
    }
)//@formatter:on
public class ImportCommand implements Command {

    private static final BaseEncoding CREDENTIALS_ENCODING = BaseEncoding.base64().omitPadding();
    private static final int MAX_CONCURRENT_TASKS = 3;

    private final AudioConnect plugin = AudioConnect.getInstance();

    private final Queue<RegionAudio> importQueue = new ConcurrentLinkedQueue<>();
    private final AtomicInteger runningTasks = new AtomicInteger();
    private final AtomicBoolean abort = new AtomicBoolean();
    private final AtomicBoolean stop = new AtomicBoolean();

    @Override
    public boolean execute(CommandSender sender, Arguments args) {
        Arguments.validateType(args, getClass());

        if (args.getSubCmdIndex() == 0) {
            return importMCJukebox(sender);
        } else if (args.getSubCmdIndex() == 1) {
            return stopImport(sender);
        }

        return false;
    }


    public boolean importMCJukebox(CommandSender sender) {
        Plugin mcJukeboxPlugin = Bukkit.getPluginManager().getPlugin("MCJukebox");
        if (mcJukeboxPlugin == null) {
            plugin.getMessenger().sendErrorMessage(sender, "failed.import-mcjukebox-absent");
            return false;
        }
        if (runningTasks.get() > 0) {
            plugin.getMessenger().sendErrorMessage(sender, "failed.import-running");
            return false;
        }
        AudioConnectConfig config = plugin.getConfiguration();
        if (!config.validate()) {
            plugin.getMessenger().sendErrorMessage(sender, "failed.verify-invalid-config");
            return false;
        }

        Map<String, String> mcJukeboxRegions;
        try {
            mcJukeboxRegions = getMCJukeboxRegions(mcJukeboxPlugin);
            if (mcJukeboxRegions.isEmpty()) {
                plugin.getMessenger().sendErrorMessage(sender, "failed.import-mcjukebox-nothing");
                return false;
            }
        } catch (Exception e) {
            plugin.getMessenger().sendErrorMessage(sender, "failed.import-mcjukebox-error");
            plugin.getLogger().log(Level.WARNING, "Error reading MCJukebox regions.data file", e);
            return false;
        }

        // Reset import state
        importQueue.clear();
        abort.set(false);
        stop.set(false);

        // Initialize import status and task(s)
        int importSize = mcJukeboxRegions.size();
        ImportStatus importStatus = new ImportStatus(sender, importSize);
        ImportTask importTask = new MCJukeboxImportTask(importStatus, config);

        plugin.getMessenger().sendMessage(sender, "misc.import-started", importSize, importTask.importType);

        // Initialize import queue and verify audio URL before running import task
        int queueSize = 0; // regionAudioQueue.size() is not constant-time
        for (Map.Entry<String, String> entry : mcJukeboxRegions.entrySet()) {
            URL audioUrl = getValidAudioUrl(entry.getValue());
            if (audioUrl != null) {
                importQueue.add(new RegionAudio(entry.getKey(), audioUrl.toString()));
                queueSize++;
            } else {
                importStatus.addError(new RegionAudio(entry.getKey(), entry.getValue()), ImportError.INVALID_URL);
            }
        }

        if (importQueue.isEmpty()) {
            // All region audio URLs were invalid
            importStatus.print(importTask.importType);
            return true;
        }

        // Display import progress if it may take some time (at least 3 sequential tasks)
        if (queueSize > MAX_CONCURRENT_TASKS * 3) {
            new ImportProgressTask(importTask.importType, importStatus).start();
        }

        // Run import tasks
        int concurrentTasks = Math.min(queueSize, MAX_CONCURRENT_TASKS);
        for (int i = 0; i < concurrentTasks; i++) {
            runningTasks.incrementAndGet();
            // Uses Cached ThreadPoolExecutor
            Bukkit.getScheduler().runTaskAsynchronously(plugin, importTask);
        }
        return true;
    }

    public boolean stopImport(CommandSender sender) {
        if (runningTasks.get() == 0 || stop.getAndSet(true)) {
            plugin.getMessenger().sendErrorMessage(sender, "failed.import-stopped");
            return false;
        }
        plugin.getMessenger().sendMessage(sender, "succeeded.import-stopping");
        return true;
    }


    private static Map<String, String> getMCJukeboxRegions(Plugin mcJukeboxPlugin) throws Exception {
        // Force any in-memory regions to be saved to file
        Bukkit.getPluginManager().disablePlugin(mcJukeboxPlugin);
        Bukkit.getPluginManager().enablePlugin(mcJukeboxPlugin);

        Map<String, String> regions = new HashMap<>();
        File regionsFile = new File(mcJukeboxPlugin.getDataFolder(), "regions.data");
        try (ObjectInputStream input = new ObjectInputStream(new FileInputStream(regionsFile))) {

            for (Map.Entry<?, ?> entry : ((Map<?, ?>) input.readObject()).entrySet()) {
                String regionId = (String) entry.getKey();
                String audioUrl = (String) entry.getValue();
                // Skip MCJukebox "Shows" which are identified by a leading '@'
                if (!audioUrl.startsWith("@")) {
                    regions.put(regionId, audioUrl);
                }
            }
        } catch (FileNotFoundException e) {
            // return empty regions map
        }
        return regions;
    }

    private static URL getValidAudioUrl(String audioUrl) {
        try {
            // Ensure the string is a valid HTTP URI and URL
            URI audioUri = new URI(audioUrl);
            return audioUri.getScheme() != null && audioUri.getScheme().startsWith("http") ? new URL(audioUrl) : null;
        } catch (MalformedURLException | URISyntaxException e) {
            return null;
        }
    }


    private abstract class ImportTask implements Runnable {

        private static final String IMPORT_PATH = "/account/audio/import";
        private static final String USER_AGENT = "AudioConnect";
        // Give the server plenty of time to download the audio file (but not infinite)
        private static final int RESPONSE_TIMEOUT = 60000;

        private final CookieManager cookieManager = new CookieManager(new ThreadLocalCookieStore(), null);

        private final String importType;
        private final ImportStatus status;

        private final String credentials;
        private final URL importUrl;
        private final URI importUri;

        private ImportTask(String importType, ImportStatus status, AudioConnectConfig config) {
            this.importType = importType;
            this.status = status;

            String rawCredentials = config.getConnectionUserId() + ":" + config.getConnectionUserPassword();
            credentials = CREDENTIALS_ENCODING.encode(rawCredentials.getBytes(StandardCharsets.UTF_8));

            try {
                URL baseUrl = config.getConnectionWebappUrl();

                importUrl = new URL(baseUrl + IMPORT_PATH);
                importUri = importUrl.toURI();
            } catch (MalformedURLException | URISyntaxException e) {
                throw new IllegalStateException("Invalid audio import URL", e);
            }
        }

        @Override
        public void run() {
            try {
                RegionAudio regionAudio;
                while (!abort.get() && !stop.get() && (regionAudio = importQueue.poll()) != null) {
                    importRegionAudio(regionAudio);

                    status.importCount.incrementAndGet();
                }
            } catch (Exception e) {
                abort("failed.verify-error", e);
            }

            // If this task is the last to complete, finish the import
            if (runningTasks.decrementAndGet() == 0 && !abort.get()) {
                saveRegionChanges();

                printStatus();
            }
        }

        private void importRegionAudio(RegionAudio regionAudio) throws Exception {
            String requestParams = "audio-url=" + regionAudio.audioUrl;
            byte[] data = requestParams.getBytes(StandardCharsets.UTF_8);

            HttpURLConnection connection = (HttpURLConnection) importUrl.openConnection();
            connection.setReadTimeout(RESPONSE_TIMEOUT);
            connection.setUseCaches(false);
            connection.setDoOutput(true);
            // do request
            connection.setRequestMethod("POST");
            connection.setRequestProperty(HttpHeaders.USER_AGENT, USER_AGENT);
            connection.setRequestProperty(HttpHeaders.AUTHORIZATION, "Basic " + credentials);
            connection.setRequestProperty(HttpHeaders.CONTENT_TYPE, "application/x-www-form-urlencoded");
            connection.setRequestProperty(HttpHeaders.CONTENT_LENGTH, String.valueOf(data.length));
            // CookieManager requires this non-null requestHeaders parameter but never uses it...
            Map<String, List<String>> emptyRequestHeaders = Collections.emptyMap();
            for (Map.Entry<String, List<String>> cookieHeader : cookieManager.get(importUri, emptyRequestHeaders).entrySet()) {
                connection.setRequestProperty(cookieHeader.getKey(), StringUtils.join(cookieHeader.getValue(), ";"));
            }

            connection.getOutputStream().write(data);

            // get response
            int responseCode = connection.getResponseCode();
            if (responseCode == HttpURLConnection.HTTP_UNAUTHORIZED) {
                // abort if configured credentials are incorrect
                abort("failed.import-response-401", null);
            } else {
                cookieManager.put(importUri, connection.getHeaderFields());

                if (responseCode == HttpURLConnection.HTTP_OK) {
                    String audioId;
                    try (BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()))) {
                        // Read only the first line (server should only respond with the audio ID in response body)
                        audioId = in.readLine();
                    }

                    AudioMessage.validateIdentifier(audioId);
                    status.addSuccess(regionAudio, audioId);

                    // WorldGuard region management is thread safe
                    if (!importRegion(regionAudio.regionId, audioId)) {
                        status.addError(regionAudio, ImportError.UNKNOWN_REGION);
                    }
                } else {
                    ImportError error;
                    try (BufferedReader in = new BufferedReader(new InputStreamReader(connection.getErrorStream()))) {
                        // Read only the first line (server should only respond with the error code in response body)
                        error = ImportError.valueOf(in.readLine());
                    }

                    if (error.stop) {
                        stop(error.msgPath);
                    } else {
                        status.addError(regionAudio, error);
                    }
                }
            }
        }

        private void abort(final String msgPath, final Exception exception) {
            if (!abort.getAndSet(true)) {
                // print on main thread
                Bukkit.getScheduler().runTask(plugin, new Runnable() {
                    @Override
                    public void run() {
                        status.printError(Level.SEVERE, msgPath, exception);
                    }
                });
            }
        }

        private void stop(final String msgPath) {
            if (!stop.getAndSet(true)) {
                // print on main thread
                Bukkit.getScheduler().runTask(plugin, new Runnable() {
                    @Override
                    public void run() {
                        status.printError(Level.WARNING, msgPath, null);
                    }
                });
            }
        }

        private void printStatus() {
            // print on main thread
            Bukkit.getScheduler().runTask(plugin, new Runnable() {
                @Override
                public void run() {
                    status.print(importType);
                }
            });
        }

        protected abstract boolean importRegion(String regionId, String audioId);

        protected abstract void saveRegionChanges();

    }

    private class MCJukeboxImportTask extends ImportTask {

        // Every RegionManager because MCJukebox only stores region ID and ignores the World
        private final Set<RegionManager> regionManagers;

        private MCJukeboxImportTask(ImportStatus status, AudioConnectConfig config) {
            super("MCJukebox", status, config);

            ImmutableSet.Builder<RegionManager> regionManagersBuilder = ImmutableSet.builder();
            for (World world : Bukkit.getWorlds()) {
                RegionManager regionManager = WorldGuardPlugin.inst().getRegionManager(world);
                if (regionManager != null) {
                    regionManagersBuilder.add(regionManager);
                }
            }
            regionManagers = regionManagersBuilder.build();
        }


        @Override
        protected boolean importRegion(String regionId, String audioId) {
            boolean imported = false;
            for (RegionManager regionManager : regionManagers) {
                // Region IDs are unique for each RegionAudio import, so no need to synchronize
                ProtectedRegion region = regionManager.getRegion(regionId);
                if (region != null) {
                    AudioTrack audioTrack = new AudioTrack(audioId);
                    Set<AudioTrack> audioTracks = region.getFlag(plugin.getAudioFlag());
                    audioTracks = (audioTracks != null ? new HashSet<>(audioTracks) : new HashSet<AudioTrack>());
                    if (audioTracks.add(audioTrack)) {
                        region.setFlag(plugin.getAudioFlag(), audioTracks);
                    }
                    imported = true;
                }
            }
            return imported;
        }

        @Override
        protected void saveRegionChanges() {
            for (RegionManager regionManager : regionManagers) {
                try {
                    regionManager.saveChanges();
                } catch (StorageException e) {
                    String msg = "Failed to save '" + regionManager + "' WorldGuard region changes from MCJukebox import";
                    plugin.getLogger().log(Level.WARNING, msg, e);
                }
            }
        }

    }

    private class ImportStatus {

        private final Map<RegionAudio, String> imported = new ConcurrentHashMap<>();
        private final Map<RegionAudio, ImportError> errors = new ConcurrentHashMap<>();
        private final AtomicInteger importCount = new AtomicInteger();

        private final UUID playerId;
        private final int importSize;

        private ImportStatus(CommandSender sender, int importSize) {
            this.playerId = (sender instanceof Player ? ((Player) sender).getUniqueId() : null);
            this.importSize = importSize;
        }

        private void addSuccess(RegionAudio regionAudio, String audioId) {
            imported.put(regionAudio, audioId);
        }

        private void addError(RegionAudio regionAudio, ImportError error) {
            errors.put(regionAudio, error);
        }

        private void printError(final Level level, final String msgPath, final Exception exception) {
            plugin.getLogger().log(level, plugin.getMessenger().getMessage(msgPath, false), exception);
            if (playerId != null) {
                Player player = Bukkit.getPlayer(playerId);
                if (player != null && player.isOnline()) {
                    plugin.getMessenger().sendErrorMessage(player, msgPath);
                }
            }
        }

        private void print(final String importType) {
            Map<String, String> importedAudio = new HashMap<>();
            for (Map.Entry<RegionAudio, String> entry : imported.entrySet()) {
                importedAudio.put(entry.getValue(), entry.getKey().audioUrl);
            }

            Logger logger = plugin.getLogger();
            Messenger messenger = plugin.getMessenger();

            if (playerId != null) {
                Player player = Bukkit.getPlayer(playerId);
                if (player != null && player.isOnline()) {
                    player.sendMessage(getHeaderMessage(messenger, importType, true));

                    messenger.sendMessage(player, "misc.import-count", imported.size(), importSize, importedAudio.size());
                    if (!errors.isEmpty()) {
                        messenger.sendMessage(player, "misc.import-errors", errors.size());
                    }
                    messenger.sendMessage(player, "misc.import-info");
                }
            }

            logger.info(messenger.getMessage("misc.top-bar", false));
            logger.info(getHeaderMessage(messenger, importType, false));
            logger.info(messenger.getMessage("misc.import-count", false, imported.size(), importSize, importedAudio.size()));
            logger.info("");
            logger.info(messenger.getMessage("misc.import-rename", false, plugin.getConfiguration().getConnectionHost()));
            logger.info("");
            logger.info(messenger.getMessage("misc.import-audio-list", false));
            if (importedAudio.isEmpty()) {
                logger.info("  NONE");
            } else {
                for (Map.Entry<String, String> entry : importedAudio.entrySet()) {
                    logger.info("- " + entry.getKey() + " ( " + entry.getValue() + " )");
                }
            }
            logger.info("");
            logger.info(messenger.getMessage("misc.import-region-list", false));
            if (imported.isEmpty()) {
                logger.info("  NONE");
            } else {
                for (Map.Entry<RegionAudio, String> entry : imported.entrySet()) {
                    // If an imported RegionAudio also had an error, It was a partial import and failed at the importing the region
                    if (!errors.containsKey(entry.getKey())) {
                        logger.info("- " + entry.getKey().regionId + " (" + entry.getValue() + ")");
                    }
                }
            }
            logger.info("");
            if (!errors.isEmpty()) {
                logger.info(messenger.getMessage("misc.import-error-list", false));
                Map<ImportError, List<RegionAudio>> uniqueErrors = new HashMap<>();
                for (Map.Entry<RegionAudio, ImportError> entry : errors.entrySet()) {
                    List<RegionAudio> regionAudioList = uniqueErrors.get(entry.getValue());
                    if (regionAudioList == null) {
                        regionAudioList = new ArrayList<>();
                        uniqueErrors.put(entry.getValue(), regionAudioList);
                    }
                    regionAudioList.add(entry.getKey());
                }

                for (Map.Entry<ImportError, List<RegionAudio>> entry : uniqueErrors.entrySet()) {
                    logger.info(messenger.getMessage(entry.getKey().msgPath, false));
                    for (RegionAudio regionAudio : entry.getValue()) {
                        logger.info("- " + regionAudio.regionId + " ( " + regionAudio.audioUrl + " )");
                    }
                    logger.info("");
                }
            }
            logger.info(messenger.getMessage("misc.bottom-bar", false));
        }

        private String getHeaderMessage(Messenger messenger, String importType, boolean colors) {
            if (stop.get()) {
                if (errors.isEmpty()) {
                    return messenger.getMessage("misc.import-stopped", colors, importType);
                } else {
                    return messenger.getMessage("misc.import-stopped-errors", colors, importType);
                }
            } else {
                if (errors.isEmpty()) {
                    return messenger.getMessage("misc.import-finished", colors, importType);
                } else {
                    return messenger.getMessage("misc.import-finished-errors", colors, importType);
                }
            }
        }

    }

    private class ImportProgressTask extends BukkitRunnable {

        private static final int UPDATE_DELAY_TICKS = 20; // update at most once per second

        private final String importType;
        private final ImportStatus status;

        private int lastProgress;

        private ImportProgressTask(String importType, ImportStatus status) {
            this.importType = importType;
            this.status = status;
        }

        @Override
        public void run() {
            if (abort.get() || stop.get() || runningTasks.get() == 0) {
                cancel();
                return;
            }

            int progress = (int) (((double) status.importCount.get() / status.importSize) * 100);
            if (progress > lastProgress) {
                plugin.getLogger().info(plugin.getMessenger().getMessage("misc.import-progress", false, importType, progress));

                if (status.playerId != null) {
                    Player player = Bukkit.getPlayer(status.playerId);
                    if (player != null && player.isOnline()) {
                        plugin.getMessenger().sendMessage(player, "misc.import-progress", importType, progress);
                    }
                }
                lastProgress = progress;
            }
        }

        public void start() {
            runTaskTimer(plugin, UPDATE_DELAY_TICKS, UPDATE_DELAY_TICKS);
        }

    }

    private enum ImportError {
        INVALID_URL("misc.import-error-file", false),
        FILE_NOT_FOUND("misc.import-error-file-404", false),
        DOWNLOAD_ERROR("misc.import-error-file-download", false),
        EMPTY_FILE("misc.import-error-file-empty", false),
        LARGE_FILE("misc.import-error-file-large", false),
        INVALID_AUDIO_FORMAT("misc.import-error-file-format", false),
        INSUFFICIENT_CAPACITY("misc.import-error-capacity", true),
        UNKNOWN_REGION("misc.import-error-region", false);

        private final String msgPath;
        private final boolean stop;

        ImportError(String msgPath, boolean stop) {
            this.msgPath = msgPath;
            this.stop = stop;
        }
    }


    private static class RegionAudio {

        private final String regionId;
        private final String audioUrl;

        public RegionAudio(String regionId, String audioUrl) {
            this.regionId = regionId;
            this.audioUrl = audioUrl;
        }

    }

    private static class ThreadLocalCookieStore implements CookieStore {

        private static final ThreadLocal<CookieStore> cookieJar = new ThreadLocal<CookieStore>() {
            @Override
            protected CookieStore initialValue() {
                return new CookieManager().getCookieStore(); // InMemoryCookieStore
            }
        };

        @Override
        public void add(URI uri, HttpCookie cookie) {
            cookieJar.get().add(uri, cookie);
        }

        @Override
        public List<HttpCookie> get(URI uri) {
            return cookieJar.get().get(uri);
        }

        @Override
        public List<HttpCookie> getCookies() {
            return cookieJar.get().getCookies();
        }

        @Override
        public List<URI> getURIs() {
            return cookieJar.get().getURIs();
        }

        @Override
        public boolean remove(URI uri, HttpCookie cookie) {
            return cookieJar.get().remove(uri, cookie);
        }

        @Override
        public boolean removeAll() {
            return cookieJar.get().removeAll();
        }

    }

}
