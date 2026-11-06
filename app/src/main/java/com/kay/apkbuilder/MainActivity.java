package com.kay.apkbuilder;

import android.app.Activity;
import android.os.Bundle;
import android.graphics.Color;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;

public class MainActivity extends Activity {
 @Override public void onCreate(Bundle state) {
  super.onCreate(state);
  LinearLayout root = new LinearLayout(this);
  root.setOrientation(LinearLayout.VERTICAL);
  root.setGravity(Gravity.CENTER);
  root.setPadding(28,28,28,28);
  root.setBackgroundColor(Color.rgb(5,5,5));
  TextView title = new TextView(this);
  title.setText("KAY / APK BUILDER");
  title.setTextColor(Color.rgb(0,122,255));
  title.setTextSize(26);
  title.setGravity(Gravity.CENTER);
  root.addView(title);
  TextView message = new TextView(this);
  message.setText("Free Edition\nNative Android starter\n\nBuilt with GitHub Actions\n\nNext: connect project creation and AI generation.");
  message.setTextColor(Color.WHITE);
  message.setTextSize(17);
  message.setGravity(Gravity.CENTER);
  message.setPadding(0,30,0,0);
  root.addView(message);
  setContentView(root);
 }
}
