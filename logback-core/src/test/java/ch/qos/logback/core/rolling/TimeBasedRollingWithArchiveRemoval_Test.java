/**
 * Logback: the reliable, generic, fast and flexible logging framework.
 * Copyright (C) 1999-2015, QOS.ch. All rights reserved.
 *
 * This program and the accompanying materials are dual-licensed under
 * either the terms of the Eclipse Public License v1.0 as published by
 * the Eclipse Foundation
 *
 *   or (per the licensee's choosing)
 *
 * under the terms of the GNU Lesser General Public License version 2.1
 * as published by the Free Software Foundation.
 */
package ch.qos.logback.core.rolling;

import static ch.qos.logback.core.CoreConstants.DAILY_DATE_PATTERN;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import ch.qos.logback.core.rolling.helper.RollingCalendar;
import ch.qos.logback.core.util.StatusPrinter;

public class TimeBasedRollingWithArchiveRemoval_Test extends ScaffoldingForRollingTests {
    String MONTHLY_DATE_PATTERN = "yyyy-MM";
    String MONTHLY_CRONOLOG_DATE_PATTERN = "yyyy/MM";
    final String DAILY_CRONOLOG_DATE_PATTERN = "yyyy/MM/dd";

    RollingFileAppender<Object> rfa = new RollingFileAppender<Object>();
    TimeBasedRollingPolicy<Object> tbrp = new TimeBasedRollingPolicy<Object>();

    // by default tbfnatp is an instance of DefaultTimeBasedFileNamingAndTriggeringPolicy
    TimeBasedFileNamingAndTriggeringPolicy<Object> tbfnatp = new DefaultTimeBasedFileNamingAndTriggeringPolicy<Object>();

    static long MILLIS_IN_MINUTE = 60 * 1000;
    static long MILLIS_IN_HOUR = 60 * MILLIS_IN_MINUTE;
    static long MILLIS_IN_DAY = 24 * MILLIS_IN_HOUR;
    static long MILLIS_IN_MONTH = (long) ((365.242199 / 12) * MILLIS_IN_DAY);
    static int MONTHS_IN_YEAR = 12;

    int slashCount = 0;

    ConfigParameters cp; // initialized in setup

    @Before
    public void setUp() {
        super.setUp();
        this.cp = new ConfigParameters(currentTime);
    }

    private int computeSlashCount(String datePattern) {
        if (datePattern == null)
            return 0;
        else {
            int count = 0;
            for (int i = 0; i < datePattern.length(); i++) {
                char c = datePattern.charAt(i);
                if (c == '/')
                    count++;
            }
            return count;
        }
    }

    // test that the number of files at the end of the test is same as the expected number taking into account end dates
    // near the beginning of a new year. This test has been run in a loop with start date varying over a two years
    // with success.
    @Test
    public void monthlyRolloverOverManyPeriods() {
        this.slashCount = computeSlashCount(MONTHLY_CRONOLOG_DATE_PATTERN);
        int maxHistory = 2;
        int simulatedNumberOfPeriods = 40;
        String fileNamePattern = randomOutputDir + "/%d{" + MONTHLY_CRONOLOG_DATE_PATTERN + "}/clean.txt.zip";

        cp.maxHistory(maxHistory).fileNamePattern(fileNamePattern).simulatedNumberOfPeriods(simulatedNumberOfPeriods).periodDurationInMillis(MILLIS_IN_MONTH);

        long startTime = currentTime;
        long endTime = logOverMultiplePeriods(cp);
        System.out.println("randomOutputDir:" + randomOutputDir);
        System.out.println("start:" + startTime + ", end=" + endTime);
        int differenceInMonths = RollingCalendar.diffInMonths(startTime, endTime);
        System.out.println("differenceInMonths:" + differenceInMonths);
        Calendar startTimeAsCalendar = Calendar.getInstance();
        startTimeAsCalendar.setTimeInMillis(startTime);
        int indexOfStartPeriod = startTimeAsCalendar.get(Calendar.MONTH);
        boolean withExtraFolder = extraFolder(differenceInMonths, MONTHS_IN_YEAR, indexOfStartPeriod, maxHistory);

        checkFileCount(expectedCountWithFolders(maxHistory, withExtraFolder));
    }

    void generateDailyRollover(ConfigParameters cp) {
        this.slashCount = computeSlashCount(DAILY_DATE_PATTERN);
        cp.fileNamePattern(randomOutputDir + "clean-%d{" + DAILY_DATE_PATTERN + "}.txt");
        logOverMultiplePeriods(cp);
    }

