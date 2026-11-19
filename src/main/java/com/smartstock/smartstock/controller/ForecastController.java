package com.smartstock.smartstock.controller;

import com.smartstock.smartstock.dto.ApiResponse;
import com.smartstock.smartstock.dto.DemandForecastResponse;
import com.smartstock.smartstock.service.DemandForecastService;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

@RestController
@RequestMapping("/api/forecasts")
public class ForecastController {
    private static final Logger LOGGER =
            LoggerFactory.getLogger(ForecastController.class);

    private final DemandForecastService forecastService;
    private final JobOperator jobOperator;
    private final Job demandForecastJob;

    public ForecastController(
            DemandForecastService forecastService,
            JobOperator jobOperator,
            Job demandForecastJob) {
        this.forecastService = forecastService;
        this.jobOperator = jobOperator;
        this.demandForecastJob = demandForecastJob;
    }

    @GetMapping
    public ResponseEntity<List<DemandForecastResponse>> getForecasts(
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(
                forecastService.getForecasts(userDetails.getUsername()));
    }

    @GetMapping("/current")
    public ResponseEntity<List<DemandForecastResponse>> getCurrentForecasts(
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(
                forecastService.getCurrentForecasts(
                        userDetails.getUsername()));
    }

    @GetMapping("/product/{productId}")
    public ResponseEntity<DemandForecastResponse> getForecastByProduct(
            @PathVariable Long productId,
            @AuthenticationPrincipal UserDetails userDetails) {
        return forecastService.getForecast(productId, userDetails.getUsername())
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/run")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse> runForecastJob() {
        try {
            var parameters = new JobParametersBuilder()
                    .addLong("timestamp", System.currentTimeMillis())
                    .toJobParameters();
            jobOperator.start(demandForecastJob, parameters);
            return ResponseEntity.ok(
                    ApiResponse.success("Forecast job started successfully"));
        } catch (Exception exception) {
            LOGGER.error("Failed to start demand forecast job.", exception);
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.error(
                            "Could not start the forecast job."));
        }
    }
}
