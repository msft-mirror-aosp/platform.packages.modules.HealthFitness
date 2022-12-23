/*
 * Copyright (C) 2023 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.server.healthconnect;

import android.annotation.NonNull;
import android.app.ActivityManager;
import android.content.Context;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * A scheduler class to schedule task on the most relevant thread-pool.
 *
 * @hide
 */
public final class HealthConnectThreadScheduler {
    private static final int NUM_EXECUTOR_THREADS_INTERNAL_BACKGROUND = 1;
    private static final long KEEP_ALIVE_TIME_INTERNAL_BACKGROUND = 60L;
    // Executor to run HC background tasks
    private static final Executor INTERNAL_BACKGROUND_EXECUTOR =
            new ThreadPoolExecutor(
                    NUM_EXECUTOR_THREADS_INTERNAL_BACKGROUND,
                    NUM_EXECUTOR_THREADS_INTERNAL_BACKGROUND,
                    KEEP_ALIVE_TIME_INTERNAL_BACKGROUND,
                    TimeUnit.SECONDS,
                    new LinkedBlockingQueue<>());

    private static final int NUM_EXECUTOR_THREADS_BACKGROUND = 1;
    private static final long KEEP_ALIVE_TIME_BACKGROUND = 60L;
    // Executor to run HC background tasks
    static final Executor BACKGROUND_EXECUTOR =
            new ThreadPoolExecutor(
                    NUM_EXECUTOR_THREADS_BACKGROUND,
                    NUM_EXECUTOR_THREADS_BACKGROUND,
                    KEEP_ALIVE_TIME_BACKGROUND,
                    TimeUnit.SECONDS,
                    new LinkedBlockingQueue<>());

    private static final int NUM_EXECUTOR_THREADS_FOREGROUND = 1;
    private static final long KEEP_ALIVE_TIME_SHARED = 60L;
    // Executor to run HC tasks for clients
    private static final Executor FOREGROUND_EXECUTOR =
            new ThreadPoolExecutor(
                    NUM_EXECUTOR_THREADS_FOREGROUND,
                    NUM_EXECUTOR_THREADS_FOREGROUND,
                    KEEP_ALIVE_TIME_SHARED,
                    TimeUnit.SECONDS,
                    new LinkedBlockingQueue<>());

    private static final int NUM_EXECUTOR_THREADS_CONTROLLER = 1;
    private static final long KEEP_ALIVE_TIME_CONTROLLER = 60L;
    // Executor to run HC controller tasks
    private static final Executor CONTROLLER_EXECUTOR =
            new ThreadPoolExecutor(
                    NUM_EXECUTOR_THREADS_CONTROLLER,
                    NUM_EXECUTOR_THREADS_CONTROLLER,
                    KEEP_ALIVE_TIME_CONTROLLER,
                    TimeUnit.SECONDS,
                    new LinkedBlockingQueue<>());
    // Scheduler to run the tasks in a RR fashion based on client package names.
    private static final HealthConnectRoundRobinScheduler
            HEALTH_CONNECT_BACKGROUND_ROUND_ROBIN_SCHEDULER =
                    new HealthConnectRoundRobinScheduler();

    /** Schedules the task on the executor dedicated for performing internal tasks */
    static void scheduleInternalTask(Runnable task) {
        INTERNAL_BACKGROUND_EXECUTOR.execute(task);
    }

    /** Schedules the task on the executor dedicated for performing controller tasks */
    static void scheduleControllerTask(Runnable task) {
        CONTROLLER_EXECUTOR.execute(task);
    }

    /** Schedules the task on the best possible executor based on the parameters */
    static void schedule(Context context, @NonNull Runnable task, int uid, boolean isController) {
        if (isController) {
            CONTROLLER_EXECUTOR.execute(task);
            return;
        }

        if (isUidInForeground(context, uid)) {
            FOREGROUND_EXECUTOR.execute(
                    () -> {
                        try {
                            if (!isUidInForeground(context, uid)) {
                                // The app is no longer in foreground so move the task to background
                                // thread. This is because foreground thread should only be used by
                                // the foreground app and since the request of this task is no
                                // longer in foreground we don't want it to consume foreground
                                // resource anymore.
                                HEALTH_CONNECT_BACKGROUND_ROUND_ROBIN_SCHEDULER.addTask(uid, task);
                                BACKGROUND_EXECUTOR.execute(
                                        () ->
                                                HEALTH_CONNECT_BACKGROUND_ROUND_ROBIN_SCHEDULER
                                                        .getNextTask()
                                                        .run());
                                return;
                            }
                        } catch (Exception exception) {
                            // This is very unlikely, nonetheless we were unable to push the task to
                            // the background thread, ignore this and try to run it on foreground
                            // thread
                        }

                        task.run();
                    });
        } else {
            HEALTH_CONNECT_BACKGROUND_ROUND_ROBIN_SCHEDULER.addTask(uid, task);
            BACKGROUND_EXECUTOR.execute(
                    () -> HEALTH_CONNECT_BACKGROUND_ROUND_ROBIN_SCHEDULER.getNextTask().run());
        }
    }

    private static boolean isUidInForeground(Context context, int uid) {
        ActivityManager activityManager = context.getSystemService(ActivityManager.class);
        Objects.requireNonNull(activityManager);
        List<ActivityManager.RunningAppProcessInfo> runningAppProcesses =
                activityManager.getRunningAppProcesses();
        for (ActivityManager.RunningAppProcessInfo info : runningAppProcesses) {
            if (info.uid == uid
                    && info.importance
                            == ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND) {
                return true;
            }
        }
        return false;
    }
}