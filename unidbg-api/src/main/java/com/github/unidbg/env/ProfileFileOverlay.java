package com.github.unidbg.env;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Read-only guest-path overlay rooted at a profile {@code files/} directory.
 * Guest paths are POSIX-only. Does not follow host symlinks, does not serve
 * {@code /proc/self|pid/fd/*}, and never reads outside the profile directory.
 */
public final class ProfileFileOverlay {

    private final File overlayRoot;
    private final File profileDir;
    private final int configuredPid;

    ProfileFileOverlay(File overlayRoot, File profileDir, int configuredPid) {
        this.overlayRoot = overlayRoot;
        this.profileDir = profileDir;
        this.configuredPid = configuredPid;
    }

    public File getOverlayRoot() {
        return overlayRoot;
    }

    /**
     * Raw bytes for a guest pathname, or {@code null} when the overlay does not contain that file.
     * {@code /proc/self/X} and {@code /proc/<configuredPid>/X} share the same overlay file
     * except {@code fd} which stays on the explicit fd configuration path.
     */
    /**
     * Child names of an overlay directory, or {@code null} when the path is not a directory
     * in the overlay. Never follows host symlinks.
     */
    public String[] list(String pathname) {
        String normalized = normalizeGuestPath(pathname);
        if (normalized == null || isBlockedProcFd(normalized)
                || overlayRoot == null || profileDir == null) {
            return null;
        }
        Path overlayReal = resolveContainedDirectory(overlayRoot, profileDir);
        if (overlayReal == null) {
            return null;
        }
        List<String> candidates = new ArrayList<String>(2);
        candidates.add(normalized);
        String aliased = aliasProcSelf(normalized);
        if (aliased != null && !aliased.equals(normalized) && !isBlockedProcFd(aliased)) {
            candidates.add(aliased);
        }
        for (int i = 0; i < candidates.size(); i++) {
            String[] names = listExact(overlayReal, candidates.get(i));
            if (names != null) {
                return names;
            }
        }
        return null;
    }

    public byte[] read(String pathname) {
        String normalized = normalizeGuestPath(pathname);
        if (normalized == null || isBlockedProcFd(normalized)
                || overlayRoot == null || profileDir == null) {
            return null;
        }
        Path overlayReal = resolveContainedDirectory(overlayRoot, profileDir);
        if (overlayReal == null) {
            return null;
        }
        List<String> candidates = new ArrayList<String>(2);
        candidates.add(normalized);
        String aliased = aliasProcSelf(normalized);
        if (aliased != null && !aliased.equals(normalized) && !isBlockedProcFd(aliased)) {
            candidates.add(aliased);
        }
        for (int i = 0; i < candidates.size(); i++) {
            byte[] data = readExact(overlayReal, candidates.get(i));
            if (data != null) {
                return data;
            }
        }
        return null;
    }

    private String[] listExact(Path overlayReal, String guestPath) {
        String relative = guestPath.startsWith("/") ? guestPath.substring(1) : guestPath;
        if (relative.isEmpty()) {
            return null;
        }
        Path current = overlayReal;
        String[] parts = relative.split("/");
        for (int i = 0; i < parts.length; i++) {
            String part = parts[i];
            if (part.isEmpty() || !isSafePathComponent(part)) {
                return null;
            }
            Path next = current.resolve(part).normalize();
            if (!isInside(next, overlayReal)) {
                return null;
            }
            if (Files.isSymbolicLink(next)) {
                return null;
            }
            if (!Files.isDirectory(next, LinkOption.NOFOLLOW_LINKS)) {
                return null;
            }
            current = next;
        }
        try {
            File dir = current.toFile();
            String[] names = dir.list();
            if (names == null) {
                return null;
            }
            java.util.Arrays.sort(names);
            return names;
        } catch (Exception e) {
            return null;
        }
    }

    private byte[] readExact(Path overlayReal, String guestPath) {
        String relative = guestPath.startsWith("/") ? guestPath.substring(1) : guestPath;
        if (relative.isEmpty()) {
            return null;
        }
        Path current = overlayReal;
        String[] parts = relative.split("/");
        for (int i = 0; i < parts.length; i++) {
            String part = parts[i];
            if (part.isEmpty() || !isSafePathComponent(part)) {
                return null;
            }
            Path next = current.resolve(part).normalize();
            if (!isInside(next, overlayReal)) {
                return null;
            }
            if (Files.isSymbolicLink(next)) {
                return null;
            }
            boolean last = i == parts.length - 1;
            if (last) {
                if (!Files.isRegularFile(next, LinkOption.NOFOLLOW_LINKS)) {
                    return null;
                }
                try {
                    return Files.readAllBytes(next);
                } catch (IOException e) {
                    return null;
                }
            }
            if (!Files.isDirectory(next, LinkOption.NOFOLLOW_LINKS)) {
                return null;
            }
            current = next;
        }
        return null;
    }

    /**
     * Overlay root must be a real (non-symlink) directory strictly inside the profile directory.
     * Re-checked on every read so a later junction/symlink cannot widen the sandbox.
     */
    static Path resolveContainedDirectory(File overlayRoot, File profileDir) {
        if (overlayRoot == null || profileDir == null
                || !overlayRoot.isDirectory() || !profileDir.isDirectory()) {
            return null;
        }
        try {
            Path overlayPath = overlayRoot.toPath();
            Path profilePath = profileDir.toPath();
            if (Files.isSymbolicLink(overlayPath) || Files.isSymbolicLink(profilePath)) {
                return null;
            }
            Path profileReal = profilePath.toRealPath(LinkOption.NOFOLLOW_LINKS);
            Path overlayReal = overlayPath.toRealPath(LinkOption.NOFOLLOW_LINKS);
            if (!isInside(overlayReal, profileReal)) {
                return null;
            }
            return overlayReal;
        } catch (IOException e) {
            return null;
        }
    }

    static boolean isInside(Path child, Path parent) {
        if (child == null || parent == null) {
            return false;
        }
        Path normalizedChild = child.normalize();
        Path normalizedParent = parent.normalize();
        return normalizedChild.startsWith(normalizedParent) && !normalizedChild.equals(normalizedParent);
    }

    static boolean isBlockedProcFd(String normalized) {
        if (normalized == null) {
            return false;
        }
        if ("/proc/self/fd".equals(normalized) || normalized.startsWith("/proc/self/fd/")) {
            return true;
        }
        if (!normalized.startsWith("/proc/")) {
            return false;
        }
        String rest = normalized.substring("/proc/".length());
        int slash = rest.indexOf('/');
        if (slash <= 0) {
            return false;
        }
        String pid = rest.substring(0, slash);
        if (!isAllDigits(pid)) {
            return false;
        }
        String after = rest.substring(slash + 1);
        return "fd".equals(after) || after.startsWith("fd/");
    }

    private static boolean isAllDigits(String value) {
        if (value == null || value.isEmpty()) {
            return false;
        }
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c < '0' || c > '9') {
                return false;
            }
        }
        return true;
    }

    private String aliasProcSelf(String normalized) {
        if (normalized.startsWith("/proc/self/")) {
            if (configuredPid > 0) {
                return "/proc/" + configuredPid + "/" + normalized.substring("/proc/self/".length());
            }
            return null;
        }
        if (configuredPid > 0) {
            String prefix = "/proc/" + configuredPid + "/";
            if (normalized.startsWith(prefix)) {
                return "/proc/self/" + normalized.substring(prefix.length());
            }
        }
        return null;
    }

    /**
     * Absolute POSIX file path in canonical form. Rejects {@code ..}, {@code .}, empty
     * segments, trailing slash, NUL, backslash, drive letters, whitespace, and controls.
     * Does not collapse {@code //} or {@code /./}; those are invalid, not rewritten.
     */
    static String normalizeGuestPath(String pathname) {
        if (pathname == null || pathname.length() < 2 || pathname.charAt(0) != '/') {
            return null;
        }
        if (!isSafeGuestPathChars(pathname) || pathname.charAt(pathname.length() - 1) == '/') {
            return null;
        }
        String[] parts = pathname.split("/", -1);
        if (parts.length < 2 || !parts[0].isEmpty()) {
            return null;
        }
        List<String> out = new ArrayList<String>();
        for (int i = 1; i < parts.length; i++) {
            String part = parts[i];
            if (part.isEmpty() || !isSafePathComponent(part) || guestComponentHasWhitespace(part)) {
                return null;
            }
            out.add(part);
        }
        if (out.isEmpty()) {
            return null;
        }
        StringBuilder sb = new StringBuilder(pathname.length());
        for (int i = 0; i < out.size(); i++) {
            sb.append('/').append(out.get(i));
        }
        return sb.toString();
    }

    private static boolean guestComponentHasWhitespace(String value) {
        for (int i = 0; i < value.length(); i++) {
            if (Character.isWhitespace(value.charAt(i))) {
                return true;
            }
        }
        return false;
    }

    static boolean isSafeGuestPathChars(String pathname) {
        for (int i = 0; i < pathname.length(); i++) {
            char c = pathname.charAt(i);
            if (c == '\0' || c == '\\' || c == ':' || c < 0x20) {
                return false;
            }
        }
        return true;
    }

    static boolean isSafePathComponent(String part) {
        if (part == null || part.isEmpty() || ".".equals(part) || "..".equals(part)) {
            return false;
        }
        return isSafeGuestPathChars(part);
    }
}
