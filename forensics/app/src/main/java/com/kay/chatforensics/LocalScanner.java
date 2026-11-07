package com.kay.chatforensics;

import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.CallLog;
import android.provider.MediaStore;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Local, permission-scoped discovery only. No sandbox bypass or deleted-message promises.
 * Deleted files not exposed by Android's content providers cannot be read here.
 */
public final class LocalScanner {
    private LocalScanner() { }

    public static class Finding {
        public final String kind, platform, title, when, detail, uri, mime;
        public final boolean inTrash;
        Finding(String kind, String platform, String title, String when,
                String detail, String uri, String mime, boolean inTrash) {
            this.kind=kind; this.platform=platform; this.title=title; this.when=when;
            this.detail=detail; this.uri=uri; this.mime=mime; this.inTrash=inTrash;
        }
    }

    private static String formatTime(long millis) {
        if (millis <= 0) return "Unknown date";
        return new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(new Date(millis));
    }

    private static boolean matchesPlatform(String platform, String name, String folder) {
        if ("Other".equals(platform)) return true;
        String path=(name+" "+folder).toLowerCase(Locale.US);
        switch (platform) {
            case "WhatsApp": return path.contains("whatsapp");
            case "Signal": return path.contains("signal");
            case "Telegram": return path.contains("telegram");
            case "Snapchat": return path.contains("snapchat");
            case "TikTok": return path.contains("tiktok") || path.contains("douyin");
            case "Facebook Messenger": return path.contains("messenger") || path.contains("facebook");
            case "Instagram": return path.contains("instagram");
            default: return false;
        }
    }

    /**
     * Returns shared photo/video entries matching a platform label in the visible
     * file name or collection path. Labels are indicators, not identity proof.
     */
    public static List<Finding> media(Context context, String platform, int max) {
        ArrayList<Finding> all=new ArrayList<>();
        queryMedia(context.getContentResolver(), MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                "Photo", platform, max, all);
        queryMedia(context.getContentResolver(), MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                "Video", platform, max, all);
        Collections.sort(all, new Comparator<Finding>() {
            @Override public int compare(Finding a, Finding b) { return b.when.compareTo(a.when); }
        });
        return all.size()>max ? new ArrayList<>(all.subList(0,max)) : all;
    }

