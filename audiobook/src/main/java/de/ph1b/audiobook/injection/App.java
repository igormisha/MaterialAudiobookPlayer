/*
 * This file is part of Material Audiobook Player.
 *
 * Material Audiobook Player is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or any later version.
 *
 * Material Audiobook Player is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Material Audiobook Player. If not, see <http://www.gnu.org/licenses/>.
 * /licenses/>.
 */

package de.ph1b.audiobook.injection;

import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.os.Environment;
import android.preference.PreferenceManager;
import android.util.Log;

import com.google.common.io.Files;
import com.squareup.leakcanary.LeakCanary;
import com.squareup.leakcanary.RefWatcher;

import org.acra.ACRA;
import org.acra.annotation.ReportsCrashes;
import org.acra.collector.CrashReportData;
import org.acra.sender.HttpSender;
import org.acra.sender.ReportSender;
import org.acra.sender.ReportSenderException;
import org.acra.util.JSONReportBuilder;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;

import javax.inject.Inject;
import javax.inject.Singleton;

import dagger.Component;
import de.ph1b.audiobook.BuildConfig;
import de.ph1b.audiobook.activity.BaseActivity;
import de.ph1b.audiobook.activity.BookActivity;
import de.ph1b.audiobook.activity.DependencyLicensesActivity;
import de.ph1b.audiobook.adapter.BookShelfAdapter;
import de.ph1b.audiobook.adapter.BookmarkAdapter;
import de.ph1b.audiobook.dialog.BookmarkDialogFragment;
import de.ph1b.audiobook.dialog.EditBookTitleDialogFragment;
import de.ph1b.audiobook.dialog.EditCoverDialogFragment;
import de.ph1b.audiobook.dialog.JumpToPositionDialogFragment;
import de.ph1b.audiobook.dialog.SeekDialogFragment;
import de.ph1b.audiobook.dialog.prefs.AutoRewindDialogFragment;
import de.ph1b.audiobook.dialog.prefs.PlaybackSpeedDialogFragment;
import de.ph1b.audiobook.dialog.prefs.SleepDialogFragment;
import de.ph1b.audiobook.dialog.prefs.ThemePickerDialogFragment;
import de.ph1b.audiobook.fragment.BookPlayFragment;
import de.ph1b.audiobook.fragment.SettingsFragment;
import de.ph1b.audiobook.model.BookAdder;
import de.ph1b.audiobook.persistence.BookChest;
import de.ph1b.audiobook.persistence.PrefsManager;
import de.ph1b.audiobook.playback.BookReaderService;
import de.ph1b.audiobook.playback.MediaPlayerController;
import de.ph1b.audiobook.playback.PlayStateManager;
import de.ph1b.audiobook.playback.WidgetUpdateService;
import de.ph1b.audiobook.presenter.BookShelfBasePresenter;
import de.ph1b.audiobook.presenter.BookShelfPresenter;
import de.ph1b.audiobook.presenter.FolderChooserPresenter;
import de.ph1b.audiobook.presenter.FolderOverviewPresenter;
import de.ph1b.audiobook.uitools.CoverReplacement;
import de.ph1b.audiobook.view.FolderChooserActivity;
import de.ph1b.audiobook.view.FolderOverviewActivity;
import de.ph1b.audiobook.view.fragment.BookShelfFragment;
import timber.log.Timber;

@ReportsCrashes(
        httpMethod = HttpSender.Method.PUT,
        reportType = HttpSender.Type.JSON,
        formUri = "http://acra-63e870.smileupps.com/acra-material/_design/acra-storage/_update/report",
        formUriBasicAuthLogin = "97user",
        formUriBasicAuthPassword = "sUjg9VkOgxTZbzVL",
        sendReportsAtShutdown = false) // TODO: Remove this once ACRA issue #332 is fixed
public class App extends Application {

    private static ApplicationComponent applicationComponent;
    private static RefWatcher refWatcher;
    @Inject
    BookAdder bookAdder;

    public static void leakWatch(Object object) {
        refWatcher.watch(object);
    }

    @Override
    public void onCreate() {
        super.onCreate();

        // init acra and send breadcrumbs
        ACRA.init(this);
        Timber.plant(new BreadcrumbTree());

        if (BuildConfig.DEBUG) {
            // init timber
            Timber.plant(new Timber.DebugTree());
            // also write to disc here.
            Timber.plant(new WriteToDiscTree());

            // force enable acra in debug mode
            PreferenceManager.getDefaultSharedPreferences(this)
                    .edit()
                    .putBoolean("acra.enable", true)
                    .apply();

            // remove default senders
            ACRA.getErrorReporter().removeAllReportSenders();

            // forward crashes to timber
            ACRA.getErrorReporter().addReportSender(new ReportSender() {
                @Override
                public void send(Context context, CrashReportData errorContent) throws ReportSenderException {
                    try {
                        Timber.e("Timber caught %s", errorContent.toJSON().toString());
                    } catch (JSONReportBuilder.JSONReportException e) {
                        e.printStackTrace();
                    }
                }
            });
        }
        Timber.i("onCreate");
        refWatcher = LeakCanary.install(this);

        applicationComponent = newComponent();
        component().inject(this);

        bookAdder.scanForFiles(true);
        startService(new Intent(this, BookReaderService.class));
    }