    void generateDailyRolloverAndCheckFileCount(ConfigParameters cp) {
        generateDailyRollover(cp);
        checkFileCount(expectedCountWithoutFoldersWithInactivity(cp.maxHistory, cp.simulatedNumberOfPeriods, cp.startInactivity + cp.numInactivityPeriods));
    }

    @Test
    public void checkCleanupForBasicDailyRollover() {
        cp.maxHistory(20).simulatedNumberOfPeriods(20 * 3).startInactivity(0).numInactivityPeriods(0);
        generateDailyRolloverAndCheckFileCount(cp);
    }

    @Test
    public void checkCleanupForBasicDailyRolloverWithSizeCap() {
        long sizeOfOuputPerPeriod = 36 * 1024;
        int sizeInUnitsOfPeriodCount = 2;

        cp.maxHistory(20).simulatedNumberOfPeriods(20 * 3).startInactivity(0).numInactivityPeriods(0).sizeCap(sizeInUnitsOfPeriodCount * sizeOfOuputPerPeriod);
        generateDailyRollover(cp);
        checkFileCount(sizeInUnitsOfPeriodCount + 1);
        StatusPrinter.print(context);
    }

    @Test
    public void checkCleanupForBasicDailyRolloverWithMaxSize() {
        cp.maxHistory(6).simulatedNumberOfPeriods(70).startInactivity(30).numInactivityPeriods(1);
        generateDailyRolloverAndCheckFileCount(cp);
    }

    // Since the duration of a month (in seconds) varies from month to month, tests with inactivity period must
    // be conducted with daily rollover not monthly
    @Test
    public void checkCleanupForDailyRollover_15Periods() {
        cp.maxHistory(5).simulatedNumberOfPeriods(15).startInactivity(6).numInactivityPeriods(3);
        generateDailyRolloverAndCheckFileCount(cp);
    }

    @Test
    public void checkCleanupForDailyRolloverWithInactivity_70Periods() {
        cp.maxHistory(6).simulatedNumberOfPeriods(70).startInactivity(30).numInactivityPeriods(1);
        generateDailyRolloverAndCheckFileCount(cp);
    }

    @Test
    public void checkCleanupForDailyRolloverWithInactivity_10Periods() {
        cp.maxHistory(6).simulatedNumberOfPeriods(10).startInactivity(3).numInactivityPeriods(4);
        generateDailyRolloverAndCheckFileCount(cp);
    }

    @Test
    public void checkCleanupForDailyRolloverWithSecondPhase() {
        slashCount = computeSlashCount(DAILY_DATE_PATTERN);
        int maxHistory = 5;
        String fileNamePattern = randomOutputDir + "clean-%d{" + DAILY_DATE_PATTERN + "}.txt";

        ConfigParameters cp0 = new ConfigParameters(currentTime).maxHistory(maxHistory).fileNamePattern(fileNamePattern)
                        .simulatedNumberOfPeriods(maxHistory * 2);
        long endTime = logOverMultiplePeriods(cp0);

        ConfigParameters cp1 = new ConfigParameters(endTime + MILLIS_IN_DAY * 10).maxHistory(maxHistory).fileNamePattern(fileNamePattern)
                        .simulatedNumberOfPeriods(maxHistory);
        logOverMultiplePeriods(cp1);
        checkFileCount(expectedCountWithoutFolders(maxHistory));
    }

    @Test
    public void dailyRolloverWithCronologPattern() {
        this.slashCount = computeSlashCount(DAILY_CRONOLOG_DATE_PATTERN);
        String fileNamePattern = randomOutputDir + "/%d{" + DAILY_CRONOLOG_DATE_PATTERN + "}/clean.txt.zip";
        cp.maxHistory(8).fileNamePattern(fileNamePattern).simulatedNumberOfPeriods(8 * 3);
        logOverMultiplePeriods(cp);
        int expectedDirMin = 9 + slashCount;
        int expectDirMax = expectedDirMin + 1 + 1;
        expectedFileAndDirCount(9, expectedDirMin, expectDirMax);
    }

    @Test
    public void dailySizeBasedRollover() {
        SizeAndTimeBasedFNATP<Object> sizeAndTimeBasedFNATP = new SizeAndTimeBasedFNATP<Object>();
        sizeAndTimeBasedFNATP.setMaxFileSize("10000");
        tbfnatp = sizeAndTimeBasedFNATP;
        this.slashCount = computeSlashCount(DAILY_DATE_PATTERN);
        String fileNamePattern = randomOutputDir + "/%d{" + DAILY_DATE_PATTERN + "}-clean.%i.zip";
        cp.maxHistory(5).fileNamePattern(fileNamePattern).simulatedNumberOfPeriods(5 * 4);
        logOverMultiplePeriods(cp);
        checkPatternCompliance(5 + 1 + slashCount, "\\d{4}-\\d{2}-\\d{2}-clean(\\.\\d)(.zip)?");
    }

