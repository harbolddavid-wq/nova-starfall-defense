package com.starlight.icons;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;

/** Transparent shortcut trampoline. Launcher shortcuts must target our own exported Activity. */
public class ShortcutLaunchActivity extends Activity {
    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        try {
            Intent request = getIntent();
            String pkg = request == null ? null : request.getStringExtra("target_package");
            String cls = request == null ? null : request.getStringExtra("target_activity");
            if (pkg == null || cls == null || pkg.isEmpty() || cls.isEmpty()) {
                throw new IllegalArgumentException("Shortcut has no app target");
            }
            Intent i = new Intent(Intent.ACTION_MAIN);
            i.addCategory(Intent.CATEGORY_LAUNCHER);
            i.setComponent(new ComponentName(pkg, cls));
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
            startActivity(i);
        } catch (Exception ex) {
            Toast.makeText(this, "Cannot open this app: " + ex.getMessage(), Toast.LENGTH_LONG).show();
        } finally {
            finish();
        }
    }
}
