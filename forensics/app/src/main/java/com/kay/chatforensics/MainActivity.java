package com.kay.chatforensics;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Bundle;
import android.content.Intent;
import android.net.Uri;
import android.graphics.Color;
import android.database.Cursor;
import android.provider.OpenableColumns;
import android.webkit.WebView;
import android.view.View;
import android.widget.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.json.JSONObject;

public class MainActivity extends Activity {
    private static final int PICK_FILE = 10, SAVE_REPORT = 11, SAVE_SCAN_CSV = 12;
    private static final int REQUEST_MEDIA = 33, REQUEST_PHONE_LOG = 34;
    private static final String[] PLATFORMS = {"WhatsApp", "Signal", "Telegram", "Snapchat", "TikTok", "Facebook Messenger", "Instagram", "Other"};
    private Spinner platform;
    private TextView status;
    private LinearLayout entries;
    private WebView reportView;
    private File reportForExport;
    private LinearLayout scanRows;
    private final ArrayList<LocalScanner.Finding> lastScan = new ArrayList<>();
    private String scanTitle = "No scan completed yet.";

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        showHome();
    }

    private TextView text(String value, int size, int color) {
        TextView t = new TextView(this);
        t.setText(value);
        t.setTextSize(size);
        t.setTextColor(color);
        t.setPadding(0, 12, 0, 12);
        return t;
    }

    private void showHome() {
        reportView = null;
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(0xff101722);
        LinearLayout root = new LinearLayout(this);
        root.setPadding(32, 32, 32, 32);
        root.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(root);
        root.addView(text("KAY'S CHAT FORENSICS", 25, 0xff73d9ff));
        root.addView(text("OFFLINE • LOCAL EVIDENCE ANALYSIS • v0.3.0", 12, 0xffbfd4e8));
        root.addView(text("Import files you own or have permission to examine. The original imports stay in private storage with SHA-256 hashes.", 14, Color.WHITE));
        root.addView(text("CHOOSE A PLATFORM FOR YOUR IMPORT", 13, Color.WHITE));
        platform = new Spinner(this);
        platform.setAdapter(new ArrayAdapter<String>(this, android.R.layout.simple_spinner_dropdown_item, PLATFORMS));
        root.addView(platform);
        Button add = new Button(this);
        add.setText("ADD EVIDENCE FILE");
        add.setOnClickListener(v -> {
            Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            i.addCategory(Intent.CATEGORY_OPENABLE);
            i.setType("*/*");
            startActivityForResult(i, PICK_FILE);
        });
        root.addView(add);

        Button analyze = new Button(this);
        analyze.setText("RUN CHAT4N6 ANALYSIS");
        analyze.setOnClickListener(v -> analyze());
        root.addView(analyze);

        Button view = new Button(this);
        view.setText("OPEN LATEST REPORT");
        view.setOnClickListener(v -> openLatestReport());
        root.addView(view);

        Button legal = new Button(this);
        legal.setText("THIRD-PARTY LICENSE & COPYRIGHT");
        legal.setOnClickListener(v -> showThirdPartyLicense());
        root.addView(legal);

        Button search = new Button(this);
        search.setText("SEARCH TEXT EXPORTS");
        search.setOnClickListener(v -> askSearch());
        root.addView(search);

        Button export = new Button(this);
        export.setText("EXPORT REPORT ZIP");
        export.setOnClickListener(v -> startExport());
        root.addView(export);

        root.addView(text("ONE-TAP LOCAL SCANS", 19, Color.WHITE));
        Button mediaScan = new Button(this);
        mediaScan.setText("SCAN SELECTED PLATFORM — PHOTOS & VIDEOS");
        mediaScan.setOnClickListener(v -> beginMediaScan());
        root.addView(mediaScan);
        Button calls = new Button(this);
        calls.setText("SCAN DEVICE PHONE CALL HISTORY");
        calls.setOnClickListener(v -> beginPhoneScan());
        root.addView(calls);
        Button csv = new Button(this);
        csv.setText("SAVE SCAN RESULTS AS CSV");
        csv.setOnClickListener(v -> exportScanCsv());
        root.addView(csv);
        root.addView(text("Scans only shared media made accessible by Android and your device's own phone call log after permission. Android trash entries may appear if the system makes them visible. Other apps' private chats, deleted photos, messages and in-app call logs cannot be scanned automatically without accessible forensic evidence. Use ADD EVIDENCE FILE for compatible exports and databases.", 13, 0xffffcb85));

        status = text("Ready. No internet or account needed.", 13, 0xff7cffa8);
        root.addView(status);
        root.addView(text("SCAN RESULTS", 19, Color.WHITE));
        scanRows = new LinearLayout(this);
        scanRows.setOrientation(LinearLayout.VERTICAL);
        root.addView(scanRows);
        renderScanResults();
        root.addView(text("IMPORTS & HASHES", 19, Color.WHITE));
        entries = new LinearLayout(this);
        entries.setOrientation(LinearLayout.VERTICAL);
        root.addView(entries);
        root.addView(text("Supported chat4n6 database inputs: WhatsApp msgstore.db / wa.db / crypt14-15 with an authorized key, plaintext Signal signal.sqlite, and Telegram cache.db or cache4.db. Import related WAL and journal files separately. Snapchat, TikTok, Facebook and Instagram exports are kept and searchable as text where possible; they are not handled by the chat4n6 recovery engine.", 13, 0xffffcb85));
        setContentView(scroll);
        refresh();
    }

    private File evidenceDir() { File d = new File(getFilesDir(), "evidence"); if (!d.exists()) d.mkdirs(); return d; }
    private File inputDir() { File d = new File(getFilesDir(), "forensic-input"); if (!d.exists()) d.mkdirs(); return d; }
    private File reportsDir() { File d = new File(getFilesDir(), "reports"); if (!d.exists()) d.mkdirs(); return d; }
    private String now() { return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss Z", Locale.US).format(new Date()); }

    private String safeFileName(String file) {
        String justName = file.replace('\\', '/');
        justName = justName.substring(justName.lastIndexOf('/') + 1);
        return justName.replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    // Maps imported forensic DBs into the directory structure the upstream engine actually reads.
    private String forensicPath(String kind, String file) {
        String name = file.toLowerCase(Locale.US);
        if ("WhatsApp".equals(kind)) {
            if (name.equals("key")) return "data/data/com.whatsapp/files/key";
            if (name.equals("wa.db") || name.startsWith("wa.db-")) return "data/data/com.whatsapp/databases/" + name;
            if (name.equals("msgstore.db") || name.startsWith("msgstore.db-") ||
                    name.equals("msgstore.db.crypt14") || name.equals("msgstore.db.crypt15")) {
                return "data/data/com.whatsapp/databases/" + name;
            }
        }
        if ("Signal".equals(kind) && (name.equals("signal.sqlite") || name.startsWith("signal.sqlite-") ||
                name.equals("signal.db") || name.startsWith("signal.db-"))) {
            return "data/data/org.thoughtcrime.securesms/databases/" + name;
        }
        if ("Telegram".equals(kind) && (name.equals("cache.db") || name.startsWith("cache.db-") ||
                name.equals("cache4.db") || name.startsWith("cache4.db-"))) {
            return "data/data/org.telegram.messenger/files/" + name;
        }
        return null;
    }

    private void copy(File source, File dest) throws IOException {
        File parent = dest.getParentFile();
        if (parent == null || (!parent.exists() && !parent.mkdirs())) throw new IOException("Cannot prepare import folder");
        try (InputStream in = new FileInputStream(source); OutputStream out = new FileOutputStream(dest)) {
            byte[] buf = new byte[32768]; int count;
            while ((count = in.read(buf)) != -1) out.write(buf, 0, count);
        }
    }

    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (result != RESULT_OK || data == null || data.getData() == null) return;
        if (request == SAVE_REPORT) {
            Uri dest = data.getData();
            File report = reportForExport;
            if (report == null) return;
            status.setText("Exporting report ZIP...");
            new Thread(() -> {
                try (OutputStream output = getContentResolver().openOutputStream(dest)) {
                    if (output == null) throw new IOException("Cannot open destination");
                    try (ZipOutputStream zip = new ZipOutputStream(output)) { zipDirectory(report, report, zip); }
                    runOnUiThread(() -> status.setText("Report ZIP exported."));
                } catch (Exception ex) { runOnUiThread(() -> status.setText("Export failed: " + ex.getMessage())); }
            }).start();
            return;
        }
        if (request == SAVE_SCAN_CSV) {
            try (OutputStream out = getContentResolver().openOutputStream(data.getData())) {
                if (out == null) throw new IOException("Cannot save CSV to selected location");
                out.write(LocalScanner.toCsv(lastScan).getBytes(StandardCharsets.UTF_8));
                status.setText("Scan results exported to CSV.");
            } catch (Exception ex) { status.setText("CSV export failed: " + ex.getMessage()); }
            return;
        }
        if (request != PICK_FILE) return;
        Uri uri = data.getData();
        String kind = PLATFORMS[platform.getSelectedItemPosition()];
        status.setText("Importing...");
        new Thread(() -> {
            File original = null;
            try {
                String filename = "evidence";
                try (Cursor cursor = getContentResolver().query(uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
                    if (cursor != null && cursor.moveToFirst() && !cursor.isNull(0)) filename = cursor.getString(0);
                }
                filename = safeFileName(filename);
                String id = UUID.randomUUID().toString();
                original = new File(evidenceDir(), id + ".bin");
                MessageDigest digest = MessageDigest.getInstance("SHA-256");
                long size = 0;
                try (InputStream in = getContentResolver().openInputStream(uri); OutputStream out = new FileOutputStream(original)) {
                    if (in == null) throw new IOException("Unreadable selected document");
                    byte[] buf = new byte[32768]; int count;
                    while ((count = in.read(buf)) != -1) { out.write(buf, 0, count); digest.update(buf, 0, count); size += count; }
                }
                StringBuilder hash = new StringBuilder();
                for (byte byteValue : digest.digest()) hash.append(String.format(Locale.US, "%02x", byteValue & 255));
                String path = forensicPath(kind, filename);
                if (path != null) copy(original, new File(inputDir(), path));
                JSONObject metadata = new JSONObject();
                metadata.put("id", id); metadata.put("platform", kind); metadata.put("name", filename);
                metadata.put("sha256", hash.toString()); metadata.put("bytes", size);
                metadata.put("timestamp", now()); metadata.put("engine_path", path == null ? "" : path);
                try (OutputStream out = new FileOutputStream(new File(evidenceDir(), id + ".json"))) {
                    out.write(metadata.toString(2).getBytes(StandardCharsets.UTF_8));
                }
                String resultText = "Imported " + filename + (path == null ? " (saved for text search; not an engine input)." : " (available for chat4n6 analysis).");
                runOnUiThread(() -> { status.setText(resultText); refresh(); });
            } catch (Exception ex) {
                if (original != null) original.delete();
                runOnUiThread(() -> status.setText("Import failed: " + ex.getMessage()));
            }
        }).start();
    }

    private JSONObject metadata(File file) throws Exception {
        try (InputStream in = new FileInputStream(file); ByteArrayOutputStream buffer = new ByteArrayOutputStream()) {
            byte[] b = new byte[4096]; int n;
            while ((n = in.read(b)) != -1) buffer.write(b, 0, n);
            return new JSONObject(buffer.toString("UTF-8"));
        }
    }

    private void refresh() {
        entries.removeAllViews();
        File[] files = evidenceDir().listFiles((dir, name) -> name.endsWith(".json"));
        if (files == null || files.length == 0) { entries.addView(text("No files imported yet.", 13, 0xffbfd4e8)); return; }
        Arrays.sort(files, (a,b) -> Long.compare(b.lastModified(), a.lastModified()));
        for (File file : files) {
            try {
                JSONObject j = metadata(file);
                TextView row = text(j.optString("platform") + " • " + j.optString("name") +
                        "\n" + j.optString("timestamp") + "\n" + j.optLong("bytes") +
                        " bytes\nSHA-256: " + j.optString("sha256"), 12, Color.WHITE);
                row.setTextIsSelectable(true);
                entries.addView(row);
                View border = new View(this);
                border.setBackgroundColor(0xff35495c);
                entries.addView(border, new LinearLayout.LayoutParams(-1,1));
            } catch (Exception ex) { entries.addView(text("Unreadable evidence entry", 12, 0xffff8888)); }
        }
    }

    private void analyze() {
        File executable = new File(getApplicationInfo().nativeLibraryDir, "libchat4n6.so");
        if (!executable.isFile()) { status.setText("No Rust engine in this APK; install the native-engine build (arm64)."); return; }
        File[] cases = evidenceDir().listFiles((d, name) -> name.endsWith(".json"));
        if (cases == null || cases.length == 0) { status.setText("Import at least one forensic database first."); return; }
        status.setText("Running chat4n6 offline. Keep the app open...");
        new Thread(() -> {
            try {
                // Migrate prior v0.1 imports and rebuild the expected input paths without modifying originals.
                boolean hasEngineInput = false;
                for (File metaFile : cases) {
                    JSONObject j = metadata(metaFile);
                    String path = forensicPath(j.optString("platform"), j.optString("name"));
                    File saved = new File(evidenceDir(), j.optString("id") + ".bin");
                    if (path != null && saved.isFile()) { copy(saved, new File(inputDir(), path)); hasEngineInput = true; }
                }
                if (!hasEngineInput) {
                    runOnUiThread(() -> status.setText("No compatible database files. Choose WhatsApp, Signal or Telegram and import a supported DB."));
                    return;
                }
                File out = new File(reportsDir(), "run-" + System.currentTimeMillis());
                if (!out.mkdirs()) throw new IOException("Could not create report folder");
                ProcessBuilder pb = new ProcessBuilder(executable.getAbsolutePath(), "run",
                        "--input", inputDir().getAbsolutePath(), "--output", out.getAbsolutePath(),
                        "--case-name", "Kay's Chat Forensics", "--no-unalloc");
                pb.redirectErrorStream(true);
                Process p = pb.start();
                StringBuilder log = new StringBuilder();
                try (InputStream in = p.getInputStream()) {
                    byte[] buf = new byte[4096]; int n;
                    while ((n = in.read(buf)) != -1) {
                        if (log.length() < 50000) log.append(new String(buf, 0, n, StandardCharsets.UTF_8));
                    }
                }
                int exit = p.waitFor();
                if (exit != 0) throw new IOException("Engine exited " + exit + ": " + log.toString().trim());
                File index = new File(out, "index.html");
                if (!index.exists()) throw new IOException("Engine finished but report was not produced");
                getPreferences(0).edit().putString("latest_report", out.getAbsolutePath()).apply();
                runOnUiThread(() -> status.setText("Analysis complete! Tap OPEN LATEST REPORT."));
            } catch (Exception ex) {
                String message = ex.getMessage();
                if (message != null && message.length() > 250) message = message.substring(0, 250);
                String finalMessage = message;
                runOnUiThread(() -> status.setText("Analysis failed: " + finalMessage));
            }
        }).start();
    }

    private File latestReport() {
        File dir = new File(getPreferences(0).getString("latest_report", ""));
        return new File(dir, "index.html").isFile() ? dir : null;
    }

    private void openLatestReport() {
        File dir = latestReport();
        if (dir == null) { status.setText("No completed analysis report yet."); return; }
        reportView = new WebView(this);
        reportView.setBackgroundColor(Color.WHITE);
        reportView.getSettings().setJavaScriptEnabled(false);
        reportView.getSettings().setAllowFileAccess(true);
        reportView.loadUrl(Uri.fromFile(new File(dir, "index.html")).toString());
        setContentView(reportView);
    }

    private void startExport() {
        reportForExport = latestReport();
        if (reportForExport == null) { status.setText("Run analysis before exporting a report."); return; }
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/zip");
        intent.putExtra(Intent.EXTRA_TITLE, "Kays-Forensics-report.zip");
        startActivityForResult(intent, SAVE_REPORT);
    }

    private void zipDirectory(File root, File current, ZipOutputStream zip) throws IOException {
        File[] files = current.listFiles();
        if (files == null) return;
        for (File f : files) {
            if (f.isDirectory()) { zipDirectory(root, f, zip); continue; }
            String rel = root.toURI().relativize(f.toURI()).getPath();
            zip.putNextEntry(new ZipEntry(rel));
            try (InputStream in = new FileInputStream(f)) {
                byte[] bytes = new byte[32768]; int n;
                while ((n = in.read(bytes)) != -1) zip.write(bytes, 0, n);
            }
            zip.closeEntry();
        }
    }

    private void askSearch() {
        EditText query = new EditText(this);
        query.setSingleLine(true);
        query.setHint("Word, name or phrase");
        new AlertDialog.Builder(this).setTitle("Search imported text exports").setView(query)
                .setPositiveButton("Search", (dialog, which) -> {
                    String term = query.getText().toString().trim().toLowerCase(Locale.US);
                    if (term.length() == 0) return;
                    status.setText("Searching text exports...");
                    new Thread(() -> doSearch(term)).start();
                }).setNegativeButton("Cancel", null).show();
    }

    private void doSearch(String term) {
        StringBuilder findings = new StringBuilder();
        int hits = 0;
        File[] metas = evidenceDir().listFiles((dir, name) -> name.endsWith(".json"));
        if (metas == null) return;
        for (File metaFile : metas) {
            if (hits >= 100) break;
            try {
                JSONObject j = metadata(metaFile);
                String name = j.optString("name").toLowerCase(Locale.US);
                if (!(name.endsWith(".txt") || name.endsWith(".csv") ||
                        name.endsWith(".json") || name.endsWith(".html") || name.endsWith(".htm"))) continue;
                File file = new File(evidenceDir(), j.optString("id") + ".bin");
                if (!file.isFile() || file.length() > 5_000_000) continue; // limit memory
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
                    String line; int row = 0;
                    while ((line = reader.readLine()) != null && hits < 100) {
                        row++;
                        int position = line.toLowerCase(Locale.US).indexOf(term);
                        if (position >= 0) {
                            int start = Math.max(0, position - 90);
                            int end = Math.min(line.length(), position + term.length() + 160);
                            String excerpt = line.substring(start, end).replaceAll("<[^>]+>", " ");
                            findings.append(j.optString("platform")).append(" / ").append(j.optString("name")).append(" line ").append(row).append("\n")
                                    .append(excerpt).append("\n\n");
                            hits++;
                        }
                    }
                }
            } catch (Exception ignored) { }
        }
        int count = hits;
        String textResult = findings.length() == 0 ? "No matches in supported text exports. Binary databases are analyzed through chat4n6 instead." : findings.toString();
        runOnUiThread(() -> {
            status.setText("Text search: " + count + " matches (max 100).");
            ScrollView scroller = new ScrollView(this);
            TextView result = text(textResult, 13, Color.BLACK);
            result.setPadding(24, 14, 24, 14);
            result.setTextIsSelectable(true);
            scroller.addView(result);
            new AlertDialog.Builder(this).setTitle("Text export matches").setView(scroller).setPositiveButton("Close", null).show();
        });
    }


    private void showThirdPartyLicense() {
        try (InputStream in = getAssets().open("chat4n6-LICENSE.txt");
             ByteArrayOutputStream bytes = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096];
            int n;
            while ((n = in.read(buffer)) != -1) bytes.write(buffer, 0, n);
            TextView notice = text(new String(bytes.toByteArray(), StandardCharsets.UTF_8), 14, Color.BLACK);
            notice.setPadding(24, 24, 24, 24);
            notice.setTextIsSelectable(true);
            ScrollView scroller = new ScrollView(this);
            scroller.addView(notice);
            new AlertDialog.Builder(this)
                .setTitle("chat4n6 — MIT License & Copyright")
                .setView(scroller)
                .setPositiveButton("Close", null)
                .show();
        } catch (IOException ex) {
            Toast.makeText(this, "Could not read bundled license: " + ex.getMessage(), Toast.LENGTH_LONG).show();
        }
    }


    private void beginMediaScan() {
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            boolean images=checkSelfPermission(android.Manifest.permission.READ_MEDIA_IMAGES)==android.content.pm.PackageManager.PERMISSION_GRANTED;
            boolean videos=checkSelfPermission(android.Manifest.permission.READ_MEDIA_VIDEO)==android.content.pm.PackageManager.PERMISSION_GRANTED;
            boolean selected=android.os.Build.VERSION.SDK_INT>=34 &&
                    checkSelfPermission(android.Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)==android.content.pm.PackageManager.PERMISSION_GRANTED;
            if (!images && !videos && !selected) {
                if (android.os.Build.VERSION.SDK_INT >= 34)
                    requestPermissions(new String[] {android.Manifest.permission.READ_MEDIA_IMAGES,
                            android.Manifest.permission.READ_MEDIA_VIDEO,
                            android.Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED},REQUEST_MEDIA);
                else requestPermissions(new String[]{android.Manifest.permission.READ_MEDIA_IMAGES,
                        android.Manifest.permission.READ_MEDIA_VIDEO},REQUEST_MEDIA);
                return;
            }
        } else if (checkSelfPermission(android.Manifest.permission.READ_EXTERNAL_STORAGE)
                != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{android.Manifest.permission.READ_EXTERNAL_STORAGE}, REQUEST_MEDIA);
            return;
        }
        runMediaScan();
    }

    private void beginPhoneScan() {
        new AlertDialog.Builder(this)
            .setTitle("Read device phone call history?")
            .setMessage("This scans your phone's regular call log, not calls inside WhatsApp, Signal, or other apps. Results stay on this device unless you export them.")
            .setPositiveButton("Scan phone calls", (dialog, which) -> {
                if (checkSelfPermission(android.Manifest.permission.READ_CALL_LOG)
                        != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                    requestPermissions(new String[]{android.Manifest.permission.READ_CALL_LOG}, REQUEST_PHONE_LOG);
                } else runPhoneScan();
            }).setNegativeButton("Cancel",null).show();
    }

    @Override public void onRequestPermissionsResult(int request, String[] permissions, int[] grants) {
        super.onRequestPermissionsResult(request,permissions,grants);
        if (request==REQUEST_MEDIA) {
            boolean granted=false;
            for(int g:grants) if(g==android.content.pm.PackageManager.PERMISSION_GRANTED) granted=true;
            if(granted)runMediaScan();
            else status.setText("Media access denied. You can allow access in Android Settings > Apps > Kays Forensics > Permissions.");
        } else if (request==REQUEST_PHONE_LOG) {
            if(grants.length>0 && grants[0]==android.content.pm.PackageManager.PERMISSION_GRANTED)runPhoneScan();
            else status.setText("Phone call log permission denied or restricted by Android.");
        }
    }

    private void runMediaScan() {
        String chosen = PLATFORMS[platform.getSelectedItemPosition()];
        status.setText("Scanning shared photos and videos for "+chosen+"...");
        new Thread(() -> {
            try {
                List<LocalScanner.Finding> found=LocalScanner.media(this,chosen,250);
                runOnUiThread(() -> {
                    lastScan.clear();
                    lastScan.addAll(found);
                    scanTitle="Media scan: "+chosen+" — "+found.size()+" accessible items (limit 250)";
                    status.setText(found.isEmpty()?"No matching accessible shared-media files found. This does not mean deleted items do not exist.":scanTitle);
                    renderScanResults();
                });
            } catch(Exception ex) {
                runOnUiThread(() -> status.setText("Media scan unavailable: "+ex.getMessage()));
            }
        }).start();
    }

    private void runPhoneScan() {
        status.setText("Reading device phone call history...");
        new Thread(() -> {
            try {
                List<LocalScanner.Finding> found=LocalScanner.phoneCalls(this,250);
                runOnUiThread(() -> {
                    lastScan.clear();
                    lastScan.addAll(found);
                    scanTitle="Device phone call history: "+found.size()+" entries (limit 250)";
                    status.setText(scanTitle);
                    renderScanResults();
                });
            } catch(Exception ex) {
                runOnUiThread(() -> status.setText("Call log unavailable: "+ex.getMessage()));
            }
        }).start();
    }

    private void renderScanResults() {
        if (scanRows==null) return;
        scanRows.removeAllViews();
        scanRows.addView(text(scanTitle,13,0xffbfd4e8));
        int displayed=0;
        for(LocalScanner.Finding f:lastScan) {
            if(displayed++>=75)break;
            String label=f.kind+": "+f.title+"\n"+f.when+"\n"+f.detail;
            TextView entry=text(label,13,f.inTrash?0xffffca82:Color.WHITE);
            entry.setTextIsSelectable(true);
            if(f.uri!=null && !f.uri.isEmpty()) {
                entry.setOnClickListener(v -> {
                    try {
                        Intent i=new Intent(Intent.ACTION_VIEW);
                        i.setDataAndType(Uri.parse(f.uri),f.mime);
                        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                        startActivity(i);
                    } catch(Exception ex) {
                        Toast.makeText(this,"Cannot open this media item; it may require restoration in your Gallery.",Toast.LENGTH_LONG).show();
                    }
                });
            }
            scanRows.addView(entry);
            View line=new View(this);
            line.setBackgroundColor(0xff35495c);
            scanRows.addView(line,new LinearLayout.LayoutParams(-1,1));
        }
        if(lastScan.size()>75)scanRows.addView(text("Showing 75 items. Save the CSV to see all scanned results.",12,0xffbfd4e8));
    }

    private void exportScanCsv() {
        if(lastScan.isEmpty()) { status.setText("No results to export. Run a scan first."); return; }
        Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("text/csv");
        i.putExtra(Intent.EXTRA_TITLE,"Kays-Forensics-local-scan.csv");
        startActivityForResult(i,SAVE_SCAN_CSV);
    }

    @Override public void onBackPressed() {
        if (reportView != null && reportView.canGoBack()) { reportView.goBack(); return; }
        if (reportView != null) { reportView.destroy(); showHome(); return; }
        super.onBackPressed();
    }
}