    @Test
    public void dailySizeBasedRolloverWithSizeCap()  {
        SizeAndTimeBasedFNATP<Object> sizeAndTimeBasedFNATP = new SizeAndTimeBasedFNATP<Object>();
        int fileSize = 12 * 1024;
        int sizeCap = 4 * fileSize + 100;
        sizeAndTimeBasedFNATP.setMaxFileSize(Integer.toString(fileSize));
        tbfnatp = sizeAndTimeBasedFNATP;
        this.slashCount = computeSlashCount(DAILY_DATE_PATTERN);
        String fileNamePattern = randomOutputDir + "/%d{" + DAILY_DATE_PATTERN + "}-clean.%i";
        cp.maxHistory(6).fileNamePattern(fileNamePattern).simulatedNumberOfPeriods(10).sizeCap(sizeCap);
        logOverMultiplePeriods(cp);

        List<File> foundFiles = findFilesByPattern("\\d{4}-\\d{2}-\\d{2}-clean(\\.\\d)");
        Collections.sort(foundFiles, new Comparator<File>() {
            public int compare(File f0, File f1) {
                String s0 = f0.getName().toString();
                String s1 = f1.getName().toString();
                return s0.compareTo(s1);
            }
        });
        String oldest = foundFiles.get(0).getName();
        // the oldest file must end with a suffix other than 0
        assertFalse(oldest.endsWith("0"));
        System.out.println(oldest);
        StatusPrinter.print(context);
        checkFileCount(sizeCap / fileSize + 1);
    }

    @Test
    public void dailyChronologSizeBasedRollover() {
        SizeAndTimeBasedFNATP<Object> sizeAndTimeBasedFNATP = new SizeAndTimeBasedFNATP<Object>();
        sizeAndTimeBasedFNATP.setMaxFileSize("10000");
        tbfnatp = sizeAndTimeBasedFNATP;
        slashCount = 1;
        String fileNamePattern = randomOutputDir + "/%d{" + DAILY_DATE_PATTERN + "}/clean.%i.zip";
        cp.maxHistory(5).fileNamePattern(fileNamePattern).simulatedNumberOfPeriods(5 * 4);
        logOverMultiplePeriods(cp);
        checkDirPatternCompliance(6);
    }

    @Test
    public void dailyChronologSizeBasedRolloverWithSecondPhase() {
        SizeAndTimeBasedFNATP<Object> sizeAndTimeBasedFNATP = new SizeAndTimeBasedFNATP<Object>();
        sizeAndTimeBasedFNATP.setMaxFileSize("10000");
        tbfnatp = sizeAndTimeBasedFNATP;
        this.slashCount = 1;
        String fileNamePattern = randomOutputDir + "/%d{" + DAILY_DATE_PATTERN + "}/clean.%i";
        int maxHistory = 5;
        cp.maxHistory(maxHistory).fileNamePattern(fileNamePattern).simulatedNumberOfPeriods(3);
        long endTime = logOverMultiplePeriods(cp);

        int simulatedNumberOfPeriods = maxHistory * 4;
        ConfigParameters cp1 = new ConfigParameters(endTime + MILLIS_IN_DAY * 7).maxHistory(maxHistory).fileNamePattern(fileNamePattern)
                        .simulatedNumberOfPeriods(simulatedNumberOfPeriods);
        logOverMultiplePeriods(cp1);
        checkDirPatternCompliance(maxHistory + 1);
    }

    void logOncePeriod(long currentTime, String fileNamePattern, int maxHistory) {
        ConfigParameters params = new ConfigParameters(currentTime).fileNamePattern(fileNamePattern).maxHistory(maxHistory);
        buildRollingFileAppender(params, DO_CLEAN_HISTORY_ON_START);
        rfa.doAppend("Hello ----------------------------------------------------------" + new Date(currentTime));
        rfa.stop();
    }

    @Test
    public void cleanHistoryOnStart() {
        long simulatedTime = this.currentTime;
        String fileNamePattern = randomOutputDir + "clean-%d{" + DAILY_DATE_PATTERN + "}.txt";
        int maxHistory = 3;
        for (int i = 0; i <= 5; i++) {
            logOncePeriod(simulatedTime, fileNamePattern, maxHistory);
            simulatedTime += MILLIS_IN_DAY;
        }
        // StatusPrinter.print(context);
        checkFileCount(expectedCountWithoutFolders(maxHistory));
    }

