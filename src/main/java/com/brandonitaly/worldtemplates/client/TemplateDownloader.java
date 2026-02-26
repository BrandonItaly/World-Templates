package com.brandonitaly.worldtemplates.client;

import com.google.common.hash.Hashing;
import net.minecraft.client.Minecraft;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.URL;
import java.nio.file.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class TemplateDownloader {
    private static final Logger LOGGER = LoggerFactory.getLogger("worldtemplates");

    public static void loadAndExtractTemplate(WorldTemplate template, java.util.function.Consumer<String> onSuccess, Runnable onFail) {
        Path gameDir = Minecraft.getInstance().gameDirectory.toPath();
        Path templatesDir = gameDir.resolve("world_templates");
        String fileName = Path.of(template.templateLocation()).getFileName().toString();
        Path savedTemplateFile = templatesDir.resolve(fileName);

        try {
            String safeFolderName = net.minecraft.util.FileUtil.findAvailableName(gameDir.resolve("saves"), template.folderName(), "");
            Path savesDir = gameDir.resolve("saves").resolve(safeFolderName);

            Files.createDirectories(templatesDir);

            boolean needsDownload = true;
            String downloadUrl = null;
            String expectedChecksum = null;

            // 1. Parse the URL and Checksum from the JSON data
            if (template.downloadURI().isPresent()) {
                downloadUrl = template.downloadURI().get();
                if (downloadUrl.contains("?checksum=")) {
                    String[] parts = downloadUrl.split("\\?checksum=");
                    downloadUrl = parts[0];
                    if (parts.length > 1) {
                        expectedChecksum = parts[1];
                    }
                }
            }

            // 2. Validate existing file using MD5 Checksum
            if (Files.exists(savedTemplateFile)) {
                if (expectedChecksum != null) {
                    String localChecksum = calculateMD5(savedTemplateFile);
                    if (expectedChecksum.equalsIgnoreCase(localChecksum)) {
                        needsDownload = false; // File is complete and intact
                    } else {
                        LOGGER.info("Checksum mismatch for {}. Expected: {}, Got: {}. Redownloading...", fileName, expectedChecksum, localChecksum);
                        Files.delete(savedTemplateFile);
                    }
                } else {
                    // No checksum to verify against; assume existing file is good
                    needsDownload = false;
                }
            } else if (downloadUrl == null) {
                needsDownload = false; // File is missing, but there's no URL to download from
            }

            // 3. Download if necessary
            if (needsDownload && downloadUrl != null) {
                LOGGER.info("Downloading template to: {}", savedTemplateFile);
                URL url = new URL(downloadUrl); 
                try (InputStream in = url.openStream()) {
                    Files.copy(in, savedTemplateFile, StandardCopyOption.REPLACE_EXISTING);
                }
            }

            // 4. Extract the ZIP into the saves folder
            if (Files.exists(savedTemplateFile)) {
                LOGGER.info("Extracting template from {} to {}", savedTemplateFile, savesDir);
                extractZip(savedTemplateFile, savesDir);
                
                // Pass the safeFolderName back to the UI
                onSuccess.accept(safeFolderName);
            } else {
                LOGGER.error("Template file not found and no valid download URI provided.");
                onFail.run();
            }
        } catch (Exception e) {
            LOGGER.error("Failed to process template {}", template.folderName(), e);
            onFail.run();
        }
    }

    private static String calculateMD5(Path file) {
        try {
            String hash = com.google.common.io.Files.asByteSource(file.toFile()).hash(Hashing.md5()).toString();
            return hash;
        } catch (IOException e) {
            LOGGER.warn("Error calculating MD5 checksum for file {}", file, e);
            return null;
        }
    }

    private static void extractZip(Path zipFile, Path targetDir) throws IOException {
        Files.createDirectories(targetDir);
        
        // Convert the target directory to an absolute path first
        Path absoluteTargetDir = targetDir.toAbsolutePath().normalize();

        try (ZipInputStream zis = new ZipInputStream(Files.newInputStream(zipFile))) {
            ZipEntry zipEntry = zis.getNextEntry();
            while (zipEntry != null) {
                // Resolve against the absolute target dir
                Path newFile = absoluteTargetDir.resolve(zipEntry.getName()).normalize();
                
                if (!newFile.startsWith(absoluteTargetDir)) {
                    throw new IOException("Bad zip entry: " + zipEntry.getName());
                }
                
                if (zipEntry.isDirectory()) {
                    Files.createDirectories(newFile);
                } else {
                    Files.createDirectories(newFile.getParent());
                    Files.copy(zis, newFile, StandardCopyOption.REPLACE_EXISTING);
                }
                zipEntry = zis.getNextEntry();
            }
            zis.closeEntry();
        }
    }
}