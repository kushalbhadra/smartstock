package com.smartstock.smartstock.config.batch;

import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;
import org.springframework.batch.core.launch.JobOperator;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class BatchScheduler {

    @Autowired
    private JobOperator jobOperator;

    @Autowired
    private Job demandForecastJob;

    // Runs every day at 2:00 AM
    // Cron format:
    // second minute hour day month weekday
    @Scheduled(cron = "0 0 2 * * *")
    public void runNightlyForecast() {

        System.out.println(
                "Starting nightly demand forecast job..."
        );

        try {

            // Every execution gets a unique parameter
            JobParameters params =
                    new JobParametersBuilder()
                            .addLong(
                                    "timestamp",
                                    System.currentTimeMillis()
                            )
                            .toJobParameters();

            jobOperator.start(
                    demandForecastJob,
                    params
            );

            System.out.println(
                    "Nightly forecast job started."
            );

        } catch (Exception e) {

            System.err.println(
                    "Batch job failed: "
                            + e.getMessage()
            );
        }
    }
}