    @Test
    public void cleanHistoryOnStartWithDayPattern() {
        long simulatedTime = this.currentTime;
        String fileNamePattern = randomOutputDir + "clean-%d{yyyy-MM-dd}.txt";
        int maxHistory = 3;
        for (int i = 0; i <= 5; i++) {
            logOncePeriod(simulatedTime, fileNamePattern, maxHistory);
            simulatedTime += MILLIS_IN_DAY;
        }
        StatusPrinter.print(context);
        checkFileCount(expectedCountWithoutFolders(maxHistory));
    }

    @Ignore
    @Test
    // this test assumes a high degree of collisions in the archived files. Every 24 hours, the archive
    // belonging to the previous day will be overwritten. Given that logback goes 14 days (336 hours) in history
    // to clean files on start up, it is bound to delete more recent files. It is not logback's responsibility
    // to cater for such degenerate cases.
    public void cleanHistoryOnStartWithHourPattern() {
        long now = this.currentTime;
        String fileNamePattern = randomOutputDir + "clean-%d{HH}.txt";
        int maxHistory = 3;
        for (int i = 0; i <= 5; i++) {
            logOncePeriod(now, fileNamePattern, maxHistory);
            now = now + MILLIS_IN_HOUR;
        }
        StatusPrinter.print(context);
        checkFileCount(expectedCountWithoutFolders(maxHistory));
    }

    int expectedCountWithoutFolders(int maxHistory) {
        return maxHistory + 1;
    }

    int expectedCountWithFolders(int maxHistory, boolean withExtraFolder) {
        int numLogFiles = (maxHistory + 1);
        int numLogFilesAndFolders = numLogFiles * 2;
        int result = numLogFilesAndFolders + slashCount;
        if (withExtraFolder)
            result += 1;
        return result;
    }

    void buildRollingFileAppender(ConfigParameters cp, boolean cleanHistoryOnStart) {
        rfa.setContext(context);
        rfa.setEncoder(encoder);
        tbrp.setContext(context);
        tbrp.setFileNamePattern(cp.fileNamePattern);
        tbrp.setMaxHistory(cp.maxHistory);
        tbrp.setTotalSizeCap(cp.sizeCap);
        tbrp.setParent(rfa);
        tbrp.setCleanHistoryOnStart(cleanHistoryOnStart);
        tbrp.timeBasedFileNamingAndTriggeringPolicy = tbfnatp;
        tbrp.timeBasedFileNamingAndTriggeringPolicy.setCurrentTime(cp.simulatedTime);
        tbrp.start();
        rfa.setRollingPolicy(tbrp);
        rfa.start();
    }

    boolean DO_CLEAN_HISTORY_ON_START = true;
    boolean DO_NOT_CLEAN_HISTORY_ON_START = false;

    // long logOverMultiplePeriodsContinuously(long simulatedTime, String fileNamePattern, long periodDurationInMillis,
    // int maxHistory,
    // int simulatedNumberOfPeriods) {
    // return logOverMultiplePeriods(simulatedTime, fileNamePattern, periodDurationInMillis, maxHistory,
    // simulatedNumberOfPeriods, 0, 0);
    // }

    // long logOverMultiplePeriods(long simulatedTime, String fileNamePattern, long periodDurationInMillis, int
    // maxHistory, int simulatedNumberOfPeriods,
    // int startInactivity, int numInactivityPeriods) {
    long logOverMultiplePeriods(ConfigParameters cp) {

        buildRollingFileAppender(cp, DO_NOT_CLEAN_HISTORY_ON_START);
        int ticksPerPeriod = 512;
        int runLength = cp.simulatedNumberOfPeriods * ticksPerPeriod;
        int startInactivityIndex = 1 + cp.startInactivity * ticksPerPeriod;
        int endInactivityIndex = startInactivityIndex + cp.numInactivityPeriods * ticksPerPeriod;
        long tickDuration = cp.periodDurationInMillis / ticksPerPeriod;

        for (int i = 0; i <= runLength; i++) {
            if (i < startInactivityIndex || i > endInactivityIndex) {
                rfa.doAppend("Hello ----------------------------------------------------------" + i);
            }
            tbrp.timeBasedFileNamingAndTriggeringPolicy.setCurrentTime(addTime(tbrp.timeBasedFileNamingAndTriggeringPolicy.getCurrentTime(), tickDuration));
            add(tbrp.compressionFuture);
            add(tbrp.cleanUpFuture);
            waitForJobsToComplete();
        }
        rfa.stop();
        return tbrp.timeBasedFileNamingAndTriggeringPolicy.getCurrentTime();
    }

