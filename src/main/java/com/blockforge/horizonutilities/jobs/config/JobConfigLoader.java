package com.blockforge.horizonutilities.jobs.config;

import com.blockforge.horizonutilities.HorizonUtilitiesPlugin;
import com.blockforge.horizonutilities.jobs.Job;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.util.*;
import java.util.logging.Logger;

/**
 * Scans the {@code jobs/} folder inside the plugin data directory, copies any
 * missing default job configs from the JAR resources, and loads each YAML file
 * into a {@link Job} object.
 */
public class JobConfigLoader {

    /** Default job config filenames bundled inside the JAR under resources/jobs/. */
    private static final List<String> DEFAULT_JOB_FILES = Arrays.asList(
            "miner.yml",
            "woodcutter.yml",
            "farmer.yml",
            "hunter.yml",
            "fisherman.yml",
            "builder.yml",
            "digger.yml",
            "crafter.yml",
            "enchanter.yml",
            "explorer.yml",
            "weaponsmith.yml",
            "brewer.yml"
    );

    private JobConfigLoader() {}

    /**
     * Loads all job definitions from {@code &lt;dataFolder&gt;/jobs/}.
     * Missing default configs are extracted from JAR resources.
     *
     * @param plugin the plugin instance
     * @return unmodifiable map of jobId -> Job
     */
    public static Map<String, Job> loadAll(HorizonUtilitiesPlugin plugin) {
        Logger log = plugin.getLogger();
        File jobsDir = new File(plugin.getDataFolder(), "jobs");
        if (!jobsDir.exists()) {
            jobsDir.mkdirs();
        }

        // Copy defaults from resources if not present
        for (String fileName : DEFAULT_JOB_FILES) {
            File target = new File(jobsDir, fileName);
            if (!target.exists()) {
                copyDefault(plugin, "jobs/" + fileName, target, log);
            }
        }

        // Load every .yml in the directory
        File[] files = jobsDir.listFiles((dir, name) -> name.endsWith(".yml"));
        if (files == null || files.length == 0) {
            log.warning("[Jobs] No job config files found in " + jobsDir.getPath());
            return Collections.emptyMap();
        }

        Map<String, Job> jobs = new LinkedHashMap<>();
        for (File file : files) {
            String jobId = file.getName().replace(".yml", "").toLowerCase(Locale.ROOT);
            try {
                var config = YamlConfiguration.loadConfiguration(file);
                Job job = Job.loadFromConfig(jobId, config, log);
                jobs.put(jobId, job);
                log.info("[Jobs] Loaded job: " + job.getDisplayName() + " (" + jobId + ")");
            } catch (Exception e) {
                log.warning("[Jobs] Failed to load job '" + jobId + "': " + e.getMessage());
            }
        }

        return Collections.unmodifiableMap(jobs);
    }

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

    private static void copyDefault(HorizonUtilitiesPlugin plugin,
                                    String resourcePath,
                                    File target,
                                    Logger log) {
        try (InputStream in = plugin.getResource(resourcePath)) {
            if (in == null) {
                // Resource not bundled — create a minimal skeleton
                createSkeleton(target, target.getName().replace(".yml", ""), log);
                return;
            }
            target.getParentFile().mkdirs();
            try (OutputStream out = Files.newOutputStream(target.toPath())) {
                in.transferTo(out);
            }
            log.info("[Jobs] Copied default config: " + resourcePath);
        } catch (IOException e) {
            log.warning("[Jobs] Failed to copy default config '" + resourcePath + "': " + e.getMessage());
        }
    }

    private static void createSkeleton(File target, String jobId, Logger log) {
        try {
            target.getParentFile().mkdirs();
            target.createNewFile();
            var cfg = YamlConfiguration.loadConfiguration(target);
            cfg.set("display-name", capitalise(jobId));
            cfg.set("icon", "PAPER");
            cfg.set("description", "A " + jobId + " job.");
            cfg.set("max-level", 100);
            cfg.set("hourly-income-cap", -1);
            cfg.save(target);
            log.info("[Jobs] Created skeleton config for job: " + jobId);
        } catch (IOException e) {
            log.warning("[Jobs] Failed to create skeleton for '" + jobId + "': " + e.getMessage());
        }
    }

    private static String capitalise(String s) {
        if (s == null || s.isEmpty()) return s;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
