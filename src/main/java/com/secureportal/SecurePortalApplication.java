package com.secureportal;

import com.secureportal.config.AppProperties;
import com.secureportal.config.StorageProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties({AppProperties.class, StorageProperties.class})
public class SecurePortalApplication {

    public static void main(String[] args) {
        SpringApplication.run(SecurePortalApplication.class, args);
    }
}
