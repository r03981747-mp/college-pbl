package com.smartcampus.dto;

import java.util.ArrayList;
import java.util.List;

/**
 * Result of the multithreading / synchronisation demonstration.
 * Every line in {@link #getOutcomes()} describes what one real Java thread
 * experienced.
 */
public class ConcurrencyTestResult {

    private String resourceLabel;
    private int attempts;
    private int succeeded;
    private int failed;
    private long elapsedMillis;
    private List<String> outcomes = new ArrayList<>();
    private String summary;

    public ConcurrencyTestResult() {
    }

    public String getResourceLabel() {
        return resourceLabel;
    }

    public void setResourceLabel(String resourceLabel) {
        this.resourceLabel = resourceLabel;
    }

    public int getAttempts() {
        return attempts;
    }

    public void setAttempts(int attempts) {
        this.attempts = attempts;
    }

    public int getSucceeded() {
        return succeeded;
    }

    public void setSucceeded(int succeeded) {
        this.succeeded = succeeded;
    }

    public int getFailed() {
        return failed;
    }

    public void setFailed(int failed) {
        this.failed = failed;
    }

    public long getElapsedMillis() {
        return elapsedMillis;
    }

    public void setElapsedMillis(long elapsedMillis) {
        this.elapsedMillis = elapsedMillis;
    }

    public List<String> getOutcomes() {
        return outcomes;
    }

    public void setOutcomes(List<String> outcomes) {
        this.outcomes = outcomes;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }
}
