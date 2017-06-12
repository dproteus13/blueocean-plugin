package io.jenkins.blueocean.rest.impl.pipeline;

import com.google.common.base.Objects;
import hudson.model.Result;
import io.jenkins.blueocean.rest.model.BlueRun.BlueRunResult;
import io.jenkins.blueocean.rest.model.BlueRun.BlueRunState;
import org.jenkinsci.plugins.workflow.actions.ArgumentsAction;
import org.jenkinsci.plugins.workflow.actions.ErrorAction;
import org.jenkinsci.plugins.workflow.cps.nodes.StepStartNode;
import org.jenkinsci.plugins.workflow.graph.AtomNode;
import org.jenkinsci.plugins.workflow.graph.FlowNode;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.jenkinsci.plugins.workflow.pipelinegraphanalysis.TimingInfo;
import org.jenkinsci.plugins.workflow.steps.FlowInterruptedException;
import org.jenkinsci.plugins.workflow.support.steps.ExecutorStep;
import org.jenkinsci.plugins.workflow.support.steps.input.InputStep;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * @author Vivek Pandey
 */
public class FlowNodeWrapper {

    public enum NodeType {STAGE, PARALLEL, STEP}

    private final FlowNode node;
    private final NodeRunStatus status;
    private final TimingInfo timingInfo;
    public final List<String> edges = new ArrayList<>();
    public final NodeType type;
    private final String displayName;
    private final String description;
    private final InputStep inputStep;
    private final WorkflowRun run;
    private String causeOfFailure;

    private List<FlowNodeWrapper> parents = new ArrayList<>();

    private ErrorAction blockErrorAction;

    public static FlowNodeWrapper createNodeAllocation(FlowNode node, WorkflowRun run) {
        if (!(node instanceof StepStartNode)) {
            return null;
        }

        StepStartNode startNode = (StepStartNode) node;
        if (!startNode.getDescriptor().getKlass().toJavaClass().equals(ExecutorStep.class)) {
            return null;
        }
        String cause;
        try {
            cause = PipelineNodeUtil.getCauseOfBlockage(node, run);
        } catch (IOException | InterruptedException e) {
            cause = null;
        }

        NodeRunStatus status;
        String description;
        if (cause == null) {
            description = PipelineNodeUtil.getDisplayName(node);
            status = new NodeRunStatus(BlueRunResult.SUCCESS, BlueRunState.FINISHED);
        }
        else {
            description = cause;
            status = new NodeRunStatus(BlueRunResult.UNKNOWN, BlueRunState.QUEUED);
        }
        return new FlowNodeWrapper(node, status, new TimingInfo(), NodeType.STEP, null, description, null, run);
    }

    private FlowNodeWrapper(FlowNode node, NodeRunStatus status, TimingInfo timingInfo, NodeType type, String displayName, String description, InputStep inputStep, WorkflowRun run) {
        this.node = node;
        this.status = status;
        this.timingInfo = timingInfo;
        this.type = type;
        this.displayName = Objects.firstNonNull(displayName, PipelineNodeUtil.getDisplayName(node));
        this.inputStep = inputStep;
        this.run = run;

        // Find the appropriate description or nothing
        String stepArgumentsAsString = ArgumentsAction.getStepArgumentsAsString(node);
        if (description != null) {
            this.description = description;
        } else if (stepArgumentsAsString != null) {
            this.description = stepArgumentsAsString;
        } else {
            this.description = null;
        }
    }

    public FlowNodeWrapper(@Nonnull FlowNode node, @Nonnull NodeRunStatus status, @Nonnull TimingInfo timingInfo, @Nonnull  WorkflowRun run) {
        this(node, status, timingInfo, getNodeType(node), null, null, null, run);
    }

    public FlowNodeWrapper(@Nonnull FlowNode node, @Nonnull NodeRunStatus status,
                           @Nonnull TimingInfo timingInfo, @Nullable InputStep inputStep, @Nonnull WorkflowRun run) {
        this(node, status, timingInfo, getNodeType(node), null, null, inputStep, run);
    }


    public WorkflowRun getRun() {
        return run;
    }

    public @Nonnull String getDisplayName() {
        return displayName;
    }

    public @Nullable String getDescription() {
        return description;
    }

    private static NodeType getNodeType(FlowNode node){
        if(PipelineNodeUtil.isStage(node)){
            return NodeType.STAGE;
        }else if(PipelineNodeUtil.isParallelBranch(node)){
            return NodeType.PARALLEL;
        }else if(node instanceof AtomNode){
            return NodeType.STEP;
        }
        throw new IllegalArgumentException(String.format("Unknown FlowNode %s, type: %s",node.getId(),node.getClass()));
    }

    public @Nonnull NodeRunStatus getStatus() {
        if (hasBlockError()) {
            if (isBlockErrorInterruptedWithAbort()) {
                return new NodeRunStatus(BlueRunResult.ABORTED, BlueRunState.FINISHED);
            } else {
                return new NodeRunStatus(BlueRunResult.FAILURE, BlueRunState.FINISHED);
            }
        }
        return status;
    }

    public @Nonnull TimingInfo getTiming(){
        return timingInfo;
    }

    public @Nonnull String getId(){
        return node.getId();
    }

    public @Nonnull FlowNode getNode(){
        return node;
    }

    public void addEdge(String id){
        this.edges.add(id);
    }

    public void addEdges(List<String> edges){
        this.edges.addAll(edges);
    }

    public void addParent(FlowNodeWrapper parent){
        parents.add(parent);
    }

    public void addParents(Collection<FlowNodeWrapper> parents){
        parents.addAll(parents);
    }

    public @CheckForNull FlowNodeWrapper getFirstParent(){
        return parents.size() > 0 ? parents.get(0): null;
    }

    public @Nonnull List<FlowNodeWrapper> getParents(){
        return parents;
    }

    public String getCauseOfFailure() {
        return causeOfFailure;
    }

    public void setCauseOfFailure(String causeOfFailure) {
        this.causeOfFailure = causeOfFailure;
    }

    @Override
    public boolean equals(Object obj) {
        if(!(obj instanceof FlowNodeWrapper)){
            return false;
        }
        return node.equals(((FlowNodeWrapper)obj).node);
    }

    public @CheckForNull InputStep getInputStep() {
        return inputStep;
    }

    @Override
    public int hashCode() {
        return node.hashCode();
    }

    boolean hasBlockError(){
        return blockErrorAction != null
                && blockErrorAction.getError() != null;
    }

    String blockError(){
        if(hasBlockError()){
            return blockErrorAction.getError().getMessage();
        }
        return null;
    }

    @CheckForNull String nodeError(){
        ErrorAction errorAction = node.getError();
        if(errorAction != null) {
            return errorAction.getError().getMessage();
        }
        return null;
    }

    boolean isBlockErrorInterruptedWithAbort() {
        if (hasBlockError()) {
            Throwable error = blockErrorAction.getError();
            if (error instanceof FlowInterruptedException) {
                FlowInterruptedException interrupted = (FlowInterruptedException)error;
                return interrupted.getResult().equals(Result.ABORTED);
            }
        }
        return false;
    }

    boolean isLoggable(){
        return PipelineNodeUtil.isLoggable.apply(node);
    }

    public void setBlockErrorAction(ErrorAction blockErrorAction) {
        this.blockErrorAction = blockErrorAction;
    }
}
