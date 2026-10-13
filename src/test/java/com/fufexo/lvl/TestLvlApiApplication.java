package com.fufexo.lvl;

import org.springframework.boot.SpringApplication;

public class TestLvlApiApplication {

	public static void main(String[] args) {
		SpringApplication.from(LvlApiApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
