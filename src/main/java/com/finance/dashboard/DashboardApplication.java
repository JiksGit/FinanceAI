package com.finance.dashboard;

import com.finance.dashboard.config.AlphaVantageConfig;
import com.finance.dashboard.config.EximConfig;
import com.finance.dashboard.config.JwtConfig;
import com.finance.dashboard.config.OpenAiConfig;
import com.finance.dashboard.config.ServiceAuthConfig;
import com.finance.dashboard.config.SignalConfig;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableConfigurationProperties({JwtConfig.class, EximConfig.class, AlphaVantageConfig.class, SignalConfig.class, OpenAiConfig.class, ServiceAuthConfig.class})
@EnableScheduling
@SpringBootApplication
public class DashboardApplication {

	public static void main(String[] args) {
		SpringApplication.run(DashboardApplication.class, args);
	}

}