    private static void queryMedia(ContentResolver resolver, Uri collection, String kind,
            String platform, int max, ArrayList<Finding> out) {
        boolean modern=Build.VERSION.SDK_INT>=29;
        boolean trashSupported=Build.VERSION.SDK_INT>=30;
        ArrayList<String> cols = new ArrayList<>();
        cols.add(MediaStore.MediaColumns._ID);
        cols.add(MediaStore.MediaColumns.DISPLAY_NAME);
        cols.add(MediaStore.MediaColumns.MIME_TYPE);
        cols.add(MediaStore.MediaColumns.DATE_ADDED);
        if (modern) { cols.add(MediaStore.MediaColumns.RELATIVE_PATH); cols.add(MediaStore.MediaColumns.IS_TRASHED); }
        else cols.add(MediaStore.MediaColumns.DATA);
        String[] projection=cols.toArray(new String[0]);
        Cursor cursor=null;
        try {
            if (trashSupported) {
                try {
                    Bundle opts=new Bundle();
                    opts.putInt(MediaStore.QUERY_ARG_MATCH_TRASHED, MediaStore.MATCH_INCLUDE);
                    cursor=resolver.query(collection,projection,opts,null);
                } catch (IllegalArgumentException unsupported) {
                    cursor=null;
                }
            }
            if (cursor==null) {
                cursor=resolver.query(collection,projection,null,null,MediaStore.MediaColumns.DATE_ADDED+" DESC");
            }
            if (cursor==null) return;
            int id=cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID);
            int name=cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME);
            int mime=cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.MIME_TYPE);
            int date=cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_ADDED);
            int folder=cursor.getColumnIndexOrThrow(modern?MediaStore.MediaColumns.RELATIVE_PATH:MediaStore.MediaColumns.DATA);
            int trashed=modern?cursor.getColumnIndex(MediaStore.MediaColumns.IS_TRASHED):-1;
            while(cursor.moveToNext() && out.size()<max*2) {
                String n=cursor.isNull(name)?"Unnamed":cursor.getString(name);
                String f=cursor.isNull(folder)?"":cursor.getString(folder);
                if (!matchesPlatform(platform,n,f)) continue;
                long itemId=cursor.getLong(id);
                Uri uri=ContentUris.withAppendedId(collection,itemId);
                long seconds=cursor.isNull(date)?0:cursor.getLong(date);
                boolean isTrashed=trashed>=0 && !cursor.isNull(trashed) && cursor.getInt(trashed)==1;
                String type=cursor.isNull(mime)?"*/*":cursor.getString(mime);
                out.add(new Finding(kind,platform,n,formatTime(seconds*1000L),
                    "Folder: "+f+(isTrashed?" | Android trash: may be restorable":""),
                    uri.toString(),type,isTrashed));
            }
        } catch (SecurityException | IllegalArgumentException unavailable) {
            // Permission was denied or this OEM provider does not support the requested columns.
        } finally {
            if(cursor!=null)cursor.close();
        }
    }

    /** Device PHONE call log only: not WhatsApp/Signal/etc calling history. */
    public static List<Finding> phoneCalls(Context context,int max) {
        ArrayList<Finding> list=new ArrayList<>();
        String[] projection={CallLog.Calls.NUMBER,CallLog.Calls.CACHED_NAME,
                CallLog.Calls.DATE,CallLog.Calls.TYPE,CallLog.Calls.DURATION};
        try(Cursor cursor=context.getContentResolver().query(CallLog.Calls.CONTENT_URI,
                projection,null,null,CallLog.Calls.DATE+" DESC")) {
            if(cursor==null) return list;
            int number=cursor.getColumnIndexOrThrow(CallLog.Calls.NUMBER);
            int name=cursor.getColumnIndexOrThrow(CallLog.Calls.CACHED_NAME);
            int date=cursor.getColumnIndexOrThrow(CallLog.Calls.DATE);
            int type=cursor.getColumnIndexOrThrow(CallLog.Calls.TYPE);
            int duration=cursor.getColumnIndexOrThrow(CallLog.Calls.DURATION);
            while(cursor.moveToNext() && list.size()<max) {
                String numberText=cursor.isNull(number)?"Unknown":cursor.getString(number);
                String contact=cursor.isNull(name)?"":cursor.getString(name);
                int callType=cursor.getInt(type);
                String label=callType==CallLog.Calls.INCOMING_TYPE?"Incoming":
                        callType==CallLog.Calls.OUTGOING_TYPE?"Outgoing":
                        callType==CallLog.Calls.MISSED_TYPE?"Missed":"Other";
                list.add(new Finding("Phone call","Device",contact.isEmpty()?numberText:contact+" ("+numberText+")",
                    formatTime(cursor.getLong(date)),label+" | "+cursor.getLong(duration)+" seconds",
                    "","",false));
            }
        } catch(SecurityException | IllegalArgumentException unavailable) {
            throw new SecurityException("Device call log is not accessible. Android may require additional installer approval.", unavailable);
        }
        return list;
    }

    private static String csv(String value) {
        return "\""+(value==null?"":value).replace("\"","\"\"").replace("\r"," ").replace("\n"," ")+"\"";
    }

    public static String toCsv(List<Finding> findings) {
        StringBuilder text=new StringBuilder("kind,platform,name,date,details,android_uri,mime,in_android_trash\n");
        for(Finding f:findings) {
            text.append(csv(f.kind)).append(',').append(csv(f.platform)).append(',')
                .append(csv(f.title)).append(',').append(csv(f.when)).append(',')
                .append(csv(f.detail)).append(',').append(csv(f.uri)).append(',')
                .append(csv(f.mime)).append(',').append(f.inTrash).append('\n');
        }
        return text.toString();
    }
}
