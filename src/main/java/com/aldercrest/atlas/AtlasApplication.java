package com.aldercrest.atlas;

import com.aldercrest.atlas.config.SchemaMigration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class AtlasApplication {
    public static void main(String[] args) {
        if (args.length == 1 && "--migrate".equals(args[0])) {
            SchemaMigration.run(System.getenv());
            return;
        }
        SpringApplication.run(AtlasApplication.class, args);
    }
}
