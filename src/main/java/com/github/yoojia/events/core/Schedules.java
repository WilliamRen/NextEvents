package com.github.yoojia.events.core;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 任务调度器
 * @author YOOJIA.CHEN (yoojia.chen@gmail.com)
 */
public final class Schedules {

    /**
     * 调用者调度器
     * @return Schedule
     */
    public static Schedule newCaller() {
        return new Schedule() {
            @Override
            public void invoke(Callable<Void> task, int flags) throws Exception {
                if (FLAG_ON_CALLER_THREAD != flags) {
                    throw new IllegalArgumentException("Caller schedule just accept <Schedule.FLAG_ON_CALLER_THREAD>");
                }
                task.call();
            }
        };
    }

    /**
     * 指定ExecutorService实现的高度器
     * @param service ExecutorService
     * @return Schedule
     */
    public static Schedule newService(final ExecutorService service) {
        return new Schedule() {

            private final ExecutorService mThreads = service;

            @Override
            public void invoke(Callable<Void> task, int scheduleFlags) throws Exception {
                Schedules.invoke(mThreads, task, scheduleFlags);
            }

        };
    }

    /**
     * 全局共享线程池调度器
     * @return Schedule
     */
    public static Schedule sharedThreads(){
        return SharedSchedule.getDefault();
    }

    public static void certainlyShutdownThreads(){
        SharedSchedule.EXECUTOR.shutdown();
    }

    private static void invoke(ExecutorService threads, final Callable<Void> task, int scheduleFlags) throws Exception{
        switch (scheduleFlags) {
            case Schedule.FLAG_ON_CALLER_THREAD:
                task.call();
                break;
            case Schedule.FLAG_ON_THREADS:
                threads.submit(task);
                break;
            case Schedule.FLAG_ON_MAIN_THREAD:
                throw new UnsupportedOperationException("Unsupported run on main thread");
            default:
                throw new IllegalArgumentException("Unsupported Schedule flags: " + scheduleFlags);
        }
    }

    private static class SharedSchedule implements Schedule {

        private static final int CPU_COUNT = Runtime.getRuntime().availableProcessors();
        private static final int CORE_POOL_SIZE = CPU_COUNT + 1;
        private static final int MAXIMUM_POOL_SIZE = CPU_COUNT * 2 + 1;
        private static final int KEEP_ALIVE = 1;

        private static final ThreadFactory THREAD_FACTORY = new ThreadFactory() {

            private final AtomicInteger mCount = new AtomicInteger(1);

            public Thread newThread(Runnable r) {
                return new Thread(r, "SharedThread #" + mCount.getAndIncrement());
            }
        };

        private static BlockingQueue<Runnable> QUEUE;
        private static ThreadPoolExecutor EXECUTOR;

        private static SharedSchedule mDefaultSchedule;

        @Override
        public void invoke(Callable<Void> task, int flags) throws Exception {
            Schedules.invoke(EXECUTOR, task, flags);
        }

        public static SharedSchedule getDefault(){
            synchronized (SharedSchedule.class) {
                if (mDefaultSchedule == null) {
                    QUEUE = new LinkedBlockingQueue<>();
                    EXECUTOR = new ThreadPoolExecutor(CORE_POOL_SIZE, MAXIMUM_POOL_SIZE, KEEP_ALIVE,
                            TimeUnit.SECONDS, QUEUE, THREAD_FACTORY);
                    mDefaultSchedule = new SharedSchedule();
                }
                return mDefaultSchedule;
            }
        }

    }

}
