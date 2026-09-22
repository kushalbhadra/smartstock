package com.smartstock.smartstock.config.batch;

import com.smartstock.smartstock.entity.DemandForecast;
import com.smartstock.smartstock.entity.Product;
import com.smartstock.smartstock.repository.DemandForecastRepository;
import com.smartstock.smartstock.repository.ProductRepository;
import com.smartstock.smartstock.repository.StockRepository;
import com.smartstock.smartstock.service.GroqAiService;

import org.springframework.batch.core.step.Step;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.infrastructure.item.ItemProcessor;
import org.springframework.batch.infrastructure.item.ItemReader;
import org.springframework.batch.infrastructure.item.ItemWriter;
import org.springframework.batch.infrastructure.item.support.ListItemReader;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import org.springframework.transaction.PlatformTransactionManager;

import java.math.BigDecimal;
import java.util.List;

@Configuration
public class DemandForecastJobConfig {

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private StockRepository stockRepository;

    @Autowired
    private DemandForecastRepository forecastRepository;

    @Autowired
    private GroqAiService groqAiService;


    // =========================================================
    // READER
    // =========================================================

    @Bean
    public ItemReader<ProductForecastData> productForecastReader() {

        List<Product> products = productRepository.findAll();

        List<ProductForecastData> data = products.stream()
                .map(product -> {

                    // Calculate current stock across all warehouses
                    int currentStock = stockRepository
                            .findByProductId(product.getId())
                            .stream()
                            .mapToInt(stock -> stock.getQuantity())
                            .sum();

                    /*
                     * For now these are 0.
                     *
                     * Later we will calculate actual
                     * outbound movements from stock_movements.
                     */
                    int outbound7 = 0;
                    int outbound30 = 0;

                    return new ProductForecastData(
                            product.getId(),
                            product.getName(),
                            product.getCategory(),
                            currentStock,
                            outbound7,
                            outbound30
                    );
                })
                .toList();

        return new ListItemReader<>(data);
    }


    // =========================================================
    // PROCESSOR
    // =========================================================

    @Bean
    public ItemProcessor<ProductForecastData, DemandForecast>
    forecastProcessor() {

        return data -> {

            System.out.println(
                    "Processing forecast for: "
                            + data.getProductName()
            );

            // Call Groq AI
            GroqAiService.ForecastResult result =
                    groqAiService.forecastDemand(
                            data.getProductId(),
                            data.getProductName(),
                            data.getCategory(),
                            data.getOutboundLast30Days(),
                            data.getOutboundLast7Days(),
                            data.getCurrentStock()
                    );

            // Create a Product reference
            Product product = new Product();
            product.setId(data.getProductId());

            // Create DemandForecast entity
            return DemandForecast.builder()
                    .product(product)
                    .forecastNextWeek(
                            result.forecastNextWeek()
                    )
                    .forecastNextMonth(
                            result.forecastNextMonth()
                    )
                    .confidenceScore(
                            BigDecimal.valueOf(
                                    result.confidenceScore()
                            )
                    )
                    .anomalyDetected(
                            result.anomalyDetected()
                    )
                    .recommendation(
                            result.recommendation()
                    )
                    .suggestedReorderQuantity(
                            result.suggestedReorderQuantity()
                    )
                    .build();
        };
    }


    // =========================================================
    // WRITER
    // =========================================================

    @Bean
    public ItemWriter<DemandForecast> forecastWriter() {

        return chunk -> {

            forecastRepository.saveAll(
                    chunk.getItems()
            );

            System.out.println(
                    "Saved "
                            + chunk.size()
                            + " forecasts"
            );
        };
    }


    // =========================================================
    // STEP
    // =========================================================

    @Bean
    public Step demandForecastStep(
            JobRepository jobRepository,
            PlatformTransactionManager transactionManager) {

        return new StepBuilder(
                "demandForecastStep",
                jobRepository
        )
                .<ProductForecastData, DemandForecast>chunk(10)
                .reader(productForecastReader())
                .processor(forecastProcessor())
                .writer(forecastWriter())
                .transactionManager(transactionManager)
                .build();
    }


    // =========================================================
    // JOB
    // =========================================================

    @Bean
    public Job demandForecastJob(
            JobRepository jobRepository,
            Step demandForecastStep) {

        return new JobBuilder(
                "demandForecastJob",
                jobRepository
        )
                .start(demandForecastStep)
                .build();
    }
}