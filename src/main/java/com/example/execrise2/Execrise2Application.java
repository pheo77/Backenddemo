package com.example.execrise2;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Entry point chính của ứng dụng.
 *
 * @EnableAsync:      Xử lý bất đồng bộ (@Async) cho RAG pipeline
 * @EnableScheduling: Cho phép @Scheduled chạy (GroqKeyPool recovery mỗi 15s)
 */
@SpringBootApplication
@EnableAsync
@EnableScheduling
public class Execrise2Application {

    public static void main(String[] args) {
        SpringApplication.run(Execrise2Application.class, args);
    }

}
