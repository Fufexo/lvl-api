package com.fufexo.lvl;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class LvlApiApplication {

	public static void main(String[] args) {
		SpringApplication.run(LvlApiApplication.class, args);
	}

}
