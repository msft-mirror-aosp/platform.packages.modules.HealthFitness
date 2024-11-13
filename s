[1m[0m[38;5;11m───────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────[0m
[38;5;11mmodified: service/java/com/android/server/healthconnect/HealthConnectDailyJobs.java
[38;5;11m───────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────[0m
[36m[1m[38;5;13m@ service/java/com/android/server/healthconnect/HealthConnectDailyJobs.java:97 @[1m[1m[38;5;146m public class HealthConnectDailyJobs {[0m
        int userId = params.getExtras().getInt(EXTRA_USER_ID, /* defaultValue= */ DEFAULT_INT);[m
        UserHandle userHandle = UserHandle.getUserHandleForUid(userId);[m
        UsageStatsCollector usageStatsCollector =[m
[1m[38;5;1m[31m                new UsageStatsCollector(context, userHandle, preferenceHelper, accessLogsHelper[7m[27m);[m[0m
[1m[38;5;2m[32m[m[32m                new UsageStatsCollector(context, userHandle, preferenceHelper, accessLogsHelper[7m, timeSource[27m);[m[0m
        AutoDeleteService.startAutoDelete([m
                context,[m
                healthDataCategoryPriorityHelper,[m
[36m[1m[38;5;13m@ service/java/com/android/server/healthconnect/HealthConnectDailyJobs.java:112 @[1m[1m[38;5;146m public class HealthConnectDailyJobs {[0m
                preferenceHelper,[m
                transactionManager,[m
                medicalDataSourceHelper,[m
[1m[38;5;1m[31m                medicalResourceHelper,[m[0m
[1m[38;5;1m[31m                timeSource);[m[0m
[1m[38;5;2m[32m[m[32m                medicalResourceHelper);[m[0m
    }[m
}[m
[1m[0m[38;5;11m───────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────[0m
[38;5;11mmodified: service/java/com/android/server/healthconnect/logging/DailyLoggingService.java
[38;5;11m───────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────[0m
[36m[1m[38;5;13m@ service/java/com/android/server/healthconnect/logging/DailyLoggingService.java:45 @[1m[1m[38;5;146m public class DailyLoggingService {[0m
            PreferenceHelper preferenceHelper,[m
            TransactionManager transactionManager,[m
            MedicalDataSourceHelper medicalDataSourceHelper,[m
[1m[38;5;1m[31m            MedicalResourceHelper medicalResourceHelper,[m[0m
[1m[38;5;1m[31m            TimeSource timeSource) {[m[0m
[1m[38;5;2m[32m[m[32m            MedicalResourceHelper medicalResourceHelper) {[m[0m
        logDatabaseStats(context, transactionManager);[m
        logUsageStats([m
                context,[m
                usageStatsCollector,[m
                preferenceHelper,[m
                medicalDataSourceHelper,[m
[1m[38;5;1m[31m                medicalResourceHelper,[m[0m
[1m[38;5;1m[31m                timeSource);[m[0m
[1m[38;5;2m[32m[m[32m                medicalResourceHelper);[m[0m
    }[m
[m
    private static void logDatabaseStats(Context context, TransactionManager transactionManager) {[m
[36m[1m[38;5;13m@ service/java/com/android/server/healthconnect/logging/DailyLoggingService.java:68 @[1m[1m[38;5;146m public class DailyLoggingService {[0m
            UsageStatsCollector usageStatsCollector,[m
            PreferenceHelper preferenceHelper,[m
            MedicalDataSourceHelper medicalDataSourceHelper,[m
[1m[38;5;1m[31m            MedicalResourceHelper medicalResourceHelper,[m[0m
[1m[38;5;1m[31m            TimeSource timeSource) {[m[0m
[1m[38;5;2m[32m[m[32m            MedicalResourceHelper medicalResourceHelper) {[m[0m
        try {[m
            UsageStatsLogger.log([m
                    context,[m
                    usageStatsCollector,[m
                    preferenceHelper,[m
                    medicalDataSourceHelper,[m
[1m[38;5;1m[31m                    medicalResourceHelper,[m[0m
[1m[38;5;1m[31m                    timeSource);[m[0m
[1m[38;5;2m[32m[m[32m                    medicalResourceHelper);[m[0m
        } catch (Exception exception) {[m
            Slog.e(HEALTH_CONNECT_DAILY_LOGGING_SERVICE, "Failed to log usage stats", exception);[m
        }[m
[1m[0m[38;5;11m───────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────[0m
[38;5;11mmodified: service/java/com/android/server/healthconnect/logging/UsageStatsCollector.java
[38;5;11m───────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────[0m
[36m[1m[38;5;13m@ service/java/com/android/server/healthconnect/logging/UsageStatsCollector.java:20 @[1m[0m
package com.android.server.healthconnect.logging;[m
[m
import static android.content.pm.PackageManager.GET_PERMISSIONS;[m
[1m[38;5;2m[32m[m[32mimport static android.health.connect.HealthPermissions.WRITE_MEDICAL_DATA;[m[0m
[7m[32m [m
[1m[38;5;2m[32m[m[32mimport static com.android.healthfitness.flags.AconfigFlagHelper.isPersonalHealthRecordEnabled;[m[0m
[m
import android.content.Context;[m
import android.content.pm.PackageInfo;[m
import android.content.pm.PackageManager;[m
import android.health.connect.HealthConnectManager;[m
[1m[38;5;2m[32m[m[32mimport android.health.connect.HealthPermissions;[m[0m
import android.os.UserHandle;[m
[m
import com.android.server.healthconnect.permission.PackageInfoUtils;[m
import com.android.server.healthconnect.storage.datatypehelpers.AccessLogsHelper;[m
import com.android.server.healthconnect.storage.datatypehelpers.PreferenceHelper;[m
[1m[38;5;2m[32m[m[32mimport com.android.server.healthconnect.utils.TimeSource;[m[0m
[m
import java.time.Instant;[m
import java.time.temporal.ChronoUnit;[m
import java.util.HashMap;[m
import java.util.List;[m
import java.util.Map;[m
[1m[38;5;2m[32m[m[32mimport java.util.Set;[m[0m
[m
/**[m
 * Collects Health Connect usage stats.[m
[36m[1m[38;5;13m@ service/java/com/android/server/healthconnect/logging/UsageStatsCollector.java:49 @[1m[1m[38;5;146m import java.util.Map;[0m
 * @hide[m
 */[m
public final class UsageStatsCollector {[m
[1m[38;5;2m[32m[m[32m    /**[m[0m
[1m[38;5;2m[32m[m[32m     * A client is considered as "monthly active" by PHR if it has made any read medical resources[m[0m
[1m[38;5;2m[32m[m[32m     * API call within this number of days.[m[0m
[1m[38;5;2m[32m[m[32m     */[m[0m
[1m[38;5;2m[32m[m[32m    private static final long PHR_MONTHLY_ACTIVE_USER_DURATION = 30; // 30 days[m[0m
[7m[32m [m
    private static final String USER_MOST_RECENT_ACCESS_LOG_TIME =[m
            "USER_MOST_RECENT_ACCESS_LOG_TIME";[m
    private static final String EXPORT_PERIOD_PREFERENCE_KEY = "export_period_key";[m
[36m[1m[38;5;13m@ service/java/com/android/server/healthconnect/logging/UsageStatsCollector.java:64 @[1m[1m[38;5;146m public final class UsageStatsCollector {[0m
[m
    private final PreferenceHelper mPreferenceHelper;[m
    private final AccessLogsHelper mAccessLogsHelper;[m
[1m[38;5;2m[32m[m[32m    private final TimeSource mTimeSource;[m[0m
[m
    public UsageStatsCollector([m
            Context context,[m
            UserHandle userHandle,[m
            PreferenceHelper preferenceHelper,[m
[1m[38;5;1m[31m            AccessLogsHelper accessLogsHelper) {[m[0m
[1m[38;5;2m[32m[m[32m            AccessLogsHelper accessLogsHelper,[m[0m
[1m[38;5;2m[32m[m[32m            TimeSource timeSource) {[m[0m
        mContext = context;[m
        mPreferenceHelper = preferenceHelper;[m
        mAccessLogsHelper = accessLogsHelper;[m
[1m[38;5;2m[32m[m[32m        mTimeSource = timeSource;[m[0m
        List<PackageInfo> allPackagesInstalledForUser =[m
                context.createContextAsUser(userHandle, /* flag= */ 0)[m
                        .getPackageManager()[m
[36m[1m[38;5;13m@ service/java/com/android/server/healthconnect/logging/UsageStatsCollector.java:121 @[1m[1m[38;5;146m public final class UsageStatsCollector {[0m
        return packageNameToPermissionsGranted;[m
    }[m
[m
[1m[38;5;2m[32m[m[32m    /**[m[0m
[1m[38;5;2m[32m[m[32m     * Returns whether the current user is considered as a PHR monthly active user.[m[0m
[1m[38;5;2m[32m[m[32m     */[m[0m
[1m[38;5;2m[32m[m[32m    public boolean isPhrMonthlyActiveUser() {[m[0m
[1m[38;5;2m[32m[m[32m        Instant lastReadMedicalResourcesApiTimeStamp =[m[0m
[1m[38;5;2m[32m[m[32m                mPreferenceHelper.getPhrLastReadMedicalResourcesApiTimeStamp();[m[0m
[1m[38;5;2m[32m[m[32m        if (lastReadMedicalResourcesApiTimeStamp == null) {[m[0m
[1m[38;5;2m[32m[m[32m            return false;[m[0m
[1m[38;5;2m[32m[m[32m        }[m[0m
[1m[38;5;2m[32m[m[32m        return mTimeSource[m[0m
[1m[38;5;2m[32m[m[32m                .getInstantNow()[m[0m
[1m[38;5;2m[32m[m[32m                .minus(PHR_MONTHLY_ACTIVE_USER_DURATION, ChronoUnit.DAYS)[m[0m
[1m[38;5;2m[32m[m[32m                .isBefore(lastReadMedicalResourcesApiTimeStamp);[m[0m
[1m[38;5;2m[32m[m[32m    }[m[0m
[7m[32m [m
[1m[38;5;2m[32m[m[32m    /** Returns the number of clients that are granted at least one PHR <b>read</b> permission. */[m[0m
[1m[38;5;2m[32m[m[32m    public long getGrantedPhrAppsCount() {[m[0m
[1m[38;5;2m[32m[m[32m        Map<String, List<String>> packageNameToPermissionsGranted = getPackagesHoldingHealthPermissions();[m[0m
[1m[38;5;2m[32m[m[32m        // isPersonalHealthRecordEnabled() should be enabled when PHR telemetry flag is enabled,[m[0m
[1m[38;5;2m[32m[m[32m        // however, without this check, getAllMedicalPermissions() might throw an exception. 0[m[0m
[1m[38;5;2m[32m[m[32m        // should be returned instead of an exception.[m[0m
[1m[38;5;2m[32m[m[32m        if (!isPersonalHealthRecordEnabled()) {[m[0m
[1m[38;5;2m[32m[m[32m            return 0;[m[0m
[1m[38;5;2m[32m[m[32m        }[m[0m
[7m[32m [m
[1m[38;5;2m[32m[m[32m        // note that this set includes WRITE_MEDICAL_DATA[m[0m
[1m[38;5;2m[32m[m[32m        Set<String> medicalPermissions = HealthPermissions.getAllMedicalPermissions();[m[0m
[7m[32m [m
[1m[38;5;2m[32m[m[32m        return packageNameToPermissionsGranted.values().stream()[m[0m
[1m[38;5;2m[32m[m[32m                .map([m[0m
[1m[38;5;2m[32m[m[32m                        grantedPermissions -> {[m[0m
[1m[38;5;2m[32m[m[32m                            for (String grantedPerm : grantedPermissions) {[m[0m
[1m[38;5;2m[32m[m[32m                                // excluding WRITE_MEDICAL_DATA because we are only counting read[m[0m
[1m[38;5;2m[32m[m[32m                                // perms[m[0m
[1m[38;5;2m[32m[m[32m                                if (WRITE_MEDICAL_DATA.equals(grantedPerm)) {[m[0m
[1m[38;5;2m[32m[m[32m                                    continue;[m[0m
[1m[38;5;2m[32m[m[32m                                }[m[0m
[1m[38;5;2m[32m[m[32m                                if (medicalPermissions.contains(grantedPerm)) {[m[0m
[1m[38;5;2m[32m[m[32m                                    // we just need to find one granted medical perm that is not[m[0m
[1m[38;5;2m[32m[m[32m                                    // WRITE, hence returning early.[m[0m
[1m[38;5;2m[32m[m[32m                                    return 1;[m[0m
[1m[38;5;2m[32m[m[32m                                }[m[0m
[1m[38;5;2m[32m[m[32m                            }[m[0m
[1m[38;5;2m[32m[m[32m                            return 0;[m[0m
[1m[38;5;2m[32m[m[32m                        })[m[0m
[1m[38;5;2m[32m[m[32m                .filter(count -> count > 0)[m[0m
[1m[38;5;2m[32m[m[32m                .count();[m[0m
[1m[38;5;2m[32m[m[32m    }[m[0m
[7m[32m [m
    /**[m
     * Returns the configured export frequency of the user.[m
     *[m
[1m[0m[38;5;11m───────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────[0m
[38;5;11mmodified: service/java/com/android/server/healthconnect/logging/UsageStatsLogger.java
[38;5;11m───────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────[0m
[36m[1m[38;5;13m@ service/java/com/android/server/healthconnect/logging/UsageStatsLogger.java:20 @[1m[0m
package com.android.server.healthconnect.logging;[m
[m
import static android.health.HealthFitnessStatsLog.HEALTH_CONNECT_PERMISSION_STATS;[m
[1m[38;5;1m[31mimport static android.health.connect.HealthPermissions.WRITE_MEDICAL_DATA;[m[0m
[m
[1m[38;5;1m[31mimport static com.android.healthfitness.flags.AconfigFlagHelper.isPersonalHealthRecordEnabled;[m[0m
import static com.android.healthfitness.flags.Flags.personalHealthRecordTelemetry;[m
[m
import android.content.Context;[m
import android.health.HealthFitnessStatsLog;[m
[1m[38;5;1m[31mimport android.health.connect.HealthPermissions;[m[0m
[m
import com.android.healthfitness.flags.Flags;[m
import com.android.server.healthconnect.storage.datatypehelpers.MedicalDataSourceHelper;[m
[36m[1m[38;5;13m@ service/java/com/android/server/healthconnect/logging/UsageStatsLogger.java:32 @[1m[1m[38;5;146m import com.android.server.healthconnect.storage.datatypehelpers.MedicalResourceH[0m
import com.android.server.healthconnect.storage.datatypehelpers.PreferenceHelper;[m
import com.android.server.healthconnect.utils.TimeSource;[m
[m
[1m[38;5;1m[31mimport java.time.Instant;[m[0m
[1m[38;5;1m[31mimport java.time.temporal.ChronoUnit;[m[0m
import java.util.List;[m
import java.util.Map;[m
[1m[38;5;1m[31mimport java.util.Set;[m[0m
[m
/**[m
 * Logs Health Connect usage stats.[m
[36m[1m[38;5;13m@ service/java/com/android/server/healthconnect/logging/UsageStatsLogger.java:41 @[1m[1m[38;5;146m import java.util.Set;[0m
 * @hide[m
 */[m
final class UsageStatsLogger {[m
[1m[38;5;1m[31m    /**[m[0m
[1m[38;5;1m[31m     * A client is considered as "monthly active" by PHR if it has made any read medical resources[m[0m
[1m[38;5;1m[31m     * API call within this number of days.[m[0m
[1m[38;5;1m[31m     */[m[0m
[1m[38;5;1m[31m    private static final long PHR_MONTHLY_ACTIVE_USER_DURATION = 30; // 30 days[m[0m
[m
    /** Write Health Connect usage stats to statsd. */[m
    static void log([m
[36m[1m[38;5;13m@ service/java/com/android/server/healthconnect/logging/UsageStatsLogger.java:48 @[1m[1m[38;5;146m final class UsageStatsLogger {[0m
            UsageStatsCollector usageStatsCollector,[m
            PreferenceHelper preferenceHelper,[m
            MedicalDataSourceHelper medicalDataSourceHelper,[m
[1m[38;5;1m[31m            MedicalResourceHelper medicalResourceHelper,[m[0m
[1m[38;5;1m[31m            TimeSource timeSource) {[m[0m
[1m[38;5;2m[32m[m[32m            MedicalResourceHelper medicalResourceHelper) {[m[0m
        usageStatsCollector.upsertLastAccessLogTimeStamp();[m
        Map<String, List<String>> packageNameToPermissionsGranted =[m
                usageStatsCollector.getPackagesHoldingHealthPermissions();[m
[36m[1m[38;5;13m@ service/java/com/android/server/healthconnect/logging/UsageStatsLogger.java:70 @[1m[1m[38;5;146m final class UsageStatsLogger {[0m
                medicalDataSourceHelper,[m
                medicalResourceHelper,[m
                preferenceHelper,[m
[1m[38;5;1m[31m                timeSource,[m[0m
[1m[38;5;1m[31m                packageNameToPermissionsGranted);[m[0m
[1m[38;5;2m[32m[m[32m                usageStatsCollector);[m[0m
[m
        HealthFitnessStatsLog.write([m
                HealthFitnessStatsLog.HEALTH_CONNECT_USAGE_STATS,[m
[36m[1m[38;5;13m@ service/java/com/android/server/healthconnect/logging/UsageStatsLogger.java:83 @[1m[1m[38;5;146m final class UsageStatsLogger {[0m
            MedicalDataSourceHelper medicalDataSourceHelper,[m
            MedicalResourceHelper medicalResourceHelper,[m
            PreferenceHelper preferenceHelper,[m
[1m[38;5;1m[31m            TimeSource timeSource,[m[0m
[1m[38;5;1m[31m            Map<String, List<String>> packageNameToPermissionsGranted) {[m[0m
[1m[38;5;2m[32m[m[32m            UsageStatsCollector usageStatsCollector) {[m[0m
        if (!personalHealthRecordTelemetry()) {[m
            return;[m
        }[m
[36m[1m[38;5;13m@ service/java/com/android/server/healthconnect/logging/UsageStatsLogger.java:94 @[1m[1m[38;5;146m final class UsageStatsLogger {[0m
                HealthFitnessStatsLog.HEALTH_CONNECT_PHR_USAGE_STATS,[m
                medicalDataSourcesCount,[m
                medicalResourcesCount,[m
[1m[38;5;1m[31m                isPhrMonthlyActiveUser(preferenceHelper, timeSource),[m[0m
[1m[38;5;1m[31m                (int) getGrantedPhrAppsCount(packageNameToPermissionsGranted));[m[0m
[1m[38;5;1m[31m    }[m[0m
[7m[31m [m
[1m[38;5;1m[31m    private static boolean isPhrMonthlyActiveUser([m[0m
[1m[38;5;1m[31m            PreferenceHelper preferenceHelper, TimeSource timeSource) {[m[0m
[1m[38;5;1m[31m        Instant lastReadMedicalResourcesApiTimeStamp =[m[0m
[1m[38;5;1m[31m                preferenceHelper.getPhrLastReadMedicalResourcesApiTimeStamp();[m[0m
[1m[38;5;1m[31m        if (lastReadMedicalResourcesApiTimeStamp == null) {[m[0m
[1m[38;5;1m[31m            return false;[m[0m
[1m[38;5;1m[31m        }[m[0m
[1m[38;5;1m[31m        return timeSource[m[0m
[1m[38;5;1m[31m                .getInstantNow()[m[0m
[1m[38;5;1m[31m                .minus(PHR_MONTHLY_ACTIVE_USER_DURATION, ChronoUnit.DAYS)[m[0m
[1m[38;5;1m[31m                .isBefore(lastReadMedicalResourcesApiTimeStamp);[m[0m
[1m[38;5;1m[31m    }[m[0m
[7m[31m [m
[1m[38;5;1m[31m    /** Returns the number of clients that are granted at least one PHR <b>read</b> permission. */[m[0m
[1m[38;5;1m[31m    private static long getGrantedPhrAppsCount([m[0m
[1m[38;5;1m[31m            Map<String, List<String>> packageNameToPermissionsGranted) {[m[0m
[1m[38;5;1m[31m        // isPersonalHealthRecordEnabled() should be enabled when PHR telemetry flag is enabled,[m[0m
[1m[38;5;1m[31m        // however, without this check, getAllMedicalPermissions() might throw an exception. 0[m[0m
[1m[38;5;1m[31m        // should be returned instead of an exception.[m[0m
[1m[38;5;1m[31m        if (!isPersonalHealthRecordEnabled()) {[m[0m
[1m[38;5;1m[31m            return 0;[m[0m
[1m[38;5;1m[31m        }[m[0m
[7m[31m [m
[1m[38;5;1m[31m        // note that this set includes WRITE_MEDICAL_DATA[m[0m
[1m[38;5;1m[31m        Set<String> medicalPermissions = HealthPermissions.getAllMedicalPermissions();[m[0m
[7m[31m [m
[1m[38;5;1m[31m        return packageNameToPermissionsGranted.values().stream()[m[0m
[1m[38;5;1m[31m                .map([m[0m
[1m[38;5;1m[31m                        grantedPermissions -> {[m[0m
[1m[38;5;1m[31m                            for (String grantedPerm : grantedPermissions) {[m[0m
[1m[38;5;1m[31m                                // excluding WRITE_MEDICAL_DATA because we are only counting read[m[0m
[1m[38;5;1m[31m                                // perms[m[0m
[1m[38;5;1m[31m                                if (WRITE_MEDICAL_DATA.equals(grantedPerm)) {[m[0m
[1m[38;5;1m[31m                                    continue;[m[0m
[1m[38;5;1m[31m                                }[m[0m
[1m[38;5;1m[31m                                if (medicalPermissions.contains(grantedPerm)) {[m[0m
[1m[38;5;1m[31m                                    // we just need to find one granted medical perm that is not[m[0m
[1m[38;5;1m[31m                                    // WRITE, hence returning early.[m[0m
[1m[38;5;1m[31m                                    return 1;[m[0m
[1m[38;5;1m[31m                                }[m[0m
[1m[38;5;1m[31m                            }[m[0m
[1m[38;5;1m[31m                            return 0;[m[0m
[1m[38;5;1m[31m                        })[m[0m
[1m[38;5;1m[31m                .filter(count -> count > 0)[m[0m
[1m[38;5;1m[31m                .count();[m[0m
[1m[38;5;2m[32m[m[32m                usageStatsCollector.isPhrMonthlyActiveUser(),[m[0m
[1m[38;5;2m[32m[m[32m                (int) usageStatsCollector.getGrantedPhrAppsCount());[m[0m
    }[m
[m
    static void logExportImportStats(UsageStatsCollector usageStatsCollector) {[m
[1m[0m[38;5;11m───────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────[0m
[38;5;11mmodified: tests/unittests/src/com/android/server/healthconnect/logging/DailyLoggingServiceTest.java
[38;5;11m───────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────[0m
[36m[1m[38;5;13m@ tests/unittests/src/com/android/server/healthconnect/logging/DailyLoggingServiceTest.java:42 @[1m[1m[38;5;146m import static org.mockito.ArgumentMatchers.any;[0m
import static org.mockito.ArgumentMatchers.anyBoolean;[m
import static org.mockito.ArgumentMatchers.anyInt;[m
import static org.mockito.ArgumentMatchers.anyLong;[m
[1m[38;5;2m[32m[m[32mimport static org.mockito.Mockito.clearInvocations;[m[0m
import static org.mockito.Mockito.eq;[m
[1m[38;5;2m[32m[m[32mimport static org.mockito.Mockito.mock;[m[0m
import static org.mockito.Mockito.never;[m
[1m[38;5;1m[31mimport static org.mockito.Mockito.spy;[m[0m
import static org.mockito.Mockito.times;[m
import static org.mockito.Mockito.when;[m
[m
import android.content.Context;[m
import android.content.pm.PackageInfo;[m
[1m[38;5;2m[32m[m[32mimport android.content.pm.PackageManager;[m[0m
import android.database.DatabaseUtils;[m
import android.health.HealthFitnessStatsLog;[m
import android.health.connect.HealthConnectManager;[m
[36m[1m[38;5;13m@ tests/unittests/src/com/android/server/healthconnect/logging/DailyLoggingServiceTest.java:92 @[1m[1m[38;5;146m import java.time.temporal.ChronoUnit;[0m
import java.util.List;[m
import java.util.Map;[m
import java.util.Set;[m
[1m[38;5;2m[32m[m[32mimport java.util.stream.Collectors;[m[0m
[m
public class DailyLoggingServiceTest {[m
[m
[36m[1m[38;5;13m@ tests/unittests/src/com/android/server/healthconnect/logging/DailyLoggingServiceTest.java:210 @[1m[1m[38;5;146m public class DailyLoggingServiceTest {[0m
        }[m
        UsageStatsCollector usageStatsCollector =[m
                new UsageStatsCollector([m
[1m[38;5;1m[31m                        mContext, mCurrentUser, mPreferenceHelper, mAccessLogsHelper[7m[27m);[m[0m
[1m[38;5;2m[32m[m[32m                        mContext, mCurrentUser, mPreferenceHelper, mAccessLogsHelper[7m, mFakeTimeSource[27m);[m[0m
[m
        DailyLoggingService.logDailyMetrics([m
                mContext,[m
[36m[1m[38;5;13m@ tests/unittests/src/com/android/server/healthconnect/logging/DailyLoggingServiceTest.java:218 @[1m[1m[38;5;146m public class DailyLoggingServiceTest {[0m
                mPreferenceHelper,[m
                mTransactionManager,[m
                mMedicalDataSourceHelper,[m
[1m[38;5;1m[31m                mMedicalResourceHelper,[m[0m
[1m[38;5;1m[31m                mFakeTimeSource);[m[0m
[1m[38;5;2m[32m[m[32m                mMedicalResourceHelper);[m[0m
[m
        ExtendedMockito.verify([m
                () ->[m
[36m[1m[38;5;13m@ tests/unittests/src/com/android/server/healthconnect/logging/DailyLoggingServiceTest.java:240 @[1m[1m[38;5;146m public class DailyLoggingServiceTest {[0m
        when(mTransactionManager.getNumberOfEntriesInTheTable(any())).thenReturn(0L);[m
        UsageStatsCollector usageStatsCollector =[m
                new UsageStatsCollector([m
[1m[38;5;1m[31m                        mContext, mCurrentUser, mPreferenceHelper, mAccessLogsHelper[7m[27m);[m[0m
[1m[38;5;2m[32m[m[32m                        mContext, mCurrentUser, mPreferenceHelper, mAccessLogsHelper[7m, mFakeTimeSource[27m);[m[0m
[m
        DailyLoggingService.logDailyMetrics([m
                mContext,[m
[36m[1m[38;5;13m@ tests/unittests/src/com/android/server/healthconnect/logging/DailyLoggingServiceTest.java:248 @[1m[1m[38;5;146m public class DailyLoggingServiceTest {[0m
                mPreferenceHelper,[m
                mTransactionManager,[m
                mMedicalDataSourceHelper,[m
[1m[38;5;1m[31m                mMedicalResourceHelper,[m[0m
[1m[38;5;1m[31m                mFakeTimeSource);[m[0m
[1m[38;5;2m[32m[m[32m                mMedicalResourceHelper);[m[0m
[m
        ExtendedMockito.verify([m
                () ->[m
[36m[1m[38;5;13m@ tests/unittests/src/com/android/server/healthconnect/logging/DailyLoggingServiceTest.java:275 @[1m[1m[38;5;146m public class DailyLoggingServiceTest {[0m
                .thenReturn(String.valueOf(subtractDaysFromInstantNow(/* numberOfDays= */ 0)));[m
        UsageStatsCollector usageStatsCollector =[m
                new UsageStatsCollector([m
[1m[38;5;1m[31m                        mContext, mCurrentUser, mPreferenceHelper, mAccessLogsHelper[7m[27m);[m[0m
[1m[38;5;2m[32m[m[32m                        mContext, mCurrentUser, mPreferenceHelper, mAccessLogsHelper[7m, mFakeTimeSource[27m);[m[0m
[m
        DailyLoggingService.logDailyMetrics([m
                mContext,[m
[36m[1m[38;5;13m@ tests/unittests/src/com/android/server/healthconnect/logging/DailyLoggingServiceTest.java:283 @[1m[1m[38;5;146m public class DailyLoggingServiceTest {[0m
                mPreferenceHelper,[m
                mTransactionManager,[m
                mMedicalDataSourceHelper,[m
[1m[38;5;1m[31m                mMedicalResourceHelper,[m[0m
[1m[38;5;1m[31m                mFakeTimeSource);[m[0m
[1m[38;5;2m[32m[m[32m                mMedicalResourceHelper);[m[0m
[m
        // Makes sure we do not have count any app that does not have Health Connect permission[m
        // declared in the manifest as a connected or an available app.[m
[36m[1m[38;5;13m@ tests/unittests/src/com/android/server/healthconnect/logging/DailyLoggingServiceTest.java:307 @[1m[1m[38;5;146m public class DailyLoggingServiceTest {[0m
                .thenReturn(String.valueOf(subtractDaysFromInstantNow(/* numberOfDays= */ 0)));[m
        UsageStatsCollector usageStatsCollector =[m
                new UsageStatsCollector([m
[1m[38;5;1m[31m                        mContext, mCurrentUser, mPreferenceHelper, mAccessLogsHelper[7m[27m);[m[0m
[1m[38;5;2m[32m[m[32m                        mContext, mCurrentUser, mPreferenceHelper, mAccessLogsHelper[7m, mFakeTimeSource[27m);[m[0m
[m
        DailyLoggingService.logDailyMetrics([m
                mContext,[m
[36m[1m[38;5;13m@ tests/unittests/src/com/android/server/healthconnect/logging/DailyLoggingServiceTest.java:315 @[1m[1m[38;5;146m public class DailyLoggingServiceTest {[0m
                mPreferenceHelper,[m
                mTransactionManager,[m
                mMedicalDataSourceHelper,[m
[1m[38;5;1m[31m                mMedicalResourceHelper,[m[0m
[1m[38;5;1m[31m                mFakeTimeSource);[m[0m
[1m[38;5;2m[32m[m[32m                mMedicalResourceHelper);[m[0m
[m
        ExtendedMockito.verify([m
                () ->[m
[36m[1m[38;5;13m@ tests/unittests/src/com/android/server/healthconnect/logging/DailyLoggingServiceTest.java:337 @[1m[1m[38;5;146m public class DailyLoggingServiceTest {[0m
                .thenReturn(String.valueOf(subtractDaysFromInstantNow(/* numberOfDays= */ 31)));[m
        UsageStatsCollector usageStatsCollector =[m
                new UsageStatsCollector([m
[1m[38;5;1m[31m                        mContext, mCurrentUser, mPreferenceHelper, mAccessLogsHelper[7m[27m);[m[0m
[1m[38;5;2m[32m[m[32m                        mContext, mCurrentUser, mPreferenceHelper, mAccessLogsHelper[7m, mFakeTimeSource[27m);[m[0m
[m
        DailyLoggingService.logDailyMetrics([m
                mContext,[m
[36m[1m[38;5;13m@ tests/unittests/src/com/android/server/healthconnect/logging/DailyLoggingServiceTest.java:345 @[1m[1m[38;5;146m public class DailyLoggingServiceTest {[0m
                mPreferenceHelper,[m
                mTransactionManager,[m
                mMedicalDataSourceHelper,[m
[1m[38;5;1m[31m                mMedicalResourceHelper,[m[0m
[1m[38;5;1m[31m                mFakeTimeSource);[m[0m
[1m[38;5;2m[32m[m[32m                mMedicalResourceHelper);[m[0m
[m
        ExtendedMockito.verify([m
                () ->[m
[36m[1m[38;5;13m@ tests/unittests/src/com/android/server/healthconnect/logging/DailyLoggingServiceTest.java:367 @[1m[1m[38;5;146m public class DailyLoggingServiceTest {[0m
                .thenReturn(String.valueOf(subtractDaysFromInstantNow(/* numberOfDays= */ 1)));[m
        UsageStatsCollector usageStatsCollector =[m
                new UsageStatsCollector([m
[1m[38;5;1m[31m                        mContext, mCurrentUser, mPreferenceHelper, mAccessLogsHelper[7m[27m);[m[0m
[1m[38;5;2m[32m[m[32m                        mContext, mCurrentUser, mPreferenceHelper, mAccessLogsHelper[7m, mFakeTimeSource[27m);[m[0m
[m
        DailyLoggingService.logDailyMetrics([m
                mContext,[m
[36m[1m[38;5;13m@ tests/unittests/src/com/android/server/healthconnect/logging/DailyLoggingServiceTest.java:375 @[1m[1m[38;5;146m public class DailyLoggingServiceTest {[0m
                mPreferenceHelper,[m
                mTransactionManager,[m
                mMedicalDataSourceHelper,[m
[1m[38;5;1m[31m                mMedicalResourceHelper,[m[0m
[1m[38;5;1m[31m                mFakeTimeSource);[m[0m
[1m[38;5;2m[32m[m[32m                mMedicalResourceHelper);[m[0m
[m
        ExtendedMockito.verify([m
                () ->[m
[36m[1m[38;5;13m@ tests/unittests/src/com/android/server/healthconnect/logging/DailyLoggingServiceTest.java:392 @[1m[1m[38;5;146m public class DailyLoggingServiceTest {[0m
                .thenReturn(String.valueOf(subtractDaysFromInstantNow(/* numberOfDays= */ 1)));[m
        UsageStatsCollector usageStatsCollector =[m
                new UsageStatsCollector([m
[1m[38;5;1m[31m                        mContext, mCurrentUser, mPreferenceHelper, mAccessLogsHelper[7m[27m);[m[0m
[1m[38;5;2m[32m[m[32m                        mContext, mCurrentUser, mPreferenceHelper, mAccessLogsHelper[7m, mFakeTimeSource[27m);[m[0m
[m
        DailyLoggingService.logDailyMetrics([m
                mContext,[m
[36m[1m[38;5;13m@ tests/unittests/src/com/android/server/healthconnect/logging/DailyLoggingServiceTest.java:400 @[1m[1m[38;5;146m public class DailyLoggingServiceTest {[0m
                mPreferenceHelper,[m
                mTransactionManager,[m
                mMedicalDataSourceHelper,[m
[1m[38;5;1m[31m                mMedicalResourceHelper,[m[0m
[1m[38;5;1m[31m                mFakeTimeSource);[m[0m
[1m[38;5;2m[32m[m[32m                mMedicalResourceHelper);[m[0m
[m
        ExtendedMockito.verify([m
                () ->[m
[36m[1m[38;5;13m@ tests/unittests/src/com/android/server/healthconnect/logging/DailyLoggingServiceTest.java:421 @[1m[1m[38;5;146m public class DailyLoggingServiceTest {[0m
                .thenReturn(String.valueOf(subtractDaysFromInstantNow(/* numberOfDays= */ 31)));[m
        UsageStatsCollector usageStatsCollector =[m
                new UsageStatsCollector([m
[1m[38;5;1m[31m                        mContext, mCurrentUser, mPreferenceHelper, mAccessLogsHelper[7m[27m);[m[0m
[1m[38;5;2m[32m[m[32m                        mContext, mCurrentUser, mPreferenceHelper, mAccessLogsHelper[7m, mFakeTimeSource[27m);[m[0m
[m
        DailyLoggingService.logDailyMetrics([m
                mContext,[m
[36m[1m[38;5;13m@ tests/unittests/src/com/android/server/healthconnect/logging/DailyLoggingServiceTest.java:429 @[1m[1m[38;5;146m public class DailyLoggingServiceTest {[0m
                mPreferenceHelper,[m
                mTransactionManager,[m
                mMedicalDataSourceHelper,[m
[1m[38;5;1m[31m                mMedicalResourceHelper,[m[0m
[1m[38;5;1m[31m                mFakeTimeSource);[m[0m
[1m[38;5;2m[32m[m[32m                mMedicalResourceHelper);[m[0m
[m
        ExtendedMockito.verify([m
                () ->[m
[36m[1m[38;5;13m@ tests/unittests/src/com/android/server/healthconnect/logging/DailyLoggingServiceTest.java:448 @[1m[1m[38;5;146m public class DailyLoggingServiceTest {[0m
                .thenReturn(String.valueOf(7));[m
        UsageStatsCollector usageStatsCollector =[m
                new UsageStatsCollector([m
[1m[38;5;1m[31m                        mContext, mCurrentUser, mPreferenceHelper, mAccessLogsHelper[7m[27m);[m[0m
[1m[38;5;2m[32m[m[32m                        mContext, mCurrentUser, mPreferenceHelper, mAccessLogsHelper[7m, mFakeTimeSource[27m);[m[0m
[m
        DailyLoggingService.logDailyMetrics([m
                mContext,[m
[36m[1m[38;5;13m@ tests/unittests/src/com/android/server/healthconnect/logging/DailyLoggingServiceTest.java:456 @[1m[1m[38;5;146m public class DailyLoggingServiceTest {[0m
                mPreferenceHelper,[m
                mTransactionManager,[m
                mMedicalDataSourceHelper,[m
[1m[38;5;1m[31m                mMedicalResourceHelper,[m[0m
[1m[38;5;1m[31m                mFakeTimeSource);[m[0m
[1m[38;5;2m[32m[m[32m                mMedicalResourceHelper);[m[0m
[m
        ExtendedMockito.verify([m
                () ->[m
[36m[1m[38;5;13m@ tests/unittests/src/com/android/server/healthconnect/logging/DailyLoggingServiceTest.java:474 @[1m[1m[38;5;146m public class DailyLoggingServiceTest {[0m
        when(mPreferenceHelper.getPreference(EXPORT_PERIOD_PREFERENCE_KEY)).thenReturn(null);[m
        UsageStatsCollector usageStatsCollector =[m
                new UsageStatsCollector([m
[1m[38;5;1m[31m                        mContext, mCurrentUser, mPreferenceHelper, mAccessLogsHelper[7m[27m);[m[0m
[1m[38;5;2m[32m[m[32m                        mContext, mCurrentUser, mPreferenceHelper, mAccessLogsHelper[7m, mFakeTimeSource[27m);[m[0m
[m
        DailyLoggingService.logDailyMetrics([m
                mContext,[m
[36m[1m[38;5;13m@ tests/unittests/src/com/android/server/healthconnect/logging/DailyLoggingServiceTest.java:482 @[1m[1m[38;5;146m public class DailyLoggingServiceTest {[0m
                mPreferenceHelper,[m
                mTransactionManager,[m
                mMedicalDataSourceHelper,[m
[1m[38;5;1m[31m                mMedicalResourceHelper,[m[0m
[1m[38;5;1m[31m                mFakeTimeSource);[m[0m
[1m[38;5;2m[32m[m[32m                mMedicalResourceHelper);[m[0m
[m
        ExtendedMockito.verify([m
                () ->[m
[36m[1m[38;5;13m@ tests/unittests/src/com/android/server/healthconnect/logging/DailyLoggingServiceTest.java:499 @[1m[1m[38;5;146m public class DailyLoggingServiceTest {[0m
                .thenReturn(String.valueOf(subtractDaysFromInstantNow(/* numberOfDays= */ 31)));[m
        UsageStatsCollector usageStatsCollector =[m
                new UsageStatsCollector([m
[1m[38;5;1m[31m                        mContext, mCurrentUser, mPreferenceHelper, mAccessLogsHelper[7m[27m);[m[0m
[1m[38;5;2m[32m[m[32m                        mContext, mCurrentUser, mPreferenceHelper, mAccessLogsHelper[7m, mFakeTimeSource[27m);[m[0m
[m
        DailyLoggingService.logDailyMetrics([m
                mContext,[m
[36m[1m[38;5;13m@ tests/unittests/src/com/android/server/healthconnect/logging/DailyLoggingServiceTest.java:507 @[1m[1m[38;5;146m public class DailyLoggingServiceTest {[0m
                mPreferenceHelper,[m
                mTransactionManager,[m
                mMedicalDataSourceHelper,[m
[1m[38;5;1m[31m                mMedicalResourceHelper,[m[0m
[1m[38;5;1m[31m                mFakeTimeSource);[m[0m
[1m[38;5;2m[32m[m[32m                mMedicalResourceHelper);[m[0m
[m
        ExtendedMockito.verify([m
                () ->[m
[36m[1m[38;5;13m@ tests/unittests/src/com/android/server/healthconnect/logging/DailyLoggingServiceTest.java:535 @[1m[1m[38;5;146m public class DailyLoggingServiceTest {[0m
[m
        UsageStatsCollector usageStatsCollector =[m
                new UsageStatsCollector([m
[1m[38;5;1m[31m                        mContext, mCurrentUser, mPreferenceHelper, mAccessLogsHelper[7m[27m);[m[0m
[1m[38;5;2m[32m[m[32m                        mContext, mCurrentUser, mPreferenceHelper, mAccessLogsHelper[7m, mFakeTimeSource[27m);[m[0m
[m
        DailyLoggingService.logDailyMetrics([m
                mContext,[m
[36m[1m[38;5;13m@ tests/unittests/src/com/android/server/healthconnect/logging/DailyLoggingServiceTest.java:543 @[1m[1m[38;5;146m public class DailyLoggingServiceTest {[0m
                mPreferenceHelper,[m
                mTransactionManager,[m
                mMedicalDataSourceHelper,[m
[1m[38;5;1m[31m                mMedicalResourceHelper,[m[0m
[1m[38;5;1m[31m                mFakeTimeSource);[m[0m
[1m[38;5;2m[32m[m[32m                mMedicalResourceHelper);[m[0m
[m
        ExtendedMockito.verify([m
                () ->[m
[36m[1m[38;5;13m@ tests/unittests/src/com/android/server/healthconnect/logging/DailyLoggingServiceTest.java:573 @[1m[1m[38;5;146m public class DailyLoggingServiceTest {[0m
                .thenReturn(String.valueOf(subtractDaysFromInstantNow(/* numberOfDays= */ 0)));[m
        UsageStatsCollector usageStatsCollector =[m
                new UsageStatsCollector([m
[1m[38;5;1m[31m                        mContext, mCurrentUser, mPreferenceHelper, mAccessLogsHelper[7m[27m);[m[0m
[1m[38;5;2m[32m[m[32m                        mContext, mCurrentUser, mPreferenceHelper, mAccessLogsHelper[7m, mFakeTimeSource[27m);[m[0m
[m
        DailyLoggingService.logDailyMetrics([m
                mContext,[m
[36m[1m[38;5;13m@ tests/unittests/src/com/android/server/healthconnect/logging/DailyLoggingServiceTest.java:581 @[1m[1m[38;5;146m public class DailyLoggingServiceTest {[0m
                mPreferenceHelper,[m
                mTransactionManager,[m
                mMedicalDataSourceHelper,[m
[1m[38;5;1m[31m                mMedicalResourceHelper,[m[0m
[1m[38;5;1m[31m                mFakeTimeSource);[m[0m
[1m[38;5;2m[32m[m[32m                mMedicalResourceHelper);[m[0m
[m
        ExtendedMockito.verify([m
                () ->[m
[36m[1m[38;5;13m@ tests/unittests/src/com/android/server/healthconnect/logging/DailyLoggingServiceTest.java:622 @[1m[1m[38;5;146m public class DailyLoggingServiceTest {[0m
                .thenReturn(String.valueOf(subtractDaysFromInstantNow(/* numberOfDays= */ 0)));[m
        UsageStatsCollector usageStatsCollector =[m
                new UsageStatsCollector([m
[1m[38;5;1m[31m                        mContext, mCurrentUser, mPreferenceHelper, mAccessLogsHelper[7m[27m);[m[0m
[1m[38;5;2m[32m[m[32m                        mContext, mCurrentUser, mPreferenceHelper, mAccessLogsHelper[7m, mFakeTimeSource[27m);[m[0m
[m
        DailyLoggingService.logDailyMetrics([m
                mContext,[m
[36m[1m[38;5;13m@ tests/unittests/src/com/android/server/healthconnect/logging/DailyLoggingServiceTest.java:630 @[1m[1m[38;5;146m public class DailyLoggingServiceTest {[0m
                mPreferenceHelper,[m
                mTransactionManager,[m
                mMedicalDataSourceHelper,[m
[1m[38;5;1m[31m                mMedicalResourceHelper,[m[0m
[1m[38;5;1m[31m                mFakeTimeSource);[m[0m
[1m[38;5;2m[32m[m[32m                mMedicalResourceHelper);[m[0m
[m
        ExtendedMockito.verify([m
                () ->[m
[36m[1m[38;5;13m@ tests/unittests/src/com/android/server/healthconnect/logging/DailyLoggingServiceTest.java:646 @[1m[1m[38;5;146m public class DailyLoggingServiceTest {[0m
    public void phrStats_flagDisabled_expectNoLogs() {[m
        UsageStatsCollector usageStatsCollector =[m
                new UsageStatsCollector([m
[1m[38;5;1m[31m                        mContext, mCurrentUser, mPreferenceHelper, mAccessLogsHelper[7m[27m);[m[0m
[1m[38;5;2m[32m[m[32m                        mContext, mCurrentUser, mPreferenceHelper, mAccessLogsHelper[7m, mFakeTimeSource[27m);[m[0m
[m
        DailyLoggingService.logDailyMetrics([m
                mContext,[m
[36m[1m[38;5;13m@ tests/unittests/src/com/android/server/healthconnect/logging/DailyLoggingServiceTest.java:654 @[1m[1m[38;5;146m public class DailyLoggingServiceTest {[0m
                mPreferenceHelper,[m
                mTransactionManager,[m
                mMedicalDataSourceHelper,[m
[1m[38;5;1m[31m                mMedicalResourceHelper,[m[0m
[1m[38;5;1m[31m                mFakeTimeSource);[m[0m
[1m[38;5;2m[32m[m[32m                mMedicalResourceHelper);[m[0m
[m
        ExtendedMockito.verify([m
                () ->[m
[36m[1m[38;5;13m@ tests/unittests/src/com/android/server/healthconnect/logging/DailyLoggingServiceTest.java:679 @[1m[1m[38;5;146m public class DailyLoggingServiceTest {[0m
        when(mPreferenceHelper.getPhrLastReadMedicalResourcesApiTimeStamp())[m
                .thenReturn(NOW.minus(29, ChronoUnit.DAYS));[m
        mFakeTimeSource.setInstant(NOW);[m
[1m[38;5;1m[31m        UsageStatsCollector usageStatsCollector =[m[0m
[1m[38;5;1m[31m                spy([m[0m
[1m[38;5;1m[31m                        new UsageStatsCollector([m[0m
[1m[38;5;1m[31m                                mContext, mCurrentUser, mPreferenceHelper, mAccessLogsHelper));[m[0m
[1m[38;5;1m[31m        when(usageStatsCollector.getPackagesHoldingHealthPermissions())[m[0m
[1m[38;5;1m[31m                .thenReturn([m[0m
[1m[38;5;2m[32m[m[32m        mockGrantedPermissions([m[0m
                        Map.of([m
                                CONNECTED_APP_PACKAGE_NAME,[m
                                List.of(WRITE_STEPS, READ_STEPS),[m
[36m[1m[38;5;13m@ tests/unittests/src/com/android/server/healthconnect/logging/DailyLoggingServiceTest.java:693 @[1m[1m[38;5;146m public class DailyLoggingServiceTest {[0m
                                        READ_STEPS,[m
                                        WRITE_MEDICAL_DATA,[m
                                        READ_MEDICAL_DATA_CONDITIONS)));[m
[1m[38;5;2m[32m[m[32m        UsageStatsCollector usageStatsCollector =[m[0m
[1m[38;5;2m[32m[m[32m                        new UsageStatsCollector([m[0m
[1m[38;5;2m[32m[m[32m                                mContext, mCurrentUser, mPreferenceHelper, mAccessLogsHelper, mFakeTimeSource);[m[0m
[m
        DailyLoggingService.logDailyMetrics([m
                mContext,[m
[36m[1m[38;5;13m@ tests/unittests/src/com/android/server/healthconnect/logging/DailyLoggingServiceTest.java:703 @[1m[1m[38;5;146m public class DailyLoggingServiceTest {[0m
                mPreferenceHelper,[m
                mTransactionManager,[m
                mMedicalDataSourceHelper,[m
[1m[38;5;1m[31m                mMedicalResourceHelper,[m[0m
[1m[38;5;1m[31m                mFakeTimeSource);[m[0m
[1m[38;5;2m[32m[m[32m                mMedicalResourceHelper);[m[0m
[m
        ExtendedMockito.verify([m
                () ->[m
[36m[1m[38;5;13m@ tests/unittests/src/com/android/server/healthconnect/logging/DailyLoggingServiceTest.java:728 @[1m[1m[38;5;146m public class DailyLoggingServiceTest {[0m
        when(mPreferenceHelper.getPhrLastReadMedicalResourcesApiTimeStamp())[m
                .thenReturn(NOW.minus(31, ChronoUnit.DAYS));[m
        mFakeTimeSource.setInstant(NOW);[m
[1m[38;5;1m[31m        UsageStatsCollector usageStatsCollector =[m[0m
[1m[38;5;1m[31m                spy([m[0m
[1m[38;5;1m[31m                        new UsageStatsCollector([m[0m
[1m[38;5;1m[31m                                mContext, mCurrentUser, mPreferenceHelper, mAccessLogsHelper));[m[0m
[1m[38;5;1m[31m        when(usageStatsCollector.getPackagesHoldingHealthPermissions())[m[0m
[1m[38;5;1m[31m                .thenReturn([m[0m
[1m[38;5;2m[32m[m[32m        mockGrantedPermissions([m[0m
                        Map.of([m
                                CONNECTED_APP_PACKAGE_NAME,[m
                                List.of(WRITE_STEPS, READ_STEPS),[m
[36m[1m[38;5;13m@ tests/unittests/src/com/android/server/healthconnect/logging/DailyLoggingServiceTest.java:738 @[1m[1m[38;5;146m public class DailyLoggingServiceTest {[0m
                                List.of(WRITE_STEPS, READ_EXERCISE),[m
                                CONNECTED_APP_PACKAGE_NAME + "3",[m
                                List.of(WRITE_STEPS, READ_DISTANCE)));[m
[1m[38;5;2m[32m[m[32m        UsageStatsCollector usageStatsCollector =[m[0m
[7m[32m [m
[1m[38;5;2m[32m[m[32m                        new UsageStatsCollector([m[0m
[1m[38;5;2m[32m[m[32m                                mContext, mCurrentUser, mPreferenceHelper, mAccessLogsHelper, mFakeTimeSource);[m[0m
[m
        DailyLoggingService.logDailyMetrics([m
                mContext,[m
[36m[1m[38;5;13m@ tests/unittests/src/com/android/server/healthconnect/logging/DailyLoggingServiceTest.java:749 @[1m[1m[38;5;146m public class DailyLoggingServiceTest {[0m
                mPreferenceHelper,[m
                mTransactionManager,[m
                mMedicalDataSourceHelper,[m
[1m[38;5;1m[31m                mMedicalResourceHelper,[m[0m
[1m[38;5;1m[31m                mFakeTimeSource);[m[0m
[1m[38;5;2m[32m[m[32m                mMedicalResourceHelper);[m[0m
[m
        ExtendedMockito.verify([m
                () ->[m
[36m[1m[38;5;13m@ tests/unittests/src/com/android/server/healthconnect/logging/DailyLoggingServiceTest.java:770 @[1m[1m[38;5;146m public class DailyLoggingServiceTest {[0m
    })[m
    public void phrStats_flagEnabledAndIsNotMonthlyActiveUserDueToNoTimeStamp_expectCorrectLogs() {[m
        when(mPreferenceHelper.getPhrLastReadMedicalResourcesApiTimeStamp()).thenReturn(null);[m
[1m[38;5;1m[31m        UsageStatsCollector usageStatsCollector =[m[0m
[1m[38;5;1m[31m                spy([m[0m
[1m[38;5;1m[31m                        new UsageStatsCollector([m[0m
[1m[38;5;1m[31m                                mContext, mCurrentUser, mPreferenceHelper, mAccessLogsHelper));[m[0m
[1m[38;5;1m[31m        when(usageStatsCollector.getPackagesHoldingHealthPermissions())[m[0m
[1m[38;5;1m[31m                .thenReturn([m[0m
[1m[38;5;2m[32m[m[32m        mockGrantedPermissions([m[0m
                        Map.of([m
                                CONNECTED_APP_PACKAGE_NAME,[m
                                List.of(WRITE_STEPS, READ_STEPS, READ_MEDICAL_DATA_VACCINES),[m
[36m[1m[38;5;13m@ tests/unittests/src/com/android/server/healthconnect/logging/DailyLoggingServiceTest.java:785 @[1m[1m[38;5;146m public class DailyLoggingServiceTest {[0m
                                        WRITE_STEPS,[m
                                        READ_DISTANCE,[m
                                        READ_MEDICAL_DATA_ALLERGIES_INTOLERANCES)));[m
[1m[38;5;2m[32m[m[32m        UsageStatsCollector usageStatsCollector =[m[0m
[1m[38;5;2m[32m[m[32m                        new UsageStatsCollector([m[0m
[1m[38;5;2m[32m[m[32m                                mContext, mCurrentUser, mPreferenceHelper, mAccessLogsHelper, mFakeTimeSource);[m[0m
[m
        DailyLoggingService.logDailyMetrics([m
                mContext,[m
[36m[1m[38;5;13m@ tests/unittests/src/com/android/server/healthconnect/logging/DailyLoggingServiceTest.java:795 @[1m[1m[38;5;146m public class DailyLoggingServiceTest {[0m
                mPreferenceHelper,[m
                mTransactionManager,[m
                mMedicalDataSourceHelper,[m
[1m[38;5;1m[31m                mMedicalResourceHelper,[m[0m
[1m[38;5;1m[31m                mFakeTimeSource);[m[0m
[1m[38;5;2m[32m[m[32m                mMedicalResourceHelper);[m[0m
[m
        ExtendedMockito.verify([m
                () ->[m
[36m[1m[38;5;13m@ tests/unittests/src/com/android/server/healthconnect/logging/DailyLoggingServiceTest.java:808 @[1m[1m[38;5;146m public class DailyLoggingServiceTest {[0m
                times(1));[m
    }[m
[m
[1m[38;5;2m[32m[m[32m    private void mockGrantedPermissions(Map<String, List<String>> packageNameToGrantedPermissionsMap) {[m[0m
[1m[38;5;2m[32m[m[32m        // This is needed so HealthConnectManager recognizes all the permissions in[m[0m
[1m[38;5;2m[32m[m[32m        // packageNameToGrantedPermissionsMap as health permissions.[m[0m
[1m[38;5;2m[32m[m[32m        ExtendedMockito.doReturn(packageNameToGrantedPermissionsMap.values().stream().flatMap(List::stream).collect([m[0m
[1m[38;5;2m[32m[m[32m                        Collectors.toSet()))[m[0m
[1m[38;5;2m[32m[m[32m                .when(() -> HealthConnectManager.getHealthPermissions(mContext));[m[0m
[7m[32m [m
[1m[38;5;2m[32m[m[32m        List<PackageInfo> installedPackages = packageNameToGrantedPermissionsMap.entrySet().stream().map(entry -> {[m[0m
[1m[38;5;2m[32m[m[32m            String packageName = entry.getKey();[m[0m
[1m[38;5;2m[32m[m[32m            List<String> grantedPermissions = entry.getValue();[m[0m
[1m[38;5;2m[32m[m[32m            PackageInfo packageInfo = mock(PackageInfo.class);[m[0m
[1m[38;5;2m[32m[m[32m            packageInfo.requestedPermissions = grantedPermissions.toArray(new String[0]);[m[0m
[1m[38;5;2m[32m[m[32m            packageInfo.requestedPermissionsFlags = grantedPermissions.stream().mapToInt(perm -> PackageInfo.REQUESTED_PERMISSION_GRANTED).toArray();[m[0m
[1m[38;5;2m[32m[m[32m            packageInfo.packageName = packageName;[m[0m
[1m[38;5;2m[32m[m[32m            return packageInfo;[m[0m
[1m[38;5;2m[32m[m[32m        }).toList();[m[0m
[1m[38;5;2m[32m[m[32m        PackageManager packageManager = mContext.createContextAsUser(mCurrentUser, 0)[m[0m
[1m[38;5;2m[32m[m[32m                .getPackageManager();[m[0m
[1m[38;5;2m[32m[m[32m        clearInvocations(packageManager);[m[0m
[1m[38;5;2m[32m[m[32m        when(packageManager[m[0m
[1m[38;5;2m[32m[m[32m                .getInstalledPackages(any()))[m[0m
[1m[38;5;2m[32m[m[32m                .thenReturn(installedPackages);[m[0m
[1m[38;5;2m[32m[m[32m    }[m[0m
[7m[32m [m
    private long subtractDaysFromInstantNow(int numberOfDays) {[m
        return Instant.now().minus(numberOfDays, ChronoUnit.DAYS).toEpochMilli();[m
    }[m
