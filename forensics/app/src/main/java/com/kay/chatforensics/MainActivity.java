package com.kay.chatforensics;

import android.app.Activity;
import android.os.Bundle;
import android.content.Intent;
import android.net.Uri;
import android.graphics.Color;
import android.view.View;
import android.widget.*;
import java.io.*;
import java.security.MessageDigest;
import java.text.SimpleDateFormat;
import java.util.*;
import org.json.*;

public class MainActivity extends Activity {
    private static final int PICK = 10;
    private LinearLayout root, records;
    private Spinner platform;
    private TextView info;
    private final String[] platforms = {"WhatsApp","Signal","Telegram","Snapchat","TikTok","Facebook Messenger","Instagram","Other"};
    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(0xff101722);
        root = new LinearLayout(this);
        root.setPadding(30,30,30,30);
        root.setOrientation(1);
        scroll.addView(root);
        root.addView(text("KAY'S CHAT FORENSICS",26,0xff75d6ff));
        root.addView(text("OFFLINE EVIDENCE ORGANIZER  •  v0.1",12,0xffaabacb));
        root.addView(text("Import files you own or are authorized to examine. Original files are copied into private app storage and hashed with SHA-256.",15,Color.WHITE));
        platform = new Spinner(this);
        platform.setAdapter(new ArrayAdapter<String>(this,android.R.layout.simple_spinner_dropdown_item,platforms));
        root.addView(platform);
        Button importButton = new Button(this);
        importButton.setText("IMPORT EVIDENCE FILE");
        root.addView(importButton);
        importButton.setOnClickListener(v -> {
            Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            i.setType("*/*");
            i.addCategory(Intent.CATEGORY_OPENABLE);
            startActivityForResult(i,PICK);
        });
        info = text("No file selected",13,0xffaabacb);
        root.addView(info);
        root.addView(text("SAVED EVIDENCE",20,Color.WHITE));
        records = new LinearLayout(this);
        records.setOrientation(1);
        root.addView(records);
        root.addView(text("Note: Importing a file does not decrypt it or recover deleted messages. This MVP does not include chat4n6's Rust engine or platform-specific parsers.",13,0xffffcc80));
        setContentView(scroll);
        refresh();
    }
    private TextView text(String s,int size,int color) {
        TextView t = new TextView(this);
        t.setText(s);t.setTextSize(size);t.setTextColor(color);t.setPadding(0,14,0,14);
        return t;
    }
    private File evidenceDir() {
        File f = new File(getFilesDir(),"evidence");
        if(!f.exists()) f.mkdirs();
        return f;
    }
    @Override protected void onActivityResult(int request,int result,Intent data) {
        super.onActivityResult(request,result,data);
        if(request!=PICK || result!=RESULT_OK || data==null || data.getData()==null) return;
        Uri uri=data.getData();
        try {
            String original="evidence";
            try(android.database.Cursor c=getContentResolver().query(uri,new String[]{android.provider.OpenableColumns.DISPLAY_NAME},null,null,null)){
                if(c!=null && c.moveToFirst()) original=c.getString(0);
            }
            String id=UUID.randomUUID().toString();
            File out=new File(evidenceDir(),id+".bin");
            MessageDigest digest=MessageDigest.getInstance("SHA-256");
            long size=0;
            try(InputStream in=getContentResolver().openInputStream(uri);OutputStream output=new FileOutputStream(out)){
                if(in==null) throw new IOException("Cannot read selected file");
                byte[] buf=new byte[32768];int n;
                while((n=in.read(buf))!=-1){output.write(buf,0,n);digest.update(buf,0,n);size+=n;}
            } catch(Exception e){out.delete();throw e;}
            StringBuilder hex=new StringBuilder();
            for(byte x:digest.digest())hex.append(String.format(Locale.US,"%02x",x & 255));
            JSONObject entry=new JSONObject();
            entry.put("id",id);entry.put("name",original);entry.put("platform",platforms[platform.getSelectedItemPosition()]);
            entry.put("timestamp",new SimpleDateFormat("yyyy-MM-dd HH:mm:ss Z",Locale.US).format(new Date()));
            entry.put("sha256",hex.toString());entry.put("bytes",size);
            try(FileOutputStream meta=new FileOutputStream(new File(evidenceDir(),id+".json"))){
                meta.write(entry.toString(2).getBytes("UTF-8"));
            }
            info.setText("Imported: "+original+" ("+size+" bytes)");
            refresh();
        }catch(Exception e){info.setText("Import failed: "+e.getMessage());}
    }
    private void refresh(){
        records.removeAllViews();
        File[] files=evidenceDir().listFiles((d,n)->n.endsWith(".json"));
        if(files==null||files.length==0){records.addView(text("No evidence imported yet.",14,0xffaabacb));return;}
        Arrays.sort(files,(a,b)->Long.compare(b.lastModified(),a.lastModified()));
        for(File f:files){
            try{
                ByteArrayOutputStream buffer=new ByteArrayOutputStream();
                try(InputStream in=new FileInputStream(f)){byte[] b=new byte[4096];int n;while((n=in.read(b))!=-1)buffer.write(b,0,n);}
                JSONObject j=new JSONObject(buffer.toString("UTF-8"));
                String summary=j.optString("platform")+" • "+j.optString("name")+"\n"+j.optString("timestamp")+"\n"+j.optLong("bytes")+" bytes\nSHA-256: "+j.optString("sha256");
                TextView row=text(summary,13,Color.WHITE);
                row.setTextIsSelectable(true);
                records.addView(row);
                View divider=new View(this);divider.setBackgroundColor(0xff354252);
                records.addView(divider,new LinearLayout.LayoutParams(-1,1));
            }catch(Exception e){records.addView(text("Could not read metadata: "+f.getName(),12,0xffff8888));}
        }
    }
}
