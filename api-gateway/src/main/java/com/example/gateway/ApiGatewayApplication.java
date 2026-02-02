package com.example.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class ApiGatewayApplication {

    public static void main(String[] args) {
        System.out.println("🚀 API Gateway started - CI test"); // <-- dòng test
        SpringApplication.run(ApiGatewayApplication.class, args);
    }

}
