package com.smartcampus.service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.smartcampus.exception.AcademicNotFoundException;
import com.smartcampus.exception.InvalidInputException;
import com.smartcampus.exception.SmartCampusException;

/**
 * Stores uploaded assignment files outside the web root.
 *
 * <p>The database only receives a random stored filename; the browser never
 * receives the real path.  Downloads go through an authenticated controller
 * that checks ownership before calling {@link #load(String)}.</p>
 */
@Service
public class FileStorageService {

    private static final Logger log = LoggerFactory.getLogger(FileStorageService.class);

    @Value("${app.upload.directory:uploads}")
    private String uploadDirectory;

    @Value("${app.upload.allowed-extensions:pdf,doc,docx,ppt,pptx,xls,xlsx,txt,zip,png,jpg,jpeg}")
    private String allowedExtensions;

    @Value("${app.upload.max-size-mb:20}")
    private long globalMaxSizeMb;

    public Set<String> globalExtensions() {
        return parseTypes(allowedExtensions);
    }

    public long globalMaxSizeBytes() {
        return Math.max(1, globalMaxSizeMb) * 1024L * 1024L;
    }

    public Set<String> parseTypes(String csv) {
        String value = csv == null || csv.isBlank()
                ? "pdf,doc,docx,ppt,pptx,xls,xlsx,txt,zip,png,jpg,jpeg"
                : csv;
        Set<String> result = Arrays.stream(value.split("[,\\s]+"))
                .map(String::trim)
                .map(item -> item.startsWith(".") ? item.substring(1) : item)
                .map(item -> item.toLowerCase(Locale.ROOT))
                .filter(item -> !item.isBlank())
                .collect(Collectors.toCollection(LinkedHashSet::new));
        return result.isEmpty() ? globalExtensions() : result;
    }

    public StoredFile store(MultipartFile file, Set<String> allowedTypes, long maxBytes) {
        String originalName = sanitiseOriginalName(file);
        String extension = extensionOf(originalName);
        Set<String> allowed = allowedTypes == null || allowedTypes.isEmpty()
                ? globalExtensions() : allowedTypes;

        if (!allowed.contains(extension)) {
            throw new InvalidInputException(
                    "File type ." + extension + " is not allowed. Allowed types: "
                            + String.join(", ", allowed) + ".");
        }
        if (file.getSize() <= 0) {
            throw new InvalidInputException("The selected file is empty.");
        }
        if (file.getSize() > globalMaxSizeBytes()) {
            throw new InvalidInputException(
                    "The file is larger than the server upload limit of "
                            + globalMaxSizeMb + " MB.");
        }
        if (file.getSize() > maxBytes) {
            throw new InvalidInputException(
                    "The file is larger than this assignment's limit of "
                            + (maxBytes / (1024L * 1024L)) + " MB.");
        }

        String storedName = UUID.randomUUID().toString().replace("-", "")
                + "." + extension;
        try {
            Path root = root();
            Files.createDirectories(root);
            Path target = root.resolve(storedName).normalize();
            if (!target.startsWith(root)) {
                throw new SmartCampusException("The upload path is invalid.");
            }
            try (InputStream input = file.getInputStream()) {
                Files.copy(input, target, StandardCopyOption.REPLACE_EXISTING);
            }
            return new StoredFile(originalName, storedName,
                    file.getContentType(), file.getSize());
        } catch (IOException ex) {
            log.error("Could not store uploaded assignment file", ex);
            throw new SmartCampusException(
                    "The file could not be stored. Please try again.", ex);
        }
    }

    public Path load(String storedFileName) {
        if (storedFileName == null || storedFileName.isBlank()
                || storedFileName.contains("/") || storedFileName.contains("\\")
                || storedFileName.contains("..")) {
            throw new AcademicNotFoundException("The submission file is missing from storage.");
        }
        Path root = root();
        Path target = root.resolve(storedFileName).normalize();
        if (!target.startsWith(root) || !Files.isRegularFile(target)) {
            throw new AcademicNotFoundException("The submission file is missing from storage.");
        }
        return target;
    }

    public void delete(String storedFileName) {
        if (storedFileName == null || storedFileName.isBlank()) {
            return;
        }
        try {
            Path root = root();
            Path target = root.resolve(storedFileName).normalize();
            if (target.startsWith(root)) {
                Files.deleteIfExists(target);
            }
        } catch (IOException ex) {
            // An orphaned file is not a reason to fail the database operation.
            log.warn("Could not delete an assignment file from storage: {}", ex.getMessage());
        }
    }

    private Path root() {
        return Paths.get(uploadDirectory).toAbsolutePath().normalize();
    }

    private String sanitiseOriginalName(MultipartFile file) {
        String original = file == null ? null : file.getOriginalFilename();
        if (original == null || original.isBlank()) {
            throw new InvalidInputException("Please choose a file to upload.");
        }
        String cleaned = Paths.get(original).getFileName().toString()
                .replaceAll("[\\r\\n\\t]", "_")
                .trim();
        if (cleaned.isBlank()) {
            throw new InvalidInputException("The selected filename is not valid.");
        }
        return cleaned.length() > 255 ? cleaned.substring(cleaned.length() - 255) : cleaned;
    }

    private String extensionOf(String filename) {
        int dot = filename.lastIndexOf('.');
        if (dot < 0 || dot == filename.length() - 1) {
            throw new InvalidInputException("The selected file has no extension.");
        }
        return filename.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    public record StoredFile(String originalFileName, String storedFileName,
                             String contentType, long size) {
    }
}
