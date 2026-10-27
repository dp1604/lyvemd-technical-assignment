package com.aldercrest.atlas.config;

import ca.uhn.fhir.context.FhirContext;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class FoundationConfiguration {
    @Bean
    Clock clinicalClock() { return Clock.systemUTC(); }

    @Bean
    FhirContext fhirContext() { return FhirContext.forR4(); }
}
