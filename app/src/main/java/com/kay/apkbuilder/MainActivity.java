package com.kay.apkbuilder;

import android.app.Activity;
import android.os.Bundle;
import android.content.Intent;
import android.net.Uri;
import android.graphics.Color;
import android.view.Gravity;
import android.view.View;
import android.widget.*;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class MainActivity extends Activity {
    private static final int SAVE_ZIP = 104;
    private EditText appName, description;
    private Spinner template;
    private TextView status;
    private String pendingName, pendingDescription, pendingTemplate;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(Color.rgb(5,5,5));
        LinearLayout root = new LinearLayout(this);
        root.setPadding(24,32,24,32);
        root.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(root);
        TextView title = label("KAY / APK BUILDER", 26, 0xff007aff);
        root.addView(title);
        root.addView(label("FREE EDITION  •  NATIVE ANDROID", 12, 0xffaaaaaa));
        root.addView(label("Create an Android project", 22, Color.WHITE));
        root.addView(label("Enter a name, choose a working starter template, and export a Gradle project ZIP. Upload its contents to a GitHub repository to build an APK.", 14, 0xffbbbbbb));

        root.addView(label("APP NAME", 13, Color.WHITE));
        appName = field("My Android App");
        root.addView(appName);
        root.addView(label("WHAT SHOULD YOUR APP DO?", 13, Color.WHITE));
        description = field("Describe your idea (saved in the generated README)");
        description.setMinLines(3);
        description.setGravity(Gravity.TOP);
        root.addView(description);
        root.addView(label("STARTER TEMPLATE", 13, Color.WHITE));
        template = new Spinner(this);
        String[] options = {"Counter app", "Notes app"};
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, options);
        template.setAdapter(adapter);
        root.addView(template);
        Button generate = new Button(this);
        generate.setText("GENERATE PROJECT ZIP");
        generate.setOnClickListener(v -> beginExport());
        root.addView(generate);
        status = label("Ready. No AI API key required.", 13, 0xff00ff41);
        root.addView(status);
        root.addView(label("The description is recorded in README.md; it does not automatically change the generated app logic. This version creates real, editable native starter projects, not arbitrary AI-generated apps.", 12, 0xff999999));
        setContentView(scroll);
    }

    private TextView label(String value, int size, int color) {
        TextView v = new TextView(this);
        v.setText(value);
        v.setTextSize(size);
        v.setTextColor(color);
        v.setPadding(0,12,0,12);
        return v;
    }
    private EditText field(String hint) {
        EditText v = new EditText(this);
        v.setHint(hint);
        v.setTextColor(Color.WHITE);
        v.setHintTextColor(0xff777777);
        v.setSingleLine(false);
        return v;
    }
    private void beginExport() {
        pendingName = appName.getText().toString().trim();
        pendingDescription = description.getText().toString().trim();
        pendingTemplate = template.getSelectedItemPosition() == 1 ? "notes" : "counter";
        if (pendingName.isEmpty()) { appName.setError("Enter an app name"); return; }
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/zip");
        intent.putExtra(Intent.EXTRA_TITLE, pendingName.replaceAll("[^a-zA-Z0-9_-]", "_") + "-android.zip");
        startActivityForResult(intent, SAVE_ZIP);
    }
    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != SAVE_ZIP || resultCode != RESULT_OK || data == null) return;
        Uri uri = data.getData();
        if (uri == null) return;
        try (OutputStream stream = getContentResolver().openOutputStream(uri)) {
            if (stream == null) throw new Exception("Unable to open destination");
            try (ZipOutputStream zip = new ZipOutputStream(stream)) {
                write(zip, "settings.gradle", "pluginManagement { repositories { google(); mavenCentral(); gradlePluginPortal() } }\n" +
                    "dependencyResolutionManagement { repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS); repositories { google(); mavenCentral() } }\n" +
                    "rootProject.name = 'GeneratedAndroidApp'\ninclude ':app'\n");
                write(zip, "build.gradle", "plugins { id 'com.android.application' version '8.7.3' apply false }\n");
                write(zip, "gradle.properties", "org.gradle.jvmargs=-Xmx2048m -Dfile.encoding=UTF-8\nandroid.useAndroidX=true\n");
                write(zip, "app/build.gradle", "plugins { id 'com.android.application' }\nandroid {\n namespace 'com.kay.generated'\n compileSdk 35\n defaultConfig { applicationId 'com.kay.generated'; minSdk 24; targetSdk 35; versionCode 1; versionName '1.0' }\n compileOptions { sourceCompatibility JavaVersion.VERSION_17; targetCompatibility JavaVersion.VERSION_17 }\n}\n");
                write(zip, "app/src/main/AndroidManifest.xml", "<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\"><application android:label=\"" + xml(pendingName) + "\" android:theme=\"@android:style/Theme.Material.NoActionBar\"><activity android:name=\".MainActivity\" android:exported=\"true\"><intent-filter><action android:name=\"android.intent.action.MAIN\"/><category android:name=\"android.intent.category.LAUNCHER\"/></intent-filter></activity></application></manifest>\n");
                write(zip, "app/src/main/java/com/kay/generated/MainActivity.java", source(pendingTemplate, pendingName));
                write(zip, ".github/workflows/build-apk.yml", workflow());
                write(zip, "README.md", "# " + pendingName.replace("\n"," ") + "\n\n" + pendingDescription + "\n\nTemplate: " + pendingTemplate + "\n\nUpload the extracted files to a GitHub repository. In Actions, run Build Native Android APK and download the artifact.\n");
            }
            status.setText("Project ZIP saved successfully. Upload extracted files to GitHub to build the APK.");
        } catch (Exception e) {
            status.setText("Export failed: " + e.getMessage());
        }
    }
    private static String xml(String s) {
        return s.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\"","&quot;").replace("'","&apos;");
    }
    private static String javaString(String s) {
        return s.replace("\\","\\\\").replace("\"","\\\"").replace("\n"," ").replace("\r"," ");
    }
    private static void write(ZipOutputStream zip, String path, String text) throws Exception {
        zip.putNextEntry(new ZipEntry(path));
        zip.write(text.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }
    private static String source(String kind, String name) {
        String head = "package com.kay.generated;\nimport android.app.Activity;\nimport android.os.Bundle;\nimport android.view.Gravity;\nimport android.widget.*;\n";
        if ("notes".equals(kind)) {
            return head + "public class MainActivity extends Activity {\n" +
                " @Override public void onCreate(Bundle b) { super.onCreate(b); LinearLayout layout=new LinearLayout(this);layout.setOrientation(1);layout.setPadding(24,24,24,24);\n" +
                " TextView title=new TextView(this);title.setText(\"" + javaString(name) + "\");title.setTextSize(24);layout.addView(title);\n" +
                " EditText note=new EditText(this);note.setHint(\"Write a note\");note.setMinLines(4);layout.addView(note);\n" +
                " note.setText(getPreferences(0).getString(\"note\",\"\"));Button save=new Button(this);save.setText(\"Save note\");layout.addView(save);\n" +
                " save.setOnClickListener(v->{getPreferences(0).edit().putString(\"note\",note.getText().toString()).apply();Toast.makeText(this,\"Saved\",Toast.LENGTH_SHORT).show();});setContentView(layout); }\n}\n";
        }
        return head + "public class MainActivity extends Activity {\n" +
            " int count=0; @Override public void onCreate(Bundle b) {super.onCreate(b);LinearLayout layout=new LinearLayout(this);layout.setGravity(Gravity.CENTER);layout.setOrientation(1);\n" +
            " TextView title=new TextView(this);title.setText(\"" + javaString(name) + "\");title.setTextSize(24);layout.addView(title);\n" +
            " TextView number=new TextView(this);number.setText(\"0\");number.setTextSize(42);layout.addView(number);\n" +
            " Button add=new Button(this);add.setText(\"Add 1\");layout.addView(add);add.setOnClickListener(v->number.setText(String.valueOf(++count)));setContentView(layout); }\n}\n";
    }
    private static String workflow() {
        return "name: Build Native Android APK\non:\n  workflow_dispatch:\n  push:\n    branches: [main]\njobs:\n  build:\n    runs-on: ubuntu-latest\n    permissions:\n      contents: read\n    steps:\n      - uses: actions/checkout@v4\n      - uses: actions/setup-java@v4\n        with:\n          distribution: temurin\n          java-version: '17'\n      - name: Install Android SDK packages\n        run: |\n          yes | \"$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager\" --licenses >/dev/null || true\n          \"$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager\" 'platforms;android-35' 'build-tools;35.0.0'\n      - uses: gradle/actions/setup-gradle@v4\n        with:\n          gradle-version: '8.9'\n      - name: Compile APK\n        run: gradle --no-daemon :app:assembleDebug\n      - uses: actions/upload-artifact@v4\n        with:\n          name: generated-android-apk\n          path: app/build/outputs/apk/debug/*.apk\n          if-no-files-found: error\n";
    }
}
