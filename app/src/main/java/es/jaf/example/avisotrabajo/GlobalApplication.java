package es.jaf.example.avisotrabajo;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Application;
import android.content.Context;
import android.os.Build;
import android.view.Window;
import android.view.WindowManager;

import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Random;

public class GlobalApplication extends Application {
    @SuppressLint("SimpleDateFormat")
    private static final SimpleDateFormat sdf = new SimpleDateFormat("dd-MM-yyyy HH:mm:ss");
    private static Context appContext;
    private static int iconId;
    private static Activity mCurrentActivity;
    private static int random;

    @Override
    public void onCreate() {
        super.onCreate();
        appContext = getApplicationContext();
        Random r = new Random();
        random = r.nextInt(1000) + 1001;
    }

    public static int getRandom() {
        return random;
    }

    static void setIconId(int value) {
        iconId = value;
        if (mCurrentActivity instanceof MainActivity) {
            ((MainActivity)mCurrentActivity).refreshImage();
        }
    }

    static int getIconId() {
        return iconId;
    }

    static Activity getCurrentActivity(){
        return mCurrentActivity;
    }

    static void setCurrentActivity(Activity value){
        mCurrentActivity = value;
    }

    protected static void showMessage( AlertDialog dialog, String text, boolean isTimeToNotify) {
        AlertDialog.Builder builder = new AlertDialog.Builder(appContext);
        builder.setMessage(text + (isTimeToNotify ? "" : ("\r\n\r\n" + appContext.getString(R.string.not_disturb))))
                .setTitle(appContext.getString(R.string.warning));
        dialog = builder.create();
        Window w = dialog.getWindow();
        if (w != null) {
            w.setType((Build.VERSION.SDK_INT < Build.VERSION_CODES.O) ?
                WindowManager.LayoutParams.TYPE_SYSTEM_ALERT
                :WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY);
        }
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.show();
    }

    static void saveException(String text, Exception ex) {
        FileOutputStream f = null;
        PrintWriter pw = null;
        try {
            File root = android.os.Environment.getExternalStorageDirectory();
            File dir = new File(root.getAbsolutePath() + "/" + BuildConfig.APPLICATION_ID);
            if (!dir.exists()) {
                if (!dir.mkdirs()) {
                    return;
                }
            }
            File file = new File(dir, "exceptions.txt");
            f = new FileOutputStream(file, true);
            pw = new PrintWriter(f);
            pw.println(sdf.format(new Date()) + "\t" + text);
            if (ex != null) {
                pw.println(sdf.format(new Date()) + "\t" + "Message:" + ex.getMessage());
                ex.printStackTrace(pw);
            }
            pw.flush();
            pw.close();
            f.close();
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (pw != null) {
                pw.flush();
                pw.close();
            }
            if (f != null) {
                try {
                    f.close();
                } catch (Exception e) {/**/}
            }
        }
    }
}