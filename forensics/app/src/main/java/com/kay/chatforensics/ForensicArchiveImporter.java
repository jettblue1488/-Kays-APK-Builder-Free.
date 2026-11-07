package com.kay.chatforensics;

import org.json.JSONArray;
import org.json.JSONObject;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * User-selected ZIP evidence ingestion.
 * Retains the original ZIP untouched in private app evidence storage.
 * Builds a separate, bounded set of working copies for read-only analysis.
 */
public final class ForensicArchiveImporter {
    private ForensicArchiveImporter() {}
    private static final long MAX_ZIP_BYTES = 512L * 1024 * 1024;
    private static final long MAX_DECOMPRESSED_BYTES = 1024L * 1024 * 1024;
    private static final long MAX_SINGLE_FILE_BYTES = 128L * 1024 * 1024;
    private static final long MAX_EXTRACTED_BYTES = 500L * 1024 * 1024;
    private static final int MAX_ENTRIES = 5000;
    private static final int MAX_SAVED = 250;

    public static final class Result {
        public int examined, saved, engineFiles, textFiles, mediaFiles, ignored, invalid;
        public long extractedBytes;
        public File manifest;
        public String description() {
            return "Examined " + examined + " archive entries. Preserved " + saved +
                " files (" + engineFiles + " chat4n6 DB/key sidecars, " +
                textFiles + " text exports, " + mediaFiles + " media files)." +
                " Skipped: " + ignored + "; unsafe paths: " + invalid + ".";
        }
    }

    public static boolean looksLikeArchive(String name) {
        String n = name.toLowerCase(Locale.US);
        return n.endsWith(".zip");
    }

    /** Display-only text classification. The user must not infer attribution from filename alone. */
    public static String guessPlatform(String path, String chosen) {
        String lower = path.toLowerCase(Locale.US);
        if (lower.contains("com.whatsapp") || lower.contains("/whatsapp/")) return "WhatsApp";
        if (lower.contains("org.thoughtcrime.securesms") || lower.contains("/signal/")) return "Signal";
        if (lower.contains("org.telegram") || lower.contains("/telegram/")) return "Telegram";
        if (lower.contains("instagram")) return "Instagram";
        if (lower.contains("snapchat")) return "Snapchat";
        if (lower.contains("tiktok")) return "TikTok";
        if (lower.contains("facebook") || lower.contains("messenger")) return "Facebook Messenger";
        return chosen;
    }

    private static String dbEnginePath(String entry, String chosen) {
        String normalized = entry.toLowerCase(Locale.US).replace('\\','/');
        int slash = normalized.lastIndexOf('/');
        String file = normalized.substring(slash + 1);
        boolean whatsapp = chosen.equals("WhatsApp") || normalized.contains("com.whatsapp") ||
                normalized.contains("/whatsapp/");
        boolean signal = chosen.equals("Signal") || normalized.contains("org.thoughtcrime.securesms") ||
                normalized.contains("/signal/");
        boolean telegram = chosen.equals("Telegram") || normalized.contains("org.telegram") ||
                normalized.contains("/telegram/");
        if (whatsapp) {
            if (file.equals("key") && (normalized.contains("com.whatsapp") || chosen.equals("WhatsApp")))
                return "data/data/com.whatsapp/files/key";
            if (file.equals("wa.db") || file.equals("wa.db-wal") || file.equals("wa.db-shm") ||
                    file.equals("wa.db-journal") || file.equals("msgstore.db") ||
                    file.equals("msgstore.db-wal") || file.equals("msgstore.db-shm") ||
                    file.equals("msgstore.db-journal") || file.equals("msgstore.db.crypt14") ||
                    file.equals("msgstore.db.crypt15"))
                return "data/data/com.whatsapp/databases/" + file;
        }
        if (signal && (file.equals("signal.sqlite") || file.equals("signal.sqlite-wal") ||
                file.equals("signal.sqlite-shm") || file.equals("signal.sqlite-journal") ||
                file.equals("signal.db") || file.equals("signal.db-wal") ||
                file.equals("signal.db-shm") || file.equals("signal.db-journal")))
            return "data/data/org.thoughtcrime.securesms/databases/" + file;
        if (telegram && (file.equals("cache.db") || file.equals("cache.db-wal") ||
                file.equals("cache.db-shm") || file.equals("cache.db-journal") ||
                file.equals("cache4.db") || file.equals("cache4.db-wal") ||
                file.equals("cache4.db-shm") || file.equals("cache4.db-journal")))
            return "data/data/org.telegram.messenger/files/" + file;
        return null;
    }