    protected ApplicationComponent newComponent() {
        return DaggerApp_ApplicationComponent.builder()
                .androidModule(new AndroidModule(this))
                .build();
    }

    public static ApplicationComponent component() {
        return applicationComponent;
    }

    @Singleton
    @Component(modules = {BaseModule.class, AndroidModule.class, PresenterModule.class})
    public interface ApplicationComponent {

        BookShelfBasePresenter getBookShelfBasePresenter();

        BookChest bookChest();

        Context getContext();

        PrefsManager getPrefsManager();

        BookAdder getBookAdder();

        MediaPlayerController mediaPlayerController();

        PlayStateManager playStateManager();

        void inject(WidgetUpdateService target);

        void inject(EditBookTitleDialogFragment target);

        void inject(BookmarkAdapter target);

        void inject(CoverReplacement target);

        void inject(BaseActivity target);

        void inject(FolderChooserActivity target);

        void inject(DependencyLicensesActivity target);

        void inject(ThemePickerDialogFragment target);

        void inject(SeekDialogFragment target);

        void inject(EditCoverDialogFragment target);

        void inject(JumpToPositionDialogFragment target);

        void inject(App target);

        void inject(BookReaderService target);

        void inject(SettingsFragment target);

        void inject(SleepDialogFragment target);

        void inject(PlaybackSpeedDialogFragment target);

        void inject(BookActivity target);

        void inject(FolderChooserPresenter target);

        void inject(BookPlayFragment target);

        void inject(BookmarkDialogFragment target);

        void inject(AutoRewindDialogFragment target);

        void inject(FolderOverviewActivity target);

        void inject(BookShelfAdapter target);

        void inject(BookShelfFragment target);

        void inject(BookShelfPresenter target);

        void inject(FolderOverviewPresenter target);
    }

    private abstract static class FormattedTree extends Timber.DebugTree {
        @Override
        protected void log(int priority, String tag, String message, Throwable t) {
            onLogGathered(priorityToPrefix(priority) + "/[" + tag + "]\t" + message + "\n");
        }

        abstract void onLogGathered(String message);


        /**
         * Maps Log priority to Strings
         *
         * @param priority priority
         * @return the mapped string or the priority as a string if no mapping could be made.
         */
        private static String priorityToPrefix(int priority) {
            switch (priority) {
                case Log.VERBOSE:
                    return "V";
                case Log.DEBUG:
                    return "D";
                case Log.INFO:
                    return "I";
                case Log.WARN:
                    return "W";
                case Log.ERROR:
                    return "E";
                case Log.ASSERT:
                    return "A";
                default:
                    return String.valueOf(priority);
            }
        }
    }

    /**
     * Curtom tree that adds regular logs as custom data to acra.
     */
    private static class BreadcrumbTree extends FormattedTree {

        private static final int CRUMBS_AMOUNT = 200;
        private int crumbCount = 0;

        public BreadcrumbTree() {
            ACRA.getErrorReporter().clearCustomData();
        }

        @Override
        void onLogGathered(String message) {
            ACRA.getErrorReporter().putCustomData(String.valueOf(getNextCrumbNumber()), message);
        }

        /**
         * Returns the number of the next breadcrumb.
         *
         * @return the next crumb number.
         */
        private int getNextCrumbNumber() {
            // returns current value and increases the next one by 1. When the limit is reached it will
            // reset the crumb.
            int nextCrumb = crumbCount;
            crumbCount++;
            if (crumbCount >= CRUMBS_AMOUNT) {
                crumbCount = 0;
            }
            return nextCrumb;
        }
    }

    /**
     * Custom tree that logs to storage.
     */
    private static class WriteToDiscTree extends FormattedTree {

        private final File LOG_FILE = new File(Environment.getExternalStorageDirectory(), "materialaudiobookplayer.log");

        /**
         * Makes sure that the log file exists
         */
        private void ensureFileExists() {
            if (!LOG_FILE.exists()) {
                try {
                    Files.createParentDirs(LOG_FILE);
                    //noinspection ResultOfMethodCallIgnored
                    LOG_FILE.createNewFile();
                } catch (IOException ignored) {
                }
            }
        }

        @Override
        void onLogGathered(String message) {
            ensureFileExists();
            try {
                Files.append(message, LOG_FILE, Charset.forName("UTF-8"));
            } catch (IOException ignored) {
            }
        }
    }
}