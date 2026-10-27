package com.aldercrest.atlas.workflow;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.*;

class WorkflowCommandTest {
    @TempDir Path directory;
    @Test void boundedBatchRetainsSeparateOrderedActionsWithoutStartingAContext() throws Exception {
        Path file=directory.resolve("commands.json");
        Files.writeString(file,"[[\"review\",\"actor\",\"patient\",\"observation\",\"NONCRITICAL\"],[\"release\",\"actor\",\"patient\",\"observation\"]]");
        var requests=WorkflowCommand.loadBatch(file);
        assertThat(requests).hasSize(2);assertThat(requests.get(0).action()).isEqualTo("review");
        assertThat(requests.get(1).action()).isEqualTo("release");assertThat(requests.get(1).option()).isNull();
    }
    @Test void malformedOrOversizedBatchIsRejectedBeforeAnyAction() throws Exception {
        Path file=directory.resolve("commands.json");
        for(String input:new String[]{"null","{}","[]","[[\"release\",\"actor\",\"patient\",17]]","[[\"release\",\"actor\",\"patient\",\"observation\"]] []","[[\"reset\",\"actor\",\"patient\",\"observation\"]]"}) {
            Files.writeString(file,input);assertThatThrownBy(() -> WorkflowCommand.loadBatch(file)).isInstanceOf(IOException.class);
        }
        Files.write(file,new byte[2*1024*1024+1]);assertThatThrownBy(() -> WorkflowCommand.loadBatch(file)).isInstanceOf(IOException.class);
    }
    @Test void singleCommandHasExplicitArityAndDoesNotInventAnAttestation() {
        assertThat(WorkflowCommand.parse(new String[]{"--workflow","review","actor","patient","observation","CRITICAL"})).isNotNull();
        assertThat(WorkflowCommand.parse(new String[]{"--workflow","release","actor","patient","observation","extra"})).isNull();
        assertThat(WorkflowCommand.parse(new String[]{"--workflow","attest-critical","actor","patient","observation"})).isNull();
        assertThat(WorkflowCommand.parse(new String[]{"--workflow","reset","actor","patient","observation"})).isNull();
    }
}