    private static boolean safeEntry(String name) {
        if(name == null || name.isEmpty() || name.length() > 1024 ||
                name.startsWith("/") || name.startsWith("\\") || name.indexOf('\0') >= 0 ||
                name.matches("^[A-Za-z]:.*")) return false;
        String n=name.replace('\\','/');
        for (String segment : n.split("/")) if(segment.equals("..") || segment.equals(".")) return false;
        return true;
    }

    private static boolean text(String lower) {
        return lower.endsWith(".json") || lower.endsWith(".txt") ||
                lower.endsWith(".csv") || lower.endsWith(".html") || lower.endsWith(".htm") ||
                lower.endsWith(".xml") || lower.endsWith(".log");
    }

    private static boolean media(String lower) {
        return lower.endsWith(".jpg") || lower.endsWith(".jpeg") ||
                lower.endsWith(".png") || lower.endsWith(".webp") ||
                lower.endsWith(".gif") || lower.endsWith(".mp4") ||
                lower.endsWith(".mov") || lower.endsWith(".3gp") ||
                lower.endsWith(".m4a") || lower.endsWith(".mp3") ||
                lower.endsWith(".wav") || lower.endsWith(".ogg") ||
                lower.endsWith(".opus");
    }

    private static void copy(File source, File destination) throws IOException {
        File folder=destination.getParentFile();
        if(folder == null || (!folder.exists() && !folder.mkdirs())) throw new IOException("Cannot create working folder");
        try(InputStream in=new FileInputStream(source); OutputStream out=new FileOutputStream(destination)) {
            byte[] block=new byte[32768]; int n;
            while((n=in.read(block)) != -1) out.write(block,0,n);
        }
    }

    private static String hex(byte[] hash) {
        StringBuilder b=new StringBuilder();
        for(byte x:hash) b.append(String.format(Locale.US,"%02x",x&255));
        return b.toString();
    }

