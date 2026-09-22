package com.smartstock.smartstock.controller;

import com.smartstock.smartstock.dto.ApiResponse;
import com.smartstock.smartstock.entity.DemandForecast;
import com.smartstock.smartstock.repository.DemandForecastRepository;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/forecasts")
public class ForecastController {

    @Autowired
    private DemandForecastRepository forecastRepository;

    @Autowired
    private JobOperator jobOperator;

    @Autowired
    private Job demandForecastJob;

    // GET /api/forecasts
    @GetMapping
    public ResponseEntity<List<DemandForecast>> getForecasts() {

        return ResponseEntity.ok(
                forecastRepository.findAllByOrderByCreatedAtDesc()
        );
    }

    // GET /api/forecasts/product/5
    @GetMapping("/product/{productId}")
    public ResponseEntity<DemandForecast> getForecastByProduct(
            @PathVariable Long productId) {

        return forecastRepository
                .findTopByProductIdOrderByCreatedAtDesc(productId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    // POST /api/forecasts/run
    // ADMIN only
    @PostMapping("/run")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse> runForecastJob() {

        try {

            JobParameters params = new JobParametersBuilder()
                    .addLong(
                            "timestamp",
                            System.currentTimeMillis()
                    )
                    .toJobParameters();

            jobOperator.start(
                    demandForecastJob,
                    params
            );

            return ResponseEntity.ok(
                    ApiResponse.success(
                            "Forecast job started successfully"
                    )
            );

        } catch (Exception e) {

            e.printStackTrace();

            return ResponseEntity.internalServerError()
                    .body(
                            ApiResponse.error(
                                    "Forecast job failed: "
                                            + e.getMessage()
                            )
                    );
        }
    }
}