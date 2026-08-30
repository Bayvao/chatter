package com.chatter.chatter.cucumber;

import io.cucumber.spring.CucumberContextConfiguration;
import org.springframework.boot.test.context.SpringBootTest;

import com.chatter.chatter.ChatterApplication;

@CucumberContextConfiguration
@SpringBootTest(classes = ChatterApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class CucumberSpringConfiguration {
}