    /**
     * Copies only recognized evidence and limited text/media files.
     * ZIP decompression cannot escape the app-private folder because archive names
     * are never used as destination paths; all target paths are fixed or random.
     */
    public static Result extract(File archive, File evidenceDir, File workingInput,
                                 File reportsDir, String chosen, String archiveId,
                                 String timestamp, String originalName,
                                 String originalSha) throws Exception {
        if(!archive.isFile()) throw new IOException("Archive missing");
        if(archive.length()>MAX_ZIP_BYTES) throw new IOException("ZIP exceeds 512 MB import limit");
        Result result=new Result();
        JSONArray index=new JSONArray();
        Set<String> mappedPaths=new HashSet<>();
        long expanded=0;
        int filesSeen=0;
        File temp=null;
        try(ZipInputStream zip=new ZipInputStream(new BufferedInputStream(new FileInputStream(archive)))) {
            ZipEntry entry;
            byte[] buffer=new byte[32768];
            while((entry=zip.getNextEntry())!=null) {
                if(++filesSeen > MAX_ENTRIES) throw new IOException("Too many archive entries (over 5000)");
                result.examined++;
                String path=entry.getName();
                boolean safe=safeEntry(path);
                if (!safe) result.invalid++;
                String fileName = safe ? path.replace('\\','/').toLowerCase(Locale.US) : "";
                String enginePath = safe ? dbEnginePath(path,chosen) : null;
                boolean isText = safe && text(fileName);
                boolean isMedia = safe && media(fileName);
                boolean wants = !entry.isDirectory() && safe && (enginePath!=null || isText || isMedia);
                boolean canSave = wants && result.saved < MAX_SAVED && result.extractedBytes < MAX_EXTRACTED_BYTES;
                MessageDigest digest = canSave ? MessageDigest.getInstance("SHA-256") : null;
                OutputStream output=null;
                long length=0;
                if(canSave) {
                    temp = new File(evidenceDir,".extract-"+UUID.randomUUID()+".tmp");
                    output=new BufferedOutputStream(new FileOutputStream(temp));
                }
                try {
                    int n;
                    while((n=zip.read(buffer))!=-1) {
                        length+=n;
                        expanded+=n;
                        if(expanded>MAX_DECOMPRESSED_BYTES) throw new IOException("Archive decompressed data exceeds 1 GB limit");
                        if(output!=null) {
                            if(length > MAX_SINGLE_FILE_BYTES || result.extractedBytes+length>MAX_EXTRACTED_BYTES) {
                                output.close(); output=null;
                                temp.delete();temp=null;
                            } else {
                                output.write(buffer,0,n);
                                digest.update(buffer,0,n);
                            }
                        }
                    }
                } finally {
                    if(output!=null)output.close();
                }
                if(temp!=null && temp.isFile() && wants && canSave) {
                    String id=UUID.randomUUID().toString();
                    File preserved=new File(evidenceDir,id+".bin");
                    if(!temp.renameTo(preserved)) {
                        copy(temp,preserved);
                        temp.delete();
                    }
                    temp=null;
                    String platform=guessPlatform(path,chosen);
                    String classification=enginePath!=null?"Chat database or companion evidence":
                            isText?"Text account export":"Media from archive";
                    String actualPath="";
                    // Prevent silently overwriting a DB from earlier archive entries.
                    if(enginePath!=null && mappedPaths.add(enginePath)) {
                        copy(preserved,new File(workingInput,enginePath));
                        result.engineFiles++;
                        actualPath=enginePath;
                    } else if(isText) result.textFiles++;
                    else if(isMedia) result.mediaFiles++;
                    JSONObject rec=new JSONObject();
                    rec.put("id",id);rec.put("name",path.substring(path.lastIndexOf('/')+1));
                    rec.put("source_path",path);rec.put("source_archive",originalName);
                    rec.put("source_archive_sha256",originalSha);
                    rec.put("platform",platform);rec.put("bytes",length);
                    rec.put("sha256",hex(digest.digest()));rec.put("timestamp",timestamp);
                    rec.put("classification",classification);rec.put("engine_path",actualPath);
                    try(OutputStream out=new FileOutputStream(new File(evidenceDir,id+".json"))) {
                        out.write(rec.toString(2).getBytes(StandardCharsets.UTF_8));
                    }
                    index.put(rec);
                    result.saved++; result.extractedBytes+=length;
                } else if(safe && !entry.isDirectory()) result.ignored++;
                zip.closeEntry();
            }
        } finally {
            if(temp!=null)temp.delete();
        }
        JSONObject manifest=new JSONObject();
        manifest.put("tool","Kays Forensics — archive evidence extractor");
        manifest.put("extraction_started_at",timestamp);
        manifest.put("archive_name",originalName);manifest.put("archive_sha256",originalSha);
        manifest.put("platform_selected",chosen);
        manifest.put("entries_examined",result.examined);
        manifest.put("files_saved",result.saved);
        manifest.put("chat4n6_sidecars_saved",result.engineFiles);
        manifest.put("text_files_saved",result.textFiles);
        manifest.put("media_files_saved",result.mediaFiles);
        manifest.put("entries_skipped",result.ignored);manifest.put("invalid_paths",result.invalid);
        manifest.put("extracted_bytes",result.extractedBytes);
        manifest.put("files",index);
        manifest.put("limitation","Only data present in the user-provided archive was extracted. Missing or deleted data cannot be inferred from omissions.");
        File report=new File(reportsDir,"extraction-"+archiveId+".json");
        try(OutputStream out=new FileOutputStream(report)) {
            out.write(manifest.toString(2).getBytes(StandardCharsets.UTF_8));
        }
        result.manifest=report;
        return result;
    }
}
