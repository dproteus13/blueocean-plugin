package io.jenkins.blueocean.rest.model;

import com.google.common.base.Predicate;
import com.google.common.collect.Iterators;
import io.jenkins.blueocean.rest.model.BlueRun.BlueRunState;

import javax.annotation.Nullable;

/**
 * Container for pipeline step resource
 *
 * @author Vivek Pandey
 */
public abstract class BluePipelineStepContainer extends Container<BluePipelineStep> {

    public boolean any(BlueRunState state) {
        return Iterators.any(iterator(), new StepStatePredicate(state));
    }

    private static class StepStatePredicate implements Predicate<BluePipelineStep> {

        private final BlueRunState state;

        public StepStatePredicate(BlueRunState state) {
            this.state = state;
        }

        @Override
        public boolean apply(@Nullable BluePipelineStep input) {
            return input != null && input.getStateObj().equals(state);
        }
    }
}
