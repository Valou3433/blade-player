package v.blade.ui.settings;

import android.content.pm.PackageInfo;
import android.os.Bundle;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.widget.TextView;

import v.blade.R;

public class AboutActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        //set theme
        setTheme(ThemesActivity.currentAppTheme);

        setContentView(R.layout.activity_about);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        TextView version = findViewById(R.id.about_version);
        try {
            PackageInfo packageInfo = this.getPackageManager().getPackageInfo(getPackageName(), 0);
            version.setText("Version " + packageInfo.versionName);
        } catch (Exception e) {
        }

        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        //set theme
        findViewById(R.id.about_layout).setBackgroundColor(ContextCompat.getColor(this, ThemesActivity.currentColorBackground));
    }
}
