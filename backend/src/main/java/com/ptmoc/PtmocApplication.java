package com.ptmoc;

import com.ptmoc.config.AmapProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(AmapProperties.class)
public class PtmocApplication {
    public static void main(String[] args) {
        SpringApplication.run(PtmocApplication.class, args);
    }
}
