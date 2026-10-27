package com.aldercrest.atlas;

import com.aldercrest.atlas.config.SchemaMigration;
import com.aldercrest.atlas.workflow.WorkflowCommand;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class AtlasApplication {
    public static void main(String[] args) {
        if (args.length == 1 && "--migrate".equals(args[0])) {
            SchemaMigration.run(System.getenv());
            return;
        }
        if(args.length>0 && "--workflow".equals(args[0])) {
            int exit=WorkflowCommand.run(args);
            if(exit!=0) {System.exit(exit);}
            return;
        }
        SpringApplication.run(AtlasApplication.class, args);
    }
}
