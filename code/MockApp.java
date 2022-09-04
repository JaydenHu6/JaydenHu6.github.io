package com.jayden.mxbean;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * MockApp
 *
 * @author jayden
 * @date 2022-08-18
 **/
public class MockApp {

    public static void main(String[] args) throws InterruptedException {
        System.out.println("程序启动");
        // 1. 每隔 1s 收集一次
        Thread countThread = new Thread(new ThreadCollecter(500));
        countThread.setName("thread-counter");
        countThread.start();
        int maxCount = args.length > 0 ? Integer.parseInt(args[0]) : 1000;
        int stepCount = 200;
        int currentCount = 0;
        long sleepTime = 2000;
        while (true) {
            Thread.sleep(sleepTime);
            for (int i = 0; i < stepCount; i++) {
                new EmptyLoopThread().start();
            }
            currentCount += stepCount;
            if (currentCount >= maxCount) {
                break;
            }
        }
    }


    public static class EmptyLoopThread extends Thread {

        @Override
        public void run() {
            for (; ; ) {
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    /**
     * ThreadCounter
     *
     * @author jayden
     * @date 2022-08-18
     **/
    public static class ThreadCollecter extends Thread {

        ThreadMXBean threadMXBean;

        int collectRate = 1000;

        public ThreadCollecter(int collectRate) {
            this.threadMXBean = ManagementFactory.getThreadMXBean();
            this.collectRate = collectRate;
        }

        public ThreadMetric getThreadMetric() {
            long startTime = System.currentTimeMillis();
            int threadCount = threadMXBean.getThreadCount();
            int daemonThreadCount = threadMXBean.getDaemonThreadCount();
            int peakThreadCount = threadMXBean.getPeakThreadCount();
            long endTime = System.currentTimeMillis();
            long spendTime = endTime - startTime;
            Thread thread = Thread.currentThread();
            long id = thread.getId();
            System.out.println(
                    String.format("spendTime:%d ms, thread count:%s, collect thread: %d", spendTime, threadCount, id));
            return new ThreadCollecter.ThreadMetric(threadCount, daemonThreadCount, peakThreadCount);
        }

        @Override
        public void run() {
            while (true) {
                try {
                    Thread.sleep(collectRate);
                    getThreadMetric();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }

        @Data
        @AllArgsConstructor
        public static class ThreadMetric {

            private Integer threadCount;
            private Integer daemonThreadCount;
            private Integer peakThreadCount;
        }

    }

}
