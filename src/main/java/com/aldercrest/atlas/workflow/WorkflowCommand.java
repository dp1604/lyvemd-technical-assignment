package com.aldercrest.atlas.workflow;

import com.aldercrest.atlas.AtlasApplication;
import com.aldercrest.atlas.ingest.IngestionJson;
import com.aldercrest.atlas.security.TestActor;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;

/** Explicit local operator mode. Never starts HTTP and never emits arguments or clinical content. */
public final class WorkflowCommand {
    private static final int MAX_BATCH_BYTES=2*1024*1024;
    private WorkflowCommand() { }
    public record Request(String action,String actorId,String patientId,String targetId,String option) { }

    public static int run(String[] args) {
        List<Request> requests;
        try {
            if(args.length==3 && "--workflow".equals(args[0]) && "batch".equals(args[1])) {requests=loadBatch(Path.of(args[2]));}
            else {Request request=parse(args);requests=request==null?List.of():List.of(request);}
        } catch(IOException | RuntimeException error) {requests=List.of();}
        if(requests.isEmpty()) {print(new WorkflowService.Outcome(400,"INVALID_COMMAND"),0);return 2;}
        SpringApplication application=new SpringApplication(AtlasApplication.class);
        application.setWebApplicationType(WebApplicationType.NONE);
        application.setLogStartupInfo(false);
        application.setDefaultProperties(Map.of("logging.level.org.springframework","OFF"));
        int completed=0;
        try(var context=application.run()) {
            WorkflowService service=context.getBean(WorkflowService.class);
            for(Request request:requests) {
                // Every service action is independently authorized, audited and committed.
                // Stop on failure: prior successes stay committed, not a bulk transaction.
                WorkflowService.Outcome outcome=invoke(service,request);
                if(outcome.status()!=200) {print(outcome,completed);return outcome.status()==503?3:2;}
                completed++;
            }
            print(new WorkflowService.Outcome(200,"ACTION_COMPLETED"),completed);return 0;
        } catch(RuntimeException error) {
            print(new WorkflowService.Outcome(503,"SERVICE_UNAVAILABLE"),completed);
            return 3;
        }
    }

    /** Kept separate so a generator can call the service without starting one JVM per action. */
    public static WorkflowService.Outcome invoke(WorkflowService service,Request request) {
        String role="revoke-proxy".equals(request.action())?"PATIENT":"CLINICIAN";
        TestActor actor=new TestActor(request.actorId(),role,true,false);
        return switch(request.action()) {
            case "review" -> service.review(actor,request.patientId(),request.targetId(),request.option());
            case "attest-critical" -> service.attestCritical(actor,request.patientId(),request.targetId(),
                    "--attest-complete".equals(request.option()));
            case "release" -> service.release(actor,request.patientId(),request.targetId());
            case "revoke-proxy" -> service.revokeProxy(actor,request.patientId(),request.targetId());
            default -> new WorkflowService.Outcome(400,"INVALID_COMMAND");
        };
    }

    public static Request parse(String[] args) {
        if(args==null || args.length<2 || !"--workflow".equals(args[0])) {return null;}
        boolean option="review".equals(args[1]) || "attest-critical".equals(args[1]);
        boolean plain="release".equals(args[1]) || "revoke-proxy".equals(args[1]);
        if((!option && !plain) || args.length!=(option?6:5)) {return null;}
        for(String value:args) {if(value==null || value.isEmpty() || value.length()>128) {return null;}}
        return new Request(args[1],args[2],args[3],args[4],option?args[5]:null);
    }
    /** Bounded JSON array of ordinary command argument arrays, without --workflow prefixes. */
    public static List<Request> loadBatch(Path path) throws IOException {
        byte[] bytes;
        try(var input=Files.newInputStream(path)) {bytes=input.readNBytes(MAX_BATCH_BYTES+1);}
        if(bytes.length>MAX_BATCH_BYTES) {throw new IOException("INVALID_COMMAND");}
        var batch=IngestionJson.mapper().readTree(bytes);
        if(batch==null || !batch.isArray() || batch.isEmpty() || batch.size()>10_000) {throw new IOException("INVALID_COMMAND");}
        List<Request> requests=new ArrayList<>();
        for(var row:batch) {
            if(!row.isArray() || row.size()<4 || row.size()>5) {throw new IOException("INVALID_COMMAND");}
            String[] arguments=new String[row.size()+1];arguments[0]="--workflow";
            for(int index=0;index<row.size();index++) {
                if(!row.get(index).isTextual()) {throw new IOException("INVALID_COMMAND");}
                arguments[index+1]=row.get(index).textValue();
            }
            Request request=parse(arguments);if(request==null) {throw new IOException("INVALID_COMMAND");}requests.add(request);
        }
        return List.copyOf(requests);
    }
    private static void print(WorkflowService.Outcome result,int completed) {
        System.out.println("ATLAS_WORKFLOW "+result.status()+" "+result.code()+" completed="+completed);
    }
}
