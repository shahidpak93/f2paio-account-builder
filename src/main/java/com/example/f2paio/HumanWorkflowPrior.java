package com.example.f2paio;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/** Small prior on ready workflows, estimated from public annotated demonstrations. */
final class HumanWorkflowPrior {
    private static final Profile PROFILE = load();

    private static final class Profile {
        int transitions;
        Evaluation evaluation;
    }
    private static final class Evaluation {
        boolean validated;
        double workContinuationRate;
    }

    static double bonus(Method method,GameState state,boolean stocked) {
        if (!stocked || !ProgressionPolicy.trains(method) || PROFILE.transitions<100 || PROFILE.evaluation==null) return 0;
        int distance=state.location.distanceTo(method.location);
        double proximity=Math.max(0,1-distance/100.0);
        return 4 * proximity * PROFILE.evaluation.workContinuationRate;
    }

    static int observations() { return PROFILE.transitions; }

    private static Profile load() {
        try (InputStream data=HumanWorkflowPrior.class.getResourceAsStream("/human-workflow-prior.json")) {
            if (data!=null) {
                Profile loaded=FilesStore.JSON.fromJson(new InputStreamReader(data,StandardCharsets.UTF_8),Profile.class);
                if (loaded!=null && loaded.evaluation!=null && loaded.evaluation.validated
                    && loaded.evaluation.workContinuationRate>=0 && loaded.evaluation.workContinuationRate<=1) return loaded;
            }
        } catch (Exception error) {
            DebugLog.event("HUMAN_PRIOR","No readable demonstration profile");
        }
        return new Profile();
    }

    private HumanWorkflowPrior() { }
}