    boolean extraFolder(int numPeriods, int periodsPerEra, int beginPeriod, int maxHistory) {
        int valueOfLastMonth = ((beginPeriod) + numPeriods) % periodsPerEra;
        return (valueOfLastMonth < maxHistory);
    }

    long addTime(long time, long timeToWait) {
        return time + timeToWait;
    }

    void expectedFileAndDirCount(int expectedFileAndDirCount, int expectedDirCountMin, int expectedDirCountMax) {
        File dir = new File(randomOutputDir);
        List<File> fileList = new ArrayList<File>();
        findFilesInFolderRecursivelyByPatterMatch(dir, fileList, "clean");
        List<File> dirList = new ArrayList<File>();
        findAllFoldersInFolderRecursively(dir, dirList);
        String msg = "expectedDirCountMin=" + expectedDirCountMin + ", expectedDirCountMax=" + expectedDirCountMax + " actual value=" + dirList.size();
        assertTrue(msg, expectedDirCountMin <= dirList.size() && dirList.size() <= expectedDirCountMax);
    }

    void checkFileCount(int expectedCount) {
        File dir = new File(randomOutputDir);
        List<File> fileList = new ArrayList<File>();
        findAllDirsOrStringContainsFilesRecursively(dir, fileList, "clean");
        assertEquals(expectedCount, fileList.size());
    }

    int expectedCountWithoutFoldersWithInactivity(int maxHistory, int totalPeriods, int endOfInactivity) {
        int availableHistory = (totalPeriods + 1) - endOfInactivity;
        int actualHistory = Math.min(availableHistory, maxHistory + 1);
        return actualHistory;
    }

    void genericFindMatching(final FileMatchFunction matchFunc, File dir, List<File> fileList, final String pattern, boolean includeDirs) {
        if (dir.isDirectory()) {
            File[] matchArray = dir.listFiles(new FileFilter() {
                public boolean accept(File f) {
                    return f.isDirectory() || matchFunc.match(f, pattern);
                }
            });
            for (File f : matchArray) {
                if (f.isDirectory()) {
                    if (includeDirs)
                        fileList.add(f);
                    genericFindMatching(matchFunc, f, fileList, pattern, includeDirs);
                } else
                    fileList.add(f);
            }
        }
    }

    private void findAllFoldersInFolderRecursively(File dir, List<File> fileList) {
        FileMatchFunction alwaysFalse = new FileMatchFunction() {
            public boolean match(File f, String pattern) {
                return false;
            }
        };
        genericFindMatching(alwaysFalse, dir, fileList, null, true);
    }

    private void findAllDirsOrStringContainsFilesRecursively(File dir, List<File> fileList, String pattern) {
        FileMatchFunction matchFunction = new FileMatchFunction() {
            public boolean match(File f, String pattern) {
                return f.getName().contains(pattern);
            }
        };
        genericFindMatching(matchFunction, dir, fileList, pattern, true);
    }

    void findFilesInFolderRecursivelyByPatterMatch(File dir, List<File> fileList, String pattern) {
        FileMatchFunction matchByPattern = new FileMatchFunction() {
            public boolean match(File f, String pattern) {
                return f.getName().matches(pattern);
            }
        };
        genericFindMatching(matchByPattern, dir, fileList, pattern, false);
    }

    Set<String> groupByClass(List<File> fileList, String regex) {
        Pattern p = Pattern.compile(regex);
        Set<String> set = new HashSet<String>();
        for (File f : fileList) {
            String n = f.getName();
            Matcher m = p.matcher(n);
            m.matches();
            int begin = m.start(1);
            String reduced = n.substring(0, begin);
            set.add(reduced);
        }
        return set;
    }

    void checkPatternCompliance(int expectedClassCount, String regex) {
        Set<String> set = findFilesByPatternClass(regex);
        assertEquals(expectedClassCount, set.size());
    }

    private List<File> findFilesByPattern(String regex) {
        File dir = new File(randomOutputDir);
        List<File> fileList = new ArrayList<File>();
        findFilesInFolderRecursivelyByPatterMatch(dir, fileList, regex);
        return fileList;
    }

    private Set<String> findFilesByPatternClass(String regex) {
        List<File> fileList = findFilesByPattern(regex);
        Set<String> set = groupByClass(fileList, regex);
        return set;
    }

    void checkDirPatternCompliance(int expectedClassCount) {
        File dir = new File(randomOutputDir);
        List<File> fileList = new ArrayList<File>();
        findAllFoldersInFolderRecursively(dir, fileList);
        for (File f : fileList) {
            assertTrue(f.list().length >= 1);
        }
        assertEquals(expectedClassCount, fileList.size());
    }
}
