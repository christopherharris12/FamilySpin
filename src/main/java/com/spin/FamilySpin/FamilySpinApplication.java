package com.spin.FamilySpin;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class FamilySpinApplication {

	public static void main(String[] args) {
		SpringApplication.run(FamilySpinApplication.class, args);
	}

}
